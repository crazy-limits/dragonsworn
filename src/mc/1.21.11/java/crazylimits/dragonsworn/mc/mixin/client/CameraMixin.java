package crazylimits.dragonsworn.mc.mixin.client;

import crazylimits.dragonsworn.body.PreyView;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.client.showcase.FilmCamera;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Held by the dragon, a player lies flat (see {@code LivingEntityRendererMixin}): the camera sees from its lying head
 * ({@code PreyHold.lyingEyes}), not from its standing eye height, inside the dragon. Third-person and camera mods
 * (Shoulder Surfing Reloaded redirects the third-person {@code move} in the same method) place the camera first; this
 * moves whatever they placed by the difference at the end ({@code PreyView.camera}), and holds no state between frames.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float eyeHeight;

	@Shadow
	private float eyeHeightOld;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void dragonsworn$lyingEyes(Level level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
		EnderDragon dragon = PreyHold.carrier(entity);
		Vec3 lying = dragon == null ? null : DragonswornDragon.brain(dragon).prey.lyingEyes(entity, partialTick);
		if (lying == null) return;
		// where the game put the eyes before anything moved the camera on from them
		Vec3 standing = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()),
				Mth.lerp(partialTick, entity.yo, entity.getY()) + Mth.lerp(partialTick, eyeHeightOld, eyeHeight),
				Mth.lerp(partialTick, entity.zo, entity.getZ()));
		Vec3 camera = ((Camera) (Object) this).position();
		double[] to = PreyView.camera(new double[]{camera.x, camera.y, camera.z}, new double[]{standing.x, standing.y, standing.z},
				new double[]{lying.x, lying.y, lying.z}, detached);
		if (to != null) setPosition(new Vec3(to[0], to[1], to[2]));
	}

	/** The in-game film ({@code Film}) places the camera itself: only while it runs. */
	@Inject(method = "setup", at = @At("TAIL"))
	private void dragonsworn$film(Level level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
		if (!detached || !FilmCamera.active()) return;
		float[] rotation = FilmCamera.rotation(partialTick);
		setRotation(rotation[0], rotation[1]);
		setPosition(FilmCamera.eye(partialTick));
	}
}
