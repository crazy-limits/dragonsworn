package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.Parts;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Who has been hurting the dragon, and when (server): a wild dragon goes after them, blows answer them. */
public final class CombatMemory {
	private final DragonBrain brain;
	/** Whoever last attacked it (a wild dragon's target until it is far, dead or out of the game). */
	private LivingEntity lastAttacker;
	private Entity lastHurtBy;
	private int lastHurtAt = Integer.MIN_VALUE / 2;

	CombatMemory(DragonBrain brain) {
		this.brain = brain;
	}

	/** A blow at part {@code part} (index into the parts, {@link Parts}; -1: the dragon itself), whether it hurts or not. */
	public void hurtBy(DamageSource source, int part) {
		EnderDragon dragon = brain.dragon();
		if (source.getEntity() instanceof LivingEntity attacker && attacker != dragon) lastAttacker = attacker;
		// someone else going for the head makes it drop what it holds in its jaws
		boolean head = Parts.headOrNeck(part);
		Entity by = source.getEntity();
		PreyHold prey = brain.prey;
		if (head && !dragon.level().isClientSide() && by != null && by != dragon && prey.hold() == Grip.Hold.JAW && by != prey.prey()) {
			prey.release(new Vec3(0.0, 0.1, 0.0));
		}
	}

	/** A hit that took {@code lost} health off: counted (too many at once and it takes off), and a wild dragon's stance weighs it. */
	public void hit(DamageSource source, float lost) {
		EnderDragon dragon = brain.dragon();
		lastHurtBy = source.getEntity();
		lastHurtAt = dragon.tickCount;
		brain.hits.hit(dragon.tickCount);
		if (brain.context() == DragonBrain.Context.WILD) brain.stance.hurt(lost / dragon.getMaxHealth(), ThreadLocalRandom.current());
	}

	/** The living thing that hurt the dragon within the last {@code ticks}, or null. */
	public LivingEntity recentAttacker(int ticks) {
		if (!(lastHurtBy instanceof LivingEntity living) || !living.isAlive() || brain.dragon().tickCount - lastHurtAt > ticks) return null;
		return Targets.untouchable(living) ? null : living;
	}

	/** Whether {@code attacker} hurt the dragon (arrows count as their archer) within the last {@code ticks}. */
	public boolean hurtRecentlyBy(Entity attacker, int ticks) {
		return attacker != null && attacker == lastHurtBy && brain.dragon().tickCount - lastHurtAt <= ticks;
	}

	/** Whoever last attacked it, while they are still worth chasing (within {@code forget} blocks), else null. */
	LivingEntity lastAttacker(double forget) {
		EnderDragon dragon = brain.dragon();
		if (lastAttacker != null && (!lastAttacker.isAlive() || lastAttacker.distanceToSqr(dragon) > forget * forget
				|| Targets.untouchable(lastAttacker))) {
			lastAttacker = null;
		}
		return lastAttacker;
	}
}
