package crazylimits.dragonsworn.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import crazylimits.dragonsworn.mc.client.config.ConfigScreens;

/** Mod Menu's config button: YACL's screen, Cloth Config's, or the plain one ({@link ConfigScreens}). */
public final class DragonswornModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreens::create;
	}
}
