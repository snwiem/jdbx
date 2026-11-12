package org.syracus.jdbx;

import java.io.PrintWriter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import picocli.CommandLine;

/** Hosts the picocli command line inside Spring Boot and reports its exit code. */
@Component
class JdbxRunner implements CommandLineRunner, ExitCodeGenerator {

	private int exitCode;

	@Override
	public void run(String... args) {
		CommandLine commandLine = new CommandLine(new BatchCommand());
		commandLine.setOut(new PrintWriter(System.out, true));
		commandLine.setErr(new PrintWriter(System.err, true));
		exitCode = commandLine.execute(args);
	}

	@Override
	public int getExitCode() {
		return exitCode;
	}

}
