package com.datev.dbui.tui;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/** Routes the log output of the JVM to the Session log file while the Session runs. */
final class SessionLogging implements AutoCloseable {

	private final ch.qos.logback.classic.Logger root;
	private final FileAppender<ILoggingEvent> appender;

	SessionLogging(Path file) {
		if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
			root = context.getLogger(Logger.ROOT_LOGGER_NAME);
			var encoder = new PatternLayoutEncoder();
			encoder.setContext(context);
			encoder.setPattern("%d{ISO8601} %-5level [%thread] %logger{36} - %msg%n");
			encoder.start();
			appender = new FileAppender<>();
			appender.setContext(context);
			appender.setName("dbui-session-log");
			appender.setFile(file.toString());
			appender.setAppend(true);
			appender.setEncoder(encoder);
			appender.start();
			root.addAppender(appender);
		}
		else {
			root = null;
			appender = null;
		}
	}

	@Override
	public void close() {
		if (root != null) {
			root.detachAppender(appender);
			appender.stop();
		}
	}
}
