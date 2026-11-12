package org.syracus.jdbx.completion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Plain-text Completion: no terminal, no database. The sources decide what to suggest. */
public final class CompletionEngine {

	private final List<CandidateSource> sources;

	public CompletionEngine(List<CandidateSource> sources) {
		this.sources = List.copyOf(sources);
	}

	/** SQL keywords. Schema names come from the Session's schema source. */
	public static CompletionEngine standard() {
		return new CompletionEngine(List.of(new SchemaSource(), new KeywordSource()));
	}

	/** Candidates for the editor {@code text} with the cursor at offset {@code cursor}. */
	public Completion complete(String text, int cursor, SchemaModel schema) {
		var context = new CompletionContext(text, cursor, schema);
		var unique = new LinkedHashMap<String, Candidate>();
		for (CandidateSource source : sources) {
			for (Candidate candidate : source.candidates(context)) {
				unique.putIfAbsent(candidate.insert(), candidate);
			}
		}
		return new Completion(context.start(), context.end(), new ArrayList<>(unique.values()));
	}
}
