package crazylimits.dragonfall.mc.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.boss.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Resizes the vanilla parts to fit the model's bones. */
@Mixin(EnderDragonPart.class)
public interface EnderDragonPartAccessor {
	@Mutable
	@Accessor("size")
	void dragonfall$setSize(EntityDimensions size);
}
