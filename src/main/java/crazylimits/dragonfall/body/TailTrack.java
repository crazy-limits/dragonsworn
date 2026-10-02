package crazylimits.dragonfall.body;

import crazylimits.dragonfall.anim.DragonAnim;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The flying animations' tails, made with the animations ({@code tools/anims.py} tail_track, generated
 * into {@link PoseData}): the tail is a rope hung from the body, each point where a stiff tail would have
 * been a moment ago (later toward the tip), so every heave and pitch of the keyframed body runs down it as
 * a wave; it drops a little on each downstroke, hangs when the body stands up or brakes, sways with the
 * glide, and lies down (curled, on the ground) where the animation stands on its feet.
 *
 * <p>One frame per keyframe of the animation: 9 bends down, 9 bends right (degrees, {@link TailChain}'s
 * convention), how much it rests on the ground and how far that resting tail is lifted.
 */
public final class TailTrack {
	private static final int VALUES = 2 * TailChain.SEGMENTS + 2;

	private record Track(float[] data, int frames, double step, double length, boolean loop) {}

	private static final Map<DragonAnim, Track> TRACKS = new EnumMap<>(DragonAnim.class);

	static {
		for (int i = 0; i < PoseData.TAIL_ANIMS.length; i++) {
			DragonAnim anim = DragonAnim.valueOf(PoseData.TAIL_ANIMS[i].toUpperCase(Locale.ROOT));
			ByteBuffer bytes = ByteBuffer.wrap(Base64.getDecoder().decode(PoseData.TAIL_DATA[i])).order(ByteOrder.LITTLE_ENDIAN);
			float[] data = new float[bytes.remaining() / 2];
			for (int k = 0; k < data.length; k++) data[k] = bytes.getShort() / 64.0F;
			TRACKS.put(anim, new Track(data, data.length / VALUES, PoseTrack.step(anim), PoseTrack.length(anim), PoseTrack.loops(anim)));
		}
	}

	private TailTrack() {}

	/** Whether {@code anim}'s tail comes from the track. */
	public static boolean has(DragonAnim anim) {
		return TRACKS.containsKey(anim);
	}

	/** The tail of {@code anim} at {@code seconds} (looped, or held at the end), into {@code out}. */
	public static void sample(DragonAnim anim, double seconds, TailMotion.Pose out) {
		Track track = TRACKS.get(anim);
		double t = track.loop ? seconds - Math.floor(seconds / track.length) * track.length
				: Math.max(0.0, Math.min(track.length, seconds));
		double x = t / track.step;
		int i = Math.max(0, Math.min(track.frames - 2, (int) Math.floor(x)));
		double k = Math.min(1.0, x - i);
		int a = i * VALUES, b = a + VALUES, n = TailChain.SEGMENTS;
		for (int s = 0; s < n; s++) {
			out.x[s] = lerp(track.data[a + s], track.data[b + s], k);
			out.y[s] = lerp(track.data[a + n + s], track.data[b + n + s], k);
		}
		out.rest = lerp(track.data[a + 2 * n], track.data[b + 2 * n], k);
		out.lift = lerp(track.data[a + 2 * n + 1], track.data[b + 2 * n + 1], k);
	}

	private static double lerp(double a, double b, double k) {
		return a + (b - a) * k;
	}
}
