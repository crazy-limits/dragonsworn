package crazylimits.dragonsworn.mc.arena.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import crazylimits.dragonsworn.mc.arena.Wards;
import crazylimits.dragonsworn.mc.arena.client.WardRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A warded crystal is drawn with its rune ward round it ({@link WardRenderer}), after the crystal itself (its last
 * {@code popPose}, the pose back at the crystal's feet, before the beam moves it): asking for the ward's translucent
 * buffer then draws the crystal's batch first. Drawn before the crystal, the ward's batch went first instead, and its
 * panes, writing depth, hid the crystal behind them.
 */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererMixin {
	@Inject(method = "render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 1, shift = At.Shift.AFTER))
	private void dragonsworn$ward(EndCrystal crystal, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
								  int light, CallbackInfo ci) {
		if (Wards.warded(crystal)) WardRenderer.draw(poseStack, buffers.getBuffer(WardRenderer.TYPE), crystal.time + partialTick);
	}
}
