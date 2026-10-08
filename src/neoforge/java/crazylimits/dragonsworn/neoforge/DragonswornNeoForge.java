package crazylimits.dragonsworn.neoforge;

import crazylimits.dragonsworn.Dragonsworn;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.DragonswornCommon;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import crazylimits.dragonsworn.neoforge.irons.IronsSpells;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Both sides: the dragon's new phases (its AI lives on the server). Rendering: {@link DragonswornNeoForgeClient}. */
@Mod(Dragonsworn.MOD_ID)
public final class DragonswornNeoForge {
	public DragonswornNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
		DragonswornCommon.init();
		// the AI's server config: now, as a server starts, and on /reload (the sync to everyone, no single player)
		DragonswornCommon.loadConfig(FMLPaths.CONFIGDIR.get());
		NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> DragonswornCommon.reloadConfig());
		NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, event -> {
			if (event.getPlayer() == null) DragonswornCommon.reloadConfig();
		});
		// Iron's Spells 'n Spellbooks: the dragon counters spells (1.21.1)
		IronsSpells.init();
		// the mods list's config button
		if (dist.isClient()) DragonswornNeoForgeClient.registerConfigScreen(container);
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
