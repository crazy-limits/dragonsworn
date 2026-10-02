package crazylimits.dragonsworn.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import crazylimits.dragonsworn.anim.DragonCommand;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import crazylimits.dragonsworn.mc.breath.client.VoidFlameParticle;
import crazylimits.dragonsworn.mc.client.ArenaTour;
import crazylimits.dragonsworn.mc.client.DragonRenderer;
import crazylimits.dragonsworn.mc.client.Showcase;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

public final class DragonswornFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Replaces the vanilla renderer: the entity itself stays minecraft:ender_dragon.
		EntityRendererRegistry.register(EntityType.ENDER_DRAGON, DragonRenderer::new);
		ParticleFactoryRegistry.getInstance().register(BreathParticles.VOID_BREATH, VoidFlameParticle.Breath::new);
		ParticleFactoryRegistry.getInstance().register(BreathParticles.VOID_FLAME, VoidFlameParticle.Cloud::new);
		// NeoForge reads the cutout from the block models' render_type
		BlockRenderLayerMap.INSTANCE.putBlock(DragonFire.BLOCK, RenderType.cutout());
		if (Showcase.ENABLED) ClientTickEvents.END_CLIENT_TICK.register(Showcase::tick);
		if (ArenaTour.ENABLED) ClientTickEvents.END_CLIENT_TICK.register(ArenaTour::tick);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
				ClientCommandManager.literal("dragonsworn").then(ClientCommandManager.literal("anim")
						.then(ClientCommandManager.argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									DragonCommand.choices().forEach(builder::suggest);
									return builder.buildFuture();
								})
								.executes(ctx -> {
									ctx.getSource().sendFeedback(Component.literal(
											DragonCommand.run(StringArgumentType.getString(ctx, "name"))));
									return 1;
								})))));
	}
}
