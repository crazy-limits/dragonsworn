package crazylimits.dragonsworn.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The little of TOML the config needs: {@code [table]} and {@code [dotted.table]} headers, {@code key = value}
 * lines with booleans, integers, floats and quoted strings, and {@code #} comments. Keys come out as
 * {@code table.key}. Anything else (arrays, inline tables, multi-line strings) is a problem, reported by line.
 */
public final class Toml {
	private Toml() {}

	/** The values of {@code text} by dotted key (Boolean, Long, Double or String); what did not parse goes to {@code problems}. */
	public static Map<String, Object> parse(String text, List<String> problems) {
		Map<String, Object> values = new LinkedHashMap<>();
		String table = "";
		String[] lines = text.split("\r?\n", -1);
		for (int n = 0; n < lines.length; n++) {
			String line = stripComment(lines[n]).strip();
			if (line.isEmpty()) continue;
			if (line.startsWith("[")) {
				if (!line.endsWith("]") || line.startsWith("[[")) {
					problems.add("line " + (n + 1) + ": bad table header: " + line);
					continue;
				}
				table = line.substring(1, line.length() - 1).strip();
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0) {
				problems.add("line " + (n + 1) + ": expected key = value: " + line);
				continue;
			}
			String key = unquote(line.substring(0, eq).strip());
			Object value = value(line.substring(eq + 1).strip());
			if (value == null) {
				problems.add("line " + (n + 1) + ": bad value for " + key + ": " + line.substring(eq + 1).strip());
				continue;
			}
			values.put(table.isEmpty() ? key : table + "." + key, value);
		}
		return values;
	}

	/** A value's text as it is written to the file. */
	public static String format(Object value) {
		if (value instanceof String s) return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
		if (value instanceof Double d) {
			String s = Double.toString(d);
			return s.contains("E") ? String.format(java.util.Locale.ROOT, "%.6f", d) : s;
		}
		return String.valueOf(value);
	}

	private static String stripComment(String line) {
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) quoted = !quoted;
			else if (c == '#' && !quoted) return line.substring(0, i);
		}
		return line;
	}

	private static String unquote(String key) {
		return key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"") ? key.substring(1, key.length() - 1) : key;
	}

	private static Object value(String text) {
		if (text.equals("true")) return Boolean.TRUE;
		if (text.equals("false")) return Boolean.FALSE;
		if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
			return text.substring(1, text.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
		}
		String number = text.replace("_", "");
		try {
			if (number.matches("[+-]?\\d+")) return Long.parseLong(number);
			if (number.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) return Double.parseDouble(number);
		} catch (NumberFormatException e) {
			return null;
		}
		return null;
	}
}
