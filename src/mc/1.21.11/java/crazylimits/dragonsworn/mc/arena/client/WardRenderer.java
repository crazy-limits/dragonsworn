package crazylimits.dragonsworn.mc.arena.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import crazylimits.dragonsworn.Dragonsworn;
import crazylimits.dragonsworn.arena.CrystalWard;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws a crystal's rune ward ({@link CrystalWard}): each ring a circle of flat panes, each turned
 * {@link CrystalWard#STEP} degrees from the last, its rune facing out, read left to right from outside (the
 * pane after it lies to its right, clockwise seen from the ring's top). Fullbright and see-through, both
 * sides (from inside, the runes read mirrored); a slow wave of light runs round each ring.
 */
public final class WardRenderer {
	public static final RenderType TYPE = RenderTypes.entityTranslucent(
			Identifier.fromNamespaceAndPath(Dragonsworn.MOD_ID, "textures/entity/crystal_ward.png"));

	private WardRenderer() {}

	/** The ward round a crystal standing at the pose's origin, at {@code time} ticks: one batch of panes per ring. */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, float time) {
		for (int r = 0; r < CrystalWard.RINGS.size(); r++) {
			CrystalWard.Ring ring = CrystalWard.RINGS.get(r);
			poseStack.pushPose();
			poseStack.translate(0.0, CrystalWard.CENTER, 0.0);
			poseStack.mulPose(Axis.YP.rotationDegrees((float) CrystalWard.yaw(ring, time)));
			poseStack.mulPose(Axis.XP.rotationDegrees((float) ring.tilt()));
			poseStack.mulPose(Axis.YP.rotationDegrees((float) CrystalWard.spin(ring, time)));
			int index = r;
			collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> ring(pose, buffer, index, ring, time));
			poseStack.popPose();
		}
	}

	private static void ring(PoseStack.Pose pose, VertexConsumer buffer, int r, CrystalWard.Ring ring, float time) {
		float corner = (float) ring.cornerRadius();
		float top = (float) (CrystalWard.PANE_HEIGHT / 2.0);
		float v0 = r * Math.round(CrystalWard.PANE_HEIGHT * CrystalWard.DENSITY) / (float) CrystalWard.TEXTURE_HEIGHT;
		float v1 = (r + 1) * Math.round(CrystalWard.PANE_HEIGHT * CrystalWard.DENSITY) / (float) CrystalWard.TEXTURE_HEIGHT;
		int cell = ring.cellWidth();
		for (int i = 0; i < CrystalWard.PANES; i++) {
			// round the ring's y axis, angle a at (cos a, sin a) in x-z: the next pane lies at the smaller angle, to the right seen from outside
			double left = -Math.toRadians(i * CrystalWard.STEP), right = -Math.toRadians((i + 1) * CrystalWard.STEP);
			double middle = (left + right) / 2.0;
			float lx = corner * (float) Math.cos(left), lz = corner * (float) Math.sin(left);
			float rx = corner * (float) Math.cos(right), rz = corner * (float) Math.sin(right);
			float nx = (float) Math.cos(middle), nz = (float) Math.sin(middle);
			float u0 = i * cell / (float) CrystalWard.TEXTURE_WIDTH, u1 = (i + 1) * cell / (float) CrystalWard.TEXTURE_WIDTH;
			int alpha = Math.round(255.0F * (float) CrystalWard.glow(r, i, time));
			int color = alpha << 24 | 0xFFFFFF;
			vertex(buffer, pose, lx, top, lz, u0, v0, color, nx, nz);
			vertex(buffer, pose, lx, -top, lz, u0, v1, color, nx, nz);
			vertex(buffer, pose, rx, -top, rz, u1, v1, color, nx, nz);
			vertex(buffer, pose, rx, top, rz, u1, v0, color, nx, nz);
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color,
							   float nx, float nz) {
		buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, 0.0F, nz);
	}
}
