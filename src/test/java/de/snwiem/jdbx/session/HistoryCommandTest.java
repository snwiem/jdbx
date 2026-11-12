package de.snwiem.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class HistoryCommandTest {

	private static HistoryCommand.Outcome run(String argument, List<String> entries) {
		return HistoryCommand.run(argument, entries, true, 0);
	}

	@Test
	void listNumbersEntriesFromOneOldestFirstAndMarksMoreLines() {
		var outcome = run("", List.of("select 1", "select\n  2\nfrom t", "/describe orders"));
		assertThat(outcome.blocks()).hasSize(1);
		assertThat(outcome.blocks().getFirst().kind()).isEqualTo(MessageBlock.Kind.INFO);
		assertThat(outcome.blocks().getFirst().text()).isEqualTo("1  select 1\n2  select …\n3  /describe orders");
		assertThat(outcome.editorText()).isNull();
		assertThat(outcome.clear()).isFalse();
	}

	@Test
	void aLongFirstLineIsCutByMaxWidthAndZeroMeansNoCut() {
		var entries = List.of("select 1234567890", "select 1\nfrom t");
		assertThat(HistoryCommand.run("", entries, true, 8).blocks().getFirst().text()).isEqualTo("1  select …\n2  select 1 …");
		assertThat(HistoryCommand.run("", entries, true, 0).blocks().getFirst().text()).isEqualTo("1  select 1234567890\n2  select 1 …");
	}

	@Test
	void numbersAreRightAlignedAndCarriageReturnsEndTheFirstLine() {
		var entries = new java.util.ArrayList<String>();
		for (int i = 1; i <= 10; i++) {
			entries.add(i == 10 ? "ten\r\nmore" : "e" + i);
		}
		assertThat(run("", entries).blocks().getFirst().text()).startsWith(" 1  e1\n 2  e2\n").endsWith("\n10  ten …");
	}

	private static final List<String> THREE = List.of("select 1", "select\n  2\nfrom t", "/describe orders");

	@Test
	void aNumberLoadsTheWholeEntryIntoTheEditorAndShowsNothing() {
		var outcome = run("2", THREE);
		assertThat(outcome.editorText()).isEqualTo("select\n  2\nfrom t");
		assertThat(outcome.blocks()).isEmpty();
		assertThat(outcome.clear()).isFalse();
		assertThat(run(" 3 ", THREE).editorText()).isEqualTo("/describe orders");
	}

	@Test
	void badArgumentsAndNumbersOutOfRangeAreErrors() {
		for (String bad : List.of("0", "-1", "abc", "1 2", "clear now", "01x", "1.5")) {
			var blocks = run(bad, THREE).blocks();
			assertThat(blocks).hasSize(1);
			assertThat(blocks.getFirst().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(blocks.getFirst().text()).isEqualTo("Usage: /history [<number>|clear]");
		}
		for (String outside : List.of("4", "99", "99999999999999999999")) {
			var outcome = run(outside, THREE);
			assertThat(outcome.blocks().getFirst().kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(outcome.blocks().getFirst().text()).isEqualTo("No history entry " + outside + ". The history has 3 entries.");
			assertThat(outcome.editorText()).isNull();
		}
	}

	@Test
	void anEmptyOrDisabledHistoryGivesInfo() {
		assertThat(run("", List.of()).blocks().getFirst().text()).isEqualTo("The history is empty.");
		assertThat(run("clear", List.of()).blocks().getFirst().text()).isEqualTo("The history is empty.");
		assertThat(run("clear", List.of()).clear()).isFalse();
		for (String argument : List.of("", "1", "clear")) {
			var outcome = HistoryCommand.run(argument, THREE, false, 0);
			assertThat(outcome.blocks().getFirst().kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(outcome.blocks().getFirst().text()).isEqualTo("The history is off.");
			assertThat(outcome.editorText()).isNull();
			assertThat(outcome.clear()).isFalse();
		}
	}

	@Test
	void clearAsksTheSessionToEmptyTheHistoryAndSaysHowManyEntries() {
		var outcome = run("clear", THREE);
		assertThat(outcome.clear()).isTrue();
		assertThat(outcome.blocks().getFirst().text()).isEqualTo("History cleared (3 entries).");
		assertThat(run("clear", List.of("select 1")).blocks().getFirst().text()).isEqualTo("History cleared (1 entry).");
		assertThat(run("2", List.of("select 1")).blocks().getFirst().text()).isEqualTo("No history entry 2. The history has 1 entry.");
	}
}
