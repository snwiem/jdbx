package com.datev.dbui.completion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.datev.dbui.completion.Candidate.Kind;
import com.datev.dbui.completion.CompletionContext.TableRef;

/** Schema, table, view and column names from the {@link SchemaModel}, chosen by the position in the Statement. */
public final class SchemaSource implements CandidateSource {

	private record Resolved(String schema, String table) {
	}

	@Override
	public List<Candidate> candidates(CompletionContext context) {
		return switch (context.position()) {
			case NONE, SLASH, KEYWORD -> List.of();
			case TABLE -> tablesAndSchemas(context);
			case QUALIFIED_TABLE -> context.qualifier().size() == 1 ? tables(context, context.qualifier().get(0)) : List.of();
			case QUALIFIED -> qualified(context);
			case EXPRESSION -> expression(context);
		};
	}

	private List<Candidate> tablesAndSchemas(CompletionContext context) {
		List<Candidate> result = new ArrayList<>(schemas(context));
		String current = context.schema().defaultSchema();
		if (current != null) {
			result.addAll(tables(context, current));
		}
		return result;
	}

	private List<Candidate> schemas(CompletionContext context) {
		return context.schema().schemas().stream().filter(context::matches).sorted(Comparator.comparing(String::toLowerCase))
			.map(s -> new Candidate(context.identifier(s), s, Kind.SCHEMA)).toList();
	}

	private List<Candidate> tables(CompletionContext context, String schema) {
		return context.schema().tables(schema).stream().filter(t -> context.matches(t.name()))
			.sorted(Comparator.comparing(t -> t.name().toLowerCase()))
			.map(t -> new Candidate(context.identifier(t.name()), t.name(), t.view() ? Kind.VIEW : Kind.TABLE)).toList();
	}

	private List<Candidate> columns(CompletionContext context, Resolved table) {
		return context.schema().columns(table.schema(), table.table()).stream().filter(context::matches)
			.map(c -> new Candidate(context.identifier(c), c, Kind.COLUMN)).toList();
	}

	private List<Candidate> qualified(CompletionContext context) {
		var qualifier = context.qualifier();
		if (qualifier.size() == 2) {
			return resolve(context, qualifier.get(0), qualifier.get(1)).map(t -> columns(context, t)).orElse(List.of());
		}
		String name = qualifier.get(0);
		for (TableRef ref : context.tables()) {
			if (name.equals(ref.alias())) {
				return resolve(context, ref.schema(), ref.table()).map(t -> columns(context, t)).orElse(List.of());
			}
		}
		for (TableRef ref : context.tables()) {
			if (ref.alias() == null && name.equals(ref.table())) {
				return resolve(context, ref.schema(), ref.table()).map(t -> columns(context, t)).orElse(List.of());
			}
		}
		var inDefaultSchema = resolve(context, null, name);
		if (inDefaultSchema.isPresent()) {
			return columns(context, inDefaultSchema.get());
		}
		return tables(context, name);
	}

	private List<Candidate> expression(CompletionContext context) {
		List<Candidate> columns = new ArrayList<>();
		for (TableRef ref : context.tables()) {
			resolve(context, ref.schema(), ref.table()).ifPresent(t -> columns.addAll(columns(context, t)));
		}
		return columns.isEmpty() ? tablesAndSchemas(context) : columns;
	}

	private Optional<Resolved> resolve(CompletionContext context, String schema, String table) {
		String actual = schema != null ? schema : context.schema().defaultSchema();
		if (actual == null || context.schema().tables(actual).stream().noneMatch(t -> t.name().equals(table))) {
			return Optional.empty();
		}
		return Optional.of(new Resolved(actual, table));
	}
}
