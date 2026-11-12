package com.datev.dbui.tui;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.datev.dbui.config.*;
import com.datev.dbui.profile.ProfileCommands;
import com.datev.dbui.session.*;

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

	private InteractiveMode(Session session, TuiRunner runner, String connection, AtomicReference<Throwable> failure) {
		this.session = session; this.runner = runner; this.failure = failure;
		this.screen = new InteractiveScreen(connection, this::submit, session::cancel, session.settings(), this::cycleOrientation);
		this.screen.completion(session::complete);
		session.clipboard(sequence -> {
			runner.backend().writeRaw(sequence);
			runner.backend().flush();
		});
		screen.onInteraction(save -> complete(session.saveDialog(save)), password -> {
			if (session.interaction() instanceof SessionInteraction.Confirmation) submit(password);
			else complete(session.answerPassword(password));
		}, () -> {
			session.cancelInteraction(); screen.interaction(null);
			runner.draw(this::render);
		});
	}

	/** Complete options connect directly; incomplete values open a prefilled dialog on the same fullscreen screen. */
	public static int start(ConnectionOptions options, Settings settings) throws Exception {
		return start(options, settings, List.of());
	}

	public static int start(ConnectionOptions options, Settings settings, List<String> configErrors) throws Exception {
		Map<String, String> environment = System.getenv();
		Path configDirectory = ConfigLoader.directory(options.configDir, environment, Path.of(System.getProperty("user.home")));
		var profiles = new ProfileCommands(configDirectory, environment);
		Profile raw = options.profile == null ? null : new ConfigLoader(configDirectory, environment).profileForEditing(options.profile);
		Profile resolved = raw == null ? null : profiles.resolve(raw);
		var model = ProfileDialogModel.prefill(options, resolved, raw, settings);
		ConnectionParameters parameters = null;
		try {
			model.validate(false);
			parameters = ConnectionParameters.resolve(new ConnectionOptions(), profiles.resolve(model.connectionProfile()), settings);
		}
		catch (ConfigException e) {
			// Missing or invalid fields are edited in Interactive mode; Batch mode keeps its fail-fast path.
		}
		Path stateBase = SessionDirectory.defaultStateBase();
		var directory = SessionDirectory.createOrDisabled(stateBase);
		Session session;
		if (parameters == null) {
			session = new Session(directory, settings);
			session.configureProfiles(configDirectory, environment);
			session.openConnectionDialog(model, stateBase);
		}
		else {
			session = Session.connect(directory, new SessionContext(stateBase,
				settings.withExecution(settings.rowLimit(), settings.timeout(), parameters.readOnly()), parameters, options.profile, profiles::load));
			session.configureProfiles(configDirectory, environment);
		}
		return run(session, parameters == null ? "not connected" : ConnectionInfo.of(parameters, options.profile).label(), configErrors);
	}

	public static int run(Connection connection, String label, SessionContext context) throws Exception {
		return run(new Session(connection, SessionDirectory.createOrDisabled(context.stateBase()), context), label);
	}

	private static int run(Session session, String label) throws Exception {
		return run(session, label, List.of());
	}

	private static int run(Session session, String label, List<String> configErrors) throws Exception {
		var failure = new AtomicReference<Throwable>();
		var logging = new AtomicReference<SessionLogging>(session.logFile() == null ? null : new SessionLogging(session.logFile()));
		try (var terminalLogging = new TerminalLogging(); session) {
			session.onDirectoryChange(next -> {
				var old = logging.getAndSet(next.logFile() == null ? null : new SessionLogging(next.logFile()));
				if (old != null) old.close();
			});
			var config = TuiConfig.builder().backend(new InteractiveBackend()).alternateScreen(true).mouseCapture(true)
				.bracketedPaste(true).hideCursor(true).keyboardProtocol(KeyboardProtocol.AUTO).tickRate(Duration.ofMillis(100))
				.errorHandler((error, context) -> { failure.set(error.cause()); return ErrorAction.QUIT_IMMEDIATELY; }).build();
			try (var runner = TuiRunner.create(config)) {
				var mode = new InteractiveMode(session, runner, label, failure);
				mode.screen.startupErrors(configErrors);
				mode.refresh();
				runner.run((event, ignored) -> mode.screen.handle(event), mode::render);
			}
		}
		finally {
			var last = logging.getAndSet(null);
			if (last != null) last.close();
		}
		if (failure.get() != null) throw new IOException("Interactive mode failed: " + failure.get().getMessage(), failure.get());
		return 0;
	}

	private void render(Frame frame) {
		screen.status(session.status());
		screen.settings(session.settings());
		screen.render(frame);
	}
	private void refresh() {
		screen.interaction(session.interaction()); screen.update(session.messageBlocks()); screen.history(session.history());
		if (session.connectionLabel() != null) screen.connection(session.connectionLabel());
	}

	private void cycleOrientation() {
		screen.update(session.cycleOrientation().messageBlocks());
		screen.settings(session.settings());
	}

	private void submit(String input) { complete(session.submit(input)); }
	private void complete(CompletableFuture<Session.Update> pending) {
		screen.busy(true); refresh();
		pending.whenComplete((update, error) -> runner.runLater(() -> {
			screen.busy(false);
			if (error != null) { failure.set(error); runner.quit(); }
			else {
				refresh();
				if (update.editorText() != null) screen.editorText(update.editorText());
				if (update.ended()) runner.quit();
				else runner.draw(this::render); // TamboUI 0.7.0 does not redraw after a runLater callback.
			}
		}));
	}
}
