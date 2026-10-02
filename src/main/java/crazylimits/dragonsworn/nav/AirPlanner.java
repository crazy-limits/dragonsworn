package crazylimits.dragonsworn.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Flight paths around obstacles. The dragon's body (its hull: head to hips, about 7 x 5 x 7 blocks
 * around the torso) must fit wherever it flies; the wings brush through whatever is breakable.
 *
 * <p>A clear straight line is used as it is. Otherwise an A* search runs over a coarse grid of
 * {@link #CELL}-block cells (26 neighbours), a cell being open when the hull fits at its center, and the
 * result is pulled taut: each waypoint skips ahead to the farthest one still in straight view.
 * One planner per search; it caches which positions are clear.
 */
public final class AirPlanner {
	public static final int CELL = 4;
	/** Hull half-extents around the body center, blocks. */
	public static final int HALF_WIDTH = 3, HALF_HEIGHT = 2;
	/** Spacing of the checks along a straight line, blocks. */
	static final double LINE_STEP = 2.0;

	private final BlockGrid grid;
	private final Map<Long, Boolean> clearCache = new HashMap<>();

	public AirPlanner(BlockGrid grid) {
		this.grid = grid;
	}

	/** Whether the hull fits centered at the block containing x, y, z. */
	public boolean clear(double x, double y, double z) {
		int bx = floor(x), by = floor(y), bz = floor(z);
		long key = key(bx, by, bz);
		Boolean known = clearCache.get(key);
		if (known != null) return known;
		boolean free = true;
		outer:
		for (int dy = -HALF_HEIGHT; dy <= HALF_HEIGHT; dy++) {
			for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
				for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
					if (grid.blocked(bx + dx, by + dy, bz + dz)) {
						free = false;
						break outer;
					}
				}
			}
		}
		clearCache.put(key, free);
		return free;
	}

	/** Whether the hull can fly straight from a to b. */
	public boolean lineClear(double[] a, double[] b) {
		double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int steps = Math.max(1, (int) Math.ceil(length / LINE_STEP));
		for (int i = 1; i <= steps; i++) {
			double k = (double) i / steps;
			if (!clear(a[0] + dx * k, a[1] + dy * k, a[2] + dz * k)) return false;
		}
		return true;
	}

	/**
	 * Waypoints from {@code from} to {@code to} (excluding the start, ending at the goal or the closest
	 * reachable point to it), or an empty list when nothing better than staying put was found.
	 */
	public List<double[]> plan(double[] from, double[] to, int maxNodes) {
		if (lineClear(from, to)) return List.of(to.clone());
		int[] goal = cell(to);
		// a goal inside terrain: aim at the first open cell above it
		for (int i = 0; i < 8 && !clearCell(goal); i++) goal[1]++;
		int[] startCell = cell(from);

		PriorityQueue<Node> open = new PriorityQueue<>();
		Map<Long, Node> seen = new HashMap<>();
		Node start = new Node(startCell, null, 0.0, dist(startCell, goal));
		open.add(start);
		seen.put(key(startCell[0], startCell[1], startCell[2]), start);
		Node best = start;
		int expanded = 0;
		while (!open.isEmpty() && expanded < maxNodes) {
			Node n = open.poll();
			if (n.closed) continue;
			n.closed = true;
			expanded++;
			if (n.h < best.h) best = n;
			if (n.h == 0.0 || (n.h <= 1.8 && lineClear(center(n.c), to))) {
				best = n;
				break;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) continue;
						int[] c = {n.c[0] + dx, n.c[1] + dy, n.c[2] + dz};
						long k = key(c[0], c[1], c[2]);
						double g = n.g + Math.sqrt(dx * dx + dy * dy + dz * dz);
						Node old = seen.get(k);
						if (old != null && (old.closed || old.g <= g)) continue;
						if (old == null && !clearCell(c)) {
							seen.put(k, Node.WALL);
							continue;
						}
						if (old == Node.WALL) continue;
						Node next = new Node(c, n, g, dist(c, goal));
						seen.put(k, next);
						open.add(next);
					}
				}
			}
		}
		if (best == start) return List.of();
		List<double[]> raw = new ArrayList<>();
		for (Node n = best; n != null && n != start; n = n.parent) raw.add(center(n.c));
		Collections.reverse(raw);
		if (best.h <= 1.8 && lineClear(raw.get(raw.size() - 1), to)) raw.add(to.clone());
		return taut(from, raw);
	}

	/** Each point skips to the farthest later one in straight view. */
	private List<double[]> taut(double[] from, List<double[]> raw) {
		List<double[]> out = new ArrayList<>();
		double[] at = from;
		int i = 0;
		while (i < raw.size()) {
			int far = i;
			for (int j = raw.size() - 1; j > i; j--) {
				if (lineClear(at, raw.get(j))) {
					far = j;
					break;
				}
			}
			at = raw.get(far);
			out.add(at);
			i = far + 1;
		}
		return out;
	}

	private boolean clearCell(int[] c) {
		double[] p = center(c);
		return clear(p[0], p[1], p[2]);
	}

	private static int[] cell(double[] p) {
		return new int[]{floor(p[0] / CELL), floor(p[1] / CELL), floor(p[2] / CELL)};
	}

	private static double[] center(int[] c) {
		return new double[]{c[0] * CELL + CELL / 2.0, c[1] * CELL + CELL / 2.0, c[2] * CELL + CELL / 2.0};
	}

	private static double dist(int[] a, int[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}

	static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	private static final class Node implements Comparable<Node> {
		static final Node WALL = new Node(new int[3], null, 0, 0);
		final int[] c;
		final Node parent;
		final double g, h;
		boolean closed;

		Node(int[] c, Node parent, double g, double h) {
			this.c = c;
			this.parent = parent;
			this.g = g;
			this.h = h;
		}

		@Override
		public int compareTo(Node o) {
			return Double.compare(g + h * 1.2, o.g + o.h * 1.2);
		}
	}
}
