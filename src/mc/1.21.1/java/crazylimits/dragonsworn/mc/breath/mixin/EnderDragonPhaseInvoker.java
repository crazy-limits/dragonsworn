package crazylimits.dragonsworn.mc.breath.mixin;

import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's phase registry is a private factory; new phases get the next id, the same on both sides. */
@Mixin(EnderDragonPhase.class)
public interface EnderDragonPhaseInvoker {
	@Invoker("create")
	static <T extends DragonPhaseInstance> EnderDragonPhase<T> dragonsworn$create(Class<T> type, String name) {
		throw new AssertionError("mixin");
	}
}
