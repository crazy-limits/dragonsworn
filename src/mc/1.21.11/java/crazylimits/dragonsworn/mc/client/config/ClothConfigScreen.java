package crazylimits.dragonsworn.mc.client.config;

import crazylimits.dragonsworn.config.DragonConfig;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The config screen built with Cloth Config (only loaded when it is installed: {@link ConfigScreens}). */
final class ClothConfigScreen {
	private ClothConfigScreen() {}

	static Screen create(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(ConfigScreens.title())
				.setSavingRunnable(ConfigScreens::save);
		ConfigEntryBuilder entries = builder.entryBuilder();
		boolean first = true;
		for (ConfigScreens.Category c : ConfigScreens.categories()) {
			ConfigCategory category = builder.getOrCreateCategory(c.label());
			if (first && ConfigScreens.remote()) category.addEntry(entries.startTextDescription(ConfigScreens.remoteNote()).build());
			first = false;
			category.addEntry(entries.startTextDescription(c.heading()).build());
			for (DragonConfig.Option o : c.main().options()) category.addEntry(entry(entries, o));
			for (ConfigScreens.Group g : c.groups()) {
				SubCategoryBuilder sub = entries.startSubCategory(g.label()).setTooltip(g.heading());
				for (DragonConfig.Option o : g.options()) sub.add(entry(entries, o));
				category.addEntry(sub.build());
			}
		}
		return builder.build();
	}

	private static AbstractConfigListEntry<?> entry(ConfigEntryBuilder entries, DragonConfig.Option o) {
		Component name = ConfigScreens.label(o), tooltip = ConfigScreens.tooltip(o);
		if (o instanceof DragonConfig.Flag f) {
			return entries.startBooleanToggle(name, f.get()).setDefaultValue(f.defaultValue()).setTooltip(tooltip).setSaveConsumer(f::set).build();
		}
		if (o instanceof DragonConfig.Int i) {
			return entries.startIntField(name, i.get()).setDefaultValue(i.defaultValue()).setMin(i.min()).setMax(i.max())
					.setTooltip(tooltip).setSaveConsumer(i::set).build();
		}
		DragonConfig.Num n = (DragonConfig.Num) o;
		return entries.startDoubleField(name, n.get()).setDefaultValue(n.defaultValue()).setMin(n.min()).setMax(n.max())
				.setTooltip(tooltip).setSaveConsumer(n::set).build();
	}
}
