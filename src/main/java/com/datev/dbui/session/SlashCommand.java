package com.datev.dbui.session;

import java.util.List;
import java.util.function.Function;

import picocli.CommandLine.Model.PositionalParamSpec;

/** One Slash command: picocli argument metadata and a Session-local action. Arguments are not shell words. */
public record SlashCommand(String name, String help, PositionalParamSpec argument, boolean background,
		Function<String, List<MessageBlock>> action) {

	@SafeVarargs
	public static PositionalParamSpec argument(String label, String arity, List<String> candidates,
			picocli.CommandLine.ITypeConverter<String>... converters) {
		return PositionalParamSpec.builder().index("0").arity(arity).required(!arity.startsWith("0")).type(String.class).paramLabel(label)
			.completionCandidates(candidates).converters(converters).build();
	}
}
