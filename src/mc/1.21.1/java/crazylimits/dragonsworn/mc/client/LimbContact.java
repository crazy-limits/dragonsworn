package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.limb.Affine;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Where the dragon's four feet are against the ground, measured on the model as drawn: the lowest point
 * of each hind foot and of each folded hand (the front feet of the wing-walk, see {@code tools/walk.py}),
 * against the top of the block under that point. The showcase switches it on ({@link #enabled}); the
 * renderer calls {@link #afterRender} right after drawing a dragon, while the bones hold its matrices.
 */
public final class LimbContact {
	public static final String[] LIMBS = {"left hind", "right hind", "left front", "right front"};
	private static final String[] ROOTS = {"foot_left", "foot_right", "left_wing_tip2", "right_wing_tip2"};

	public static boolean enabled;

	/** One tick's measurement: per limb, the lowest point's height over the ground under it (negative: sunk in) and that ground's height. */
	public record Sample(int tick, String anim, double[] gap, double[] ground, double[][] feet, boolean[] stepping, float yaw) {}

	private static final List<Sample> SAMPLES = new ArrayList<>();

	private LimbContact() {}

	/** Starts measuring (every tick the AI dragon is drawn standing on the ground). */
	public static void start() {
		SAMPLES.clear();
		enabled = true;
	}

	/** Stops measuring and hands over what was measured. */
	public static List<Sample> stop() {
		enabled = false;
		List<Sample> out = List.copyOf(SAMPLES);
		SAMPLES.clear();
		return out;
	}

	public static void afterRender(EnderDragon dragon, GeoModel<?> model, float partialTick) {
		if (!enabled || dragon.isNoAi() || !DragonswornDragon.brain(dragon).onGround()) return;
		if (!SAMPLES.isEmpty() && SAMPLES.get(SAMPLES.size() - 1).tick() == dragon.tickCount) return;
		// The model as drawn, by the same forward kinematics the limb layer uses (checked against
		// GeckoLib's bone pivots). Not GeckoLib's tracked world matrix: in 4.9 it carries an extra twice
		// the identity, so any point off a bone's pivot comes out twice as far from it.
		BodyFrame frame = LimbAnimator.frame(dragon, partialTick);
		LimbAnimator.Clearance clearance = new LimbAnimator.Clearance(dragon.level(), frame);
		double[] gap = new double[ROOTS.length], ground = new double[ROOTS.length];
		for (int i = 0; i < ROOTS.length; i++) {
			Optional<GeoBone> root = model.getBone(ROOTS[i]);
			if (root.isEmpty() || root.get().getParent() == null) return;
			// the closest any part of the limb comes to the ground under that part: 0 resting on it,
			// positive floating above it, negative inside it
			gap[i] = -clearance.of(root.get(), LimbAnimator.matrix(root.get().getParent()));
			// the ground under the limb's own pivot (the ankle, the hand's apex): flat or a step there
			GeoBone b = root.get();
			double[] at = frame.toWorld(Affine.apply(LimbAnimator.matrix(b), new double[]{b.getPivotX(), b.getPivotY(), b.getPivotZ()}, new double[3]), new double[3]);
			ground[i] = groundTop(dragon.level(), at[0], at[1] + 2.0, at[2]);
		}
		String anim = DragonswornDragon.brain(dragon).choice().anim().name().toLowerCase(Locale.ROOT);
		// where each foot (ankle, wrist claw) is drawn, and whether it is in a turn's step
		double[][] feet = new double[ROOTS.length][];
		boolean[] stepping = new boolean[ROOTS.length];
		for (int i = 0; i < ROOTS.length; i++) {
			feet[i] = LimbAnimator.footPoint(model, dragon, i, partialTick);
			stepping[i] = LimbAnimator.stepping(dragon, i);
		}
		SAMPLES.add(new Sample(dragon.tickCount, anim, gap, ground, feet, stepping, (float) DragonswornDragon.brain(dragon).body.yaw(partialTick)));
	}

	private static void collect(GeoBone bone, List<GeoBone> out) {
		out.add(bone);
		for (GeoBone child : bone.getChildBones()) collect(child, out);
	}

	/** The top of the first solid block at or below a little above (x, y, z). */
	private static double groundTop(Level level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y) + 3, Mth.floor(z));
		for (int i = 0; i < 12; i++, pos.move(Direction.DOWN)) {
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
		}
		return Double.NaN;
	}
}
