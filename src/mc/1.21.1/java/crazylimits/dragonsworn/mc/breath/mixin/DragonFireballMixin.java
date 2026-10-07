package crazylimits.dragonsworn.mc.breath.mixin;

import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cloud a dragon fireball leaves burns with void flame instead of vanilla's Dragon's Breath particle,
 * hurts from the moment it bursts (vanilla's cloud waits a second, by which time whoever it hit has walked
 * out of it), and the ground where it bursts catches dragon fire.
 */
@Mixin(DragonFireball.class)
public abstract class DragonFireballMixin {
	@Inject(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/DragonFireball;discard()V"))
	private void dragonsworn$dragonFire(HitResult hit, CallbackInfo ci) {
		DragonFireball self = (DragonFireball) (Object) this;
		// a few flames, only right where it bursts
		DragonFire.spread(self.level(), self.position(), 1.5, 0.35F);
	}

	@ModifyArg(method = "onHit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/AreaEffectCloud;setParticle(Lnet/minecraft/core/particles/ParticleOptions;)V"))
	private ParticleOptions dragonsworn$voidFlame(ParticleOptions particle) {
		return BreathParticles.VOID_FLAME;
	}

	@ModifyArg(method = "onHit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private Entity dragonsworn$hurtAtOnce(Entity entity) {
		if (entity instanceof AreaEffectCloud cloud) cloud.setWaitTime(0);
		return entity;
	}
}
