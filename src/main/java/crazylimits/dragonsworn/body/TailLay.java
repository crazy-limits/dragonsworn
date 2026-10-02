package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.nav.BlockGrid;

import static crazylimits.dragonsworn.body.Tail.N;

/**
 * A standing tail ({@link TailMotion.Pose#rest}) lowered from its root until its lowest point rests on what
 * is under it: on the real blocks when there is a world, so on a slope or at a ledge it follows the ground
 * (hanging no further than {@link #MAX_DROOP} past the flat-ground pose), else on the flat ground the
 * animations were made for (model y = 0). The first step of {@link Tail#solve}; stateless.
 */
public final class TailLay {
	/** How far past the flat-ground pose a laid tail may hang to reach lower ground (ledge, slope), degrees. */
	public static final double MAX_DROOP = 35.0;
	/** How far above the flat-ground pose a laid tail may be raised to rest on higher ground, degrees. */
	static final double MAX_RAISE = 45.0;
	/** The root bend range searched for the flat-ground lay, degrees (the tail curls under past it). */
	static final double LAY_MIN = -80.0, LAY_MAX = 60.0;
	/** How far above the ground the lowest point of a laid tail rests (blocks, half a pixel). */
	static final double REST_GAP = 0.5 / 16.0;

	private TailLay() {
	}

	/**
	 * {@code motion} with a laid tail lowered onto the ground into {@code outX}/{@code outY}: the root's
	 * bend (shared by its two segments) that brings the lowest point to rest, minus the lift.
	 */
	public static void lay(TailChain chain, TailMotion.Pose motion, Tail.World world, double[] outX, double[] outY) {
		System.arraycopy(motion.x, 0, outX, 0, N);
		System.arraycopy(motion.y, 0, outY, 0, N);
		double root = 0.0;
		if (motion.rest > 0.0) {
			double flat = rootFor(chain, outX, outY, null, LAY_MIN, LAY_MAX);
			root = world == null || world.grid == null ? flat
					: rootFor(chain, outX, outY, world, flat - MAX_RAISE, flat + MAX_DROOP);
			root *= motion.rest;
		}
		root -= motion.lift;
		outX[0] += root / 2;
		outX[1] += root / 2;
	}

	/**
	 * The root bend in [lo, hi] that rests the tail's lowest point on the ground: the lowest the tail
	 * goes while still clear of it (bisection; the tail sinks as the root bends down). {@code hi} when it
	 * hangs free even there, {@code lo} when even that is not clear.
	 */
	private static double rootFor(TailChain chain, double[] x, double[] y, Tail.World world, double lo, double hi) {
		double x0 = x[0], x1 = x[1];
		double[] cap = new double[9];
		if (clearance(chain, x, y, x0, x1, hi, world, cap) >= 0.0) {
			x[0] = x0;
			x[1] = x1;
			return hi;
		}
		if (clearance(chain, x, y, x0, x1, lo, world, cap) < 0.0) {
			x[0] = x0;
			x[1] = x1;
			return lo;
		}
		for (int it = 0; it < 24; it++) {
			double mid = (lo + hi) / 2;
			if (clearance(chain, x, y, x0, x1, mid, world, cap) >= 0.0) lo = mid;
			else hi = mid;
		}
		x[0] = x0;
		x[1] = x1;
		return lo;
	}

	/** How far the tail's lowest point is above the ground (blocks) with the root bent by {@code root}. */
	private static double clearance(TailChain chain, double[] x, double[] y, double x0, double x1, double root, Tail.World world, double[] cap) {
		x[0] = x0 + root / 2;
		x[1] = x1 + root / 2;
		chain.pose(x, y);
		double low = Double.POSITIVE_INFINITY;
		for (int s = 0; s < N; s++) {
			chain.capsule(s, cap);
			double r = TailChain.radius(s);
			for (int e = 0; e < 2; e++) {
				double gap;
				if (world == null || world.grid == null) {
					gap = cap[e * 3 + 1] - r;
				} else {
					PartSolver.toWorld(world.body, world.partialTick, cap, e, cap, 2);
					double wx = world.x + cap[6], wy = world.y + cap[7], wz = world.z + cap[8];
					double ground = groundBelow(world.grid, wx, wy, wz);
					// nothing under it near enough: it does not rest there
					if (Double.isNaN(ground)) continue;
					gap = wy - r - ground;
				}
				low = Math.min(low, gap);
			}
		}
		return low - REST_GAP;
	}

	/**
	 * The ground under (x, y, z): the top of the first blocked cell at or below it within a few blocks
	 * (a step the point has sunk into counts). NaN when there is none, or when the blocks there rise
	 * above the point: that is a wall, which the collisions keep the tail out of, not ground to lie on.
	 */
	private static double groundBelow(BlockGrid grid, double x, double y, double z) {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		int top = (int) Math.floor(y);
		if (grid.blocked(bx, top + 1, bz)) return Double.NaN;
		for (int cy = top; cy > top - 8; cy--) {
			if (grid.blocked(bx, cy, bz)) return cy + 1.0;
		}
		return Double.NaN;
	}
}
