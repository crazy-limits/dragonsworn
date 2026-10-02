package crazylimits.dragonfall.anim;

/**
 * The fly-by bite ({@link DragonAnim#GLIDE_BITE}): a dragon that cannot land by its prey (on a wall, a
 * pillar, in the air) swings out for a run, comes back in gliding fast on a line that takes its jaws
 * through the prey's middle, and bites it in passing. Game-free (timing, the line, what a hit does), so
 * it is unit tested; the bridge's {@code FlybyBitePhase} flies it.
 *
 * <p>The bite starts {@link #HIT_TICKS} before the jaws close, so it starts {@link #startDistance} short
 * of where the dragon must be then (its speed times that). The body passes {@link #CLEARANCE} higher
 * than the jaws' resting place in the bite's pose would put it ({@link #PULL} behind it), so over a
 * wall's top it clears the parapet: the neck reaches down the rest (body/Strike's IK, aimed at the prey). The aim follows the
 * prey until {@code REACTION_TICKS} before the blow; a prey out of the jaws' way by then is missed.
 *
 * <p>The hit is the dragon's momentum: the damage and the knockback (along the flight) grow with its
 * speed ({@link #damage}, {@link #knockback}).
 */
public final class FlybyBite {
	/** Ticks from the start of the bite to the jaws closing, as the model shows it (it plays BLEND_TICKS late). */
	public static final int HIT_TICKS = (int) Math.round(DragonAnim.BITE_SECONDS * 20.0) + DragonAnim.BLEND_TICKS;
	/** The aim stops following the prey this long before the blow: the window to dodge. */
	public static final int REACTION_TICKS = 6;
	/** Ticks the bite plays (the animation and the blend into it). */
	public static final int LENGTH_TICKS = (int) Math.round(1.3 * 20.0) + DragonAnim.BLEND_TICKS;
	/**
	 * It swings out at least this far (horizontal blocks) for its run, and never starts one at prey further
	 * than {@link #MAX_RANGE} (past where a wild dragon keeps a target, for it may have roamed on since:
	 * it flies in itself).
	 */
	public static final double RUN_UP = 40.0, MAX_RANGE = 160.0;
	/** Glide speed it holds through the pass, blocks per tick: at least, and at most. */
	public static final double MIN_SPEED = 0.9, MAX_SPEED = 1.3;
	/**
	 * Blocks the body passes over the height the bite's pose would put it at, and behind where it would
	 * put it along the line: the neck reaches down (and back) the rest, its sweet spot for a low bite.
	 */
	public static final double CLEARANCE = 1.5, PULL = 1.5;
	/** The bite starts once the line is within this many blocks of the prey across, and the facing within {@link #LINE_UP} degrees of it. */
	public static final double OFF_LINE = 3.0, LINE_UP = 25.0;
	/** How close (blocks) the jaws must come to a body to hit it: a little more than a standing bite (it sweeps past). */
	public static final double RADIUS = 1.6;
	/** Damage: a base, and per block/tick of speed; at most {@link #MAX_DAMAGE}. */
	public static final float BASE_DAMAGE = 6.0F, SPEED_DAMAGE = 8.0F, MAX_DAMAGE = 20.0F;
	/** Knockback along the flight per block/tick of speed, and the lift with it. */
	public static final double KNOCKBACK = 1.6, LIFT = 0.3, SPEED_LIFT = 0.2;

	private FlybyBite() {}

	/** How far (blocks, along the line) short of where it must be at the blow the bite starts, at {@code speed} blocks/tick. */
	public static double startDistance(double speed) {
		return speed * HIT_TICKS;
	}

	/** The damage of a hit at {@code speed} blocks/tick. */
	public static float damage(double speed) {
		return Math.min(MAX_DAMAGE, BASE_DAMAGE + SPEED_DAMAGE * (float) Math.max(0.0, speed));
	}

	/** The push of a hit (blocks/tick: x, y, z) by a dragon moving at (vx, vy, vz): along its flight, more the faster, and up. */
	public static double[] knockback(double vx, double vy, double vz) {
		double speed = Math.sqrt(vx * vx + vy * vy + vz * vz), horizontal = Math.hypot(vx, vz);
		if (horizontal < 1e-6) return new double[] {0.0, LIFT + SPEED_LIFT * speed, 0.0};
		double push = KNOCKBACK * speed;
		return new double[] {vx / horizontal * push, LIFT + SPEED_LIFT * speed, vz / horizontal * push};
	}

	/**
	 * Where on its line the dragon is, relative to where it must be at the blow (dx, dz from the dragon to
	 * that point), flying along the unit heading (hx, hz): {blocks still to go along it, blocks off it across}.
	 */
	public static double[] alongLine(double dx, double dz, double hx, double hz) {
		return new double[] {dx * hx + dz * hz, Math.abs(dx * hz - dz * hx)};
	}

	/** Whether the bite starts now: lined up, and {@code ahead} blocks short of the point, at {@code speed}. */
	public static boolean due(double ahead, double across, double offFacing, double speed) {
		return ahead > 0.0 && ahead <= startDistance(speed) && across <= OFF_LINE && Math.abs(offFacing) <= LINE_UP;
	}
}
