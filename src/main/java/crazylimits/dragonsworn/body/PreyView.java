package crazylimits.dragonsworn.body;

/**
 * Where a held player sees from, game-free (the game's camera: {@code mc/mixin/client/CameraMixin}). Held, a
 * player is drawn lying flat ({@link Grip}): its eyes are at its lying head ({@link #lyingEyes}), not at its
 * standing eye height, where the game puts the camera, inside the dragon.
 *
 * <p>The camera is moved last, after the game and every camera mod placed it, and by the difference only
 * ({@link #camera}): a third-person camera, vanilla's or a camera mod's (Shoulder Surfing Reloaded's over-the-shoulder
 * offset, its drag and sway), keeps where it was put round the eyes and moves with them. A first-person camera that
 * is not at the standing eyes was put somewhere else by another mod (at the drawn head, say): it is left there. Nothing
 * is kept between frames, so nothing is left over once the hold ends.
 */
public final class PreyView {
	/**
	 * Blocks: a first-person camera this close to the standing eyes is where the game put it; farther, another mod
	 * placed it. The game's own placement repeats the eyes' sum exactly, so only rounding is within this.
	 */
	public static final double PLACED = 1.0E-4;

	private PreyView() {}

	/**
	 * The eyes of a held prey lying along {@code yaw} (degrees, Minecraft's: its head toward (-sin, cos)), its
	 * feet at {@code x, y, z} and its full standing {@code height} and {@code eyeHeight} (blocks): level with its
	 * middle, {@code eyeHeight - height / 2} from the middle toward its head.
	 */
	public static double[] lyingEyes(double x, double y, double z, double eyeHeight, double height, double yaw) {
		double out = eyeHeight - height / 2.0, r = Math.toRadians(yaw);
		return new double[]{x - Math.sin(r) * out, y + height / 2.0, z + Math.cos(r) * out};
	}

	/**
	 * Where the camera goes for a held player whose {@code standing} eyes are where the game put them and whose
	 * {@code lying} ones are where it sees from: the camera as placed so far, moved by {@code lying - standing}. Null
	 * to leave it be: a first-person ({@code !detached}) camera another mod put elsewhere than the standing eyes.
	 */
	public static double[] camera(double[] camera, double[] standing, double[] lying, boolean detached) {
		double dx = camera[0] - standing[0], dy = camera[1] - standing[1], dz = camera[2] - standing[2];
		if (!detached && dx * dx + dy * dy + dz * dz > PLACED * PLACED) return null;
		return new double[]{lying[0] + dx, lying[1] + dy, lying[2] + dz};
	}
}
