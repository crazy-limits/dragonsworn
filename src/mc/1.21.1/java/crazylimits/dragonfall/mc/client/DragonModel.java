package crazylimits.dragonfall.mc.client;

import crazylimits.dragonfall.Dragonfall;
import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.anim.DragonDebug;
import crazylimits.dragonfall.body.DragonBody;
import crazylimits.dragonfall.body.PoseTrack;
import crazylimits.dragonfall.body.Tail;
import crazylimits.dragonfall.body.TailMotion;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.LevelGrid;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import java.util.Arrays;

/**
 * assets/dragonfall/{geo,animations,textures}/entity/ender_dragon.*
 *
 * <p>On top of the keyframes it bends the neck through turns, the head first ({@link DragonBody#bends}),
 * holds the head nearer level than a banked body, and turns the wings against the body's pitch
 * ({@link DragonBody#wingCounter}). The server places the hitboxes with the same numbers. Then
 * {@link LimbAnimator} plants the feet on the ground and turns the head. Last the tail, keyed straight in
 * every animation: its whole pose is procedural ({@link TailMotion}: the animation's motion, laid on the
 * real ground, trailing turns, swinging against the head, whipped by a strike) and kept out of blocks
 * ({@link Tail}), hung from the body bone as drawn.
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

	public DragonModel() {
		super(ResourceLocation.fromNamespaceAndPath(Dragonfall.MOD_ID, "ender_dragon"));
	}

	@Override
	public void setCustomAnimations(ReplacedEnderDragon animatable, long instanceId, AnimationState<ReplacedEnderDragon> state) {
		if (!(state.getData(DataTickets.ENTITY) instanceof EnderDragon dragon)) return;
		float partialTick = state.getPartialTick();
		if (DragonDebug.forcedAnimation != null) {
			forcedTail(dragon, DragonDebug.forcedAnimation, partialTick);
			return;
		}
		DragonBody body = DragonfallDragon.brain(dragon).body;
		body.bends(partialTick, neckX, neckY, tailX, tailY);
		// a bite, tail strike or breath aimed at its target (Strike), on the animation's time as shown (after the blend)
		Arrays.fill(aimNeckX, 0.0);
		Arrays.fill(aimNeckY, 0.0);
		Arrays.fill(aimTailX, 0.0);
		Arrays.fill(aimTailY, 0.0);
		DragonBrain brain = DragonfallDragon.brain(dragon);
		double shown = Math.max(0.0, brain.clock.seconds() + (partialTick - DragonAnim.BLEND_TICKS) / 20.0);
		brain.strike.addBends(brain.clock.anim(), shown, body, partialTick, aimNeckX, aimNeckY, aimTailX, aimTailY);
		for (int i = 0; i < NECK.length; i++) add(NECK[i], neckX[i] + aimNeckX[i], neckY[i] + aimNeckY[i], 0.0);
		add("head_group", aimNeckX[NECK.length], aimNeckY[NECK.length], 0.0);
		add("head_group", 0.0, 0.0, body.headRoll(partialTick));
		GeoBone torso = getAnimationProcessor().getBone("body");
		if (torso != null) {
			double keyframed = Math.toDegrees(torso.getRotX() - torso.getInitialSnapshot().getRotX());
			double counter = body.wingCounter(partialTick, keyframed);
			add("left_wing", counter, 0.0, 0.0);
			add("right_wing", counter, 0.0, 0.0);
		}
		// the feet planted on the ground by IK, the head turned to what the dragon watches (LimbAnimator)
		LimbAnimator.apply(this, dragon, partialTick);
		// a roar in flight opens the jaw with the sound (-X opens it)
		add("jaw_group", -DragonfallDragon.brain(dragon).roar.jaw(dragon.tickCount + partialTick), 0.0, 0.0);

		// the tail: trailing the turn, the strike's whip, swinging against the head's turn
		double look = LimbAnimator.tailYaw(dragon);
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
		GeoBone torso = getAnimationProcessor().getBone("body");
		if (torso == null) return;
		DragonBody body = DragonfallDragon.brain(dragon).body;
		limbs.chain.body(LimbAnimator.matrix(torso));
		limbs.world.set(new LevelGrid(dragon.level()), body, partialTick, Mth.lerp(partialTick, dragon.xo, dragon.getX()),
				Mth.lerp(partialTick, dragon.yo, dragon.getY()), Mth.lerp(partialTick, dragon.zo, dragon.getZ()), dragon.tickCount + partialTick);
		limbs.tail.solve(limbs.chain, limbs.motion, bendX, bendY, limbs.world, finalTailX, finalTailY);
		for (int i = 0; i < TAIL.length; i++) add(TAIL[i], finalTailX[i], finalTailY[i], 0.0);
	}

	private void add(String name, double x, double y, double z) {
		GeoBone bone = getAnimationProcessor().getBone(name);
		if (bone == null) return;
		bone.setRotX(bone.getRotX() + (float) Math.toRadians(x));
		bone.setRotY(bone.getRotY() + (float) Math.toRadians(y));
		bone.setRotZ(bone.getRotZ() + (float) Math.toRadians(z));
	}
}
