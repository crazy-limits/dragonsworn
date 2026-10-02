package crazylimits.dragonsworn.mc;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.jetbrains.annotations.Nullable;

/**
 * Implemented on every entity by a mixin: the dragon that carries it in its talons or jaws, if any
 * (set by {@link PreyHold}; read through {@link PreyHold#carrier}, which checks it is still so).
 */
public interface Carried {
	@Nullable
	EnderDragon dragonsworn$carrier();

	void dragonsworn$setCarrier(@Nullable EnderDragon dragon);
}
