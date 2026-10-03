package crazylimits.dragonsworn.mc.client.showcase;

import net.minecraft.world.phys.Vec3;

/**
 * The film's free camera ({@link Film}): an eye and the point it looks at, kept round what the shot
 * follows and eased toward where it wants them every tick. While it is on, {@code CameraMixin} puts the
 * game's (third-person) camera there, so the player's own model is drawn wherever it stands.
 */
public final class FilmCamera {
	private static Vec3 eye, eyeO, at, atO;
	/** The eased offsets of the eye and of the look point from the shot's anchor. */
	private static Vec3 offset, look;

	private FilmCamera() {
	}

	public static boolean active() {
		return eye != null;
	}

	/**
	 * Frames {@code anchor} (what the shot follows, taken as it is: a fast dragon never runs out of the
	 * frame): the eye at {@code anchor + eye}, looking at {@code anchor + at}, both offsets eased
	 * {@code ease} of the way to what is wanted (the look twice as fast); the first move snaps.
	 */
	static void move(Vec3 anchor, Vec3 wantEye, Vec3 wantAt, double ease) {
		if (offset == null) {
			offset = wantEye;
			look = wantAt;
		} else {
			offset = offset.lerp(wantEye, ease);
			look = look.lerp(wantAt, Math.min(1.0, ease * 2.0));
		}
		eyeO = eye == null ? anchor.add(offset) : eye;
		atO = at == null ? anchor.add(look) : at;
		eye = anchor.add(offset);
		at = anchor.add(look);
	}

	static void off() {
		eye = eyeO = at = atO = offset = look = null;
	}

	/** The eye now (ticks interpolated). */
	public static Vec3 eye(float partialTick) {
		return eyeO.lerp(eye, partialTick);
	}

	/** Where the eye looks along, as the game's yaw (0 faces south, +z) and pitch (down is positive), in degrees. */
	public static float[] rotation(float partialTick) {
		Vec3 d = atO.lerp(at, partialTick).subtract(eye(partialTick));
		return new float[]{(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))};
	}

	/** The unit direction the eye looks along now. */
	static Vec3 forward() {
		return at.subtract(eye).normalize();
	}
}
