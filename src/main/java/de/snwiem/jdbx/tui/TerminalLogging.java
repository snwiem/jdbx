package org.syracus.jdbx.tui;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;

/** Temporarily detaches console appenders, leaving file logging unchanged. */
final class TerminalLogging implements AutoCloseable {

	private record Attachment(Logger logger, Appender<ILoggingEvent> appender) { }

	private final List<Attachment> detached = new ArrayList<>();

	TerminalLogging() {
		if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
			for (Logger logger : context.getLoggerList()) {
				logger.iteratorForAppenders().forEachRemaining(appender -> {
					if (appender instanceof ConsoleAppender<?>) {
						detached.add(new Attachment(logger, appender));
					}
				});
			}
			detached.forEach(attachment -> attachment.logger().detachAppender(attachment.appender()));
		}
	}

	@Override
	public void close() {
		detached.forEach(attachment -> attachment.logger().addAppender(attachment.appender()));
	}
}
