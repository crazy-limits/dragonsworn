package crazylimits.dragonfall.anim;

import java.util.Locale;

/** Every animation in {@code ender_dragon.animation.json}, in the order {@code tools/anims.py} writes them. */
public enum DragonAnim {
	IDLE(true),
	/** The wing-walk: hind feet and the wrists of the folded wings. */
	WALK(true),
	/** Continuous wingbeats in level or climbing flight: every downstroke drives the dragon forward. */
	FLY(true),
	/** One push from a glide: up, down until the wings point down, then into the glide. */
	FLAP(false),
	GLIDE(true),
	/** Standing up in the air: chest up, tail hanging, head level; the beats hold it at a height. */
	HOVER(true),
	/** Crouch on all fours, all four push off together, then the wings beat it up (ends in the hover). */
	TAKEOFF(false),
	/**
	 * Landing at speed, an eagle's: legs swung forward, a flare and one braking stroke, then the wings
	 * sweep down so all four feet strike together at {@link #LAND_TOUCH_SECONDS}, the hind feet ahead of
	 * the body; it skids out over them into the stance.
	 */
	LAND(false),
	ROAR(false),
	/** The bite: cock back, strike with the jaw open, snap shut. The game aims the neck onto the prey (body/Strike). */
	ATTACK(false),
	/** The tail strike: raised rattling tail (the telegraph), then the whip; the game aims it at the prey (body/Strike). */
	TAIL_SWEEP(false),
	/** The stream breath: inhale (the telegraph), then fire poured at the ground ahead. See BreathAttack. */
	BREATH(false),
	DEATH(false);

	/**
	 * Ground speed the walk cycle is keyed to, in blocks per second: stride 36 px over 75% of a 2.4 s
	 * cycle (see {@code tools/walk.py}). Played at this speed, a planted foot does not slide.
	 */
	public static final double WALK_BLOCKS_PER_SECOND = 36.0 / (0.75 * 2.4) / 16.0;
	/** One wingbeat ({@link #FLY}, {@link #HOVER}, {@link #FLAP}), in seconds. */
	public static final double FLAP_SECONDS = 1.6;
	/**
	 * One {@link #FLAP} push, in seconds: a whole beat out of the glide and back into it (see
	 * {@code tools/anims.py} flap).
	 */
	public static final double PUSH_SECONDS = FLAP_SECONDS;
	/**
	 * Where in a beat (0..1) the downstroke runs: the wings push air between these phases (55 % of the
	 * beat, as big birds do). Beat phase 0 is wings level and rising; they are at the top at the start
	 * of the downstroke and at the bottom at its end ({@code tools/flight.py}).
	 */
	public static final double DOWNSTROKE_START = 0.225, DOWNSTROKE_END = 0.775;
	/**
	 * {@link #TAKEOFF}: when all four limbs push off the ground together (the jump), when the wings reach
	 * the top and the first power stroke starts, and its length. It ends on a beat boundary of the hover
	 * that follows; the hover's beat (on the server) starts at {@link #TAKEOFF_PHASE} at the jump so the
	 * two stay in step.
	 */
	public static final double TAKEOFF_JUMP_SECONDS = 0.55, TAKEOFF_TOP_SECONDS = 0.8, TAKEOFF_SECONDS = 2.04;
	public static final double TAKEOFF_PHASE = DOWNSTROKE_START - (TAKEOFF_TOP_SECONDS - TAKEOFF_JUMP_SECONDS) / FLAP_SECONDS;
	/**
	 * {@link #LAND}: the flare starts, the braking stroke is done, all four feet strike the ground (the
	 * wings swept down onto their claws), and its length (it ends standing).
	 */
	public static final double LAND_FLARE_SECONDS = 0.5, LAND_BRAKE_SECONDS = 1.5, LAND_TOUCH_SECONDS = 1.9, LAND_SECONDS = 3.3;
	/** {@link #ATTACK}: the moment the jaws close on whatever is in front. */
	public static final double BITE_SECONDS = 0.6;
	/** {@link #TAIL_SWEEP}: the tail's tip lands on its aim. */
	public static final double TAIL_HIT_SECONDS = 0.8;
	/** {@link #ROAR}: the roar itself starts (after rearing up): the jaw snaps open and the growl plays. */
	public static final double ROAR_SECONDS = 0.6;
	/**
	 * Ticks the model blends into a new animation before that animation's timeline starts (GeckoLib's
	 * controller transition). Anything timed to what the model shows is this much later than the
	 * animation's own seconds.
	 */
	public static final int BLEND_TICKS = 6;

	private final boolean loops;
	private final String id;

	DragonAnim(boolean loops) {
		this.loops = loops;
		this.id = "animation.ender_dragon." + name().toLowerCase(Locale.ROOT);
	}

	public boolean loops() {
		return loops;
	}

	/** Name of the animation in the GeckoLib animation file. */
	public String id() {
		return id;
	}

	/** Parses {@code walk}, {@code WALK} or the full id; null when nothing matches. */
	public static DragonAnim byName(String name) {
		for (DragonAnim anim : values()) {
			if (anim.name().equalsIgnoreCase(name) || anim.id.equals(name)) return anim;
		}
		return null;
	}

	/** Strength of a downstroke at beat phase {@code u} (0..1, wraps): 0 outside it, peaking at 1. */
	public static double downstroke(double u) {
		u -= Math.floor(u);
		if (u < DOWNSTROKE_START || u > DOWNSTROKE_END) return 0.0;
		return Math.sin(Math.PI * (u - DOWNSTROKE_START) / (DOWNSTROKE_END - DOWNSTROKE_START));
	}
}
