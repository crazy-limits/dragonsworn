package crazylimits.dragonfall.anim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What {@code /dragonfall anim <name|auto>} does; the loaders only wire it into Brigadier. */
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
	public static String run(String choice) {
		if (AUTO.equalsIgnoreCase(choice)) {
			DragonDebug.forcedAnimation = null;
			return "Dragons follow their phase again.";
		}
		DragonAnim anim = DragonAnim.byName(choice);
		if (anim == null) return "Unknown animation '" + choice + "'. Try: " + String.join(", ", choices());
		DragonDebug.forcedAnimation = anim;
		return "Every dragon now loops '" + anim.name().toLowerCase(Locale.ROOT) + "'. '/dragonfall anim auto' to undo.";
	}
}
