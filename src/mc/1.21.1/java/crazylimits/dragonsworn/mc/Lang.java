package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The core's {@link Text} as a game component: translated by the player's language
 * ({@code assets/dragonsworn/lang/*.json}), the core's English where no lang file has the key.
 */
public final class Lang {
	private Lang() {}

	public static MutableComponent of(Text text) {
		return Component.translatableWithFallback(text.key(), text.english(), text.args());
	}
}
