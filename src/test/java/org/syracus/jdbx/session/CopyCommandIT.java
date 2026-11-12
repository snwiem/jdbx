package org.syracus.jdbx.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CopyCommandIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	@TempDir Path directory;

	private Session session() throws Exception {
		return new Session(DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()),
			SessionDirectory.create(directory.resolve("state")));
	}

	private static MessageBlock submit(Session session, String input) throws Exception {
		return session.submit(input).get(10, TimeUnit.SECONDS).messageBlocks().getLast();
	}

	@Test
	void anEmptyQueryReplacesTheEarlierResultAndANewQueryEnablesCopyAgain() throws Exception {
		var sent = new ArrayList<String>();
		try (Session session = session()) {
			session.clipboard(sent::add);
			submit(session, "/format csv");
			submit(session, "/limit 1");
			submit(session, "select n as id from generate_series(1,2) n");
			submit(session, "select 7 as id where false");
			assertThat(submit(session, "/copy").text()).contains("No non-empty query Result", "Nothing was sent");
			assertThat(sent).isEmpty();
			submit(session, "create temporary table copy_empty(id int); select 8 as id");

			var feedback = submit(session, "/copy");

			assertThat(sent).containsExactly("\u001b]52;c;aWQKOAo=\u0007");
			assertThat(feedback.text()).isEqualTo("Sent 1 rows to the terminal clipboard (csv).");
		}
	}

	@Test
	void infoErrorsDumpAndTranscriptEvictionNeverBecomeClipboardContent() throws Exception {
		var sent = new ArrayList<String>();
		try (Session session = session()) {
			session.clipboard(sent::add);
			submit(session, "/format csv");
			submit(session, "select 1 as id; select 7 as id");
			for (int i = 0; i < TranscriptMemory.MAX_BLOCKS; i++) submit(session, "/limit 1000");
			assertThat(session.messageBlocks()).noneMatch(block -> block.kind() == MessageBlock.Kind.RESULT);
			assertThat(submit(session, "select missing_copy_column").kind()).isEqualTo(MessageBlock.Kind.ERROR);
			submit(session, "/dump " + directory.resolve("dump.csv") + " select 9 as id");
			submit(session, "/unknown");

			var feedback = submit(session, "/copy");

			assertThat(sent).containsExactly("\u001b]52;c;aWQKNwo=\u0007");
			assertThat(feedback.text()).isEqualTo("Sent 1 rows to the terminal clipboard (csv).");
		}
	}

	@Test
	void schemaResultsDoNotCountAsQueryResultsForCopy() throws Exception {
		var sent = new ArrayList<String>();
		try (Session session = session()) {
			session.clipboard(sent::add);
			submit(session, "select 7 as id");
			assertThat(submit(session, "/describe").kind()).isEqualTo(MessageBlock.Kind.RESULT);

			var feedback = submit(session, "/copy");

			assertThat(feedback.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(feedback.text()).contains("No non-empty query Result", "Nothing was sent");
			assertThat(sent).isEmpty();
		}
	}

	@Test
	void aLastStatementWithoutAResultRefusesCopyButDoesNotChangeSaveRetention() throws Exception {
		var sent = new ArrayList<String>();
		try (Session session = session()) {
			session.clipboard(sent::add);
			submit(session, "/format csv");
			submit(session, "select 7 as id; create temporary table copy_target(id int)");

			var feedback = submit(session, "/copy");

			assertThat(feedback.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(feedback.text()).contains("No non-empty query Result", "Nothing was sent");
			assertThat(sent).isEmpty();
			Path saved = directory.resolve("still-held.csv");
			submit(session, "/save " + saved);
			assertThat(Files.readString(saved)).isEqualTo("id\n7\n");
		}
	}

	@Test
	void copyUsesTheHeldQueryWithCurrentOutputSettingsAndItsOriginalRowLimitLikeSave() throws Exception {
		var sent = new ArrayList<String>();
		try (Session session = session()) {
			session.clipboard(sent::add);
			submit(session, "/limit 2");
			submit(session, "select n as id from generate_series(7,10) n");
			submit(session, "/limit 50");
			submit(session, "/format csv");
			submit(session, "/orientation vertical");
			Path saved = directory.resolve("held.csv");
			submit(session, "/save " + saved);

			var feedback = submit(session, "/copy");

			assertThat(sent).containsExactly("\u001b]52;c;aWQsNyw4Cg==\u0007");
			assertThat(Files.readString(saved)).isEqualTo("id,7,8\n");
			assertThat(feedback.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(feedback.text()).isEqualTo("Sent 2 rows to the terminal clipboard (csv), truncated at 2 rows (Row limit).");
		}
	}
}
