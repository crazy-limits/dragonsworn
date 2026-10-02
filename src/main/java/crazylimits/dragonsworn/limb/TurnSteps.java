package crazylimits.dragonsworn.limb;

import crazylimits.dragonsworn.math.Maths;

import java.util.Arrays;

/**
 * Turning on the spot by stepping round, the way a big animal (or a person) turns: the feet stay where
 * they are planted while the body turns over them, and a foot left too far behind where the animation
 * would put it ({@link #TRIGGER}) lifts and steps there in an arc, a little past it the way the turn
 * goes ({@link #LEAD_TICKS}), so it lands ahead and the next step comes later. A limb that cannot
 * hold its foot where it stands any more (its IK at the end of its reach: {@link #strain}) steps too,
 * however little it lags and whatever the other pair is doing, the way an animal steps when a leg is
 * at full stretch. Every foot comes down a little in toward the body ({@link #INSET}), the front hands
 * most (they stand on straight arms, at full stretch already), so the limb has room to give as the
 * turn carries the body round over it. The feet go in diagonal
 * pairs (left hind with right front, then the other two), never both pairs at once.
 *
 * <p>Feet are numbered as {@code LimbAnimator} does: 0 left hind, 1 right hind, 2 left front (wrist),
 * 3 right front. Positions are world x, z (blocks); time in ticks. Game-free, so it is unit tested;
 * the renderer moves each foot by {@link #offsetX}/{@link #offsetZ} and lifts it by {@link #lift} (IK).
 */
public final class TurnSteps {
	public static final int FEET = 4;
	/** How far (blocks) a planted foot may fall behind its place before it steps. */
	public static final double TRIGGER = 0.45;
	/** Past this a foot steps whatever its partner pair is doing; past {@link #SNAP} it is put back at once. */
	static final double FORCE = 1.5, SNAP = 2.5;
	/** How long a step takes, ticks, and how high the foot is lifted at its middle, blocks. */
	public static final double STEP_TICKS = 7.0, LIFT = 0.45;
	/** How much quicker a step goes when the other pair is as far behind as {@link #FORCE}. */
	static final double HURRY = 1.5;
	/** A step lands where its place will be this many ticks on (the turn carries on meanwhile). */
	static final double LEAD_TICKS = 6.0;
	/** How fast the effect fades in and out (share per tick). */
	static final double FADE = 0.25;
	/** How far in toward the body (blocks) each foot comes down from its place. */
	static final double[] INSET = {0.3, 0.3, 0.3, 0.3};
	/** The diagonal pair of each foot. */
	static final int[] PAIR = {0, 1, 1, 0};

	/** Called when a foot comes down. */
	public interface Plant {
		void planted(int foot);
	}

	private final double[][] planted = new double[FEET][2], from = new double[FEET][2], to = new double[FEET][2];
	private final double[][] rest = new double[FEET][2], restVelocity = new double[FEET][2];
	/** Progress of each foot's step, 0..1; negative: planted. */
	private final double[] step = new double[FEET];
	private final double[] at = new double[FEET * 2];
	private final boolean[] strained = new boolean[FEET];
	private double cx = Double.NaN, cz = Double.NaN;
	private double weight;
	private boolean started;

	public TurnSteps() {
		Arrays.fill(step, -1.0);
	}

	/**
	 * One frame: {@code places[i]} is where the animation puts foot i now (world x, z); {@code active}:
	 * whether the feet keep their footing this way now (standing, not walking, not in the air).
	 * {@code dt}: ticks since the last frame.
	 */
	public void update(double dt, double[][] places, boolean active, Plant plant) {
		update(dt, places, Double.NaN, Double.NaN, active, plant);
	}

	/** As above, {@code cx, cz}: the middle of the body (world), for the front hands' {@link #INSET}; NaN: none. */
	public void update(double dt, double[][] places, double cx, double cz, boolean active, Plant plant) {
		this.cx = cx;
		this.cz = cz;
		if (!active) {
			fadeOut(dt, places);
			return;
		}
		weight = Math.min(1.0, weight + FADE * dt);
		if (!started) start(places);
		followPlaces(dt, places);
		advanceSteps(dt, plant);
		stepLaggards();
		Arrays.fill(strained, false);
	}

	/** Inactive: the animation takes the feet back, gradually, from where they stand. */
	private void fadeOut(double dt, double[][] places) {
		weight = Math.max(0.0, weight - FADE * dt);
		for (int i = 0; i < FEET; i++) {
			if (step[i] >= 0.0) {
				double[] p = at(i);
				planted[i][0] = p[i * 2];
				planted[i][1] = p[i * 2 + 1];
			}
			step[i] = -1.0;
			rest[i][0] = places[i][0];
			rest[i][1] = places[i][1];
		}
		if (weight == 0.0) started = false;
		Arrays.fill(strained, false);
	}

	/** Becoming active: every foot planted where the animation has it. */
	private void start(double[][] places) {
		for (int i = 0; i < FEET; i++) {
			planted[i][0] = rest[i][0] = places[i][0];
			planted[i][1] = rest[i][1] = places[i][1];
			restVelocity[i][0] = restVelocity[i][1] = 0.0;
			step[i] = -1.0;
		}
		started = true;
	}

	/** Where the animation puts each foot now, and how fast that place moves (smoothed): steps aim ahead along it. */
	private void followPlaces(double dt, double[][] places) {
		for (int i = 0; i < FEET; i++) {
			if (dt > 1e-6) {
				double k = Math.min(1.0, 0.5 * dt);
				restVelocity[i][0] += ((places[i][0] - rest[i][0]) / dt - restVelocity[i][0]) * k;
				restVelocity[i][1] += ((places[i][1] - rest[i][1]) / dt - restVelocity[i][1]) * k;
			}
			rest[i][0] = places[i][0];
			rest[i][1] = places[i][1];
		}
	}

	/** Feet in the air: on toward where their place is going, quicker while the other pair is left far behind. */
	private void advanceSteps(double dt, Plant plant) {
		double[] waiting = new double[2];
		for (int i = 0; i < FEET; i++) if (step[i] < 0.0) waiting[PAIR[i]] = Math.max(waiting[PAIR[i]], urgency(i));
		for (int i = 0; i < FEET; i++) {
			if (step[i] < 0.0) continue;
			double behind = Math.max(0.0, Math.min(1.0, (waiting[1 - PAIR[i]] - TRIGGER) / (FORCE - TRIGGER)));
			step[i] += dt / STEP_TICKS * (1.0 + HURRY * behind);
			target(i, Math.max(0.0, (1.0 - step[i]) * STEP_TICKS) + LEAD_TICKS * 0.5);
			if (step[i] >= 1.0) {
				step[i] = -1.0;
				planted[i][0] = to[i][0];
				planted[i][1] = to[i][1];
				if (plant != null) plant.planted(i);
			}
		}
	}

	/**
	 * Planted feet left behind: the furthest behind steps, its diagonal partner with it; a limb at full
	 * stretch steps now, on its own if the other pair is still in the air; one {@link #SNAP}-far is put back.
	 */
	private void stepLaggards() {
		int worst = -1;
		double worstLag = TRIGGER;
		for (int i = 0; i < FEET; i++) {
			if (step[i] >= 0.0) continue;
			double lag = urgency(i);
			if (lag(i) > SNAP) {
				planted[i][0] = rest[i][0];
				planted[i][1] = rest[i][1];
				continue;
			}
			if (lag > worstLag) {
				worstLag = lag;
				worst = i;
			}
		}
		for (int i = 0; i < FEET; i++) {
			if (!strained[i] || step[i] >= 0.0 || !pairStepping(1 - PAIR[i])) continue;
			beginStep(i);
			if (i == worst) worst = -1;
		}
		if (worst >= 0 && (!pairStepping(1 - PAIR[worst]) || worstLag > FORCE)) {
			for (int i = 0; i < FEET; i++) {
				if (PAIR[i] != PAIR[worst] || step[i] >= 0.0) continue;
				if (i != worst && urgency(i) < TRIGGER * 0.4) continue;
				beginStep(i);
			}
		}
	}

	/** Lifts planted foot i toward where its place will be when it lands. */
	private void beginStep(int i) {
		from[i][0] = planted[i][0];
		from[i][1] = planted[i][1];
		step[i] = 0.0;
		target(i, STEP_TICKS + LEAD_TICKS * 0.5);
	}

	private void target(int i, double ahead) {
		to[i][0] = rest[i][0] + restVelocity[i][0] * ahead;
		to[i][1] = rest[i][1] + restVelocity[i][1] * ahead;
		if (INSET[i] > 0.0 && !Double.isNaN(cx)) {
			double dx = cx - to[i][0], dz = cz - to[i][1], d = Math.hypot(dx, dz);
			if (d > 1e-6) {
				to[i][0] += dx / d * INSET[i];
				to[i][1] += dz / d * INSET[i];
			}
		}
	}

	private double lag(int i) {
		return Math.hypot(planted[i][0] - rest[i][0], planted[i][1] - rest[i][1]);
	}

	private boolean pairStepping(int pair) {
		for (int i = 0; i < FEET; i++) if (PAIR[i] == pair && step[i] >= 0.0) return true;
		return false;
	}

	/** Foot i's limb cannot keep it where it is planted (reported by the IK this frame): it steps next. */
	public void strain(int i) {
		strained[i] = true;
	}

	/** How far behind foot i counts as being: a strained limb as good as {@link #FORCE}-far. */
	private double urgency(int i) {
		return strained[i] ? Math.max(lag(i), FORCE * 0.8) : lag(i);
	}

	/** Where foot i is (world x, z): planted, or along its step. */
	private double[] at(int i) {
		double s = step[i];
		if (s < 0.0) {
			at[i * 2] = planted[i][0];
			at[i * 2 + 1] = planted[i][1];
		} else {
			double u = Maths.smoothstep(s);
			at[i * 2] = from[i][0] + (to[i][0] - from[i][0]) * u;
			at[i * 2 + 1] = from[i][1] + (to[i][1] - from[i][1]) * u;
		}
		return at;
	}

	/** How far foot i must be moved from where the animation puts it (world x), already faded. */
	public double offsetX(int i) {
		return started ? (at(i)[i * 2] - rest[i][0]) * Maths.smoothstep(weight) : 0.0;
	}

	public double offsetZ(int i) {
		return started ? (at(i)[i * 2 + 1] - rest[i][1]) * Maths.smoothstep(weight) : 0.0;
	}

	/** How high foot i is lifted off the ground now (blocks). */
	public double lift(int i) {
		return step[i] < 0.0 ? 0.0 : LIFT * Math.sin(Math.PI * Math.min(1.0, step[i])) * Maths.smoothstep(weight);
	}

	public boolean stepping(int i) {
		return step[i] >= 0.0;
	}

	public double weight() {
		return weight;
	}
}
