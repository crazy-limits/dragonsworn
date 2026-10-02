package crazylimits.dragonfall.ai;

import java.util.List;

/**
 * Which attack a flying dragon makes on its target. Game-free, so it is unit tested; the brain asks it
 * whenever an attack from the air is due, and tries the attacks in the order given until one can start
 * (a snatch needs prey it can hold, a breath pass open sky over it, a charge a clear line).
 *
 * <p>Where the target stands decides the repertoire ({@link Reach}). On open ground the dragon would
 * rather land and fight on foot; its air attacks are the snatch, the breath pass, the fireball pass, the
 * charge and the barrage. Where it cannot come down beside its prey (on a wall's top, a pillar, a ledge
 * with no foothold, a player pillaring up a spire to its crystal) it fights from the air in earnest: it
 * bites in passing ({@code FlybyBite}), pours the breath pass over it, or hovers in close to bite or breathe
 * ({@code HoverAttack}). A target in the air (gliding on elytra, flying) gets the fly-by bite and the hover
 * attacks: there is no ground to come down to at all.
 */
public final class AirTactics {
	/** Where the target is, for an attack from the air. */
	public enum Reach {
		/** On the ground, with room for the dragon to land beside it. */
		GROUND,
		/** On solid ground, but nowhere to land within reach of it (not even a narrow foothold). */
		WALL,
		/** In the air: gliding, flying, or over a drop. */
		AIR
	}

	public enum Attack { SNATCH, BREATH_PASS, FIREBALL_PASS, CHARGE, BARRAGE, FLYBY_BITE, HOVER_BITE, HOVER_BREATH }

	/** Each repertoire with its odds (summing to 1), in fallback order: the last can always start. */
	private static final Attack[] GROUND_ATTACKS = {Attack.SNATCH, Attack.BREATH_PASS, Attack.FIREBALL_PASS, Attack.CHARGE, Attack.BARRAGE};
	private static final double[] GROUND_ODDS = {0.2, 0.25, 0.2, 0.2, 0.15};
	private static final Attack[] WALL_ATTACKS = {Attack.SNATCH, Attack.FLYBY_BITE, Attack.BREATH_PASS, Attack.HOVER_BITE, Attack.HOVER_BREATH, Attack.BARRAGE};
	private static final double[] WALL_ODDS = {0.1, 0.25, 0.15, 0.2, 0.2, 0.1};
	private static final Attack[] AIR_ATTACKS = {Attack.FLYBY_BITE, Attack.HOVER_BITE, Attack.HOVER_BREATH, Attack.BARRAGE};
	private static final double[] AIR_ODDS = {0.35, 0.25, 0.3, 0.1};

	private AirTactics() {}

	/** Where the target is: in the air, or on the ground with or without room to land beside it. */
	public static Reach reach(boolean airborne, boolean canLand) {
		return airborne ? Reach.AIR : canLand ? Reach.GROUND : Reach.WALL;
	}

	/**
	 * The attacks to try on a target at {@code reach}, first choice first: the one {@code roll} (0..1)
	 * picks by the odds, then the rest of the repertoire after it, ending with one that always starts.
	 */
	public static List<Attack> choices(Reach reach, double roll) {
		Attack[] attacks = switch (reach) {
			case GROUND -> GROUND_ATTACKS;
			case WALL -> WALL_ATTACKS;
			case AIR -> AIR_ATTACKS;
		};
		double[] odds = switch (reach) {
			case GROUND -> GROUND_ODDS;
			case WALL -> WALL_ODDS;
			case AIR -> AIR_ODDS;
		};
		int pick = attacks.length - 1;
		double sum = 0.0;
		for (int i = 0; i < odds.length; i++) {
			sum += odds[i];
			if (roll < sum) {
				pick = i;
				break;
			}
		}
		return List.of(attacks).subList(pick, attacks.length);
	}

	/** Whether {@code reach} keeps the dragon in the air: it attacks from there more often. */
	public static boolean airborne(Reach reach) {
		return reach != Reach.GROUND;
	}
}
