package com.datev.dbui.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Session seam: Format, Orientation and the text retained in the Transcript. */
@Testcontainers
class OutputSwitchingIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	@TempDir
	Path stateDir;

	private Session session() throws Exception {
		return new Session(DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()),
			SessionDirectory.create(stateDir));
	}

	private static MessageBlock submit(Session session, String input) throws Exception {
		return session.submit(input).get(5, TimeUnit.SECONDS).messageBlocks().getLast();
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({ "csv,'id,name\n7,Alice\n'", "tsv,'id\tname\n7\tAlice\n'",
		"markdown,'| id | name |\n| --- | --- |\n| 7 | Alice |\n'",
		"json-pretty,'[\n  {\n    \"id\": 7,\n    \"name\": \"Alice\"\n  }\n]\n'" })
	void everyFormatUsesTheSharedFormatterForTheNextResult(String format, String expected) throws Exception {
		try (Session session = session()) {
			assertThat(submit(session, "/format " + format).kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(submit(session, "select 7 as id, 'Alice' as name").text()).isEqualTo(expected);
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "/format xml", "/format", "/format json extra", "/orientation diagonal", "/orientation" })
	void invalidOutputValuesListTheValidValuesAndLeaveTheSettingsUnchanged(String input) throws Exception {
		try (Session session = session()) {
			var band = submit(session, input);
			assertThat(band.kind()).isEqualTo(MessageBlock.Kind.ERROR);
			if (input.startsWith("/format")) {
				assertThat(band.text()).contains("table", "markdown", "csv", "tsv", "json", "json-pretty");
			}
			else {
				assertThat(band.text()).contains("horizontal", "vertical");
			}
			assertThat(session.settings().format()).isEqualTo("table");
			assertThat(session.settings().orientation()).isEqualTo("horizontal");
		}
	}

	@Test
	void schemaResultsUseTheCurrentFormatAndOrientationIncludingDescribeTables() throws Exception {
		try (Session session = session()) {
			submit(session, "create table output_schema (id integer primary key)");
			submit(session, "/format json");
			assertThat(submit(session, "/schema").text()).contains("{\"schema\":\"public\"}");
			assertThat(submit(session, "/tables public").text()).contains("{\"name\":\"output_schema\",\"type\":\"TABLE\"");
			submit(session, "/format csv");
			submit(session, "/orientation vertical");
			int before = session.messageBlocks().size();
			submit(session, "/describe output_schema");
			var results = session.messageBlocks().subList(before, session.messageBlocks().size()).stream()
				.filter(block -> block.kind() == MessageBlock.Kind.RESULT).toList();
			assertThat(results).hasSize(3);
			assertThat(results.getFirst().text()).startsWith("name,id\ntype,int4\nnullable,NO\n");
			assertThat(results.get(1).text()).isEqualTo("name,output_schema_pkey\ncolumns,id\n");
			assertThat(results.getLast().text()).isEqualTo("name,output_schema_pkey\ncolumns,id\nunique,YES\n");
		}
	}

	@Test
	void orientationChangesTheNextResultAndConfirmsTheSetting() throws Exception {
		try (Session session = session()) {
			var changed = submit(session, "/orientation vertical");
			assertThat(changed.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(changed.text()).isEqualTo("Orientation: vertical");
			assertThat(session.settings().orientation()).isEqualTo("vertical");
			assertThat(submit(session, "select 7 as id, 'Alice' as name").text()).isEqualTo("id   | 7\nname | Alice\n");
			submit(session, "/format json");
			assertThat(submit(session, "select 7 as id").text()).isEqualTo("[\n{\"id\":7}\n]\n");
		}
	}

	@Test
	void formatChangesTheNextResultWhileEarlierResultsKeepTheirRenderedText() throws Exception {
		try (Session session = session()) {
			var earlier = submit(session, "select 7 as id");
			var changed = submit(session, "/format json");
			assertThat(changed.kind()).isEqualTo(MessageBlock.Kind.INFO);
			assertThat(changed.text()).isEqualTo("Format: json");
			assertThat(submit(session, "select 8 as id").text()).isEqualTo("[\n{\"id\":8}\n]\n");
			assertThat(session.settings().format()).isEqualTo("json");
			assertThat(earlier.text()).isEqualTo("id\n--\n7\n");
			assertThat(session.messageBlocks().get(1).text()).isEqualTo(earlier.text());
			assertThat(session.messageBlocks().get(1).result()).isNull();
		}
	}
}
