package crazylimits.dragonfall.neoforge;

import crazylimits.dragonfall.Dragonfall;
import crazylimits.dragonfall.mc.DragonSounds;
import crazylimits.dragonfall.mc.DragonfallCommon;
import crazylimits.dragonfall.mc.breath.BreathParticles;
import crazylimits.dragonfall.mc.breath.DragonFire;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Both sides: the dragon's new phases (its AI lives on the server). Rendering: {@link DragonfallNeoForgeClient}. */
@Mod(Dragonfall.MOD_ID)
public final class DragonfallNeoForge {
	public DragonfallNeoForge(IEventBus modBus) {
		DragonfallCommon.init();
		// the server needs them too: a breath cloud syncs its particle by id
		modBus.addListener(RegisterEvent.class, event -> event.register(Registries.PARTICLE_TYPE,
				helper -> BreathParticles.ALL.forEach(helper::register)));
		// the server plays some (the landing) by id
		modBus.addListener(RegisterEvent.class, event -> event.register(Registries.SOUND_EVENT,
				helper -> DragonSounds.ALL.forEach(helper::register)));
		// blocks are created while their registry is open
		modBus.addListener(RegisterEvent.class, event -> event.register(Registries.BLOCK,
				helper -> DragonFire.register(helper::register)));
	}
}
