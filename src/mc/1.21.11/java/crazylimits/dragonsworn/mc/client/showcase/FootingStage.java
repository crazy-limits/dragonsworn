package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.mc.client.LimbContact;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * Stage {@code footing}: on uneven footholds the planted feet and folded hands hold still, and walking
 * they do not jitter. A dragon with nobody to fight stands on curvy ground (block heights rising and
 * falling under every limb), astride a one-block step, sat up on a 3 by 3 platform and clinging to a lone
 * pillar; each against the same stance on flat ground (the animation's own motion). Then it walks over the
 * curvy ground, against a walk on flat ground. Every tick {@link LimbContact} measures where each limb is
 * drawn. Standing, a planted limb (not in a turn's step) may move up or down a tick little more than on
 * flat ground and never by much at once; walking, how sharply each limb's up and down speed changes a tick
 * (its jerk) may be little more than on flat ground.
 */
final class FootingStage {
	private FootingStage() {
	}

	/**
	 * How much more a planted limb may bob (mean blocks a tick) than on flat ground, and the most in any one
	 * tick (or half as much again as the same stance on flat ground, where the animation moves the limb).
	 */
	static final double BOB_OVER_FLAT = 0.01, BOB_MAX = 0.12;
	/** How many times the flat walk's jerk (mean change of vertical speed a tick) a limb may have walking over curvy ground. */
	static final double JERK_OVER_FLAT = 3.0;

	private enum Shape {FLAT, CURVY, STEP, PLATFORM, PILLAR}

	static void footing(int x, int y, int z) {
		double[] flat = stand("flat", x, y, z, Foothold.STAND, Shape.FLAT, null);
		stand("curvy", x + 60, y, z, Foothold.STAND, Shape.CURVY, flat);
		stand("step", x + 120, y, z, Foothold.STAND, Shape.STEP, flat);
		double[] upright = stand("flat-upright", x + 180, y, z, Foothold.UPRIGHT, Shape.FLAT, null);
		stand("platform", x + 240, y, z, Foothold.UPRIGHT, Shape.PLATFORM, upright);
		double[] cling = stand("flat-cling", x + 300, y, z, Foothold.CLING, Shape.FLAT, null);
		stand("pillar", x + 360, y, z, Foothold.CLING, Shape.PILLAR, cling);
		double[] walk = walk("flat", x, y, z + 80, false, null);
		walk("curvy", x + 60, y, z + 80, true, walk);
	}

	/** Builds the site's ground at x, y, z; returns the height it stands at in the middle. */
	private static int build(Shape shape, int x, int y, int z) {
		switch (shape) {
			case CURVY -> {
				server(level -> {
					for (int dx = -24; dx <= 24; dx++) {
						for (int dz = -24; dz <= 24; dz++) {
							for (int h = 0; h < curve(dx, dz); h++) level.setBlockAndUpdate(new BlockPos(x + dx, y + h, z + dz), Blocks.GRASS_BLOCK.defaultBlockState());
						}
					}
				});
				return y + curve(0, 0);
			}
			// the left half (west: it faces north) one block up
			case STEP -> command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x - 12, y, z - 12, x - 1, y, z + 12), 2);
			case PLATFORM -> {
				command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x - 1, y, z - 1, x + 1, y + 5, z + 1), 2);
				return y + 6;
			}
			case PILLAR -> {
				command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x, y, z, x, y + 5, z), 2);
				return y + 6;
			}
			default -> {
			}
		}
		return y;
	}

	/** Summons the AI dragon at x, top, z facing north and stands it there with {@code foothold}, fighting {@code prey} (may be null). */
	private static void summon(String name, int x, int top, int z, Foothold foothold, boolean prey) {
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %.1f %d %.1f {Tags:[\"df_ai\"],Rotation:[0f,0f]}", x + 0.5, top, z + 0.5), 4);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, name + " footing: a dragon stands there");
			if (dragon == null) return;
			dragon.setYRot(0.0F);
			List<? extends Husk> husks = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			GroundFightPhase.start(dragon, prey && !husks.isEmpty() ? husks.get(0) : null, false, foothold);
		});
	}

	/**
	 * Standing with nobody to fight on {@code shape}; returns each limb's mean bob, its share of the ticks
	 * planted, and its most bob in a tick (filled in when measured).
	 */
	private static double[] stand(String name, int x, int y, int z, Foothold foothold, Shape shape, double[] flat) {
		int top = build(shape, x, y, z);
		command(view(x - 16, top + 8, z + 10, x, top + 2, z), 20);
		summon(name, x, top, z, foothold, false);
		// settle into the stance before measuring
		STEPS.add(new Step(30, mc -> {}));
		STEPS.add(new Step(1, mc -> LimbContact.start()));
		for (int i = 0; i < 8; i++) track(String.format(Locale.ROOT, "footing-%s-%d", name, i), 10, 18, 5);
		double[] bob = new double[12];
		STEPS.add(new Step(1, mc -> {
			List<LimbContact.Sample> samples = LimbContact.stop();
			write(mc, name, samples);
			bob(name, samples, flat, bob);
		}));
		command("kill @e[tag=df_ai]", 2);
		return bob;
	}

	/** Walking north over {@code curvy} ground or flat to a husk 30 blocks off; returns each limb's mean jerk (filled in when measured). */
	private static double[] walk(String name, int x, int y, int z, boolean curvy, double[] flat) {
		int top = build(curvy ? Shape.CURVY : Shape.FLAT, x, y, z + 12);
		command(view(x - 20, top + 10, z, x, top + 2, z - 10), 20);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}",
				x, y, z - 18), 2);
		summon("walk " + name, x, top, z + 12, Foothold.STAND, true);
		STEPS.add(new Step(10, mc -> {}));
		STEPS.add(new Step(1, mc -> LimbContact.start()));
		for (int i = 0; i < 10; i++) track(String.format(Locale.ROOT, "footing-walk-%s-%d", name, i), 8, 20, 5);
		double[] jerk = new double[4];
		STEPS.add(new Step(1, mc -> jerk(name, LimbContact.stop(), flat, jerk)));
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
		return jerk;
	}

	/** The curvy site's height at dx, dz from its middle: 0 to 2 blocks, a few blocks between rises. */
	private static int curve(int dx, int dz) {
		return (int) Math.round(1.0 + 1.2 * Math.sin(dx * 0.55 + 0.4) * Math.cos(dz * 0.45 + 0.9));
	}

	/** Each planted limb's bob: how far it moved up or down a tick, on average and at most. */
	private static void bob(String name, List<LimbContact.Sample> samples, double[] flat, double[] mean) {
		double[] sum = new double[4], most = new double[4];
		int[] n = new int[4];
		for (int k = 1; k < samples.size(); k++) {
			LimbContact.Sample a = samples.get(k - 1), b = samples.get(k);
			// standing: a dragon with nobody to fight may wander off now and then
			if (b.tick() != a.tick() + 1 || a.anim().equals("walk") || b.anim().equals("walk")) continue;
			for (int i = 0; i < 4; i++) {
				if (a.feet()[i] == null || b.feet()[i] == null || a.stepping()[i] || b.stepping()[i]) continue;
				double dy = Math.abs(b.feet()[i][1] - a.feet()[i][1]);
				sum[i] += dy;
				most[i] = Math.max(most[i], dy);
				n[i]++;
			}
		}
		for (int i = 0; i < 4; i++) {
			mean[i] = n[i] == 0 ? Double.NaN : sum[i] / n[i];
			mean[4 + i] = n[i] / (double) Math.max(1, samples.size());
			mean[8 + i] = most[i];
		}
		REPORT.add(String.format(Locale.ROOT, "INFO footing %s: %d ticks; planted limbs bob %.3f %.3f %.3f %.3f a tick, at most %.3f %.3f %.3f %.3f; planted %d %d %d %d ticks (%s)",
				name, samples.size(), mean[0], mean[1], mean[2], mean[3], most[0], most[1], most[2], most[3], n[0], n[1], n[2], n[3],
				String.join(", ", LimbContact.LIMBS)));
		check(samples.size() > 40, name + " footing: it stood long enough to measure (" + samples.size() + " ticks)");
		if (flat == null) return;
		for (int i = 0; i < 4; i++) {
			String limb = LimbContact.LIMBS[i];
			// as much of the time planted as on flat ground in the same stance (sat up and clinging it steps round more)
			check(mean[4 + i] >= Math.min(0.5, flat[4 + i] - 0.15), String.format(Locale.ROOT, "%s footing: the %s limb stands, not stepping over and over (%d of %d ticks)",
					name, limb, n[i], samples.size()));
			if (Double.isNaN(mean[i])) continue;
			// at most BOB_MAX at once, or no more than the stance itself on flat ground (clinging, the wings beat)
			check(mean[i] <= flat[i] + BOB_OVER_FLAT && most[i] <= Math.max(BOB_MAX, flat[8 + i] * 1.5),
					String.format(Locale.ROOT, "%s footing: the %s limb holds still (bob %.3f a tick, flat %.3f; at most %.3f)", name, limb, mean[i], flat[i], most[i]));
		}
	}

	/** Per tick, where each limb is drawn (world y) and whether it is in a step: run/showcase-footing-<name>.csv. */
	private static void write(Minecraft mc, String name, List<LimbContact.Sample> samples) {
		List<String> lines = new ArrayList<>();
		lines.add("tick,anim,y_lh,y_rh,y_lf,y_rf,step_lh,step_rh,step_lf,step_rf,gap_lh,gap_rh,gap_lf,gap_rf");
		for (LimbContact.Sample s : samples) {
			StringBuilder line = new StringBuilder(s.tick() + "," + s.anim());
			for (double[] f : s.feet()) line.append(String.format(Locale.ROOT, ",%.3f", f == null ? Double.NaN : f[1]));
			for (boolean b : s.stepping()) line.append(b ? ",1" : ",0");
			for (double g : s.gap()) line.append(String.format(Locale.ROOT, ",%.3f", g));
			lines.add(line.toString());
		}
		try {
			Files.write(mc.gameDirectory.toPath().resolve("showcase-footing-" + name + ".csv"), lines);
		} catch (IOException e) {
			LOG.error("Could not write the footing measurements", e);
		}
	}

	/** Each limb's jerk walking: the mean change of its vertical speed from one tick to the next. */
	private static void jerk(String name, List<LimbContact.Sample> samples, double[] flat, double[] mean) {
		double[] sum = new double[4], most = new double[4];
		int n = 0;
		for (int k = 2; k < samples.size(); k++) {
			LimbContact.Sample a = samples.get(k - 2), b = samples.get(k - 1), c = samples.get(k);
			if (c.tick() != b.tick() + 1 || b.tick() != a.tick() + 1 || !c.anim().equals("walk") || !a.anim().equals("walk")) continue;
			boolean all = true;
			for (int i = 0; i < 4; i++) all &= a.feet()[i] != null && b.feet()[i] != null && c.feet()[i] != null;
			if (!all) continue;
			for (int i = 0; i < 4; i++) {
				double d2 = Math.abs(c.feet()[i][1] - 2.0 * b.feet()[i][1] + a.feet()[i][1]);
				sum[i] += d2;
				most[i] = Math.max(most[i], d2);
			}
			n++;
		}
		for (int i = 0; i < 4; i++) mean[i] = n == 0 ? Double.NaN : sum[i] / n;
		REPORT.add(String.format(Locale.ROOT, "INFO footing walk %s: %d walking ticks; limbs jerk %.4f %.4f %.4f %.4f a tick, at most %.3f %.3f %.3f %.3f (%s)",
				name, n, mean[0], mean[1], mean[2], mean[3], most[0], most[1], most[2], most[3], String.join(", ", LimbContact.LIMBS)));
		check(n > 30, "walk " + name + " footing: it walked long enough to measure (" + n + " ticks)");
		if (flat == null || n == 0) return;
		for (int i = 0; i < 4; i++) {
			check(mean[i] <= flat[i] * JERK_OVER_FLAT, String.format(Locale.ROOT, "walk %s footing: the %s limb moves smoothly (jerk %.4f a tick, flat %.4f)",
					name, LimbContact.LIMBS[i], mean[i], flat[i]));
		}
	}
}
