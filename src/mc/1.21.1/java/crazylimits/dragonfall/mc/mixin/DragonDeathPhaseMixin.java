package crazylimits.dragonfall.mc.mixin;

import crazylimits.dragonfall.mc.DragonfallDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonDeathPhase;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's dying flight (to the portal, dying on arrival or on any bump) becomes the last flight of
 * {@code ai/DeathFlight}: over the altar, straight up, then death and the cocoon ({@code DragonBrain#deathTick}).
 * The client's explosions on the way stay.
 */
@Mixin(DragonDeathPhase.class)
public abstract class DragonDeathPhaseMixin extends AbstractDragonPhaseInstance {
	protected DragonDeathPhaseMixin(EnderDragon dragon) {
		super(dragon);
	}

	@Inject(method = "begin", at = @At("TAIL"))
	private void dragonfall$begin(CallbackInfo ci) {
		if (!dragon.level().isClientSide) DragonfallDragon.brain(dragon).startDeathFlight();
	}

	@Inject(method = "doServerTick", at = @At("HEAD"), cancellable = true)
	private void dragonfall$tick(CallbackInfo ci) {
		DragonfallDragon.brain(dragon).deathTick();
		ci.cancel();
	}

	@Inject(method = "getFlyTargetLocation", at = @At("HEAD"), cancellable = true)
	private void dragonfall$target(CallbackInfoReturnable<Vec3> cir) {
		cir.setReturnValue(DragonfallDragon.brain(dragon).deathTarget());
	}
}
