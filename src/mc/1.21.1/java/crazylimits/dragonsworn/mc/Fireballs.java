package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.body.PartSolver;
import crazylimits.dragonsworn.body.Parts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Every fireball the dragon shoots (the roam's pass and barrage, the arena's strafe): its head turns to the
 * target first, and only once it points there ({@link BreathAttack#FIREBALL_CONE}) does the dragon heat up,
 * the stream breath's glow played {@link BreathAttack#FIREBALL_SPEEDUP} times faster; the fireball flies from
 * the mouth when the glow reaches the jaw. A head that does not come round within
 * {@link BreathAttack#FIREBALL_AIM_TICKS} drops the shot before any glow; once glowing it always fires, at the
 * target (led by its motion) unless the target got behind the head ({@link BreathAttack#FIREBALL_FIRE_CONE}).
 * The attack that charged it holds its course until the shot ({@link #charging}). Synced as {@link DragonData#FIREBALL}: a count of charges x {@link #STAGES} + the stage.
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
	/** Where the target stood as the glow started (server): its motion over the windup leads the shot. */
	private Vec3 heatFrom;
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

	/** Whether {@code entity} is a dragon fireball (for code built against every version: the class moved in 1.21.2). */
	public static boolean isFireball(Entity entity) {
		return entity instanceof DragonFireball;
	}

	/** Server: a fireball is charging (the head turning, or heating up): the attack holds its course until it flies. */
	public boolean charging() {
		return target != null;
	}

	/** Server: starts charging a fireball at {@code target}: the head turns to it first. Ignored while one is charging. */
	public void charge(LivingEntity target) {
		if (this.target != null) return;
		EnderDragon dragon = brain.dragon();
		this.target = target;
		ticks = 0;
		Entity before = brain.lookTarget();
		lookBefore = before == null ? -1 : before.getId();
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
				heatFrom = target.position();
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
		Vec3 line = head.subtract(brain.partCenter(Parts.NECK_UPPER)).normalize();
		// at where the target will be (it never glows for nothing); down the head's line only when the target died or
		// got behind the head, so it never shoots backwards over its own body
		Vec3 aim = gone ? null : lead(at, head, at.position().subtract(heatFrom).multiply(1, 0, 1).scale(1.0 / ticks));
		Vec3 dir = aim != null && aim.dot(line) > Math.cos(Math.toRadians(BreathAttack.FIREBALL_FIRE_CONE)) ? aim : line;
		// out of the mouth, in front of the head
		Vec3 from = head.add(dir.scale(MUZZLE));
		if (!dragon.isSilent()) dragon.level().levelEvent(null, LevelEvent.SOUND_DRAGON_FIREBALL, dragon.blockPosition(), 0);
		DragonFireball fireball = new DragonFireball(dragon.level(), dragon, dir);
		fireball.moveTo(from.x, from.y, from.z, 0.0F, 0.0F);
		dragon.level().addFreshEntity(fireball);
	}

	/**
	 * The way from {@code head} to where {@code target}'s middle will be when the fireball gets there (unit):
	 * {@code motion} (blocks per tick across the ground; up and down is mostly a jump's) for the ticks it flies.
	 */
	private static Vec3 lead(LivingEntity target, Vec3 head, Vec3 motion) {
		Vec3 to = new Vec3(target.getX() - head.x, target.getY(0.5) - head.y, target.getZ() - head.z);
		double flight = Math.min(to.length() / BreathAttack.FIREBALL_SPEED, BreathAttack.FIREBALL_LEAD_TICKS);
		return to.add(motion.scale(flight)).normalize();
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
