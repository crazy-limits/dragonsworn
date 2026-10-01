package crazylimits.dragonfall.mc.client;

import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.anim.DragonAnimSelector.Kind;
import crazylimits.dragonfall.body.DragonBody;
import crazylimits.dragonfall.body.Tail;
import crazylimits.dragonfall.body.TailChain;
import crazylimits.dragonfall.body.TailMotion;
import crazylimits.dragonfall.limb.Affine;
import crazylimits.dragonfall.limb.BodyFrame;
import crazylimits.dragonfall.limb.HeadLook;
import crazylimits.dragonfall.limb.Joint;
import crazylimits.dragonfall.limb.LimbIK;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.LevelGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The layer over the keyframes that gives every limb its own footing, every frame (the technique games
 * call foot IK, or terrain adaptation):
 * <ul>
 *   <li><b>Feet.</b> The animation keeps stepping as it was made to (its strides are matched to the
 *       ground speed, so planted feet do not slide), but it was made for flat ground. Each limb measures
 *       the real ground under its own foot against that flat ground and is carried up or down by the
 *       difference by {@link LimbIK}: the hind legs bend at hip and knee, the folded wings (the dragon
 *       walks on its wrists) rise or drop at the shoulder. So on uneven ground one foot stands lower than
 *       another, and every limb still meets the body where it should. The body tilts and settles with the
 *       ground first ({@link DragonBody#ground}); the limbs take up the rest.</li>
 *   <li><b>Head.</b> The neck carries the head round to whatever the dragon is paying attention to
 *       ({@link HeadLook}); the tail answers the other way ({@link #tailYaw}, bent in with the rest of
 *       the tail by {@link DragonModel}).</li>
 * </ul>
 * A limb the animation lifts well off the ground on purpose (a wing spread in a roar) is left alone.
 * Editor space (pixels, degrees) as in {@code tools/rig.py}; GeckoLib's bones hold exactly that.
 */
final class LimbAnimator {
	private static final String[][] LEGS = {{"upperleg_left", "lowerleg_left", "foot_left"}, {"upperleg_right", "lowerleg_right", "foot_right"}};
	private static final String[] SHOULDERS = {"left_wing", "right_wing"};
	/** The bone carrying the wrist claw, and which of its cubes is the claw (as in {@code tools/walk.py}). */
	private static final String[] CLAWS = {"left_wing_tip6", "right_wing_tip6"};
	private static final int CLAW_CUBE = 2;
	/** The folded hand (the fan and its fingers): it must clear the ground it hangs over. */
	private static final String[] HANDS = {"left_wing_tip2", "right_wing_tip2"};
	private static final String[] NECK = {"neck_1", "neck_2", "neck_3", "neck_4"};
	/** A foot the animation holds this high above its ground (pixels) is lifted on purpose: left alone by RELEASE_TOP. */
	private static final double RELEASE_FROM = 20.0, RELEASE_TOP = 36.0;
	/** The furthest a foot is carried up or down, blocks. */
	private static final double MAX_SHIFT = 1.6;
	/** Per tick: how fast a foot's shift follows the ground, rising and dropping (rising is quicker). */
	private static final double RISE = 0.7, DROP = 0.35;

	/** Per dragon, client side. */
	static final class State {
		final HeadLook look = new HeadLook();
		/** The tail as drawn: its solver (it remembers how it keeps out of blocks), chain, motion, world. */
		final Tail tail = new Tail();
		final TailChain chain = new TailChain();
		final TailMotion.Pose motion = new TailMotion.Pose();
		final Tail.World world = new Tail.World();
		/** The debug animation forced on the model, and since when (ticks). */
		DragonAnim forced;
		double forcedSince;
		/** How far each foot is carried up (blocks, negative: down), eased. */
		final double[] shift = new double[4];
		/** How much the feet follow the ground, 0..1. */
		double footing, last = Double.NaN;
	}

	private static final Map<EnderDragon, State> STATES = new WeakHashMap<>();

	private LimbAnimator() {}

	static State state(EnderDragon dragon) {
		return STATES.computeIfAbsent(dragon, d -> new State());
	}

	static void apply(GeoModel<?> model, EnderDragon dragon, float partialTick) {
		DragonBrain brain = DragonfallDragon.brain(dragon);
		State state = state(dragon);
		double time = dragon.tickCount + partialTick;
		double dt = Double.isNaN(state.last) ? 1.0 : Math.max(0.0, Math.min(5.0, time - state.last));
		state.last = time;
		Kind kind = brain.kind();
		DragonAnim action = brain.action();
		boolean standing = kind != Kind.AIR && kind != Kind.DYING;
		// the takeoff pushes off with its feet on the ground until the jump
		if (kind == Kind.AIR && action == DragonAnim.TAKEOFF && brain.clock.anim() == DragonAnim.TAKEOFF
				&& brain.clock.seconds() < DragonAnim.TAKEOFF_JUMP_SECONDS) standing = true;
		state.footing += ((standing ? 1.0 : 0.0) - state.footing) * (1.0 - Math.pow(0.7, dt));
		if (!standing && state.footing < 0.01) state.footing = 0.0;

		BodyFrame frame = frame(dragon, partialTick);

		look(model, dragon, brain, state, frame, time, kind, action);
		if (state.footing <= 0.0) {
			java.util.Arrays.fill(state.shift, 0.0);
			return;
		}
		feet(model, dragon, state, frame, dt);
	}

	/** Where the model is drawn this frame: as the renderer places it ({@code DragonRenderer#applyRotations}). */
	static BodyFrame frame(EnderDragon dragon, float partialTick) {
		DragonBody body = DragonfallDragon.brain(dragon).body;
		return new BodyFrame().set(Mth.lerp(partialTick, dragon.xo, dragon.getX()),
				Mth.lerp(partialTick, dragon.yo, dragon.getY()) + body.lift(partialTick), Mth.lerp(partialTick, dragon.zo, dragon.getZ()),
				body.yaw(partialTick), body.pitch(partialTick), body.roll(partialTick));
	}

	// ---------------------------------------------------------------- the feet

	private static void feet(GeoModel<?> model, EnderDragon dragon, State state, BodyFrame frame, double dt) {
		GeoBone bodyBone = bone(model, "body");
		if (bodyBone == null) return;
		double[] bodyM = matrix(bodyBone);
		Level level = dragon.level();
		for (int s = 0; s < 2; s++) {
			// hind leg: the ankle (the foot's pivot) is carried by the ground's difference under it
			GeoBone[] bones = new GeoBone[3];
			Joint[] leg = new Joint[3];
			for (int k = 0; k < 3; k++) {
				bones[k] = bone(model, LEGS[s][k]);
				if (bones[k] == null) return;
				leg[k] = joint(bones[k]);
			}
			double[] shin = Affine.mul(Affine.mul(bodyM, leg[0].local()), leg[1].local());
			Joint foot = leg[2];
			double[] ankle = Affine.apply(shin, new double[]{foot.pivot[0] + foot.pos[0], foot.pivot[1] + foot.pos[1], foot.pivot[2] + foot.pos[2]}, new double[3]);
			Clearance ground = new Clearance(level, frame);
			double[] target = shifted(level, frame, state, s, ankle, ankle[1] - 3.0, ground.of(bones[2], shin), dt);
			if (target != null) {
				LimbIK.solveLeg(bodyM, leg[0], leg[1], leg[2], target);
				for (int k = 0; k < 3; k++) setRotation(bones[k], leg[k]);
			}

			// front limb: the wrist claw, carried the same way by turning the shoulder
			GeoBone shoulderBone = bone(model, SHOULDERS[s]), claw = bone(model, CLAWS[s]);
			if (shoulderBone == null || claw == null || claw.getCubes().size() <= CLAW_CUBE) return;
			double[] clawPoint = Affine.apply(matrix(claw), clawRest(claw.getCubes().get(CLAW_CUBE)), new double[3]);
			GeoBone hand = bone(model, HANDS[s]);
			double handClear = hand == null || hand.getParent() == null ? Double.NEGATIVE_INFINITY : ground.of(hand, matrix(hand.getParent()));
			target = shifted(level, frame, state, 2 + s, clawPoint, clawPoint[1], handClear, dt);
			if (target != null) {
				Joint animated = joint(shoulderBone);
				// the claw as the shoulder carries it: in the frame its turn moves
				double[] carried = Affine.apply(Affine.invertRigid(Affine.mul(bodyM, animated.local())), clawPoint, new double[3]);
				double wantY = target[1];
				// The wing turns about the shoulder, so the hand's inner parts rise less than the wrist:
				// raise the wrist again by whatever of the hand is still inside the ground.
				for (int pass = 0; pass < 3; pass++) {
					Joint shoulder = joint(shoulderBone);
					shoulder.rot[2] = animated.rot[2];
					LimbIK.solveWing(bodyM, shoulder, carried, wantY);
					setRotation(shoulderBone, shoulder);
					if (hand == null || hand.getParent() == null) break;
					double inside = new Clearance(level, frame).of(hand, matrix(hand.getParent())) * held(clawPoint[1]) * state.footing;
					if (inside < 0.01) break;
					wantY += inside * 16.0;
				}
			}
		}
	}

	/**
	 * Where {@code point} (model pixels) must go so foot {@code i} stands on the real ground: moved up or
	 * down by the difference between that ground and the flat ground the animation was made for (model
	 * y = 0 under the foot), eased per foot; and at least {@code clear} (blocks) up, so no part of the
	 * limb is inside the ground under it (a heel or toe, the edge of a folded hand over a step).
	 * {@code sole}: the model height of the foot's bottom. Null when there is nothing to do.
	 */
	private static double[] shifted(Level level, BodyFrame frame, State state, int i, double[] point, double sole, double clear, double dt) {
		double[] flat = frame.toWorld(new double[]{point[0], 0.0, point[2]}, new double[3]);
		double ground = groundTop(level, flat[0], flat[1] + 2.0, flat[2]);
		double want = Double.isNaN(ground) ? 0.0 : ground - flat[1];
		want = Math.max(-MAX_SHIFT, Math.min(MAX_SHIFT, Math.max(want, clear)));
		// a foot lifted high on purpose is the animation's
		double held = held(sole);
		want *= held * state.footing;
		double rate = want > state.shift[i] ? RISE : DROP;
		state.shift[i] += (want - state.shift[i]) * (1.0 - Math.pow(1.0 - rate, dt));
		// never inside the ground: a limb coming onto a higher block at a step's edge lifts at once
		double floor = Math.min(MAX_SHIFT, clear) * held * state.footing;
		if (state.shift[i] < floor) state.shift[i] = floor;
		if (Math.abs(state.shift[i]) < 1e-4) return null;
		double[] world = frame.toWorld(point, new double[3]);
		world[1] += state.shift[i];
		return frame.toModel(world, world);
	}

	/** How much a foot whose bottom the animation holds {@code sole} pixels up is the ground's, 0..1. */
	private static double held(double sole) {
		return 1.0 - smooth((sole - RELEASE_FROM) / (RELEASE_TOP - RELEASE_FROM));
	}

	/** The middle of the claw cube's underside, in the bone's rest coordinates (pixels). */
	private static double[] clawRest(GeoCube cube) {
		double low = Double.MAX_VALUE, x = 0, z = 0;
		int n = 0;
		for (GeoQuad quad : cube.quads()) {
			if (quad == null) continue;
			for (GeoVertex v : quad.vertices()) low = Math.min(low, v.position().y);
		}
		for (GeoQuad quad : cube.quads()) {
			if (quad == null) continue;
			for (GeoVertex v : quad.vertices()) {
				if (v.position().y > low + 1e-4) continue;
				x += v.position().x;
				z += v.position().z;
				n++;
			}
		}
		double[] p = {x / n * 16.0, low * 16.0, z / n * 16.0};
		return Affine.apply(cubeMatrix(cube), p, p);
	}

	/**
	 * The top of the ground at or below (x, y, z): what a foot stands on. Plants and leaves the dragon
	 * tramples ({@link LevelGrid#breakable}) are not ground; NaN when nothing is near.
	 */
	private static double groundTop(Level level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
		for (int i = 0; i < 6; i++, pos.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || LevelGrid.breakable(state)) continue;
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
		}
		return Double.NaN;
	}

	// ---------------------------------------------------------------- the head

	private static void look(GeoModel<?> model, EnderDragon dragon, DragonBrain brain, State state, BodyFrame frame, double time, Kind kind, DragonAnim action) {
		GeoBone head = bone(model, "head_group");
		if (head == null) return;
		Entity target = brain.lookTarget();
		// an attack, a roar or the breath aims the head itself
		boolean free = action == null && kind != Kind.DYING && kind != Kind.PERCH_BREATH && kind != Kind.PERCH_FLAMING;
		double wantYaw = Double.NaN, wantPitch = Double.NaN;
		if (target != null && free) {
			double[] m = matrix(head);
			double[] eye = Affine.apply(m, new double[]{head.getPivotX(), head.getPivotY(), head.getPivotZ()}, new double[3]);
			// the head's forward (-Z) now, as animated
			double fx = -m[2], fy = -m[6], fz = -m[10];
			double[] at = frame.toModel(new double[]{target.getX(), target.getEyeY(), target.getZ()}, new double[3]);
			double dx = at[0] - eye[0], dy = at[1] - eye[1], dz = at[2] - eye[2];
			wantYaw = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, -dz) - Math.atan2(-fx, -fz)));
			wantPitch = Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)) - Math.atan2(fy, Math.hypot(fx, fz)));
		}
		state.look.update(time, wantYaw, wantPitch, free ? (kind == Kind.AIR ? 0.7 : 1.0) : 0.0);
		for (int i = 0; i < NECK.length; i++) add(bone(model, NECK[i]), state.look.neckPitch(i), state.look.neckYaw(i));
		add(head, state.look.headPitch(), state.look.headYaw());
	}

	/** How far each tail segment swings against the head's turn, degrees. */
	static double tailYaw(EnderDragon dragon) {
		return state(dragon).look.tailYaw(TailChain.SEGMENTS);
	}

	// ---------------------------------------------------------------- bones

	private static GeoBone bone(GeoModel<?> model, String name) {
		return model.getAnimationProcessor().getBone(name);
	}

	private static Joint joint(GeoBone bone) {
		return new Joint().set(new double[]{bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()},
				new double[]{-bone.getPosX(), bone.getPosY(), bone.getPosZ()},
				new double[]{Math.toDegrees(bone.getRotX()), Math.toDegrees(bone.getRotY()), Math.toDegrees(bone.getRotZ())});
	}

	/** The bone's matrix in the model (pixels), through all its parents. */
	static double[] matrix(GeoBone bone) {
		double[] local = joint(bone).local();
		return bone.getParent() == null ? local : Affine.mul(matrix(bone.getParent()), local);
	}

	/**
	 * How far a limb is inside the ground: over every corner of every cube under a bone, the most the
	 * ground under that corner is above it (blocks; negative: the limb is clear by that much). The world
	 * columns are looked up once each.
	 */
	static final class Clearance {
		private final Level level;
		private final BodyFrame frame;
		private final Map<Long, Double> columns = new java.util.HashMap<>();
		private final double[] p = new double[3];

		Clearance(Level level, BodyFrame frame) {
			this.level = level;
			this.frame = frame;
		}

		/** The most any part of {@code bone}'s subtree is inside the ground; {@code parent}: its parent's model matrix. */
		double of(GeoBone bone, double[] parent) {
			return of(bone, parent, Double.NEGATIVE_INFINITY);
		}

		private double of(GeoBone bone, double[] parent, double worst) {
			if (bone.isHidden()) return worst;
			double[] m = Affine.mul(parent, joint(bone).local());
			for (GeoCube cube : bone.getCubes()) {
				double[] cm = Affine.mul(m, cubeMatrix(cube));
				for (GeoQuad quad : cube.quads()) {
					if (quad == null) continue;
					for (GeoVertex vertex : quad.vertices()) {
						p[0] = vertex.position().x * 16.0;
						p[1] = vertex.position().y * 16.0;
						p[2] = vertex.position().z * 16.0;
						frame.toWorld(Affine.apply(cm, p, p), p);
						double g = ground(p[0], p[1], p[2]);
						if (!Double.isNaN(g)) worst = Math.max(worst, g - p[1]);
					}
				}
			}
			for (GeoBone child : bone.getChildBones()) worst = of(child, m, worst);
			return worst;
		}

		/** The ground top in the column at x, z, searched down from a little above y. */
		private double ground(double x, double y, double z) {
			int bx = Mth.floor(x), bz = Mth.floor(z), by = Mth.floor(y);
			long key = ((long) bx << 38) ^ ((long) (bz & 0x3FFFFFF) << 12) ^ (by & 0xFFF);
			Double known = columns.get(key);
			if (known == null) {
				known = groundTop(level, x, y + 2.0, z);
				columns.put(key, known);
			}
			return known;
		}
	}

	/**
	 * A cube's own turn inside its bone (pixels): GeckoLib keeps it out of the vertices and turns the
	 * cube about its pivot when drawing, Z then Y then X like a bone.
	 */
	static double[] cubeMatrix(GeoCube cube) {
		Joint j = new Joint();
		// GeckoLib keeps the cube's pivot in blocks (mirrored like the vertices) and its turn in radians
		j.pivot[0] = cube.pivot().x * 16.0;
		j.pivot[1] = cube.pivot().y * 16.0;
		j.pivot[2] = cube.pivot().z * 16.0;
		j.rot[0] = Math.toDegrees(cube.rotation().x);
		j.rot[1] = Math.toDegrees(cube.rotation().y);
		j.rot[2] = Math.toDegrees(cube.rotation().z);
		return j.local();
	}

	private static void setRotation(GeoBone bone, Joint joint) {
		bone.setRotX((float) Math.toRadians(joint.rot[0]));
		bone.setRotY((float) Math.toRadians(joint.rot[1]));
		bone.setRotZ((float) Math.toRadians(joint.rot[2]));
	}

	private static void add(GeoBone bone, double pitch, double yaw) {
		if (bone == null) return;
		bone.setRotX(bone.getRotX() + (float) Math.toRadians(pitch));
		bone.setRotY(bone.getRotY() + (float) Math.toRadians(yaw));
	}

	private static double smooth(double u) {
		u = Math.max(0.0, Math.min(1.0, u));
		return u * u * (3.0 - 2.0 * u);
	}
}
