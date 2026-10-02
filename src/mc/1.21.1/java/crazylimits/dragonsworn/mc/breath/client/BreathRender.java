package crazylimits.dragonsworn.mc.breath.client;

import crazylimits.dragonsworn.anim.BreathAttack;
import crazylimits.dragonsworn.anim.BreathPass;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Pours the stream breath's flames out of the model's mouth. The renderer calls {@link #afterRender}
 * right after drawing a dragon, while the head and jaw bones still hold that dragon's matrices: the
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
	/** Blocks per tick the flames leave the mouth with. */
	private static final double SPEED = 0.85, JITTER = 0.07;
	/** The pass's flames fly faster: its aim is further from the mouth. */
	private static final double PASS_SPEED = 1.1;

	/** The last tick flames were spawned for each dragon: the renderer runs per frame, the flames per tick. */
	private static final Map<EnderDragon, Integer> LAST_TICK = new WeakHashMap<>();

	private BreathRender() {}

	public static void afterRender(EnderDragon dragon, GeoModel<?> model) {
		BreathStreamPhase perched = dragon.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase p ? p : null;
		double passTicks = perched == null ? BreathPassPhase.breathTicks(dragon, 0.0F) : Double.NaN;
		if (perched == null && Double.isNaN(passTicks)) return;
		Optional<GeoBone> head = model.getBone(HEAD), jaw = model.getBone(JAW);
		if (head.isEmpty() || jaw.isEmpty()) return;
		// Tracking starts the frame after it is switched on; until then the matrices are empty.
		if (!head.get().isTrackingMatrices() || !jaw.get().isTrackingMatrices()) {
			head.get().setTrackingMatrices(true);
			jaw.get().setTrackingMatrices(true);
			return;
		}
		int tick = perched != null ? perched.ticks() : (int) Math.round(passTicks);
		boolean streaming = perched != null ? BreathAttack.streaming(tick) : BreathPass.streaming(tick);
		boolean glowing = perched != null ? BreathAttack.glowing(tick) : BreathPass.glowing(tick);
		Vec3 aim = perched != null ? null : BreathPassPhase.aimPoint(dragon);
		if (!streaming && !glowing || perched == null && aim == null) return;
		Integer last = LAST_TICK.put(dragon, dragon.tickCount);
		if (last != null && last == dragon.tickCount) return;

		Vec3 upper = point(head.get()), lower = point(jaw.get());
		Vec3 mouth = upper.add(lower).scale(0.5);
		Vec3 dir = (perched != null ? perched.stream().getLocation() : aim).subtract(mouth).normalize();
		// in flight the flames carry the dragon's own speed
		Vec3 carried = perched != null ? Vec3.ZERO : new Vec3(dragon.getX() - dragon.xo, dragon.getY() - dragon.yo, dragon.getZ() - dragon.zo);
		double jet = perched != null ? SPEED : PASS_SPEED;
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

	/** The mouth point carried by {@code bone}, in world space. GeckoLib mirrors X; the point is on x = 0. */
	private static Vec3 point(GeoBone bone) {
		Vector4f v = new Vector4f(0.0F, (MOUTH_Y - bone.getPivotY()) / 16.0F, (MOUTH_Z - bone.getPivotZ()) / 16.0F, 1.0F);
		worldMatrix(bone).transform(v);
		return new Vec3(v.x, v.y, v.z);
	}

	/**
	 * The bone's true world matrix. GeckoLib 4.9.3 builds it with {@code RenderUtil.translateMatrix}, which
	 * adds an identity-plus-translation in place, twice over the same matrix: what it stores is the true one
	 * plus a multiple of the identity (the pivot still lands right, points off it do not). A true affine
	 * matrix has m33 = 1, so the excess is m33 - 1 times the identity; none once GeckoLib is fixed.
	 */
	private static Matrix4f worldMatrix(GeoBone bone) {
		Matrix4f m = new Matrix4f(bone.getWorldSpaceMatrix());
		float excess = m.m33() - 1.0F;
		if (excess != 0.0F) m.m00(m.m00() - excess).m11(m.m11() - excess).m22(m.m22() - excess).m33(1.0F);
		return m;
	}
}
