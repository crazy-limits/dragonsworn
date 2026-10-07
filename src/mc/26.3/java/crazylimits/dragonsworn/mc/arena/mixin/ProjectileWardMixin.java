package crazylimits.dragonsworn.mc.arena.mixin;

import crazylimits.dragonsworn.mc.arena.Wards;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every projectile (arrows, fireballs, tridents, snowballs, wind charges...) bounces off the crystals' rune wards ({@link Wards}). */
@Mixin(Projectile.class)
public abstract class ProjectileWardMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void dragonsworn$ward(CallbackInfo ci) {
		Wards.deflect((Projectile) (Object) this);
	}
}
