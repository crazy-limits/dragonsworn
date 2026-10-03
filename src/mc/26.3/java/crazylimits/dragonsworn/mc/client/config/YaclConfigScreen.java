package crazylimits.dragonsworn.mc.client.config;

import crazylimits.dragonsworn.config.DragonConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The config screen built with YACL (only loaded when YACL is installed: {@link ConfigScreens}). */
final class YaclConfigScreen {
	private YaclConfigScreen() {}

	static Screen create(Screen parent) {
		YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder().title(ConfigScreens.title()).save(ConfigScreens::save);
		boolean first = true;
		for (ConfigScreens.Category c : ConfigScreens.categories()) {
			ConfigCategory.Builder category = ConfigCategory.createBuilder()
					.name(c.label()).tooltip(c.heading());
			if (first && ConfigScreens.remote()) category.option(LabelOption.create(ConfigScreens.remoteNote()));
			first = false;
			category.option(LabelOption.create(c.heading()));
			for (DragonConfig.Option o : c.main().options()) category.option(option(o));
			for (ConfigScreens.Group g : c.groups()) {
				OptionGroup.Builder group = OptionGroup.createBuilder()
						.name(g.label()).description(OptionDescription.of(g.heading()));
				for (DragonConfig.Option o : g.options()) group.option(option(o));
				category.group(group.build());
			}
			builder.category(category.build());
		}
		return builder.build().generateScreen(parent);
	}

	private static Option<?> option(DragonConfig.Option o) {
		Component name = ConfigScreens.label(o);
		OptionDescription description = OptionDescription.of(ConfigScreens.tooltip(o));
		if (o instanceof DragonConfig.Flag f) {
			return Option.<Boolean>createBuilder().name(name).description(description)
					.binding(f.defaultValue(), f::get, f::set).controller(TickBoxControllerBuilder::create).build();
		}
		if (o instanceof DragonConfig.Int i) {
			return Option.<Integer>createBuilder().name(name).description(description)
					.binding(i.defaultValue(), i::get, i::set)
					.controller(opt -> IntegerFieldControllerBuilder.create(opt).range(i.min(), i.max())).build();
		}
		DragonConfig.Num n = (DragonConfig.Num) o;
		return Option.<Double>createBuilder().name(name).description(description)
				.binding(n.defaultValue(), n::get, n::set)
				.controller(opt -> DoubleFieldControllerBuilder.create(opt).range(n.min(), n.max())).build();
	}
}
