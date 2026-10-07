package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;

import java.util.List;

/**
 * The End's dragon guards its crystals: of the players within {@link DragonConfig#GUARD_RADIUS} of a
 * standing crystal (the threats), it goes after the one nearest a crystal, nearness to the dragon itself
 * counting by {@link DragonConfig#GUARD_DRAGON_WEIGHT}. One at a time: it keeps after the one it chose
 * while that one still threatens a crystal, unless another gets {@link DragonConfig#GUARD_SWITCH} blocks
 * nearer a crystal than it (going for another crystal while it is busy).
 */
public final class CrystalGuard {
	/** A player: some id for it, its distance to the nearest standing crystal and to the dragon (blocks). */
	public record Suspect(int id, double crystal, double dragon) {
		boolean threat() {
			return crystal <= DragonConfig.GUARD_RADIUS.get();
		}

		double score() {
			return crystal + DragonConfig.GUARD_DRAGON_WEIGHT.get() * dragon;
		}
	}

	/** Whom it is after: a {@link Suspect#id}, or {@link #NONE}. */
	public static final int NONE = Integer.MIN_VALUE;
	private int current = NONE;

	/** Picks whom to go after among {@code suspects} (and remembers it): a {@link Suspect#id}, {@link #NONE} when nobody threatens a crystal. */
	public int pick(List<Suspect> suspects) {
		Suspect best = null, kept = null;
		for (Suspect s : suspects) {
			if (!s.threat()) continue;
			if (best == null || s.score() < best.score()) best = s;
			if (s.id() == current) kept = s;
		}
		if (kept != null && best != null && best.crystal() >= kept.crystal() - DragonConfig.GUARD_SWITCH.get()) best = kept;
		current = best == null ? NONE : best.id();
		return current;
	}

	/** Whom it is after ({@link #NONE}: nobody). */
	public int current() {
		return current;
	}
}
