package crazylimits.dragonsworn.mc.arena;

import crazylimits.dragonsworn.arena.Monolith;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The crystal spires the End's own worldgen names, where a datapack moves them: Stellarity's {@code the_end}
 * biome places End spike features with their own spike lists in place of vanilla's (which take
 * {@code SpikeFeature.getSpikesForLevel}'s seeded ring). Spikes over the exit portal are left out
 * ({@link Monolith#spire}). Looked up once per set of registries (a server's).
 */
public final class SpikeLayout {
	private static volatile RegistryAccess seen;
	private static volatile List<SpikeFeature.EndSpike> spires;

	private SpikeLayout() {}

	/** The spires the End biome's spike features list, or null when they take vanilla's ring. */
	@Nullable
	public static List<SpikeFeature.EndSpike> island(WorldGenLevel level) {
		RegistryAccess access = level.registryAccess();
		if (access != seen) {
			spires = find(access);
			seen = access;
		}
		return spires;
	}

	@Nullable
	private static List<SpikeFeature.EndSpike> find(RegistryAccess access) {
		Optional<Holder.Reference<Biome>> end = access.lookupOrThrow(Registries.BIOME).get(Biomes.THE_END);
		if (end.isEmpty()) return null;
		List<SpikeFeature.EndSpike> found = new ArrayList<>();
		for (HolderSet<PlacedFeature> step : end.get().value().getGenerationSettings().features())
			for (Holder<PlacedFeature> placed : step)
				if (placed.value().feature().value().config() instanceof SpikeConfiguration config)
					for (SpikeFeature.EndSpike spike : config.getSpikes())
						if (Monolith.spire(spike.getCenterX(), spike.getCenterZ(), spike.getRadius())) found.add(spike);
		return found.isEmpty() ? null : List.copyOf(found);
	}
}
