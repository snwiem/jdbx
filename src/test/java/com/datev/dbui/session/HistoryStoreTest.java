package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryStoreTest {

	@Test
	void entriesRoundTripIncludingMultiLineAndBackslashes(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("history");
		var store = HistoryStore.open(file, true);
		store.add("select 1");
		store.add("select 2\nfrom t\r\nwhere a = '\\n'");
		store.add("/format json");
		assertThat(Files.readAllLines(file)).hasSize(3);
		assertThat(HistoryStore.open(file, true).entries())
			.containsExactly("select 1", "select 2\nfrom t\r\nwhere a = '\\n'", "/format json");
	}

	@Test
	void linesThatStartWithASpaceBlankLinesResumeAndQuitAreNotSaved(@TempDir Path dir) throws Exception {
		var store = HistoryStore.open(dir.resolve("history"), true);
		store.add(" select secret");
		store.add("   ");
		store.add("/resume 3");
		store.add("/quit");
		store.add("select 1");
		assertThat(store.entries()).containsExactly("select 1");
		assertThat(Files.readAllLines(dir.resolve("history"))).containsExactly("select 1");
	}

	@Test
	void disabledHistoryKeepsNothingAndWritesNoFile(@TempDir Path dir) {
		var store = HistoryStore.open(dir.resolve("history"), false);
		store.add("select 1");
		assertThat(store.entries()).isEmpty();
		assertThat(dir.resolve("history")).doesNotExist();
	}

	@Test
	void aFailingWriteDisablesTheStoreInsteadOfThrowing(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("history");
		Files.createDirectory(file); // a directory where the file should be: every write fails
		var store = HistoryStore.open(file, true);
		store.add("select 1");
		assertThat(store.entries()).containsExactly("select 1");
		assertThat(store.failure()).isNotNull();
	}

	@Test
	void anUnreadableFileGivesAnEmptyHistory(@TempDir Path dir) {
		assertThat(HistoryStore.open(dir.resolve("none"), true).entries()).isEqualTo(List.of());
	}
}
