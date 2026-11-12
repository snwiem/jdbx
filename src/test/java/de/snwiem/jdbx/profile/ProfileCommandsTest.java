package de.snwiem.jdbx.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.snwiem.jdbx.config.ConfigException;
import de.snwiem.jdbx.config.Profile;
import de.snwiem.jdbx.config.ProfileStore;

/** Selecting Profiles by name, quoted name with spaces, or list number, for /connect, /profile edit and remove. */
class ProfileCommandsTest {

	@TempDir
	Path directory;

	ProfileCommands commands;

	@BeforeEach
	void profiles() {
		var store = new ProfileStore(directory);
		for (String name : new String[] { "Test", "Test 3", "Test2", "7" }) {
			store.save(new Profile(name, null, "localhost", 5432, "db", "user", "secret", null, null, Map.of()));
		}
		commands = new ProfileCommands(directory, Map.of());
	}

	private static String editName(ProfileCommands.Action action) {
		return ((ProfileCommands.Edit) action).editingName();
	}

	@Test
	void editsProfileByQuotedNameWithSpace() {
		assertThat(editName(new ProfileCommand(commands).execute("edit \"Test 3\""))).isEqualTo("Test 3");
	}

	@Test
	void editsProfileByUnquotedNameWithSpace() {
		assertThat(editName(new ProfileCommand(commands).execute("edit Test 3"))).isEqualTo("Test 3");
	}

	@Test
	void editsProfileByListNumber() {
		commands.list();
		// the list is sorted as the store sorts it: 7, Test, Test 3, Test2
		var names = commands.names();
		int number = names.indexOf("Test 3") + 1;

		assertThat(editName(new ProfileCommand(commands).execute("edit " + number))).isEqualTo("Test 3");
	}

	@Test
	void anExactNameWinsOverAListNumber() {
		commands.list();

		assertThat(editName(new ProfileCommand(commands).execute("edit 7"))).isEqualTo("7");
	}

	@Test
	void aQuotedNumberIsAlwaysAName() {
		assertThatThrownBy(() -> new ProfileCommand(commands).execute("edit \"3\""))
			.isInstanceOf(ConfigException.class).hasMessageContaining("Unknown Profile '3'");
	}

	@Test
	void removesProfileByQuotedNameAndByNumber() {
		var names = commands.names();
		commands.list();

		assertThat(((ProfileCommands.Remove) new ProfileCommand(commands).execute("remove \"Test 3\"")).name()).isEqualTo("Test 3");
		assertThat(((ProfileCommands.Remove) new ProfileCommand(commands).execute("remove " + (names.indexOf("Test2") + 1))).name())
			.isEqualTo("Test2");
	}

	@Test
	void connectsByQuotedNameAndByNumber() {
		commands.list();
		var names = commands.names();

		assertThat(((ProfileCommands.Connect) new ConnectCommand(commands).execute("\"Test 3\"")).profile().name()).isEqualTo("Test 3");
		assertThat(((ProfileCommands.Connect) new ConnectCommand(commands).execute("Test 3")).profile().name()).isEqualTo("Test 3");
		assertThat(((ProfileCommands.Connect) new ConnectCommand(commands).execute(String.valueOf(names.indexOf("Test 3") + 1))).profile().name())
			.isEqualTo("Test 3");
	}

	@Test
	void anUnknownNumberOrNameGivesAHelpfulError() {
		assertThatThrownBy(() -> new ProfileCommand(commands).execute("edit 99"))
			.isInstanceOf(ConfigException.class).hasMessageContaining("No Profile number 99");
		assertThatThrownBy(() -> new ProfileCommand(commands).execute("edit nope"))
			.isInstanceOf(ConfigException.class).hasMessageContaining("Unknown Profile 'nope'");
	}

	@Test
	void anUnbalancedQuoteIsRejected() {
		assertThatThrownBy(() -> new ProfileCommand(commands).execute("edit \"Test 3"))
			.isInstanceOf(ConfigException.class).hasMessageContaining("quote");
	}

	@Test
	void completionOffersNamesWithSpacesInQuotes() {
		assertThat(commands.completionNames()).contains("Test", "\"Test 3\"", "Test2");
	}
}
