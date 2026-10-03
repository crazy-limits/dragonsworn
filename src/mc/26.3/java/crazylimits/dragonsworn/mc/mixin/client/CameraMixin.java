package crazylimits.dragonsworn.mc.mixin.client;

import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.client.showcase.FilmCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
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
	@Nullable
	private Entity entity;

	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void dragonsworn$lyingEyes(float partialTick, CallbackInfo ci) {
		if (detached || entity == null) return;
		EnderDragon dragon = PreyHold.carrier(entity);
		Vec3 eyes = dragon == null ? null : DragonswornDragon.brain(dragon).prey.lyingEyes(entity, partialTick);
		if (eyes != null) setPosition(eyes);
	}

	/** The in-game film ({@code Film}) places the camera itself: only while it runs. */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void dragonsworn$film(float partialTick, CallbackInfo ci) {
		if (!detached || !FilmCamera.active()) return;
		float[] rotation = FilmCamera.rotation(partialTick);
		setRotation(rotation[0], rotation[1]);
		setPosition(FilmCamera.eye(partialTick));
	}
}
