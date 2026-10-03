package crazylimits.dragonsworn.fabric;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.client.VoidFlameParticle;
import crazylimits.dragonsworn.mc.client.DragonRenderer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/** Client registrations whose Fabric API moved between Minecraft versions (this one: 1.21.11). */
final class FabricClientParts {
	private FabricClientParts() {
	}

	/** Replaces the vanilla renderer: the entity itself stays minecraft:ender_dragon. */
	static void renderer() {
		EntityRendererRegistry.register(EntityType.ENDER_DRAGON, DragonRenderer::new);
	}

	static void particles() {
		ParticleFactoryRegistry.getInstance().register(BreathParticles.VOID_BREATH, VoidFlameParticle.Breath::new);
		ParticleFactoryRegistry.getInstance().register(BreathParticles.VOID_FLAME, VoidFlameParticle.Cloud::new);
	}

	/** Draws {@code block} cut out (NeoForge reads the cutout from the block models' render_type). */
	static void cutout(Block block) {
		BlockRenderLayerMap.putBlock(block, ChunkSectionLayer.CUTOUT);
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return ClientCommandManager.literal(name);
	}

	static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
		return ClientCommandManager.argument(name, type);
	}
}
