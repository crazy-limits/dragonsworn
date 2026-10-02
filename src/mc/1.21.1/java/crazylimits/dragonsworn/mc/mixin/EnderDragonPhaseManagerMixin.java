package crazylimits.dragonsworn.mc.mixin;

import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps vanilla phases that only make sense at the End's exit portal for Dragonsworn's own, depending on
 * where the dragon is (see {@code DragonBrain#remap}): a summoned dragon circles its home instead of the
 * pillar ring at 0, 0, and a dragon leaving the ground anywhere jumps off with its legs and wings.
 */
@Mixin(EnderDragonPhaseManager.class)
public abstract class EnderDragonPhaseManagerMixin {
	@Shadow
	@Final
	private EnderDragon dragon;

	@ModifyVariable(method = "setPhase", at = @At("HEAD"), argsOnly = true)
	private EnderDragonPhase<?> dragonsworn$remap(EnderDragonPhase<?> phase) {
		// the first phase is set while the dragon is still being constructed: nothing to remap yet
		if (dragon == null || dragon.getPhaseManager() == null || dragon.level().isClientSide) return phase;
		return DragonswornDragon.brain(dragon).remap(phase);
	}
}
