package com.datev.dbui.completion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.datev.dbui.session.SlashCommand;
import com.datev.dbui.session.SlashCommandRegistry;

class SlashCommandSourceTest {
	private final SlashCommandRegistry registry = new SlashCommandRegistry(List.of(
		new SlashCommand("format", "Format", SlashCommand.argument("Format", "1", List.of("table", "json")), false, a -> List.of()),
		new SlashCommand("readonly", "Read-only", SlashCommand.argument("on|off", "0..1", List.of("on", "off")), true, a -> List.of())));
	private final CompletionEngine engine = new CompletionEngine(List.of(new SlashCommandSource(registry), new KeywordSource()));

	@Test
	void namesAndArgumentsReplaceOnlyTheWordAroundTheCursor() {
		var names = engine.complete("  /forzzz json", 6, SchemaModel.EMPTY);
		assertThat(names.candidates()).extracting(Candidate::insert).containsExactly("/format");
		assertThat(names.apply("  /forzzz json", names.candidates().getFirst()).text()).isEqualTo("  /format json");
		var args = engine.complete("/format jxxx", 9, SchemaModel.EMPTY);
		assertThat(args.candidates()).extracting(Candidate::insert).containsExactly("json");
		assertThat(args.apply("/format jxxx", args.candidates().getFirst()).text()).isEqualTo("/format json");
		assertThat(engine.complete("/readonly ", 10, SchemaModel.EMPTY).candidates()).extracting(Candidate::insert)
			.containsExactly("on", "off");
	}

	@Test
	void slashAndSqlSourcesDoNotOfferEachOthersCandidates() {
		assertThat(engine.complete("/", 1, SchemaModel.EMPTY).candidates()).extracting(Candidate::insert)
			.containsExactly("/format", "/readonly");
		assertThat(engine.complete("/unknown ", 9, SchemaModel.EMPTY).candidates()).isEmpty();
		assertThat(engine.complete("/format json ", 13, SchemaModel.EMPTY).candidates()).isEmpty();
		assertThat(engine.complete("sel", 3, SchemaModel.EMPTY).candidates()).extracting(Candidate::insert).containsExactly("select");
		assertThat(registry.help()).contains("Tab / Alt-/", "Completion Tab / Enter", "Shift-Tab / Ctrl-O");
	}
}
