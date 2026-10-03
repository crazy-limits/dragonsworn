package crazylimits.dragonsworn.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lang files ({@code src/mc/shared/resources/assets/dragonsworn/lang}): {@code en_us.json} holds every
 * text the config screens show exactly as {@link DragonConfig} words it, and a subtitle for every sound
 * (sounds.json); every other language only translates keys {@code en_us.json} has, with the same
 * {@code %s} arguments.
 */
class LangFilesTest {
	private static final Pattern ARG = Pattern.compile("%(\\d+\\$)?s");

	@Test
	void englishHoldsEveryConfigText() {
		Map<String, String> english = read(lang().resolve("en_us.json"));
		List<String> wrong = new ArrayList<>();
		DragonConfig.translations().forEach((key, text) -> {
			if (!text.equals(english.get(key))) wrong.add(key + " should be \"" + text + "\", is \"" + english.get(key) + "\"");
		});
		assertEquals(List.of(), wrong, "en_us.json is behind DragonConfig");
	}

	@Test
	void everySoundHasASubtitle() throws IOException {
		Map<String, String> english = read(lang().resolve("en_us.json"));
		String sounds = Files.readString(lang().resolveSibling("sounds.json"), StandardCharsets.UTF_8);
		int events = 0;
		Matcher event = Pattern.compile("\"([a-z_.]+)\"\\s*:\\s*\\{\\s*\"subtitle\"\\s*:\\s*\"([^\"]+)\"").matcher(sounds);
		while (event.find()) {
			events++;
			assertTrue(english.containsKey(event.group(2)), event.group(1) + "'s subtitle " + event.group(2) + " is not in en_us.json");
		}
		// every event must carry a subtitle: count the events without one too
		int all = (int) Pattern.compile("\"entity\\.ender_dragon\\.[a-z_]+\"\\s*:").matcher(sounds).results().count();
		assertEquals(all, events, "a sound event without a subtitle");
	}

	@Test
	void translationsMatchTheEnglishKeysAndArguments() throws IOException {
		Map<String, String> english = read(lang().resolve("en_us.json"));
		List<String> wrong = new ArrayList<>();
		try (Stream<Path> files = Files.list(lang())) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				read(file).forEach((key, text) -> {
					String en = english.get(key);
					if (en == null) wrong.add(file.getFileName() + ": " + key + " is not in en_us.json");
					else if (args(en) != args(text)) wrong.add(file.getFileName() + ": " + key + " has " + args(text) + " %s, English " + args(en));
				});
			}
		}
		assertEquals(List.of(), wrong);
	}

	private static long args(String text) {
		return ARG.matcher(text).results().count();
	}

	/** A lang file: one flat object of strings. */
	static Map<String, String> read(Path file) {
		String json;
		try {
			json = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		Map<String, String> out = new LinkedHashMap<>();
		int[] at = {skip(json, 0)};
		expect(json, at, '{', file);
		while (true) {
			at[0] = skip(json, at[0]);
			if (json.charAt(at[0]) == '}') break;
			String key = string(json, at, file);
			at[0] = skip(json, at[0]);
			expect(json, at, ':', file);
			at[0] = skip(json, at[0]);
			assertNull(out.put(key, string(json, at, file)), file.getFileName() + ": " + key + " twice");
			at[0] = skip(json, at[0]);
			if (json.charAt(at[0]) == ',') at[0]++;
		}
		assertEquals(json.length(), skip(json, at[0] + 1), file.getFileName() + ": text after the object");
		return out;
	}

	private static String string(String json, int[] at, Path file) {
		expect(json, at, '"', file);
		StringBuilder out = new StringBuilder();
		for (char c; (c = json.charAt(at[0]++)) != '"'; ) {
			if (c != '\\') {
				out.append(c);
				continue;
			}
			char e = json.charAt(at[0]++);
			switch (e) {
				case 'n' -> out.append('\n');
				case 't' -> out.append('\t');
				case 'u' -> {
					out.append((char) Integer.parseInt(json.substring(at[0], at[0] + 4), 16));
					at[0] += 4;
				}
				default -> out.append(e);
			}
		}
		return out.toString();
	}

	private static void expect(String json, int[] at, char c, Path file) {
		assertEquals(c, json.charAt(at[0]), file.getFileName() + ": expected '" + c + "' at " + at[0]);
		at[0]++;
	}

	private static int skip(String json, int at) {
		while (at < json.length() && Character.isWhitespace(json.charAt(at))) at++;
		return at;
	}

	/** {@code assets/dragonsworn/lang}, found from the test's working directory (a Stonecutter version folder). */
	static Path lang() {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path lang = dir.resolve("src/mc/shared/resources/assets/dragonsworn/lang");
			if (Files.isDirectory(lang)) return lang;
		}
		throw new IllegalStateException("lang folder not found above " + Path.of("").toAbsolutePath());
	}
}
