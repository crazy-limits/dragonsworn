package crazylimits.dragonsworn.mc.mixin.client;

import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held by the dragon, a player lies flat (see {@code LivingEntityRendererMixin}): in first person the
 * camera is at its lying head ({@code PreyHold.lyingEyes}), not at its standing eye height, inside the dragon.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "setup", at = @At("TAIL"))
	private void dragonsworn$lyingEyes(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
		if (detached) return;
		EnderDragon dragon = PreyHold.carrier(entity);
		Vec3 eyes = dragon == null ? null : DragonswornDragon.brain(dragon).prey.lyingEyes(entity, partialTick);
		if (eyes != null) setPosition(eyes);
	}
}
