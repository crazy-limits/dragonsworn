package crazylimits.dragonsworn.debug;

import crazylimits.dragonsworn.Text;
import crazylimits.dragonsworn.anim.DragonAnim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What {@code /dragonsworn anim <name|auto>} does; the loaders only wire it into Brigadier. */
public final class DragonCommand {
	public static final String AUTO = "auto";

	private DragonCommand() {}

	public static List<String> choices() {
		List<String> out = new ArrayList<>();
		out.add(AUTO);
		for (DragonAnim anim : DragonAnim.values()) out.add(anim.name().toLowerCase(Locale.ROOT));
		return out;
	}

	/** Applies the choice and returns the feedback line. */
	public static Text run(String choice) {
		if (AUTO.equalsIgnoreCase(choice)) {
			DragonDebug.forcedAnimation = null;
			return new Text("commands.dragonsworn.anim.auto", "Dragons follow their phase again.");
		}
		DragonAnim anim = DragonAnim.byName(choice);
		if (anim == null) {
			return new Text("commands.dragonsworn.anim.unknown", "Unknown animation '%s'. Try: %s", choice, String.join(", ", choices()));
		}
		DragonDebug.forcedAnimation = anim;
		return new Text("commands.dragonsworn.anim.forced", "Every dragon now loops '%s'. '/dragonsworn anim auto' to undo.",
				anim.name().toLowerCase(Locale.ROOT));
	}
}
