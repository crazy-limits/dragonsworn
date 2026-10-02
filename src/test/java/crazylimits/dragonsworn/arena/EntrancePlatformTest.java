package crazylimits.dragonsworn.arena;

import crazylimits.dragonsworn.arena.Monolith.Block;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntrancePlatformTest {
	private static Map<String, Block> sphere() {
		Map<String, Block> blocks = new HashMap<>();
		EntrancePlatform.forEach(100, 49, 0, (x, y, z, b) -> assertNull(blocks.put(x + "," + y + "," + z, b)));
		return blocks;
	}

	@Test
	void floorUnderTheArrivalSpotAndHeadroomAboveIt() {
		Map<String, Block> s = sphere();
		assertEquals(Block.OBSIDIAN, s.get("100,48,0"));
		for (int y = 49; y <= 55; y++) assertEquals(Block.AIR, s.get("100," + y + ",0"));
		// vanilla's 5x5 floor and its 3 blocks of air are all inside
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++) {
				assertEquals(Block.OBSIDIAN, s.get((100 + dx) + ",48," + dz));
				for (int y = 49; y <= 51; y++) assertEquals(Block.AIR, s.get((100 + dx) + "," + y + "," + dz));
			}
	}

	@Test
	void aQuarterOfTheHeightIsObsidian() {
		int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
		for (Map.Entry<String, Block> e : sphere().entrySet()) {
			int y = Integer.parseInt(e.getKey().split(",")[1]);
			low = Math.min(low, y);
			high = Math.max(high, y);
			assertEquals(y < 49 ? Block.OBSIDIAN : Block.AIR, e.getValue());
		}
		int height = high - low + 1, obsidian = 49 - low;
		assertEquals(0.25, obsidian / (double) height, 0.05);
	}
}
