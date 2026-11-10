package com.datev.dbui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DbuiApplication {

	public static void main(String[] args) {
		System.exit(run(args));
	}

	/** Runs dbui once and returns the exit code. */
	public static int run(String... args) {
		try (ConfigurableApplicationContext context = SpringApplication.run(DbuiApplication.class, args)) {
			return SpringApplication.exit(context);
		}
	}

}
