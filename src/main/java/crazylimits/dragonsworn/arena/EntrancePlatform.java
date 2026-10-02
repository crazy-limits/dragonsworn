package crazylimits.dragonsworn.arena;

import crazylimits.dragonsworn.arena.Monolith.Block;

/**
 * The End's entrance platform (where the portal drops you) rebuilt as a sphere instead of vanilla's 5x5 slab:
 * its bottom quarter (by height) is obsidian, a bowl whose flat top is the floor, the other three quarters are
 * cleared to air. The floor is vanilla's (its top face at the feet block's y), so the arrival spot is unchanged.
 */
public final class EntrancePlatform {
	/** The sphere's radius in blocks: a floor ~8.5 blocks across, cleared 7.5 blocks up. */
	public static final double RADIUS = 5;
	/** No block is set further than this from the feet block, on any axis. */
	public static final int REACH = (int) Math.ceil(RADIUS);

	private EntrancePlatform() {}

	/**
	 * Every block of the sphere around the feet block {@code (x, y, z)} (vanilla's {@code createEndPlatform}
	 * origin): obsidian below y, air from y up.
	 */
	public static void forEach(int x, int y, int z, Monolith.Visitor visitor) {
		// the cut plane (the floor's top, y) lies a quarter of the diameter up from the bottom
		double cy = y + RADIUS / 2;
		for (int dy = -REACH; dy <= REACH * 2; dy++)
			for (int dz = -REACH; dz <= REACH; dz++)
				for (int dx = -REACH; dx <= REACH; dx++) {
					double ry = y + dy + 0.5 - cy;
					if (dx * dx + ry * ry + dz * dz > RADIUS * RADIUS) continue;
					visitor.block(x + dx, y + dy, z + dz, dy < 0 ? Block.OBSIDIAN : Block.AIR);
				}
	}
}
