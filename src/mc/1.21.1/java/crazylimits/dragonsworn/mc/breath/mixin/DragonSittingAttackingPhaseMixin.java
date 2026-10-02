package crazylimits.dragonsworn.mc.breath.mixin;

import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonSittingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingAttackingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the roar, the perched dragon either breathes vanilla's lingering cloud or pours the stream. Its
 * growl is no longer vanilla's (restarted every tick from the first tick, before the jaw opens):
 * DragonVoiceMixin plays it once, as the roar animation opens the jaw.
 */
@Mixin(DragonSittingAttackingPhase.class)
public abstract class DragonSittingAttackingPhaseMixin extends AbstractDragonSittingPhase {
	private DragonSittingAttackingPhaseMixin(EnderDragon dragon) {
		super(dragon);
	}

	@ModifyArg(method = "doServerTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/EnderDragonPhaseManager;setPhase(Lnet/minecraft/world/entity/boss/enderdragon/phases/EnderDragonPhase;)V"))
	private EnderDragonPhase<?> dragonsworn$maybeStream(EnderDragonPhase<?> next) {
		if (next != EnderDragonPhase.SITTING_FLAMING) return next;
		BreathStreamPhase stream = this.dragon.getPhaseManager().getPhase(BreathStreamPhase.PHASE);
		return BreathAttack.chooseStream(this.dragon.getRandom().nextDouble(), stream.streamsThisLanding())
				? BreathStreamPhase.PHASE : next;
	}

	@Inject(method = "doClientTick", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$noEarlyGrowl(CallbackInfo ci) {
		ci.cancel();
	}
}
