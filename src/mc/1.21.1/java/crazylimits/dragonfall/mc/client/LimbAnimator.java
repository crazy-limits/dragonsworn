package crazylimits.dragonfall.mc.client;

import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.anim.DragonAnimSelector.Kind;
import crazylimits.dragonfall.body.DragonBody;
import crazylimits.dragonfall.body.Grip;
import crazylimits.dragonfall.body.Tail;
import crazylimits.dragonfall.body.TailChain;
import crazylimits.dragonfall.body.TailMotion;
import crazylimits.dragonfall.limb.Affine;
import crazylimits.dragonfall.limb.BodyFrame;
import crazylimits.dragonfall.limb.HeadLook;
import crazylimits.dragonfall.limb.Joint;
import crazylimits.dragonfall.limb.LimbIK;
import crazylimits.dragonfall.limb.Toes;
import crazylimits.dragonfall.limb.TurnSteps;
import crazylimits.dragonfall.anim.DragonVoice;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.LevelGrid;
import crazylimits.dragonfall.mc.PreyHold;
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
 *   <li><b>Turning on the spot.</b> Standing, the dragon does not spin on its planted feet: they stay
 *       where they stand while the body turns over them, and step round after it in diagonal pairs
 *       ({@link TurnSteps}); IK reaches them sideways too (the thigh splays at the hip, the folded wing
 *       turns at the shoulder and bends at the elbow), so a planted foot or wrist never slides.</li>
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
	/** The elbow hinge (turns about Z only: the wing rule). */
	private static final String[] ELBOWS = {"left_wing_tip", "right_wing_tip"};
	/** The bone carrying the wrist claw, and which of its cubes is the claw (as in {@code tools/walk.py}). */
	private static final String[] CLAWS = {"left_wing_tip6", "right_wing_tip6"};
	private static final int CLAW_CUBE = 2;
	/** The folded hand (the fan and its fingers): it must clear the ground it hangs over. */
	private static final String[] HANDS = {"left_wing_tip2", "right_wing_tip2"};
	/** The fan's apex (its pivot): where the folded hand stands on the ground, as {@code tools/walk.py} plants it. */
	private static final String[] APEXES = {"left_wing_tip3", "right_wing_tip3"};
	private static final String[] NECK = {"neck_1", "neck_2", "neck_3", "neck_4"};
	/** Each hind foot's toes (see {@link Toes}): the three in front, then the back toe. */
	private static final String[][] TOES = {{"foot_left_toe1", "foot_left_toe2", "foot_left_toe3", "foot_left_back_toe"},
			{"foot_right_toe1", "foot_right_toe2", "foot_right_toe3", "foot_right_back_toe"}};
	/** A foot this far over the ground (pixels) is off it: its toes hang. */
	private static final double TOE_CONTACT = 4.0;
	/** The curls a toe is tried at for what stops it (see {@link #lowestClear}), degrees. */
	private static final double TOE_TOP = 50.0, TOE_STEP = 6.0;
	/** A foot the animation holds this high above its ground (pixels) is lifted on purpose: left alone by RELEASE_TOP. */
	private static final double RELEASE_FROM = 20.0, RELEASE_TOP = 36.0;
	/** The furthest a foot is carried up or down, blocks. */
	private static final double MAX_SHIFT = 1.6;
	/** A planted foot its limb misses by more than this (pixels) is dragged: it must step. */
	private static final double STRAIN = 0.1;
	/** Per tick: how fast a foot's shift follows the ground, rising and dropping (rising is quicker). */
	private static final double RISE = 0.7, DROP = 0.35;

	/** Per dragon, client side. */
	static final class State {
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
		/**
		 * How far each hind foot (left, right) has gone over to reaching for, or holding, prey (0..1), where to
		 * (model pixels); how far the reaching feet are tipped up (degrees), and how far the right foot has
		 * closed on its prey (0..1: held level and turned across it, {@link Grip#TALON_YAW}).
		 */
		final double[] talon = new double[2];
		final double[][] talonAim = new double[2][3];
		double reachLift, clutch;
		/** The toes of each hind foot, and how far they are opened for a landing (0..1). */
		final Toes[] toes = {new Toes(), new Toes()};
		double landingOpen;
		/** The feet stepping round as it turns on the spot. */
		final TurnSteps steps = new TurnSteps();
		/** The head and neck hitboxes: their centres on the last two ticks, and how far they were from the drawn anchors. */
		final double[][] hitPrev = new double[3][], hitNow = new double[3][];
		final double[] drawnOff = new double[3];
		int hitTick = Integer.MIN_VALUE, drawnFrames;
	}

	/** Animations that stand still on their feet: a turn on the spot steps round in them. */
	private static boolean planted(DragonAnim anim) {
		return anim == DragonAnim.IDLE || anim == DragonAnim.ATTACK || anim == DragonAnim.ROAR || anim == DragonAnim.BREATH
				|| anim == DragonAnim.UPRIGHT || anim == DragonAnim.UPRIGHT_BITE || anim == DragonAnim.CLING || anim == DragonAnim.CLING_BITE;
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
		// on the ground: also the takeoff until the jump, and a running landing once its feet strike
		boolean standing = brain.footing();
		state.footing += ((standing ? 1.0 : 0.0) - state.footing) * (1.0 - Math.pow(0.7, dt));
		if (!standing && state.footing < 0.01) state.footing = 0.0;

		BodyFrame frame = frame(dragon, partialTick);

		look(model, dragon, brain, state, frame, partialTick);
		talon(model, dragon, brain, state, frame, partialTick, dt);
		if (state.footing <= 0.0) {
			java.util.Arrays.fill(state.shift, 0.0);
		} else {
			boolean turning = standing && kind != Kind.AIR && planted(brain.clock.anim()) && brain.prey.hold() == Grip.Hold.NONE;
			feet(model, dragon, state, frame, dt, turning);
		}
		toes(model, dragon, brain, state, frame, partialTick, dt);
	}

	/** Where the model is drawn this frame: as the renderer places it ({@code DragonRenderer#applyRotations}). */
	static BodyFrame frame(EnderDragon dragon, float partialTick) {
		DragonBody body = DragonfallDragon.brain(dragon).body;
		return new BodyFrame().set(Mth.lerp(partialTick, dragon.xo, dragon.getX()),
				Mth.lerp(partialTick, dragon.yo, dragon.getY()) + body.lift(partialTick), Mth.lerp(partialTick, dragon.zo, dragon.getZ()),
				body.yaw(partialTick), body.pitch(partialTick), body.roll(partialTick));
	}

	// ---------------------------------------------------------------- the feet

	private static void feet(GeoModel<?> model, EnderDragon dragon, State state, BodyFrame frame, double dt, boolean turning) {
		GeoBone bodyBone = bone(model, "body");
		if (bodyBone == null) return;
		double[] bodyM = matrix(bodyBone);
		Level level = dragon.level();
		// where the animation puts each foot: the hind ankles, the wrist claws
		GeoBone[][] legBones = new GeoBone[2][3];
		double[][] points = new double[4][];
		double[][] shins = new double[2][];
		for (int s = 0; s < 2; s++) {
			for (int k = 0; k < 3; k++) {
				legBones[s][k] = bone(model, LEGS[s][k]);
				if (legBones[s][k] == null) return;
			}
			Joint thigh = joint(legBones[s][0]), shin = joint(legBones[s][1]), foot = joint(legBones[s][2]);
			shins[s] = Affine.mul(Affine.mul(bodyM, thigh.local()), shin.local());
			points[s] = Affine.apply(shins[s], new double[]{foot.pivot[0] + foot.pos[0], foot.pivot[1] + foot.pos[1], foot.pivot[2] + foot.pos[2]}, new double[3]);
			GeoBone apex = bone(model, APEXES[s]);
			if (apex == null) return;
			points[2 + s] = Affine.apply(matrix(apex), new double[]{apex.getPivotX(), apex.getPivotY(), apex.getPivotZ()}, new double[3]);
		}
		// turning on the spot: the planted feet hold still, and step round after the body
		double[][] places = new double[4][2];
		for (int i = 0; i < 4; i++) {
			double[] w = frame.toWorld(points[i], new double[3]);
			places[i][0] = w[0];
			places[i][1] = w[2];
		}
		double[] middle = frame.toWorld(new double[]{0.0, 0.0, 0.0}, new double[3]);
		state.steps.update(dt, places, middle[0], middle[2], turning, foot -> DragonAudio.play(dragon, foot < 2 ? DragonVoice.Cue.STEP_HIND : DragonVoice.Cue.STEP_FRONT));

		Clearance ground = new Clearance(level, frame);
		for (int s = 0; s < 2; s++) {
			// hind leg: the ankle (the foot's pivot) is carried by the ground's difference under it
			GeoBone[] bones = legBones[s];
			Joint[] leg = new Joint[3];
			for (int k = 0; k < 3; k++) leg[k] = joint(bones[k]);
			double[] ankle = points[s];
			double held = held(ankle[1] - 3.0);
			double[] moved = stepped(frame, state, s, ankle, held);
			double[] target = shifted(level, frame, state, s, moved, ankle[1] - 3.0, ground.of(bones[2], shins[s]), dt);
			target = lifted(state, s, moved, target, held);
			if (target != null && moved != ankle) {
				// carried round in a turn: where it goes may be higher ground than the animation's place,
				// so the foot is raised again by whatever of it is still inside the ground there
				Joint[] animatedLeg = {joint(bones[0]), joint(bones[1]), joint(bones[2])};
				for (int pass = 0; pass < 3; pass++) {
					for (int k = 0; k < 3; k++) leg[k] = joint(bones[k]);
					for (int k = 0; k < 3; k++) System.arraycopy(animatedLeg[k].rot, 0, leg[k].rot, 0, 3);
					double left = LimbIK.solveLegReach(bodyM, leg[0], leg[1], leg[2], target);
					if (pass == 0 && left > STRAIN && !state.steps.stepping(s)) state.steps.strain(s);
					for (int k = 0; k < 3; k++) setRotation(bones[k], leg[k]);
					double inside = new Clearance(level, frame).of(bones[2], Affine.mul(Affine.mul(bodyM, leg[0].local()), leg[1].local())) * held * state.footing;
					if (!(inside >= 0.01)) break; // NaN too: -Infinity (no ground near) times a lifted foot's 0
					target[1] += inside * 16.0;
				}
			} else if (target != null) {
				LimbIK.solveLeg(bodyM, leg[0], leg[1], leg[2], target);
				for (int k = 0; k < 3; k++) setRotation(bones[k], leg[k]);
			}

			// front limb: the wrist claw, carried the same way by turning the shoulder
			GeoBone shoulderBone = bone(model, SHOULDERS[s]), claw = bone(model, CLAWS[s]);
			if (shoulderBone == null || claw == null || claw.getCubes().size() <= CLAW_CUBE) return;
			double[] clawPoint = Affine.apply(matrix(claw), clawRest(claw.getCubes().get(CLAW_CUBE)), new double[3]);
			GeoBone hand = bone(model, HANDS[s]);
			double handClear = hand == null || hand.getParent() == null ? Double.NEGATIVE_INFINITY : ground.of(hand, matrix(hand.getParent()));
			// a turn holds the folded hand where it stands (by its apex), the claw going with it; only a
			// hand down on the ground (it lies flat in the stance: the gap under its lowest point, not
			// any one point's height, says whether it stands on it)
			double[] apex = points[2 + s];
			double apexHeld = Double.isInfinite(handClear) ? 0.0 : held(-handClear * 16.0);
			double[] apexMoved = stepped(frame, state, 2 + s, apex, apexHeld);
			boolean handMoved = apexMoved != apex;
			double[] clawMoved = handMoved ? new double[]{clawPoint[0] + apexMoved[0] - apex[0], clawPoint[1] + apexMoved[1] - apex[1], clawPoint[2] + apexMoved[2] - apex[2]} : clawPoint;
			target = shifted(level, frame, state, 2 + s, clawMoved, clawPoint[1], handClear, dt);
			target = lifted(state, 2 + s, clawMoved, target, apexHeld);
			GeoBone elbowBone = bone(model, ELBOWS[s]);
			if (target != null) {
				Joint animated = joint(shoulderBone);
				// the claw as the shoulder carries it: in the frame its turn moves
				double[] carried = Affine.apply(Affine.invertRigid(Affine.mul(bodyM, animated.local())), clawPoint, new double[3]);
				// the apex as the elbow carries it
				Joint animatedElbow = elbowBone == null ? null : joint(elbowBone);
				double[] atElbow = animatedElbow == null ? null
						: Affine.apply(Affine.invertRigid(Affine.mul(Affine.mul(bodyM, animated.local()), animatedElbow.local())), apex, new double[3]);
				// how far the ground (and the step's arc) raise the hand
				double rise = target[1] - clawMoved[1];
				// The wing turns about the shoulder, so the hand's inner parts rise less than the wrist:
				// raise the wrist again by whatever of the hand is still inside the ground.
				for (int pass = 0; pass < 3; pass++) {
					Joint shoulder = joint(shoulderBone);
					System.arraycopy(animated.rot, 0, shoulder.rot, 0, 3);
					if (handMoved && atElbow != null) {
						// a planted hand held where it stands: shoulder and elbow together
						Joint elbow = joint(elbowBone);
						double left = LimbIK.solveArmReach(bodyM, shoulder, elbow, atElbow, new double[]{apexMoved[0], apexMoved[1] + rise, apexMoved[2]});
						// at the end of its reach: the hand must step, or it would be dragged
						if (left > STRAIN && !state.steps.stepping(2 + s)) state.steps.strain(2 + s);
						setRotation(elbowBone, elbow);
					} else {
						LimbIK.solveWing(bodyM, shoulder, carried, clawPoint[1] + rise);
					}
					setRotation(shoulderBone, shoulder);
					if (hand == null || hand.getParent() == null) break;
					double inside = new Clearance(level, frame).of(hand, matrix(hand.getParent())) * held(clawPoint[1]) * state.footing;
					if (!(inside >= 0.01)) break; // NaN too: -Infinity (no ground near) times a lifted foot's 0
					rise += inside * 16.0;
				}
			}
		}
	}

	/**
	 * Where foot {@code i} (0, 1 the hind ankles, 2, 3 the folded hands' apexes) is drawn now, world blocks; null
	 * when the model lacks the bones. For the showcase's check that planted feet do not slide.
	 */
	static double[] footPoint(GeoModel<?> model, EnderDragon dragon, int i, float partialTick) {
		BodyFrame frame = frame(dragon, partialTick);
		int s = i % 2;
		if (i < 2) {
			GeoBone shin = bone(model, LEGS[s][1]), foot = bone(model, LEGS[s][2]);
			if (shin == null || foot == null) return null;
			Joint f = joint(foot);
			double[] p = {f.pivot[0] + f.pos[0], f.pivot[1] + f.pos[1], f.pivot[2] + f.pos[2]};
			return frame.toWorld(Affine.apply(matrix(shin), p, p), p);
		}
		GeoBone apex = bone(model, APEXES[s]);
		if (apex == null) return null;
		double[] p = Affine.apply(matrix(apex), new double[]{apex.getPivotX(), apex.getPivotY(), apex.getPivotZ()}, new double[3]);
		return frame.toWorld(p, p);
	}

	/** Whether foot {@code i} is lifted in a turn's step now. */
	static boolean stepping(EnderDragon dragon, int i) {
		return state(dragon).steps.stepping(i);
	}

	/**
	 * {@code point} (model pixels, as animated) where the turn's footwork has foot {@code i} now; the same
	 * array when it is where the animation put it. A foot the animation lifts on purpose is left to it.
	 */
	private static double[] stepped(BodyFrame frame, State state, int i, double[] point, double held) {
		double dx = state.steps.offsetX(i) * held, dz = state.steps.offsetZ(i) * held;
		if (Math.abs(dx) < 1e-4 && Math.abs(dz) < 1e-4) return point;
		double[] world = frame.toWorld(point, new double[3]);
		world[0] += dx;
		world[2] += dz;
		return frame.toModel(world, world);
	}

	/** {@code target} with foot {@code i} raised by its step's arc (null: nothing to do, as {@link #shifted}). */
	private static double[] lifted(State state, int i, double[] moved, double[] target, double held) {
		double lift = state.steps.lift(i) * held * 16.0;
		if (target == null && lift < 1e-3 && !state.steps.stepping(i) && Math.abs(state.steps.offsetX(i)) + Math.abs(state.steps.offsetZ(i)) < 1e-4) return null;
		if (target == null) target = moved.clone();
		target[1] += lift;
		return target;
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

	// ---------------------------------------------------------------- the talons

	/**
	 * The snatch ({@link Grip}): as the dragon dives at its prey both hind legs are thrown forward under the
	 * chest, toes spread, as an eagle's are ({@link Grip#REACH_ANKLE}); over the last {@link Grip#REACH_NEAR}
	 * blocks the right foot reaches out for the prey, and once it is caught holds it, toes curled round its
	 * chest, while the left goes back to the animation. Each leg is solved by {@link LimbIK} onto its aim and
	 * blended in over the animation's leg; easing out when the hold ends.
	 */
	private static void talon(GeoModel<?> model, EnderDragon dragon, DragonBrain brain, State state, BodyFrame frame, float partialTick, double dt) {
		Grip.Hold hold = brain.prey.hold();
		Entity prey = brain.prey.prey();
		boolean reach = prey != null && hold == Grip.Hold.REACH, held = prey != null && hold == Grip.Hold.TALON;
		boolean[] on = {reach, reach || held};
		for (int s = 0; s < 2; s++) {
			state.talon[s] += ((on[s] ? 1.0 : 0.0) - state.talon[s]) * (1.0 - Math.pow(held && s == 1 ? 0.5 : 0.82, dt));
			if (!on[s] && state.talon[s] < 0.01) state.talon[s] = 0.0;
		}
		state.reachLift += ((reach ? Grip.REACH_SPREAD : 0.0) - state.reachLift) * (1.0 - Math.pow(0.6, dt));
		state.clutch += ((held ? 1.0 : 0.0) - state.clutch) * (1.0 - Math.pow(0.7, dt));
		if (!held && state.clutch < 0.01) state.clutch = 0.0;
		if (held && state.clutch > 0.99) state.clutch = 1.0;   // closed: the toes freeze on it (Toes)
		if (state.talon[0] == 0.0 && state.talon[1] == 0.0) return;
		if (held) {
			System.arraycopy(Grip.TALON_ANKLE, 0, state.talonAim[1], 0, 3);
		} else if (reach) {
			for (int s = 0; s < 2; s++) {
				state.talonAim[s][0] = (s == 0 ? -1.0 : 1.0) * Grip.REACH_ANKLE[0];
				state.talonAim[s][1] = Grip.REACH_ANKLE[1];
				state.talonAim[s][2] = Grip.REACH_ANKLE[2];
			}
			// close in, the right ankle goes out from there to where its sole comes down on the prey's back
			double[] pad = PreyHold.padOffset(brain.body.yaw(partialTick));
			double[] ankle = {Mth.lerp(partialTick, prey.xo, prey.getX()) - pad[0],
					Mth.lerp(partialTick, prey.yo, prey.getY()) + prey.getBbHeight() / 2.0 + prey.getBbWidth() / 2.0 - pad[1],
					Mth.lerp(partialTick, prey.zo, prey.getZ()) - pad[2]};
			double[] forward = frame.toWorld(state.talonAim[1], new double[3]);
			double gap = Math.sqrt(Mth.lengthSquared(forward[0] - ankle[0], forward[1] - ankle[1], forward[2] - ankle[2]));
			double k = smooth(Mth.clamp(1.0 - gap / Grip.REACH_NEAR, 0.0, 1.0));
			double[] out = frame.toModel(ankle, new double[3]);
			for (int a = 0; a < 3; a++) state.talonAim[1][a] += (out[a] - state.talonAim[1][a]) * k;
		}
		GeoBone bodyBone = bone(model, "body");
		if (bodyBone == null) return;
		double[] bodyM = matrix(bodyBone);
		for (int s = 0; s < 2; s++) {
			if (state.talon[s] == 0.0) continue;
			GeoBone[] bones = new GeoBone[3];
			for (int k = 0; k < 3; k++) {
				bones[k] = bone(model, LEGS[s][k]);
				if (bones[k] == null) return;
			}
			Joint[] animated = new Joint[3], leg = new Joint[3];
			for (int k = 0; k < 3; k++) {
				animated[k] = joint(bones[k]);
				leg[k] = joint(bones[k]);
			}
			LimbIK.solveLeg(bodyM, leg[0], leg[1], leg[2], state.talonAim[s]);
			leg[2].rot[0] += state.reachLift;
			if (s == 1 && state.clutch > 0.0) {
				// holding: the foot level in the world and turned across the prey, its toes to curl round it
				double[] level = levelFoot(Affine.mul(Affine.mul(bodyM, leg[0].local()), leg[1].local()), brain, partialTick);
				double c = smooth(state.clutch);
				for (int a = 0; a < 3; a++) leg[2].rot[a] += wrapDegrees(level[a] - leg[2].rot[a]) * c;
			}
			double w = smooth(state.talon[s]);
			for (int k = 0; k < 3; k++) {
				for (int a = 0; a < 3; a++) leg[k].rot[a] = animated[k].rot[a] + (leg[k].rot[a] - animated[k].rot[a]) * w;
				setRotation(bones[k], leg[k]);
			}
		}
	}

	/**
	 * The foot's rotation (degrees, Z Y X as a bone's) under a shin of model matrix {@code shin} that holds
	 * its sole level in the world (against the body's pitch and roll) with its toes turned {@link Grip#TALON_YAW}.
	 */
	private static double[] levelFoot(double[] shin, DragonBrain brain, float partialTick) {
		// the body's pitch and roll as a rotation of the model's axes (no yaw: then model and world axes agree)
		BodyFrame tilt = new BodyFrame().set(0.0, 0.0, 0.0, 0.0, brain.body.pitch(partialTick), brain.body.roll(partialTick));
		double[] o = tilt.toWorld(new double[3], new double[3]);
		double[] tiltM = Affine.identity();
		for (int c = 0; c < 3; c++) {
			double[] e = new double[3];
			e[c] = 16.0;
			tilt.toWorld(e, e);
			for (int r = 0; r < 3; r++) tiltM[r * 4 + c] = e[r] - o[r];
		}
		// wanted in the model: the tilt undone, then the turn; in the shin's frame
		double[] want = Affine.mul(transpose(tiltM), Affine.rotationZYX(0.0, Grip.TALON_YAW, 0.0));
		double[] m = Affine.mul(transpose(shin), want);
		double b = Math.asin(Math.max(-1.0, Math.min(1.0, -m[8])));
		return new double[]{Math.toDegrees(Math.atan2(m[9], m[10])), Math.toDegrees(b), Math.toDegrees(Math.atan2(m[4], m[0]))};
	}

	/** The rotation part of {@code m}, transposed (inverted). */
	private static double[] transpose(double[] m) {
		double[] t = Affine.identity();
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) t[r * 4 + c] = m[c * 4 + r];
		}
		return t;
	}

	private static double wrapDegrees(double a) {
		return Mth.wrapDegrees(a);
	}

	// ---------------------------------------------------------------- the toes

	/** How far a point (world) is inside something, blocks: positive inside. */
	private interface Inside {
		double at(double[] world);
	}

	/**
	 * The toes ({@link Toes}), last, on the feet as drawn: planted they are straight, in the air they hang
	 * and stir, reaching for the ground to land or for prey they open wide, and the right foot's close round
	 * what it holds, each as far as the prey lets it, and stay so until it lets go.
	 */
	private static void toes(GeoModel<?> model, EnderDragon dragon, DragonBrain brain, State state, BodyFrame frame, float partialTick, double dt) {
		Grip.Hold hold = brain.prey.hold();
		Entity prey = brain.prey.prey();
		boolean reach = prey != null && hold == Grip.Hold.REACH, held = prey != null && hold == Grip.Hold.TALON;
		boolean landing = brain.clock.anim() == DragonAnim.LAND && !brain.footing();
		state.landingOpen += ((landing ? 1.0 : 0.0) - state.landingOpen) * (1.0 - Math.pow(0.85, dt));
		Clearance ground = new Clearance(dragon.level(), frame);
		Inside inPrey = null;
		if (held) {
			double w = prey.getBbWidth() / 2.0 + 0.02, h = prey.getBbHeight() / 2.0 + 0.02;
			double mx = Mth.lerp(partialTick, prey.xo, prey.getX()), my = Mth.lerp(partialTick, prey.yo, prey.getY()) + prey.getBbHeight() / 2.0;
			double mz = Mth.lerp(partialTick, prey.zo, prey.getZ());
			double yaw = brain.prey.lyingYaw(prey, partialTick);
			double r = Math.toRadians(Double.isNaN(yaw) ? brain.body.yaw(partialTick) : yaw), fx = -Math.sin(r), fz = Math.cos(r);
			// it lies flat along its yaw: as long as it is tall, as thick as it is wide
			inPrey = p -> {
				double dx = p[0] - mx, dy = p[1] - my, dz = p[2] - mz;
				double along = dx * fx + dz * fz, across = dx * fz - dz * fx;
				return Math.min(Math.min(w - Math.abs(across), w - Math.abs(dy)), h - Math.abs(along));
			};
		}
		double time = dragon.tickCount + partialTick;
		double[] side = new double[Toes.COUNT - 1], stop = new double[Toes.COUNT];
		for (int s = 0; s < 2; s++) {
			GeoBone foot = bone(model, LEGS[s][2]);
			if (foot == null || foot.getParent() == null) return;
			GeoBone[] toes = new GeoBone[Toes.COUNT];
			for (int i = 0; i < Toes.COUNT; i++) {
				toes[i] = bone(model, TOES[s][i]);
				if (toes[i] == null) return;
			}
			double[] footM = matrix(foot);
			double over = -ground.of(foot, matrix(foot.getParent()));
			double contact = Double.isNaN(over) ? 0.0 : (1.0 - smooth(over * 16.0 / TOE_CONTACT)) * state.footing;
			double grip = s == 1 ? smooth(state.clutch) : 0.0;
			double open = Math.max(state.landingOpen, reach ? smooth(state.talon[s]) : 0.0) * (1.0 - grip);
			Toes t = state.toes[s];
			for (int i = 0; i < Toes.COUNT; i++) {
				// outward: a toe on the foot's +x side swings its tip to +x with a negative turn about Y
				if (i < Toes.BACK) side[i] = -Math.signum(toes[i].getPivotX() - foot.getPivotX());
				// where it meets the prey, while it closes (closed, it is frozen)
				stop[i] = inPrey != null && grip > 0.0 && !t.frozen() ? lowestClear(toes[i], footM, t.spread(i), frame, inPrey) : Double.NEGATIVE_INFINITY;
			}
			t.update(dt, time, contact, open, grip, side, stop);
			// from the rest pose, not on top of the bone's last turn: no animation keys the toes, and GeckoLib
			// does not reset an unkeyed bone every frame, so adding would wind them round and round
			for (int i = 0; i < Toes.COUNT; i++) {
				var rest = toes[i].getInitialSnapshot();
				toes[i].setRotX(rest.getRotX() + (float) Math.toRadians(t.curl(i)));
				toes[i].setRotY(rest.getRotY() + (float) Math.toRadians(t.spread(i)));
				toes[i].setRotZ(rest.getRotZ());
			}
		}
	}

	/**
	 * The lowest curl (degrees, as {@link Toes}) at which no corner of {@code toe} (its rest pose turned by
	 * the curl and {@code spread}, on a foot of model matrix {@code footM}) is inside anything; {@code -Infinity}
	 * when it curls all the way ({@link Toes#GRIP_CURL}) clear, {@link #TOE_TOP} when even that is inside.
	 */
	private static double lowestClear(GeoBone toe, double[] footM, double spread, BodyFrame frame, Inside inside) {
		Joint rest = joint(toe);
		var snapshot = toe.getInitialSnapshot();
		rest.rot[0] = Math.toDegrees(snapshot.getRotX());
		rest.rot[1] = Math.toDegrees(snapshot.getRotY());
		rest.rot[2] = Math.toDegrees(snapshot.getRotZ());
		java.util.function.DoublePredicate clear = c -> {
			double[] m = Affine.mul(footM, rest.local(rest.rot[0] + c, rest.rot[1] + spread, rest.rot[2]));
			double[] p = new double[3];
			for (GeoCube cube : toe.getCubes()) {
				double[] cm = Affine.mul(m, cubeMatrix(cube));
				for (GeoQuad quad : cube.quads()) {
					if (quad == null) continue;
					for (GeoVertex v : quad.vertices()) {
						p[0] = v.position().x * 16.0;
						p[1] = v.position().y * 16.0;
						p[2] = v.position().z * 16.0;
						if (inside.at(frame.toWorld(Affine.apply(cm, p, p), p)) > 0.0) return false;
					}
				}
			}
			return true;
		};
		if (!clear.test(TOE_TOP)) return TOE_TOP;
		double hi = TOE_TOP;
		for (double c = TOE_TOP - TOE_STEP; c >= Toes.GRIP_CURL; c -= TOE_STEP) {
			if (!clear.test(c)) {
				double lo = c;
				for (int k = 0; k < 6; k++) {
					double mid = (hi + lo) / 2.0;
					if (clear.test(mid)) hi = mid;
					else lo = mid;
				}
				return hi;
			}
			hi = c;
		}
		return Double.NEGATIVE_INFINITY;
	}

	// ---------------------------------------------------------------- the head

	/** Where the head and neck hitboxes hang on the model (pixels, as tools/parts.py anchors them): jaw_upper, neck_4, neck_2. */
	private static final String[] DRAWN_BONES = {"jaw_upper", "neck_4", "neck_2"};
	private static final double[][] DRAWN_ANCHORS = {{0, 55, -105}, {0, 54, -78}, {0, 52, -50}};
	private static final int[] DRAWN_PARTS = {0, 1, 8};

	/** The head's look ({@link DragonBrain#look}: ticked with the hitboxes), between ticks. */
	private static void look(GeoModel<?> model, EnderDragon dragon, DragonBrain brain, State state, BodyFrame frame, float partialTick) {
		GeoBone head = bone(model, "head_group");
		if (head == null) return;
		HeadLook look = brain.look;
		for (int i = 0; i < NECK.length; i++) add(bone(model, NECK[i]), look.neckPitch(i, partialTick), look.neckYaw(i, partialTick));
		add(head, look.headPitch(partialTick), look.headYaw(partialTick));
		// how far the head and neck hitboxes are from their anchors as drawn (the showcase checks it):
		// the hitboxes moved on the last tick, the model is drawn between it and the one before
		if (state.hitTick != dragon.tickCount) {
			state.hitTick = dragon.tickCount;
			for (int k = 0; k < DRAWN_PARTS.length; k++) {
				var c = dragon.getSubEntities()[DRAWN_PARTS[k]].getBoundingBox().getCenter();
				state.hitPrev[k] = state.hitNow[k] == null ? new double[]{c.x, c.y, c.z} : state.hitNow[k];
				state.hitNow[k] = new double[]{c.x, c.y, c.z};
			}
		}
		for (int k = 0; k < DRAWN_BONES.length; k++) {
			GeoBone bone = bone(model, DRAWN_BONES[k]);
			if (bone == null) return;
			double[] drawn = frame.toWorld(Affine.apply(matrix(bone), DRAWN_ANCHORS[k], new double[3]), new double[3]);
			double off = 0.0;
			for (int a = 0; a < 3; a++) {
				double d = drawn[a] - Mth.lerp(partialTick, state.hitPrev[k][a], state.hitNow[k][a]);
				off += d * d;
			}
			state.drawnOff[k] = Math.sqrt(off);
		}
		state.drawnFrames++;
	}

	/**
	 * How far the head, front neck and middle neck hitboxes were from their anchors as last drawn
	 * (blocks; the hitboxes taken at the frame's partial tick); null before the dragon is drawn.
	 */
	static double[] hitboxOffsets(EnderDragon dragon) {
		State state = STATES.get(dragon);
		return state == null || state.drawnFrames == 0 ? null : state.drawnOff;
	}

	/** How far each tail segment swings against the head's turn, degrees. */
	static double tailYaw(EnderDragon dragon, float partialTick) {
		return DragonfallDragon.brain(dragon).look.tailYaw(TailChain.SEGMENTS, partialTick);
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
