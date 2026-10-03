package crazylimits.dragonsworn.anim;

import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.flight.FlightModel;

/**
 * Picks the dragon's animation. Game-free, so it is unit tested; the server runs it too (it needs the
 * pose to place hitboxes and time attacks), and the client plays what it picks.
 */
public final class DragonAnimSelector {
	/** What the dragon's current phase amounts to, as far as animation goes. */
	public enum Kind {
		/** Anything airborne: the flight plan decides. */
		AIR,
		/** Standing on the ground (landed to fight or rest): walk or idle, or an action. */
		GROUND,
		/** The vanilla perch on the exit portal: looking around, breathing fire, roaring. */
		PERCH_SCANNING, PERCH_FLAMING, PERCH_ATTACKING,
		/** Perched, pouring the stream breath (it turns in place to sweep it: never a walk). */
		PERCH_BREATH,
		DYING
	}

	/** An animation and a key that changes whenever it must restart from its beginning. */
	public record Choice(DragonAnim anim, int key) {}

	/** Below this horizontal speed (blocks per tick) a dragon on the ground stands still. */
	static final double WALK_THRESHOLD = 0.02;

	private DragonAnimSelector() {}

	/**
	 * @param action    a one-shot (roar, bite, tail sweep, takeoff) the server started, or null
	 * @param actionSeq its sequence number
	 * @param flight    the flight plan (airborne only)
	 * @param horizontalSpeed blocks per tick
	 */
	public static Choice select(Kind kind, DragonAnim action, int actionSeq, FlightModel.Plan flight, double horizontalSpeed) {
		return select(kind, Foothold.STAND, action, actionSeq, flight, horizontalSpeed);
	}

	/** As above; {@code foothold}: how it stands on the ground ({@link Kind#GROUND}), sat up it never walks. */
	public static Choice select(Kind kind, Foothold foothold, DragonAnim action, int actionSeq, FlightModel.Plan flight, double horizontalSpeed) {
		return select(kind, foothold, action, actionSeq, flight, horizontalSpeed > WALK_THRESHOLD);
	}

	/** As above, {@code walking} on the ground given (a {@link Gait}'s: it walks on through a short stall). */
	public static Choice select(Kind kind, Foothold foothold, DragonAnim action, int actionSeq, FlightModel.Plan flight, boolean walking) {
		if (kind == Kind.DYING) return new Choice(DragonAnim.DEATH, 0);
		if (action != null) return new Choice(action, actionSeq);
		if (kind == Kind.PERCH_BREATH) return new Choice(DragonAnim.BREATH, 0);
		if (kind == Kind.GROUND && foothold == Foothold.UPRIGHT) return new Choice(DragonAnim.UPRIGHT, 0);
		if (kind == Kind.GROUND && foothold == Foothold.CLING) return new Choice(DragonAnim.CLING, 0);
		return switch (kind) {
			case PERCH_SCANNING, PERCH_FLAMING, PERCH_ATTACKING, GROUND -> {
				if (walking) yield new Choice(DragonAnim.WALK, 0);
				if (kind == Kind.PERCH_ATTACKING) yield new Choice(DragonAnim.ROAR, 0);
				// Breath: the lunge with the jaw open aims the head at the ground in front.
				if (kind == Kind.PERCH_FLAMING) yield new Choice(DragonAnim.ATTACK, 0);
				yield new Choice(DragonAnim.IDLE, 0);
			}
			default -> switch (flight.mode()) {
				case GLIDE -> new Choice(DragonAnim.GLIDE, 0);
				case FLY -> new Choice(DragonAnim.FLY, flight.sequence());
				case HOVER -> new Choice(DragonAnim.HOVER, flight.sequence());
				case PUSH -> new Choice(DragonAnim.FLAP, flight.sequence());
			};
		};
	}

	/**
	 * Playback speed for {@code anim} so the feet match the ground: the walk runs at the dragon's real
	 * speed over its keyed speed (clamped so a crawl does not freeze it), and stands mid-stride while it does
	 * not move (a {@link Gait}'s stall); everything else plays at 1.
	 */
	public static double playbackSpeed(DragonAnim anim, double horizontalSpeed) {
		if (anim != DragonAnim.WALK) return 1.0;
		if (horizontalSpeed <= WALK_THRESHOLD) return 0.0;
		double blocksPerSecond = horizontalSpeed * 20.0;
		return Math.max(0.35, Math.min(2.5, blocksPerSecond / DragonAnim.WALK_BLOCKS_PER_SECOND));
	}

	/** One action code for the synced data: 0 = none, else the animation's ordinal + 1, and a sequence. */
	public static int encodeAction(DragonAnim anim, int sequence) {
		return (anim == null ? 0 : anim.ordinal() + 1) | (sequence & 0xFFFFFF) << 5;
	}

	public static DragonAnim actionAnim(int bits) {
		int code = bits & 31;
		return code == 0 || code > DragonAnim.values().length ? null : DragonAnim.values()[code - 1];
	}

	public static int actionSequence(int bits) {
		return bits >>> 5;
	}
}
