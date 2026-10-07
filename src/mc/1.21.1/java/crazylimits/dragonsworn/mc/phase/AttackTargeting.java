package crazylimits.dragonsworn.mc.phase;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** A vanilla phase (given by a mixin) that attacks someone: the head keeps on them ({@code DragonBrain}). */
public interface AttackTargeting {
	/** Whom it attacks, or closes in on to attack; null for nobody. */
	@Nullable
	LivingEntity dragonsworn$attackTarget();
}
