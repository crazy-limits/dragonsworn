package crazylimits.dragonsworn.mc.arena.mixin;

import crazylimits.dragonsworn.mc.arena.Stellarity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Stellarity's dragon fight switched off ({@link Stellarity#switchedOff}): its functions are not found, so the
 * {@code function} and {@code schedule} commands that call them (from its tick function) do nothing.
 */
@Mixin(ServerFunctionManager.class)
public abstract class ServerFunctionManagerMixin {
	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void dragonsworn$stellarityFight(ResourceLocation id, CallbackInfoReturnable<Optional<CommandFunction<CommandSourceStack>>> cir) {
		if (Stellarity.switchedOff(id)) cir.setReturnValue(Optional.empty());
	}
}
