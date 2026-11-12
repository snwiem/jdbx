package org.syracus.jdbx.completion;

import java.util.List;
import java.util.Set;

/** The fixed keyword list of Completion. */
final class Keywords {

	private Keywords() { }

	/** Common PostgreSQL keywords and function names, upper case, sorted. */
	static final List<String> ALL = List.of("ABS", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AVG", "BEGIN", "BETWEEN", "BIGINT",
		"BOOLEAN", "BY", "CASE", "CAST", "CEIL", "CHECK", "COALESCE", "COLUMN", "COMMENT", "COMMIT", "CONSTRAINT", "COPY",
		"COUNT", "CREATE", "CROSS", "CURRENT_DATE", "CURRENT_TIMESTAMP", "DATE", "DEFAULT", "DELETE", "DESC", "DISTINCT",
		"DROP", "ELSE", "END", "EXCEPT", "EXISTS", "EXPLAIN", "EXTRACT", "FALSE", "FETCH", "FILTER", "FIRST", "FLOOR",
		"FOREIGN", "FROM", "FULL", "FUNCTION", "GRANT", "GROUP", "HAVING", "ILIKE", "IN", "INDEX", "INNER", "INSERT",
		"INTEGER", "INTERSECT", "INTERVAL", "INTO", "IS", "JOIN", "JSONB", "KEY", "LAST", "LEFT", "LENGTH", "LIKE", "LIMIT",
		"LOWER", "MATERIALIZED", "MAX", "MIN", "NATURAL", "NOT", "NOW", "NULL", "NULLIF", "NULLS", "OFFSET", "ON", "ONLY",
		"OR", "ORDER", "OUTER", "OVER", "PARTITION", "PRIMARY", "REFERENCES", "REPLACE", "RETURNING", "REVOKE", "RIGHT",
		"ROLLBACK", "ROUND", "ROW_NUMBER", "SCHEMA", "SELECT", "SEQUENCE", "SET", "SOME", "SUBSTRING", "SUM", "TABLE",
		"TEXT", "THEN", "TIMESTAMP", "TO", "TRIM", "TRUE", "TRUNCATE", "UNION", "UNIQUE", "UPDATE", "UPPER", "USING",
		"VACUUM", "VALUES", "VARCHAR", "VIEW", "WHEN", "WHERE", "WINDOW", "WITH");

	/** Words that PostgreSQL does not allow as plain names. A table or column with such a name is inserted quoted. */
	static final Set<String> RESERVED = Set.of("all", "analyse", "and", "any", "array", "as", "asc", "both", "case", "cast",
		"check", "column", "constraint", "create", "current_date", "current_timestamp", "current_user", "default", "desc",
		"distinct", "do", "else", "end", "except", "false", "fetch", "for", "foreign", "from", "grant", "group", "having",
		"in", "initially", "intersect", "into", "leading", "limit", "not", "null", "offset", "on", "only", "or", "order",
		"placing", "primary", "references", "returning", "select", "session_user", "some", "symmetric", "table", "then",
		"to", "trailing", "true", "union", "unique", "user", "using", "variadic", "when", "where", "window", "with");
}
