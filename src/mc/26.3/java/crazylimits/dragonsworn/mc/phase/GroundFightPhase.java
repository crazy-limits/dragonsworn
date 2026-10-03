package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.ai.CombatStance;
import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.ai.GroundTactics;
import crazylimits.dragonsworn.ai.Roaming;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.PoseTrack;
import crazylimits.dragonsworn.body.Strike;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.Targets;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.GroundPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * On the ground. With a target it fights ({@link GroundTactics}): turns to face it head first, walks
 * in along a path around obstacles ({@link GroundPlanner}), bites what is in front, strikes with its tail
 * at what is beside it (or behind it, when hurt from there), turns round to what it cannot reach, and
 * roars. One blow at a time.
 *
 * <p>A blow is aimed ({@link Strike}): the jaws or the tail's tip go exactly to where the target was a
 * moment before the blow (the server's {@code dodge_window}), and only what is there when it lands is hit. Stepping
 * out of the way is a dodge. Hit too often at once (from its blind spots), it takes to the air
 * ({@link crazylimits.dragonsworn.ai.HitTally}). A wild dragon is lazy: it fights on foot for as long as it
 * takes, and takes a break in the air only when hurt too much ({@link CombatStance}).
 *
 * <p>Without one it roams on foot ({@link Roaming}): walks a stretch, stops and looks
 * around, walks on, and wakes for any player who comes close. After a while (a long one, for a wild
 * dragon), or when the target gets away, it takes off.
 *
 * <p>The seize (Skyrim's dragons): now and then the bite does not let go. It is a bite first: only one
 * that lands (its jaws close on the target, {@link #blow}) takes the prey; dodged, the jaws close on
 * nothing and nothing is held. The prey hangs from the jaws
 * ({@link Grip.Hold#JAW}), unable to move but free to use items (an ender pearl gets it out), and is
 * shaken and chewed (the server's {@code chew_damage} every {@code chew_interval}) until it is flung away. Anyone
 * else hitting the head or neck makes the dragon drop it ({@code DragonBrain#hurtBy}).
 *
 * <p>On a narrow foothold ({@link Foothold#UPRIGHT}: sat up on its hind feet, the wings out for balance;
 * {@link Foothold#CLING}: gripping a pillar's top, the wings beating) it fights with its head alone: it
 * turns to face its prey and bites (and seizes) what is in reach, but never walks, lashes its tail or
 * roars. Once its prey has been out of the jaws' reach for {@code narrow_patience} it takes off (and
 * looks for somewhere better); clinging tires it out after {@code cling_max}, and with nobody to fight
 * it does not stay up there long.
 *
 * <p>Arrows hit it here (unlike the vanilla perch): ranged players earn these windows too. The head
 * and neck no longer hurt by touch; the bite does.
 */
public class GroundFightPhase extends AbstractDragonPhaseInstance implements DragonswornPhase {
	/** Only targets this close are worth asking the IK whether a blow reaches. */
	static final double STRIKE_RANGE = 14.0;
	/** A narrow foothold: how long it stays up there with nobody to fight (ticks). */
	static final int NARROW_REST = 100;
	/*
	 * The rest is the server's (DragonConfig, [ground_combat]): the dodge window (REACTION_TICKS), the
	 * recoveries after a bite and a tail strike, how long a hit provokes (PROVOKED_TICKS), the damages, the
	 * seize (its chance, cooldown, hold and chewing: SEIZE_CHANCE, CHEW_INTERVAL), and on a narrow foothold
	 * NARROW_PATIENCE (prey out of the jaws' reach before it takes off) and CLING_MAX.
	 */

	@Nullable
	private LivingEntity target;
	/** How it stands: on all fours, or on a narrow foothold (no walking, no tail). */
	private Foothold foothold = Foothold.STAND;
	private int ticks, duration, lostTicks, pauseTicks, wanderTicks, unreachedTicks;
	private double walkHeading;
	@Nullable
	private DragonAnim action;
	private int actionTicks;
	private boolean actionHit;
	/** The bite playing is a seize; the next bite will be one; how long the jaws have held, and for how long they will. */
	private boolean seizing, seizeNext;
	private int holdTicks, holdFor, seizeReadyAt;
	private int attackReadyAt, roarReadyAt;
	/** Where the blow playing is aimed (world). */
	@Nullable
	private Vec3 aim;
	/** Asks whether a blow would reach, without touching the dragon's own aim. */
	private final Strike probe = new Strike();
	/** Its steps on the ground: turning, and walking a path round what is in the way. */
	private final GroundWalker walker;
	@Nullable
	private int[] wander;
	private double lastX, lastZ;

	public GroundFightPhase(EnderDragon dragon) {
		super(dragon);
		walker = new GroundWalker(dragon);
	}

	/** Lands (the dragon is on the ground already) and fights {@code target}, or rests when null. */
	public static void start(EnderDragon dragon, @Nullable LivingEntity target) {
		start(dragon, target, true);
	}

	/**
	 * As {@link #start(EnderDragon, LivingEntity)}; {@code thud}: it has just come down (hovering), all
	 * four feet at once. A running landing has struck the ground already and skidded out.
	 */
	public static void start(EnderDragon dragon, @Nullable LivingEntity target, boolean thud) {
		start(dragon, target, thud, Foothold.STAND);
	}

	/** As above, standing with {@code foothold}. */
	public static void start(EnderDragon dragon, @Nullable LivingEntity target, boolean thud, Foothold foothold) {
		dragon.getPhaseManager().setPhase(DragonPhases.GROUND_FIGHT);
		GroundFightPhase phase = dragon.getPhaseManager().getPhase(DragonPhases.GROUND_FIGHT);
		phase.foothold = foothold;
		DragonswornDragon.brain(dragon).setFoothold(foothold);
		if (foothold == Foothold.CLING) phase.duration = Math.min(phase.duration, DragonConfig.CLING_MAX.get());
		if (thud) phase.landingThud();
		phase.engage(target);
	}

	/** How it stands. */
	public Foothold foothold() {
		return foothold;
	}

	@Override
	public EnderDragonPhase<GroundFightPhase> getPhase() {
		return DragonPhases.GROUND_FIGHT;
	}

	@Override
	public boolean isSitting() {
		return true;
	}

	@Override
	public void begin() {
		target = null;
		foothold = Foothold.STAND;
		ticks = lostTicks = wanderTicks = unreachedTicks = 0;
		// a wild dragon stays down a good while
		duration = brain().context() == DragonBrain.Context.WILD ? Roaming.groundSpell(ThreadLocalRandom.current())
				: DragonConfig.between(DragonConfig.ARENA_STAY_MIN, DragonConfig.ARENA_STAY_MAX, ThreadLocalRandom.current());
		pauseTicks = Roaming.pause(ThreadLocalRandom.current());
		walkHeading = Roaming.headingOfYaw(dragon.getYRot());
		action = null;
		walker.forgetPath();
		wander = null;
		attackReadyAt = 20;
		seizing = seizeNext = false;
		holdTicks = 0;
		seizeReadyAt = 100;
		roarReadyAt = 0;
		aim = null;
		brain().hits.clear();
		lastX = dragon.getX();
		lastZ = dragon.getZ();
		dragon.setDeltaMovement(Vec3.ZERO);
	}

	private void engage(@Nullable LivingEntity target) {
		this.target = target;
		// sat up there is no roaring: the roar rears up on all fours
		if (target != null && !foothold.narrow()) startAction(DragonAnim.ROAR);
	}

	private DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	@Override
	public void end() {
		if (brain().prey.hold() == Grip.Hold.JAW) brain().prey.release(null);
		if (action != null) brain().clearAction();
		action = null;
		aim = null;
		brain().setLookTarget(null);
	}

	@Override
	public void doServerTick(ServerLevel serverLevel) {
		ticks++;
		lastX = dragon.getX();
		lastZ = dragon.getZ();
		followGround();
		// the head follows its prey, or resting, whoever comes near
		brain().setLookTarget(target != null ? target : dragon.level().getNearestPlayer(dragon, 32.0));
		// worn down from where it cannot answer: up and away
		if (DragonConfig.OVERWHELM_TAKEOFF.get() && brain().hits.overwhelmed(dragon.tickCount)) {
			brain().hits.clear();
			brain().stance.overwhelmed(ThreadLocalRandom.current());
			takeOff();
			return;
		}
		if (action != null) {
			runAction();
			return;
		}
		if (brain().prey.hold() == Grip.Hold.JAW || holdTicks > 0) {
			hold();
			return;
		}
		boolean wild = brain().context() == DragonBrain.Context.WILD;
		// hurt too much on the ground: a break in the air (it attacks from there)
		if (wild && target != null && !brain().stance.grounded()) {
			takeOff();
			return;
		}
		// a wild dragon fights on as long as it has someone to fight (clinging tires it out all the same)
		if (ticks > duration && !(wild && target != null && foothold != Foothold.CLING)) {
			takeOff();
			return;
		}
		if (target != null && (!target.isAlive() || Targets.untouchable(target))) target = null;
		// whoever is hurting it now (from a blind spot, while it watched another) becomes the target
		int provoked = DragonConfig.PROVOKED_TICKS.get();
		LivingEntity attacker = brain().combat.recentAttacker(provoked / 2);
		if (attacker != null && attacker != target && attacker.distanceToSqr(dragon) < 24.0 * 24.0
				&& !(target != null && brain().combat.hurtRecentlyBy(target, provoked))) target = attacker;
		if (target == null) {
			rest();
			return;
		}
		double dx = target.getX() - dragon.getX(), dz = target.getZ() - dragon.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		lostTicks = distance > DragonConfig.LOSE_DISTANCE.get() || Math.abs(target.getY() - dragon.getY()) > 10 ? lostTicks + 1 : 0;
		if (lostTicks > DragonConfig.LOSE_TICKS.get()) {
			takeOff();
			return;
		}
		float bearing = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dx, -dz)) - dragon.getYRot());
		boolean ready = ticks >= attackReadyAt, near = ready && distance < STRIKE_RANGE;
		Vec3 at = strikePoint(target);
		boolean narrow = foothold.narrow();
		// on a narrow foothold it cannot walk in: out of its jaws' reach too long, it leaves
		boolean biteReaches = (near || narrow) && distance < STRIKE_RANGE && reaches(bite(), at);
		unreachedTicks = narrow && !biteReaches ? unreachedTicks + 1 : 0;
		if (unreachedTicks > DragonConfig.NARROW_PATIENCE.get()) {
			takeOff();
			return;
		}
		// what the server took away from it never reaches (the bite still decides when to stop walking in)
		boolean bite = DragonConfig.BITE.get();
		GroundTactics.Decision decision = GroundTactics.decide(foothold, distance, bearing,
				near && biteReaches && bite, near && !narrow && DragonConfig.TAIL_STRIKE.get() && reaches(DragonAnim.TAIL_SWEEP, at), ready,
				DragonConfig.ROAR.get() && ticks >= roarReadyAt, brain().combat.hurtRecentlyBy(target, provoked), ThreadLocalRandom.current().nextDouble());
		switch (decision.action()) {
			case BITE -> {
				startAction(bite());
				seizing = PreyHold.holdable(target) && (seizeNext || DragonConfig.SEIZE.get() && ticks >= seizeReadyAt
						&& ThreadLocalRandom.current().nextDouble() < DragonConfig.SEIZE_CHANCE.get());
				seizeNext = false;
			}
			case TAIL_STRIKE -> startAction(DragonAnim.TAIL_SWEEP);
			case ROAR -> startAction(DragonAnim.ROAR);
			case NONE -> {
				// on a way round something it keeps walking, even where that leads away from the target
				if (decision.walk() || walker.detouring()) walker.walkToward(target.getX(), target.getZ(), GroundTactics.CLOSE_IN, ticks);
				else if (decision.turn()) walker.turnToward(target.getX(), target.getZ());
			}
		}
	}

	// ---------------------------------------------------------------- resting

	private void rest() {
		double wake = DragonConfig.WAKE_RANGE.get();
		LivingEntity near = wake <= 0.0 ? null : ((ServerLevel) dragon.level()).getNearestPlayer(TargetingConditions.forCombat().range(wake), dragon);
		if (near != null) {
			engage(near);
			duration = Math.max(duration, ticks + 300);
			return;
		}
		// nowhere to walk to up there, and nothing to do: off again soon
		if (foothold.narrow()) {
			if (++wanderTicks > NARROW_REST) takeOff();
			return;
		}
		if (wander != null) {
			// arrived, no way there, or taking too long: stop and look around a while
			if (++wanderTicks < 400 && walker.walkToward(wander[0] + 0.5, wander[2] + 0.5, 1.5, ticks)) return;
			wander = null;
			pauseTicks = Roaming.pause(ThreadLocalRandom.current());
			return;
		}
		if (--pauseTicks > 0) return;
		wander = nextWalk();
		wanderTicks = 0;
		if (wander == null) pauseTicks = 40;
	}

	/** Somewhere to walk to, on from the last walk's heading; null when nothing nearby will do. */
	@Nullable
	private int[] nextWalk() {
		GroundPlanner planner = new GroundPlanner(brain().grid());
		RandomGenerator random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < 6; attempt++) {
			double h = Roaming.nextHeading(walkHeading, attempt, random);
			double leg = Roaming.walkLeg(random);
			int x = Mth.floor(dragon.getX() + Math.cos(h) * leg), z = Mth.floor(dragon.getZ() + Math.sin(h) * leg);
			int y = planner.stand(x, z);
			if (y == BlockGrid.NO_GROUND || Math.abs(y - dragon.getY()) > 6 || !brain().ticking(x, z)) continue;
			walkHeading = h;
			return new int[]{x, y, z};
		}
		return null;
	}

	// ---------------------------------------------------------------- moving on the ground

	/**
	 * Keeps the feet on the terrain under the body: the highest ground under the hips and wrists. A
	 * wall the wrists are against is not ground to climb: only ground within a step of its feet counts
	 * there (under its middle, whatever is there).
	 */
	private void followGround() {
		BlockGrid grid = brain().grid();
		Vec3 facing = Targets.facing(dragon.getYRot());
		double fx = facing.x, fz = facing.z;
		int best = grid.ground(Mth.floor(dragon.getX()), Mth.floor(dragon.getZ()));
		// sat up, it stands on its hind feet alone, right under it
		double[][] feet = foothold.narrow() ? new double[0][] : new double[][]{{fx * 3.5, fz * 3.5}, {-fx * 1.5, -fz * 1.5}};
		for (double[] at : feet) {
			int g = grid.ground(Mth.floor(dragon.getX() + at[0]), Mth.floor(dragon.getZ() + at[1]));
			if (g != BlockGrid.NO_GROUND && g - dragon.getY() <= GroundPlanner.STEP_UP + 0.5) best = Math.max(best, g);
		}
		if (best == BlockGrid.NO_GROUND) {
			takeOff();       // the ground is gone (water, void): fly
			return;
		}
		double dy = Mth.clamp(best - dragon.getY(), -0.35, 0.25);
		if (Math.abs(dy) > 1e-3) dragon.setPos(dragon.getX(), dragon.getY() + dy, dragon.getZ());
		dragon.setDeltaMovement(dragon.getX() - lastX, 0.0, dragon.getZ() - lastZ);
	}

	/** Feet on the ground: their thud and dust (a hovering landing's, a running landing's strike). */
	public void landingThud() {
		thud(dragon);
	}

	public static void thud(EnderDragon dragon) {
		if (!(dragon.level() instanceof ServerLevel level)) return;
		// all four feet at once, and the wings braking: the walk's own steps follow from its animation
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), DragonSounds.STEP, SoundSource.HOSTILE, 1.0F, 0.7F);
		level.playSound(null, dragon.getX(), dragon.getY() + 3.0, dragon.getZ(), DragonSounds.WING, SoundSource.HOSTILE, 1.0F, 0.8F);
		BlockPos below = BlockPos.containing(dragon.getX(), dragon.getY() - 0.5, dragon.getZ());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
				dragon.getX(), dragon.getY() + 0.2, dragon.getZ(), 80, 3.0, 0.2, 3.0, 0.15);
	}

	// ---------------------------------------------------------------- actions

	/** The bite of its foothold: on all fours, sat up or clinging. */
	private DragonAnim bite() {
		return switch (foothold) {
			case STAND -> DragonAnim.ATTACK;
			case UPRIGHT -> DragonAnim.UPRIGHT_BITE;
			case CLING -> DragonAnim.CLING_BITE;
		};
	}

	private void startAction(DragonAnim anim) {
		action = anim;
		actionTicks = 0;
		actionHit = false;
		brain().startAction(anim);
		if (Strike.strikes(anim) && target != null) {
			aim = strikePoint(target);
			brain().aimStrike(aim);
		}
		if (anim == DragonAnim.ROAR) {
			roarReadyAt = ticks + DragonConfig.between(DragonConfig.ROAR_COOLDOWN_MIN, DragonConfig.ROAR_COOLDOWN_MAX, ThreadLocalRandom.current());
		}
	}

	/** Where a blow at {@code target} goes: the middle of its body. */
	private static Vec3 strikePoint(LivingEntity target) {
		return target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
	}

	/** Whether {@code anim}'s blow would put the jaws (or the tail's tip) on {@code at} from where the dragon stands. */
	private boolean reaches(DragonAnim anim, Vec3 at) {
		probe.aim(anim, at.x - dragon.getX(), at.y - dragon.getY(), at.z - dragon.getZ());
		return probe.solve(brain().body, 1.0F) < Strike.radius(anim) * 0.5;
	}

	/** When {@code anim}'s blow lands, seconds into the action: when the model shows it (the animation starts after the blend). */
	private static double hitSeconds(DragonAnim anim) {
		return switch (anim) {
			case ATTACK, UPRIGHT_BITE, CLING_BITE, TAIL_SWEEP -> Strike.hitSeconds(anim) + DragonAnim.BLEND_TICKS / 20.0;
			case ROAR -> DragonAnim.ROAR_SECONDS;
			default -> Double.MAX_VALUE;
		};
	}

	private void runAction() {
		actionTicks++;
		double seconds = actionTicks / 20.0;
		boolean strike = Strike.strikes(action);
		double hitAt = hitSeconds(action);
		// the aim follows the target until a moment before the blow; then it is committed
		if (strike && target != null && !actionHit && actionTicks < hitAt * 20.0 - DragonConfig.REACTION_TICKS.get()) {
			aim = strikePoint(target);
			brain().aimStrike(aim);
			// a bite turns the body after it while the neck coils
			if (action.bites()) walker.turnToward(target.getX(), target.getZ());
		}
		if (!actionHit && seconds >= hitAt) {
			actionHit = true;
			switch (action) {
				case ATTACK, UPRIGHT_BITE, CLING_BITE, TAIL_SWEEP -> blow(action);
				case ROAR -> roar();
				default -> {}
			}
		}
		if (seconds >= PoseTrack.length(action) + (strike ? DragonAnim.BLEND_TICKS / 20.0 : 0.0)) {
			int recovery = action.bites() ? DragonConfig.BITE_RECOVERY.get() : action == DragonAnim.TAIL_SWEEP ? DragonConfig.TAIL_RECOVERY.get() : 0;
			attackReadyAt = Math.max(attackReadyAt, ticks + recovery);
			action = null;
			aim = null;
			brain().clearAction();
		}
	}

	/**
	 * The blow lands: the jaws (or the tail's tip) are where the aim put them; whatever is there now is
	 * hit. A target that stepped away, or one the blow could not reach, is missed.
	 */
	private void blow(DragonAnim anim) {
		if (aim == null) return;
		Vec3 point = JawBlow.landing(dragon, probe, anim, aim);
		boolean bite = anim.bites();
		boolean seize = bite && seizing;
		seizing = false;
		if (bite) dragon.playSound(DragonSounds.BITE, 3.0F, 0.6F);
		else dragon.playSound(DragonSounds.TAIL, 4.0F, 0.5F);
		double radius = Strike.hitRadius(anim);
		DamageSource source = dragon.damageSources().mobAttack(dragon);
		for (LivingEntity living : JawBlow.struck(dragon, point, radius)) {
			// the seize: the jaws close on the target and keep it
			if (seize && living == target && living.hurtServer(((ServerLevel) dragon.level()), source, DragonConfig.SEIZE_DAMAGE.f()) && living.isAlive() && seize(living)) {
				seize = false;
				continue;
			}
			if (!living.hurtServer(((ServerLevel) dragon.level()), source, bite ? DragonConfig.BITE_DAMAGE.f() : DragonConfig.TAIL_DAMAGE.f()) || bite) continue;
			// the tail flings what it hits away from the dragon
			Vec3 push = living.position().subtract(dragon.position()).multiply(1, 0, 1).normalize().scale(1.8);
			living.push(push.x, 0.45, push.z);
			living.needsSync = true;
		}
	}

	/** Makes its next bite a seize (if the target can be held): it still has to land to take the prey. */
	public void seizeNext() {
		seizeNext = true;
	}

	/** A seizing bite is playing and its aim is committed: from now on only what is at the jaws when they close is taken. */
	public boolean seizeCommitted() {
		return seizing && action != null && action.bites() && !actionHit && actionTicks >= hitSeconds(action) * 20.0 - DragonConfig.REACTION_TICKS.get();
	}

	/** Takes {@code prey} in the jaws (a seizing bite closed on it) and fights it from there (the seize's hold). */
	private boolean seize(LivingEntity prey) {
		if (!brain().prey.seize(prey, Grip.Hold.JAW)) return false;
		target = prey;
		holdTicks = 0;
		holdFor = DragonConfig.between(DragonConfig.HOLD_MIN, DragonConfig.HOLD_MAX, ThreadLocalRandom.current());
		return true;
	}

	/**
	 * Prey in the jaws: shaken (the body's shake runs while the hold is synced) and chewed, then flung
	 * off to one side. When it gets away (or someone hitting the head made the dragon drop it) the fight
	 * goes on, the head free again.
	 */
	private void hold() {
		DragonBrain brain = brain();
		brain.setLookTarget(null);
		if (!brain.prey.holding()) {
			holdTicks = 0;
			attackReadyAt = Math.max(attackReadyAt, ticks + DragonConfig.BITE_RECOVERY.get());
			seizeReadyAt = ticks + DragonConfig.SEIZE_COOLDOWN.get();
			return;
		}
		holdTicks++;
		Entity prey = brain.prey.prey();
		if (holdTicks == 3 && prey instanceof ServerPlayer player) player.sendOverlayMessage(Component.translatable("dragonsworn.seized"));
		if (holdTicks % DragonConfig.CHEW_INTERVAL.get() == 0 && prey instanceof LivingEntity living) {
			living.hurt(dragon.damageSources().mobAttack(dragon), DragonConfig.CHEW_DAMAGE.f());
			dragon.playSound(DragonSounds.CHEW, 1.5F, 0.8F);
		}
		if (holdTicks >= holdFor) {
			// flung off to one side, out and up
			float yaw = (dragon.getYRot() + (dragon.getRandom().nextBoolean() ? 70.0F : -70.0F)) * Mth.DEG_TO_RAD;
			brain.prey.release(new Vec3(Mth.sin(yaw) * 1.3, 0.55, -Mth.cos(yaw) * 1.3));
			dragon.playSound(DragonSounds.FLING, 3.0F, 0.5F);
		}
	}

	/** Terrify: everything close is slowed and pushed back. The growl is the client's (DragonVoiceMixin), in step with the jaw. */
	private void roar() {
		for (Entity e : dragon.level().getEntities(dragon, dragon.getBoundingBox().inflate(16.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
			if (!(e instanceof LivingEntity living)) continue;
			Vec3 to = e.position().subtract(dragon.position()).multiply(1, 0, 1);
			double distance = to.length();
			if (distance > 16.0) continue;
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), dragon);
			Vec3 push = to.normalize().scale(1.2 * (1.0 - distance / 16.0));
			living.push(push.x, 0.2, push.z);
			living.needsSync = true;
		}
	}

	private void takeOff() {
		dragon.getPhaseManager().setPhase(DragonPhases.LIFTOFF);
	}

	@Override
	public boolean collides() {
		return false;
	}

	@Override
	public float getFlySpeed() {
		return 0.0F;
	}

	@Nullable
	@Override
	public Vec3 getFlyTargetLocation() {
		return null;
	}
}
