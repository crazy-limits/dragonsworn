package crazylimits.dragonfall.body;

import crazylimits.dragonfall.limb.GroundFit;

/**
 * The procedural layer on top of the keyframes: how the whole body sits in the air and how the neck and
 * tail bend through a turn. One per dragon, ticked on both sides with the dragon's yaw and position, so
 * the server places hitboxes exactly where the client draws the model.
 *
 * <ul>
 *   <li><b>Head first, then body, then tail.</b> The head points where the dragon is steering now; the
 *       body follows {@link #BODY_LAG} ticks later; each tail segment later again. Every neck and tail
 *       segment bends by the change in heading between its two ends.</li>
 *   <li><b>Banking.</b> Turning in flight rolls the body into the turn like a bird or an aeroplane: the
 *       roll that balances the turn, {@code tan(bank) = speed * turn rate / g}, up to almost a
 *       barrel roll. In a banked body a turn is a pull "up" toward the inside of the turn, so the bends
 *       are rotated into the body's frame, and the head is held closer to level than the body.</li>
 *   <li><b>Pitch</b> follows the climb or dive, and the body leans forward when it speeds up (taking
 *       off forward from a hover, accelerating out of a glide).</li>
 *   <li><b>Turning with every part</b> (birds: the head turns first and the body follows about a beat
 *       later; the tail twists against the roll as it starts; the wing inside the turn folds a little).
 *       The head leads by {@link #HEAD_LEAD} ticks of the turn rate, on top of the lag; the tail is a
 *       rudder, swung out further while a turn tightens and back while it opens ({@link #RUDDER}); the
 *       wings turn asymmetric ({@link #wingTurn}): the inside one swept back with its hand drooping, the
 *       outside one reaching forward, and both twisted against the roll rate, the way a roll is started
 *       and stopped.</li>
 *   <li><b>Wings against the pitch.</b> In the air the shoulders turn about their X axis against the
 *       body's whole pitch (keyframed and procedural) so the wings stay level with the ground: chest up,
 *       wings tilt forward; nose down, back. The shoulder only turns {@link #WING_FLEX} degrees.</li>
 * </ul>
 * Angles are degrees. Roll is positive with the right wing down (a right turn: yaw increasing).
 */
public final class DragonBody {
	/** How the body sits over uneven ground (on its feet): pitch, roll and lift added to the flight's. */
	public final GroundFit ground = new GroundFit();

	public enum Mode { FLIGHT, HOVER, GROUND }

	/** Ticks the body lags behind the steering (the head). */
	public static final double BODY_LAG = 4.0;
	/** Extra ticks of lag per tail segment. */
	public static final double TAIL_STEP = 1.1;
	public static final double MAX_BANK = 78.0, MAX_PITCH = 50.0;
	/** Gravity for the banking balance, blocks/tick^2: lower banks harder. */
	static final double G = 0.04;
	static final double MAX_NECK_BEND = 25.0, MAX_TAIL_BEND = 20.0;
	/** How much of the body's roll the head takes back (it stays nearer level). */
	public static final double HEAD_LEVELING = 0.6;
	/** How far a shoulder can turn against the body's pitch, degrees (the muscles' reach). */
	public static final double WING_FLEX = 35.0;
	/** Ticks of the current turn rate the head looks ahead by, and the most it leads (degrees). */
	public static final double HEAD_LEAD = 5.0, MAX_HEAD_LEAD = 30.0;
	/** How the head's lead is shared down the neck, base to head: the head end turns most. */
	private static final double[] NECK_LEAD = {0.1, 0.2, 0.3, 0.4};
	/** Degrees of rudder per degree/tick of tightening turn, on the first tail segments (root to tip). */
	public static final double RUDDER = 6.0;
	private static final double[] RUDDER_SHARE = {0.35, 0.3, 0.2, 0.1, 0.05};
	/** Fully banked (degrees) the wings are fully asymmetric: inside swept back / hand down, outside forward / up. */
	static final double WING_TURN_BANK = 50.0, INSIDE_SWEEP = 12.0, INSIDE_DROOP = 14.0, OUTSIDE_REACH = 6.0, OUTSIDE_LIFT = 5.0;
	/** Degrees of twist per degree/tick of roll, and its limit. */
	static final double ROLL_TWIST = 2.5, MAX_ROLL_TWIST = 12.0;
	private static final int SIZE = 48;

	private final double[] yaw = new double[SIZE], x = new double[SIZE], y = new double[SIZE], z = new double[SIZE];
	private final double[] pitch = new double[SIZE];
	private int latest = -1;
	private double lastRawYaw;
	private double flight, bank, prevBank, lean, smoothPitch, lastSpeed, airborne, prevAirborne;
	/** The steering's turn rate (degrees/tick, smoothed), a slower copy of it, and the roll rate. */
	private double turn, turnSlow, rollRate;
	/** The jaw hold's shake ({@link Grip}): on or off, its weight easing in and out, and its clock (ticks). */
	private boolean shaking;
	private double shake, prevShake, shakeTicks;

	/** Records this tick. Call once per game tick after the dragon moved. */
	public void tick(double yawDegrees, double px, double py, double pz, Mode mode) {
		prevShake = shake;
		shake += ((shaking ? 1.0 : 0.0) - shake) * 0.25;
		shakeTicks = shake > 1e-3 ? shakeTicks + 1.0 : 0.0;
		if (latest < 0 || teleported(px, py, pz)) {
			reset(yawDegrees, px, py, pz);
			return;
		}
		double continuous = yaw[latest] + wrap(yawDegrees - lastRawYaw);
		lastRawYaw = yawDegrees;
		latest = (latest + 1) % SIZE;
		yaw[latest] = continuous;
		x[latest] = px;
		y[latest] = py;
		z[latest] = pz;

		double flightTarget = mode == Mode.FLIGHT ? 1.0 : 0.0;
		flight += (flightTarget - flight) * 0.12;
		prevAirborne = airborne;
		airborne += ((mode == Mode.GROUND ? 0.0 : 1.0) - airborne) * 0.15;

		// bank, from the body's turn rate and speed
		double bodyTurn = (at(yaw, BODY_LAG - 1) - at(yaw, BODY_LAG + 1)) / 2.0;
		double speed = Math.hypot(at(x, BODY_LAG - 1) - at(x, BODY_LAG + 1), at(z, BODY_LAG - 1) - at(z, BODY_LAG + 1)) / 2.0;
		double bankTarget = flight * Math.toDegrees(Math.atan(speed * Math.toRadians(bodyTurn) / G));
		prevBank = bank;
		bank += (clamp(bankTarget, MAX_BANK) - bank) * 0.18;
		rollRate += (bank - prevBank - rollRate) * 0.4;
		// the steering (the head's), smoothed
		turn += (yaw[latest] - at(yaw, 1) - turn) * 0.3;
		turnSlow += (turn - turnSlow) * 0.08;

		// pitch: the climb or dive now (the body takes it BODY_LAG later), plus the forward lean
		double nowSpeed = Math.hypot(x[latest] - at(x, 2), z[latest] - at(z, 2)) / 2.0;
		double vy = (y[latest] - at(y, 2)) / 2.0;
		double path = flight * clamp(Math.toDegrees(Math.atan2(vy * 2.0, Math.max(nowSpeed, 0.05))), MAX_PITCH);
		double accel = nowSpeed - lastSpeed;
		lastSpeed = nowSpeed;
		double leanTarget = switch (mode) {
			case FLIGHT -> -Math.min(25.0, Math.max(0.0, accel * 400.0));
			// a hovering dragon tilts toward where it drifts, head and body forward
			case HOVER -> -Math.min(28.0, nowSpeed * 90.0 + Math.max(0.0, accel * 400.0));
			case GROUND -> 0.0;
		};
		lean += (leanTarget - lean) * 0.15;
		smoothPitch += (path + lean - smoothPitch) * 0.3;
		pitch[latest] = smoothPitch;
	}

	private boolean teleported(double px, double py, double pz) {
		double dx = px - x[latest], dy = py - y[latest], dz = pz - z[latest];
		return dx * dx + dy * dy + dz * dz > 16.0 * 16.0;
	}

	private void reset(double yawDegrees, double px, double py, double pz) {
		for (int i = 0; i < SIZE; i++) {
			yaw[i] = yawDegrees;
			x[i] = px;
			y[i] = py;
			z[i] = pz;
			pitch[i] = 0.0;
		}
		latest = 0;
		lastRawYaw = yawDegrees;
		bank = prevBank = lean = smoothPitch = lastSpeed = airborne = prevAirborne = 0.0;
		turn = turnSlow = rollRate = 0.0;
	}

	/** Shakes the prey held in the jaws (the neck throws the head side to side), or stops. */
	public void setShaking(boolean shaking) {
		this.shaking = shaking;
	}

	private double shakeWeight(float partialTick) {
		return prevShake + (shake - prevShake) * partialTick;
	}

	/** Body yaw (the model's facing) at render time {@code partialTick}. Continuous, not wrapped. */
	public double yaw(float partialTick) {
		return at(yaw, BODY_LAG + 1.0 - partialTick);
	}

	/** Body pitch, nose up positive. */
	public double pitch(float partialTick) {
		return at(pitch, BODY_LAG + 1.0 - partialTick) + ground.pitch(partialTick);
	}

	/** Body roll, right wing down positive. */
	public double roll(float partialTick) {
		return prevBank + (bank - prevBank) * partialTick + ground.roll(partialTick);
	}

	/** How far the model is raised (negative: lowered) from the dragon's position, blocks. */
	public double lift(float partialTick) {
		return ground.lift(partialTick);
	}

	/**
	 * Local bends of every neck (4) and tail (9) segment, degrees, to add to the keyframed bone rotations:
	 * X pitches the segment's far end up (render convention: rotation about +x), Y turns it (about +y).
	 */
	public void bends(float partialTick, double[] neckX, double[] neckY, double[] tailX, double[] tailY) {
		double phi = Math.toRadians(roll(partialTick));
		double sin = Math.sin(phi), cos = Math.cos(phi);
		double back = 1.0 - partialTick;
		int neck = neckX.length;
		// the head looks ahead into the turn it is making
		double lead = clamp(HEAD_LEAD * turn, MAX_HEAD_LEAD);
		for (int i = 1; i <= neck; i++) {
			double newer = BODY_LAG * (1.0 - (double) i / neck) + back, older = BODY_LAG * (1.0 - (double) (i - 1) / neck) + back;
			double share = i - 1 < NECK_LEAD.length ? NECK_LEAD[i - 1] : 0.0;
			bend(at(yaw, newer) - at(yaw, older) + lead * share, at(pitch, newer) - at(pitch, older), sin, cos, MAX_NECK_BEND, neckX, neckY, i - 1);
		}
		// the rudder: swung out while the turn tightens (it helps yaw the body round), back as it opens
		double rudder = -RUDDER * (turn - turnSlow) * flight;
		for (int j = 1; j <= tailX.length; j++) {
			double older = BODY_LAG + TAIL_STEP * j + back, newer = BODY_LAG + TAIL_STEP * (j - 1) + back;
			double share = j - 1 < RUDDER_SHARE.length ? RUDDER_SHARE[j - 1] : 0.0;
			bend(at(yaw, older) - at(yaw, newer) + rudder * share, at(pitch, older) - at(pitch, newer), sin, cos, MAX_TAIL_BEND, tailX, tailY, j - 1);
		}
		// the jaw hold's shake, on top
		double w = shakeWeight(partialTick);
		if (w > 1e-3) {
			double t = shakeTicks - 1.0 + partialTick;
			for (int i = 0; i < neck; i++) {
				neckY[i] += Grip.shakeYaw(i, t, w);
				neckX[i] += Grip.shakePitch(t, w);
			}
		}
	}

	/**
	 * X rotation to add to each shoulder, degrees: against the body's whole pitch, {@code keyframedPitch}
	 * (the animation's, nose up positive) plus {@link #pitch}, up to {@link #WING_FLEX}; none on the ground.
	 */
	public double wingCounter(float partialTick, double keyframedPitch) {
		double air = prevAirborne + (airborne - prevAirborne) * partialTick;
		return -air * clamp(keyframedPitch + pitch(partialTick), WING_FLEX);
	}

	/**
	 * The wings' share of a turn, editor degrees per wing, to add to the keyframes: {@code out} = left
	 * sweep (+ back), left hand (+ up), left twist (+ leading edge up), then the same for the right. In a
	 * bank the wing inside the turn sweeps back with its hand drooping and the outside one reaches
	 * forward; while the roll changes, the wing that must drop twists its leading edge down and the other
	 * up. Only in flight.
	 */
	public void wingTurn(float partialTick, double[] out) {
		double k = flight * clamp(roll(partialTick) / WING_TURN_BANK, 1.0);
		double right = Math.max(k, 0.0), left = Math.max(-k, 0.0);
		double twist = flight * clamp(ROLL_TWIST * rollRate, MAX_ROLL_TWIST);
		out[0] = INSIDE_SWEEP * left - OUTSIDE_REACH * right;
		out[1] = -INSIDE_DROOP * left + OUTSIDE_LIFT * right;
		out[2] = twist;
		out[3] = INSIDE_SWEEP * right - OUTSIDE_REACH * left;
		out[4] = -INSIDE_DROOP * right + OUTSIDE_LIFT * left;
		out[5] = -twist;
	}

	/** How far the head rolls back toward level, degrees (counter to {@link #roll}). */
	public double headRoll(float partialTick) {
		double w = shakeWeight(partialTick);
		return HEAD_LEVELING * roll(partialTick) + (w > 1e-3 ? Grip.shakeRoll(shakeTicks - 1.0 + partialTick, w) : 0.0);
	}

	/**
	 * A heading change {@code dPsi} (yaw, right positive) and pitch change {@code dTheta} between a
	 * segment's ends, expressed in a body rolled by phi: in a banked body a level turn is part yaw,
	 * part pitch toward the inside of the turn.
	 */
	private static void bend(double dPsi, double dTheta, double sin, double cos, double limit, double[] outX, double[] outY, int i) {
		outX[i] = clamp(dPsi * sin + dTheta * cos, limit);
		outY[i] = clamp(-dPsi * cos + dTheta * sin, limit);
	}

	/** History value {@code lag} ticks back (fractional, linear), 0 = the latest tick. */
	private double at(double[] values, double lag) {
		lag = Math.max(0.0, Math.min(SIZE - 2, lag));
		int i = (int) Math.floor(lag);
		double k = lag - i;
		double a = values[Math.floorMod(latest - i, SIZE)], b = values[Math.floorMod(latest - i - 1, SIZE)];
		return a + (b - a) * k;
	}

	private static double clamp(double v, double limit) {
		return Math.max(-limit, Math.min(limit, v));
	}

	private static double wrap(double degrees) {
		degrees %= 360.0;
		if (degrees >= 180.0) degrees -= 360.0;
		if (degrees < -180.0) degrees += 360.0;
		return degrees;
	}
}
