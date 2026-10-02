package crazylimits.dragonfall.arena;

import crazylimits.dragonfall.arena.Monolith.Block;
import crazylimits.dragonfall.arena.Monolith.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MonolithTest {
	/** A rolling island surface around y 60. */
	private static final Monolith.Ground ISLAND = (x, z) -> 60 + (int) Math.round(2 * Math.sin(x * 0.3) + 1.5 * Math.cos(z * 0.23));

	/** The ten spikes as vanilla lays them out (radius and height by rank). */
	private static List<Monolith> vanilla() {
		List<Monolith> all = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			int x = (int) Math.floor(42.0 * Math.cos(2.0 * (-Math.PI + Math.PI / 10 * i)));
			int z = (int) Math.floor(42.0 * Math.sin(2.0 * (-Math.PI + Math.PI / 10 * i)));
			all.add(Monolith.build(x, z, 2 + i / 3, 76 + i * 3, 0, ISLAND));
		}
		return all;
	}

	private static boolean solid(Block b) {
		return b != null && b != Block.AIR;
	}

	@Test
	void everyEndGetsTheSameMix() {
		Map<Kind, Integer> count = new EnumMap<>(Kind.class);
		for (Monolith m : vanilla()) count.merge(m.kind, 1, Integer::sum);
		assertEquals(4, count.get(Kind.WINDOW));
		assertEquals(6, count.get(Kind.CROWN));
	}

	@Test
	void theCrystalKeepsVanillasSpot() {
		for (Monolith m : vanilla()) {
			int x = m.centerX, z = m.centerZ, y = m.crystalY();
			assertEquals(m.height + 1, y);
			assertEquals(Block.BEDROCK, m.at(x, m.height, z), m.kind + " stands the crystal on bedrock");
			// the crystal's 2x2x2 box, centered on the column
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++)
					for (int dy = 0; dy < 3; dy++)
						assertFalse(solid(m.at(x + dx, y + dy, z + dz)), m.kind + ": the crystal's space is clear at " + dx + ", " + dy + ", " + dz);
		}
	}

	@Test
	void staysWithinAFeaturesReach() {
		for (Monolith m : vanilla())
			m.forEach((x, y, z, b) -> {
				assertTrue(Math.abs(x - m.centerX) <= Monolith.REACH && Math.abs(z - m.centerZ) <= Monolith.REACH);
				assertTrue(y >= m.minY);
			});
	}

	@Test
	void nothingFloats() {
		for (Monolith m : vanilla()) {
			Set<List<Integer>> solids = new HashSet<>();
			m.forEach((x, y, z, b) -> { if (solid(b)) solids.add(List.of(x, y, z)); });
			// flood from the foot (everything below the surface)
			ArrayDeque<List<Integer>> queue = new ArrayDeque<>();
			Set<List<Integer>> seen = new HashSet<>();
			for (List<Integer> p : solids)
				if (p.get(1) <= m.groundY - 1 && seen.add(p)) queue.add(p);
			int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
			while (!queue.isEmpty()) {
				List<Integer> p = queue.poll();
				for (int[] s : steps) {
					List<Integer> q = List.of(p.get(0) + s[0], p.get(1) + s[1], p.get(2) + s[2]);
					if (solids.contains(q) && seen.add(q)) queue.add(q);
				}
			}
			solids.removeAll(seen);
			assertTrue(solids.isEmpty(), m.kind + " h" + m.height + ": floating blocks " + solids);
		}
	}

	@Test
	void theCrystalStandsOnAnOpenFlatTop() {
		for (Monolith m : vanilla()) {
			int x = m.centerX, z = m.centerZ, y = m.crystalY();
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++)
					assertEquals(Monolith.Block.OBSIDIAN, m.at(x + dx, m.height - 1, z + dz), m.kind + ": a flat top under the crystal");
			int open = 0;
			for (int a = 0; a < 360; a += 5) {
				double dx = Math.cos(Math.toRadians(a)), dz = Math.sin(Math.toRadians(a));
				if (clearRay(m, x + 0.5, y + 1, z + 0.5, dx, dz)) open++;
			}
			boolean sky = true;
			for (int dy = 1; dy < 30; dy++) sky &= !solid(m.at(x, y + dy, z));
			assertEquals(72, open, m.kind + ": nothing round the crystal");
			assertTrue(sky, m.kind + ": nothing over the crystal");
			m.forEach((bx, by, bz, b) -> assertTrue(by < m.height || b == Block.BEDROCK,
				m.kind + ": nothing on the flat top but the crystal's bedrock"));
		}
	}

	@Test
	void isAPureFunctionOfTheSpike() {
		Monolith a = Monolith.build(42, 0, 4, 97, 0, ISLAND), b = Monolith.build(42, 0, 4, 97, 0, ISLAND);
		assertEquals(a.size(), b.size());
		a.forEach((x, y, z, block) -> assertEquals(block, b.at(x, y, z)));
	}

	@Test
	void findsTheGroundBesideItself() {
		// a rebuild probes the ground over the old monolith: it must never probe a column it occupies
		for (int i = 0; i < 10; i++) {
			Set<List<Integer>> probed = new HashSet<>();
			int x0 = (int) Math.floor(42.0 * Math.cos(2.0 * (-Math.PI + Math.PI / 10 * i)));
			int z0 = (int) Math.floor(42.0 * Math.sin(2.0 * (-Math.PI + Math.PI / 10 * i)));
			Monolith m = Monolith.build(x0, z0, 2 + i / 3, 76 + i * 3, 0, (x, z) -> {
				probed.add(List.of(x, z));
				return ISLAND.top(x, z);
			});
			m.forEach((x, y, z, b) -> assertFalse(solid(b) && probed.contains(List.of(x, z)),
				m.kind + " h" + m.height + " stands on a column its ground is probed in: " + x + ", " + z));
		}
	}

	/** Whether a horizontal ray from the crystal leaves the monolith's reach without hitting a block. */
	private static boolean clearRay(Monolith m, double x, double y, double z, double dx, double dz) {
		for (double t = 0; t < Monolith.REACH + 2; t += 0.1)
			if (solid(m.at((int) Math.floor(x + dx * t), (int) Math.floor(y), (int) Math.floor(z + dz * t)))) return false;
		return true;
	}
}
