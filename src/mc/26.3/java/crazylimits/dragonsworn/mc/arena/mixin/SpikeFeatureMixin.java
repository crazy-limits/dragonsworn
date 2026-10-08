package crazylimits.dragonsworn.mc.arena.mixin;

import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.arena.Monolith;
import crazylimits.dragonsworn.mc.arena.Monoliths;
import crazylimits.dragonsworn.mc.arena.OtherMods;
import crazylimits.dragonsworn.mc.arena.SpikeLayout;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Builds the End's crystal spires as Dragonsworn's monoliths instead of vanilla's obsidian cylinders, both
 * when the End generates and when the respawn ritual raises them again. Off in the config ({@code end_island.spires})
 * or when another mod also changes {@link EndSpikeFeature} (YUNG's Better End Island, BetterEnd): then theirs (or vanilla's) stand.
 * A spike over the exit portal is no spire ({@link Monolith#spire}: Stellarity's portal hollow): vanilla's code places it.
 *
 * <p>The spikes the End fight knows (crystal count, respawn, wards) are those the End's worldgen names, where a
 * datapack moves them ({@link SpikeLayout}, Stellarity; {@code other_mods.island_spike_layout}), else vanilla's ring.
 */
@Mixin(EndSpikeFeature.class)
public abstract class SpikeFeatureMixin {
	@Inject(method = "placeSpike", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$monolith(ServerLevelAccessor level, RandomSource random, EndSpikeFeature.EndSpike spike, CallbackInfo ci) {
		if (!DragonConfig.SPIRES.get() || OtherMods.rebuild(EndSpikeFeature.class)) return;
		if (!Monolith.spire(spike.getCenterX(), spike.getCenterZ(), spike.getRadius())) return;
		Monoliths.place(level, random, (EndSpikeFeature) (Object) this, spike);
		ci.cancel();
	}

	@Inject(method = "getSpikesForLevel", at = @At("HEAD"), cancellable = true)
	private static void dragonsworn$islandLayout(WorldGenLevel level, CallbackInfoReturnable<List<EndSpikeFeature.EndSpike>> cir) {
		if (!DragonConfig.SPIKE_LAYOUT.get() || OtherMods.rebuild(EndSpikeFeature.class)) return;
		List<EndSpikeFeature.EndSpike> island = SpikeLayout.island(level);
		if (island != null) cir.setReturnValue(island);
	}
}
