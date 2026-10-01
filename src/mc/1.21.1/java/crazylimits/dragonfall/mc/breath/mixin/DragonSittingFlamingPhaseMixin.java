package crazylimits.dragonfall.mc.breath.mixin;

import crazylimits.dragonfall.mc.breath.BreathParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingFlamingPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The perched breath's lingering cloud burns with void flame too, like the fireball's. */
@Mixin(DragonSittingFlamingPhase.class)
public abstract class DragonSittingFlamingPhaseMixin {
	@ModifyArg(method = "doServerTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/AreaEffectCloud;setParticle(Lnet/minecraft/core/particles/ParticleOptions;)V"))
	private ParticleOptions dragonfall$voidFlame(ParticleOptions particle) {
		return BreathParticles.VOID_FLAME;
	}
}
