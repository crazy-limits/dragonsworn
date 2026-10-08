package crazylimits.dragonsworn.neoforge.irons;

import net.neoforged.fml.ModList;

/** Iron's Spells 'n Spellbooks, when installed: the dragon's {@link Counterspell}. */
public final class IronsSpells {
	private IronsSpells() {}

	public static void init() {
		if (ModList.get().isLoaded("irons_spellbooks")) Counterspell.register();
	}
}
