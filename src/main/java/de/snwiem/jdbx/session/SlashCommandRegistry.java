package de.snwiem.jdbx.session;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import de.snwiem.jdbx.BuildInfo;
import de.snwiem.jdbx.config.KeyBindings;

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
		return help(KeyBindings.defaults());
	}

	/** The whole help: all commands, then all keys. */
	public String help(KeyBindings keys) {
		return catalog(keys).full();
	}

	/** {@code topic} is the text after {@code /help}: empty for the overview, {@code commands}, {@code keys} or a word. */
	public String help(KeyBindings keys, String topic) {
		return catalog(keys).topic(topic);
	}

	private HelpCatalog catalog(KeyBindings keys) {
		var list = new ArrayList<HelpCatalog.Command>();
		for (var command : commands) {
			var subcommand = line.getSubcommands().get("/" + command.name());
			String synopsis = usage(subcommand).lines().findFirst().orElseThrow().substring("Usage: ".length());
			list.add(new HelpCatalog.Command(command.name(), synopsis, subcommand.getCommandSpec().usageMessage().description()[0],
				command.group()));
		}
		return new HelpCatalog(list, keys, BuildInfo.current().versionText());
	}
}
