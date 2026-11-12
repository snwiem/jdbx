package de.snwiem.jdbx.session;

import java.util.List;
import java.util.function.Function;

import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * One Slash command: picocli argument metadata and a Session-local action. Arguments are not shell words.
 * {@code group} is the heading of the command in {@code /help}; a command without one is in {@value #OTHER}.
 */
public record SlashCommand(String name, String help, PositionalParamSpec argument, boolean background,
		Function<String, List<MessageBlock>> action, String group) {

	public static final String OTHER = "Other";

	public SlashCommand(String name, String help, PositionalParamSpec argument, boolean background,
			Function<String, List<MessageBlock>> action) {
		this(name, help, argument, background, action, OTHER);
	}

	public SlashCommand inGroup(String group) {
		return new SlashCommand(name, help, argument, background, action, group);
	}

	public static PositionalParamSpec argument(String label, String arity, Iterable<String> candidates) {
		return argument(label, arity, candidates, value -> value);
	}

	public static PositionalParamSpec argument(String label, String arity, Iterable<String> candidates,
			picocli.CommandLine.ITypeConverter<String> converter) {
		return PositionalParamSpec.builder().index("0").arity(arity).required(!arity.startsWith("0"))
			.type(String.class).paramLabel(label).completionCandidates(candidates)
			.converters((picocli.CommandLine.ITypeConverter<String>) value -> {
				try {
					return converter.convert(value);
				}
				catch (IllegalArgumentException e) {
					throw new picocli.CommandLine.TypeConversionException(e.getMessage());
				}
			}).build();
	}
}
