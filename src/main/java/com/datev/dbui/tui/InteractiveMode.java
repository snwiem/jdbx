package com.datev.dbui.tui;

import java.io.IOException;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.datev.dbui.config.Settings;
import com.datev.dbui.session.Session;
import com.datev.dbui.session.SessionContext;
import com.datev.dbui.session.SessionDirectory;

import dev.tamboui.terminal.Frame;
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

	private InteractiveMode(Session session, TuiRunner runner, String connection, Settings settings, AtomicReference<Throwable> failure) {
		this.session = session;
		this.runner = runner;
		this.failure = failure;
		this.screen = new InteractiveScreen(connection, this::submit, session::cancel, settings);
	}

	public static int run(Connection connection, String label, SessionContext sessionContext) throws Exception {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		// A state directory that cannot be used disables history and Session log; it does not stop dbui.
		var directory = SessionDirectory.createOrDisabled(sessionContext.stateBase());
		var logging = new AtomicReference<SessionLogging>(directory.logFile() == null ? null : new SessionLogging(directory.logFile()));
		try (var terminalLogging = new TerminalLogging(); var session = new Session(connection, directory, sessionContext)) {
			// /resume switches to a new Session directory: move the log file with it.
			session.onDirectoryChange(next -> {
				var old = logging.getAndSet(next.logFile() == null ? null : new SessionLogging(next.logFile()));
				if (old != null) {
					old.close();
				}
			});
			// TuiRunner owns and closes its backend, including when initialization fails.
			var config = TuiConfig.builder().backend(new InteractiveBackend()).alternateScreen(true).mouseCapture(true)
				.bracketedPaste(true).hideCursor(true).keyboardProtocol(KeyboardProtocol.AUTO)
				.tickRate(Duration.ofMillis(100))
				.errorHandler((error, context) -> {
					failure.set(error.cause());
					return ErrorAction.QUIT_IMMEDIATELY;
				}).build();
			try (var runner = TuiRunner.create(config)) {
				var mode = new InteractiveMode(session, runner, label, session.settings(), failure);
				mode.screen.update(session.messageBlocks());
				mode.screen.history(session.history());
				runner.run((event, ignored) -> mode.screen.handle(event), mode::render);
			}
		}
		finally {
			var last = logging.getAndSet(null);
			if (last != null) {
				last.close();
			}
		}
		if (failure.get() != null) {
			throw new IOException("Interactive mode failed: " + failure.get().getMessage(), failure.get());
		}
		return 0;
	}

	private void render(Frame frame) {
		screen.status(session.status());
		screen.settings(session.settings());
		screen.render(frame);
	}

	private void submit(String input) {
		screen.busy(true);
		var pending = session.submit(input);
		screen.update(session.messageBlocks());
		screen.history(session.history());
		pending.whenComplete((update, error) -> runner.runLater(() -> {
			screen.busy(false);
			if (error != null) {
				failure.set(error);
				runner.quit();
			}
			else {
				screen.update(update.messageBlocks());
				screen.history(session.history());
				if (session.connectionLabel() != null) {
					screen.connection(session.connectionLabel());
				}
				if (update.ended()) {
					runner.quit();
				}
				else {
					// TamboUI 0.7.0 does not redraw after a runLater callback.
					runner.draw(this::render);
				}
			}
		}));
	}
}
