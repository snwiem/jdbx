package de.snwiem.jdbx.completion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.snwiem.jdbx.completion.Candidate.Kind;

class CompletionTest {

	private static Candidate c(String insert) {
		return new Candidate(insert, insert, Kind.TABLE);
	}

	@Test
	void applyReplacesTheRangeAndPutsTheCursorAfterTheInsertedText() {
		var completion = new Completion(14, 16, List.of(c("customers")));
		var edit = completion.apply("select * from cu where 1", completion.candidates().get(0));
		assertThat(edit.text()).isEqualTo("select * from customers where 1");
		assertThat(edit.cursor()).isEqualTo(23);
	}

	@Test
	void commonPrefixIsInsertedWhenLongerThanTheTypedWord() {
		var completion = new Completion(14, 15, List.of(c("customers"), c("customer_notes"), c("custom")));
		var edit = completion.applyCommonPrefix("select * from c");
		assertThat(edit.text()).isEqualTo("select * from custom");
		assertThat(edit.cursor()).isEqualTo(20);
		assertThat(completion.afterCommonPrefix(edit).end()).isEqualTo(20);
	}

	@Test
	void commonPrefixIsNullWhenItAddsNothing() {
		var completion = new Completion(14, 16, List.of(c("cust_a"), c("cust_b"), c("cu")));
		assertThat(completion.applyCommonPrefix("select * from cu")).isNull();
		assertThat(new Completion(0, 0, List.of(c("a"), c("b"))).applyCommonPrefix("")).isNull();
	}
}
