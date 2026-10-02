package crazylimits.dragonsworn.attack;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.flight.Wingbeat;

/**
 * The hover attacks: where it cannot come down by its prey, the dragon flies in close, stands in the air
 * and bites ({@link DragonAnim#HOVER_BITE}) or pours the stream breath ({@link DragonAnim#HOVER_BREATH}).
 * Game-free (where it hovers, the breath's aim, the timing), so it is unit tested; the bridge's
 * {@code HoverAttackPhase} runs it.
 *
 * <p>To bite it hovers where the bite's pose puts its jaws on the prey's middle (body/Strike's rest
 * point, the IK reaching the rest of the way), facing it; to breathe, {@link #BREATH_DISTANCE} off and
 * {@link #BREATH_RISE} over it. Each attack starts on a beat boundary, so the wings stay in the hover's
 * stroke ({@link #onBeat}). The breath has the pass's timing ({@link BreathPass}: heat, flames and sounds
 * play the same); its aim swings after the prey inside a cone ahead ({@link #PITCH_MIN}..{@link #PITCH_MAX}
 * below level, up a little at prey in the air, {@link #YAW_ARC} either side), and the dragon turns after it.
 */
public final class HoverAttack {
	/** Ticks between bites (their number is {@link DragonConfig#HOVER_BITES}), and how long the whole spell may last. */
	public static final int BITE_RECOVERY = 14, MAX_TICKS = 600;
	/**
	 * It never starts at prey further than this (past where a wild dragon keeps a target, for it may have
	 * roamed on since: it flies in itself), and gives up beyond {@link #LOST_RANGE}.
	 */
	public static final double MAX_RANGE = 160.0, LOST_RANGE = 200.0;
	/** Ticks it may take to get into position before it gives up (the prey keeps away). */
	public static final int APPROACH_TICKS = 400;
	/** How hard a bite pushes the prey away from the dragon (its damage: {@link DragonConfig#HOVER_BITE_DAMAGE}). */
	public static final double BITE_PUSH = 0.9;
	/** Blocks off the prey (across) and over it the breath is poured from. */
	public static final double BREATH_DISTANCE = 11.0, BREATH_RISE = 5.0;
	/** Within this many blocks of where it hovers to attack, it stands in the air (beyond it flies there). */
	public static final double HOVER_RANGE = 20.0, IN_PLACE = 3.0;
	/** The breath's cone, degrees: below level (negative: above) and either side of the facing. */
	public static final double PITCH_MIN = -25.0, PITCH_MAX = 75.0, YAW_ARC = 45.0;
	/** Degrees per tick the breath's aim swings after the prey: brisk while it inhales, slower while it pours. */
	public static final double WINDUP_TURN = 5.0, STREAM_TURN = 3.0;
	/** How far the flames carry, and the damage per hit (every {@link BreathPass#DAMAGE_INTERVAL}). */
	public static final double RANGE = 20.0;
	/** Ticks the breath plays: the animation's three beats, and the blend into it. */
	public static final int BREATH_TICKS = (int) Math.round(3 * Wingbeat.FLAP_SECONDS * 20.0) + DragonAnim.BLEND_TICKS;
	/** Ticks a bite plays: one beat, and the blend into it. */
	public static final int BITE_TICKS = (int) Math.round(Wingbeat.FLAP_SECONDS * 20.0) + DragonAnim.BLEND_TICKS;
	/** Ticks from the start of a bite to the jaws closing, as the model shows it. */
	public static final int HIT_TICKS = FlybyBite.HIT_TICKS;
	/** The aim stops following the prey this long before the blow: the window to dodge. */
	public static final int REACTION_TICKS = 7;

	private HoverAttack() {}

	/** Whether the hover's beat is at a boundary ({@code phase} 0..1; -1: not beating), one tick's worth either way. */
	public static boolean onBeat(double phase) {
		double tick = 1.0 / (Wingbeat.FLAP_SECONDS * 20.0);
		return phase >= 0.0 && (phase < tick || phase > 1.0 - tick);
	}

	/**
	 * Where the dragon hovers to bite: the prey's middle (px, py, pz) less the jaws' resting place at the
	 * blow ({@code rest}, relative to the dragon as {forward, up, right} along its facing), facing the prey
	 * from the horizontal unit direction (dx, dz) it comes from. Returns {x, y, z}.
	 */
	public static double[] biteSpot(double px, double py, double pz, double dx, double dz, double[] rest) {
		// facing (dx, dz); its right is (-dz, dx) for vanilla's yaw (the head along (sin, 0, -cos), +x on the right facing north)
		double rx = -dz, rz = dx;
		return new double[] {px - dx * rest[0] - rx * rest[2], py - rest[1], pz - dz * rest[0] - rz * rest[2]};
	}

	/** Where the dragon hovers to breathe at the prey's feet (px, py, pz), coming from the horizontal unit direction (dx, dz). */
	public static double[] breathSpot(double px, double py, double pz, double dx, double dz) {
		return new double[] {px - dx * BREATH_DISTANCE, py + BREATH_RISE, pz - dz * BREATH_DISTANCE};
	}

	/**
	 * The breath's angles toward (dx, dy, dz) from the neck's base, clamped into the hover's cone:
	 * {degrees off the facing {@code yaw}, degrees below level}.
	 */
	public static double[] angles(float yaw, double dx, double dy, double dz) {
		double off = BreathAttack.offFacing(yaw, dx, dz);
		double down = Math.toDegrees(Math.atan2(-dy, Math.hypot(dx, dz)));
		return new double[] {Math.max(-YAW_ARC, Math.min(YAW_ARC, off)), Math.max(PITCH_MIN, Math.min(PITCH_MAX, down))};
	}
}
