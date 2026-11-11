package com.datev.dbui;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Transposes a Dump on disk: one spool per column, with only one row of values in memory. */
final class DiskVerticalFormatter implements ResultFormatter {

	private final Format format;
	private final ValueRenderer values;
	private final Path directory;

	DiskVerticalFormatter(Format format, FormatOptions options, Path directory) {
		this.format = format;
		this.values = new ValueRenderer(options);
		this.directory = directory;
	}

	@Override
	public void write(RowSource source, Writer out) throws IOException {
		try (var spool = new Spool(directory)) {
			List<DataOutputStream> columns = new ArrayList<>();
			for (String name : source.columns()) {
				var column = spool.open();
				columns.add(column);
				cell(column, format == Format.MARKDOWN ? name.replace("|", "\\|") : name);
			}
			var widths = spool.open();
			widths.writeInt(source.columns().stream().mapToInt(String::length).max().orElse(0));
			long count = 0;
			var rows = source.rows();
			while (rows.hasNext()) {
				var row = rows.next();
				int width = 0;
				for (int c = 0; c < columns.size(); c++) {
					String text = render(row.get(c));
					cell(columns.get(c), text);
					width = Math.max(width, text.length());
				}
				widths.writeInt(width);
				count++;
			}
			spool.finish();
			if (format == Format.MARKDOWN) {
				out.append("| Column");
				for (long r = 1; r <= count; r++) out.append(" | Row ").append(Long.toString(r));
				out.append(" |\n| ---");
				for (long r = 0; r < count; r++) out.append(" | ---");
				out.append(" |\n");
			}
			for (int c = 0; c < columns.size(); c++) {
				try (var column = input(spool.paths.get(c)); var sizes = input(spool.paths.getLast())) {
					if (format == Format.MARKDOWN) out.append("| ");
					for (long r = 0; r <= count; r++) {
						String text = cell(column);
						int width = sizes.readInt();
						if (r > 0) out.append(switch (format) { case CSV -> ","; case TSV -> "\t"; default -> " | "; });
						out.append(switch (format) { case CSV -> CsvFormatter.quote(text); case TSV -> TsvFormatter.escape(text); default -> text; });
						if (format == Format.TABLE && r < count) out.append(" ".repeat(Math.max(0, width - text.length())));
					}
					out.append(format == Format.MARKDOWN ? " |\n" : "\n");
				}
			}
		}
	}

	private String render(Object value) {
		return switch (format) {
			case TABLE -> values.forTable(value);
			case MARKDOWN -> values.forMarkdown(value);
			default -> value == null ? "" : values.plain(value);
		};
	}

	private static void cell(DataOutputStream out, String text) throws IOException {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		out.writeInt(bytes.length);
		out.write(bytes);
	}

	private static String cell(DataInputStream in) throws IOException {
		return new String(in.readNBytes(in.readInt()), StandardCharsets.UTF_8);
	}

	private static DataInputStream input(Path path) throws IOException {
		return new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
	}

	private static final class Spool implements AutoCloseable {
		private final Path directory;
		private final List<Path> paths = new ArrayList<>();
		private final List<DataOutputStream> streams = new ArrayList<>();

		Spool(Path parent) throws IOException { directory = Files.createTempDirectory(parent, ".dbui-columns-"); }

		DataOutputStream open() throws IOException {
			Path path = directory.resolve(Integer.toString(paths.size()));
			paths.add(path);
			var stream = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
			streams.add(stream);
			return stream;
		}

		void finish() throws IOException {
			IOException failure = null;
			for (var stream : streams) {
				try { stream.close(); }
				catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
			}
			streams.clear();
			if (failure != null) throw failure;
		}

		@Override public void close() throws IOException {
			try { finish(); }
			finally {
				IOException failure = null;
				for (var path : paths) {
					try { Files.deleteIfExists(path); }
					catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
				}
				try { Files.deleteIfExists(directory); }
				catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
				if (failure != null) throw failure;
			}
		}
	}
}
