package crazylimits.dragonsworn.mc.arena.mixin;

import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Whether a rocket is the one boosting a gliding player (it flies with them, not at anything). */
@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
	@Invoker("isAttachedToEntity")
	boolean dragonsworn$attached();
}
