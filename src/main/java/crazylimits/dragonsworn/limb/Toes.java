package crazylimits.dragonsworn.limb;

/**
 * The toes of one hind foot: three in front and the back toe, each its own bone hinged at its knuckle
 * ({@code tools/build_wings.py}), turned by the renderer on top of the foot ({@code LimbAnimator}). Each
 * toe has a curl (degrees about its knuckle's X, + lifts its tip, the back toe's too) and a spread
 * (degrees about Y, + swings a front toe's tip outward):
 * <ul>
 *   <li><b>On the ground</b> ({@code contact}) the toes are straight, as built: the dragon stands on its
 *       feet, toes flat, a little splayed under the weight ({@link #STAND_SPREAD}).</li>
 *   <li><b>In the air</b> they hang half curled, the claws drawn in ({@link #AIR_CURL}), stirring a little
 *       ({@link #WAVE}), drawn together.</li>
 *   <li><b>Open</b> ({@code open}: reaching for the ground to land, or for prey) they are lifted and spread
 *       wide ({@link #OPEN_CURL}, {@link #OPEN_SPREAD}).</li>
 * </ul>
 * Between these they follow on a slightly springy lag, so they flick a little when the foot lifts off or
 * lands. <b>Gripping</b> ({@code grip}) is not springy: as the grip comes on, every toe closes as far as it
 * goes ({@link #GRIP_CURL}) or until it meets the prey (the caller's {@code stop}); once the grip is full the
 * closed toes are frozen as they are, and open again only as it lets go.
 */
public final class Toes {
	/** Toes per foot; the last is the back toe. */
	public static final int COUNT = 4, BACK = 3;
	/** Toes hanging in the air: curl of the front toes and of the back toe, and their spread. */
	public static final double AIR_CURL = -28.0, AIR_BACK = -20.0, AIR_SPREAD = 2.0;
	/** How far a stirring toe in the air moves either way, degrees; and how fast (radians per tick). */
	public static final double WAVE = 4.0, WAVE_RATE = 0.11;
	/** The front toes splay this far outward under the weight. */
	public static final double STAND_SPREAD = 5.0;
	/** Open for the landing or the catch: the front toes lifted and spread, the back toe lifted. */
	public static final double OPEN_CURL = 28.0, OPEN_BACK = 15.0, OPEN_SPREAD = 18.0;
	/** Gripping: every toe curls as far as this, stopped by what it holds; the front toes spread a little round it. */
	public static final double GRIP_CURL = -110.0, GRIP_SPREAD = 10.0;
	/** The lag: natural frequency (radians per tick) and damping ratio of each toe's spring. */
	static final double OMEGA = 0.9, ZETA = 0.6;

	private final double[] curl = new double[COUNT], curlV = new double[COUNT];
	private final double[] spread = new double[COUNT], spreadV = new double[COUNT];
	/** The closed grip, per toe, and whether it is frozen (the grip is full). */
	private final double[] closed = new double[COUNT];
	private boolean frozen, started;
	private double grip;
	private final double[] side = new double[COUNT];

	/**
	 * Moves the toes on by {@code dt} ticks. {@code time}: ticks (the air's stirring); {@code contact},
	 * {@code open}: 0..1, the second over the first; {@code grip}: 0..1, how far closed on prey, over
	 * everything; {@code side}: per front toe, which way is outward (+1, -1; 0 for the middle toe);
	 * {@code stop}: per toe the curl at which it meets the prey ({@code -Infinity}: nothing), read only while
	 * the grip closes.
	 */
	public void update(double dt, double time, double contact, double open, double grip, double[] side, double[] stop) {
		this.grip = Math.max(0.0, Math.min(1.0, grip));
		if (this.grip <= 0.0) frozen = false;
		for (int i = 0; i < COUNT; i++) {
			boolean back = i == BACK;
			double s = back ? 0.0 : side[i];
			this.side[i] = s;
			double c = (back ? AIR_BACK : AIR_CURL) + WAVE * Math.sin(WAVE_RATE * time + 1.7 * i);
			double y = AIR_SPREAD;
			c += (0.0 - c) * contact;
			y += (STAND_SPREAD - y) * contact;
			c += ((back ? OPEN_BACK : OPEN_CURL) - c) * open;
			y += (OPEN_SPREAD - y) * open;
			y *= s;
			if (!started) {
				curl[i] = c;
				spread[i] = y;
			}
			double[] a = spring(curl[i], curlV[i], c, dt);
			curl[i] = a[0];
			curlV[i] = a[1];
			a = spring(spread[i], spreadV[i], y, dt);
			spread[i] = a[0];
			spreadV[i] = a[1];
			if (!frozen) closed[i] = Math.max(GRIP_CURL, stop[i]);
		}
		started = true;
		// full: closed where it is, until it lets go
		if (this.grip >= 1.0) frozen = true;
	}

	/** Curl of toe {@code i}, degrees (+ lifts the tip). */
	public double curl(int i) {
		return curl[i] + (closed[i] - curl[i]) * grip;
	}

	/** Spread of toe {@code i}, degrees about Y (already signed by its side). */
	public double spread(int i) {
		return spread[i] + (GRIP_SPREAD * side[i] - spread[i]) * grip;
	}

	/** Whether the toes are closed on prey and frozen so. */
	public boolean frozen() {
		return frozen;
	}

	/** A damped spring toward {@code target}, in steps of at most half a tick: {position, velocity}. */
	private static double[] spring(double x, double v, double target, double dt) {
		int n = Math.max(1, (int) Math.ceil(dt * 2.0));
		double h = dt / n;
		for (int k = 0; k < n; k++) {
			v += (OMEGA * OMEGA * (target - x) - 2.0 * ZETA * OMEGA * v) * h;
			x += v * h;
		}
		return new double[]{x, v};
	}
}
