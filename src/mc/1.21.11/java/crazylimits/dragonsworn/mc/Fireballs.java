package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.body.PartSolver;
import crazylimits.dragonsworn.body.Parts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Every fireball the dragon shoots (the roam's pass and barrage, the arena's strafe): its head turns to the
 * target first, and only once it points there ({@link BreathAttack#FIREBALL_CONE}) does the dragon heat up,
 * the stream breath's glow played {@link BreathAttack#FIREBALL_SPEEDUP} times faster; the fireball flies from
 * the mouth when the glow reaches the jaw. A head that does not come round within
 * {@link BreathAttack#FIREBALL_AIM_TICKS} drops the shot before any glow; once glowing it always fires.
 * Synced as {@link DragonData#FIREBALL}: a count of charges x {@link #STAGES} + the stage.
 */
public final class Fireballs {
	/** How far in front of the head's center the fireball leaves the mouth (blocks). */
	private static final double MUZZLE = 1.5;
	/** The synced stages: none (dropped, or never charged), turning the head, heating up. */
	private static final int NONE = 0, TURNING = 1, HEATING = 2, STAGES = 3;

	private final DragonBrain brain;
	/** The fireball charging (server): what it is for and ticks into its stage; null for none. */
	private LivingEntity target;
	private int ticks;
	/** What the head watched before the fireball turned it to its target (server; -1: nothing). */
	private int lookBefore = -1;
	/**
	 * Both sides: the stage the last charge is in (the client's view of {@link DragonData#FIREBALL}), the
	 * synced value last seen (client; null before the first tick), and when the glow started.
	 */
	private int stage = NONE;
	private Integer seen;
	private int seenAt = Integer.MIN_VALUE / 2;
	private final double[] lookAngles = new double[2];

	Fireballs(DragonBrain brain) {
		this.brain = brain;
	}

	/** Server: starts charging a fireball at {@code target}: the head turns to it first. Ignored while one is charging. */
	public void charge(LivingEntity target) {
		if (this.target != null) return;
		EnderDragon dragon = brain.dragon();
		this.target = target;
		ticks = 0;
		lookBefore = dragon.getEntityData().get(DragonData.LOOK);
		sync(dragon.getEntityData().get(DragonData.FIREBALL) / STAGES + 1, TURNING);
	}

	private void sync(int count, int stage) {
		EnderDragon dragon = brain.dragon();
		this.stage = stage;
		if (stage == HEATING) seenAt = dragon.tickCount;
		dragon.getEntityData().set(DragonData.FIREBALL, count * STAGES + stage);
	}

	/** Client, each tick: follows the synced stage (the glow starts as it turns to heating). */
	void clientTick() {
		EnderDragon dragon = brain.dragon();
		int fireballs = dragon.getEntityData().get(DragonData.FIREBALL);
		if (seen != null && fireballs != seen) {
			stage = fireballs % STAGES;
			if (stage == HEATING) seenAt = dragon.tickCount;
		}
		seen = fireballs;
	}

	/** Both sides: whether a fireball turns the head all the way round to its target (turning, or heating up). */
	public boolean aiming() {
		return stage == TURNING || stage == HEATING && brain.dragon().tickCount - seenAt <= BreathAttack.FIREBALL_WINDUP_TICKS;
	}

	/** Server, each tick: the head turning to the target, then the windup, then the shot. */
	void tick() {
		if (target == null) return;
		EnderDragon dragon = brain.dragon();
		int count = dragon.getEntityData().get(DragonData.FIREBALL) / STAGES;
		boolean gone = !target.isAlive() || target.level() != dragon.level();
		if (dragon.isDeadOrDying() || brain.dying() || gone && stage == TURNING) {
			if (stage == TURNING) sync(count, NONE);
			end();
			return;
		}
		// the head turns to the target through it all (the phase's own look comes back after)
		if (!gone) brain.setLookTarget(target);
		ticks++;
		if (stage == TURNING) {
			// the glow starts only once the head points at the target: a shot it cannot aim is dropped unseen
			if (headPointsAt(target)) {
				sync(count, HEATING);
				ticks = 0;
			} else if (ticks >= BreathAttack.FIREBALL_AIM_TICKS) {
				sync(count, NONE);
				end();
			}
			return;
		}
		if (ticks < BreathAttack.FIREBALL_WINDUP_TICKS) return;
		LivingEntity at = target;
		end();
		Vec3 head = brain.partCenter(Parts.HEAD);
		// at the target; if it slipped out of the head's line (or died) while the dragon heated up, down the head's line
		// (it never glows for nothing, nor shoots backwards over its own body)
		Vec3 dir = !gone && headPointsAt(at)
				? new Vec3(at.getX() - head.x, at.getY(0.5) - head.y, at.getZ() - head.z).normalize()
				: head.subtract(brain.partCenter(Parts.NECK_UPPER)).normalize();
		// out of the mouth, in front of the head
		Vec3 from = head.add(dir.scale(MUZZLE));
		if (!dragon.isSilent()) dragon.level().levelEvent(null, LevelEvent.SOUND_DRAGON_FIREBALL, dragon.blockPosition(), 0);
		DragonFireball fireball = new DragonFireball(dragon.level(), dragon, dir);
		fireball.snapTo(from.x, from.y, from.z, 0.0F, 0.0F);
		dragon.level().addFreshEntity(fireball);
	}

	private void end() {
		target = null;
		// back to what it watched before (a phase that sets its own look does so again next tick)
		Entity before = lookBefore < 0 ? null : brain.dragon().level().getEntity(lookBefore);
		brain.setLookTarget(before instanceof LivingEntity living && living.isAlive() ? living : null);
	}

	/**
	 * Whether the head, as last posed with its look turn, points within {@link BreathAttack#FIREBALL_CONE}
	 * of {@code target}: the look's remaining error, the target from the eyes against where the head points.
	 */
	private boolean headPointsAt(Entity target) {
		EnderDragon dragon = brain.dragon();
		double[] at = new double[3];
		PartSolver.toModel(brain.body, 1.0F, target.getX() - dragon.getX(), target.getY(0.5) - dragon.getY(), target.getZ() - dragon.getZ(), at);
		if (!brain.lookAngles(at, lookAngles)) return false;
		double yaw = lookAngles[0] - brain.look.yaw(), pitch = lookAngles[1] - brain.look.pitch();
		return yaw * yaw + pitch * pitch < BreathAttack.FIREBALL_CONE * BreathAttack.FIREBALL_CONE;
	}

	/** Both sides: ticks (fractional) since the last fireball's windup (its glow) started, for the heat glow. */
	public double glowTicks(float partialTick) {
		return brain.dragon().tickCount - seenAt + partialTick;
	}
}
