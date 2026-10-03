package crazylimits.dragonsworn.mc.breath.mixin;

import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingFlamingPhase;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The perched breath's lingering cloud burns with void flame too, like the fireball's, and sets the ground under it on dragon fire. */
@Mixin(DragonSittingFlamingPhase.class)
public abstract class DragonSittingFlamingPhaseMixin {
	@Shadow @Nullable private AreaEffectCloud flame;

	@ModifyArg(method = "doServerTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/AreaEffectCloud;setCustomParticle(Lnet/minecraft/core/particles/ParticleOptions;)V"))
	private ParticleOptions dragonsworn$voidFlame(ParticleOptions particle) {
		return BreathParticles.VOID_FLAME;
	}

	@Inject(method = "doServerTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private void dragonsworn$dragonFire(ServerLevel level, CallbackInfo ci) {
		if (flame != null) DragonFire.spread(flame.level(), flame.position(), 3.0, 0.5F);
	}
}
