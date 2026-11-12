package de.snwiem.jdbx.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeyBindingTest {

	@ParameterizedTest
	@CsvSource({
		"ctrl-j, ctrl-j, j, CTRL",
		"' SHIFT-TAB ', shift-tab, tab, SHIFT",
		"ALT-/, alt-/, /, ALT",
		"tab, tab, tab, NONE",
		"enter, enter, enter, NONE",
		"ctrl-space, ctrl-space, space, CTRL",
		"alt-enter, alt-enter, enter, ALT",
		"shift-enter, shift-enter, enter, SHIFT"
	})
	void parsesKeyNamesWithoutATerminal(String text, String normalized, String key, String modifier) {
		var binding = new KeyBinding(text);
		assertThat(binding.name()).isEqualTo(normalized);
		assertThat(binding.key()).isEqualTo(key);
		assertThat(binding.modifier().name()).isEqualTo(modifier);
	}
}
