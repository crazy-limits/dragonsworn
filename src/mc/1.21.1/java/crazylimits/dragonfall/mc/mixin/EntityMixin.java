package crazylimits.dragonfall.mc.mixin;

import crazylimits.dragonfall.mc.Carried;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import org.jetbrains.annotations.Nullable;

/** Every entity remembers the dragon carrying it, if any (see {@code mc/PreyHold}). */
@Mixin(Entity.class)
public abstract class EntityMixin implements Carried {
	@Unique
	@Nullable
	private EnderDragon dragonfall$carrier;

	@Override
	public EnderDragon dragonfall$carrier() {
		return dragonfall$carrier;
	}

	@Override
	public void dragonfall$setCarrier(EnderDragon dragon) {
		dragonfall$carrier = dragon;
	}
}
