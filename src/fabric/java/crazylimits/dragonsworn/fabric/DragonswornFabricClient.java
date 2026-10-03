package crazylimits.dragonsworn.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import crazylimits.dragonsworn.debug.DragonCommand;
import crazylimits.dragonsworn.mc.Lang;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import crazylimits.dragonsworn.mc.client.showcase.ArenaTour;
import crazylimits.dragonsworn.mc.client.showcase.Film;
import crazylimits.dragonsworn.mc.client.showcase.Showcase;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class DragonswornFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FabricClientParts.renderer();
		FabricClientParts.particles();
		FabricClientParts.cutout(DragonFire.BLOCK);
		if (Showcase.ENABLED) ClientTickEvents.END_CLIENT_TICK.register(Showcase::tick);
		if (ArenaTour.ENABLED) ClientTickEvents.END_CLIENT_TICK.register(ArenaTour::tick);
		if (Film.ENABLED) ClientTickEvents.END_CLIENT_TICK.register(Film::tick);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
				FabricClientParts.literal("dragonsworn").then(FabricClientParts.literal("anim")
						.then(FabricClientParts.argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> {
									DragonCommand.choices().forEach(builder::suggest);
									return builder.buildFuture();
								})
								.executes(ctx -> {
									ctx.getSource().sendFeedback(Lang.of(
											DragonCommand.run(StringArgumentType.getString(ctx, "name"))));
									return 1;
								})))));
	}
}
