package crazylimits.dragonsworn.mc.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.client.LyingPrey;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prey held by the dragon (carried, see {@code mc/PreyHold}) is drawn lying flat in its grip, face
 * down, turned about its middle (where the talons or jaws hold it): along the dragon in its talons,
 * across its jaws. How it lies is read when its render state is extracted ({@link LyingPrey}).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void dragonsworn$lyingYaw(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		EnderDragon dragon = PreyHold.carrier(entity);
		((LyingPrey) state).dragonsworn$setLyingYaw(dragon != null ? DragonswornDragon.brain(dragon).prey.lyingYaw(entity, partialTick) : Double.NaN);
	}

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V"))
	private void dragonsworn$lying(LivingEntityRenderer<?, ?, ?> self, LivingEntityRenderState state, PoseStack pose, float bodyYaw, float scale,
			Operation<Void> original) {
		double yaw = ((LyingPrey) state).dragonsworn$lyingYaw();
		if (Double.isNaN(yaw)) {
			original.call(self, state, pose, bodyYaw, scale);
			return;
		}
		float middle = state.boundingBoxHeight / 2.0F / scale;
		pose.mulPose(Axis.YP.rotationDegrees(180.0F - (float) yaw));
		// head first along the yaw, face down, about its middle
		pose.translate(0.0F, middle, 0.0F);
		pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
		pose.translate(0.0F, -middle, 0.0F);
	}
}
