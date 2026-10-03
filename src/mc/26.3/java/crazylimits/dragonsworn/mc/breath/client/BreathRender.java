package crazylimits.dragonsworn.mc.breath.client;

import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.attack.FlamePuff;
import crazylimits.dragonsworn.attack.BreathPass;
import crazylimits.dragonsworn.limb.Affine;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.mc.client.GeoBones;
import crazylimits.dragonsworn.mc.client.LimbAnimator;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pours the stream breath's flames out of the model's mouth. The renderer calls {@link #afterRender}
 * as it poses a dragon for drawing, with the head and jaw bones as drawn: the
 * flames start inside the mouth, between the upper and lower jaw (so they follow the animation), and fly
 * at the point where the phase's stream lands, so they burn exactly where the server burns. The breath
 * pass ({@link BreathPassPhase}) pours them the same way from a flying dragon: there they carry its
 * speed too, so the stream stays on the moving aim.
 */
public final class BreathRender {
	private static final String HEAD = "head_group", JAW = "jaw_group";
	/** A point inside the mouth, model px: a little behind the teeth, between the jaws. */
	private static final float MOUTH_Y = 52.0F, MOUTH_Z = -96.0F;
	private static final int FLAMES_PER_TICK = 16, EMBERS_PER_TICK = 2;
	/** The flames' scatter (blocks per tick); their speed is {@link FlamePuff}'s, as the server's puffs that burn. */
	private static final double JITTER = 0.07;

	/** The last tick flames were spawned for each dragon: the renderer runs per frame, the flames per tick. */
	private static final Map<EnderDragon, Integer> LAST_TICK = new WeakHashMap<>();

	private BreathRender() {}

	public static void afterRender(EnderDragon dragon, GeoBones.Model model, float partialTick) {
		BreathStreamPhase perched = dragon.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase p ? p : null;
		double passTicks = perched == null ? BreathPassPhase.breathTicks(dragon, 0.0F) : Double.NaN;
		if (perched == null && Double.isNaN(passTicks)) return;
		GeoBones.Bone head = model.bone(HEAD), jaw = model.bone(JAW);
		if (head == null || jaw == null) return;
		int tick = perched != null ? perched.ticks() : (int) Math.round(passTicks);
		boolean streaming = perched != null ? BreathAttack.streaming(tick) : BreathPass.streaming(tick);
		boolean glowing = perched != null ? BreathAttack.glowing(tick) : BreathPass.glowing(tick);
		Vec3 aim = perched != null ? null : BreathPassPhase.aimPoint(dragon);
		if (!streaming && !glowing || perched == null && aim == null) return;
		Integer last = LAST_TICK.put(dragon, dragon.tickCount);
		if (last != null && last == dragon.tickCount) return;

		BodyFrame frame = LimbAnimator.frame(dragon, partialTick);
		Vec3 upper = point(head, frame), lower = point(jaw, frame);
		Vec3 mouth = upper.add(lower).scale(0.5);
		Vec3 dir = (perched != null ? perched.stream().getLocation() : aim).subtract(mouth).normalize();
		// in flight the flames carry the dragon's own speed
		Vec3 carried = perched != null ? Vec3.ZERO : new Vec3(dragon.getX() - dragon.xo, dragon.getY() - dragon.yo, dragon.getZ() - dragon.zo);
		double jet = perched != null ? FlamePuff.JET : FlamePuff.FLYING_JET;
		RandomSource random = dragon.getRandom();
		if (glowing) {
			// the telegraph: embers flicker up inside the parting jaws
			for (int i = 0; i < EMBERS_PER_TICK; i++) {
				dragon.level().addAlwaysVisibleParticle(BreathParticles.VOID_FLAME, mouth.x, mouth.y, mouth.z,
						carried.x + random.nextGaussian() * 0.02, carried.y, carried.z + random.nextGaussian() * 0.02);
			}
			return;
		}
		for (int i = 0; i < FLAMES_PER_TICK; i++) {
			// spread along this tick's stretch of the stream so it reads as one continuous jet
			double lead = random.nextDouble() * jet;
			double speed = jet * (0.85 + random.nextDouble() * 0.3);
			dragon.level().addAlwaysVisibleParticle(BreathParticles.VOID_BREATH,
					mouth.x + dir.x * lead, mouth.y + dir.y * lead, mouth.z + dir.z * lead,
					carried.x + dir.x * speed + random.nextGaussian() * JITTER,
					carried.y + dir.y * speed + random.nextGaussian() * JITTER,
					carried.z + dir.z * speed + random.nextGaussian() * JITTER);
		}
	}

	/**
	 * The mouth point carried by {@code bone}, in world space: the point in the rest model (pixels, on
	 * x = 0) moved with the bone as drawn, by the same forward kinematics as the limbs ({@link GeoBones#matrix}).
	 */
	private static Vec3 point(GeoBones.Bone bone, BodyFrame frame) {
		double[] p = frame.toWorld(Affine.apply(GeoBones.matrix(bone), new double[]{0.0, MOUTH_Y, MOUTH_Z}, new double[3]), new double[3]);
		return new Vec3(p[0], p[1], p[2]);
	}
}
