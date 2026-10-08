package crazylimits.dragonsworn.mc.arena;

import crazylimits.dragonsworn.config.DragonConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Stellarity (a datapack, also shipped as a mod) runs its own End fight in functions: it kills the fight's
 * dragon, summons its own (tagged {@link #BOSS}) and drives it by rewriting its phase; its respawn ritual, boss
 * bars and attacks (which turn every dragon fireball into its own) all live under {@link #DRAGON}, and on the
 * first visit it removes the pillars' crystals ({@link #REMOVE_CRYSTALS}: its fight starts without them). Which fight
 * runs is the config's ({@code other_mods.stellarity_dragon_fight}): Stellarity's, Dragonsworn then leaving its
 * dragon's moves alone, or Dragonsworn's, those functions then not found (the commands calling them fail
 * quietly) and vanilla's dragon fighting on Stellarity's island.
 */
public final class Stellarity {
	/** The tag on Stellarity's own dragon. */
	public static final String BOSS = "fe.boss";
	/** Stellarity's dragon fight: every function under this path. */
	private static final String DRAGON = "stellarity:mobs/dragon/";
	/** Removes every crystal (and its fire) on its main island, once, as a player first comes. */
	private static final String REMOVE_CRYSTALS = "stellarity:post_gen/remove_crystals";

	private Stellarity() {}

	/** Whether function {@code id} is part of Stellarity's dragon fight while that is switched off. */
	public static boolean switchedOff(Identifier id) {
		if (DragonConfig.STELLARITY_FIGHT.get()) return false;
		String name = id.toString();
		return name.startsWith(DRAGON) || name.equals(REMOVE_CRYSTALS);
	}

	/** Whether {@code dragon} is Stellarity's own, in a fight left to Stellarity. */
	public static boolean owns(Entity dragon) {
		return DragonConfig.STELLARITY_FIGHT.get() && dragon.entityTags().contains(BOSS);
	}
}
