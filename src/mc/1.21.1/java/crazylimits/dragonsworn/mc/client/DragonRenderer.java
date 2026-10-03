package crazylimits.dragonsworn.mc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import crazylimits.dragonsworn.body.DragonBody;
import crazylimits.dragonsworn.body.PartSolver;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.breath.client.BreathRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the vanilla Ender Dragon with the Dragonsworn model, turned and banked by the procedural body
 * (see {@link #applyRotations}). The crystal healing beam and the death rays are kept.
 *
 * <p>Assets: the model and its textures (skin, glowmask) derive from the "Ender Dragon Reborn" resource pack
 * by Parrie43, All Rights Reserved. The LGPL does not cover them: see LICENSE-ASSETS.md.
 */
public final class DragonRenderer extends GeoReplacedEntityRenderer<EnderDragon, ReplacedEnderDragon> {
	/** The model reaches ~12 blocks from the entity's origin, well past the vanilla culling box. */
	private static final double CULL_MARGIN = 14.0;
	private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0) / 2.0);

	public DragonRenderer(EntityRendererProvider.Context context) {
		super(context, new DragonModel(), new ReplacedEnderDragon());
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		addRenderLayer(new HeatGlowLayer(this));
		this.shadowRadius = 0;
	}

	/** The dragon being drawn (for the render layers, which get the stand-in animatable). */
	EnderDragon entity() {
		return this.currentEntity;
	}

	/**
	 * The body's yaw, pitch and bank from the procedural layer ({@link DragonBody}): the body follows the
	 * steering a few ticks late (the head leads), rolls into turns and pitches with the climb. Pitch and
	 * roll turn the model about the middle of the torso. {@code PartSolver} places the hitboxes the
	 * same way.
	 */
	@Override
	protected void applyRotations(ReplacedEnderDragon animatable, PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTick, float nativeScale) {
		DragonBody body = DragonswornDragon.brain(this.currentEntity).body;
		poseStack.translate(0.0, body.lift(partialTick), 0.0);
		poseStack.mulPose(Axis.YP.rotationDegrees((float) -body.yaw(partialTick)));
		poseStack.translate(0.0, BodyFrame.CENTER_Y, BodyFrame.CENTER_Z);
		poseStack.mulPose(Axis.XP.rotationDegrees((float) body.pitch(partialTick)));
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) -body.roll(partialTick)));
		poseStack.translate(0.0, -BodyFrame.CENTER_Y, -BodyFrame.CENTER_Z);
	}

	@Override
	public void render(EnderDragon dragon, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
			int packedLight) {
		super.render(dragon, entityYaw, partialTick, poseStack, buffers, packedLight);
		BreathRender.afterRender(dragon, getGeoModel());
		LimbContact.afterRender(dragon, getGeoModel(), partialTick);

		if (dragon.dragonDeathTime > 0) {
			float progress = (dragon.dragonDeathTime + partialTick) / 200.0F;
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees((float) -DragonswornDragon.brain(dragon).body.yaw(partialTick)));
			poseStack.translate(0.0F, 3.5F, -1.0F);
			renderRays(poseStack, progress, buffers.getBuffer(RenderType.dragonRays()));
			renderRays(poseStack, progress, buffers.getBuffer(RenderType.dragonRaysDepth()));
			poseStack.popPose();
		}

		if (dragon.nearestCrystal != null) {
			poseStack.pushPose();
			float dx = (float) (dragon.nearestCrystal.getX() - Mth.lerp(partialTick, dragon.xo, dragon.getX()));
			float dy = (float) (dragon.nearestCrystal.getY() - Mth.lerp(partialTick, dragon.yo, dragon.getY()));
			float dz = (float) (dragon.nearestCrystal.getZ() - Mth.lerp(partialTick, dragon.zo, dragon.getZ()));
			EnderDragonRenderer.renderCrystalBeams(dx, dy + EndCrystalRenderer.getY(dragon.nearestCrystal, partialTick), dz,
					partialTick, dragon.tickCount, poseStack, buffers, packedLight);
			poseStack.popPose();
		}
	}

	@Override
	public boolean shouldRender(EnderDragon dragon, Frustum frustum, double camX, double camY, double camZ) {
		if (!dragon.shouldRender(camX, camY, camZ)) return false;
		return frustum.isVisible(dragon.getBoundingBoxForCulling().inflate(CULL_MARGIN));
	}

	/** The vanilla death rays: light bursting out of the dragon while it dies. */
	private static void renderRays(PoseStack poseStack, float progress, VertexConsumer consumer) {
		poseStack.pushPose();
		float fade = Math.min(progress > 0.8F ? (progress - 0.8F) / 0.2F : 0.0F, 1.0F);
		int center = FastColor.ARGB32.colorFromFloat(1.0F - fade, 1.0F, 1.0F, 1.0F);
		int edge = 0xFF00FF;
		RandomSource random = RandomSource.create(432L);
		Vector3f origin = new Vector3f(), a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
		Quaternionf rotation = new Quaternionf();
		int count = Mth.floor((progress + progress * progress) / 2.0F * 60.0F);
		for (int i = 0; i < count; i++) {
			rotation.rotationXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI)
					.rotateXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI,
							random.nextFloat() * Mth.TWO_PI + progress * Mth.HALF_PI);
			poseStack.mulPose(rotation);
			float length = random.nextFloat() * 20.0F + 5.0F + fade * 10.0F;
			float width = random.nextFloat() * 2.0F + 1.0F + fade * 2.0F;
			a.set(-HALF_SQRT_3 * width, length, -0.5F * width);
			b.set(HALF_SQRT_3 * width, length, -0.5F * width);
			c.set(0.0F, length, width);
			PoseStack.Pose pose = poseStack.last();
			consumer.addVertex(pose, origin).setColor(center);
			consumer.addVertex(pose, a).setColor(edge);
			consumer.addVertex(pose, b).setColor(edge);
			consumer.addVertex(pose, origin).setColor(center);
			consumer.addVertex(pose, b).setColor(edge);
			consumer.addVertex(pose, c).setColor(edge);
			consumer.addVertex(pose, origin).setColor(center);
			consumer.addVertex(pose, c).setColor(edge);
			consumer.addVertex(pose, a).setColor(edge);
		}
		poseStack.popPose();
	}
}
