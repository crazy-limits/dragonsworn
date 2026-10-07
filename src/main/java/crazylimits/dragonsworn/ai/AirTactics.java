package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

	/** Every attack from the air, each switched on or off by the server ({@code [attacks]}). */
	public enum Attack {
		SNATCH(DragonConfig.SNATCH, false),
		BREATH_PASS(DragonConfig.BREATH_PASS, true),
		FIREBALL_PASS(DragonConfig.FIREBALL_PASS, true),
		CHARGE(DragonConfig.CHARGE, false),
		BARRAGE(DragonConfig.BARRAGE, true),
		FLYBY_BITE(DragonConfig.FLYBY_BITE, false),
		HOVER_BITE(DragonConfig.HOVER_BITE, false),
		HOVER_BREATH(DragonConfig.HOVER_BREATH, true);

		private final DragonConfig.Flag allowed;
		/** It hits everything round where it lands (flame, a fireball's cloud), not just the one target. */
		public final boolean area;

		Attack(DragonConfig.Flag allowed, boolean area) {
			this.allowed = allowed;
			this.area = area;
		}

		/** Whether the server lets the dragon use it at all. */
		public boolean enabled() {
			return allowed.get();
		}
	}

	/** One attack of a repertoire, with the server's weight for it being the first choice ({@code [attacks.*]}). */
	private record Option(Attack attack, DragonConfig.Num weight) {
	}

	/**
	 * Each {@link Reach}'s repertoire in fallback order (the last can always start). Adding an attack: an
	 * {@link Attack} with its flag, a weight per reach in {@link DragonConfig}, and its line(s) here.
	 */
	private static final Map<Reach, List<Option>> REPERTOIRES = new EnumMap<>(Map.of(
			Reach.GROUND, List.of(
					new Option(Attack.SNATCH, DragonConfig.GROUND_SNATCH),
					new Option(Attack.BREATH_PASS, DragonConfig.GROUND_BREATH_PASS),
					new Option(Attack.FIREBALL_PASS, DragonConfig.GROUND_FIREBALL_PASS),
					new Option(Attack.CHARGE, DragonConfig.GROUND_CHARGE),
					new Option(Attack.BARRAGE, DragonConfig.GROUND_BARRAGE)),
			Reach.WALL, List.of(
					new Option(Attack.SNATCH, DragonConfig.WALL_SNATCH),
					new Option(Attack.FLYBY_BITE, DragonConfig.WALL_FLYBY_BITE),
					new Option(Attack.BREATH_PASS, DragonConfig.WALL_BREATH_PASS),
					new Option(Attack.HOVER_BITE, DragonConfig.WALL_HOVER_BITE),
					new Option(Attack.HOVER_BREATH, DragonConfig.WALL_HOVER_BREATH),
					new Option(Attack.BARRAGE, DragonConfig.WALL_BARRAGE)),
			Reach.AIR, List.of(
					new Option(Attack.FLYBY_BITE, DragonConfig.AIR_FLYBY_BITE),
					new Option(Attack.HOVER_BITE, DragonConfig.AIR_HOVER_BITE),
					new Option(Attack.HOVER_BREATH, DragonConfig.AIR_HOVER_BREATH),
					new Option(Attack.BARRAGE, DragonConfig.AIR_BARRAGE))));

	private AirTactics() {}

	/** Where the target is: in the air, or on the ground with or without room to land beside it. */
	public static Reach reach(boolean airborne, boolean canLand) {
		return airborne ? Reach.AIR : canLand ? Reach.GROUND : Reach.WALL;
	}

	/** As below, on a target alone. */
	public static List<Attack> choices(Reach reach, double roll) {
		return choices(reach, roll, 1.0);
	}

	/**
	 * The attacks to try on a target at {@code reach}, first choice first: the one {@code roll} (0..1)
	 * picks by the odds, then the rest of the repertoire after it, ending with one that always starts (the
	 * barrage, unless the server disabled it). Empty when every attack of the repertoire is disabled.
	 * {@code areaBias} ({@link Crowd#areaBias}) multiplies the odds of the {@link Attack#area} attacks:
	 * players bunched together draw the breath and the fireballs.
	 */
	public static List<Attack> choices(Reach reach, double roll, double areaBias) {
		// a disabled attack is out altogether, not even a fallback
		List<Option> options = new ArrayList<>();
		for (Option o : REPERTOIRES.get(reach)) if (o.attack.enabled()) options.add(o);
		if (options.isEmpty()) return List.of();
		double total = 0.0;
		for (Option o : options) total += weight(o, areaBias);
		int pick = options.size() - 1;
		if (total > 0.0) {
			double sum = 0.0;
			for (int i = 0; i < options.size(); i++) {
				sum += weight(options.get(i), areaBias) / total;
				if (roll < sum) {
					pick = i;
					break;
				}
			}
		}
		List<Attack> attacks = new ArrayList<>();
		for (Option o : options.subList(pick, options.size())) attacks.add(o.attack);
		return List.copyOf(attacks);
	}

	private static double weight(Option o, double areaBias) {
		return o.attack.area ? o.weight.get() * areaBias : o.weight.get();
	}

	/** The weight of {@code attack} being the first choice at {@code reach} (0 where it is not in the repertoire). */
	static double odds(Reach reach, Attack attack) {
		for (Option o : REPERTOIRES.get(reach)) if (o.attack == attack) return o.weight.get();
		return 0.0;
	}

	/** Whether {@code reach} keeps the dragon in the air: it attacks from there more often. */
	public static boolean airborne(Reach reach) {
		return reach != Reach.GROUND;
	}
}
