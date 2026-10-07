package crazylimits.dragonsworn.nav;

import crazylimits.dragonsworn.limb.GroundFit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the dragon can grip on any {@link Surface.Face}: the ground, or a wall (its face's frame makes it
 * ground, {@link SurfaceGrid}). A site fits when each of its four feet ({@link GroundFit#FOOTPRINT}, turned
 * to its heading) finds the face within {@link #STANCE} of the others, nothing stands out of the face round
 * it more than that, and the body has {@link #HEIGHT} blocks of air over the face ({@link #RADIUS} round).
 * Holes in the face do not matter where no foot is (it grips beside a tunnel's mouth). Coming from the air,
 * the space in front of it must be open too ({@code approach}). On a wall it is always upright, heading
 * straight up the face ({@link Surface.Face#upYaw}), and it never grips a sheer face: a hind foot must
 * stand on something, a block right under its heel in the world (a ledge, a step of a stepped wall, the
 * ground at the wall's foot: {@link #heels}), the hands on the face above as anywhere on a wall; the site
 * is lowered ({@link Site#drop}) so the heel rests on it. What is below that does not matter (the body
 * hangs above its feet). Every face is tried, sheer, stepped back or diagonal ({@link Surface.Face}); of a
 * wall's faces the one it lies flattest in wins ({@link #ROUGH}).
 *
 * <p>Two searches: {@link #near} a point (where to land, a wall too, to reach prey there) and
 * {@link #toward} a point from where the dragon is (the next hop on its way across the ground: onto a
 * ledge, down off one). It never walks or hops onto a wall: it lands there from the air. Game-free.
 */
public final class SurfaceSites {
	public static final int RADIUS = 5, STANCE = 2, HEIGHT = 7, APPROACH_RADIUS = 4;
	/**
	 * The wall pose's hind feet (anims.WALL_FEET as solved): their lowest point (the back toe's tip) below
	 * the position, down the face (blocks). Sideways they stand a block off its middle, in the next blocks.
	 */
	public static final double HEEL = 36 / 16.0;
	/** ... their middles off its middle to each side, and their height out of the face (blocks). */
	static final double HIND = 1.0, HEEL_OUT = 3 / 16.0;
	/** How far out from the body's height a foot's face is looked for (blocks): a stepped face may stand out that far. */
	static final double REACH_OUT = 2.5;
	/** Rows down the face from the standing block from which nothing counts (the foothold's, and below: the body hangs above them). */
	static final int FOOTHOLD = (int) Math.ceil(0.5 + HEEL);
	/** What a block of the face's rise or fall across the site costs it (the face it lies flattest in wins). */
	static final double ROUGH = 1.5;
	/** On a wall, what a block of the point's being off to the side (not straight above) costs a site. */
	static final double ASIDE = 0.7;
	/** The faces tried, ground first. */
	private static final Surface.Face[] FACES = Surface.Face.values();

	/**
	 * A place to stand: the face, the standing block in its frame (x, y, z local), the heading there (local
	 * yaw, degrees) and, on a wall, how far below the standing block's middle the position is ({@code drop},
	 * blocks: its heels on the foothold).
	 */
	public record Site(Surface.Face face, int x, int y, int z, float yaw, double drop) {
		public Site(Surface.Face face, int x, int y, int z, float yaw) {
			this(face, x, y, z, yaw, 0.0);
		}

		/** Where the dragon's position goes, world: the middle of the standing block's face, {@link #drop} lower. */
		public double[] world() {
			double[] w = face.toWorld(new double[]{x + 0.5, y, z + 0.5}, new double[3]);
			w[1] -= drop;
			return w;
		}
	}

	private final BlockGrid world;

	public SurfaceSites(BlockGrid world) {
		this.world = world;
	}

	/**
	 * Standing (local) y if the dragon fits on {@code grid} ({@code face}'s view) at x, z heading {@code yaw},
	 * with {@code approach} blocks open in front of it (0: none needed); else {@link BlockGrid#NO_GROUND}.
	 */
	public static int fits(BlockGrid grid, Surface.Face face, int x, int z, double yaw, int approach) {
		double a = Math.toRadians(-yaw), c = Math.cos(a), s = Math.sin(a);
		int[] feet = new int[GroundFit.FOOTPRINT.length];
		double sum = 0;
		for (int i = 0; i < feet.length; i++) {
			double mx = GroundFit.FOOTPRINT[i][0], mz = GroundFit.FOOTPRINT[i][1];
			feet[i] = grid.ground((int) Math.floor(x + 0.5 + mx * c + mz * s), (int) Math.floor(z + 0.5 - mx * s + mz * c));
			if (feet[i] == BlockGrid.NO_GROUND) return BlockGrid.NO_GROUND;
			sum += feet[i];
		}
		int h = (int) Math.round(sum / feet.length);
		for (int f : feet) if (Math.abs(f - h) > STANCE) return BlockGrid.NO_GROUND;
		// on a wall: something under a hind heel, and nothing counts from the foothold's row down the face
		double[] down = face.wall() ? down(face) : null;
		if (down != null && Double.isNaN(heels(SurfaceGrid.world(grid), face, x + 0.5, h, z + 0.5))) return BlockGrid.NO_GROUND;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
				if (down != null && dx * down[0] + dz * down[1] >= FOOTHOLD) continue;
				int g = grid.ground(x + dx, z + dz);
				if (g != BlockGrid.NO_GROUND && g > h + STANCE) return BlockGrid.NO_GROUND;
				int from = g == BlockGrid.NO_GROUND ? h : Math.max(g, h);
				for (int y = from; y < h + HEIGHT; y++) {
					if (grid.blocked(x + dx, y, z + dz)) return BlockGrid.NO_GROUND;
				}
			}
		}
		for (int y = h + HEIGHT; y < h + approach; y++) {
			for (int dx = -APPROACH_RADIUS; dx <= APPROACH_RADIUS; dx += 2) {
				for (int dz = -APPROACH_RADIUS; dz <= APPROACH_RADIUS; dz += 2) {
					if (grid.blocked(x + dx, y, z + dz)) return BlockGrid.NO_GROUND;
				}
			}
		}
		return h;
	}

	/** Down a wall's face in its frame (local x, z, unit; a whole block on a sheer compass wall). */
	static double[] down(Surface.Face face) {
		double[] l = face.toLocal(new double[]{0.0, -1.0, 0.0}, new double[3]);
		double n = Math.hypot(l[0], l[2]);
		return new double[]{l[0] / n, l[2] / n};
	}

	/**
	 * The dragon upright on wall {@code face} with its position at local (lx, ly, lz): how far its position
	 * must go down (world y, blocks, 0..1) for a hind heel (standing on the face under it) to rest on a block
	 * right under it in the world, the heel's own block open; NaN when neither heel has one ({@code world}: the
	 * world itself).
	 */
	public static double heels(BlockGrid world, Surface.Face face, double lx, double ly, double lz) {
		double a = Math.toRadians(-face.upYaw()), c = Math.cos(a), s = Math.sin(a);
		double[] n = face.normal, p = new double[3];
		double best = Double.NaN;
		for (int side = -1; side <= 1; side += 2) {
			double mx = side * HIND;
			// the foot stands on the face under its sole (half a block up from the heel): found straight in along
			// the normal from out in front (exact on any face; a stepped one is not where the body's middle is)
			double sole = HEEL - 0.5;
			face.toWorld(new double[]{lx + mx * c + sole * s, ly + REACH_OUT, lz - mx * s + sole * c}, p);
			double in = Double.NaN;
			for (double d = 0.0; d <= REACH_OUT + STANCE + 1.0; d += 0.125) {
				if (world.blocked((int) Math.floor(p[0] - n[0] * d), (int) Math.floor(p[1] - n[1] * d), (int) Math.floor(p[2] - n[2] * d))) {
					in = d;
					break;
				}
			}
			double hy = Double.isNaN(in) ? ly : ly + REACH_OUT - in;
			face.toWorld(new double[]{lx + mx * c + HEEL * s, hy + HEEL_OUT, lz - mx * s + HEEL * c}, p);
			// a heel a hair into its foothold still stands on it
			int bx = (int) Math.floor(p[0]), by = (int) Math.floor(p[1] + SUNK), bz = (int) Math.floor(p[2]);
			if (world.blocked(bx, by, bz) || !world.blocked(bx, by - 1, bz)) continue;
			double drop = Math.max(0.0, p[1] - by);
			if (Double.isNaN(best) || drop < best) best = drop;
		}
		return best;
	}


	/**
	 * The site, on {@code walls} only or any face, from which the jaws reach the point (px, py, pz) (world):
	 * between {@code minRange} and {@code maxRange} from it across the face (preferably {@code prefer}),
	 * within {@code height} of it out of or into the face, heading for it. Among equals the one nearest
	 * (fx, fy, fz) (where the dragon comes from). Null when nothing fits.
	 */
	public Site near(double px, double py, double pz, double minRange, double maxRange, double prefer, double height,
			boolean walls, int approach, double fx, double fy, double fz) {
		List<Candidate> all = new ArrayList<>();
		double[] p = {px, py, pz}, from = {fx, fy, fz}, l = new double[3];
		for (Surface.Face face : FACES) {
			if (walls && !face.wall()) continue;
			face.toLocal(p, l);
			double lx = l[0], ly = l[1], lz = l[2];
			BlockGrid grid = SurfaceGrid.around(world, face, ly);
			int reach = (int) Math.ceil(maxRange);
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					int x = (int) Math.floor(lx) + dx, z = (int) Math.floor(lz) + dz;
					double ox = lx - (x + 0.5), oz = lz - (z + 0.5), r = Math.hypot(ox, oz);
					if (r < minRange || r > maxRange) continue;
					int g = grid.ground(x, z);
					if (g == BlockGrid.NO_GROUND || Math.abs(g - ly) > height) continue;
					double[] w = face.toWorld(new double[]{x + 0.5, g, z + 0.5}, new double[3]);
					double score;
					float yaw;
					if (face.wall()) {
						// upright on a wall: the point straight up the face ahead of it, a bite's length on
						yaw = face.upYaw();
						double ux = Math.sin(Math.toRadians(yaw)), uz = -Math.cos(Math.toRadians(yaw));
						double along = ox * ux + oz * uz, aside = Math.abs(ox * uz - oz * ux);
						score = Math.abs(along - prefer) + ASIDE * aside + 0.05 * dist(w, from);
					} else {
						yaw = (float) Math.toDegrees(Math.atan2(ox, -oz));
						score = Math.abs(r - prefer) + 0.05 * dist(w, from);
					}
					all.add(new Candidate(face, grid, x, z, yaw, score + ROUGH * rough(grid, x, z)));
				}
			}
		}
		return best(all, approach);
	}

	/**
	 * The next place to hop to on the way to (tx, ty, tz) from (fx, fy, fz) (world), on the ground only (it
	 * gets onto a wall only from the air: {@link #near}): within {@code hop} blocks of where it is, at least
	 * {@code gain} blocks nearer the target than it is now, the nearest to it of those; heading for the
	 * target. Null when no hop gets it nearer.
	 */
	public Site toward(double fx, double fy, double fz, double tx, double ty, double tz, double hop, double gain) {
		double[] f = {fx, fy, fz}, t = {tx, ty, tz};
		double now = dist(f, t);
		List<Candidate> all = new ArrayList<>();
		int reach = (int) Math.ceil(hop);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int x = (int) Math.floor(fx) + dx, z = (int) Math.floor(fz) + dz;
				int g = world.ground(x, z);
				if (g == BlockGrid.NO_GROUND) continue;
				double[] w = {x + 0.5, g, z + 0.5};
				double d = dist(w, f);
				if (d > hop || d < 2.0) continue;
				double left = dist(w, t);
				if (left > now - gain) continue;
				double yaw = Math.toDegrees(Math.atan2(tx - (x + 0.5), -(tz - (z + 0.5))));
				all.add(new Candidate(Surface.Face.FLOOR, world, x, z, (float) yaw, left));
			}
		}
		return best(all, 0);
	}

	/**
	 * Whether the dragon at local (lx, ly, lz) on {@code face} (its position, {@code grid} the face's view)
	 * still has a block under a hind heel ({@link #heels}, within a little); on the ground always (the
	 * ground under it is the walk's to keep).
	 */
	public static boolean standsOn(BlockGrid grid, Surface.Face face, double lx, double ly, double lz) {
		if (!face.wall()) return true;
		double drop = heels(SurfaceGrid.world(grid), face, lx, ly, lz);
		return !Double.isNaN(drop) && drop < STANDS;
	}

	/** How far over its foothold a heel may be and still stand on it, and into it (blocks). */
	static final double STANDS = 0.4, SUNK = 0.05;

	/** How far the face rises or falls across (x, z) in {@code grid}'s frame, both ways two blocks off (blocks). */
	static double rough(BlockGrid grid, int x, int z) {
		return step(grid.ground(x + 2, z), grid.ground(x - 2, z)) + step(grid.ground(x, z + 2), grid.ground(x, z - 2));
	}

	private static double step(int a, int b) {
		return a == BlockGrid.NO_GROUND || b == BlockGrid.NO_GROUND ? 0.0 : Math.abs(a - b);
	}

	private record Candidate(Surface.Face face, BlockGrid grid, int x, int z, float yaw, double score) {}

	private static Site best(List<Candidate> all, int approach) {
		all.sort(Comparator.comparingDouble(Candidate::score));
		int tries = 0;
		for (Candidate c : all) {
			int y = fits(c.grid, c.face, c.x, c.z, c.yaw, approach);
			if (y != BlockGrid.NO_GROUND) {
				double drop = c.face.wall() ? heels(SurfaceGrid.world(c.grid), c.face, c.x + 0.5, y, c.z + 0.5) : 0.0;
				return new Site(c.face, c.x, y, c.z, c.yaw, drop);
			}
			if (++tries > 400) break;
		}
		return null;
	}

	private static double dist(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
