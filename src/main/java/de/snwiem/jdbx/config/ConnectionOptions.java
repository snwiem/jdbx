package org.syracus.jdbx.config;

import picocli.CommandLine.Option;

/** Command-line connection options. They override Profile fields. Reusable as a picocli mixin. */
public class ConnectionOptions {

	@Option(names = { "-p", "--profile" }, description = "Profile name from profiles.yaml")
	public String profile;

	@Option(names = "--config-dir", description = "Config directory (default: $JDBX_CONFIG_DIR, $XDG_CONFIG_HOME/jdbx or ~/.config/jdbx)")
	public String configDir;

	@Option(names = "--url", description = "JDBC URL (overrides the Profile host, port and database)")
	public String url;

	@Option(names = "--host", description = "Database host")
	public String host;

	@Option(names = "--port", description = "Database port (default: 5432)")
	public Integer port;

	@Option(names = "--db", description = "Database name")
	public String database;

	@Option(names = "--user", description = "Database user")
	public String user;

	@Option(names = "--password", description = "Database password")
	public String password;

	@Option(names = "--schema", description = "Initial schema (search path)")
	public String schema;

	@Option(names = "--read-only", arity = "0..1", fallbackValue = "true", description = "Read-only (true or false)")
	public Boolean readOnly;

}
