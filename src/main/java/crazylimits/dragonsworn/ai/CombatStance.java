package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;

import java.util.random.RandomGenerator;

/**
 * Whether a wild dragon fights its target on foot or from the air. Dragons are lazy: a fight starts on
 * the ground ({@link Stance#GROUND}). Hurt too much there ({@link DragonConfig#GROUND_HEALTH_LIMIT} of its
 * health since it came down, or too many hits at once: {@link HitTally}), it takes a break in the air
 * ({@link Stance#AIR}), attacking from there; after {@link DragonConfig#BREAK_MIN}..{@link DragonConfig#BREAK_MAX}
 * ticks, or hurt {@link DragonConfig#AIR_HEALTH_LIMIT} more up there, it would rather land again and go on with
 * the fight on the ground. With nobody to fight for {@link DragonConfig#CALM_TICKS} it calms down: the next
 * fight starts on the ground again. All of these are the server's {@link DragonConfig} ({@code [stance]}).
 */
public final class CombatStance {
	public enum Stance { GROUND, AIR }


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
		if (stance == Stance.GROUND && lost >= DragonConfig.GROUND_HEALTH_LIMIT.get()) toAir(random);
		else if (stance == Stance.AIR && lost >= DragonConfig.AIR_HEALTH_LIMIT.get()) toGround();
	}

	/** Too many hits at once on the ground (from where it cannot answer): up at once. */
	public void overwhelmed(RandomGenerator random) {
		if (stance == Stance.GROUND) toAir(random);
	}

	/** One tick; {@code fighting}: it has a target. */
	public void tick(boolean fighting) {
		if (fighting) quiet = 0;
		else if (++quiet >= DragonConfig.CALM_TICKS.get()) {
			toGround();
			return;
		}
		if (stance == Stance.AIR && --breakLeft <= 0) toGround();
	}

	private void toAir(RandomGenerator random) {
		stance = Stance.AIR;
		lost = 0.0;
		breakLeft = DragonConfig.between(DragonConfig.BREAK_MIN, DragonConfig.BREAK_MAX, random);
	}

	private void toGround() {
		stance = Stance.GROUND;
		lost = 0.0;
	}
}
