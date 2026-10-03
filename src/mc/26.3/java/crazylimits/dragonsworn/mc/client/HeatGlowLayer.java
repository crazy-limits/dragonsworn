package crazylimits.dragonsworn.mc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import crazylimits.dragonsworn.Dragonsworn;
import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.attack.BreathPass;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;

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
 *
 * <p>Assets: the heat frames are baked from the dragon texture, which derives from the "Ender Dragon Reborn"
 * resource pack by Parrie43, All Rights Reserved. The LGPL does not cover them: see LICENSE-ASSETS.md.
 */
final class HeatGlowLayer extends GeoRenderLayer<ReplacedEnderDragon, EnderDragon, DragonRenderer.State> {
	/** {@code tools/heat.py} FRAMES. */
	static final int FRAMES = 8;
	private static final RenderType[] GLOW = new RenderType[FRAMES + 1];

	static {
		for (int k = 1; k <= FRAMES; k++) {
			GLOW[k] = RenderTypes.eyes(Identifier.fromNamespaceAndPath(Dragonsworn.MOD_ID, "textures/entity/heat/ender_dragon_heat_" + k + ".png"));
		}
	}

	HeatGlowLayer(DragonRenderer renderer) {
		super(renderer);
	}

	@Override
	public void addRenderData(ReplacedEnderDragon animatable, EnderDragon dragon, DragonRenderer.State state, float partialTick) {
		state.heatBrightness = 0.0;
		state.heat = 0.0;
		if (dragon == null || dragon.isInvisible()) return;
		double pass = BreathPassPhase.breathTicks(dragon, partialTick);
		if (dragon.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase phase) {
			double tick = phase.ticks() + partialTick;
			state.heatBrightness = BreathAttack.heatBrightness(tick);
			state.heat = BreathAttack.heat(tick);
		} else if (!Double.isNaN(pass)) {
			state.heatBrightness = BreathPass.heatBrightness(pass);
			state.heat = BreathPass.heat(pass);
		} else {
			double tick = DragonswornDragon.brain(dragon).fireballs.glowTicks(partialTick);
			if (tick > BreathAttack.FIREBALL_WINDUP_TICKS + BreathAttack.FIREBALL_COOL_TICKS) return;
			state.heatBrightness = BreathAttack.fireballHeatBrightness(tick);
			state.heat = BreathAttack.fireballHeat(tick);
		}
	}

	@Override
	public void submitRenderTask(RenderPassInfo<DragonRenderer.State> renderPassInfo, SubmitNodeCollector renderTasks) {
		if (!renderPassInfo.willRender()) return;
		DragonRenderer.State state = renderPassInfo.renderState();
		double k = state.heat * FRAMES;
		int lo = (int) Math.floor(k);
		double f = k - lo;
		submit(lo, (1.0 - f) * state.heatBrightness, renderPassInfo, renderTasks);
		submit(lo + 1, f * state.heatBrightness, renderPassInfo, renderTasks);
	}

	/** Draws heat frame {@code frame} at {@code weight} (frame 0 is dark): the model again, posed as drawn. */
	private static void submit(int frame, double weight, RenderPassInfo<DragonRenderer.State> renderPassInfo, SubmitNodeCollector renderTasks) {
		if (frame < 1 || frame > FRAMES || weight < 1.0 / 255.0) return;
		// additive: the vertex color scales the glow
		int c = (int) Math.round(Math.min(1.0, weight) * 255.0);
		int color = 0xFF000000 | c << 16 | c << 8 | c;
		int overlay = renderPassInfo.packedOverlay();
		renderTasks.order(1).submitCustomGeometry(renderPassInfo.poseStack(), GLOW[frame], (pose, consumer) -> {
			PoseStack poseStack = renderPassInfo.poseStack();
			poseStack.pushPose();
			poseStack.last().set(pose);
			renderPassInfo.renderPosed(() -> renderPassInfo.model().render(renderPassInfo, consumer, LightCoordsUtil.FULL_BRIGHT, overlay, color));
			poseStack.popPose();
		});
	}
}
