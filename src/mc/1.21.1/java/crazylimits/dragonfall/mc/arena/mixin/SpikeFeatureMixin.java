package crazylimits.dragonfall.mc.arena.mixin;

import crazylimits.dragonfall.mc.arena.Monoliths;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the End's crystal spires as Dragonfall's monoliths instead of vanilla's obsidian cylinders, both
 * when the End generates and when the respawn ritual raises them again.
 */
@Mixin(SpikeFeature.class)
public abstract class SpikeFeatureMixin {
	@Inject(method = "placeSpike", at = @At("HEAD"), cancellable = true)
	private void dragonfall$monolith(ServerLevelAccessor level, RandomSource random, SpikeConfiguration config,
									 SpikeFeature.EndSpike spike, CallbackInfo ci) {
		Monoliths.place(level, random, config, spike);
		ci.cancel();
	}
}
