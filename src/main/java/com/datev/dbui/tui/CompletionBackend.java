package com.datev.dbui.tui;

import java.util.concurrent.CompletableFuture;

import com.datev.dbui.completion.Completion;

/** Asks for Completion off the input thread. The future may complete late, for example while the schema loads. */
@FunctionalInterface
public interface CompletionBackend {

	CompletableFuture<Completion> complete(String text, int cursor);
}
