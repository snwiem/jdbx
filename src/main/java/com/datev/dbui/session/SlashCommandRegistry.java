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
			String usage = usage(commandLine).lines().findFirst().orElseThrow();
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

	private static String usage(CommandLine command) {
		// The invisible root has an empty name; picocli otherwise includes its separator in the synopsis.
		return command.getUsageMessage(CommandLine.Help.Ansi.OFF).replaceFirst("^Usage:\\s+", "Usage: ");
	}

	public String help() {
		var text = new StringBuilder("Slash commands\n");
		for (var command : commands) {
			text.append(usage(line.getSubcommands().get("/" + command.name()))).append('\n');
		}
		text.append("""
			Key bindings
			Enter                     Send input; accept a Ctrl-R match without sending
			Ctrl-J                    Insert a newline (Kitty); use bracketed paste on legacy terminals
			Up / Down                 History at the first / last editor line; otherwise move the cursor
			Ctrl-R                    Search history; press again for an older match
			Esc                       Cancel history search and restore the draft
			Ctrl-C                    Cancel a running Statement, cancel search, or clear idle input; never exit
			Ctrl-D                    Ignore; never exit (use /quit)
			Shift-Tab / Ctrl-O         Cycle Orientation; Ctrl-O is the legacy terminal fallback
			Page Up / Page Down        Scroll the Transcript
			Mouse wheel               Scroll the Transcript
			Left / Right               Move the cursor; Ctrl-Left / Ctrl-Right move by word
			Home / End                 Move to the start / end of a line
			Backspace / Delete         Delete before / after the cursor
			""");
		return text.toString().stripTrailing();
	}
}
