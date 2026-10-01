package crazylimits.dragonfall.mc.phase;

import crazylimits.dragonfall.ai.GroundTactics;
import crazylimits.dragonfall.ai.Roaming;
import crazylimits.dragonfall.anim.DragonAnim;
import crazylimits.dragonfall.body.PoseTrack;
import crazylimits.dragonfall.body.Strike;
import crazylimits.dragonfall.mc.DragonBrain;
import crazylimits.dragonfall.mc.DragonPhases;
import crazylimits.dragonfall.mc.DragonSounds;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.nav.BlockGrid;
import crazylimits.dragonfall.nav.GroundPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * On the ground. With a target it fights ({@link GroundTactics}): turns to face it head first, walks
 * in along a path around obstacles ({@link GroundPlanner}), bites what is in front, strikes with its tail
 * at what is beside it (or behind it, when hurt from there), turns round to what it cannot reach, and
 * roars. One blow at a time.
 *
 * <p>A blow is aimed ({@link Strike}): the jaws or the tail's tip go exactly to where the target was a
 * moment before the blow ({@link #REACTION_TICKS}), and only what is there when it lands is hit. Stepping
 * out of the way is a dodge. Hit too often at once (from its blind spots), it takes to the air
 * ({@link crazylimits.dragonfall.ai.HitTally}).
 *
 * <p>Without one it roams on foot ({@link Roaming}): walks a stretch, stops and looks
 * around, walks on, and wakes for any player who comes close. After a while (or when the target gets
 * away) it takes off.
 *
 * <p>Arrows hit it here (unlike the vanilla perch): ranged players earn these windows too. The head
 * and neck no longer hurt by touch; the bite does.
 */
public class GroundFightPhase extends AbstractDragonPhaseInstance implements DragonfallPhase {
	/** Walking speed, blocks per tick (the walk animation speeds up to match). */
	static final double WALK_SPEED = 0.11;
	/** Degrees per tick it can turn. */
	static final float TURN_SPEED = 4.0F;
	private static final TargetingConditions WAKE = TargetingConditions.forCombat().range(24.0);
	/** The aim stops following the target this long before the blow lands: the window to dodge. */
	static final int REACTION_TICKS = 7;
	/** Recovery after a blow before the next one, ticks. */
	static final int BITE_RECOVERY = 12, TAIL_RECOVERY = 20;
	/** A hit from the target this recent makes a target behind worth the tail. */
	static final int PROVOKED_TICKS = 40;
	/** Only targets this close are worth asking the IK whether a blow reaches. */
	static final double STRIKE_RANGE = 14.0;
	static final float BITE_DAMAGE = 12.0F, TAIL_DAMAGE = 9.0F;

	@Nullable
	private LivingEntity target;
	private int ticks, duration, lostTicks, pauseTicks, wanderTicks;
	private double walkHeading;
	@Nullable
	private DragonAnim action;
	private int actionTicks;
	private boolean actionHit;
	private int attackReadyAt, roarReadyAt;
	/** Where the blow playing is aimed (world). */
	@Nullable
	private Vec3 aim;
	/** Asks whether a blow would reach, without touching the dragon's own aim. */
	private final Strike probe = new Strike();
	private List<int[]> path = List.of();
	private int pathIndex, repathAt;
	@Nullable
	private int[] wander;
	private double lastX, lastZ;

	public GroundFightPhase(EnderDragon dragon) {
		super(dragon);
	}

	/** Lands (the dragon is on the ground already) and fights {@code target}, or rests when null. */
	public static void start(EnderDragon dragon, @Nullable LivingEntity target) {
		dragon.getPhaseManager().setPhase(DragonPhases.GROUND_FIGHT);
		dragon.getPhaseManager().getPhase(DragonPhases.GROUND_FIGHT).engage(target);
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
		ticks = lostTicks = wanderTicks = 0;
		duration = 400 + dragon.getRandom().nextInt(300);
		pauseTicks = Roaming.pause(ThreadLocalRandom.current());
		walkHeading = Roaming.headingOfYaw(dragon.getYRot());
		action = null;
		path = List.of();
		wander = null;
		attackReadyAt = 20;
		roarReadyAt = 0;
		aim = null;
		brain().hits.clear();
		lastX = dragon.getX();
		lastZ = dragon.getZ();
		dragon.setDeltaMovement(Vec3.ZERO);
		landingThud();
	}

	private void engage(@Nullable LivingEntity target) {
		this.target = target;
		if (target != null) startAction(DragonAnim.ROAR);
		// a wild dragon landed to roam stays down a good while
		else if (brain().context() == DragonBrain.Context.WILD) duration = Roaming.groundSpell(ThreadLocalRandom.current());
	}

	private DragonBrain brain() {
		return DragonfallDragon.brain(dragon);
	}

	@Override
	public void end() {
		if (action != null) brain().clearAction();
		action = null;
		aim = null;
		brain().setLookTarget(null);
	}

	@Override
	public void doServerTick() {
		ticks++;
		lastX = dragon.getX();
		lastZ = dragon.getZ();
		followGround();
		// the head follows its prey, or resting, whoever comes near
		brain().setLookTarget(target != null ? target : dragon.level().getNearestPlayer(dragon, 32.0));
		// worn down from where it cannot answer: up and away
		if (brain().hits.overwhelmed(dragon.tickCount)) {
			brain().hits.clear();
			takeOff();
			return;
		}
		if (action != null) {
			runAction();
			return;
		}
		if (ticks > duration) {
			takeOff();
			return;
		}
		if (target != null && (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator()))) target = null;
		// whoever is hurting it now (from a blind spot, while it watched another) becomes the target
		LivingEntity attacker = brain().recentAttacker(PROVOKED_TICKS / 2);
		if (attacker != null && attacker != target && attacker.distanceToSqr(dragon) < 24.0 * 24.0
				&& !(target != null && brain().hurtRecentlyBy(target, PROVOKED_TICKS))) target = attacker;
		if (target == null) {
			rest();
			return;
		}
		double dx = target.getX() - dragon.getX(), dz = target.getZ() - dragon.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		lostTicks = distance > 40 || Math.abs(target.getY() - dragon.getY()) > 10 ? lostTicks + 1 : 0;
		if (lostTicks > 60) {
			takeOff();
			return;
		}
		float bearing = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dx, -dz)) - dragon.getYRot());
		boolean ready = ticks >= attackReadyAt, near = ready && distance < STRIKE_RANGE;
		Vec3 at = strikePoint(target);
		GroundTactics.Decision decision = GroundTactics.decide(distance, bearing,
				near && reaches(DragonAnim.ATTACK, at), near && reaches(DragonAnim.TAIL_SWEEP, at), ready, ticks >= roarReadyAt,
				brain().hurtRecentlyBy(target, PROVOKED_TICKS), ThreadLocalRandom.current().nextDouble());
		switch (decision.action()) {
			case BITE -> startAction(DragonAnim.ATTACK);
			case TAIL_STRIKE -> startAction(DragonAnim.TAIL_SWEEP);
			case ROAR -> startAction(DragonAnim.ROAR);
			case NONE -> {
				if (decision.walk()) walkToward(target.getX(), target.getZ(), GroundTactics.CLOSE_IN);
				else if (decision.turn()) turnToward(target.getX(), target.getZ());
			}
		}
	}

	// ---------------------------------------------------------------- resting

	private void rest() {
		LivingEntity near = dragon.level().getNearestPlayer(WAKE, dragon);
		if (near != null) {
			engage(near);
			duration = Math.max(duration, ticks + 300);
			return;
		}
		if (wander != null) {
			// arrived, no way there, or taking too long: stop and look around a while
			if (++wanderTicks < 400 && walkToward(wander[0] + 0.5, wander[2] + 0.5, 1.5)) return;
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
			double leg = Roaming.between(Roaming.WALK_LEG_MIN, Roaming.WALK_LEG_MAX, random);
			int x = Mth.floor(dragon.getX() + Math.cos(h) * leg), z = Mth.floor(dragon.getZ() + Math.sin(h) * leg);
			int y = planner.stand(x, z);
			if (y == BlockGrid.NO_GROUND || Math.abs(y - dragon.getY()) > 6 || !brain().ticking(x, z)) continue;
			walkHeading = h;
			return new int[]{x, y, z};
		}
		return null;
	}

	// ---------------------------------------------------------------- moving on the ground

	/** Turns toward x, z by at most {@link #TURN_SPEED}; the head leads (the procedural body). */
	private boolean turnToward(double x, double z) {
		float want = (float) Math.toDegrees(Math.atan2(x - dragon.getX(), -(z - dragon.getZ())));
		float turn = Mth.clamp(Mth.wrapDegrees(want - dragon.getYRot()), -TURN_SPEED, TURN_SPEED);
		dragon.setYRot(dragon.getYRot() + turn);
		dragon.yRotA = 0.0F;
		return Math.abs(Mth.wrapDegrees(want - dragon.getYRot())) < 20.0F;
	}

	/**
	 * One step along a path toward x, z, stopping {@code reach} blocks short. Returns false when there
	 * is nowhere to go (arrived, or no path).
	 */
	private boolean walkToward(double x, double z, double reach) {
		if (Math.hypot(x - dragon.getX(), z - dragon.getZ()) <= reach) return false;
		if (path.isEmpty() || pathIndex >= path.size() || ticks >= repathAt) {
			repathAt = ticks + 20;
			path = new GroundPlanner(brain().grid()).plan(Mth.floor(dragon.getX()), Mth.floor(dragon.getZ()), Mth.floor(x), Mth.floor(z), reach, 1500);
			pathIndex = 0;
			if (path.isEmpty()) {
				turnToward(x, z);
				return false;
			}
		}
		int[] next = path.get(pathIndex);
		double nx = next[0] + 0.5, nz = next[2] + 0.5;
		if (Math.hypot(nx - dragon.getX(), nz - dragon.getZ()) < 1.0) {
			pathIndex++;
			return pathIndex < path.size();
		}
		if (!turnToward(nx, nz)) return true;          // face the way first, then walk
		float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
		double step = Math.min(WALK_SPEED, Math.hypot(nx - dragon.getX(), nz - dragon.getZ()));
		dragon.setPos(dragon.getX() + Mth.sin(yaw) * step, dragon.getY(), dragon.getZ() - Mth.cos(yaw) * step);
		return true;
	}

	/** Keeps the feet on the terrain under the body: the highest ground under the hips and wrists. */
	private void followGround() {
		BlockGrid grid = brain().grid();
		float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
		double fx = Mth.sin(yaw), fz = -Mth.cos(yaw);
		int best = BlockGrid.NO_GROUND;
		for (double[] at : new double[][]{{0, 0}, {fx * 3.5, fz * 3.5}, {-fx * 1.5, -fz * 1.5}}) {
			int g = grid.ground(Mth.floor(dragon.getX() + at[0]), Mth.floor(dragon.getZ() + at[1]));
			best = Math.max(best, g);
		}
		if (best == BlockGrid.NO_GROUND) {
			takeOff();       // the ground is gone (water, void): fly
			return;
		}
		double dy = Mth.clamp(best - dragon.getY(), -0.35, 0.25);
		if (Math.abs(dy) > 1e-3) dragon.setPos(dragon.getX(), dragon.getY() + dy, dragon.getZ());
		dragon.setDeltaMovement(dragon.getX() - lastX, 0.0, dragon.getZ() - lastZ);
	}

	private void landingThud() {
		if (!(dragon.level() instanceof ServerLevel level)) return;
		// all four feet at once, and the wings braking: the walk's own steps follow from its animation
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), DragonSounds.STEP, SoundSource.HOSTILE, 1.0F, 0.7F);
		level.playSound(null, dragon.getX(), dragon.getY() + 3.0, dragon.getZ(), DragonSounds.WING, SoundSource.HOSTILE, 1.0F, 0.8F);
		BlockPos below = BlockPos.containing(dragon.getX(), dragon.getY() - 0.5, dragon.getZ());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)),
				dragon.getX(), dragon.getY() + 0.2, dragon.getZ(), 80, 3.0, 0.2, 3.0, 0.15);
	}

	// ---------------------------------------------------------------- actions

	private void startAction(DragonAnim anim) {
		action = anim;
		actionTicks = 0;
		actionHit = false;
		brain().startAction(anim);
		if (Strike.strikes(anim) && target != null) {
			aim = strikePoint(target);
			brain().aimStrike(aim);
		}
		if (anim == DragonAnim.ROAR) roarReadyAt = ticks + 60 + 300 + dragon.getRandom().nextInt(200);
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

	private void runAction() {
		actionTicks++;
		double seconds = actionTicks / 20.0;
		boolean strike = Strike.strikes(action);
		// a blow lands when the model shows it (the animation starts after the blend)
		double hitAt = switch (action) {
			case ATTACK, TAIL_SWEEP -> Strike.hitSeconds(action) + DragonAnim.BLEND_TICKS / 20.0;
			case ROAR -> DragonAnim.ROAR_SECONDS;
			default -> Double.MAX_VALUE;
		};
		// the aim follows the target until a moment before the blow; then it is committed
		if (strike && target != null && !actionHit && actionTicks < hitAt * 20.0 - REACTION_TICKS) {
			aim = strikePoint(target);
			brain().aimStrike(aim);
			// a bite turns the body after it while the neck coils
			if (action == DragonAnim.ATTACK) turnToward(target.getX(), target.getZ());
		}
		if (!actionHit && seconds >= hitAt) {
			actionHit = true;
			switch (action) {
				case ATTACK, TAIL_SWEEP -> blow(action);
				case ROAR -> roar();
				default -> {}
			}
		}
		if (seconds >= PoseTrack.length(action) + (strike ? DragonAnim.BLEND_TICKS / 20.0 : 0.0)) {
			int recovery = action == DragonAnim.ATTACK ? BITE_RECOVERY : action == DragonAnim.TAIL_SWEEP ? TAIL_RECOVERY : 0;
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
		probe.aim(anim, aim.x - dragon.getX(), aim.y - dragon.getY(), aim.z - dragon.getZ());
		probe.solve(brain().body, 1.0F);
		double[] end = new double[3];
		probe.blow(brain().body, 1.0F, end);
		Vec3 point = dragon.position().add(end[0], end[1], end[2]);
		boolean bite = anim == DragonAnim.ATTACK;
		if (bite) dragon.playSound(SoundEvents.RAVAGER_ATTACK, 3.0F, 0.6F);
		else dragon.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 4.0F, 0.5F);
		double radius = Strike.radius(anim);
		DamageSource source = dragon.damageSources().mobAttack(dragon);
		for (Entity e : dragon.level().getEntities(dragon, new AABB(point, point).inflate(radius + 2.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
			if (!(e instanceof LivingEntity living) || !e.getBoundingBox().inflate(radius).contains(point)) continue;
			if (!living.hurt(source, bite ? BITE_DAMAGE : TAIL_DAMAGE) || bite) continue;
			// the tail flings what it hits away from the dragon
			Vec3 push = e.position().subtract(dragon.position()).multiply(1, 0, 1).normalize().scale(1.8);
			living.push(push.x, 0.45, push.z);
			living.hurtMarked = true;
		}
	}

	/** Terrify: everything close is slowed and pushed back. The growl is the client's (DragonVoiceMixin), in step with the jaw. */
	private void roar() {
		for (Entity e : dragon.level().getEntities(dragon, dragon.getBoundingBox().inflate(16.0), EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
			if (!(e instanceof LivingEntity living)) continue;
			Vec3 to = e.position().subtract(dragon.position()).multiply(1, 0, 1);
			double distance = to.length();
			if (distance > 16.0) continue;
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), dragon);
			Vec3 push = to.normalize().scale(1.2 * (1.0 - distance / 16.0));
			living.push(push.x, 0.2, push.z);
			living.hurtMarked = true;
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
