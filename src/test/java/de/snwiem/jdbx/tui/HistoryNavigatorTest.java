package de.snwiem.jdbx.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class HistoryNavigatorTest {

	@Test
	void upWalksBackThroughEntriesAndDownReturnsToTheDraft() {
		var navigator = new HistoryNavigator();
		navigator.entries(List.of("one", "two", "three"));
		assertThat(navigator.previous("draft")).contains("three");
		assertThat(navigator.previous("ignored")).contains("two");
		assertThat(navigator.previous("ignored")).contains("one");
		assertThat(navigator.previous("ignored")).isEmpty();
		assertThat(navigator.next()).contains("two");
		assertThat(navigator.next()).contains("three");
		assertThat(navigator.next()).contains("draft");
		assertThat(navigator.next()).isEmpty();
	}

	@Test
	void emptyHistoryGoesNowhereAndNewEntriesStartAtTheNewest() {
		var navigator = new HistoryNavigator();
		assertThat(navigator.previous("x")).isEmpty();
		navigator.entries(List.of("a", "b"));
		navigator.previous("");
		navigator.entries(List.of("a", "b", "c"));
		assertThat(navigator.previous("")).contains("c");
	}
}
