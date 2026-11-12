package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Session seam for the schema Slash commands. */
@Testcontainers
class SchemaCommandsIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	@TempDir
	Path stateDir;

	private Session session() throws Exception {
		var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		return new Session(connection, SessionDirectory.create(stateDir));
	}

	private static MessageBlock last(Session.Update update) {
		return update.messageBlocks().getLast();
	}

	@Test
	void schemaListsSchemasAndHidesSystemSchemasUnlessAllIsGiven() throws Exception {
		try (Session session = session()) {
			var plain = last(session.submit("/schema").get(5, TimeUnit.SECONDS)).result();
			assertThat(plain.columns()).containsExactly("schema");
			assertThat(plain.rows()).extracting(r -> r.getFirst()).contains("public").doesNotContain("pg_catalog");
			var all = last(session.submit("/schema all").get(5, TimeUnit.SECONDS)).result();
			assertThat(all.rows()).extracting(r -> r.getFirst()).contains("public", "pg_catalog", "information_schema");
		}
	}

	@Test
	void tablesAndDescribeShowTheTableOfTheCurrentSchema() throws Exception {
		try (Session session = session()) {
			session.submit("create table sc_t (id integer primary key, label text not null default 'x')").get(5, TimeUnit.SECONDS);
			var tables = session.submit("/tables").get(5, TimeUnit.SECONDS);
			assertThat(tables.messageBlocks()).extracting(MessageBlock::kind).endsWith(MessageBlock.Kind.INPUT, MessageBlock.Kind.RESULT);
			assertThat(last(tables).result().columns()).containsExactly("name", "type", "remarks");
			assertThat(last(tables).result().rows()).contains(java.util.Arrays.asList("sc_t", "TABLE", ""));

			var described = session.submit("/describe SC_T").get(5, TimeUnit.SECONDS);
			var blocks = described.messageBlocks();
			var fromDescribe = blocks.subList(blocks.size() - 1 - blocks.reversed().stream().takeWhile(b -> b.kind() != MessageBlock.Kind.INPUT).toList().size() + 1, blocks.size());
			var results = fromDescribe.stream().filter(b -> b.kind() == MessageBlock.Kind.RESULT).toList();
			assertThat(results.getFirst().result()).isNull();
			assertThat(results.getFirst().text()).contains("name", "type", "nullable", "default", "position", "remarks")
				.containsPattern("label\\s+\\| text\\s+\\| NO\\s+\\| 'x'::text\\s+\\| 2");
			assertThat(described.messageBlocks()).extracting(MessageBlock::text).contains("Primary key", "Indexes");
		}
	}

	@Test
	void quotedSchemaAndTableNamesKeepTheirSqlQuotesAndInternalWhitespace() throws Exception {
		try (Session session = session()) {
			session.submit("create schema \"Mixed Schema\"; create table \"Mixed Schema\".\"Order  Items\" (\"a\"\"b\" int)").get(5, TimeUnit.SECONDS);
			assertThat(last(session.submit("/tables \"Mixed Schema\"").get(5, TimeUnit.SECONDS)).result().rows())
				.extracting(row -> row.getFirst()).containsExactly("Order  Items");
			var described = last(session.submit("/describe \"Mixed Schema\".\"Order  Items\"").get(5, TimeUnit.SECONDS));
			assertThat(described.kind()).isEqualTo(MessageBlock.Kind.RESULT);
			assertThat(described.result().rows().getFirst().getFirst()).isEqualTo("a\"b");
		}
	}

	@Test
	void refreshReloadsTheCache() throws Exception {
		try (Session session = session()) {
			session.submit("/tables").get(5, TimeUnit.SECONDS);
			session.submit("create table sc_late (id integer)").get(5, TimeUnit.SECONDS);
			assertThat(last(session.submit("/tables").get(5, TimeUnit.SECONDS)).result().rows())
				.extracting(r -> r.getFirst()).doesNotContain("sc_late");
			assertThat(last(session.submit("/refresh").get(5, TimeUnit.SECONDS)).kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(last(session.submit("/tables").get(5, TimeUnit.SECONDS)).result().rows())
				.extracting(r -> r.getFirst()).contains("sc_late");
		}
	}

	@Test
	void unknownTableAndUnknownSchemaGiveAnErrorBandAndTheSessionStaysUsable() throws Exception {
		try (Session session = session()) {
			var unknown = last(session.submit("/describe no_such").get(5, TimeUnit.SECONDS));
			assertThat(unknown.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(unknown.text()).isEqualTo("Unknown table: no_such");
			assertThat(last(session.submit("/tables nowhere").get(5, TimeUnit.SECONDS)).text()).isEqualTo("Unknown schema: nowhere");
			assertThat(last(session.submit("/describe \"open").get(5, TimeUnit.SECONDS)).kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(last(session.submit("select 1").get(5, TimeUnit.SECONDS)).kind()).isEqualTo(MessageBlock.Kind.RESULT);
		}
	}

	@Test
	void describeWithoutArgumentListsSchemasThenTablesAndViewsOfTheCurrentSchema() throws Exception {
		try (Session session = session()) {
			session.submit("create schema ov_schema; create table ov_table (id int); create view ov_view as select 1 as x")
				.get(5, TimeUnit.SECONDS);
			for (String input : new String[] { "/describe", "/describe   " }) {
				var result = last(session.submit(input).get(5, TimeUnit.SECONDS)).result();
				assertThat(result.columns()).containsExactly("name", "type");
				var rows = result.rows();
				assertThat(rows).contains(java.util.Arrays.asList("public", "schema"), java.util.Arrays.asList("ov_schema", "schema"),
					java.util.Arrays.asList("ov_table", "table"), java.util.Arrays.asList("ov_view", "view"));
				assertThat(rows).doesNotContain(java.util.Arrays.asList("pg_catalog", "schema"));
				assertThat(rows.indexOf(java.util.Arrays.asList("ov_schema", "schema")))
					.isLessThan(rows.indexOf(java.util.Arrays.asList("ov_table", "table")));
			}
		}
	}
}
