package org.syracus.jdbx.completion;

import java.util.List;

import org.syracus.jdbx.completion.CompletionContext.Position;

/** SQL keywords and function names. They fit wherever SQL text can go, except after {@code name.}. */
public final class KeywordSource implements CandidateSource {

	@Override
	public List<Candidate> candidates(CompletionContext context) {
		return switch (context.position()) {
			case NONE, SLASH, QUALIFIED, QUALIFIED_TABLE, TABLE -> List.of();
			case KEYWORD, EXPRESSION -> Keywords.ALL.stream().filter(context::matches)
				.map(k -> new Candidate(context.keyword(k), k, Candidate.Kind.KEYWORD)).toList();
		};
	}
}
