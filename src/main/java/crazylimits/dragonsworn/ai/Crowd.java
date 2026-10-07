package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The players fighting the dragon, and how fierce that makes it. Game-free, so it is unit tested; the brain
 * hands it the survival players within {@link DragonConfig#CROWD_RANGE} now and then.
 *
 * <ul>
 *   <li><b>Pace</b>: each player past the first makes its attacks come sooner by
 *       {@link DragonConfig#PACE_PER_PLAYER} (cooldowns run that much faster), up to {@link DragonConfig#MAX_PACE}.
 *       The End's dragon with a player at its crystals ({@code guarding}) goes at least at
 *       {@link DragonConfig#GUARD_PACE}: its crystals come first.</li>
 *   <li><b>Area bias</b>: players bunched within {@link DragonConfig#GROUP_RADIUS} of whom it attacks make
 *       its area attacks likelier ({@link DragonConfig#AREA_BIAS} per extra player): the breath pass, the
 *       hovering breath and the fireballs catch them all.</li>
 * </ul>
 */
public final class Crowd {
	private final List<double[]> players = new ArrayList<>();
	private boolean guarding;

	/** The players near it now ({x, y, z} each), and whether one of them threatens its crystals. */
	public void update(List<double[]> players, boolean guarding) {
		this.players.clear();
		this.players.addAll(players);
		this.guarding = guarding;
	}

	/** How many players fight it. */
	public int count() {
		return players.size();
	}

	/** How many times as fast its cooldowns run out as against one player (1 or more). */
	public double pace() {
		return pace(players.size(), guarding);
	}

	static double pace(int count, boolean guarding) {
		double pace = Math.min(DragonConfig.MAX_PACE.get(), 1.0 + DragonConfig.PACE_PER_PLAYER.get() * Math.max(0, count - 1));
		return guarding ? Math.max(pace, DragonConfig.GUARD_PACE.get()) : pace;
	}

	/** {@code ticks} of cooldown at its {@link #pace}. */
	public int scale(int ticks) {
		return (int) Math.round(ticks / pace());
	}

	/** How many players stand within {@code radius} of (x, y, z). */
	public int within(double x, double y, double z, double radius) {
		int n = 0;
		for (double[] p : players) {
			double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
			if (dx * dx + dy * dy + dz * dz <= radius * radius) n++;
		}
		return n;
	}

	/** How much likelier an area attack is on a target at (x, y, z): 1 alone, more with players bunched there. */
	public double areaBias(double x, double y, double z) {
		return areaBias(within(x, y, z, DragonConfig.GROUP_RADIUS.get()));
	}

	static double areaBias(int bunched) {
		return 1.0 + DragonConfig.AREA_BIAS.get() * Math.max(0, bunched - 1);
	}

	/**
	 * Odds of a one-target attack ({@code single}) and an area attack ({@code area}) out of 1, the rest
	 * being something else, with the area attack weighed by {@code bias}: {single, area}, still out of 1.
	 */
	public static double[] odds(double single, double area, double bias) {
		double rest = Math.max(0.0, 1.0 - single - area), total = single + area * bias + rest;
		return total <= 0.0 ? new double[]{0.0, 0.0} : new double[]{single / total, area * bias / total};
	}
}
