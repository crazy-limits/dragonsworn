package crazylimits.dragonfall.mc.mixin;

import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's private phase registration, for Dragonfall's own phases. */
@Mixin(EnderDragonPhase.class)
public interface EnderDragonPhaseInvoker {
	@Invoker("create")
	static <T extends DragonPhaseInstance> EnderDragonPhase<T> dragonfall$createPhase(Class<T> phase, String name) {
		throw new AssertionError();
	}
}
