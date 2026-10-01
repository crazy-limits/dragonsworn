package crazylimits.dragonfall.mc.breath.mixin;

import crazylimits.dragonfall.mc.breath.BreathParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.projectile.DragonFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The cloud a dragon fireball leaves burns with void flame instead of vanilla's Dragon's Breath particle. */
@Mixin(DragonFireball.class)
public abstract class DragonFireballMixin {
	@ModifyArg(method = "onHit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/AreaEffectCloud;setParticle(Lnet/minecraft/core/particles/ParticleOptions;)V"))
	private ParticleOptions dragonfall$voidFlame(ParticleOptions particle) {
		return BreathParticles.VOID_FLAME;
	}
}
