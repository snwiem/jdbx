package com.datev.dbui.completion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.datev.dbui.schema.SchemaProvider;
import com.datev.dbui.schema.SchemaProviderModel;

/** Completion seam against a real schema: the SchemaProvider cache feeds the engine. */
@Testcontainers
class CompletionIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	@BeforeAll
	static void createTestSchema() throws Exception {
		try (var connection = connect(); var s = connection.createStatement()) {
			s.execute("""
				create schema sch;
				create table sch.targets (id integer primary key, goal text);
				create table public.customers (id integer primary key, name text, email text);
				create table public."My Table" ("Key" integer, plain integer);
				create view public.customer_names as select name from public.customers;
				""");
		}
	}

	@AfterAll
	static void nothing() {
	}

	private static Connection connect() throws Exception {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private static List<String> inserts(SchemaModel model, String marked) {
		int cursor = marked.indexOf('|');
		return CompletionEngine.standard().complete(marked.replace("|", ""), cursor, model).candidates().stream()
			.map(Candidate::insert).toList();
	}

	@Test
	void completesSchemasTablesViewsAliasesAndColumnsFromTheCache() throws Exception {
		var connection = connect();
		var model = new SchemaProviderModel(new SchemaProvider(connection));
		assertThat(inserts(model, "select * from sch.|")).containsExactly("targets");
		assertThat(inserts(model, "select t.| from customers t")).containsExactly("id", "name", "email");
		assertThat(inserts(model, "select * from |")).contains("customers", "customer_names", "\"My Table\"", "sch", "public")
			.doesNotContain("pg_catalog");
		assertThat(inserts(model, "select m.k| from \"My Table\" m")).containsExactly("\"Key\"");
		assertThat(inserts(model, "select go| from sch.targets")).contains("goal");

		assertThat(inserts(model, "/describe cust|")).containsExactly("customer_names", "customers");
		assertThat(inserts(model, "/describe sch.|")).containsExactly("targets");
		assertThat(inserts(model, "/describe my|")).containsExactly("\"My Table\"");
		assertThat(inserts(model, "/tables s|")).containsExactly("sch");

		// The cache answers now: with the connection closed, completing again must not need the database.
		connection.close();
		assertThat(inserts(model, "select t.| from customers t")).containsExactly("id", "name", "email");
		assertThat(inserts(model, "select * from sch.|")).containsExactly("targets");
	}
}
