package crazylimits.dragonfall.arena;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * One of the End's ten spires, rebuilt as a spiralling obsidian tower instead of vanilla's cylinder, with
 * vanilla's flat top. Every tower winds its own way (direction, turns, proportions), in one of two styles:
 * <ul>
 *   <li>{@link Kind#CROWN}: a twisted tower, its rounded rectangular section turning as it rises, two sharp
 *       corners winding up it;</li>
 *   <li>{@link Kind#WINDOW}: a core wrapped in scroll wings, split by spiral slits crossed by shelves.</li>
 * </ul>
 * The crystal stays where vanilla puts it (on bedrock at {@code height}, on top of the tower, the crystal
 * at {@code height + 1}, centered), so the End fight, the respawn ritual and the crystals' bounding boxes
 * work unchanged. The shape is a pure function of the spike (no world seed), so the respawn ritual
 * rebuilds exactly the same tower over the old one. Every block is blast-proof (obsidian, bedrock). No
 * spike is caged, the guarded ones included.
 */
public final class Monolith {
	public enum Kind { WINDOW, CROWN }

	public enum Block { AIR, OBSIDIAN, BEDROCK }

	/** The island's surface: the y of the top End stone block in a column, or {@link #NONE} over the void. */
	public interface Ground {
		int NONE = Integer.MIN_VALUE;

		int top(int x, int z);
	}

	public interface Visitor {
		void block(int x, int y, int z, Block block);
	}

	/** No block is set further than this from the center column (a feature may write one chunk out). */
	public static final int REACH = 14;

	/** How far the tower swells where it enters the ground, on top of its taper. */
	private static final double FLARE = 1.0;
	/** The width of the winged tower's slits. */
	private static final double SLIT = 3.0;

	public final int centerX, centerZ, radius, height, minY;
	public final Kind kind;
	/** The island surface under the tower, as found around its foot. */
	public final int groundY;

	private final Map<Long, Block> blocks = new LinkedHashMap<>();
	/** Which way it winds (+1 or -1), where the spiral starts, and how many turns it makes up to the top. */
	private final double turn, start, turns;
	/** The twisted tower's section: long over short side. */
	private final double aspect;
	/** The winged tower's wings. */
	private final int wings;
	/** The tower's "radius" at the top and at the foot. */
	private final double topRadius, footRadius;

	private Monolith(int centerX, int centerZ, int radius, int height, int minY, Ground ground) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.radius = radius;
		this.height = height;
		this.minY = minY;
		this.kind = kind(radius, height);
		SplittableRandom random = new SplittableRandom(seed(centerX, centerZ, height));
		turn = random.nextBoolean() ? 1 : -1;
		start = random.nextDouble() * 2 * Math.PI;
		turns = switch (kind) {
			case CROWN -> 0.35 + random.nextDouble() * 0.3;
			case WINDOW -> 0.4 + random.nextDouble() * 0.35;
		};
		aspect = 1.35 + random.nextDouble() * 0.4;
		wings = radius >= 5 ? 3 + random.nextInt(2) : 2 + random.nextInt(2);
		topRadius = radius + 1.0;
		footRadius = topRadius + 1.0;
		groundY = findGround(ground);
	}

	/** Builds the tower of a vanilla End spike; {@code minY} is the world's bottom (the tower goes down to it). */
	public static Monolith build(int centerX, int centerZ, int radius, int height, int minY, Ground ground) {
		Monolith m = new Monolith(centerX, centerZ, radius, height, minY, ground);
		int n = (int) Math.ceil(m.extent()) + 1;
		for (int y = minY; y < m.height; y++)
			for (int dx = -n; dx <= n; dx++)
				for (int dz = -n; dz <= n; dz++) {
					Block b = switch (m.kind) {
						case CROWN -> m.twisted(dx, y, dz);
						case WINDOW -> m.winged(dx, y, dz);
					};
					if (b != null) m.set(dx, y, dz, b);
				}
		m.set(0, height, 0, Block.BEDROCK);
		m.prune();
		return m;
	}

	/**
	 * Which style a spike gets: the thicker ones alternate between a window and a crown, by height so every
	 * End has the same mix; the thin ones are crowns.
	 */
	public static Kind kind(int radius, int height) {
		int rank = Math.floorDiv(height - 76, 3);
		return radius >= 3 && Math.floorMod(rank, 2) == 1 ? Kind.WINDOW : Kind.CROWN;
	}

	/** Where the crystal floats (vanilla's spot, block coordinates of its feet). */
	public int crystalY() {
		return height + 1;
	}

	/** The block at a world position, or {@code null} where the tower leaves the world as it is. */
	public Block at(int x, int y, int z) {
		return blocks.get(key(x - centerX, y, z - centerZ));
	}

	public void forEach(Visitor visitor) {
		for (Map.Entry<Long, Block> e : blocks.entrySet()) {
			long k = e.getKey();
			visitor.block(centerX + unpackX(k), unpackY(k), centerZ + unpackZ(k), e.getValue());
		}
	}

	public int size() {
		return blocks.size();
	}

	/** The furthest any block gets from the center column, horizontally. */
	public double extent() {
		double r = footRadius + FLARE + (kind == Kind.CROWN ? 0.6 : 0);
		return switch (kind) {
			case CROWN -> r * Math.sqrt(aspect + 1 / aspect);
			case WINDOW -> r;
		};
	}

	// ---- the profile ------------------------------------------------------------------------------

	/** How far up the tower y is, from its foot (0) to the crystal (1). */
	private double rise(int y) {
		return Math.clamp((double) (y - groundY) / Math.max(1, height - groundY), 0, 1);
	}

	/** The tower's "radius" at y: tapering up from the foot, flared where it enters the ground. */
	private double profile(int y) {
		double t = rise(y);
		double r = footRadius + (topRadius - footRadius) * t;
		double flare = Math.clamp(1 - (y - groundY) / 6.0, 0, 1);
		return r + FLARE * flare * flare;
	}

	/** The spiral's angle at y. */
	private double spiral(int y) {
		return start + turn * turns * 2 * Math.PI * rise(y);
	}

	// ---- the twisted tower (crown) ------------------------------------------------------------------

	/**
	 * A rounded rectangle turning with the spiral and swelling a little at mid height; two opposite
	 * corners are left sharp, so they wind up it as a double helix.
	 */
	private Block twisted(int dx, int y, int dz) {
		double r = profile(y) + 0.6 * Math.sin(Math.PI * rise(y));
		double a = r * Math.sqrt(aspect), b = r / Math.sqrt(aspect);
		double angle = spiral(y), c = Math.cos(angle), sn = Math.sin(angle);
		double lu = dx * c + dz * sn, lv = -dx * sn + dz * c, u = Math.abs(lu), v = Math.abs(lv);
		if (u > a || v > b) return null;
		if (lu * lv > 0) return Block.OBSIDIAN;                                 // a sharp corner
		double round = Math.min(2.0, b * 0.7);
		double cu = u - (a - round), cv = v - (b - round);
		return cu > 0 && cv > 0 && cu * cu + cv * cv > round * round ? null : Block.OBSIDIAN;
	}

	// ---- the winged tower (window) ------------------------------------------------------------------

	/**
	 * A round core wrapped in wings, each a scroll whose radius grows across it, so every wing's leading
	 * edge stands proud of the next one's foot, with a slit between them winding round the tower and
	 * showing the core, shelves across it every few blocks.
	 */
	private Block winged(int dx, int y, int dz) {
		double rho = Math.hypot(dx, dz);
		double span = 2 * Math.PI / wings;
		double into = Math.floorMod((long) Math.floor(turn * (Math.atan2(dz, dx) - spiral(y)) / (2 * Math.PI) * 1e6), 1_000_000L) / 1e6 * 2 * Math.PI;
		into -= Math.min(wings - 1, Math.floor(into / span)) * span;
		double base = profile(y), gap = SLIT / base;
		double outer = base * (0.8 + 0.2 * Math.clamp((into - gap) / (span - gap), 0, 1));
		if (rho > outer) return null;
		if (rho <= base * 0.5 || into >= gap || rho < 1.2) return Block.OBSIDIAN;   // the core, a wing
		return Math.floorMod(y - groundY, 3) == 0 && rho <= base * 0.8 ? Block.OBSIDIAN : null;   // a shelf
	}

	/**
	 * Drops what voxelizing left hanging: blocks (a thin tip, say) that touch the rest only along an edge.
	 * Everything kept is face-connected to what is below the island's surface.
	 */
	private void prune() {
		ArrayDeque<Long> queue = new ArrayDeque<>();
		Set<Long> held = new HashSet<>();
		for (Map.Entry<Long, Block> e : blocks.entrySet())
			if (e.getValue() != Block.AIR && unpackY(e.getKey()) < groundY && held.add(e.getKey())) queue.add(e.getKey());
		while (!queue.isEmpty()) {
			long k = queue.poll();
			int dx = unpackX(k), y = unpackY(k), dz = unpackZ(k);
			for (long n : new long[]{key(dx + 1, y, dz), key(dx - 1, y, dz), key(dx, y + 1, dz), key(dx, y - 1, dz), key(dx, y, dz + 1), key(dx, y, dz - 1)}) {
				Block b = blocks.get(n);
				if (b != null && b != Block.AIR && held.add(n)) queue.add(n);
			}
		}
		blocks.entrySet().removeIf(e -> e.getValue() != Block.AIR && !held.contains(e.getKey()));
	}

	// ---- helpers ----------------------------------------------------------------------------------

	/**
	 * The island's surface around the foot: the lowest of a ring of samples just outside the shaft, so that
	 * a rebuild over an old monolith finds the same ground as the first build.
	 */
	private int findGround(Ground ground) {
		double at = Math.min(extent() + 2, REACH);
		int lowest = Integer.MAX_VALUE;
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			int top = ground.top(centerX + (int) Math.round(Math.cos(a) * at), centerZ + (int) Math.round(Math.sin(a) * at));
			if (top != Ground.NONE) lowest = Math.min(lowest, top);
		}
		return lowest == Integer.MAX_VALUE ? 60 : lowest;
	}

	private void set(int dx, int y, int dz, Block block) {
		if (Math.abs(dx) > REACH || Math.abs(dz) > REACH) return;
		blocks.put(key(dx, y, dz), block);
	}

	private static long seed(int x, int z, int y) {
		return mix(x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ y * 0x165667B19E3779F9L);
	}

	private static long mix(long h) {
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		return h ^ h >>> 33;
	}

	private static long key(int dx, int y, int dz) {
		return ((long) (dx + 512) << 42) | ((long) (dz + 512) << 32) | (y & 0xFFFFFFFFL);
	}

	private static int unpackX(long k) {
		return (int) (k >>> 42) - 512;
	}

	private static int unpackZ(long k) {
		return (int) ((k >>> 32) & 0x3FF) - 512;
	}

	private static int unpackY(long k) {
		return (int) k;
	}
}
