package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.datev.dbui.config.Settings;

/** Session seam for /width. No database is needed to change and report the setting. */
class WidthCommandSessionTest {

	@TempDir Path directory;

	private Session session() throws Exception {
		return new Session(SessionDirectory.create(directory), Settings.defaults());
	}

	private static MessageBlock last(Session session, String input) throws Exception {
		return session.submit(input).get(5, TimeUnit.SECONDS).messageBlocks().getLast();
	}

	@Test
	void widthChangesTheSettingForTheRunningSession() throws Exception {
		try (Session session = session()) {
			assertThat(session.settings().maxWidth()).isEqualTo(Settings.DEFAULT_MAX_WIDTH);
			assertThat(last(session, "/width 25").text()).isEqualTo("Maximum cell width: 25");
			assertThat(session.settings().maxWidth()).isEqualTo(25);
			assertThat(last(session, "/width off").text()).isEqualTo("Maximum cell width: off");
			assertThat(session.settings().maxWidth()).isZero();
			assertThat(last(session, "/width 12").text()).isEqualTo("Maximum cell width: 12");
			assertThat(last(session, "/width 0").text()).isEqualTo("Maximum cell width: off");
			assertThat(session.settings().maxWidth()).isZero();
		}
	}

	@Test
	void badArgumentsAreErrorsAndKeepTheSetting() throws Exception {
		try (Session session = session()) {
			for (String input : new String[] { "/width", "/width -1", "/width abc", "/width 1.5", "/width 1 2" }) {
				assertThat(last(session, input).kind()).as(input).isEqualTo(MessageBlock.Kind.ERROR);
			}
			assertThat(last(session, "/width -1").text()).contains("Use an integer from 0 to 2147483647, or off");
			assertThat(session.settings().maxWidth()).isEqualTo(Settings.DEFAULT_MAX_WIDTH);
		}
	}

	@Test
	void helpListsWidthAndCompletionOffersOff() throws Exception {
		try (Session session = session()) {
			assertThat(last(session, "/help").text()).contains("/width <n|off> —");
			assertThat(session.slashCommands().completionCandidates("width")).containsExactly("off");
		}
	}
}
