package crazylimits.dragonsworn.mc.arena.mixin;

import crazylimits.dragonsworn.mc.arena.Stellarity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stellarity's crying-obsidian scatter is not placed while Dragonsworn builds the spires ({@link Stellarity#skipped}). */
@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {
	@Inject(method = "placeWithContext", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$skipDecor(PlacementContext context, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		if (Stellarity.skipped((PlacedFeature) (Object) this, context.getLevel())) cir.setReturnValue(false);
	}
}
