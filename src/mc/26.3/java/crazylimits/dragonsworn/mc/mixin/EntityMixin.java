package crazylimits.dragonsworn.mc.mixin;

import crazylimits.dragonsworn.mc.Carried;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Every entity remembers the dragon carrying it, if any (see {@code mc/PreyHold}). */
@Mixin(Entity.class)
public abstract class EntityMixin implements Carried {
	@Unique
	@Nullable
	private EnderDragon dragonsworn$carrier;

	@Override
	public EnderDragon dragonsworn$carrier() {
		return dragonsworn$carrier;
	}

	@Override
	public void dragonsworn$setCarrier(EnderDragon dragon) {
		dragonsworn$carrier = dragon;
	}
}
