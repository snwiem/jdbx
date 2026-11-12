package org.syracus.jdbx.completion;

import java.util.List;

import org.syracus.jdbx.session.SlashCommandRegistry;

/** Slash command names and literal argument candidates from the same registry used for dispatch and help. */
public final class SlashCommandSource implements CandidateSource {
	private final SlashCommandRegistry registry;

	public SlashCommandSource(SlashCommandRegistry registry) {
		this.registry = registry;
	}

	@Override
	public List<Candidate> candidates(CompletionContext context) {
		if (context.position() != CompletionContext.Position.SLASH) {
			return List.of();
		}
		String before = context.text().substring(0, context.start()).strip();
		if (before.isEmpty()) {
			return registry.commands().stream().map(c -> "/" + c.name()).filter(context::matches)
				.map(name -> new Candidate(name, name, Candidate.Kind.COMMAND)).toList();
		}
		// Current commands expose one raw positional argument, not shell words.
		return registry.completionCandidates(before).stream().filter(context::matches)
			.map(value -> new Candidate(value, value, Candidate.Kind.ARGUMENT)).toList();
	}
}
