package crazylimits.dragonsworn.neoforge.irons.mixin;

import crazylimits.dragonsworn.mc.breath.BreathParticles;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** The pool the Dragon's Breath spell leaves under whom it hits burns with void flame, as the dragon's breath clouds do. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.dragon_breath.DragonBreathPool")
public abstract class DragonBreathPoolMixin {
	@Inject(method = "getParticle", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$voidFlame(CallbackInfoReturnable<Optional<ParticleOptions>> cir) {
		cir.setReturnValue(Optional.of(BreathParticles.VOID_FLAME));
	}
}
