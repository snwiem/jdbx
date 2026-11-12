package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.syracus.jdbx.BuildInfo;
import org.syracus.jdbx.completion.Candidate;
import org.syracus.jdbx.config.Settings;

/** Session seam for /help. No database is needed. */
class HelpCommandSessionTest {

	@TempDir Path directory;

	private Session session() throws Exception {
		return new Session(SessionDirectory.create(directory), Settings.defaults());
	}

	private static MessageBlock last(Session session, String input) throws Exception {
		return session.submit(input).get(5, TimeUnit.SECONDS).messageBlocks().getLast();
	}

	@Test
	void helpShowsTheOverviewWithEveryCommandNameAndNoCommandIsWithoutAGroup() throws Exception {
		try (Session session = session()) {
			var help = last(session, "/help");
			assertThat(help.kind()).isEqualTo(MessageBlock.Kind.INFO);
			// Issue 0040 wires the current build version into the overview; command and key topics do not change.
			assertThat(help.text()).startsWith("dbui " + BuildInfo.current().versionText()
				+ ". Use /help commands, /help keys, or /help <word>.")
				.contains("Essential keys", "Enter", "Ctrl-R");
			for (var command : session.slashCommands().commands()) {
				assertThat(help.text()).contains("/" + command.name());
				assertThat(command.group()).as(command.name()).isNotEqualTo("Other");
			}
			assertThat(help.text()).doesNotContain(" — ");
		}
	}

	@Test
	void helpCommandsKeysAndAWordGiveTheirTopics() throws Exception {
		try (Session session = session()) {
			assertThat(last(session, "/help commands").text()).startsWith("Slash commands\n\nQuery and results\n")
				.contains("\nSchema\n", "\nHistory and Sessions\n", "\nConnection\n", "\nGeneral\n")
				.contains("/history [<number>|clear] — ", "/width <n|off> — ");
			assertThat(last(session, "/help keys").text()).startsWith("Key bindings\n\nEditing\n").contains("\nDialogs\n", "Legacy terminals");
			var word = last(session, "/help history").text();
			assertThat(word).contains("/history [<number>|clear] — ", "Ctrl-R").doesNotContain("/format");
			assertThat(last(session, "/help nothing-like-this").text())
				.isEqualTo("No help for \"nothing-like-this\". Try /help commands or /help keys.");
			assertThat(last(session, "/help").kind()).isEqualTo(MessageBlock.Kind.INFO);
		}
	}

	@Test
	void helpCompletionOffersTopicsAndCommandNamesButNoKeyNames() throws Exception {
		try (Session session = session()) {
			var all = session.complete("/help ", 6).get(5, TimeUnit.SECONDS).candidates();
			assertThat(all).extracting(Candidate::insert).contains("commands", "keys", "describe", "history", "help", "quit")
				.doesNotContain("ctrl-r", "Ctrl-R", "tab", "/history");
			assertThat(session.complete("/help hi", 8).get(5, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("history");
			assertThat(session.complete("/help k", 7).get(5, TimeUnit.SECONDS).candidates())
				.extracting(Candidate::insert).containsExactly("keys");
		}
	}
}
