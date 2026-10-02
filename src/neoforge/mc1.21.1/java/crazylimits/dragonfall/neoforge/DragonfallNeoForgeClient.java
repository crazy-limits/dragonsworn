package crazylimits.dragonfall.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import crazylimits.dragonfall.Dragonfall;
import crazylimits.dragonfall.anim.DragonCommand;
import crazylimits.dragonfall.mc.breath.BreathParticles;
import crazylimits.dragonfall.mc.breath.client.VoidFlameParticle;
import crazylimits.dragonfall.mc.client.ArenaTour;
import crazylimits.dragonfall.mc.client.DragonRenderer;
import crazylimits.dragonfall.mc.client.Showcase;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class DragonfallNeoForgeClient {
	private DragonfallNeoForgeClient() {}

	@EventBusSubscriber(modid = Dragonfall.MOD_ID, value = Dist.CLIENT)
	public static final class ModEvents {
		@SubscribeEvent
		public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
			// Replaces the vanilla renderer: the entity itself stays minecraft:ender_dragon.
			event.registerEntityRenderer(EntityType.ENDER_DRAGON, DragonRenderer::new);
		}

		@SubscribeEvent
		public static void registerParticles(RegisterParticleProvidersEvent event) {
			event.registerSpriteSet(BreathParticles.VOID_BREATH, VoidFlameParticle.Breath::new);
			event.registerSpriteSet(BreathParticles.VOID_FLAME, VoidFlameParticle.Cloud::new);
		}
	}

	@EventBusSubscriber(modid = Dragonfall.MOD_ID, value = Dist.CLIENT)
	public static final class GameEvents {
		@SubscribeEvent
		public static void tick(ClientTickEvent.Post event) {
			if (Showcase.ENABLED) Showcase.tick(Minecraft.getInstance());
			if (ArenaTour.ENABLED) ArenaTour.tick(Minecraft.getInstance());
		}

		@SubscribeEvent
		public static void registerCommands(RegisterClientCommandsEvent event) {
			event.getDispatcher().register(Commands.literal("dragonfall").then(Commands.literal("anim")
					.then(Commands.argument("name", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								DragonCommand.choices().forEach(builder::suggest);
								return builder.buildFuture();
							})
							.executes(ctx -> {
								ctx.getSource().sendSystemMessage(Component.literal(
										DragonCommand.run(StringArgumentType.getString(ctx, "name"))));
								return 1;
							}))));
		}
	}
}
