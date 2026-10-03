package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.Dragonsworn;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.body.DragonBody;
import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.PoseTrack;
import crazylimits.dragonsworn.body.Tail;
import crazylimits.dragonsworn.body.TailMotion;
import crazylimits.dragonsworn.body.WingRoot;
import crazylimits.dragonsworn.debug.DragonDebug;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.LevelGrid;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import java.util.Arrays;

/**
 * assets/dragonsworn/{geo,animations,textures}/entity/ender_dragon.*
 *
 * <p>On top of the keyframes it bends the neck through turns, the head first ({@link DragonBody#bends}),
 * holds the head nearer level than a banked body, turns the wings against the body's pitch
 * ({@link DragonBody#wingCounter}) and into the turn ({@link DragonBody#wingTurn}: looks only, a few
 * degrees the wing hitboxes leave out). The server places the hitboxes with the same numbers. Then
 * {@link LimbAnimator} plants the feet on the ground and turns the head. Last the tail, keyed straight in
 * every animation: its whole pose is procedural ({@link TailMotion}: the animation's motion, laid on the
 * real ground, trailing turns, swinging against the head, whipped by a strike) and kept out of blocks
 * ({@link Tail}), hung from the body bone as drawn.
 *
 * <p>GeckoLib 5 poses the bones per render pass: the renderer hands {@link #pose} the pass's model once
 * the animation has set it.
 *
 * <p>Assets: the model, animations and textures derive from the "Ender Dragon Reborn" resource pack by
 * Parrie43, All Rights Reserved. The LGPL does not cover them: see LICENSE-ASSETS.md.
 */
public final class DragonModel extends DefaultedEntityGeoModel<ReplacedEnderDragon> {
	private static final String[] NECK = {"neck_1", "neck_2", "neck_3", "neck_4"};
	private static final String[] TAIL = {"tail_1", "tail_2", "tail_3", "tail_4", "tail_5", "tail_6", "tail_7", "tail_8", "tail_9"};

	private final double[] neckX = new double[NECK.length], neckY = new double[NECK.length];
	private final double[] tailX = new double[TAIL.length], tailY = new double[TAIL.length];
	/** A strike's bends on the neck and the head (the fifth joint), and on the tail. */
	private final double[] aimNeckX = new double[NECK.length + 1], aimNeckY = new double[NECK.length + 1];
	private final double[] aimTailX = new double[TAIL.length], aimTailY = new double[TAIL.length];
	private final double[] finalTailX = new double[TAIL.length], finalTailY = new double[TAIL.length];
	private final double[] wingTurn = new double[6];
	/** The model being posed (set for the length of {@link #pose}). */
	private GeoBones.Model model;

	public DragonModel() {
		super(Identifier.fromNamespaceAndPath(Dragonsworn.MOD_ID, "ender_dragon"));
	}

	/** Adds the procedural layer to {@code model}, posed by the animation for {@code dragon} this pass. */
	void pose(GeoBones.Model model, EnderDragon dragon, float partialTick) {
		this.model = model;
		if (DragonDebug.forcedAnimation != null) {
			forcedTail(dragon, DragonDebug.forcedAnimation, partialTick);
			rootWebs();
			return;
		}
		DragonBody body = DragonswornDragon.brain(dragon).body;
		body.bends(partialTick, neckX, neckY, tailX, tailY);
		// a bite, tail strike or breath aimed at its target (Strike), on the animation's time as shown (after the blend)
		Arrays.fill(aimNeckX, 0.0);
		Arrays.fill(aimNeckY, 0.0);
		Arrays.fill(aimTailX, 0.0);
		Arrays.fill(aimTailY, 0.0);
		DragonBrain brain = DragonswornDragon.brain(dragon);
		double shown = Math.max(0.0, brain.clock.seconds() + (partialTick - DragonAnim.BLEND_TICKS) / 20.0);
		brain.strike.addBends(brain.clock.anim(), shown, body, partialTick, aimNeckX, aimNeckY, aimTailX, aimTailY);
		for (int i = 0; i < NECK.length; i++) add(NECK[i], neckX[i] + aimNeckX[i], neckY[i] + aimNeckY[i], 0.0);
		add("head_group", aimNeckX[NECK.length], aimNeckY[NECK.length], 0.0);
		add("head_group", 0.0, 0.0, body.headRoll(partialTick));
		GeoBones.Bone torso = model.bone("body");
		if (torso != null) {
			double keyframed = Math.toDegrees(torso.getRotX() - torso.getInitialSnapshot().getRotX());
			double counter = body.wingCounter(partialTick, keyframed);
			add("left_wing", counter, 0.0, 0.0);
			add("right_wing", counter, 0.0, 0.0);
		}
		// the wings' share of a turn: inside one swept back, outside one forward, twisted against the roll
		body.wingTurn(partialTick, wingTurn);
		add("left_wing", wingTurn[2], wingTurn[0], 0.0);
		add("left_wing_tip", 0.0, 0.0, -wingTurn[1]);
		add("right_wing", wingTurn[5], -wingTurn[3], 0.0);
		add("right_wing_tip", 0.0, 0.0, wingTurn[4]);
		// the feet planted on the ground by IK, the head turned to what the dragon watches (LimbAnimator)
		LimbAnimator.apply(model, dragon, partialTick);
		rootWebs();
		// a roar in flight opens the jaw with the sound (-X opens it)
		add("jaw_group", -DragonswornDragon.brain(dragon).roar.jaw(dragon.tickCount + partialTick), 0.0, 0.0);
		// prey in the jaws holds them a little open
		if (brain.prey.hold() == Grip.Hold.JAW) add("jaw_group", -Grip.JAW_OPEN, 0.0, 0.0);

		// the tail: trailing the turn, the strike's whip, swinging against the head's turn
		double look = LimbAnimator.tailYaw(dragon, partialTick);
		for (int i = 0; i < TAIL.length; i++) {
			tailX[i] += aimTailX[i];
			tailY[i] += aimTailY[i] + look;
		}
		LimbAnimator.State limbs = LimbAnimator.state(dragon);
		TailMotion.sample(brain.clock.anim(), shown, brain.clock.from(), brain.clock.fromSeconds(), brain.clock.blend(partialTick), limbs.motion);
		tail(dragon, limbs, partialTick, tailX, tailY);
	}

	/**
	 * The tail of a debug-forced animation (looped from when it was forced, as {@code ReplacedEnderDragon}
	 * plays it), on the ground and out of the blocks like any other.
	 */
	private void forcedTail(EnderDragon dragon, DragonAnim anim, float partialTick) {
		LimbAnimator.State limbs = LimbAnimator.state(dragon);
		double now = dragon.tickCount + partialTick;
		if (limbs.forced != anim) {
			limbs.forced = anim;
			limbs.forcedSince = now;
		}
		double seconds = Math.max(0.0, (now - limbs.forcedSince - DragonAnim.BLEND_TICKS) / 20.0);
		seconds %= PoseTrack.length(anim);
		TailMotion.sample(anim, seconds, limbs.motion);
		tail(dragon, limbs, partialTick, null, null);
	}

	/** Solves the tail ({@link Tail}) on the body bone as drawn and turns the tail bones by it. */
	private void tail(EnderDragon dragon, LimbAnimator.State limbs, float partialTick, double[] bendX, double[] bendY) {
		GeoBones.Bone torso = model.bone("body");
		if (torso == null) return;
		DragonBody body = DragonswornDragon.brain(dragon).body;
		limbs.chain.body(GeoBones.matrix(torso));
		limbs.world.set(new LevelGrid(dragon.level()), body, partialTick, Mth.lerp(partialTick, dragon.xo, dragon.getX()),
				Mth.lerp(partialTick, dragon.yo, dragon.getY()), Mth.lerp(partialTick, dragon.zo, dragon.getZ()), dragon.tickCount + partialTick);
		limbs.tail.solve(limbs.chain, limbs.motion, bendX, bendY, limbs.world, finalTailX, finalTailY);
		for (int i = 0; i < TAIL.length; i++) add(TAIL[i], finalTailX[i], finalTailY[i], 0.0);
	}

	/** The wings' root webs keep pointing at the body, whatever the shoulders do as drawn (WingRoot). */
	private void rootWebs() {
		GeoBones.Bone left = model.bone("left_wing"), right = model.bone("right_wing");
		if (left != null)
			add("left_wing_root_web", 0.0, 0.0, WingRoot.fold(Math.toDegrees(left.getRotX()), Math.toDegrees(left.getRotY()), Math.toDegrees(left.getRotZ())));
		if (right != null)
			add("right_wing_root_web", 0.0, 0.0, -WingRoot.fold(Math.toDegrees(right.getRotX()), -Math.toDegrees(right.getRotY()), -Math.toDegrees(right.getRotZ())));
	}

	private void add(String name, double x, double y, double z) {
		GeoBones.Bone bone = model.bone(name);
		if (bone == null) return;
		bone.setRotX(bone.getRotX() + (float) Math.toRadians(x));
		bone.setRotY(bone.getRotY() + (float) Math.toRadians(y));
		bone.setRotZ(bone.getRotZ() + (float) Math.toRadians(z));
	}
}
