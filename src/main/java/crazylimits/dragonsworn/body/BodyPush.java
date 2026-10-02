package crazylimits.dragonsworn.body;

/**
 * The dragon's hitboxes push what stands in them like mobs push each other (vanilla's {@code Entity.push}):
 * soft, not solid. Each tick something overlaps a hitbox, it is nudged sideways, away from the hitbox's
 * middle, by vanilla's push of two mobs; only it moves, the dragon stays put. The nudge adds to its
 * velocity, so it slides out over a few ticks, and a hitbox sweeping over it carries it along.
 *
 * <p>Boxes are {@code {minX, minY, minZ, maxX, maxY, maxZ}}.
 */
public final class BodyPush {
	/** Vanilla's push between two mobs (velocity added per tick, at most, blocks/tick). */
	public static final double STRENGTH = 0.05;
	/** Overlaps thinner than this are touching, not inside. */
	static final double EPSILON = 1e-6;

	private BodyPush() {}

	/** Whether box {@code e} is inside box {@code part} (overlaps it on every axis). */
	public static boolean overlaps(double[] e, double[] part) {
		for (int a = 0; a < 3; a++) {
			if (Math.min(e[a + 3], part[a + 3]) - Math.max(e[a], part[a]) <= EPSILON) return false;
		}
		return true;
	}

	/**
	 * The push on something at {@code x, z} from a hitbox centred at {@code px, pz} into {@code out}
	 * (velocity x, z), as vanilla pushes two mobs apart: along the line between their middles, by
	 * {@link #STRENGTH} at most, weaker the further apart (by the larger of the two offsets). Right on the
	 * hitbox's middle it is pushed away from {@code awayX, awayZ} instead (the dragon's middle), or nowhere.
	 */
	public static void push(double x, double z, double px, double pz, double awayX, double awayZ, double[] out) {
		double dx = x - px, dz = z - pz;
		if (Math.max(Math.abs(dx), Math.abs(dz)) < 0.01) {
			dx = x - awayX;
			dz = z - awayZ;
		}
		double f = Math.max(Math.abs(dx), Math.abs(dz));
		out[0] = out[1] = 0.0;
		if (f < 0.01) return;
		f = Math.sqrt(f);
		double k = Math.min(1.0, 1.0 / f) * STRENGTH / f;
		out[0] = dx * k;
		out[1] = dz * k;
	}
}
