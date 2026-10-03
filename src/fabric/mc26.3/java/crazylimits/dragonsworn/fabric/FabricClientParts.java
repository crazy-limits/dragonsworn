package crazylimits.dragonsworn.fabric;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.client.VoidFlameParticle;
import crazylimits.dragonsworn.mc.client.DragonRenderer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;

/** Client registrations whose Fabric API moved between Minecraft versions (this one: 26.3). */
final class FabricClientParts {
	private FabricClientParts() {
	}

	/** Replaces the vanilla renderer: the entity itself stays minecraft:ender_dragon. */
	static void renderer() {
		EntityRendererRegistry.register(EntityTypes.ENDER_DRAGON, DragonRenderer::new);
	}

	static void particles() {
		ParticleProviderRegistry.getInstance().register(BreathParticles.VOID_BREATH, VoidFlameParticle.Breath::new);
		ParticleProviderRegistry.getInstance().register(BreathParticles.VOID_FLAME, VoidFlameParticle.Cloud::new);
	}

	/** Draws {@code block} cut out. */
	static void cutout(Block block) {
		// nothing to do: 26.x draws each quad in the layer its texture's transparency asks for
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return ClientCommands.literal(name);
	}

	static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
		return ClientCommands.argument(name, type);
	}
}
