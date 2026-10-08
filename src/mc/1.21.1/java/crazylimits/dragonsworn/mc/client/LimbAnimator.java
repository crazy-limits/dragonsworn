package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.anim.DragonAnimSelector.Kind;
import crazylimits.dragonsworn.anim.DragonVoice;
import crazylimits.dragonsworn.body.DragonBody;
import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.body.Tail;
import crazylimits.dragonsworn.body.TailChain;
import crazylimits.dragonsworn.body.TailMotion;
import crazylimits.dragonsworn.limb.Affine;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.limb.HeadLook;
import crazylimits.dragonsworn.limb.HeadUpright;
import crazylimits.dragonsworn.limb.Joint;
import crazylimits.dragonsworn.limb.LimbIK;
import crazylimits.dragonsworn.limb.Toes;
import crazylimits.dragonsworn.limb.TurnSteps;
import crazylimits.dragonsworn.math.Maths;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.nav.Surface;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.GeoModel;

import java.util.Arrays;
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
public final class LimbAnimator {
	/** Each hind leg's bones: thigh, shin, foot. */
	static final String[][] LEGS = {{"upperleg_left", "lowerleg_left", "foot_left"}, {"upperleg_right", "lowerleg_right", "foot_right"}};
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
	/** A foot the animation holds this high above its ground (pixels) is lifted on purpose: left alone by RELEASE_TOP. */
	private static final double RELEASE_FROM = 20.0, RELEASE_TOP = 36.0;
	/** The furthest a foot is carried up or down, blocks. */
	private static final double MAX_SHIFT = 1.6;
	/**
	 * A planted foot its limb misses by more than this (pixels) is dragged: it must step, when a step
	 * would bring it at least {@code STEP_GAIN} (pixels) nearer the limb's reach.
	 */
	private static final double STRAIN = 0.1, STEP_GAIN = 2.0;
	/** Per tick: how fast a foot's shift follows the ground, rising and dropping (rising is quicker). */
	private static final double RISE = 0.7, DROP = 0.35;
	/**
	 * A planted limb keeps the height it stands at while it stays within {@code HOLD} (blocks) of where it
	 * came down: the ground under it dropping by more than {@code JUMP} at once (a block's edge, crossed
	 * back and forth by the idle's sway) is not followed. Sinking smoothly, and rising, always are.
	 */
	private static final double HOLD = 0.5, JUMP = 0.1;
	/**
	 * A moving limb rises to the highest ground it will be over within {@code AHEAD} ticks at its speed (eased
	 * at {@code PACE} a tick), so it is up before it gets there instead of jumping up at the block's edge.
	 */
	private static final double AHEAD = 4.0, PACE = 0.5;
	/**
	 * The most (blocks) a limb is raised over the ground under its own foot to keep the rest of it out of
	 * the ground: a heel, a toe, the far edge of a folded hand over higher ground. More would turn the
	 * whole wing up at the shoulder for one fingertip on a hill.
	 */
	private static final double CLEAR_OVER = 0.5;

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
		/** Where each limb stands (surface frame x, z, blocks) and the height it stands at there; NaN: nowhere yet. */
		final double[][] stand = new double[4][3];
		/** Where each limb's point on the ground was last frame (surface frame x, z; NaN: nowhere), and how fast it moves (blocks a tick). */
		final double[][] seen = new double[4][2], pace = new double[4][2];
		{
			for (double[] s : stand) Arrays.fill(s, Double.NaN);
			for (double[] s : seen) Arrays.fill(s, Double.NaN);
		}
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
				|| anim == DragonAnim.UPRIGHT || anim == DragonAnim.UPRIGHT_BITE || anim == DragonAnim.CLING || anim == DragonAnim.CLING_BITE
				|| anim == DragonAnim.WALL || anim == DragonAnim.WALL_BITE || anim == DragonAnim.WALL_ROAR || anim == DragonAnim.WALL_BREATH;
	}

	private static final Map<EnderDragon, State> STATES = new WeakHashMap<>();

	private LimbAnimator() {}

	static State state(EnderDragon dragon) {
		return STATES.computeIfAbsent(dragon, d -> new State());
	}

	static void apply(GeoModel<?> model, EnderDragon dragon, float partialTick) {
		DragonBrain brain = DragonswornDragon.brain(dragon);
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
		// the feet and toes work in the frame of the surface it stands on: on a wall the wall is the ground
		BodyFrame local = surfaceFrame(dragon, partialTick);
		Footing footing = footing(dragon, partialTick);

		look(model, dragon, brain, state, frame, partialTick);
		upright(model, brain, frame, partialTick);
		TalonPose.apply(model, brain, state, frame, partialTick, dt);
		if (state.footing <= 0.0) {
			Arrays.fill(state.shift, 0.0);
			for (double[] stand : state.stand) Arrays.fill(stand, Double.NaN);
			for (double[] seen : state.seen) Arrays.fill(seen, Double.NaN);
		} else {
			boolean turning = standing && kind != Kind.AIR && planted(brain.clock.anim()) && brain.prey.hold() == Grip.Hold.NONE;
			feet(model, dragon, state, local, footing, dt, turning);
		}
		ToePose.apply(model, dragon, brain, state, local, footing, partialTick, dt);
	}

	/**
	 * On a wall the head's crown is up in the world however it is aimed ({@link HeadUpright}): the head's
	 * last turn, after the strike's aim and the look.
	 */
	private static void upright(GeoModel<?> model, DragonBrain brain, BodyFrame frame, float partialTick) {
		double wall = brain.body.surface.wallness(partialTick);
		if (wall <= 0.0) return;
		GeoBone head = GeoBones.bone(model, "head_group");
		if (head == null || head.getParent() == null) return;
		double[] at = frame.toWorld(new double[3], new double[3]);
		double[] base = frame.toModel(at, new double[3]);
		double[] above = frame.toModel(new double[]{at[0], at[1] + 1.0, at[2]}, new double[3]);
		double[] up = {above[0] - base[0], above[1] - base[1], above[2] - base[2]};
		Joint joint = GeoBones.joint(head);
		double[] rot = HeadUpright.turn(GeoBones.matrix(head.getParent()), joint.rot, up, wall);
		System.arraycopy(rot, 0, joint.rot, 0, 3);
		GeoBones.setRotation(head, joint);
	}

	/** Where the model is drawn this frame: as the renderer places it ({@code DragonRenderer#applyRotations}). */
	static BodyFrame frame(EnderDragon dragon, float partialTick) {
		DragonBody body = DragonswornDragon.brain(dragon).body;
		return new BodyFrame().set(Mth.lerp(partialTick, dragon.xo, dragon.getX()),
				Mth.lerp(partialTick, dragon.yo, dragon.getY()), Mth.lerp(partialTick, dragon.zo, dragon.getZ()),
				body.yaw(partialTick), body.pitch(partialTick), body.roll(partialTick)).surface(body.surface.rotation(partialTick), body.lift(partialTick));
	}

	/**
	 * The model as drawn, in the frame of the surface it stands on ({@code nav/Surface}: on a wall its face's,
	 * where the wall is the ground; the world on the ground): what the feet stand in ({@link Footing}).
	 */
	static BodyFrame surfaceFrame(EnderDragon dragon, float partialTick) {
		DragonBody body = DragonswornDragon.brain(dragon).body;
		double[] at = Surface.applyInverse(body.surface.rotation(partialTick), new double[]{Mth.lerp(partialTick, dragon.xo, dragon.getX()),
				Mth.lerp(partialTick, dragon.yo, dragon.getY()), Mth.lerp(partialTick, dragon.zo, dragon.getZ())}, new double[3]);
		return new BodyFrame().set(at[0], at[1] + body.lift(partialTick), at[2], body.yaw(partialTick), body.pitch(partialTick), body.roll(partialTick));
	}

	/** What the feet stand on, in {@link #surfaceFrame}'s frame. */
	static Footing footing(EnderDragon dragon, float partialTick) {
		DragonBrain brain = DragonswornDragon.brain(dragon);
		return new Footing(dragon.level(), brain.face(), brain.body.surface.rotation(partialTick));
	}

	// ---------------------------------------------------------------- the feet

	private static void feet(GeoModel<?> model, EnderDragon dragon, State state, BodyFrame frame, Footing footing, double dt, boolean turning) {
		GeoBone bodyBone = GeoBones.bone(model, "body");
		if (bodyBone == null) return;
		double[] bodyM = GeoBones.matrix(bodyBone);
		// where the animation puts each foot: the hind ankles, the wrist claws
		GeoBone[][] legBones = new GeoBone[2][3];
		double[][] points = new double[4][];
		double[][] shins = new double[2][];
		for (int s = 0; s < 2; s++) {
			for (int k = 0; k < 3; k++) {
				legBones[s][k] = GeoBones.bone(model, LEGS[s][k]);
				if (legBones[s][k] == null) return;
			}
			Joint thigh = GeoBones.joint(legBones[s][0]), shin = GeoBones.joint(legBones[s][1]), foot = GeoBones.joint(legBones[s][2]);
			shins[s] = Affine.mul(Affine.mul(bodyM, thigh.local()), shin.local());
			points[s] = Affine.apply(shins[s], new double[]{foot.pivot[0] + foot.pos[0], foot.pivot[1] + foot.pos[1], foot.pivot[2] + foot.pos[2]}, new double[3]);
			GeoBone apex = GeoBones.bone(model, APEXES[s]);
			if (apex == null) return;
			points[2 + s] = Affine.apply(GeoBones.matrix(apex), new double[]{apex.getPivotX(), apex.getPivotY(), apex.getPivotZ()}, new double[3]);
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

		GroundClearance ground = new GroundClearance(footing, frame);
		for (int s = 0; s < 2; s++) {
			// hind leg: the ankle (the foot's pivot) is carried by the ground's difference under it
			GeoBone[] bones = legBones[s];
			Joint[] leg = new Joint[3];
			for (int k = 0; k < 3; k++) leg[k] = GeoBones.joint(bones[k]);
			double[] ankle = points[s];
			double held = held(ankle[1] - 3.0);
			double[] moved = stepped(frame, state, s, ankle, held);
			double[] target = shifted(footing, frame, state, s, moved, ankle[1] - 3.0, ground.of(bones[2], shins[s]), dt, !state.steps.stepping(s));
			target = lifted(state, s, moved, target, held);
			if (target != null && moved != ankle) {
				// carried round in a turn: where it goes may be higher ground than the animation's place,
				// so the foot is raised again by whatever of it is still inside the ground there
				Joint[] animatedLeg = {GeoBones.joint(bones[0]), GeoBones.joint(bones[1]), GeoBones.joint(bones[2])};
				double raised = 0.0;
				for (int pass = 0; pass < 3; pass++) {
					for (int k = 0; k < 3; k++) leg[k] = GeoBones.joint(bones[k]);
					for (int k = 0; k < 3; k++) System.arraycopy(animatedLeg[k].rot, 0, leg[k].rot, 0, 3);
					LimbIK.solveLegReach(bodyM, leg[0], leg[1], leg[2], target);
					// at the end of its reach across the ground: it must step, or the foot would be dragged (a
					// foot that cannot reach down or up does not: a step would land it no better, and again)
					double[] reached = Affine.apply(Affine.mul(Affine.mul(bodyM, leg[0].local()), leg[1].local()),
							new double[]{leg[2].pivot[0] + leg[2].pos[0], leg[2].pivot[1] + leg[2].pos[1], leg[2].pivot[2] + leg[2].pos[2]}, new double[3]);
					if (pass == 0 && across(frame, reached, target) > STRAIN && stepHelps(frame, root(bodyM, leg[0]), target, ankle) && !state.steps.stepping(s)) {
						state.steps.strain(s);
					}
					for (int k = 0; k < 3; k++) GeoBones.setRotation(bones[k], leg[k]);
					double inside = new GroundClearance(footing, frame).of(bones[2], Affine.mul(Affine.mul(bodyM, leg[0].local()), leg[1].local())) * held * state.footing;
					if (!(inside >= 0.01) || raised >= CLEAR_OVER) break; // NaN too: -Infinity (no ground near) times a lifted foot's 0
					inside = Math.min(inside, CLEAR_OVER - raised);
					raised += inside;
					target[1] += inside * 16.0;
				}
			} else if (target != null) {
				LimbIK.solveLeg(bodyM, leg[0], leg[1], leg[2], target);
				for (int k = 0; k < 3; k++) GeoBones.setRotation(bones[k], leg[k]);
			}

			// front limb: the wrist claw, carried the same way by turning the shoulder
			GeoBone shoulderBone = GeoBones.bone(model, SHOULDERS[s]), claw = GeoBones.bone(model, CLAWS[s]);
			if (shoulderBone == null || claw == null || claw.getCubes().size() <= CLAW_CUBE) return;
			double[] clawPoint = Affine.apply(GeoBones.matrix(claw), clawRest(claw.getCubes().get(CLAW_CUBE)), new double[3]);
			GeoBone hand = GeoBones.bone(model, HANDS[s]);
			double handClear = hand == null || hand.getParent() == null ? Double.NEGATIVE_INFINITY : ground.of(hand, GeoBones.matrix(hand.getParent()));
			// a turn holds the folded hand where it stands (by its apex), the claw going with it; only a
			// hand down on the ground (it lies flat in the stance: the gap under its lowest point, not
			// any one point's height, says whether it stands on it)
			double[] apex = points[2 + s];
			double apexHeld = Double.isInfinite(handClear) ? 0.0 : held(-handClear * 16.0);
			double[] apexMoved = stepped(frame, state, 2 + s, apex, apexHeld);
			boolean handMoved = apexMoved != apex;
			double[] clawMoved = handMoved ? new double[]{clawPoint[0] + apexMoved[0] - apex[0], clawPoint[1] + apexMoved[1] - apex[1], clawPoint[2] + apexMoved[2] - apex[2]} : clawPoint;
			target = shifted(footing, frame, state, 2 + s, clawMoved, clawPoint[1], handClear, dt, !state.steps.stepping(2 + s));
			target = lifted(state, 2 + s, clawMoved, target, apexHeld);
			GeoBone elbowBone = GeoBones.bone(model, ELBOWS[s]);
			if (target != null) {
				Joint animated = GeoBones.joint(shoulderBone);
				// the claw as the shoulder carries it: in the frame its turn moves
				double[] carried = Affine.apply(Affine.invertRigid(Affine.mul(bodyM, animated.local())), clawPoint, new double[3]);
				// the apex as the elbow carries it
				Joint animatedElbow = elbowBone == null ? null : GeoBones.joint(elbowBone);
				double[] atElbow = animatedElbow == null ? null
						: Affine.apply(Affine.invertRigid(Affine.mul(Affine.mul(bodyM, animated.local()), animatedElbow.local())), apex, new double[3]);
				// how far the ground (and the step's arc) raise the hand
				double rise = target[1] - clawMoved[1], raised = 0.0;
				// The wing turns about the shoulder, so the hand's inner parts rise less than the wrist:
				// raise the wrist again by whatever of the hand is still inside the ground.
				for (int pass = 0; pass < 3; pass++) {
					Joint shoulder = GeoBones.joint(shoulderBone);
					System.arraycopy(animated.rot, 0, shoulder.rot, 0, 3);
					if (handMoved && atElbow != null) {
						// a planted hand held where it stands: shoulder and elbow together
						Joint elbow = GeoBones.joint(elbowBone);
						double[] reach = {apexMoved[0], apexMoved[1] + rise, apexMoved[2]};
						LimbIK.solveArmReach(bodyM, shoulder, elbow, atElbow, reach);
						// at the end of its reach across the ground: the hand must step, or it would be dragged
						double[] reached = Affine.apply(Affine.mul(Affine.mul(bodyM, shoulder.local()), elbow.local()), atElbow, new double[3]);
						if (pass == 0 && across(frame, reached, reach) > STRAIN && stepHelps(frame, root(bodyM, shoulder), reach, apex) && !state.steps.stepping(2 + s)) {
							state.steps.strain(2 + s);
						}
						GeoBones.setRotation(elbowBone, elbow);
					} else {
						LimbIK.solveWing(bodyM, shoulder, carried, clawPoint[1] + rise);
					}
					GeoBones.setRotation(shoulderBone, shoulder);
					if (hand == null || hand.getParent() == null) break;
					double inside = new GroundClearance(footing, frame).of(hand, GeoBones.matrix(hand.getParent())) * held(clawPoint[1]) * state.footing;
					if (!(inside >= 0.01) || raised >= CLEAR_OVER) break; // NaN too: -Infinity (no ground near) times a lifted foot's 0
					inside = Math.min(inside, CLEAR_OVER - raised);
					raised += inside;
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
		BodyFrame frame = surfaceFrame(dragon, partialTick);
		int s = i % 2;
		if (i < 2) {
			GeoBone shin = GeoBones.bone(model, LEGS[s][1]), foot = GeoBones.bone(model, LEGS[s][2]);
			if (shin == null || foot == null) return null;
			Joint f = GeoBones.joint(foot);
			double[] p = {f.pivot[0] + f.pos[0], f.pivot[1] + f.pos[1], f.pivot[2] + f.pos[2]};
			return frame.toWorld(Affine.apply(GeoBones.matrix(shin), p, p), p);
		}
		GeoBone apex = GeoBones.bone(model, APEXES[s]);
		if (apex == null) return null;
		double[] p = Affine.apply(GeoBones.matrix(apex), new double[]{apex.getPivotX(), apex.getPivotY(), apex.getPivotZ()}, new double[3]);
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

	/** How far (pixels) the model point {@code reached} misses {@code target} across the ground, its height aside. */
	private static double across(BodyFrame frame, double[] reached, double[] target) {
		double[] a = frame.toWorld(reached, new double[3]), b = frame.toWorld(target, new double[3]);
		return Math.hypot(a[0] - b[0], a[2] - b[2]) * 16.0;
	}

	/** Where a limb's root joint (hip, shoulder) is in the model, its parent's matrix {@code parent}. */
	private static double[] root(double[] parent, Joint joint) {
		return Affine.apply(parent, new double[]{joint.pivot[0] + joint.pos[0], joint.pivot[1] + joint.pos[1], joint.pivot[2] + joint.pos[2]}, new double[3]);
	}

	/**
	 * Whether stepping would let a limb reach: {@code target} (model) is further from its {@code root} than
	 * the limb's own place ({@code place}, the animation's) at the target's height, by {@link #STEP_GAIN}. A limb that cannot
	 * reach down or up to the ground there either (a foot over a drop) does not step: it would land no
	 * better, and step again, and again.
	 */
	private static boolean stepHelps(BodyFrame frame, double[] root, double[] target, double[] place) {
		double[] r = frame.toWorld(root, new double[3]), t = frame.toWorld(target, new double[3]), p = frame.toWorld(place, new double[3]);
		double toTarget = Math.sqrt(sq(t[0] - r[0]) + sq(t[1] - r[1]) + sq(t[2] - r[2]));
		double toPlace = Math.sqrt(sq(p[0] - r[0]) + sq(t[1] - r[1]) + sq(p[2] - r[2]));
		return toTarget > toPlace + STEP_GAIN / 16.0;
	}

	private static double sq(double v) {
		return v * v;
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
	 * {@code sole}: the model height of the foot's bottom; {@code planted}: not in a turn's step. Null when
	 * there is nothing to do.
	 */
	private static double[] shifted(Footing footing, BodyFrame frame, State state, int i, double[] point, double sole, double clear, double dt, boolean planted) {
		double[] flat = frame.toWorld(new double[]{point[0], 0.0, point[2]}, new double[3]);
		double ground = footing.groundTop(flat[0], flat[1] + 2.0, flat[2]);
		double want = Double.isNaN(ground) ? 0.0 : ground - flat[1];
		clear = Math.min(clear, want + CLEAR_OVER);
		double ahead = ahead(footing, state, i, flat, dt);
		if (!Double.isNaN(ahead)) want = Math.max(want, ahead - flat[1]);
		want = stand(state.stand[i], flat, Math.max(want, clear), planted && held(sole) > 0.99);
		want = Math.max(-MAX_SHIFT, Math.min(MAX_SHIFT, want));
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

	/**
	 * The highest ground limb {@code i} at {@code flat} (its point on the model's ground, surface frame) will be
	 * over along its way in the next {@link #AHEAD} ticks; NaN when it stands still or there is none.
	 */
	private static double ahead(Footing footing, State state, int i, double[] flat, double dt) {
		double[] seen = state.seen[i], pace = state.pace[i];
		if (Double.isNaN(seen[0])) {
			pace[0] = pace[1] = 0.0;
		} else if (dt > 1e-6) {
			double k = Math.min(1.0, PACE * dt);
			pace[0] += ((flat[0] - seen[0]) / dt - pace[0]) * k;
			pace[1] += ((flat[2] - seen[1]) / dt - pace[1]) * k;
		}
		seen[0] = flat[0];
		seen[1] = flat[2];
		if (Math.hypot(pace[0], pace[1]) < 0.02) return Double.NaN;
		double best = Double.NaN;
		for (double t = 1.0; t <= AHEAD; t += 1.0) {
			double g = footing.groundTop(flat[0] + pace[0] * t, flat[1] + 2.0, flat[2] + pace[1] * t);
			if (!Double.isNaN(g) && (Double.isNaN(best) || g > best)) best = g;
		}
		return best;
	}

	/**
	 * How far up a limb at {@code flat} (its point on the model's ground, surface frame) stands, from
	 * {@code want} (the ground under it now): a planted limb that has not moved from where it came down
	 * ({@link #HOLD}) keeps standing where it stood when that ground drops away at once ({@link #JUMP}).
	 */
	private static double stand(double[] stand, double[] flat, double want, boolean planted) {
		double height = flat[1] + want;
		boolean near = !Double.isNaN(stand[2]) && Math.hypot(flat[0] - stand[0], flat[2] - stand[1]) < HOLD;
		if (planted && near) {
			if (height < stand[2] - JUMP) return stand[2] - flat[1];
		} else {
			stand[0] = flat[0];
			stand[1] = flat[2];
		}
		stand[2] = height;
		return want;
	}

	/** How much a foot whose bottom the animation holds {@code sole} pixels up is the ground's, 0..1. */
	private static double held(double sole) {
		return 1.0 - Maths.smoothstep((sole - RELEASE_FROM) / (RELEASE_TOP - RELEASE_FROM));
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
		return Affine.apply(GeoBones.cubeMatrix(cube), p, p);
	}

	// ---------------------------------------------------------------- the head

	/** Where the head and neck hitboxes hang on the model (pixels, as tools/parts.py anchors them): jaw_upper, neck_4, neck_2. */
	private static final String[] DRAWN_BONES = {"jaw_upper", "neck_4", "neck_2"};
	private static final double[][] DRAWN_ANCHORS = {{0, 55, -105}, {0, 54, -78}, {0, 52, -50}};
	private static final int[] DRAWN_PARTS = {Parts.HEAD, Parts.NECK_UPPER, Parts.NECK_LOWER};

	/** The head's look ({@link DragonBrain#look}: ticked with the hitboxes), between ticks. */
	private static void look(GeoModel<?> model, EnderDragon dragon, DragonBrain brain, State state, BodyFrame frame, float partialTick) {
		GeoBone head = GeoBones.bone(model, "head_group");
		if (head == null) return;
		HeadLook look = brain.look;
		for (int i = 0; i < NECK.length; i++) GeoBones.add(GeoBones.bone(model, NECK[i]), look.neckPitch(i, partialTick), look.neckYaw(i, partialTick));
		GeoBones.add(head, look.headPitch(partialTick), look.headYaw(partialTick));
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
			GeoBone bone = GeoBones.bone(model, DRAWN_BONES[k]);
			if (bone == null) return;
			double[] drawn = frame.toWorld(Affine.apply(GeoBones.matrix(bone), DRAWN_ANCHORS[k], new double[3]), new double[3]);
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
	public static double[] hitboxOffsets(EnderDragon dragon) {
		State state = STATES.get(dragon);
		return state == null || state.drawnFrames == 0 ? null : state.drawnOff;
	}

	/** The drawn tail's worst overlap with a block on its last solve (blocks): 0 when it is clear (the showcase checks it). */
	public static double tailOverlap(EnderDragon dragon) {
		return state(dragon).tail.overlap();
	}

	/** How far each tail segment swings against the head's turn, degrees. */
	static double tailYaw(EnderDragon dragon, float partialTick) {
		return DragonswornDragon.brain(dragon).look.tailYaw(TailChain.SEGMENTS, partialTick);
	}


}
