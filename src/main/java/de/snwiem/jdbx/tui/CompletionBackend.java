package de.snwiem.jdbx.tui;

import java.util.concurrent.CompletableFuture;

import de.snwiem.jdbx.completion.Completion;

/** Asks for Completion off the input thread. The future may complete late, for example while the schema loads. */
@FunctionalInterface
public interface CompletionBackend {

	CompletableFuture<Completion> complete(String text, int cursor);
}
