package crazylimits.dragonsworn.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import crazylimits.dragonsworn.Dragonsworn;
import crazylimits.dragonsworn.debug.DragonCommand;
import crazylimits.dragonsworn.mc.Lang;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.client.VoidFlameParticle;
import crazylimits.dragonsworn.mc.client.DragonRenderer;
import crazylimits.dragonsworn.mc.client.config.ConfigScreens;
import crazylimits.dragonsworn.mc.client.showcase.ArenaTour;
import crazylimits.dragonsworn.mc.client.showcase.Film;
import crazylimits.dragonsworn.mc.client.showcase.Showcase;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class DragonswornNeoForgeClient {
	private DragonswornNeoForgeClient() {}

	/** The mods list's config button: YACL's screen, Cloth Config's, or the plain one ({@link ConfigScreens}). */
	public static void registerConfigScreen(ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> ConfigScreens.create(parent));
	}

	@EventBusSubscriber(modid = Dragonsworn.MOD_ID, value = Dist.CLIENT)
	public static final class ModEvents {
		@SubscribeEvent
		public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
			// Replaces the vanilla renderer: the entity itself stays minecraft:ender_dragon.
			event.registerEntityRenderer(EntityTypes.ENDER_DRAGON, DragonRenderer::new);
		}

		@SubscribeEvent
		public static void registerParticles(RegisterParticleProvidersEvent event) {
			event.registerSpriteSet(BreathParticles.VOID_BREATH, VoidFlameParticle.Breath::new);
			event.registerSpriteSet(BreathParticles.VOID_FLAME, VoidFlameParticle.Cloud::new);
		}
	}

	@EventBusSubscriber(modid = Dragonsworn.MOD_ID, value = Dist.CLIENT)
	public static final class GameEvents {
		@SubscribeEvent
		public static void tick(ClientTickEvent.Post event) {
			if (Showcase.ENABLED) Showcase.tick(Minecraft.getInstance());
			if (ArenaTour.ENABLED) ArenaTour.tick(Minecraft.getInstance());
			if (Film.ENABLED) Film.tick(Minecraft.getInstance());
		}

		@SubscribeEvent
		public static void registerCommands(RegisterClientCommandsEvent event) {
			event.getDispatcher().register(Commands.literal("dragonsworn").then(Commands.literal("anim")
					.then(Commands.argument("name", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								DragonCommand.choices().forEach(builder::suggest);
								return builder.buildFuture();
							})
							.executes(ctx -> {
								ctx.getSource().sendSystemMessage(Lang.of(
										DragonCommand.run(StringArgumentType.getString(ctx, "name"))));
								return 1;
							}))));
		}
	}
}
