package com.datev.dbui.session;

import java.util.List;
import java.util.function.Function;

import picocli.CommandLine.Model.PositionalParamSpec;

/** One Slash command: picocli argument metadata and a Session-local action. Arguments are not shell words. */
public record SlashCommand(String name, String help, PositionalParamSpec argument, boolean background,
		Function<String, List<MessageBlock>> action) {

	public static PositionalParamSpec argument(String label, String arity, List<String> candidates) {
		return PositionalParamSpec.builder().index("0").arity(arity).type(String.class).paramLabel(label)
			.completionCandidates(candidates).build();
	}
}
