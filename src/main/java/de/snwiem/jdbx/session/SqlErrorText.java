package de.snwiem.jdbx.session;

import java.sql.SQLException;

import org.postgresql.util.PSQLException;

/** Builds the text of an error band: server message, SQL state and error position when known. Never a stack trace. */
final class SqlErrorText {

	private SqlErrorText() {
	}

	static String of(SQLException error) {
		if (error instanceof PSQLException postgres && postgres.getServerErrorMessage() != null) {
			var server = postgres.getServerErrorMessage();
			var text = new StringBuilder();
			if (server.getSeverity() != null) {
				text.append(server.getSeverity()).append(": ");
			}
			text.append(server.getMessage());
			if (server.getSQLState() != null) {
				text.append(" (").append(server.getSQLState()).append(')');
			}
			if (server.getPosition() > 0) {
				text.append(", position ").append(server.getPosition());
			}
			return text.toString();
		}
		String message = String.valueOf(error.getMessage());
		return error.getSQLState() == null ? message : message + " (" + error.getSQLState() + ")";
	}
}
