package crazylimits.dragonfall.mc.mixin.client;

import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.PreyHold;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Held prey is ticked by the dragon carrying it, right after the dragon, not by the level (see {@code mc/PreyHold}). */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void dragonfall$carried(Entity entity, CallbackInfo ci) {
		if (PreyHold.carrier(entity) != null) ci.cancel();
	}

	@Inject(method = "tickNonPassenger", at = @At("TAIL"))
	private void dragonfall$carry(Entity entity, CallbackInfo ci) {
		if (entity instanceof EnderDragon dragon) DragonfallDragon.brain(dragon).prey.carry();
	}
}
