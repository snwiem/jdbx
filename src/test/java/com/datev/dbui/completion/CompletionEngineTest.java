package com.datev.dbui.completion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CompletionEngineTest {

	/** A schema model from a literal: schema -> table -> columns. Views are named with a {@code v_} prefix. */
	private static final class FakeSchema implements SchemaModel {
		private final Map<String, Map<String, List<String>>> data;

		FakeSchema(Map<String, Map<String, List<String>>> data) {
			this.data = data;
		}

		@Override
		public List<String> schemas() {
			return List.copyOf(data.keySet());
		}

		@Override
		public String defaultSchema() {
			return "public";
		}

		@Override
		public List<Table> tables(String schema) {
			return data.getOrDefault(schema, Map.of()).keySet().stream().map(n -> new Table(n, n.startsWith("v_"))).toList();
		}

		@Override
		public List<String> columns(String schema, String table) {
			return data.getOrDefault(schema, Map.of()).getOrDefault(table, List.of());
		}
	}

	private static final SchemaModel SCHEMA = new FakeSchema(Map.of(
		"public", Map.of("orders", List.of("id", "customer_id", "total"), "customers", List.of("id", "name"),
			"Mixed Case", List.of("Key", "plain"), "v_open", List.of("id")),
		"sales", Map.of("invoices", List.of("number", "amount"))));

	/** Text with {@code |} at the cursor. Returns the inserted texts, or labels where they differ. */
	private static List<String> inserts(String marked) {
		return complete(marked).candidates().stream().map(Candidate::insert).toList();
	}

	private static Completion complete(String marked) {
		int cursor = marked.indexOf('|');
		return CompletionEngine.standard().complete(marked.replace("|", ""), cursor, SCHEMA);
	}

	@Test
	void keywordsMatchThePrefixAndFollowTheTypedCase() {
		assertThat(inserts("sel|")).contains("select").doesNotContain("SELECT", "from");
		assertThat(inserts("SEL|")).contains("SELECT").doesNotContain("select");
		assertThat(inserts("Sel|")).contains("SELECT");
		assertThat(inserts("|")).contains("SELECT", "FROM", "WHERE");
	}

	@Test
	void theRangeReplacesTheWholeWordAroundTheCursor() {
		var completion = complete("select * fr|om t");
		assertThat(completion.start()).isEqualTo(9);
		assertThat(completion.end()).isEqualTo(13);
		assertThat(completion.candidates()).extracting(Candidate::insert).contains("from");
	}

	@Test
	void unknownPrefixGivesNoCandidates() {
		assertThat(complete("zzzz|").candidates()).isEmpty();
	}

	@Test
	void onlyTheStatementUnderTheCursorCounts() {
		assertThat(inserts("select * from orders; select * from cu|")).containsExactly("customers");
		assertThat(inserts("select * from orders; sel|; select 1")).contains("select");
	}

	@Test
	void stringsAndCommentsGiveNoCandidates() {
		assertThat(complete("select 'sel|ect'").candidates()).isEmpty();
		assertThat(complete("select 1 -- sel|").candidates()).isEmpty();
		assertThat(complete("select /* sel| */ 1").candidates()).isEmpty();
	}

	@Test
	void afterFromJoinIntoUpdateAndTableComeTablesViewsAndSchemas() {
		assertThat(inserts("select * from |")).containsExactlyInAnyOrder("orders", "customers", "\"Mixed Case\"", "v_open",
			"public", "sales");
		assertThat(inserts("select * from orders o join |")).contains("customers", "sales");
		assertThat(inserts("insert into cu|")).containsExactly("customers");
		assertThat(inserts("update or|")).containsExactly("orders");
		assertThat(inserts("drop table v_|")).containsExactly("v_open");
		assertThat(inserts("select * from orders, cu|")).containsExactly("customers");
		assertThat(complete("select * from |").candidates()).extracting(Candidate::kind)
			.contains(Candidate.Kind.TABLE, Candidate.Kind.VIEW, Candidate.Kind.SCHEMA);
	}

	@Test
	void afterATableNameTheNextWordIsAKeywordNotATable() {
		assertThat(inserts("select * from orders wher|")).containsExactly("where");
	}

	@Test
	void afterSchemaDotComeTheTablesOfThatSchema() {
		assertThat(inserts("select * from sales.|")).containsExactly("invoices");
		assertThat(inserts("select * from sales.in|")).containsExactly("invoices");
		assertThat(complete("select * from sales.in|").start()).isEqualTo(20);
		assertThat(inserts("select * from nowhere.|")).isEmpty();
	}

	@Test
	void afterATableNameOrAliasDotComeItsColumns() {
		assertThat(inserts("select o.| from orders o")).containsExactly("id", "customer_id", "total");
		assertThat(inserts("select o.cu| from orders as o")).containsExactly("customer_id");
		assertThat(inserts("select c.| from orders o join customers c on c.id = o.customer_id")).containsExactly("id", "name");
		assertThat(inserts("select orders.t| from orders")).containsExactly("total");
		assertThat(inserts("select customers.| from orders, customers")).containsExactly("id", "name");
		assertThat(inserts("select sales.invoices.| from sales.invoices")).containsExactly("number", "amount");
		assertThat(inserts("select i.n| from sales.invoices i")).containsExactly("number");
		assertThat(inserts("select x.| from orders o")).isEmpty();
	}

	@Test
	void aliasesOfOtherStatementsAreNotUsed() {
		assertThat(inserts("select 1 from customers c; select c.| from orders o")).isEmpty();
	}

	@Test
	void afterSelectWhereOnGroupOrderAndSetComeColumnsOfTheFromTablesAndKeywords() {
		assertThat(inserts("select | from orders")).contains("id", "customer_id", "total", "SELECT", "COUNT").doesNotContain("name");
		assertThat(inserts("select * from orders where cu|")).contains("customer_id");
		assertThat(inserts("select * from orders o join customers c on c.id = o.customer_id where na|")).contains("name");
		assertThat(inserts("select count(*) from orders group by cu|")).contains("customer_id");
		assertThat(inserts("select * from orders order by to|")).contains("total");
		assertThat(inserts("update orders set to|")).contains("total");
		assertThat(inserts("select * from orders where id in (select id from customers) and to|")).contains("total")
			.doesNotContain("name");
		assertThat(inserts("select id, na| from orders, customers")).contains("name");
		assertThat(complete("select | from orders").candidates().get(0).kind()).isEqualTo(Candidate.Kind.COLUMN);
	}

	@Test
	void withoutFromTablesTheExpressionGetsKeywordsAndTables() {
		assertThat(inserts("select co|")).contains("count", "coalesce").doesNotContain("customers");
		assertThat(inserts("select cu|")).contains("customers", "current_date")
			.doesNotContain("customer_id");
		assertThat(inserts("select * from nowhere where cu|")).contains("customers");
	}

	@Test
	void namesThatNeedQuotesAreInsertedQuotedAndMatchedWithoutRegardToCase() {
		assertThat(inserts("select * from mix|")).containsExactly("\"Mixed Case\"");
		assertThat(inserts("select * from MIX|")).containsExactly("\"Mixed Case\"");
		assertThat(inserts("select * from \"Mix|")).containsExactly("\"Mixed Case\"");
		assertThat(inserts("select * from \"mix|\" where 1=1")).containsExactly("\"Mixed Case\"");
		assertThat(complete("select * from \"Mix|\" where 1=1").start()).isEqualTo(14);
		assertThat(complete("select * from \"Mix|\" where 1=1").end()).isEqualTo(19);
		assertThat(inserts("select * from \"ord|")).containsExactly("\"orders\"");
	}

	@Test
	void columnsOfQuotedTablesAndAliasesWork() {
		assertThat(inserts("select m.| from \"Mixed Case\" m")).containsExactly("\"Key\"", "plain");
		assertThat(inserts("select \"Mixed Case\".k| from \"Mixed Case\"")).containsExactly("\"Key\"");
		assertThat(inserts("select \"M\".| from \"Mixed Case\" \"M\"")).containsExactly("\"Key\"", "plain");
		assertThat(inserts("select M.| from \"Mixed Case\" m")).containsExactly("\"Key\"", "plain");
	}

	@Test
	void cursorInTheMiddleOfAWordUsesTheTextBeforeTheCursor() {
		assertThat(inserts("select * from or|ders")).containsExactly("orders");
		assertThat(inserts("select * from c|ders")).containsExactly("customers");
	}

	@Test
	void slashCommandsGetNoSqlCandidates() {
		assertThat(complete("/tab|").candidates()).isEmpty();
	}

	@Test
	void describeCompletesTablesAndViewsOfTheDefaultSchemaAndSchemas() {
		assertThat(inserts("/describe cu|")).containsExactly("customers");
		assertThat(inserts("/describe v|")).containsExactly("v_open");
		assertThat(inserts("/describe sa|")).containsExactly("sales");
	}

	@Test
	void tablesCompletesSchemasOnly() {
		assertThat(inserts("/tables sa|")).containsExactly("sales");
		assertThat(inserts("/tables or|")).isEmpty();
	}

	@Test
	void otherSlashPositionsGiveNoSchemaCandidates() {
		assertThat(inserts("/describe orders cu|")).isEmpty();
		assertThat(inserts("/nosuch cu|")).isEmpty();
		assertThat(inserts("/desc|")).isEmpty();
	}

	@Test
	void describeCompletesTablesOfAQualifyingSchemaAndReplacesOnlyTheNamePart() {
		var completion = complete("/describe sales.in|");
		assertThat(completion.candidates()).extracting(Candidate::insert).containsExactly("invoices");
		assertThat(completion.start()).isEqualTo(16);
		assertThat(completion.end()).isEqualTo(18);
	}

	@Test
	void describeInsertsQuotedNamesAndCompletesInsideQuotes() {
		assertThat(inserts("/describe mi|")).containsExactly("\"Mixed Case\"");
		assertThat(inserts("/describe \"Mixed C|")).containsExactly("\"Mixed Case\"");
		assertThat(inserts("/describe \"public\".cu|")).containsExactly("customers");
	}
}
