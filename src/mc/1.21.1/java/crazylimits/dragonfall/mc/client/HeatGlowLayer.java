package crazylimits.dragonfall.mc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import crazylimits.dragonfall.Dragonfall;
import crazylimits.dragonfall.anim.BreathAttack;
import crazylimits.dragonfall.anim.BreathPass;
import crazylimits.dragonfall.mc.DragonfallDragon;
import crazylimits.dragonfall.mc.breath.BreathStreamPhase;
import crazylimits.dragonfall.mc.phase.BreathPassPhase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The stream breath's heat: while the dragon inhales its chest starts to glow, the glow climbs the
 * underside of the neck, lights the jaw and the mouth, and then the fire comes; it flickers while the
 * flames pour and cools off after ({@link BreathStreamPhase}; the breath pass's shorter inhale, {@link BreathPass},
 * plays it faster). Before a fireball it plays three times
 * faster and cools off after the shot ({@link BreathAttack#fireballHeat}).
 *
 * <p>A texture animation: {@code tools/heat.py} bakes the glow at {@link #FRAMES} steps of the inhale
 * ({@link BreathAttack#heat}), and the dragon is drawn again, emissive and additive, with the two frames
 * around the current heat, weighted, so every texel brightens smoothly between them.
 */
final class HeatGlowLayer extends GeoRenderLayer<ReplacedEnderDragon> {
	/** {@code tools/heat.py} FRAMES. */
	static final int FRAMES = 8;
	private static final RenderType[] GLOW = new RenderType[FRAMES + 1];

	static {
		for (int k = 1; k <= FRAMES; k++) {
			GLOW[k] = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(Dragonfall.MOD_ID, "textures/entity/heat/ender_dragon_heat_" + k + ".png"));
		}
	}

	private final DragonRenderer dragonRenderer;

	HeatGlowLayer(DragonRenderer renderer) {
		super(renderer);
		this.dragonRenderer = renderer;
	}

	@Override
	public void render(PoseStack poseStack, ReplacedEnderDragon animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
			MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		EnderDragon dragon = dragonRenderer.entity();
		if (dragon == null || dragon.isInvisible()) return;
		double brightness, heat;
		double pass = BreathPassPhase.breathTicks(dragon, partialTick);
		if (dragon.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase phase) {
			double tick = phase.ticks() + partialTick;
			brightness = BreathAttack.heatBrightness(tick);
			heat = BreathAttack.heat(tick);
		} else if (!Double.isNaN(pass)) {
			brightness = BreathPass.heatBrightness(pass);
			heat = BreathPass.heat(pass);
		} else {
			double tick = DragonfallDragon.brain(dragon).fireballGlowTicks(partialTick);
			if (tick > BreathAttack.FIREBALL_WINDUP_TICKS + BreathAttack.FIREBALL_COOL_TICKS) return;
			brightness = BreathAttack.fireballHeatBrightness(tick);
			heat = BreathAttack.fireballHeat(tick);
		}
		double k = heat * FRAMES;
		int lo = (int) Math.floor(k);
		double f = k - lo;
		draw(lo, (1.0 - f) * brightness, poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
		draw(lo + 1, f * brightness, poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
	}

	/** Draws heat frame {@code frame} at {@code weight} (frame 0 is dark). */
	private void draw(int frame, double weight, PoseStack poseStack, ReplacedEnderDragon animatable, BakedGeoModel bakedModel,
			MultiBufferSource bufferSource, float partialTick, int packedOverlay) {
		if (frame < 1 || frame > FRAMES || weight < 1.0 / 255.0) return;
		// additive: the vertex color scales the glow
		int c = (int) Math.round(Math.min(1.0, weight) * 255.0);
		int color = 0xFF000000 | c << 16 | c << 8 | c;
		RenderType type = GLOW[frame];
		getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
				LightTexture.FULL_BRIGHT, packedOverlay, color);
	}
}
