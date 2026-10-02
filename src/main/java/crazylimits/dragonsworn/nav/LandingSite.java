package crazylimits.dragonsworn.nav;

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
 *
 * <p>Where all four limbs do not fit, a smaller {@link Foothold} may ({@link #fits(int, int, Foothold)}):
 * <ul>
 *   <li>{@link Foothold#UPRIGHT}: ground under the 3 by 3 blocks of its hind feet and tail root, within
 *       {@link #UPRIGHT_STANCE} of the center;</li>
 *   <li>{@link Foothold#CLING}: ground under the center alone (a pillar's top), nothing round it higher.</li>
 * </ul>
 * Either way the body sits up ({@link #UPRIGHT_HEIGHT} clear above the ground, nothing round it within
 * {@link #BODY_RADIUS} higher than a step), the wings held out or beating need the air round it
 * ({@link #WING_RADIUS}, above the ground's level), and it hovers straight down onto it, as above.
 */
public final class LandingSite {
	public static final int RADIUS = 5, STANCE = 2, HEIGHT = 7, APPROACH_RADIUS = 6, APPROACH_HEIGHT = 20;
	/** Narrow footholds: the ground under the feet, the body sat up, the wings held out round it (blocks). */
	public static final int UPRIGHT_STANCE = 1, UPRIGHT_HEIGHT = 9, BODY_RADIUS = 3, WING_RADIUS = 9;
	/** {@link #near}: a narrow foothold this far above or below the prey at most (blocks). */
	public static final int NEAR_HEIGHT = 5;

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
		return approachOpen(x, h + HEIGHT, h, z) ? h : BlockGrid.NO_GROUND;
	}

	/** Standing y if the dragon can come down at x, z with {@code foothold}, else {@link BlockGrid#NO_GROUND}. */
	public int fits(int x, int z, Foothold foothold) {
		if (foothold == Foothold.STAND) return fits(x, z);
		int h = grid.ground(x, z);
		if (h == BlockGrid.NO_GROUND) return h;
		// the feet: the hind feet and the tail's root on the 3 by 3 round the middle (upright), or the middle alone
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int g = grid.ground(x + dx, z + dz);
				if (foothold == Foothold.UPRIGHT ? g == BlockGrid.NO_GROUND || Math.abs(g - h) > UPRIGHT_STANCE : g > h) {
					return BlockGrid.NO_GROUND;
				}
			}
		}
		// the body, sat up: no wall beside it, nothing over it
		for (int dx = -BODY_RADIUS; dx <= BODY_RADIUS; dx++) {
			for (int dz = -BODY_RADIUS; dz <= BODY_RADIUS; dz++) {
				if (dx * dx + dz * dz > BODY_RADIUS * BODY_RADIUS) continue;
				int g = grid.ground(x + dx, z + dz);
				if (g != BlockGrid.NO_GROUND && g > h + UPRIGHT_STANCE) return BlockGrid.NO_GROUND;
				for (int y = g == BlockGrid.NO_GROUND ? h : Math.max(g, h); y < h + UPRIGHT_HEIGHT; y++) {
					if (grid.blocked(x + dx, y, z + dz)) return BlockGrid.NO_GROUND;
				}
			}
		}
		// the wings: held out (their hands droop to the ground's level) or beating
		int low = h + (foothold == Foothold.UPRIGHT ? 2 : 1);
		for (int dx = -WING_RADIUS; dx <= WING_RADIUS; dx += 2) {
			for (int dz = -WING_RADIUS; dz <= WING_RADIUS; dz += 2) {
				int r2 = dx * dx + dz * dz;
				if (r2 <= BODY_RADIUS * BODY_RADIUS || r2 > WING_RADIUS * WING_RADIUS) continue;
				for (int y = low; y < h + UPRIGHT_HEIGHT; y++) {
					if (grid.blocked(x + dx, y, z + dz)) return BlockGrid.NO_GROUND;
				}
			}
		}
		return approachOpen(x, h + UPRIGHT_HEIGHT, h, z) ? h : BlockGrid.NO_GROUND;
	}

	/** Whether the column it hovers down through is open, from {@code from} up. */
	private boolean approachOpen(int x, int from, int h, int z) {
		for (int y = from; y < h + APPROACH_HEIGHT; y++) {
			for (int dx = -APPROACH_RADIUS; dx <= APPROACH_RADIUS; dx += 2) {
				for (int dz = -APPROACH_RADIUS; dz <= APPROACH_RADIUS; dz += 2) {
					if (grid.blocked(x + dx, y, z + dz)) return false;
				}
			}
		}
		return true;
	}

	/**
	 * A narrow foothold ({@code foothold}) between {@code minRange} and {@code maxRange} blocks of the prey
	 * at (cx, cy, cz), within {@link #NEAR_HEIGHT} of its height: every column is tried (a pillar's top is
	 * one block). Scored as {@link #find}. Returns {x, y, z} or null.
	 */
	public int[] near(double cx, double cy, double cz, double minRange, double maxRange, double preferRange,
			double fromX, double fromZ, Foothold foothold) {
		int[] best = null;
		double bestScore = Double.MAX_VALUE;
		double toward = Math.atan2(fromZ - cz, fromX - cx);
		int reach = (int) Math.ceil(maxRange);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int x = (int) Math.floor(cx) + dx, z = (int) Math.floor(cz) + dz;
				double ox = x + 0.5 - cx, oz = z + 0.5 - cz, r = Math.hypot(ox, oz);
				if (r < minRange || r > maxRange) continue;
				double turn = Math.abs(Math.IEEEremainder(Math.atan2(oz, ox) - toward, 2 * Math.PI));
				double score = Math.abs(r - preferRange) + turn * 2.0;
				if (score >= bestScore) continue;
				int g = grid.ground(x, z);
				if (g == BlockGrid.NO_GROUND || Math.abs(g - cy) > NEAR_HEIGHT) continue;
				int y = fits(x, z, foothold);
				if (y == BlockGrid.NO_GROUND) continue;
				best = new int[]{x, y, z};
				bestScore = score;
			}
		}
		return best;
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
