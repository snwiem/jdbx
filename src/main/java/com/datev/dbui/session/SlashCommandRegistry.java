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

	private final CommandLine line = new CommandLine(CommandSpec.create().name("")).setExpandAtFiles(false);
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
			var parsed = line.parseArgs(argument.isEmpty() ? new String[] { name } : new String[] { name, "--", argument });
			var command = commands.stream().filter(c -> name.equals("/" + c.name())).findFirst().orElseThrow();
			String value = parsed.subcommand().matchedPositionalValue(0, "");
			return new Invocation(command.background(), () -> command.action().apply(value));
		}
		catch (CommandLine.ParameterException e) {
			String usage = usage(commandLine).lines().findFirst().orElseThrow();
			String message = e instanceof CommandLine.UnmatchedArgumentException ? "Unexpected argument: " + argument
				: e.getCause() instanceof CommandLine.TypeConversionException conversion ? conversion.getMessage() : e.getMessage();
			return new Invocation(false, () -> List.of(MessageBlock.error(usage + (argument.isEmpty() ? "" : "\n" + message))));
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
		return help(com.datev.dbui.config.KeyBindings.defaults());
	}

	public String help(com.datev.dbui.config.KeyBindings keys) {
		var text = new StringBuilder("Slash commands\n");
		for (var command : commands) {
			var subcommand = line.getSubcommands().get("/" + command.name());
			String synopsis = usage(subcommand).lines().findFirst().orElseThrow().substring("Usage: ".length());
			text.append(synopsis).append(" — ").append(subcommand.getCommandSpec().usageMessage().description()[0]).append('\n');
		}
		text.append('\n').append("""
			Key bindings
			Enter                     Send input; accept a Ctrl-R match without sending
			%-26sInsert a newline; some modified keys need Kitty keyboard support
			Up / Down                 History at the first / last editor line; otherwise move the cursor
			Ctrl-R                    Search history; press again for an older match
			Esc                       Cancel history search and restore the draft
			Ctrl-C                    Cancel a running Statement, cancel search, or clear idle input; never exit
			Ctrl-D                    Ignore; never exit (use /quit)
			%-26sCycle Orientation (primary / fallback)
			%-26sRequest Completion (primary / fallback)
			Completion Up / Down      Select a candidate
			Completion Tab / Enter    Accept a candidate without sending input
			Completion Esc            Close Completion
			Dialog Tab / Shift-Tab    Move between fields and Save (not Orientation)
			Dialog Enter              Next field or Save; never send editor input
			Dialog Ctrl-S             Save the Profile
			Dialog Ctrl-G / Ctrl-Enter Connect without saving (connection dialogs only)
			Dialog Esc / Ctrl-C       Cancel the dialog or masked prompt
			Password Enter            Connect with the masked password; never store it in history
			Confirmation y / other    Confirm Profile removal / cancel it
			Page Up / Page Down       Scroll the Transcript
			Mouse wheel               Scroll the Transcript
			Left / Right              Move the cursor; Ctrl-Left / Ctrl-Right move by word
			Home / End                Move to the start / end of a line
			Backspace / Delete        Delete before / after the cursor
			Ctrl-W / Ctrl-Backspace   Delete the previous word
			Ctrl-Delete               Delete the next word
			Keypad navigation keys    Same cursor actions as the main navigation keys
			""".formatted(keys.newline().display(),
				keys.orientationCycle().display() + " / " + keys.orientationFallback().display(),
				keys.complete().display() + " / " + keys.completeAlternate().display()));
		if (java.util.stream.Stream.of(keys.newline(), keys.orientationCycle(), keys.orientationFallback(), keys.complete(), keys.completeAlternate())
				.noneMatch(key -> key.name().equals("shift-enter"))) {
			text.append("Shift-Enter               Insert a newline when the terminal distinguishes it from Enter\n");
		}
		return text.toString().stripTrailing();
	}
}
