package de.snwiem.jdbx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Demo database seam: the scripts in {@code demo/*.sql} are loaded the way the official PostgreSQL image loads
 * them, and the objects are read through JDBC.
 */
@Testcontainers
class DemoDatabaseIT {

	@Container
	static final PostgreSQLContainer POSTGRES = withDemoScripts(new PostgreSQLContainer("postgres:17"));

	/** Copies every {@code demo/*.sql} file to the init directory of the image, like the Containerfile does. */
	private static PostgreSQLContainer withDemoScripts(PostgreSQLContainer container) {
		try (Stream<Path> files = Files.list(Path.of("demo"))) {
			files.filter(file -> file.toString().endsWith(".sql")).sorted().forEach(file -> container
				.withCopyFileToContainer(MountableFile.forHostPath(file), "/docker-entrypoint-initdb.d/" + file.getFileName()));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return container;
	}

	private static List<String> strings(String sql) throws SQLException {
		try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
				POSTGRES.getPassword()); Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			List<String> values = new ArrayList<>();
			while (resultSet.next()) {
				values.add(resultSet.getString(1));
			}
			return values;
		}
	}

	private static long count(String table) throws SQLException {
		return Long.parseLong(strings("select count(*) from " + table).get(0));
	}

	@Test
	void hasTheSchemasShopAuditAndRef() throws SQLException {
		assertThat(strings("select schema_name from information_schema.schemata")).contains("shop", "audit", "ref");
	}

	@Test
	void tablesHaveEnoughRowsForTheDemo() throws SQLException {
		assertThat(count("shop.customers")).isEqualTo(200);
		assertThat(count("shop.products")).isEqualTo(40);
		assertThat(count("shop.orders")).isEqualTo(1200);
		assertThat(count("shop.order_items")).isBetween(2500L, 3500L);
		assertThat(count("shop.reviews")).isEqualTo(400);
		assertThat(count("ref.countries")).isEqualTo(24);
		assertThat(count("ref.currencies")).isGreaterThanOrEqualTo(5);
	}

	@Test
	void auditEventsExceedTheDefaultRowLimit() throws SQLException {
		assertThat(count("audit.events")).isEqualTo(2500);
	}

	@Test
	void everyViewReturnsRows() throws SQLException {
		assertThat(strings("select table_name from information_schema.views where table_schema = 'shop' order by 1"))
			.containsExactly("v_customer_overview", "v_order_totals", "v_top_products");
		assertThat(count("shop.v_order_totals")).isEqualTo(1200);
		assertThat(count("shop.v_top_products")).isEqualTo(40);
		assertThat(count("shop.v_customer_overview")).isEqualTo(200);
	}

	@Test
	void customerOverviewIsWideEnoughToNeedVerticalOrientation() throws SQLException {
		assertThat(Long.parseLong(strings(
				"select count(*) from information_schema.columns where table_schema = 'shop' and table_name = 'v_customer_overview'")
			.get(0))).isGreaterThanOrEqualTo(20);
	}

	@Test
	void tablesAndColumnsHaveCommentsForTheRemarks() throws SQLException {
		assertThat(strings("select count(*) from pg_description d join pg_class c on c.oid = d.objoid "
				+ "join pg_namespace n on n.oid = c.relnamespace where n.nspname in ('shop', 'audit', 'ref') and d.objsubid = 0 and c.relkind = 'r'")
			.get(0)).isEqualTo("8");
		assertThat(strings("select col_description(attrelid, attnum) from pg_attribute "
				+ "where attrelid = 'shop.products'::regclass and attname = 'description'")).hasSize(1).doesNotContainNull();
	}

	@Test
	void containsNullsJsonAndNonAsciiText() throws SQLException {
		assertThat(count("shop.orders where shipped_at is null")).isPositive();
		assertThat(count("shop.customers where notes is null")).isPositive();
		assertThat(count("shop.customers where preferences ->> 'theme' = 'dark'")).isPositive();
		assertThat(count("shop.customers where notes like '%📚%'")).isPositive();
		assertThat(count("audit.events where details is not null")).isPositive();
	}

	@Test
	void shopTablesAreFoundWithoutSchemaPrefix() throws SQLException {
		assertThat(strings("select count(*) from customers").get(0)).isEqualTo("200");
	}

}
