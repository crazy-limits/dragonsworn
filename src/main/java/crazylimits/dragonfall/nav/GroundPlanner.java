package crazylimits.dragonfall.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Walking paths on the ground. A column is walkable when the dragon's feet find ground there and around
 * it ({@link #FOOT} blocks either way, within {@link #STANCE} of each other), its legs fit above
 * ({@link #BODY_HEIGHT} blocks clear) and its body, wider than its stance, does too: nothing but a
 * low step under its belly within {@link #BODY_RADIUS} blocks. From one column to the next it climbs at
 * most {@link #STEP_UP} and drops at most {@link #STEP_DOWN}. A* over the 8 neighbours, a little
 * dearer beside walls (it keeps off them when it can), then pulled taut along walkable lines.
 */
public final class GroundPlanner {
	public static final int FOOT = 1, STANCE = 2, BODY_HEIGHT = 6, STEP_UP = 1, STEP_DOWN = 2;
	/** Half-width of the body over the path (blocks), and the height its belly clears. */
	public static final int BODY_RADIUS = 2, BELLY = 1;
	/** Extra cost of a step with something solid right beside the body. */
	static final double WALL_COST = 0.6;

	private final BlockGrid grid;
	private final Map<Long, Integer> standCache = new HashMap<>();

	public GroundPlanner(BlockGrid grid) {
		this.grid = grid;
	}

	/** Standing y at column x, z, or {@link BlockGrid#NO_GROUND} if the dragon cannot stand there. */
	public int stand(int x, int z) {
		long key = AirPlanner.key(x, 0, z);
		Integer known = standCache.get(key);
		if (known != null) return known;
		int h = grid.ground(x, z);
		int result = h;
		if (h != BlockGrid.NO_GROUND) {
			check:
			for (int dx = -FOOT; dx <= FOOT; dx++) {
				for (int dz = -FOOT; dz <= FOOT; dz++) {
					int g = grid.ground(x + dx, z + dz);
					if (g == BlockGrid.NO_GROUND || Math.abs(g - h) > STANCE) {
						result = BlockGrid.NO_GROUND;
						break check;
					}
					for (int dy = Math.max(g, h); dy < h + BODY_HEIGHT; dy++) {
						if (grid.blocked(x + dx, dy, z + dz)) {
							result = BlockGrid.NO_GROUND;
							break check;
						}
					}
				}
			}
			if (result != BlockGrid.NO_GROUND && !clear(x, h, z, BODY_RADIUS)) result = BlockGrid.NO_GROUND;
		}
		standCache.put(key, result);
		return result;
	}

	/** Whether the ring {@code radius} blocks out from x, z (beyond the feet) is clear from the belly up. */
	private boolean clear(int x, int h, int z, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (Math.abs(dx) <= FOOT && Math.abs(dz) <= FOOT) continue;
				for (int y = h + BELLY; y < h + BODY_HEIGHT; y++) {
					if (grid.blocked(x + dx, y, z + dz)) return false;
				}
			}
		}
		return true;
	}

	/** Whether a wall stands right beside the body at x, z (standing at h). */
	private boolean nearWall(int x, int h, int z) {
		return !clear(x, h, z, BODY_RADIUS + 1);
	}

	private boolean step(int fromY, int toY) {
		return toY != BlockGrid.NO_GROUND && toY - fromY <= STEP_UP && fromY - toY <= STEP_DOWN;
	}

	/**
	 * Path of standing positions {x, y, z} from (fx, fz) toward (tx, tz), stopping within {@code reach}
	 * blocks of it. Empty when the dragon cannot make any progress.
	 */
	public List<int[]> plan(int fx, int fz, int tx, int tz, double reach, int maxNodes) {
		int startY = grid.ground(fx, fz);
		if (startY == BlockGrid.NO_GROUND) return List.of();
		PriorityQueue<Node> open = new PriorityQueue<>();
		Map<Long, Node> seen = new HashMap<>();
		Node start = new Node(fx, startY, fz, null, 0.0, Math.hypot(tx - fx, tz - fz));
		open.add(start);
		seen.put(AirPlanner.key(fx, 0, fz), start);
		Node best = start;
		int expanded = 0;
		while (!open.isEmpty() && expanded < maxNodes) {
			Node n = open.poll();
			if (n.closed) continue;
			n.closed = true;
			expanded++;
			if (n.h < best.h) best = n;
			if (n.h <= reach) {
				best = n;
				break;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) continue;
					int x = n.x + dx, z = n.z + dz;
					long k = AirPlanner.key(x, 0, z);
					double g = n.g + (dx != 0 && dz != 0 ? 1.4142 : 1.0);
					Node old = seen.get(k);
					if (old != null && (old.closed || old.g <= g)) continue;
					int y = stand(x, z);
					if (!step(n.y, y)) continue;
					// diagonal moves must not cut a corner
					if (dx != 0 && dz != 0 && (!step(n.y, stand(n.x + dx, n.z)) || !step(n.y, stand(n.x, n.z + dz)))) continue;
					double cost = g + Math.abs(y - n.y) * 0.5 + (nearWall(x, y, z) ? WALL_COST : 0.0);
					Node next = new Node(x, y, z, n, cost, Math.hypot(tx - x, tz - z));
					seen.put(k, next);
					open.add(next);
				}
			}
		}
		if (best == start) return List.of();
		List<int[]> raw = new ArrayList<>();
		for (Node n = best; n != null; n = n.parent) raw.add(new int[]{n.x, n.y, n.z});
		Collections.reverse(raw);
		return taut(raw);
	}

	/** Whether the dragon can walk straight from a to b (every column on the line, small steps). */
	public boolean lineWalkable(int[] a, int[] b) {
		int dx = b[0] - a[0], dz = b[2] - a[2];
		int steps = Math.max(Math.abs(dx), Math.abs(dz));
		int lastY = a[1];
		for (int i = 1; i <= steps; i++) {
			int x = a[0] + Math.round((float) dx * i / steps), z = a[2] + Math.round((float) dz * i / steps);
			int y = stand(x, z);
			if (!step(lastY, y)) return false;
			lastY = y;
		}
		return true;
	}

	private List<int[]> taut(List<int[]> raw) {
		List<int[]> out = new ArrayList<>();
		int i = 0;
		while (i < raw.size() - 1) {
			int far = i + 1;
			for (int j = raw.size() - 1; j > i + 1; j--) {
				if (lineWalkable(raw.get(i), raw.get(j))) {
					far = j;
					break;
				}
			}
			out.add(raw.get(far));
			i = far;
		}
		return out;
	}

	private static final class Node implements Comparable<Node> {
		final int x, y, z;
		final Node parent;
		final double g, h;
		boolean closed;

		Node(int x, int y, int z, Node parent, double g, double h) {
			this.x = x;
			this.y = y;
			this.z = z;
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
