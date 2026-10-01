package crazylimits.dragonfall.nav;

/**
 * Where the dragon can come down. A site fits when:
 * <ul>
 *   <li>there is solid ground under the whole footprint (a disc of {@link #RADIUS} blocks: body, legs
 *       and folded wings), no column more than {@link #STANCE} above or below the center;</li>
 *   <li>the body fits above it ({@link #HEIGHT} blocks clear), and</li>
 *   <li>the column it descends through, hovering with its wings spread, is open
 *       ({@link #APPROACH_RADIUS} blocks around, up to {@link #APPROACH_HEIGHT} above).</li>
 * </ul>
 * Breakable blocks (leaves, plants) count as empty: it crashes down through them. Nothing fits: it
 * stays in the air.
 */
public final class LandingSite {
	public static final int RADIUS = 5, STANCE = 2, HEIGHT = 7, APPROACH_RADIUS = 6, APPROACH_HEIGHT = 20;

	private final BlockGrid grid;

	public LandingSite(BlockGrid grid) {
		this.grid = grid;
	}

	/** Standing y if the dragon can land with its origin at x, z, else {@link BlockGrid#NO_GROUND}. */
	public int fits(int x, int z) {
		int h = grid.ground(x, z);
		if (h == BlockGrid.NO_GROUND) return h;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
				int g = grid.ground(x + dx, z + dz);
				if (g == BlockGrid.NO_GROUND || g > h + STANCE || g < h - STANCE) return BlockGrid.NO_GROUND;
				for (int y = Math.min(g, h); y < h + HEIGHT; y++) {
					if (y >= g && grid.blocked(x + dx, y, z + dz)) return BlockGrid.NO_GROUND;
				}
			}
		}
		for (int y = h + HEIGHT; y < h + APPROACH_HEIGHT; y++) {
			for (int dx = -APPROACH_RADIUS; dx <= APPROACH_RADIUS; dx += 2) {
				for (int dz = -APPROACH_RADIUS; dz <= APPROACH_RADIUS; dz += 2) {
					if (grid.blocked(x + dx, y, z + dz)) return BlockGrid.NO_GROUND;
				}
			}
		}
		return h;
	}

	/**
	 * The best site between {@code minRange} and {@code maxRange} blocks of (cx, cz): closest to
	 * {@code preferRange} from it, and among equals, nearest the direction of (fromX, fromZ) so the
	 * dragon lands facing its approach. Returns {x, y, z} or null when nothing fits.
	 */
	public int[] find(double cx, double cz, double minRange, double maxRange, double preferRange, double fromX, double fromZ) {
		int[] best = null;
		double bestScore = Double.MAX_VALUE;
		double toward = Math.atan2(fromZ - cz, fromX - cx);
		for (double r = minRange; r <= maxRange + 1e-6; r += 3.0) {
			int count = Math.max(1, (int) Math.round(2 * Math.PI * Math.max(r, 1.0) / 4.0));
			for (int i = 0; i < count; i++) {
				double a = toward + 2 * Math.PI * i / count;
				int x = (int) Math.floor(cx + Math.cos(a) * r), z = (int) Math.floor(cz + Math.sin(a) * r);
				double turn = Math.abs(Math.IEEEremainder(a - toward, 2 * Math.PI));
				double score = Math.abs(r - preferRange) + turn * 2.0;
				if (score >= bestScore) continue;
				int y = fits(x, z);
				if (y == BlockGrid.NO_GROUND) continue;
				best = new int[]{x, y, z};
				bestScore = score;
			}
		}
		return best;
	}
}
