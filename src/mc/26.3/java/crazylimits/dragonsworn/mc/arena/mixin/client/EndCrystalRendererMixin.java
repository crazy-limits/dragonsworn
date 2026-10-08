package crazylimits.dragonsworn.mc.arena.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import crazylimits.dragonsworn.mc.arena.Wards;
import crazylimits.dragonsworn.mc.arena.client.WardRenderer;
import crazylimits.dragonsworn.mc.client.Shaders;
import crazylimits.dragonsworn.mc.arena.client.WardState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A warded crystal is drawn with its rune ward round it ({@link WardRenderer}): the flag rides on the render state. */
@Mixin(EndCrystalRenderer.class)
public abstract class EndCrystalRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
			at = @At("TAIL"))
	private void dragonsworn$extractWard(EndCrystal crystal, EndCrystalRenderState state, float partialTick, CallbackInfo ci) {
		((WardState) state).dragonsworn$setWarded(Wards.warded(crystal));
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"))
	private void dragonsworn$ward(EndCrystalRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
								  CallbackInfo ci) {
		// see-through and glowing: no shader pack shadow
		if (!((WardState) state).dragonsworn$warded() || Shaders.shadowPass()) return;
		WardRenderer.submit(poseStack, collector, state.ageInTicks);
	}
}
