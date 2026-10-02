package crazylimits.dragonsworn.body;

import crazylimits.dragonsworn.nav.BlockGrid;

/**
 * The tail's final pose, every frame: the animation's motion ({@link TailMotion}), laid on the ground,
 * plus the bends the caller adds (it trails turns, swings against the head, whips in a strike), then
 * kept out of blocks.
 *
 * <ul>
 *   <li><b>Lying down.</b> A standing tail ({@link TailMotion.Pose#rest}) is lowered from its root until
 *       its lowest point rests on what is under it: on the real blocks when there is a world, so on a
 *       slope or at a ledge it follows the ground (hanging no further than {@link TailLay#MAX_DROOP} past the
 *       flat-ground pose), else on the flat ground the animations were made for (model y = 0).</li>
 *   <li><b>Never through a block.</b> Root to tip, each segment is checked as a capsule against the
 *       blocks that stop the dragon ({@link BlockGrid#blocked}; leaves and plants it smashes do not
 *       count). A segment that would pass into one is turned away from it: the smallest extra bend, in
 *       whatever direction clears it, preferring the way out of the block (over a wall, up off the
 *       ground) and a way that leaves the rest of the tail clear too. Segments past it ride along and
 *       are checked in turn, so the tail drapes over, around or along what it meets.</li>
 *   <li><b>Smoothly.</b> Each segment keeps its avoiding bend from the frame before and lets it go
 *       gradually ({@link #RELEASE} per tick), only as far as stays clear, so the tail neither flickers
 *       between two ways round an obstacle nor snaps back when it is past it.</li>
 * </ul>
 * One per caller (the server's hitboxes, the client's renderer): it remembers its avoiding bends.
 */
public final class Tail {
	/** The most a segment turns to keep out of blocks, degrees. */
	public static final double MAX_AVOID = 80.0;
	/** Per tick, the share of an avoiding bend let go when it is no longer needed. */
	static final double RELEASE = 0.12;
	/** Overlap with a block tolerated (blocks): what a capsule's rounding gets wrong anyway. */
	static final double TOLERANCE = 0.02;
	private static final double[] RINGS = {2, 4, 7, 10, 14, 19, 25, 32, 40, 50, 62, 80};
	private static final int DIRECTIONS = 16;
	static final int N = TailChain.SEGMENTS;

	/** Where the dragon is and what is around it; null in a {@link #solve} for the flat ground, no blocks. */
	public static final class World {
		BlockGrid grid;
		DragonBody body;
		float partialTick;
		double x, y, z, time;

		/** The grid, the body as drawn, the dragon's position at {@code partialTick}, and the time (ticks). */
		public World set(BlockGrid grid, DragonBody body, float partialTick, double x, double y, double z, double time) {
			this.grid = grid;
			this.body = body;
			this.partialTick = partialTick;
			this.x = x;
			this.y = y;
			this.z = z;
			this.time = time;
			return this;
		}
	}

	private final double[] avoidX = new double[N], avoidY = new double[N];
	private final double[] cap = new double[6], world = new double[6], probe = new double[3];
	private final Cells cells = new Cells();
	private double lastTime = Double.NaN;
	/** The last frame's worst overlap with a block, blocks: 0 when the tail is clear (for tests and the showcase). */
	private double overlap;

	/**
	 * The tail's bends this frame into {@code outX}/{@code outY}: {@code motion} laid on the ground, plus
	 * {@code bendX}/{@code bendY} (or null), kept out of blocks when {@code world} is given. {@code chain}
	 * must hang from the body as it is drawn; it is left posed as returned.
	 */
	public void solve(TailChain chain, TailMotion.Pose motion, double[] bendX, double[] bendY, World world, double[] outX, double[] outY) {
		TailLay.lay(chain, motion, world, outX, outY);
		if (bendX != null) {
			for (int i = 0; i < N; i++) {
				outX[i] += bendX[i];
				outY[i] += bendY[i];
			}
		}
		if (world == null || world.grid == null) {
			chain.pose(outX, outY);
			overlap = 0.0;
			return;
		}
		double dt = Double.isNaN(lastTime) ? 1.0 : Math.max(0.0, Math.min(5.0, world.time - lastTime));
		lastTime = world.time;
		avoid(chain, world, outX, outY, dt);
	}

	/** The last solve's worst overlap with a block (blocks): 0 when clear. */
	public double overlap() {
		return overlap;
	}

	// ---------------------------------------------------------------- keeping out of blocks

	private void avoid(TailChain chain, World world, double[] outX, double[] outY, double dt) {
		cells.reset(world.grid);
		double keep = Math.pow(1.0 - RELEASE, dt);
		overlap = 0.0;
		for (int s = 0; s < N; s++) {
			// the bend held from the last frame, let go a little
			double px = avoidX[s], py = avoidY[s], rx = px * keep, ry = py * keep;
				if (penetration(chain, world, s, outX[s] + rx, outY[s] + ry, null) <= TOLERANCE) {
				avoidX[s] = rx;
				avoidY[s] = ry;
			} else if (penetration(chain, world, s, outX[s] + px, outY[s] + py, null) <= TOLERANCE) {
				// let go only as far as stays clear
				double clear = 1.0, blocked = keep;
				for (int it = 0; it < 6; it++) {
					double mid = (clear + blocked) / 2;
					if (penetration(chain, world, s, outX[s] + px * mid, outY[s] + py * mid, null) <= TOLERANCE) clear = mid;
					else blocked = mid;
				}
				avoidX[s] = px * clear;
				avoidY[s] = py * clear;
			} else {
				search(chain, world, s, outX, outY, rx, ry);
			}
			chain.segment(s, outX[s] + avoidX[s], outY[s] + avoidY[s]);
			overlap = Math.max(overlap, penetration(chain, world, s, outX[s] + avoidX[s], outY[s] + avoidY[s], null));
		}
		for (int s = 0; s < N; s++) {
			outX[s] += avoidX[s];
			outY[s] += avoidY[s];
		}
	}

	/**
	 * Segment {@code s} is in a block even with its held bend: rings of extra bends round {@code (cx, cy)},
	 * nearest first; the first ring with clear bends gives the one that moves the segment most along the
	 * way out of the block and leaves the segments past it clearest. Nothing clear: the least overlap.
	 */
	private void search(TailChain chain, World world, int s, double[] x, double[] y, double cx, double cy) {
		penetration(chain, world, s, x[s] + cx, y[s] + cy, probe);
		double nx = probe[0], ny = probe[1], nz = probe[2];
		// where the segment's end is now, to see which way each candidate moves it
		chain.capsule(s, cap);
		double ex = cap[3], ey = cap[4], ez = cap[5];
		double bestScore = Double.NEGATIVE_INFINITY, bestX = cx, bestY = cy;
		double leastPen = Double.POSITIVE_INFINITY, leastX = cx, leastY = cy;
		for (double ring : RINGS) {
			boolean found = false;
			for (int d = 0; d < DIRECTIONS; d++) {
				double a = 2 * Math.PI * d / DIRECTIONS;
				double ax = cx + ring * Math.cos(a), ay = cy + ring * Math.sin(a);
				double m = Math.hypot(ax, ay);
				if (m > MAX_AVOID) {
					ax *= MAX_AVOID / m;
					ay *= MAX_AVOID / m;
				}
				double pen = penetration(chain, world, s, x[s] + ax, y[s] + ay, null);
				if (pen < leastPen) {
					leastPen = pen;
					leastX = ax;
					leastY = ay;
				}
				if (pen > TOLERANCE) continue;
				chain.capsule(s, cap);
				double score = (cap[3] - ex) * nx + (cap[4] - ey) * ny + (cap[5] - ez) * nz;
				// what it does to the rest of the tail, carried as it is
				score -= 4.0 * restPenetration(chain, world, s, x, y, ax, ay);
				if (!found || score > bestScore) {
					bestScore = score;
					bestX = ax;
					bestY = ay;
				}
				found = true;
			}
			if (found) {
				avoidX[s] = bestX;
				avoidY[s] = bestY;
				chain.segment(s, x[s] + bestX, y[s] + bestY);
				return;
			}
		}
		avoidX[s] = leastX;
		avoidY[s] = leastY;
	}

	/** How far the segments past {@code s} overlap blocks with {@code s} bent by (ax, ay) extra. */
	private double restPenetration(TailChain chain, World world, int s, double[] x, double[] y, double ax, double ay) {
		chain.segment(s, x[s] + ax, y[s] + ay);
		double total = 0.0;
		for (int k = s + 1; k < N; k++) {
			chain.segment(k, x[k] + avoidX[k], y[k] + avoidY[k]);
			total += penetration(chain, world, k, Double.NaN, Double.NaN, null);
		}
		return total;
	}

	/**
	 * How deep segment {@code s} reaches into blocks (blocks), bent by (bx, by) (NaN: as posed). With
	 * {@code away}, also the direction out of them (model space), summed over the overlaps.
	 */
	private double penetration(TailChain chain, World world, int s, double bx, double by, double[] away) {
		if (!Double.isNaN(bx)) chain.segment(s, bx, by);
		chain.capsule(s, cap);
		PartSolver.toWorld(world.body, world.partialTick, cap, 0, this.world, 0);
		PartSolver.toWorld(world.body, world.partialTick, cap, 1, this.world, 1);
		double r = TailChain.radius(s);
		double ax = this.world[0], ay = this.world[1], az = this.world[2];
		double dx = this.world[3] - ax, dy = this.world[4] - ay, dz = this.world[5] - az;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		// spheres along the axis no further apart than the radius; the root's start is in the body
		int n = Math.max(1, (int) Math.ceil(length / (0.75 * r)));
		double deepest = 0.0, ox = 0.0, oy = 0.0, oz = 0.0;
		for (int i = 1; i <= n; i++) {
			double t = (double) i / n;
			double sx = world.x + ax + dx * t, sy = world.y + ay + dy * t, sz = world.z + az + dz * t;
			int x0 = (int) Math.floor(sx - r), x1 = (int) Math.floor(sx + r);
			int y0 = (int) Math.floor(sy - r), y1 = (int) Math.floor(sy + r);
			int z0 = (int) Math.floor(sz - r), z1 = (int) Math.floor(sz + r);
			for (int cx = x0; cx <= x1; cx++) {
				for (int cy = y0; cy <= y1; cy++) {
					for (int cz = z0; cz <= z1; cz++) {
						if (!cells.blocked(cx, cy, cz)) continue;
						// nearest point of the cell to the sphere's center
						double qx = Math.max(cx, Math.min(cx + 1, sx)), qy = Math.max(cy, Math.min(cy + 1, sy)), qz = Math.max(cz, Math.min(cz + 1, sz));
						double vx = sx - qx, vy = sy - qy, vz = sz - qz, d = Math.sqrt(vx * vx + vy * vy + vz * vz);
						double depth = r - d;
						if (depth <= 0.0) continue;
						if (d < 1e-6) {
							// the center is inside the cell: out through its nearest face
							depth = r;
							double[] faces = {sx - cx, cx + 1 - sx, sy - cy, cy + 1 - sy, sz - cz, cz + 1 - sz};
							int f = 0;
							for (int k = 1; k < 6; k++) if (faces[k] < faces[f]) f = k;
							depth += faces[f];
							vx = f == 0 ? -1 : f == 1 ? 1 : 0;
							vy = f == 2 ? -1 : f == 3 ? 1 : 0;
							vz = f == 4 ? -1 : f == 5 ? 1 : 0;
							d = 1.0;
						}
						deepest = Math.max(deepest, depth);
						ox += vx / d * depth;
						oy += vy / d * depth;
						oz += vz / d * depth;
					}
				}
			}
		}
		if (away != null) {
			// the world direction into the model's axes: toModel is affine, so take the difference of two points
			double[] m = new double[3], o = new double[3];
			PartSolver.toModel(world.body, world.partialTick, ox, oy, oz, m);
			PartSolver.toModel(world.body, world.partialTick, 0.0, 0.0, 0.0, o);
			double mx = m[0] - o[0], my = m[1] - o[1], mz = m[2] - o[2], l = Math.sqrt(mx * mx + my * my + mz * mz);
			if (l < 1e-9) {
				mx = 0.0;
				my = 1.0;
				mz = 0.0;
				l = 1.0;
			}
			away[0] = mx / l;
			away[1] = my / l;
			away[2] = mz / l;
		}
		return deepest;
	}

	/** What {@link BlockGrid#blocked} said, cached for one solve in a window round the tail. */
	private static final class Cells {
		private static final int SIZE = 40, HALF = SIZE / 2;
		private final byte[] known = new byte[SIZE * SIZE * SIZE];
		private BlockGrid grid;
		private int ox, oy, oz;
		private boolean placed;

		void reset(BlockGrid grid) {
			this.grid = grid;
			placed = false;
		}

		boolean blocked(int x, int y, int z) {
			if (!placed) {
				java.util.Arrays.fill(known, (byte) 0);
				ox = x - HALF;
				oy = y - HALF;
				oz = z - HALF;
				placed = true;
			}
			int i = x - ox, j = y - oy, k = z - oz;
			if (i < 0 || j < 0 || k < 0 || i >= SIZE || j >= SIZE || k >= SIZE) return grid.blocked(x, y, z);
			int at = (i * SIZE + j) * SIZE + k;
			if (known[at] == 0) known[at] = grid.blocked(x, y, z) ? (byte) 2 : (byte) 1;
			return known[at] == 2;
		}
	}
}
