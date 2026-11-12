package org.syracus.jdbx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class JdbxApplication {

	public static void main(String[] args) {
		System.exit(run(args));
	}

	/** Runs jdbx once and returns the exit code. */
	public static int run(String... args) {
		try (ConfigurableApplicationContext context = SpringApplication.run(JdbxApplication.class, args)) {
			return SpringApplication.exit(context);
		}
	}

}
