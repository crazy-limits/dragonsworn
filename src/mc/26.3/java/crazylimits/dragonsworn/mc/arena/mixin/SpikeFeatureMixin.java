package crazylimits.dragonsworn.mc.arena.mixin;

import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.arena.Monoliths;
import crazylimits.dragonsworn.mc.arena.OtherMods;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the End's crystal spires as Dragonsworn's monoliths instead of vanilla's obsidian cylinders, both
 * when the End generates and when the respawn ritual raises them again. Off in the config ({@code end_island.spires})
 * or when another mod also changes {@link EndSpikeFeature} (YUNG's Better End Island): then theirs (or vanilla's) stand.
 */
@Mixin(EndSpikeFeature.class)
public abstract class SpikeFeatureMixin {
	@Inject(method = "placeSpike", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$monolith(ServerLevelAccessor level, RandomSource random, EndSpikeFeature.EndSpike spike, CallbackInfo ci) {
		if (!DragonConfig.SPIRES.get() || OtherMods.rebuild(EndSpikeFeature.class)) return;
		Monoliths.place(level, random, (EndSpikeFeature) (Object) this, spike);
		ci.cancel();
	}
}
