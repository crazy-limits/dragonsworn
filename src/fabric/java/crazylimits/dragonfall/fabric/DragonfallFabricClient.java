package crazylimits.dragonfall.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import crazylimits.dragonfall.anim.DragonCommand;
import crazylimits.dragonfall.mc.breath.BreathParticles;
import crazylimits.dragonfall.mc.breath.client.VoidFlameParticle;
import crazylimits.dragonfall.mc.client.DragonRenderer;
import crazylimits.dragonfall.mc.client.Showcase;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

public final class DragonfallFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Replaces the vanilla renderer: the entity itself stays minecraft:ender_dragon.
		EntityRendererRegistry.register(EntityType.ENDER_DRAGON, DragonRenderer::new);
		ParticleFactoryRegistry.getInstance().register(BreathParticles.VOID_BREATH, VoidFlameParticle.Breath::new);
		ParticleFactoryRegistry.getInstance().register(BreathParticles.VOID_FLAME, VoidFlameParticle.Cloud::new);
		if (Showcase.ENABLED) ClientTickEvents.END_CLIENT_TICK.register(Showcase::tick);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
				ClientCommandManager.literal("dragonfall").then(ClientCommandManager.literal("anim")
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
