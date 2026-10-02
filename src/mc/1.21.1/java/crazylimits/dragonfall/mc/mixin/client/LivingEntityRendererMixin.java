package crazylimits.dragonfall.mc.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import crazylimits.dragonfall.mc.DragonfallDragon;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prey held by the dragon (it rides it, see {@code mc/PreyHold}) is drawn lying flat in its grip, face
 * down, turned about its middle (where the talons or jaws hold it): along the dragon in its talons,
 * across its jaws. Never seated.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z", ordinal = 0))
	private boolean dragonfall$notSeated(LivingEntity entity, Operation<Boolean> original) {
		return original.call(entity) && !(entity.getVehicle() instanceof EnderDragon);
	}

	@WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V"))
	private void dragonfall$lying(LivingEntityRenderer<?, ?> self, LivingEntity entity, PoseStack pose, float bob, float bodyYaw, float partialTick,
			float scale, Operation<Void> original) {
		double yaw = entity.getVehicle() instanceof EnderDragon dragon ? DragonfallDragon.brain(dragon).prey.lyingYaw(entity, partialTick) : Double.NaN;
		if (Double.isNaN(yaw)) {
			original.call(self, entity, pose, bob, bodyYaw, partialTick, scale);
			return;
		}
		float middle = entity.getBbHeight() / 2.0F / scale;
		pose.mulPose(Axis.YP.rotationDegrees(180.0F - (float) yaw));
		// head first along the yaw, face down, about its middle
		pose.translate(0.0F, middle, 0.0F);
		pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
		pose.translate(0.0F, -middle, 0.0F);
	}
}
