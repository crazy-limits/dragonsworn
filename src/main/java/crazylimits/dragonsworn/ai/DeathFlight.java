package crazylimits.dragonsworn.ai;

/**
 * The dying dragon's last flight, before it wraps itself in its wings ({@code DragonAnim.DEATH}). Brought down
 * (vanilla's {@code DYING} phase, health held at 1), it flies to the island's altar (the exit portal) if there
 * is one, {@link #HEIGHT} over it, slowing into the hover as it closes in ({@link #HOVER_RADIUS}); over it, it
 * rises straight up {@link #RISE} more on its wings. There it is done: the game lets it die, and the cocoon
 * forms as vanilla's light bursts out of it. Without an altar (a wild dragon) it only rises from where it is.
 * It gives up where it is after {@link #MAX_TICKS} (stuck on the way), or once blocked above on the rise.
 *
 * <p>Game-free: positions are blocks, one {@link #tick} per game tick.
 */
public final class DeathFlight {
	public enum Stage { APPROACH, RISE, DONE }

	/** Over the altar's top (blocks) where the approach ends and the rise begins. */
	public static final double HEIGHT = 18.0;
	/** How far it rises over the altar (or where it was) before the cocoon. */
	public static final double RISE = 14.0;
	/** Horizontally this close to the altar it hovers in (blocks); further, it flies. */
	public static final double HOVER_RADIUS = 18.0;
	/** Over the altar: this close horizontally and this close to {@link #HEIGHT} (blocks). */
	public static final double ARRIVED = 2.5, ARRIVED_HEIGHT = 3.0;
	/** The rise is done this close under its top (the hover slows as it nears it). */
	public static final double TOP_MARGIN = 1.0;
	/** Ticks of the whole flight, and of the rise alone, before it dies wherever it is. */
	public static final int MAX_TICKS = 900, MAX_RISE_TICKS = 240;

	private Stage stage = Stage.DONE;
	private double x, y, z, top;
	private int ticks, riseTicks;

	/**
	 * Starts the flight from {@code fromY} (blocks); {@code altar} = {x, top y, z} of the altar, or null
	 * (it rises from {@code fromX}, {@code fromZ}).
	 */
	public void start(double fromX, double fromY, double fromZ, double[] altar) {
		ticks = riseTicks = 0;
		if (altar == null) {
			x = fromX;
			z = fromZ;
			y = fromY;
			beginRise();
		} else {
			x = altar[0];
			y = altar[1] + HEIGHT;
			z = altar[2];
			top = y + RISE;
			stage = Stage.APPROACH;
		}
	}

	private void beginRise() {
		stage = Stage.RISE;
		top = y + RISE;
		riseTicks = 0;
	}

	/** One tick at the dragon's position; {@code blocked}: it ran into something above it. */
	public Stage tick(double px, double py, double pz, boolean blocked) {
		if (stage == Stage.DONE) return stage;
		if (++ticks > MAX_TICKS) return stage = Stage.DONE;
		if (stage == Stage.APPROACH) {
			if (Math.hypot(px - x, pz - z) < ARRIVED && Math.abs(py - y) < ARRIVED_HEIGHT) {
				y = py;
				beginRise();
			}
			return stage;
		}
		if (py >= top - TOP_MARGIN || blocked || ++riseTicks > MAX_RISE_TICKS) stage = Stage.DONE;
		return stage;
	}

	public Stage stage() {
		return stage;
	}

	/** Where it flies now: over the altar at {@link #HEIGHT}, then the top of the rise. */
	public double targetX() {
		return x;
	}

	public double targetY() {
		return stage == Stage.APPROACH ? y : top;
	}

	public double targetZ() {
		return z;
	}

	/** Whether it hovers (rising, or close to the altar) rather than flies, at {@code px}, {@code pz}. */
	public boolean hovers(double px, double pz) {
		return stage != Stage.APPROACH || Math.hypot(px - x, pz - z) < HOVER_RADIUS;
	}
}
