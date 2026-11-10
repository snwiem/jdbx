package com.datev.dbui.session;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/** One picocli command tree per Session. Also supplies help and argument candidates for on-request Completion. */
public final class SlashCommandRegistry {

	public record Invocation(boolean background, Supplier<List<MessageBlock>> action) {
		public List<MessageBlock> execute() {
			return action.get();
		}
	}

	private final CommandLine line = new CommandLine(CommandSpec.create().name(""));
	private final List<SlashCommand> commands;

	public SlashCommandRegistry(List<SlashCommand> commands) {
		this.commands = List.copyOf(commands);
		for (var command : commands) {
			var spec = CommandSpec.create().name("/" + command.name());
			spec.usageMessage().description(command.help());
			if (command.argument() != null) {
				spec.addPositional(command.argument());
			}
			line.addSubcommand("/" + command.name(), new CommandLine(spec).setExpandAtFiles(false));
		}
	}

	/** The entire argument is raw text: SQL double quotes, whitespace and backslashes retain their meaning. */
	public Invocation parse(String input) {
		String text = input.strip();
		int end = 0;
		while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
			end++;
		}
		String name = text.substring(0, end);
		var commandLine = line.getSubcommands().get(name);
		if (commandLine == null) {
			return new Invocation(false, () -> List.of(MessageBlock.error("Unknown Slash command: " + text)));
		}
		String argument = text.substring(end).strip();
		try {
			var parsed = line.setExpandAtFiles(false).parseArgs(argument.isEmpty() ? new String[] { name } : new String[] { name, "--", argument });
			var command = commands.stream().filter(c -> name.equals("/" + c.name())).findFirst().orElseThrow();
			String value = parsed.subcommand().matchedPositionalValue(0, "");
			return new Invocation(command.background(), () -> command.action().apply(value));
		}
		catch (CommandLine.ParameterException e) {
			String usage = commandLine.getUsageMessage(CommandLine.Help.Ansi.OFF).lines().findFirst().orElseThrow();
			return new Invocation(false, () -> List.of(MessageBlock.error(usage + (argument.isEmpty() ? "" : "\n" + e.getMessage()))));
		}
	}

	public List<SlashCommand> commands() {
		return commands;
	}

	public List<String> completionCandidates(String name) {
		var spec = line.getSubcommands().get(name.startsWith("/") ? name : "/" + name);
		if (spec == null || spec.getCommandSpec().positionalParameters().isEmpty()) {
			return List.of();
		}
		var candidates = new ArrayList<String>();
		var values = spec.getCommandSpec().positionalParameters().getFirst().completionCandidates();
		if (values != null) {
			values.forEach(candidates::add);
		}
		return List.copyOf(candidates);
	}

	public String help() {
		var text = new StringBuilder("Slash commands\n");
		for (var command : commands) {
			text.append(line.getSubcommands().get("/" + command.name()).getUsageMessage(CommandLine.Help.Ansi.OFF)).append('\n');
		}
		return text.toString().stripTrailing();
	}
}
