package crazylimits.dragonfall.fabric;

import crazylimits.dragonfall.mc.DragonSounds;
import crazylimits.dragonfall.mc.DragonfallCommon;
import crazylimits.dragonfall.mc.breath.BreathParticles;
import crazylimits.dragonfall.mc.breath.DragonFire;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Both sides: the dragon's new phases (its AI lives on the server). */
public final class DragonfallFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		DragonfallCommon.init();
		// the server needs them too: a breath cloud syncs its particle by id
		BreathParticles.ALL.forEach((id, type) -> Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, type));
		// the server plays some (the landing) by id
		DragonSounds.ALL.forEach((id, sound) -> Registry.register(BuiltInRegistries.SOUND_EVENT, id, sound));
		DragonFire.register((id, block) -> Registry.register(BuiltInRegistries.BLOCK, id, block));
	}
}
