package de.snwiem.jdbx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class StatementSplitterTest {

	private static List<String> split(String input) {
		return new StatementSplitter().split(input);
	}

	@Test
	void splitsAtSemicolons() {
		assertThat(split("select 1; select 2;")).containsExactly("select 1", "select 2");
	}

	@Test
	void trailingSemicolonIsOptional() {
		assertThat(split("select 1; select 2")).containsExactly("select 1", "select 2");
	}

	@Test
	void skipsEmptyStatements() {
		assertThat(split(" ;; select 1 ;\n; ")).containsExactly("select 1");
		assertThat(split("")).isEmpty();
	}

	@Test
	void doesNotSplitInsideSingleQuotedStrings() {
		assertThat(split("select 'a;b'; select 2")).containsExactly("select 'a;b'", "select 2");
	}

	@Test
	void doubledSingleQuoteStaysInsideString() {
		assertThat(split("select 'it''s;ok'; select 2")).containsExactly("select 'it''s;ok'", "select 2");
	}

	@Test
	void backslashEscapesQuoteOnlyInEStrings() {
		assertThat(split("select E'a\\';b'; select 2")).containsExactly("select E'a\\';b'", "select 2");
		assertThat(split("select 'a\\'; select 2")).containsExactly("select 'a\\'", "select 2");
	}

	@Test
	void doesNotSplitInsideQuotedIdentifiers() {
		assertThat(split("select 1 as \"a;b\"; select 2")).containsExactly("select 1 as \"a;b\"", "select 2");
		assertThat(split("select 1 as \"a\"\";b\"; select 2")).containsExactly("select 1 as \"a\"\";b\"", "select 2");
	}

	@Test
	void doesNotSplitInsideLineComments() {
		assertThat(split("select 1 -- a; b\n; select 2")).containsExactly("select 1 -- a; b", "select 2");
	}

	@Test
	void doesNotSplitInsideBlockComments() {
		assertThat(split("select /* a; b */ 1; select 2")).containsExactly("select /* a; b */ 1", "select 2");
	}

	@Test
	void blockCommentsNest() {
		assertThat(split("select /* a /* b; */ c; */ 1; select 2")).containsExactly("select /* a /* b; */ c; */ 1",
				"select 2");
	}

	@Test
	void doesNotSplitInsideDollarQuotedStrings() {
		assertThat(split("do $$ begin perform 1; end $$; select 2")).containsExactly("do $$ begin perform 1; end $$",
				"select 2");
		assertThat(split("do $body$ a; $$ b; $body$; select 2")).containsExactly("do $body$ a; $$ b; $body$",
				"select 2");
	}

	@Test
	void dollarParameterIsNotADollarQuote() {
		assertThat(split("select $1; select 2")).containsExactly("select $1", "select 2");
	}

	@Test
	void commentOnlyInputYieldsNoStatement() {
		assertThat(split("-- nothing\n")).isEmpty();
	}
}
