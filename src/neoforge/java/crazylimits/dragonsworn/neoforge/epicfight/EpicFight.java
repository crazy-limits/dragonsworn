package crazylimits.dragonsworn.neoforge.epicfight;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;

/**
 * Epic Fight, when installed: our dragon, not its. It gets no Epic Fight patch ({@code mixin/EntityPatchProviderMixin});
 * here it keeps vanilla's 16 x 8 box, which Epic Fight shrinks to 5 x 3 for every dragon, patched or not.
 */
public final class EpicFight {
	private EpicFight() {}

	public static void init() {
		if (!ModList.get().isLoaded("epicfight")) return;
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityEvent.Size.class, event -> {
			if (event.getEntity() instanceof EnderDragon dragon) event.setNewSize(dragon.getType().getDimensions());
		});
	}
}
