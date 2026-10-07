package crazylimits.dragonsworn.nav;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A hiding place: prey in a passage too narrow for the dragon's head (a tunnel dug into a wall, a slit in a
 * spire, a hole in the ground). The passage is followed from the prey out to its mouth, the first place
 * where the head fits (a {@link #HEAD} block cube of air): the dragon puts its jaws in there and bites as far
 * along the passage as they reach, or pours its breath down it. Game-free; world coordinates.
 */
public final class Burrow {
	/** The head's size: a passage is open (its mouth) where a cube this wide is all air (blocks, odd). */
	public static final int HEAD = 3;
	/** How far along a passage the mouth is searched for (blocks walked from the prey). */
	public static final int MAX_DEPTH = 16;
	/** Prey this close to the open (cells walked) is not hiding: the head reaches it anyway. */
	static final int MIN_DEPTH = 3;
	/** Hiding prey has a roof over it within this many blocks over its feet. */
	static final int ROOF = 3;
	private static final int MAX_CELLS = 600;
	private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/** The passage's cells (their middles, world), from the mouth (first) to the prey (last). */
	private final List<double[]> path;
	/** Distance along the path to each cell, blocks. */
	private final double[] along;

	private Burrow(List<double[]> path) {
		this.path = path;
		along = new double[path.size()];
		for (int i = 1; i < path.size(); i++) along[i] = along[i - 1] + dist(path.get(i - 1), path.get(i));
	}

	/**
	 * The passage the prey at (x, y, z) (its feet) hides in, or null when it is out in the open, in a
	 * passage that never opens (sealed in), or too deep to find.
	 */
	public static Burrow find(BlockGrid grid, double x, double y, double z) {
		int[] start = {(int) Math.floor(x), (int) Math.floor(y + 0.2), (int) Math.floor(z)};
		if (grid.blocked(start[0], start[1], start[2])) start[1]++;
		if (grid.blocked(start[0], start[1], start[2]) || open(grid, start) || !roofed(grid, start)) return null;
		Map<Long, int[]> parent = new HashMap<>();
		Map<Long, Integer> depth = new HashMap<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		parent.put(key(start), null);
		depth.put(key(start), 0);
		queue.add(start);
		while (!queue.isEmpty() && parent.size() < MAX_CELLS) {
			int[] c = queue.poll();
			int d = depth.get(key(c));
			if (open(grid, c)) {
				if (d < MIN_DEPTH) return null;
				List<double[]> path = new ArrayList<>();
				for (int[] p = c; p != null; p = parent.get(key(p))) path.add(new double[]{p[0] + 0.5, p[1] + 0.5, p[2] + 0.5});
				return new Burrow(path);
			}
			if (d >= MAX_DEPTH) continue;
			for (int[] s : STEPS) {
				int[] n = {c[0] + s[0], c[1] + s[1], c[2] + s[2]};
				long k = key(n);
				if (parent.containsKey(k) || grid.blocked(n[0], n[1], n[2])) continue;
				parent.put(k, c);
				depth.put(k, d + 1);
				queue.add(n);
			}
		}
		return null;
	}

	/** Whether there is a block over cell c within {@link #ROOF}: out under the sky nothing is hiding. */
	static boolean roofed(BlockGrid grid, int[] c) {
		for (int dy = 1; dy <= ROOF; dy++) if (grid.blocked(c[0], c[1] + dy, c[2])) return true;
		return false;
	}

	/** Whether the head fits round cell c: the {@link #HEAD} cube about it is all air. */
	static boolean open(BlockGrid grid, int[] c) {
		int r = HEAD / 2;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (grid.blocked(c[0] + dx, c[1] + dy, c[2] + dz)) return false;
				}
			}
		}
		return true;
	}

	/** The mouth: the middle of the first cell the head fits in (world). */
	public double[] mouth() {
		return path.get(0).clone();
	}

	/** How far the prey is in from the mouth, blocks (along the passage). */
	public double depth() {
		return along[along.length - 1];
	}

	/** The point {@code d} blocks in from the mouth along the passage (clamped to its ends). */
	public double[] at(double d) {
		if (d <= 0) return mouth();
		for (int i = 1; i < path.size(); i++) {
			if (d <= along[i]) {
				double k = (d - along[i - 1]) / (along[i] - along[i - 1]);
				double[] a = path.get(i - 1), b = path.get(i);
				return new double[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
			}
		}
		return path.get(path.size() - 1).clone();
	}

	/** The way into the passage at the mouth (unit, world): from the mouth toward a little inside. */
	public double[] axis() {
		double[] a = mouth(), b = at(Math.min(2.0, depth()));
		double[] d = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
		double n = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
		if (n < 1e-9) return new double[]{0, 0, 0};
		for (int i = 0; i < 3; i++) d[i] /= n;
		return d;
	}

	/**
	 * Whether the point (x, y, z) is within {@code radius} of the passage between {@code from} and
	 * {@code to} blocks in from the mouth: what a bite that far in reaches.
	 */
	public boolean reaches(double x, double y, double z, double from, double to, double radius) {
		double[] p = {x, y, z};
		double step = 0.25;
		for (double d = Math.max(0.0, from); d <= Math.min(to, depth()) + 1e-9; d += step) {
			if (dist(at(d), p) <= radius) return true;
		}
		return false;
	}

	private static double dist(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static long key(int[] c) {
		return AirPlanner.key(c[0], c[1], c[2]);
	}
}
