package crazylimits.dragonsworn.mc.arena;

import crazylimits.dragonsworn.arena.Monolith;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The crystal spires the End's own worldgen names, where a datapack moves them: Stellarity's {@code the_end}
 * biome places End spike features with their own spike lists in place of vanilla's (which take
 * {@code EndSpikeFeature.getSpikesForLevel}'s seeded ring). Spikes over the exit portal are left out
 * ({@link Monolith#spire}). Looked up once per set of registries (a server's).
 */
public final class SpikeLayout {
	private static volatile RegistryAccess seen;
	private static volatile List<EndSpikeFeature.EndSpike> spires;

	private SpikeLayout() {}

	/** The spires the End biome's spike features list, or null when they take vanilla's ring. */
	@Nullable
	public static List<EndSpikeFeature.EndSpike> island(WorldGenLevel level) {
		RegistryAccess access = level.registryAccess();
		if (access != seen) {
			spires = find(access);
			seen = access;
		}
		return spires;
	}

	@Nullable
	private static List<EndSpikeFeature.EndSpike> find(RegistryAccess access) {
		Optional<Holder.Reference<Biome>> end = access.lookupOrThrow(Registries.BIOME).get(Biomes.THE_END);
		if (end.isEmpty()) return null;
		List<EndSpikeFeature.EndSpike> found = new ArrayList<>();
		for (HolderSet<PlacedFeature> step : end.get().value().getGenerationSettings().features())
			for (Holder<PlacedFeature> placed : step)
				if (placed.value().feature().value() instanceof EndSpikeFeature feature)
					for (EndSpikeFeature.EndSpike spike : feature.spikes())
						if (Monolith.spire(spike.getCenterX(), spike.getCenterZ(), spike.getRadius())) found.add(spike);
		return found.isEmpty() ? null : List.copyOf(found);
	}
}
