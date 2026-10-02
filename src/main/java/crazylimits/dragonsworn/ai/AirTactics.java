package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;

import java.util.ArrayList;
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

	/** Each repertoire in fallback order (the last can always start); its odds are the server's ({@link DragonConfig}, {@code [attacks.*]}). */
	private static final Attack[] GROUND_ATTACKS = {Attack.SNATCH, Attack.BREATH_PASS, Attack.FIREBALL_PASS, Attack.CHARGE, Attack.BARRAGE};
	private static final Attack[] WALL_ATTACKS = {Attack.SNATCH, Attack.FLYBY_BITE, Attack.BREATH_PASS, Attack.HOVER_BITE, Attack.HOVER_BREATH, Attack.BARRAGE};
	private static final Attack[] AIR_ATTACKS = {Attack.FLYBY_BITE, Attack.HOVER_BITE, Attack.HOVER_BREATH, Attack.BARRAGE};

	private AirTactics() {}

	/** Where the target is: in the air, or on the ground with or without room to land beside it. */
	public static Reach reach(boolean airborne, boolean canLand) {
		return airborne ? Reach.AIR : canLand ? Reach.GROUND : Reach.WALL;
	}

	/**
	 * The attacks to try on a target at {@code reach}, first choice first: the one {@code roll} (0..1)
	 * picks by the odds, then the rest of the repertoire after it, ending with one that always starts (the
	 * barrage, unless the server disabled it). Empty when every attack of the repertoire is disabled.
	 */
	public static List<Attack> choices(Reach reach, double roll) {
		Attack[] repertoire = switch (reach) {
			case GROUND -> GROUND_ATTACKS;
			case WALL -> WALL_ATTACKS;
			case AIR -> AIR_ATTACKS;
		};
		// a disabled attack is out altogether, not even a fallback
		List<Attack> attacks = new ArrayList<>();
		for (Attack a : repertoire) if (enabled(a)) attacks.add(a);
		if (attacks.isEmpty()) return List.of();
		double total = 0.0;
		for (Attack a : attacks) total += odds(reach, a);
		int pick = attacks.size() - 1;
		if (total > 0.0) {
			double sum = 0.0;
			for (int i = 0; i < attacks.size(); i++) {
				sum += odds(reach, attacks.get(i)) / total;
				if (roll < sum) {
					pick = i;
					break;
				}
			}
		}
		return List.copyOf(attacks.subList(pick, attacks.size()));
	}

	/** Whether the server lets the dragon use {@code attack} at all. */
	public static boolean enabled(Attack attack) {
		return switch (attack) {
			case SNATCH -> DragonConfig.SNATCH.get();
			case BREATH_PASS -> DragonConfig.BREATH_PASS.get();
			case FIREBALL_PASS -> DragonConfig.FIREBALL_PASS.get();
			case CHARGE -> DragonConfig.CHARGE.get();
			case BARRAGE -> DragonConfig.BARRAGE.get();
			case FLYBY_BITE -> DragonConfig.FLYBY_BITE.get();
			case HOVER_BITE -> DragonConfig.HOVER_BITE.get();
			case HOVER_BREATH -> DragonConfig.HOVER_BREATH.get();
		};
	}

	/** The weight of {@code attack} being the first choice at {@code reach} (0 where it is not in the repertoire). */
	static double odds(Reach reach, Attack attack) {
		DragonConfig.Num weight = switch (reach) {
			case GROUND -> switch (attack) {
				case SNATCH -> DragonConfig.GROUND_SNATCH;
				case BREATH_PASS -> DragonConfig.GROUND_BREATH_PASS;
				case FIREBALL_PASS -> DragonConfig.GROUND_FIREBALL_PASS;
				case CHARGE -> DragonConfig.GROUND_CHARGE;
				case BARRAGE -> DragonConfig.GROUND_BARRAGE;
				default -> null;
			};
			case WALL -> switch (attack) {
				case SNATCH -> DragonConfig.WALL_SNATCH;
				case FLYBY_BITE -> DragonConfig.WALL_FLYBY_BITE;
				case BREATH_PASS -> DragonConfig.WALL_BREATH_PASS;
				case HOVER_BITE -> DragonConfig.WALL_HOVER_BITE;
				case HOVER_BREATH -> DragonConfig.WALL_HOVER_BREATH;
				case BARRAGE -> DragonConfig.WALL_BARRAGE;
				default -> null;
			};
			case AIR -> switch (attack) {
				case FLYBY_BITE -> DragonConfig.AIR_FLYBY_BITE;
				case HOVER_BITE -> DragonConfig.AIR_HOVER_BITE;
				case HOVER_BREATH -> DragonConfig.AIR_HOVER_BREATH;
				case BARRAGE -> DragonConfig.AIR_BARRAGE;
				default -> null;
			};
		};
		return weight == null ? 0.0 : weight.get();
	}

	/** Whether {@code reach} keeps the dragon in the air: it attacks from there more often. */
	public static boolean airborne(Reach reach) {
		return reach != Reach.GROUND;
	}
}
