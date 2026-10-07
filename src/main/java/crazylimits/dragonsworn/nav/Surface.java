package crazylimits.dragonsworn.nav;

/**
 * What the dragon stands on: the ground, or a wall it clings to ({@link Face}). A wall is the ground turned
 * onto its side. In a face's own frame ("local") up points out of the wall (the face's normal) and the way
 * into the wall (-normal) points straight up it, so everything made for the ground works on a wall in that frame: the
 * stance (the hind feet and the wrists of the folded wings gripping the face, the chest out from it), the
 * wing-walk (climbing), foot IK, the turn on the spot, the tail laid down, the planners and the landing
 * sites. A dragon facing a wall that grips it turns nose up by a right angle and keeps its yaw, which is
 * now its heading up the face.
 *
 * <p>The model, the hitboxes and the renderer turn by {@link #rotation}: from where the dragon was to the
 * face it was given, evenly over {@link #TURN_TICKS} (both sides tick it from the synced face, so the
 * hitboxes stay on the model). That is the animations' blend, in step with it.
 *
 * <p>A wall is gripped from the air the way a bird lands on a trunk: flying in, facing the face, it flares,
 * the body tipping up in the air until it stands nearly upright ({@link #LEAN} of the turn, eased over
 * {@link #LEAN_TICKS}: the frame leans toward the face while its face is still the floor), wings beating;
 * at the grip only the rest is left, as the hover blends into the wall pose. Taking off it is the same
 * backwards: off the face into the lean, which then eases out as it flies off. Game-free.
 */
public final class Surface {
	/** Ticks a change of face takes (gripping a wall from the hover, pushing off it): anim/DragonAnim.BLEND_TICKS. */
	public static final int TURN_TICKS = 6;
	/**
	 * Of the right angle onto a wall, how much the flare toward it takes (with the hover pose's own 38 degrees
	 * up, the body then stands as upright as on the wall), and how long it takes to tip up (or down).
	 */
	public static final double LEAN = 0.6;
	public static final int LEAN_TICKS = 15;

	/**
	 * The ground, or a wall by the way its face looks: its outward direction across the ground (the four
	 * compass faces, then the four diagonals) and how steep it is ({@link #tilt}: 90 a sheer wall, 60 one
	 * stepped back as it rises, 45 a stair of cliffs). The body follows what is left over (it pitches
	 * and rolls to the ground under its feet, limb/GroundFit), so these frames cover every wall from a 45 degree
	 * stair to a sheer face, at any angle round the compass. Appended only (the ordinal is synced and saved
	 * by name).
	 */
	public enum Face {
		FLOOR(0, 0, 0), NORTH(0, -1, 90), SOUTH(0, 1, 90), WEST(-1, 0, 90), EAST(1, 0, 90),
		NORTH_EAST(1, -1, 90), NORTH_WEST(-1, -1, 90), SOUTH_EAST(1, 1, 90), SOUTH_WEST(-1, 1, 90),
		NORTH_STEEP(0, -1, 60), SOUTH_STEEP(0, 1, 60), WEST_STEEP(-1, 0, 60), EAST_STEEP(1, 0, 60),
		NORTH_EAST_STEEP(1, -1, 60), NORTH_WEST_STEEP(-1, -1, 60), SOUTH_EAST_STEEP(1, 1, 60), SOUTH_WEST_STEEP(-1, 1, 60),
		NORTH_SLOPE(0, -1, 45), SOUTH_SLOPE(0, 1, 45), WEST_SLOPE(-1, 0, 45), EAST_SLOPE(1, 0, 45),
		NORTH_EAST_SLOPE(1, -1, 45), NORTH_WEST_SLOPE(-1, -1, 45), SOUTH_EAST_SLOPE(1, 1, 45), SOUTH_WEST_SLOPE(-1, 1, 45);

		/** The way the face looks across the ground (a unit vector; zero for the floor). */
		public final double nx, nz;
		/** How steep it is, degrees: 0 the floor, 90 a sheer wall. */
		public final double tilt;
		/** The face's outward normal (the model's up on it), world. */
		public final double[] normal;
		/** world = m local (row-major). The sheer compass walls' are signed permutations (whole blocks onto whole blocks). */
		private final double[] m;
		/** The same turn as a unit quaternion {w, x, y, z}. */
		private final double[] q;

		Face(int dx, int dz, double tilt) {
			double len = Math.hypot(dx, dz);
			this.nx = len == 0 ? 0 : dx / len;
			this.nz = len == 0 ? 0 : dz / len;
			this.tilt = tilt;
			if (len == 0) {
				m = new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
			} else {
				// tilted by `tilt` about k = up x h: up goes to the normal, facing the wall (-h) to up the face, the yaw kept
				double[] r = about(new double[]{nz, 0, -nx}, Math.toRadians(tilt));
				for (int i = 0; i < 9; i++) if (Math.abs(r[i]) < 1e-12) r[i] = 0.0;
				m = r;
			}
			normal = new double[]{m[1], m[4], m[7]};
			q = quaternion(m);
		}

		public boolean wall() {
			return this != FLOOR;
		}

		/** From its {@link #ordinal}; {@link #FLOOR} for anything else. */
		public static Face of(int ordinal) {
			return ordinal > 0 && ordinal < values().length ? values()[ordinal] : FLOOR;
		}

		/** The wall whose face looks most nearly toward (dx, dz). */
		public static Face toward(double dx, double dz) {
			if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? EAST : WEST;
			return dz > 0 ? SOUTH : NORTH;
		}

		/** {@code local} (a direction or a point relative to the world's origin) into the world. {@code out} may be {@code local}. */
		public double[] toWorld(double[] local, double[] out) {
			double x = local[0], y = local[1], z = local[2];
			out[0] = m[0] * x + m[1] * y + m[2] * z;
			out[1] = m[3] * x + m[4] * y + m[5] * z;
			out[2] = m[6] * x + m[7] * y + m[8] * z;
			return out;
		}

		/** The inverse of {@link #toWorld}. */
		public double[] toLocal(double[] world, double[] out) {
			double x = world[0], y = world[1], z = world[2];
			out[0] = m[0] * x + m[3] * y + m[6] * z;
			out[1] = m[1] * x + m[4] * y + m[7] * z;
			out[2] = m[2] * x + m[5] * y + m[8] * z;
			return out;
		}

		/** The world block that local block (x, y, z) is. */
		public int[] blockToWorld(int x, int y, int z) {
			double[] c = toWorld(new double[]{x + 0.5, y + 0.5, z + 0.5}, new double[3]);
			return new int[]{(int) Math.floor(c[0]), (int) Math.floor(c[1]), (int) Math.floor(c[2])};
		}

		/** The local block that world block (x, y, z) is. */
		public int[] blockToLocal(int x, int y, int z) {
			double[] c = toLocal(new double[]{x + 0.5, y + 0.5, z + 0.5}, new double[3]);
			return new int[]{(int) Math.floor(c[0]), (int) Math.floor(c[1]), (int) Math.floor(c[2])};
		}

		/** The local yaw of heading straight up the face (a wall's; the dragon stays upright on one). */
		public float upYaw() {
			return yawOf(0.0, 1.0, 0.0);
		}

		/** The local yaw (degrees, the game's: 0 faces -z) of the world direction (dx, dy, dz) laid onto the face. */
		public float yawOf(double dx, double dy, double dz) {
			double[] l = toLocal(new double[]{dx, dy, dz}, new double[3]);
			return (float) Math.toDegrees(Math.atan2(l[0], -l[2]));
		}
	}

	private Face face = Face.FLOOR, lean = Face.FLOOR;
	private double[] from = {1, 0, 0, 0}, now = {1, 0, 0, 0}, prev = {1, 0, 0, 0};
	private double t = 1.0;
	/** The change under way: onto a face ({@link #TURN_TICKS}, evenly) or a lean ({@link #LEAN_TICKS}, eased). */
	private boolean leaning;
	/** Gripping a face out of a flare: the flare's angle when the grip began (degrees), and the change's progress a tick ago. */
	private double gripFrom, prevT = 1.0;
	private final double[] scratch = new double[4];
	private float cachedTick = Float.NaN;
	private final double[] cached = new double[9];

	/** One tick toward {@code target} (both sides, from the synced face). */
	public void tick(Face target) {
		tick(target, Face.FLOOR);
	}

	/**
	 * One tick toward {@code target}, on the floor leaning toward wall {@code toward} ({@link #LEAN}; the
	 * floor: upright). Both sides, from the synced face and lean.
	 */
	public void tick(Face target, Face toward) {
		Face lean = target.wall() ? Face.FLOOR : toward;
		if (target != face || lean != this.lean) {
			gripFrom = !face.wall() && target.wall() ? angle(now) : 0.0;
			from = now.clone();
			leaning = target == face;
			face = target;
			this.lean = lean;
			t = 0.0;
		}
		prev = now.clone();
		prevT = t;
		double step = 1.0 / (leaning ? LEAN_TICKS : TURN_TICKS);
		t = t + step > 1.0 - 1e-9 ? 1.0 : t + step;
		double[] to = lean == Face.FLOOR ? face.q : slerp(Face.FLOOR.q, lean.q, LEAN, new double[4]);
		// onto a face evenly, as the animations blend (AnimClock.blend); a lean eased
		now = slerp(from, to, leaning ? t * t * (3 - 2 * t) : t, new double[4]);
		cachedTick = Float.NaN;
	}

	/** Straight onto {@code target}, no easing (a dragon loaded or teleported onto it). */
	public void reset(Face target) {
		face = target;
		lean = Face.FLOOR;
		gripFrom = 0.0;
		prevT = 1.0;
		t = 1.0;
		from = target.q.clone();
		now = target.q.clone();
		prev = target.q.clone();
		cachedTick = Float.NaN;
	}

	/**
	 * How far the body is tipped up off the floor's frame by a flare in the air (degrees): flying in to a wall,
	 * or just off one; fading out evenly as it grips the face (the wall pose's own then takes over). Zero on
	 * the ground and settled on a wall. The neck bends down by it (body/DragonBody), so the head stays as in
	 * the hover, looking ahead, not up and back over its shoulder.
	 */
	public double flare(float partialTick) {
		if (!face.wall()) return angle(slerp(prev, now, partialTick, scratch));
		if (gripFrom <= 0.0) return 0.0;
		double u = prevT + (t - prevT) * partialTick;
		return gripFrom * (1.0 - u);
	}

	/** A turn's angle (degrees). */
	private static double angle(double[] q) {
		return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(q[0]))));
	}

	/** The face it is on, or turning onto. */
	public Face face() {
		return face;
	}

	/** Still turning onto its face. */
	public boolean turning() {
		return t < 1.0;
	}

	/** How far the body is turned from the floor's frame toward a wall's, 0..1 (1 on a wall, settled). */
	public double wallness(float partialTick) {
		double w = Math.abs(slerp(prev, now, partialTick, scratch)[0]);
		// a quarter turn has w = cos 45
		return Math.max(0.0, Math.min(1.0, (1.0 - w) / (1.0 - Math.sqrt(0.5))));
	}

	/** The turn at {@code partialTick} as a row-major 3x3 matrix (world = R local). Shared: do not keep it. */
	public double[] rotation(float partialTick) {
		if (partialTick == cachedTick) return cached;
		double[] q = slerp(prev, now, partialTick, scratch);
		double w = q[0], x = q[1], y = q[2], z = q[3];
		cached[0] = 1 - 2 * (y * y + z * z);
		cached[1] = 2 * (x * y - w * z);
		cached[2] = 2 * (x * z + w * y);
		cached[3] = 2 * (x * y + w * z);
		cached[4] = 1 - 2 * (x * x + z * z);
		cached[5] = 2 * (y * z - w * x);
		cached[6] = 2 * (x * z - w * y);
		cached[7] = 2 * (y * z + w * x);
		cached[8] = 1 - 2 * (x * x + y * y);
		cachedTick = partialTick;
		return cached;
	}

	/** The turn at {@code partialTick} as a unit quaternion {w, x, y, z} (for the renderer). */
	public double[] quaternion(float partialTick) {
		return slerp(prev, now, partialTick, new double[4]);
	}

	/** {@code r v} into {@code out} ({@code out} may be {@code v}). */
	public static double[] apply(double[] r, double[] v, double[] out) {
		double x = v[0], y = v[1], z = v[2];
		out[0] = r[0] * x + r[1] * y + r[2] * z;
		out[1] = r[3] * x + r[4] * y + r[5] * z;
		out[2] = r[6] * x + r[7] * y + r[8] * z;
		return out;
	}

	/** {@code r}<sup>T</sup> {@code v}: the turn undone. */
	public static double[] applyInverse(double[] r, double[] v, double[] out) {
		double x = v[0], y = v[1], z = v[2];
		out[0] = r[0] * x + r[3] * y + r[6] * z;
		out[1] = r[1] * x + r[4] * y + r[7] * z;
		out[2] = r[2] * x + r[5] * y + r[8] * z;
		return out;
	}

	/** Rotation by {@code angle} (radians) about unit axis {@code k}, row-major. */
	static double[] about(double[] k, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle), t = 1.0 - c, x = k[0], y = k[1], z = k[2];
		return new double[]{t * x * x + c, t * x * y - s * z, t * x * z + s * y, t * x * y + s * z, t * y * y + c, t * y * z - s * x,
				t * x * z - s * y, t * y * z + s * x, t * z * z + c};
	}

	static double[] mul(double[] a, double[] b) {
		double[] m = new double[9];
		for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) m[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
		return m;
	}

	static double[] mulT(double[] m, double[] v) {
		return new double[]{m[0] * v[0] + m[3] * v[1] + m[6] * v[2], m[1] * v[0] + m[4] * v[1] + m[7] * v[2], m[2] * v[0] + m[5] * v[1] + m[8] * v[2]};
	}

	/** A rotation matrix as a unit quaternion {w, x, y, z}. */
	static double[] quaternion(double[] m) {
		double tr = m[0] + m[4] + m[8], w, x, y, z;
		if (tr > 0) {
			double s = Math.sqrt(tr + 1.0) * 2;
			w = 0.25 * s;
			x = (m[7] - m[5]) / s;
			y = (m[2] - m[6]) / s;
			z = (m[3] - m[1]) / s;
		} else if (m[0] > m[4] && m[0] > m[8]) {
			double s = Math.sqrt(1.0 + m[0] - m[4] - m[8]) * 2;
			w = (m[7] - m[5]) / s;
			x = 0.25 * s;
			y = (m[1] + m[3]) / s;
			z = (m[2] + m[6]) / s;
		} else if (m[4] > m[8]) {
			double s = Math.sqrt(1.0 + m[4] - m[0] - m[8]) * 2;
			w = (m[2] - m[6]) / s;
			x = (m[1] + m[3]) / s;
			y = 0.25 * s;
			z = (m[5] + m[7]) / s;
		} else {
			double s = Math.sqrt(1.0 + m[8] - m[0] - m[4]) * 2;
			w = (m[3] - m[1]) / s;
			x = (m[2] + m[6]) / s;
			y = (m[5] + m[7]) / s;
			z = 0.25 * s;
		}
		return new double[]{w, x, y, z};
	}

	static double[] slerp(double[] a, double[] b, double u, double[] out) {
		double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
		double sign = 1.0;
		if (dot < 0) {
			dot = -dot;
			sign = -1.0;
		}
		double ka, kb;
		if (dot > 0.9995) {
			ka = 1 - u;
			kb = u * sign;
		} else {
			double th = Math.acos(dot), s = Math.sin(th);
			ka = Math.sin((1 - u) * th) / s;
			kb = Math.sin(u * th) / s * sign;
		}
		double n = 0;
		for (int i = 0; i < 4; i++) {
			out[i] = a[i] * ka + b[i] * kb;
			n += out[i] * out[i];
		}
		n = Math.sqrt(n);
		for (int i = 0; i < 4; i++) out[i] /= n;
		return out;
	}
}
