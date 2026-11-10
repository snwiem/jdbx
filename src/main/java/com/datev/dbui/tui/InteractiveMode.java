package com.datev.dbui.tui;

import java.io.IOException;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.datev.dbui.session.Session;

import dev.tamboui.terminal.KeyboardProtocol;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.error.ErrorAction;

/** Owns terminal resources and transfers Session updates back to the terminal thread. */
public final class InteractiveMode {

	private final Session session;
	private final TuiRunner runner;
	private final InteractiveScreen screen;
	private final AtomicReference<Throwable> failure;

	private InteractiveMode(Session session, TuiRunner runner, String connection, AtomicReference<Throwable> failure) {
		this.session = session;
		this.runner = runner;
		this.failure = failure;
		this.screen = new InteractiveScreen(connection, this::submit);
	}

	public static int run(Connection connection, String label) throws Exception {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		try (var logging = new TerminalLogging(); var session = new Session(connection)) {
			// TuiRunner owns and closes its backend, including when initialization fails.
			var config = TuiConfig.builder().backend(new InteractiveBackend()).alternateScreen(true).mouseCapture(true)
				.bracketedPaste(true).hideCursor(true).keyboardProtocol(KeyboardProtocol.AUTO)
				.tickRate(Duration.ofMillis(100))
				.errorHandler((error, context) -> {
					failure.set(error.cause());
					return ErrorAction.QUIT_IMMEDIATELY;
				}).build();
			try (var runner = TuiRunner.create(config)) {
				var mode = new InteractiveMode(session, runner, label, failure);
				runner.run((event, ignored) -> mode.screen.handle(event), mode.screen::render);
			}
		}
		if (failure.get() != null) {
			throw new IOException("Interactive mode failed: " + failure.get().getMessage(), failure.get());
		}
		return 0;
	}

	private void submit(String input) {
		screen.busy(true);
		var pending = session.submit(input);
		screen.update(session.messageBlocks());
		pending.whenComplete((update, error) -> runner.runLater(() -> {
			screen.busy(false);
			if (error != null) {
				failure.set(error);
				runner.quit();
			}
			else {
				screen.update(update.messageBlocks());
				if (update.ended()) {
					runner.quit();
				}
				else {
					// TamboUI 0.7.0 does not redraw after a runLater callback.
					runner.draw(screen::render);
				}
			}
		}));
	}
}
