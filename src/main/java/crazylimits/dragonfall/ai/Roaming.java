package crazylimits.dragonfall.ai;

import java.util.random.RandomGenerator;

/**
 * A wild dragon with nothing to hunt roams, with no home to keep to: it flies a while, wandering across
 * the land, comes down somewhere along its way, walks about there for a while, and takes off again.
 * Each leg of the wander (in the air or on foot) bends the last one by at most {@link #DRIFT}, so it
 * travels instead of circling one spot. Headings are radians, {@code atan2(dz, dx)}; spells are ticks.
 */
public final class Roaming {
	/** How long it flies before looking for somewhere to land, and how long it stays down. */
	public static final int AIR_MIN = 600, AIR_MAX = 1600, GROUND_MIN = 800, GROUND_MAX = 2000;
	/** Each new leg turns at most this far from the last. */
	public static final double DRIFT = Math.toRadians(55);
	/** Flight legs (blocks), and the height above the ground it cruises at. */
	public static final double FLY_LEG_MIN = 40, FLY_LEG_MAX = 75, CRUISE_MIN = 16, CRUISE_MAX = 32;
	/** Walks (blocks), and the pause (ticks) between them. */
	public static final double WALK_LEG_MIN = 8, WALK_LEG_MAX = 22;
	public static final int PAUSE_MIN = 40, PAUSE_MAX = 200;

	private Roaming() {}

	public static int airSpell(RandomGenerator random) {
		return random.nextInt(AIR_MIN, AIR_MAX + 1);
	}

	public static int groundSpell(RandomGenerator random) {
		return random.nextInt(GROUND_MIN, GROUND_MAX + 1);
	}

	public static int pause(RandomGenerator random) {
		return random.nextInt(PAUSE_MIN, PAUSE_MAX + 1);
	}

	/**
	 * The heading of the next leg. Attempt 0 drifts from {@code heading}; when a leg does not work out
	 * (no ground, the edge of the loaded world, a wall) each further attempt may swing wider, up to
	 * turning right round.
	 */
	public static double nextHeading(double heading, int attempt, RandomGenerator random) {
		double spread = Math.min(Math.PI, DRIFT * (1 + attempt));
		return heading + (random.nextDouble() * 2.0 - 1.0) * spread;
	}

	public static double between(double min, double max, RandomGenerator random) {
		return min + random.nextDouble() * (max - min);
	}

	/** The heading of a dragon with yaw {@code yawDegrees} (a dragon's nose is at sin(yaw), -cos(yaw)). */
	public static double headingOfYaw(float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return Math.atan2(-Math.cos(yaw), Math.sin(yaw));
	}
}
