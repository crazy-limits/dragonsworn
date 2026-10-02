package crazylimits.dragonfall.mc.arena.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The crystal beam (the dragon's healing beam, the crystals' own beams), vanilla's tube exactly, except that
 * its texture (the runes, {@code tools/crystal_beam.py}) flows from the crystal toward the beam's other end
 * (the dragon): vanilla scrolls it the other way. The tube starts (thin) at the receiving end and reaches the
 * crystal at {@code length}; v grows toward the crystal, so the scroll adds time where vanilla subtracts it.
 */
@Mixin(EnderDragonRenderer.class)
public abstract class EnderDragonRendererMixin {
	@Unique
	private static final RenderType DRAGONFALL$BEAM =
			RenderType.entitySmoothCutout(ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal_beam.png"));
	/** Texture lengths per tick (vanilla's speed), and blocks per texture length along the beam. */
	@Unique
	private static final float DRAGONFALL$SCROLL = 0.01F, DRAGONFALL$TILE = 32.0F;

	@Inject(method = "renderCrystalBeams", at = @At("HEAD"), cancellable = true)
	private static void dragonfall$beamTowardTheDragon(float x, float y, float z, float partialTick, int tickCount,
			PoseStack poseStack, MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
		float horizontal = Mth.sqrt(x * x + z * z);
		float length = Mth.sqrt(x * x + y * y + z * z);
		poseStack.pushPose();
		poseStack.translate(0.0F, 2.0F, 0.0F);
		poseStack.mulPose(Axis.YP.rotation((float) -Math.atan2(z, x) - (float) (Math.PI / 2)));
		poseStack.mulPose(Axis.XP.rotation((float) -Math.atan2(horizontal, y) - (float) (Math.PI / 2)));
		VertexConsumer buffer = buffers.getBuffer(DRAGONFALL$BEAM);
		float scroll = (tickCount + partialTick) * DRAGONFALL$SCROLL;
		float near = scroll, far = length / DRAGONFALL$TILE + scroll;
		PoseStack.Pose pose = poseStack.last();
		float px = 0.0F, py = 0.75F, pu = 0.0F;
		for (int j = 1; j <= 8; j++) {
			float qx = Mth.sin(j * (float) (Math.PI * 2) / 8.0F) * 0.75F;
			float qy = Mth.cos(j * (float) (Math.PI * 2) / 8.0F) * 0.75F;
			float qu = j / 8.0F;
			buffer.addVertex(pose, px * 0.2F, py * 0.2F, 0.0F).setColor(0xFF000000).setUv(pu, near)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, -1.0F, 0.0F);
			buffer.addVertex(pose, px, py, length).setColor(-1).setUv(pu, far)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, -1.0F, 0.0F);
			buffer.addVertex(pose, qx, qy, length).setColor(-1).setUv(qu, far)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, -1.0F, 0.0F);
			buffer.addVertex(pose, qx * 0.2F, qy * 0.2F, 0.0F).setColor(0xFF000000).setUv(qu, near)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, -1.0F, 0.0F);
			px = qx;
			py = qy;
			pu = qu;
		}
		poseStack.popPose();
		ci.cancel();
	}
}
