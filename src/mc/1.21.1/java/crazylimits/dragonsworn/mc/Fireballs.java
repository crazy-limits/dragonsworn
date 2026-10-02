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
 * Every fireball the dragon shoots (the roam's pass and barrage, the arena's strafe): the dragon heats up
 * first, the stream breath's glow played {@link BreathAttack#FIREBALL_SPEEDUP} times faster (every client,
 * on the change of {@link DragonData#FIREBALL}), its head turns to the target, and the fireball flies from
 * the mouth once the glow reaches the jaw and the head points at the target.
 */
public final class Fireballs {
	/** How far in front of the head's center the fireball leaves the mouth (blocks). */
	private static final double MUZZLE = 1.5;

	private final DragonBrain brain;
	/** The fireball charging (server): what it is for and ticks into its windup; null for none. */
	private LivingEntity target;
	private int ticks;
	/** What the head watched before the fireball turned it to its target (server; -1: nothing). */
	private int lookBefore = -1;
	/**
	 * When the last fireball started charging: the server's own, the client's view of {@link DragonData#FIREBALL}
	 * (the count last seen, null before the first tick, and when it changed).
	 */
	private Integer seen;
	private int seenAt = Integer.MIN_VALUE / 2;
	private final double[] lookAngles = new double[2];

	Fireballs(DragonBrain brain) {
		this.brain = brain;
	}

	/** Server: starts charging a fireball at {@code target}. Ignored while one is charging. */
	public void charge(LivingEntity target) {
		if (this.target != null) return;
		EnderDragon dragon = brain.dragon();
		this.target = target;
		ticks = 0;
		seenAt = dragon.tickCount;
		lookBefore = dragon.getEntityData().get(DragonData.LOOK);
		dragon.getEntityData().set(DragonData.FIREBALL, dragon.getEntityData().get(DragonData.FIREBALL) + 1);
	}

	/** Client, each tick: notices a new charge from the synced count (its glow starts then). */
	void clientTick() {
		EnderDragon dragon = brain.dragon();
		int fireballs = dragon.getEntityData().get(DragonData.FIREBALL);
		if (seen != null && fireballs != seen) seenAt = dragon.tickCount;
		seen = fireballs;
	}

	/** Server, each tick: the windup, then the shot (or none, if the head never came round). */
	void tick() {
		if (target == null) return;
		EnderDragon dragon = brain.dragon();
		if (dragon.isDeadOrDying() || brain.dying() || !target.isAlive() || target.level() != dragon.level()) {
			end();
			return;
		}
		// the head turns to the target through the windup (the phase's own look comes back after)
		brain.setLookTarget(target);
		if (++ticks < BreathAttack.FIREBALL_WINDUP_TICKS) return;
		LivingEntity at = target;
		Vec3 head = brain.partCenter(Parts.HEAD);
		Vec3 aim = new Vec3(at.getX() - head.x, at.getY(0.5) - head.y, at.getZ() - head.z);
		if (!headPointsAt(at)) {
			// behind it, or the head has not come round yet: wait for it, but never shoot backwards
			if (ticks >= BreathAttack.FIREBALL_WINDUP_TICKS + BreathAttack.FIREBALL_AIM_TICKS) end();
			return;
		}
		end();
		Vec3 dir = aim.normalize();
		// out of the mouth, in front of the head
		Vec3 from = head.add(dir.scale(MUZZLE));
		if (!dragon.isSilent()) dragon.level().levelEvent(null, LevelEvent.SOUND_DRAGON_FIREBALL, dragon.blockPosition(), 0);
		DragonFireball fireball = new DragonFireball(dragon.level(), dragon, dir);
		fireball.moveTo(from.x, from.y, from.z, 0.0F, 0.0F);
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

	/** Client: ticks (fractional) since the last fireball's windup started, for the heat glow. */
	public double glowTicks(float partialTick) {
		return brain.dragon().tickCount - seenAt + partialTick;
	}
}
