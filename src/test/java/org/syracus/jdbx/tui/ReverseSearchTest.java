package org.syracus.jdbx.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReverseSearchTest {

	private final List<String> entries = List.of("select 1", "insert into t values (1)", "select * from t", "/format json");

	@Test
	void typingFindsTheNewestMatchAndCtrlRMovesToOlderOnes() {
		var search = new ReverseSearch(entries);
		assertThat(search.match()).isNull();
		search.type('t');
		assertThat(search.match()).isEqualTo("/format json");
		search.type('/');
		assertThat(search.match()).isNull();
		search.backspace();
		assertThat(search.query()).isEqualTo("t");
		search.type(' ');
		search.type('1');
		assertThat(search.match()).isEqualTo("select 1");
	}

	@Test
	void searchIsCaseInsensitiveAndRepeatedCtrlRStepsBackThroughMatches() {
		var search = new ReverseSearch(entries);
		"SELECT".chars().forEach(c -> search.type((char) c));
		assertThat(search.match()).isEqualTo("select * from t");
		search.older();
		assertThat(search.match()).isEqualTo("select 1");
		search.older();
		assertThat(search.match()).isEqualTo("select 1"); // no older match: stays
		assertThat(search.failing()).isFalse();
	}

	@Test
	void aQueryWithoutMatchFails() {
		var search = new ReverseSearch(entries);
		search.type('z');
		assertThat(search.failing()).isTrue();
		assertThat(search.match()).isNull();
	}
}
