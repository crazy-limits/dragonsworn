package crazylimits.dragonsworn.mc.breath;

import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.body.Strike;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonData;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.LiftoffPhase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonSittingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

/**
 * The stream breath ({@link BreathAttack}) as a vanilla dragon phase. The perched dragon picks it after
 * its roar instead of the lingering cloud (see {@code DragonSittingAttackingPhaseMixin}): it inhales,
 * then pours void flame ahead while turning slowly after the nearest player, then perches on.
 *
 * <p>With a target the stream is aimed: an aim point follows the target, but no faster than
 * {@link BreathAttack#AIM_SPEED} while the flames pour (a running player outpaces it), and the neck is
 * drawn out straight toward it with the head pointing down it ({@code Strike}); the flames leave the
 * model's mouth for the aim. Without one the stream sweeps the ground ahead as before.
 *
 * <p>The server pours a flame puff every {@link BreathAttack#DAMAGE_INTERVAL} ({@code BreathFlames}): it burns
 * what it reaches when it gets there. The client only plays sounds here; the flames themselves are spawned
 * by {@code BreathRender} out of the model's animated mouth.
 */
public class BreathStreamPhase extends AbstractDragonSittingPhase {
	/** Ticks the model's facing lags the entity's yaw (DragonRenderer turns it by getLatencyPos(7)). */
	private static final int MODEL_LAG = 7;
	/** More than this many ticks since the last stream means the dragon landed again. */
	private static final int NEW_LANDING_TICKS = 300;
	private static final TargetingConditions TARGETING = TargetingConditions.forCombat().range(BreathAttack.RANGE + 10.0);

	private int ticks;
	private int streams;
	private int lastStreamTick = Integer.MIN_VALUE / 2;
	/** Server: where the stream is aimed (world), or null. */
	private Vec3 aim;
	/** Who the stream is poured at, when given ({@link #setTarget}); else the nearest player. */
	private LivingEntity target;
	/** The body is turning after a target that left the neck's reach. */
	private boolean turning;
	/** Poured down a tunnel ({@link #pourDown}): at a fixed point, back to the fight after (server). */
	@Nullable
	private Vec3 fixedAim;
	private boolean resume;

	public BreathStreamPhase(EnderDragon dragon) {
		super(dragon);
	}

	@Override
	public void begin() {
		ticks = 0;
		aim = null;
		target = null;
		turning = false;
		fixedAim = null;
		resume = false;
		if (dragon.tickCount - lastStreamTick > NEW_LANDING_TICKS) streams = 0;
		streams++;
	}

	@Override
	public void end() {
		lastStreamTick = dragon.tickCount;
		aim = null;
		if (!dragon.level().isClientSide()) brain().aimStrike(null);
	}

	private DragonBrain brain() {
		return DragonswornDragon.brain(dragon);
	}

	public int streamsThisLanding() {
		return dragon.tickCount - lastStreamTick > NEW_LANDING_TICKS ? 0 : streams;
	}

	/**
	 * From a fight on the ground or a wall: pours the stream at {@code at} (deep in the tunnel {@code target}
	 * hides in), then fights {@code target} on ({@link GroundFightPhase}).
	 */
	public static void pourDown(EnderDragon dragon, LivingEntity target, Vec3 at) {
		dragon.getPhaseManager().setPhase(DragonPhases.BREATH_STREAM);
		BreathStreamPhase phase = dragon.getPhaseManager().getPhase(DragonPhases.BREATH_STREAM);
		phase.target = target;
		phase.fixedAim = at;
		phase.resume = true;
		phase.streams--;
	}

	/** Pours the stream at {@code target} (any living thing) instead of the nearest player. */
	public void setTarget(LivingEntity target) {
		this.target = target;
	}

	/** Ticks since the phase began, on either side. */
	public int ticks() {
		return ticks;
	}

	@Override
	public void doServerTick(ServerLevel serverLevel) {
		ticks++;
		// on a wall its foothold gone under it: it falls off and flies
		if (brain().face().wall() && !brain().standsOn()) {
			LiftoffPhase.fall(dragon);
			return;
		}
		LivingEntity target = this.target != null && this.target.isAlive() ? this.target
				: serverLevel.getNearestPlayer(TARGETING, dragon, dragon.getX(), dragon.getY(), dragon.getZ());
		if (target != null && ticks < BreathAttack.WINDUP_TICKS + BreathAttack.STREAM_TICKS) {
			// the neck follows the target; the body only turns once the target leaves the neck's reach
			// across the face it stands on (on a wall, the wall's frame: its yaw is the heading on it)
			Vec3 toward = brain().local(fixedAim != null ? fixedAim : target.position()), here = brain().local();
			double dx = toward.x - here.x, dz = toward.z - here.z;
			turning = BreathAttack.bodyTurns(BreathAttack.offFacing(dragon.getYRot(), dx, dz), turning);
			float step = ticks < BreathAttack.WINDUP_TICKS ? BreathAttack.WINDUP_TURN : BreathAttack.STREAM_TURN;
			// upright on a wall: only the neck follows
			if (turning && !brain().face().wall()) dragon.setYRot(BreathAttack.turnToward(dragon.getYRot(), dx, dz, step));
			// the aim follows the target's body, a little above its feet; quick while inhaling, slow while pouring
			Vec3 at = fixedAim != null ? fixedAim : target.position().add(0.0, target.getBbHeight() * 0.3, 0.0);
			double speed = ticks < BreathAttack.WINDUP_TICKS ? BreathAttack.AIM_SPEED * 3.0 : BreathAttack.AIM_SPEED;
			Vec3 to = aim == null ? Vec3.ZERO : at.subtract(aim);
			aim = aim == null ? at : to.length() <= speed ? at : aim.add(to.normalize().scale(speed));
		}
		if (ticks < BreathAttack.WINDUP_TICKS + BreathAttack.STREAM_TICKS) brain().aimStrike(aim);
		if (BreathAttack.streaming(ticks) && (ticks - BreathAttack.WINDUP_TICKS) % BreathAttack.DAMAGE_INTERVAL == 0) burn();
		if (ticks >= BreathAttack.TOTAL_TICKS && resume) {
			GroundFightPhase.start(dragon, this.target, false, Foothold.STAND);
			return;
		}
		if (ticks >= BreathAttack.TOTAL_TICKS) {
			dragon.getPhaseManager().setPhase(streams >= DragonConfig.MAX_STREAMS.get() ? EnderDragonPhase.TAKEOFF : EnderDragonPhase.SITTING_SCANNING);
		}
	}

	/** The stream's aim (world) on either side, from the synced data; null when it just sweeps the ground. */
	public Vec3 aim() {
		Vector3fc a = dragon.getEntityData().get(DragonData.STRIKE);
		return Float.isFinite(a.x()) ? dragon.position().add(a.x(), a.y(), a.z()) : null;
	}

	/** The stream's start this tick: the model's mouth (the head's hitbox, on the aimed neck), or the stream pose's. */
	public Vec3 mouth() {
		if (aim() != null) return brain().partCenter(Parts.HEAD);
		double[] m = BreathAttack.mouth(dragon.flightHistory.get(MODEL_LAG).yRot());
		return dragon.position().add(m[0], m[1], m[2]);
	}

	/** The stream's axis this tick. */
	public Vec3 direction() {
		Vec3 aim = aim();
		if (aim != null) {
			Vec3 to = aim.subtract(mouth());
			if (to.lengthSqr() > 1e-4) return to.normalize();
		}
		double[] d = BreathAttack.direction(dragon.flightHistory.get(MODEL_LAG).yRot(), ticks);
		return new Vec3(d[0], d[1], d[2]);
	}

	/** Where the stream stops this tick: the block it splashes on, or the end of its reach. */
	public HitResult stream() {
		return stream(dragon, mouth(), direction(), BreathAttack.RANGE);
	}

	/** Where a stream from {@code mouth} along {@code dir} stops: the block it splashes on, or after {@code range}. */
	public static HitResult stream(EnderDragon dragon, Vec3 mouth, Vec3 dir, double range) {
		return dragon.level().clip(new ClipContext(mouth, mouth.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
	}

	private void burn() {
		brain().flames.pour(mouth(), direction(), BreathAttack.RANGE, DragonConfig.STREAM_DAMAGE.f(), false);
	}

	@Override
	public void doClientTick() {
		ticks++;
		// the inhale is silent: an attack does not roar (DragonVoice fades a roar going on)
		if (ticks == BreathAttack.WINDUP_TICKS) {
			dragon.level().playLocalSound(dragon.getX(), dragon.getY(), dragon.getZ(), DragonSounds.BREATH,
					dragon.getSoundSource(), 4.0F, 0.7F, false);
		}
		if (BreathAttack.streaming(ticks) && ticks % 5 == 0) {
			dragon.level().playLocalSound(dragon.getX(), dragon.getY(), dragon.getZ(), DragonSounds.FLAMES,
					dragon.getSoundSource(), 3.0F, 0.45F + dragon.getRandom().nextFloat() * 0.1F, false);
		}
	}

	@Override
	public EnderDragonPhase<BreathStreamPhase> getPhase() {
		return DragonPhases.BREATH_STREAM;
	}
}
