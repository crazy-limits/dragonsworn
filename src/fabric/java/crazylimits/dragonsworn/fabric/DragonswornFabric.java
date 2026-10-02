package crazylimits.dragonsworn.fabric;

import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.DragonswornCommon;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Both sides: the dragon's new phases (its AI lives on the server). */
public final class DragonswornFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		DragonswornCommon.init();
		// the AI's server config: now, as a server starts, and on /reload
		DragonswornCommon.loadConfig(FabricLoader.getInstance().getConfigDir());
		ServerLifecycleEvents.SERVER_STARTING.register(server -> DragonswornCommon.reloadConfig());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> DragonswornCommon.reloadConfig());
		// the server needs them too: a breath cloud syncs its particle by id
		BreathParticles.ALL.forEach((id, type) -> Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, type));
		// the server plays some (the landing) by id
		DragonSounds.ALL.forEach((id, sound) -> Registry.register(BuiltInRegistries.SOUND_EVENT, id, sound));
		DragonFire.register((id, block) -> Registry.register(BuiltInRegistries.BLOCK, id, block));
	}
}
