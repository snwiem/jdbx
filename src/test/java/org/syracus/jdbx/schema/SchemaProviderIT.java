package com.datev.dbui.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Schema provider seam: JDBC metadata of a small PostgreSQL test schema. */
@Testcontainers
class SchemaProviderIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	static Connection connection;

	@BeforeAll
	static void createTestSchema() throws Exception {
		connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		try (var s = connection.createStatement()) {
			s.execute("""
				create schema sales;
				create table public.customers (id integer primary key, name varchar(50) not null default 'anon', email text);
				comment on table public.customers is 'All customers';
				comment on column public.customers.email is 'Contact address';
				create unique index customers_email_idx on public.customers (email);
				create table public.orders (
					customer_id integer not null references public.customers(id),
					line integer not null,
					note text,
					primary key (customer_id, line));
				create view public.customer_names as select name from public.customers;
				create table sales.targets (id integer primary key, customer_id integer references public.customers(id));
				create table public."My Table" (id integer);
				""");
		}
	}

	@AfterAll
	static void close() throws Exception {
		connection.close();
	}

	private SchemaProvider provider() {
		return new SchemaProvider(connection);
	}

	@Test
	void schemasHideSystemSchemasUnlessAllIsRequested() throws Exception {
		assertThat(provider().schemas(false)).containsExactly("public", "sales");
		assertThat(provider().schemas(true)).contains("public", "sales", "pg_catalog", "information_schema");
	}

	@Test
	void currentSchemaIsPublicByDefault() throws Exception {
		assertThat(provider().currentSchema()).isEqualTo("public");
	}

	@Test
	void tablesListTablesAndViewsWithTypeAndRemarks() throws Exception {
		var tables = provider().tables("public");
		assertThat(tables).extracting(SchemaProvider.TableInfo::name)
			.containsExactly("My Table", "customer_names", "customers", "orders");
		var customers = tables.stream().filter(t -> t.name().equals("customers")).findFirst().orElseThrow();
		assertThat(customers.type()).isEqualTo("TABLE");
		assertThat(customers.remarks()).isEqualTo("All customers");
		assertThat(tables.stream().filter(t -> t.name().equals("customer_names")).findFirst().orElseThrow().type())
			.isEqualTo("VIEW");
		assertThat(provider().tables("sales")).extracting(SchemaProvider.TableInfo::name).containsExactly("targets");
	}

	@Test
	void describeListsColumnsWithTypeNullableDefaultPositionAndRemarks() throws Exception {
		var table = provider().describe("public", "customers").orElseThrow();
		assertThat(table.columns()).hasSize(3);
		var name = table.columns().get(1);
		assertThat(name.name()).isEqualTo("name");
		assertThat(name.type()).isEqualTo("varchar");
		assertThat(name.nullable()).isFalse();
		assertThat(name.defaultValue()).isEqualTo("'anon'::character varying");
		assertThat(name.position()).isEqualTo(2);
		assertThat(table.columns().get(2).remarks()).isEqualTo("Contact address");
		assertThat(table.columns().get(2).nullable()).isTrue();
	}

	@Test
	void describeShowsCompositePrimaryKeyInKeyOrder() throws Exception {
		var key = provider().describe("public", "orders").orElseThrow().primaryKey();
		assertThat(key.columns()).containsExactly("customer_id", "line");
	}

	@Test
	void describeShowsForeignKeysInBothDirections() throws Exception {
		var provider = provider();
		var orders = provider.describe("public", "orders").orElseThrow();
		assertThat(orders.references()).singleElement().satisfies(fk -> {
			assertThat(fk.columns()).containsExactly("customer_id");
			assertThat(fk.otherSchema()).isEqualTo("public");
			assertThat(fk.otherTable()).isEqualTo("customers");
			assertThat(fk.otherColumns()).containsExactly("id");
		});
		var customers = provider.describe("public", "customers").orElseThrow();
		assertThat(customers.referencedBy()).extracting(f -> f.otherSchema() + "." + f.otherTable())
			.containsExactlyInAnyOrder("public.orders", "sales.targets");
		assertThat(customers.referencedBy().getFirst().columns()).containsExactly("id");
	}

	@Test
	void describeShowsIndexesWithColumnsAndUniqueFlag() throws Exception {
		var indexes = provider().describe("public", "customers").orElseThrow().indexes();
		assertThat(indexes).extracting(SchemaProvider.IndexInfo::name).containsExactly("customers_email_idx", "customers_pkey");
		assertThat(indexes.getFirst().columns()).containsExactly("email");
		assertThat(indexes.getFirst().unique()).isTrue();
	}

	@Test
	void describeFindsMixedCaseTableAndReturnsEmptyForUnknownTable() throws Exception {
		assertThat(provider().describe("public", "My Table")).isPresent();
		assertThat(provider().describe("public", "my table")).isEmpty();
		assertThat(provider().describe("public", "nope")).isEmpty();
	}

	@Test
	void metadataIsCachedUntilRefresh() throws Exception {
		var provider = provider();
		assertThat(provider.tables("sales")).hasSize(1);
		try (var s = connection.createStatement()) {
			s.execute("create table sales.late (id integer)");
		}
		try {
			assertThat(provider.tables("sales")).hasSize(1);
			provider.refresh();
			assertThat(provider.tables("sales")).hasSize(2);
		}
		finally {
			try (var s = connection.createStatement()) {
				s.execute("drop table sales.late");
			}
		}
	}
}
