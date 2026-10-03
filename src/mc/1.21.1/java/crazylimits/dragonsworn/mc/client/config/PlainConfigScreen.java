package crazylimits.dragonsworn.mc.client.config;

import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.config.Toml;
import crazylimits.dragonsworn.mc.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dragonsworn's own config screen, from vanilla widgets alone: what Mod Menu and the NeoForge mods list
 * open when neither YACL nor Cloth Config is installed. One scrolling list of every table's options (a
 * tick box or a number field each, the comment as its tooltip); Save checks every number before it
 * applies and writes them, a field that does not parse turns red.
 */
public final class PlainConfigScreen extends Screen {
	private static final int ROW_WIDTH = 340, FIELD_WIDTH = 90, ERROR = 0xFF5555, TEXT = 0xE0E0E0;

	private final Screen parent;
	/** The values being edited, kept across a resize: text for numbers, Boolean for flags. */
	private final Map<DragonConfig.Option, Object> pending = new LinkedHashMap<>();
	private final Map<DragonConfig.Option, EditBox> fields = new LinkedHashMap<>();

	public PlainConfigScreen(Screen parent) {
		super(ConfigScreens.title());
		this.parent = parent;
		for (DragonConfig.Option o : DragonConfig.options()) pending.put(o, current(o));
	}

	private static Object current(DragonConfig.Option o) {
		if (o instanceof DragonConfig.Flag f) return f.get();
		if (o instanceof DragonConfig.Int i) return Integer.toString(i.get());
		return Toml.format(((DragonConfig.Num) o).get());
	}

	private static Object fallback(DragonConfig.Option o) {
		if (o instanceof DragonConfig.Flag f) return f.defaultValue();
		if (o instanceof DragonConfig.Int i) return Integer.toString(i.defaultValue());
		return Toml.format(((DragonConfig.Num) o).defaultValue());
	}

	@Override
	protected void init() {
		fields.clear();
		OptionList list = new OptionList(minecraft, width, height - 64, 32);
		if (ConfigScreens.remote()) list.add(new Heading(ConfigScreens.remoteNote(), null));
		for (String name : DragonConfig.sections().keySet()) {
			MutableComponent label = Lang.of(DragonConfig.sectionLabel(name));
			if (name.contains(".")) {
				label = Lang.of(DragonConfig.sectionLabel(name.substring(0, name.indexOf('.')))).append(": ").append(label);
			}
			list.add(new Heading(label.withStyle(s -> s.withBold(true)), Lang.of(DragonConfig.sectionHeading(name))));
			for (DragonConfig.Option o : DragonConfig.options()) {
				if (o.section().equals(name)) list.add(new Row(o, widget(o)));
			}
		}
		addRenderableWidget(list);
		int y = height - 27, w = 100;
		addRenderableWidget(Button.builder(Component.translatableWithFallback("dragonsworn.config.reset", "Reset to defaults"), b -> {
			for (DragonConfig.Option o : DragonConfig.options()) pending.put(o, fallback(o));
			rebuildWidgets();
		}).bounds(width / 2 - w * 3 / 2 - 6, y, w, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 - w / 2, y, w, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save()).bounds(width / 2 + w / 2 + 6, y, w, 20).build());
	}

	private AbstractWidget widget(DragonConfig.Option o) {
		Tooltip tooltip = Tooltip.create(ConfigScreens.tooltip(o));
		if (o instanceof DragonConfig.Flag) {
			CycleButton<Boolean> button = CycleButton.onOffBuilder((Boolean) pending.get(o)).displayOnlyValue()
					.create(0, 0, FIELD_WIDTH, 20, ConfigScreens.label(o), (b, value) -> pending.put(o, value));
			button.setTooltip(tooltip);
			return button;
		}
		EditBox field = new EditBox(font, 0, 0, FIELD_WIDTH, 18, ConfigScreens.label(o));
		field.setMaxLength(24);
		field.setValue((String) pending.get(o));
		// not by colour alone: a value that does not parse also says so in the tooltip (and the narrator reads it)
		Tooltip invalid = Tooltip.create(Component.translatableWithFallback(o instanceof DragonConfig.Int ? "dragonsworn.config.invalid.whole"
				: "dragonsworn.config.invalid.number", o instanceof DragonConfig.Int ? "Not a whole number" : "Not a number")
				.withStyle(s -> s.withColor(ERROR)).append("\n").append(ConfigScreens.tooltip(o)));
		field.setResponder(text -> {
			pending.put(o, text);
			boolean valid = parses(o, text);
			field.setTextColor(valid ? TEXT : ERROR);
			field.setTooltip(valid ? tooltip : invalid);
		});
		boolean valid = parses(o, field.getValue());
		field.setTextColor(valid ? TEXT : ERROR);
		field.setTooltip(valid ? tooltip : invalid);
		fields.put(o, field);
		return field;
	}

	private static boolean parses(DragonConfig.Option o, String text) {
		try {
			if (o instanceof DragonConfig.Int) Integer.parseInt(text.strip());
			else Double.parseDouble(text.strip());
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** Applies and writes every value, unless a number does not parse (it stays red, the screen open). */
	private void save() {
		for (Map.Entry<DragonConfig.Option, Object> e : pending.entrySet()) {
			if (e.getValue() instanceof String text && !parses(e.getKey(), text)) {
				EditBox field = fields.get(e.getKey());
				if (field != null) setFocused(field);
				return;
			}
		}
		for (Map.Entry<DragonConfig.Option, Object> e : pending.entrySet()) {
			DragonConfig.Option o = e.getKey();
			if (o instanceof DragonConfig.Flag f) f.set((Boolean) e.getValue());
			else if (o instanceof DragonConfig.Int i) i.set(Integer.parseInt(((String) e.getValue()).strip()));
			else ((DragonConfig.Num) o).set(Double.parseDouble(((String) e.getValue()).strip()));
		}
		ConfigScreens.save();
		onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	// ---------------------------------------------------------------- the list

	private static final class OptionList extends ContainerObjectSelectionList<Line> {
		OptionList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, 24);
		}

		void add(Line entry) {
			addEntry(entry);
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}
	}

	private abstract static class Line extends ContainerObjectSelectionList.Entry<Line> {}

	/** A table's name (its heading as the tooltip), or a note. */
	private final class Heading extends Line {
		private final Component text, heading;

		Heading(Component text, Component heading) {
			this.text = text;
			this.heading = heading;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
				boolean hovering, float partialTick) {
			Font font = PlainConfigScreen.this.font;
			String line = text.getString(), shown = font.plainSubstrByWidth(line, width);
			graphics.drawString(font, shown.equals(line) ? text : Component.literal(font.plainSubstrByWidth(line, width - 12) + "..."), left, top + 8, 0xFFD27F);
			if (hovering && heading != null) PlainConfigScreen.this.setTooltipForNextRenderPass(font.split(heading, 240));
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}

	/** An option: its name, and its widget at the right. */
	private final class Row extends Line {
		private final Component label;
		private final AbstractWidget widget;

		Row(DragonConfig.Option option, AbstractWidget widget) {
			this.label = ConfigScreens.label(option);
			this.widget = widget;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
				boolean hovering, float partialTick) {
			Font font = PlainConfigScreen.this.font;
			// a long (translated) name is cut short of the widget, and shown in full when hovered
			int room = width - FIELD_WIDTH - 10;
			String line = label.getString();
			boolean cut = font.width(line) > room;
			graphics.drawString(font, cut ? font.plainSubstrByWidth(line, room - font.width("...")) + "..." : line, left + 4, top + 6, TEXT);
			if (cut && hovering && mouseX < left + room) PlainConfigScreen.this.setTooltipForNextRenderPass(font.split(label, 240));
			widget.setX(left + width - FIELD_WIDTH - 2);
			widget.setY(top + 1);
			widget.render(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(widget);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(widget);
		}
	}
}
