package crazylimits.dragonsworn.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crazylimits.dragonsworn.mc.PreyHold;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A held player is placed by the dragon (see {@code mc/PreyHold}), as a passenger is by its vehicle: the
 * server takes only its look from its move packets, and it is not floating (no kick for flying).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@WrapOperation(method = {"handlePlayerPositionChange", "tickPlayer"}, require = 2,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isPassenger()Z"))
	private boolean dragonsworn$held(ServerPlayer player, Operation<Boolean> original) {
		return original.call(player) || PreyHold.carrier(player) != null;
	}
}
