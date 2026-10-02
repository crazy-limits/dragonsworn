package crazylimits.dragonfall.nav;

import crazylimits.dragonfall.anim.DragonAnim;

/**
 * A landing at speed, an eagle's: instead of stopping above a site and hovering down, the dragon glides in
 * along a straight approach, flares, strikes the ground with its feet ahead of it and skids out to a stop
 * on the site ({@link DragonAnim#LAND}).
 *
 * <p>The way in, from far to near: the {@link #lead} point lines the dragon up with the approach; from the
 * {@link #entry} point it glides straight down the approach line, and the landing starts (the gear goes
 * down) once it is {@link #startDistance} from {@link #touch}, where the feet strike; the site is where
 * it stops. A runway fits when the strip from touch to site is
 * flat ({@link #STRIP_STEP} up or down at most), open above for the body, and the glide down to it is
 * open air. From the entry the dragon flies a path fixed in advance ({@link #path}): a cubic from where
 * it is, at the speed it has, to the touch point at {@link #TOUCH_SPEED} on the touch tick, then a skid
 * that bleeds that speed off evenly over {@link #SKID_TICKS}.
 *
 * <p>Positions are the dragon's (its feet), blocks; velocities blocks per tick.
 */
public final class Runway {
	/** Speed along the ground as the feet strike (blocks/tick), and the skid's length (ticks). */
	public static final double TOUCH_SPEED = 0.45;
	public static final int SKID_TICKS = 14;
	/** How far the skid carries it: it stops on the site. */
	public static final double SKID = TOUCH_SPEED * SKID_TICKS / 2.0;
	/** Still sinking a little as the feet strike (blocks/tick): it drops onto them out of the stall. */
	static final double TOUCH_SINK = 0.12;
	/**
	 * Ticks from the start of the landing to the feet striking the ground, as the model shows it: the
	 * animation's own time plus the blend into it.
	 */
	public static final int TOUCH_TICKS = (int) Math.round(DragonAnim.LAND_TOUCH_SECONDS * 20) + DragonAnim.BLEND_TICKS;
	/**
	 * The entry: this far before the touch point along the approach, this high above it (a ~15 degree
	 * glide); far enough out for a landing started at any speed up to {@link #MAX_SPEED}.
	 */
	public static final double GLIDE_IN = 42.0, ENTRY_HEIGHT = 10.0;
	/** The fastest a landing starts at (blocks/tick): faster, it is slowed to this. */
	public static final double MAX_SPEED = 1.4;
	/** The lead point: this much further back and higher, to line up. */
	public static final double LEAD = 22.0, LEAD_HEIGHT = 4.0;
	/** The strip: no column more than this above or below the site; this many blocks either side clear. */
	static final int STRIP_STEP = 1, STRIP_HALF_WIDTH = 2, BODY_HEIGHT = 5;
	/** Going at least this fast (blocks/tick), a dragon about to land lands running. */
	public static final double MIN_SPEED = 0.45;

	public final double dirX, dirZ;
	public final double[] touch, entry, lead, site;

	private Runway(double dirX, double dirZ, double[] site) {
		this.dirX = dirX;
		this.dirZ = dirZ;
		this.site = site;
		touch = along(site, -SKID, 0.0);
		entry = along(touch, -GLIDE_IN, ENTRY_HEIGHT);
		lead = along(entry, -LEAD, LEAD_HEIGHT);
	}

	private double[] along(double[] from, double distance, double up) {
		return new double[]{from[0] + dirX * distance, from[1] + up, from[2] + dirZ * distance};
	}

	/**
	 * How far from the touch point (along the ground) a landing at {@code speed} starts, so that it slows
	 * evenly to {@link #TOUCH_SPEED} by the touch.
	 */
	public static double startDistance(double speed) {
		return (Math.min(speed, MAX_SPEED) + TOUCH_SPEED) / 2.0 * TOUCH_TICKS;
	}

	/** Yaw of the approach (Minecraft's: 0 = +z, 90 = -x), degrees. */
	public float yaw() {
		return (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
	}

	/**
	 * A runway onto the site {x, y, z} (its block; the dragon stops on its center) for a dragon coming
	 * from (fromX, fromZ), or null when the strip or the glide down to it is blocked.
	 */
	public static Runway plan(BlockGrid grid, int[] site, double fromX, double fromZ) {
		double sx = site[0] + 0.5, sz = site[2] + 0.5;
		double dx = sx - fromX, dz = sz - fromZ, len = Math.hypot(dx, dz);
		if (len < 1e-6) return null;
		Runway r = new Runway(dx / len, dz / len, new double[]{sx, site[1], sz});
		return r.stripClear(grid, site[1]) && r.airClear(grid, r.entry, r.touch) && r.airClear(grid, r.lead, r.entry) ? r : null;
	}

	/** The ground from a little before the touch point to a little past the site: flat and open above. */
	private boolean stripClear(BlockGrid grid, int y) {
		double length = SKID + 4.0;
		for (double d = -2.0; d <= length; d += 1.0) {
			for (int side = -STRIP_HALF_WIDTH; side <= STRIP_HALF_WIDTH; side++) {
				int x = (int) Math.floor(touch[0] + dirX * d - dirZ * side), z = (int) Math.floor(touch[2] + dirZ * d + dirX * side);
				int g = grid.ground(x, z);
				if (g == BlockGrid.NO_GROUND || Math.abs(g - y) > STRIP_STEP) return false;
				for (int h = g; h < y + BODY_HEIGHT; h++) if (grid.blocked(x, h, z)) return false;
			}
		}
		return true;
	}

	/** The air the body passes through from a to b (both feet positions): no block in the way. */
	private boolean airClear(BlockGrid grid, double[] a, double[] b) {
		double dist = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
		int steps = (int) Math.ceil(dist);
		for (int i = 0; i <= steps; i++) {
			double k = (double) i / steps;
			double x = a[0] + (b[0] - a[0]) * k, y = a[1] + (b[1] - a[1]) * k, z = a[2] + (b[2] - a[2]) * k;
			for (int side = -STRIP_HALF_WIDTH; side <= STRIP_HALF_WIDTH; side++) {
				int bx = (int) Math.floor(x - dirZ * side), bz = (int) Math.floor(z + dirX * side);
				// near the touch point the feet reach the strip: only the body above them must be clear
				for (int h = (int) Math.floor(y) + 1; h < y + BODY_HEIGHT; h++) if (grid.blocked(bx, h, bz)) return false;
			}
		}
		return true;
	}

	/**
	 * Where the dragon is {@code tick} ticks into the landing that started at {@code start} moving at
	 * {@code velocity}: {x, y, z, vx, vy, vz}. Up to {@link #TOUCH_TICKS} a cubic through the air (it
	 * starts from exactly where and how fast the dragon was going, and strikes the touch point at
	 * {@link #TOUCH_SPEED}, still sinking a little), then the skid along the ground to the site;
	 * {@code groundY} is the ground under it there.
	 */
	public double[] path(double[] start, double[] velocity, int tick, double groundY) {
		if (tick <= TOUCH_TICKS) {
			double T = TOUCH_TICKS, s = tick / T;
			double h00 = 2 * s * s * s - 3 * s * s + 1, h10 = s * s * s - 2 * s * s + s, h01 = -2 * s * s * s + 3 * s * s, h11 = s * s * s - s * s;
			double d00 = 6 * s * s - 6 * s, d10 = 3 * s * s - 4 * s + 1, d01 = -6 * s * s + 6 * s, d11 = 3 * s * s - 2 * s;
			double[] end = {dirX * TOUCH_SPEED, -TOUCH_SINK, dirZ * TOUCH_SPEED};
			double[] out = new double[6];
			for (int i = 0; i < 3; i++) {
				out[i] = h00 * start[i] + h10 * T * velocity[i] + h01 * touch[i] + h11 * T * end[i];
				out[i + 3] = (d00 * start[i] + d10 * T * velocity[i] + d01 * touch[i] + d11 * T * end[i]) / T;
			}
			return out;
		}
		int t = Math.min(tick - TOUCH_TICKS, SKID_TICKS);
		double speed = TOUCH_SPEED * (1.0 - (double) t / SKID_TICKS);
		double d = TOUCH_SPEED * t - TOUCH_SPEED * t * t / (2.0 * SKID_TICKS);
		return new double[]{touch[0] + dirX * d, groundY, touch[2] + dirZ * d, dirX * speed, 0.0, dirZ * speed};
	}

	/** Whether {@code tick} is on the ground (the feet have struck). */
	public static boolean grounded(int tick) {
		return tick >= TOUCH_TICKS;
	}

	private static double sq(double v) {
		return v * v;
	}
}
