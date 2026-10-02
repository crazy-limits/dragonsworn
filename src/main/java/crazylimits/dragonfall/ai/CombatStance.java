package crazylimits.dragonfall.ai;

import java.util.random.RandomGenerator;

/**
 * Whether a wild dragon fights its target on foot or from the air. Dragons are lazy: a fight starts on
 * the ground ({@link Stance#GROUND}). Hurt too much there ({@link #GROUND_LIMIT} of its health since it
 * came down, or too many hits at once: {@link HitTally}), it takes a break in the air ({@link Stance#AIR}),
 * attacking from there; after {@link #BREAK_MIN}..{@link #BREAK_MAX} ticks, or hurt {@link #AIR_LIMIT}
 * more up there, it would rather land again and go on with the fight on the ground. With nobody to fight
 * for {@link #CALM_TICKS} it calms down: the next fight starts on the ground again.
 */
public final class CombatStance {
	public enum Stance { GROUND, AIR }

	/** Health lost (a fraction of its maximum) on the ground that sends it up, and in the air that brings it down. */
	public static final double GROUND_LIMIT = 0.2, AIR_LIMIT = 0.1;
	/** How long a break in the air lasts (ticks). */
	public static final int BREAK_MIN = 300, BREAK_MAX = 600;
	public static final int CALM_TICKS = 600;

	private Stance stance = Stance.GROUND;
	private double lost;
	private int breakLeft, quiet;

	public Stance stance() {
		return stance;
	}

	public boolean grounded() {
		return stance == Stance.GROUND;
	}

	/** Health lost to a hit, as a fraction of its maximum. */
	public void hurt(double fraction, RandomGenerator random) {
		quiet = 0;
		lost += fraction;
		if (stance == Stance.GROUND && lost >= GROUND_LIMIT) toAir(random);
		else if (stance == Stance.AIR && lost >= AIR_LIMIT) toGround();
	}

	/** Too many hits at once on the ground (from where it cannot answer): up at once. */
	public void overwhelmed(RandomGenerator random) {
		if (stance == Stance.GROUND) toAir(random);
	}

	/** One tick; {@code fighting}: it has a target. */
	public void tick(boolean fighting) {
		if (fighting) quiet = 0;
		else if (++quiet >= CALM_TICKS) {
			toGround();
			return;
		}
		if (stance == Stance.AIR && --breakLeft <= 0) toGround();
	}

	private void toAir(RandomGenerator random) {
		stance = Stance.AIR;
		lost = 0.0;
		breakLeft = random.nextInt(BREAK_MIN, BREAK_MAX + 1);
	}

	private void toGround() {
		stance = Stance.GROUND;
		lost = 0.0;
	}
}
