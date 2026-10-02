package crazylimits.dragonfall.mc.breath.mixin;

import crazylimits.dragonfall.mc.breath.BreathParticles;
import crazylimits.dragonfall.mc.breath.DragonFire;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cloud a dragon fireball leaves burns with void flame instead of vanilla's Dragon's Breath particle,
 * and the ground where it bursts catches dragon fire.
 */
@Mixin(DragonFireball.class)
public abstract class DragonFireballMixin {
	@Inject(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/DragonFireball;discard()V"))
	private void dragonfall$dragonFire(HitResult hit, CallbackInfo ci) {
		DragonFireball self = (DragonFireball) (Object) this;
		// a few flames, only right where it bursts
		DragonFire.spread(self.level(), self.position(), 1.5, 0.35F);
	}

	@ModifyArg(method = "onHit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/AreaEffectCloud;setParticle(Lnet/minecraft/core/particles/ParticleOptions;)V"))
	private ParticleOptions dragonfall$voidFlame(ParticleOptions particle) {
		return BreathParticles.VOID_FLAME;
	}
}
