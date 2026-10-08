package crazylimits.dragonsworn.arena;

import crazylimits.dragonsworn.config.DragonConfig;

import java.util.List;

/**
 * The rune ward round some of the End's crystals, in place of vanilla's iron cages: three rings turning round
 * the crystal, each a circle of {@link #PANES} flat panes, each pane turned {@link #STEP} degrees from the last,
 * a rune of the Standard Galactic Alphabet on each ({@code tools/crystal_ward.py}). A projectile flying into the
 * ward's sphere ({@link #RADIUS}) bounces off it, and the crystal takes no projectile damage: it is broken up close
 * (or by an explosion). Which crystals: those on the shortest spires (the easiest to shoot, the ones vanilla
 * cages), more the harder the difficulty ({@link #count}), ranked among the island's own spires (vanilla's, or a
 * datapack's or another mod's layout: {@link #warded}).
 *
 * <p>A ring's frame: the ring lies in its x-z plane round its y axis. It is tilted {@link Ring#tilt} degrees
 * about x, the tilt's direction turning round the world's vertical ({@link #yaw}), and the ring spins about its
 * own axis ({@link #spin}). A tilted ring dips {@code radius sin(tilt) + PANE_HEIGHT / 2 cos(tilt)} below the
 * centre: kept under {@link #CENTER}, so no ring sinks into the spire's top. The radii are far enough apart
 * that no two rings ever touch, however they turn.
 */
public final class CrystalWard {
	/** Panes per ring, each turned {@link #STEP} degrees from the last. */
	public static final int PANES = 16;
	public static final double STEP = 360.0 / PANES;
	/** The rings' centre above the crystal's feet (the crystal bobs round 1 block up). */
	public static final double CENTER = 1.1;
	/** Blocks: the panes' height (along the ring's axis). */
	public static final double PANE_HEIGHT = 0.5625;
	/** The texture's texels per block, and its size (one row of panes per ring, written by {@code crystal_ward.py}). */
	public static final int DENSITY = 32, TEXTURE_WIDTH = 384, TEXTURE_HEIGHT = 64;

	/**
	 * One ring: {@code radius} to the panes' middles, {@code tilt} in degrees, {@code yaw} the tilt's direction at
	 * time 0, {@code spin} and {@code precession} in degrees per tick (round its axis, round the vertical).
	 */
	public record Ring(double radius, double tilt, double yaw, double spin, double precession) {
		/** Blocks: a pane's width (the panes meet edge to edge round the circle). */
		public double paneWidth() {
			return 2.0 * radius * Math.tan(Math.toRadians(STEP / 2.0));
		}

		/** The pane's corners' distance from the axis. */
		public double cornerRadius() {
			return radius / Math.cos(Math.toRadians(STEP / 2.0));
		}

		/** Texels: one pane's cell in its texture row. */
		public int cellWidth() {
			return (int) Math.round(paneWidth() * DENSITY);
		}

		/** Blocks: how far the ring reaches below its centre. */
		public double dip() {
			double t = Math.toRadians(tilt);
			return radius * Math.sin(t) + PANE_HEIGHT / 2.0 * Math.cos(t);
		}
	}

	/** Innermost first: it clears the crystal's glass (half a diagonal of its 1-block cube, plus its bob). */
	public static final List<Ring> RINGS = List.of(
			new Ring(1.4, 38.0, 0.0, 2.4, 0.35),
			new Ring(1.65, 26.0, 120.0, -1.8, -0.5),
			new Ring(1.9, 15.0, 240.0, 1.3, 0.25));
	/** The sphere projectiles bounce off: just outside the outer ring's panes. */
	public static final double RADIUS = 2.0;
	/** The share of its speed a projectile keeps in the bounce. */
	public static final double BOUNCE = 0.8;
	/** Blocks a bounced projectile is set outside the sphere (so the next step starts clear of it). */
	private static final double CLEAR = 0.05;
	private CrystalWard() {}

	/** How many crystals are warded at {@code difficulty} (0 peaceful .. 3 hard, vanilla's ids). */
	public static int count(int difficulty) {
		return switch (difficulty) {
			case 3 -> DragonConfig.WARDED_HARD.get();
			case 2 -> DragonConfig.WARDED_NORMAL.get();
			default -> DragonConfig.WARDED_EASY.get();
		};
	}

	/**
	 * Whether the crystal on spike {@code index} of the island's spikes ({@code heights}) is warded when
	 * {@code count} are: the shortest first, equal heights in the spikes' order. An index outside them is not.
	 */
	public static boolean warded(int[] heights, int index, int count) {
		if (index < 0 || index >= heights.length) return false;
		int rank = 0;
		for (int i = 0; i < heights.length; i++)
			if (heights[i] < heights[index] || heights[i] == heights[index] && i < index) rank++;
		return rank < count;
	}

	/** Degrees: the direction ring {@code ring}'s tilt leans to at {@code time} ticks. */
	public static double yaw(Ring ring, double time) {
		return ring.yaw + ring.precession * time;
	}

	/** Degrees: how far ring {@code ring} has turned round its own axis at {@code time} ticks. */
	public static double spin(Ring ring, double time) {
		return ring.spin * time;
	}

	/** 0..1: how brightly pane {@code pane} of ring {@code ring} glows at {@code time}: a slow wave runs round each ring. */
	public static double glow(int ring, int pane, double time) {
		double phase = 2.0 * Math.PI * (pane / (double) PANES) - time * (0.08 + 0.03 * ring);
		return 0.7 + 0.3 * Math.cos(phase);
	}

	/**
	 * A projectile's step from {@code p} by {@code v} against the ward centred at {@code c}: if it enters the
	 * sphere from outside within the step, where it bounces (just outside the sphere, on the entry point's normal)
	 * and its velocity after the bounce (mirrored about the surface there, {@link #BOUNCE} of its speed), as
	 * {x, y, z, vx, vy, vz}; else null. A projectile already inside (fired from there) flies on.
	 */
	public static double[] bounce(double px, double py, double pz, double vx, double vy, double vz,
								  double cx, double cy, double cz) {
		double dx = px - cx, dy = py - cy, dz = pz - cz;
		double a = vx * vx + vy * vy + vz * vz;
		double c = dx * dx + dy * dy + dz * dz - RADIUS * RADIUS;
		if (a < 1.0E-9 || c <= 0.0) return null;
		double b = dx * vx + dy * vy + dz * vz;
		if (b >= 0.0) return null;
		double disc = b * b - a * c;
		if (disc < 0.0) return null;
		double t = (-b - Math.sqrt(disc)) / a;
		if (t > 1.0) return null;
		double nx = (dx + t * vx) / RADIUS, ny = (dy + t * vy) / RADIUS, nz = (dz + t * vz) / RADIUS;
		double along = vx * nx + vy * ny + vz * nz;
		double out = RADIUS + CLEAR;
		return new double[] {
				cx + nx * out, cy + ny * out, cz + nz * out,
				(vx - 2.0 * along * nx) * BOUNCE, (vy - 2.0 * along * ny) * BOUNCE, (vz - 2.0 * along * nz) * BOUNCE};
	}
}
