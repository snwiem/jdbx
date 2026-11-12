package org.syracus.jdbx.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.syracus.jdbx.config.KeyBindings;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.tui.event.EventParser;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/** Real terminal bytes through TamboUI's parser, then the configured editor bindings. */
class EditorKeysTest {

	@ParameterizedTest
	@MethodSource("slashKeys")
	void slashBindingsMatchTerminalEvents(String binding, String bytes) throws Exception {
		var keys = EditorKeys.from(KeyBindings.from(Map.of("complete-alternate", binding)));
		assertThat(keys.requestsCompletion(parse(bytes))).isTrue();
	}

	static Stream<Arguments> slashKeys() {
		return Stream.of(
			Arguments.of("alt-/", "\033[55:47;4u"), // German Alt+Shift+7, shifted character '/'
			Arguments.of("ctrl-/", "\033[55:47;6u"), // German Ctrl+Shift+7
			Arguments.of("alt-/", "\033/"), // Legacy Alt-/
			Arguments.of("alt-/", "\033[47;3u"), // US Kitty Alt-/
			Arguments.of("ctrl-/", "\033[47;5u")); // US Kitty Ctrl-/
	}

	@ParameterizedTest
	@ValueSource(strings = {"\033[Z", "\033[9;2u"})
	void shiftTabDoesNotRequestCompletionByDefault(String bytes) throws Exception {
		var keys = EditorKeys.defaults();
		var event = parse(bytes);
		assertThat(keys.requestsCompletion(event)).isFalse();
		assertThat(keys.orientationCycle().matches(event)).isTrue();
	}

	@ParameterizedTest
	@MethodSource("legacyControlAliases")
	void legacyControlAliasesAreNotDistinctWithoutKitty(String binding, String legacy, KeyCode code, String kitty) throws Exception {
		var keys = EditorKeys.from(KeyBindings.from(Map.of("newline", binding)));
		var event = parse(legacy);
		assertThat(event.code()).isEqualTo(code);
		assertThat(keys.newline().matches(event)).isFalse();
		assertThat(keys.newline().matches(parse(kitty))).isTrue();
	}

	static Stream<Arguments> legacyControlAliases() {
		return Stream.of(
			Arguments.of("ctrl-h", "\b", KeyCode.BACKSPACE, "\033[104;5u"),
			Arguments.of("ctrl-i", "\t", KeyCode.TAB, "\033[105;5u"),
			Arguments.of("ctrl-j", "\n", KeyCode.ENTER, "\033[106;5u"),
			Arguments.of("ctrl-m", "\r", KeyCode.ENTER, "\033[109;5u"));
	}

	private static KeyEvent parse(String bytes) throws Exception {
		try (var terminal = new DumbTerminal(new ByteArrayInputStream(bytes.getBytes(StandardCharsets.UTF_8)),
			new ByteArrayOutputStream()); var backend = new JLineBackend(terminal)) {
			return (KeyEvent) EventParser.readEvent(backend, 100);
		}
	}
}
