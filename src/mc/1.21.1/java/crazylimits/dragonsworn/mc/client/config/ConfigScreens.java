package crazylimits.dragonsworn.mc.client.config;

import com.mojang.logging.LogUtils;
import crazylimits.dragonsworn.config.DragonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The config screen for Mod Menu (Fabric) and the mods list (NeoForge): built with YACL when it is
 * installed, else Cloth Config, else Dragonsworn's own plain screen ({@link PlainConfigScreen}). Every one
 * is generated from {@link DragonConfig}'s options, so nothing is listed twice. Values apply at once (in
 * single player the integrated server reads the same statics) and are saved to the file; on a dedicated
 * server only the server's own file counts.
 */
public final class ConfigScreens {
	/** Which library builds the screen; {@code -Ddragonsworn.configScreen=plain|cloth|yacl} forces one (testing). */
	public enum Library { YACL, CLOTH, PLAIN }

	private ConfigScreens() {}

	public static Screen create(Screen parent) {
		return switch (library()) {
			case YACL -> YaclConfigScreen.create(parent);
			case CLOTH -> ClothConfigScreen.create(parent);
			case PLAIN -> new PlainConfigScreen(parent);
		};
	}

	public static Library library() {
		String forced = System.getProperty("dragonsworn.configScreen");
		if (forced != null) {
			for (Library l : Library.values()) if (l.name().equalsIgnoreCase(forced) && available(l)) return l;
		}
		if (available(Library.YACL)) return Library.YACL;
		if (available(Library.CLOTH)) return Library.CLOTH;
		return Library.PLAIN;
	}

	private static boolean available(Library library) {
		return switch (library) {
			case YACL -> present("dev.isxander.yacl3.api.YetAnotherConfigLib");
			case CLOTH -> present("me.shedaniel.clothconfig2.api.ConfigBuilder");
			case PLAIN -> true;
		};
	}

	private static boolean present(String className) {
		try {
			Class.forName(className, false, ConfigScreens.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	// ---------------------------------------------------------------- shared by the builders

	/** The screen's title. */
	static Component title() {
		return Component.literal("Dragonsworn: dragon AI");
	}

	/** A note when connected to a server whose own config is what counts. */
	static Component remoteNote() {
		return Component.literal("You are on a server: these are your local settings; the server's own config file decides its dragons.");
	}

	static boolean remote() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getConnection() != null && !mc.hasSingleplayerServer();
	}

	/** The top-level tables, each with its options and its sub-tables ({@code attacks} -> {@code attacks.wall}, ...). */
	static List<Category> categories() {
		List<Category> categories = new ArrayList<>();
		for (Map.Entry<String, String> section : DragonConfig.sections().entrySet()) {
			String name = section.getKey();
			int dot = name.indexOf('.');
			Group group = new Group(name, DragonConfig.label(name), section.getValue(), options(name));
			if (dot < 0) {
				categories.add(new Category(name, DragonConfig.label(name), section.getValue(), group, new ArrayList<>()));
			} else {
				String parent = name.substring(0, dot);
				for (Category c : categories) if (c.name.equals(parent)) c.groups.add(group);
			}
		}
		return categories;
	}

	private static List<DragonConfig.Option> options(String section) {
		List<DragonConfig.Option> list = new ArrayList<>();
		for (DragonConfig.Option o : DragonConfig.options()) if (o.section().equals(section)) list.add(o);
		return list;
	}

	/** A tooltip: what the option does, then its range and default. */
	static Component tooltip(DragonConfig.Option option) {
		return Component.literal(option.comment() + "\n" + option.describeRange());
	}

	/** Writes the file (a screen's save). */
	static void save() {
		try {
			DragonConfig.save();
		} catch (IOException e) {
			LogUtils.getLogger().warn("[Dragonsworn] could not save the config", e);
		}
	}

	/** A top-level table: its own options ({@code main}) and its sub-tables. */
	record Category(String name, String label, String heading, Group main, List<Group> groups) {}

	record Group(String name, String label, String heading, List<DragonConfig.Option> options) {}
}
