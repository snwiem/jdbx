package de.snwiem.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.snwiem.jdbx.config.Settings;

class CopyCommandSessionTest {

	@TempDir Path directory;

	@Test
	void copyAcceptsNoArgumentAndHelpExplainsItsTerminalAndSizeRequirements() throws Exception {
		var sent = new ArrayList<String>();
		try (var session = new Session(SessionDirectory.create(directory), Settings.defaults())) {
			session.clipboard(sent::add);

			var error = session.submit("/copy extra").get(5, TimeUnit.SECONDS).messageBlocks().getLast();
			var help = session.submit("/help commands").get(5, TimeUnit.SECONDS).messageBlocks().getLast();

			assertThat(error.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			assertThat(error.text()).isEqualTo("Usage: /copy\nUnexpected argument: extra");
			assertThat(sent).isEmpty();
			assertThat(help.text()).contains("/copy —", "current Format and Orientation", "OSC 52", "64 KB", "UTF-8", "/save");
			assertThat(session.slashCommands().commands()).extracting(SlashCommand::name).contains("copy");
			assertThat(session.slashCommands().completionCandidates("copy")).isEmpty();
		}
	}

	@Test
	void copyWithoutAQueryResultNeedsNoConnectionAndDoesNotWriteToTheClipboard() throws Exception {
		var sent = new ArrayList<String>();
		try (var session = new Session(SessionDirectory.create(directory), Settings.defaults())) {
			session.clipboard(sent::add);

			var band = session.submit("/copy").get(5, TimeUnit.SECONDS).messageBlocks().getLast();

			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(band.text()).contains("No non-empty query Result", "Nothing was sent");
			assertThat(sent).isEmpty();
		}
	}
}
