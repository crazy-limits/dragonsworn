package crazylimits.dragonsworn.neoforge.epicfight.mixin;

import crazylimits.dragonsworn.mc.Fireballs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Epic Fight gives the dragon no patch: its own (phases, animations, renderer) would replace ours. Its dragon
 * fireballs keep vanilla's speed too (Epic Fight doubles it; {@code Fireballs} leads the target at vanilla's).
 * Ahead of datapack patches and the global stun rule's, which this one method hands out as well.
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.provider.CommonEntityPatchProvider")
public abstract class EntityPatchProviderMixin {
	@Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$noDragonPatch(Entity entity, CallbackInfoReturnable<Object> cir) {
		if (entity instanceof EnderDragon || Fireballs.isFireball(entity)) cir.setReturnValue(null);
	}
}
