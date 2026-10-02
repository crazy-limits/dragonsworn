package crazylimits.dragonsworn;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core's shape, checked on its sources: it stays game-free (no Minecraft, loader or GeckoLib import) and
 * its packages form layers (no two packages import each other, directly or round a loop), so any one of them
 * can be understood and tested from the ones below it.
 */
class ArchitectureTest {
	private static final String BASE = "crazylimits.dragonsworn";
	private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);
	private static final List<String> GAME = List.of("net.minecraft.", "net.fabricmc.", "net.neoforged.", "software.bernie.", "com.mojang.");

	@Test
	void theCoreImportsNothingFromTheGameOrALoader() {
		List<String> offenders = new ArrayList<>();
		for (Path file : sources()) {
			for (String imported : imports(file)) {
				if (GAME.stream().anyMatch(imported::startsWith)) offenders.add(core().relativize(file) + ": " + imported);
			}
		}
		assertEquals(List.of(), offenders, "the core (src/main/java) must stay game-free; put this in src/mc instead");
	}

	@Test
	void corePackagesHaveNoDependencyCycles() {
		Map<String, Set<String>> uses = packageGraph();
		List<String> cycles = new ArrayList<>();
		for (String start : uses.keySet()) {
			List<String> loop = loopFrom(start, uses);
			if (loop != null) cycles.add(String.join(" -> ", loop));
		}
		assertEquals(List.of(), cycles, "package imports must form layers: " + uses);
	}

	/** Package (below {@link #BASE}) -> the other core packages its classes import. */
	private static Map<String, Set<String>> packageGraph() {
		Map<String, Set<String>> uses = new TreeMap<>();
		for (Path file : sources()) {
			String from = packageOf(file);
			Set<String> to = uses.computeIfAbsent(from, k -> new TreeSet<>());
			for (String imported : imports(file)) {
				if (!imported.startsWith(BASE + ".")) continue;
				String rest = imported.substring(BASE.length() + 1);
				int dot = rest.indexOf('.');
				String pkg = dot < 0 ? "" : rest.substring(0, dot);
				if (!pkg.equals(from)) to.add(pkg);
			}
		}
		return uses;
	}

	/** A loop of imports leading back to {@code start}, or null. */
	private static List<String> loopFrom(String start, Map<String, Set<String>> uses) {
		List<String> path = new ArrayList<>(List.of(start));
		return walk(start, start, uses, path, new TreeSet<>()) ? path : null;
	}

	private static boolean walk(String start, String at, Map<String, Set<String>> uses, List<String> path, Set<String> seen) {
		for (String next : uses.getOrDefault(at, Set.of())) {
			path.add(next);
			if (next.equals(start)) return true;
			if (seen.add(next) && walk(start, next, uses, path, seen)) return true;
			path.remove(path.size() - 1);
		}
		return false;
	}

	private static String packageOf(Path file) {
		Path rel = core().resolve(BASE.replace('.', '/')).relativize(file.getParent());
		return rel.getNameCount() == 0 || rel.toString().isEmpty() ? "" : rel.getName(0).toString();
	}

	private static List<String> imports(Path file) {
		try {
			Matcher m = IMPORT.matcher(Files.readString(file));
			List<String> out = new ArrayList<>();
			while (m.find()) out.add(m.group(2));
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<Path> sources() {
		try (Stream<Path> files = Files.walk(core())) {
			List<Path> out = files.filter(p -> p.toString().endsWith(".java")).toList();
			assertTrue(out.size() > 20, "found the core's sources under " + core());
			return out;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** {@code src/main/java}, found from the test's working directory (a Stonecutter version folder). */
	private static Path core() {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path src = dir.resolve("src/main/java");
			if (Files.isDirectory(src.resolve(BASE.replace('.', '/')))) return src;
		}
		throw new IllegalStateException("src/main/java not found above " + Path.of("").toAbsolutePath());
	}
}
