package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.datev.dbui.config.ConnectionParameters;
import com.datev.dbui.config.Settings;

/** Session seam for /history. No database: inputs that need one fail, but they still enter the history. */
class HistoryCommandSessionTest {

	@TempDir Path directory;

	private Session session() throws Exception {
		var parameters = new ConnectionParameters("jdbc:postgresql://localhost:1/none", Map.of(), false);
		var context = new SessionContext(directory.resolve("state"), Settings.defaults(), parameters, null, name -> null);
		return new Session(null, SessionDirectory.create(context.stateBase()), context);
	}

	private static Session.Update submit(Session session, String input) throws Exception {
		return session.submit(input).get(5, TimeUnit.SECONDS);
	}

	@Test
	void historyListsInputsAndIsNotStoredItself() throws Exception {
		try (Session session = session()) {
			submit(session, "select 1");
			submit(session, "/limit 5");
			var update = submit(session, "/history");
			assertThat(update.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(update.messageBlocks().getLast().text()).isEqualTo("1  select 1\n2  /limit 5");
			assertThat(update.editorText()).isNull();
			submit(session, "/history 1");
			submit(session, "/history 9");
			assertThat(session.history()).containsExactly("select 1", "/limit 5");
			submit(session, "/history clear");
			submit(session, "select 2");
			assertThat(session.history()).containsExactly("select 2");
		}
	}

	@Test
	void aNumberLoadsTheEntryIntoTheEditorThroughTheUpdate() throws Exception {
		try (Session session = session()) {
			submit(session, "select 1");
			submit(session, "select\n 2");
			var update = submit(session, "/history 2");
			assertThat(update.editorText()).isEqualTo("select\n 2");
			assertThat(update.messageBlocks().getLast().kind()).isEqualTo(MessageBlock.Kind.INPUT);
			assertThat(submit(session, "/history 9").editorText()).isNull();
			assertThat(submit(session, "select 3").editorText()).isNull();
		}
	}

	@Test
	void clearEmptiesTheHistoryInMemoryAndOnDiskAndNumberingRestarts() throws Exception {
		try (Session session = session()) {
			submit(session, "select 1");
			submit(session, "select 2");
			var update = submit(session, "/history clear");
			assertThat(update.messageBlocks().getLast().text()).isEqualTo("History cleared (2 entries).");
			assertThat(session.history()).isEmpty();
			assertThat(Files.readString(session.directory().historyFile())).isEmpty();
			submit(session, "select 3");
			assertThat(submit(session, "/history").messageBlocks().getLast().text()).isEqualTo("1  select 3");
		}
	}

	@Test
	void helpListsHistoryAndCompletionOffersClear() throws Exception {
		try (Session session = session()) {
			assertThat(submit(session, "/help commands").messageBlocks().getLast().text())
				.contains("/history [<number>|clear] —").doesNotContain("[[");
			assertThat(session.slashCommands().completionCandidates("history")).containsExactly("clear");
			assertThat(session.complete("/history cl", 11).get(5, TimeUnit.SECONDS).candidates())
				.extracting(com.datev.dbui.completion.Candidate::insert).containsExactly("clear");
		}
	}
}
