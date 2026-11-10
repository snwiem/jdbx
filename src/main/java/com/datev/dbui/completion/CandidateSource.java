package com.datev.dbui.completion;

import java.util.List;

/** Supplies candidates for the context. Sources can be added to the engine without changing it. */
public interface CandidateSource {

	List<Candidate> candidates(CompletionContext context);
}
