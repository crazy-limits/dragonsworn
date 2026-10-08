package crazylimits.dragonsworn.mc.arena;

import crazylimits.dragonsworn.config.DragonConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Stellarity (a datapack, also shipped as a mod) runs its own End fight in functions: it kills the fight's
 * dragon, summons its own (tagged {@link #BOSS}) and drives it by rewriting its phase; its respawn ritual, boss
 * bars and attacks (which turn every dragon fireball into its own) all live under {@link #DRAGON}, and on the
 * first visit it removes the pillars' crystals ({@link #REMOVE_CRYSTALS}: its fight starts without them). Which fight
 * runs is the config's ({@code other_mods.stellarity_dragon_fight}): Stellarity's, Dragonsworn then leaving its
 * dragon's moves alone, or Dragonsworn's, those functions then not found (the commands calling them fail
 * quietly) and vanilla's dragon fighting on Stellarity's island.
 *
 * <p>Its island also turns obsidian into crying obsidian round the pillars ({@link #DECOR}): not on Dragonsworn's spires.
 */
public final class Stellarity {
	/** The tag on Stellarity's own dragon. */
	public static final String BOSS = "fe.boss";
	/** Stellarity's dragon fight: every function under this path. */
	private static final String DRAGON = "stellarity:mobs/dragon/";
	/** Removes every crystal (and its fire) on its main island, once, as a player first comes. */
	private static final String REMOVE_CRYSTALS = "stellarity:post_gen/remove_crystals";
	/** The placed feature scattering crying obsidian through the obsidian round its pillars. */
	private static final ResourceKey<PlacedFeature> DECOR = ResourceKey.create(Registries.PLACED_FEATURE,
			ResourceLocation.fromNamespaceAndPath("stellarity", "dragons_den/obsidian_spike_decor"));
	private static volatile RegistryAccess seen;
	private static volatile PlacedFeature decor;

	private Stellarity() {}

	/** Whether function {@code id} is part of Stellarity's dragon fight while that is switched off. */
	public static boolean switchedOff(ResourceLocation id) {
		if (DragonConfig.STELLARITY_FIGHT.get()) return false;
		String name = id.toString();
		return name.startsWith(DRAGON) || name.equals(REMOVE_CRYSTALS);
	}

	/** Whether {@code dragon} is Stellarity's own, in a fight left to Stellarity. */
	public static boolean owns(Entity dragon) {
		return DragonConfig.STELLARITY_FIGHT.get() && dragon.getTags().contains(BOSS);
	}

	/** Whether {@code feature} is Stellarity's crying-obsidian scatter while Dragonsworn builds the spires (skipped then). */
	public static boolean skipped(PlacedFeature feature, WorldGenLevel level) {
		RegistryAccess access = level.registryAccess();
		if (access != seen) {
			decor = access.lookupOrThrow(Registries.PLACED_FEATURE).get(DECOR).map(Holder::value).orElse(null);
			seen = access;
		}
		return feature == decor && DragonConfig.SPIRES.get() && !OtherMods.rebuild(SpikeFeature.class);
	}
}
