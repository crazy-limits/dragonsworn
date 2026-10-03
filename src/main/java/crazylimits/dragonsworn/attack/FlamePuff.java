package crazylimits.dragonsworn.attack;

/**
 * One puff of a breath stream, flying as its flame particles do ({@code VoidFlameParticle.Breath}, spawned by
 * {@code BreathRender}): it leaves the mouth at the jet's speed plus the dragon's own, each tick moves by its
 * velocity, then slows by {@link #FRICTION} and drifts up by {@link #RISE}. It burns for {@link #FIRE_TICKS}
 * (the particle's fire share of its life); after that it is smoke and harmless. The server moves one per
 * damage interval ({@code BreathFlames}): what it passes through is hurt then, and where it meets a block it
 * splashes, so damage and dragon fire land when and where the flames do, not the moment they leave the mouth.
 * Game-free, so it is unit tested.
 */
public final class FlamePuff {
	/** Blocks per tick the flames leave the mouth with: perched, and in flight (its aim is further off). */
	public static final double JET = 0.85, FLYING_JET = 1.1;
	/** The particle's slowing per tick and its upward drift per tick (as {@code VoidFlameParticle}). */
	public static final double FRICTION = 0.95, RISE = 0.003;
	/** Ticks a puff burns: the breath particle turns to smoke at 0.55 of a 30-43 tick life. */
	public static final int FIRE_TICKS = 20;

	private final double[] pos, vel;
	private final double range;
	private int age;
	private double flown;

	/** A puff at {@code mouth} with {@code velocity} (blocks per tick), burning out after {@code range} blocks at most. */
	public FlamePuff(double[] mouth, double[] velocity, double range) {
		this.pos = mouth.clone();
		this.vel = velocity.clone();
		this.range = range;
	}

	/** Where it is (world). */
	public double[] pos() {
		return pos;
	}

	/** Ticks it has flown. */
	public int age() {
		return age;
	}

	/** How far it reaches round its path: the stream's cone, widening with the distance flown. */
	public double radius() {
		return BreathAttack.MOUTH_RADIUS + BreathAttack.SPREAD * flown;
	}

	/** It still burns: younger than {@link #FIRE_TICKS} and short of its range. */
	public boolean burning() {
		return age < FIRE_TICKS && flown < range;
	}

	/** One tick: moves along its velocity (copying where it was into {@code from}), then slows and drifts up. */
	public void step(double[] from) {
		System.arraycopy(pos, 0, from, 0, 3);
		for (int i = 0; i < 3; i++) pos[i] += vel[i];
		flown += Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2]);
		for (int i = 0; i < 3; i++) vel[i] *= FRICTION;
		vel[1] += RISE;
		age++;
	}

	/** Stops it at {@code at} on its last step (a block it splashed on). */
	public void stopAt(double[] at) {
		System.arraycopy(at, 0, pos, 0, 3);
		age = FIRE_TICKS;
	}

	/** How far a puff launched at {@code speed} flies while it burns (no drift, nothing in the way). */
	public static double reach(double speed) {
		return speed * (1.0 - Math.pow(FRICTION, FIRE_TICKS)) / (1.0 - FRICTION);
	}
}
