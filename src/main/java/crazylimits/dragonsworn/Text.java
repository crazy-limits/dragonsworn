package crazylimits.dragonsworn;

import java.util.Locale;

/**
 * A line of text the player reads, as a translation key (the lang files, {@code assets/dragonsworn/lang})
 * with its English for where no lang file has it: the bridge turns it into
 * {@code Component.translatableWithFallback(key, english, args)}. {@code english} is a format string
 * ({@code %s} per argument), as the lang files' values are.
 */
public record Text(String key, String english, Object... args) {
	/** The English, arguments filled in (logs, files, tests). */
	public String toEnglish() {
		return args.length == 0 ? english : String.format(Locale.ROOT, english, args);
	}
}
