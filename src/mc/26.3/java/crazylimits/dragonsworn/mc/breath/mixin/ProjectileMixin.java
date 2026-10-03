package crazylimits.dragonsworn.mc.breath.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A dragon's fireball flies through its own body: the parts (head, neck, wings' hull, tail) are other
 * entities than the dragon, so vanilla would let the shot burst on its own head or tail.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$notOwnBody(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (target instanceof EnderDragonPart part && part.parentMob == ((Projectile) (Object) this).getOwner()) {
			cir.setReturnValue(false);
		}
	}
}
