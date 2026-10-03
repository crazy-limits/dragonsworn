package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.body.PoseTrack;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.client.LimbContact;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Full run only: roaming, and walking over hills with the feet on the ground. */
final class TerrainStage {
	private TerrainStage() {
	}


	/**
	 * Free roaming: a summoned dragon with nobody to hunt (the camera is in creative) is left alone. It
	 * must wander off from where it appeared, come down somewhere on its own, and walk about there.
	 */
	static void roaming(int rx, int y, int rz) {
		command(view(rx - 40, y + 20, rz, rx, y + 12, rz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", rx, y + 20, rz), 20);
		Set<String> phases = new LinkedHashSet<>();
		double[] furthest = new double[1], walked = new double[1], last = {Double.NaN, 0.0};
		for (int i = 0; i < 60; i++) {
			track(String.format(Locale.ROOT, "roam-%02d", i), 20, 34);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				phases.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", ""));
				furthest[0] = Math.max(furthest[0], Math.hypot(dragon.getX() - rx, dragon.getZ() - rz));
				if (DragonswornDragon.brain(dragon).onGround()) {
					if (!Double.isNaN(last[0])) walked[0] += Math.hypot(dragon.getX() - last[0], dragon.getZ() - last[1]);
					last[0] = dragon.getX();
					last[1] = dragon.getZ();
				} else {
					last[0] = Double.NaN;
				}
			});
		}
		server(level -> {
			REPORT.add("INFO roaming phases seen: " + phases);
			check(furthest[0] > 50, String.format(Locale.ROOT, "it wandered off from where it appeared (%.0f blocks)", furthest[0]));
			check(phases.contains("DragonswornGroundFight"), "it came down on its own with nobody to hunt");
			check(walked[0] > 6, String.format(Locale.ROOT, "it walked about on the ground (%.1f blocks)", walked[0]));
		});
		command("kill @e[tag=df_ai]", 2);
	}

	/**
	 * Feet on uneven ground. A dragon stands on flat ground before a stepped mound (three terraces, one block up
	 * every four) and walks over it to a husk beyond, the last stretch with a one-block step under its
	 * left side, so its feet stand at different heights front to back and side to side. Every tick on
	 * the ground {@link LimbContact} measures how close each limb comes to the ground under it: the
	 * nearest any of its corners is above (floating) or below (sunk in) the block under that corner. A planted foot touches: over each walk cycle every foot must
	 * come within {@link #CONTACT} of the ground at least once, and none may sink deeper than that.
	 * Flat ground is checked the same way first, which shows the measurement itself is sound.
	 */
	static void hills(int tx, int y, int tz) {
		command(view(tx - 30, y + 12, tz + 20, tx, y + 2, tz), 40);
		for (int[] t : new int[][]{{-9, 9, 0}, {-5, 5, 1}, {-1, 1, 2}}) {
			command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", tx + t[0], y + t[2], tz - 12, tx + t[1], y + t[2], tz + 12), 2);
		}
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:grass_block", tx + 10, y, tz - 12, tx + 24, y, tz - 1), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}",
				tx + 18, y, tz), 2);
		// nose toward +x (a dragon's yaw 90)
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[90f,0f]}", tx - 20, y, tz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty(), "a dragon and its prey at the hills");
			if (dragon != null && !prey.isEmpty()) GroundFightPhase.start(dragon, prey.get(0));
		});
		// let the model settle from the hover it was summoned in into the ground pose before measuring
		STEPS.add(new Step(20, mc -> {}));
		STEPS.add(new Step(1, mc -> LimbContact.start()));
		for (int i = 0; i < 40; i++) track(String.format(Locale.ROOT, "hills-%02d", i), 8, 20, 2);
		STEPS.add(new Step(1, mc -> {
			List<LimbContact.Sample> samples = LimbContact.stop();
			REPORT.add("INFO hills: " + samples.size() + " ticks measured on the ground (per tick: showcase-limbs.csv)");
			writeLimbs(mc, samples);
			check(samples.size() > 150, "the dragon stood and walked long enough to measure (" + samples.size() + " ticks)");
			footing(samples, false, y);
			footing(samples, true, y);
		}));
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/** How far a planted foot may be off the ground (blocks): a quarter block, 4 model px. */
	static final double CONTACT = 0.25;
	/** Ticks of one walk cycle: every foot is planted at some point within it. */
	static final int CYCLE = (int) Math.round(PoseTrack.length(DragonAnim.WALK) * 20);

	/**
	 * Reports and checks the feet over either the flat stretches (all four on the base ground) or the
	 * uneven ones. Floating: over a whole walk cycle a foot never came down to within this of the ground
	 * under it. Sunk: the deepest a foot went into the ground.
	 */
	static void footing(List<LimbContact.Sample> samples, boolean uneven, int baseY) {
		String where = uneven ? "uneven ground" : "flat ground";
		int n = LimbContact.LIMBS.length;
		double[] floating = new double[n], sunk = new double[n];
		int windows = 0;
		for (int end = CYCLE; end <= samples.size(); end++) {
			List<LimbContact.Sample> window = samples.subList(end - CYCLE, end);
			// flat: the whole cycle on the base ground; uneven: at least half of it off it
			long off = window.stream().filter(s -> !flat(s, baseY)).count();
			if (uneven ? off < CYCLE / 2 : off > 0) continue;
			windows++;
			for (int limb = 0; limb < n; limb++) {
				double low = Double.MAX_VALUE;
				for (LimbContact.Sample s : window) {
					if (Double.isNaN(s.gap()[limb])) continue;
					low = Math.min(low, s.gap()[limb]);
					sunk[limb] = Math.min(sunk[limb], s.gap()[limb]);
				}
				floating[limb] = Math.max(floating[limb], low);
			}
		}
		check(windows > 0, "there were walk cycles on " + where + " (" + windows + ")");
		if (windows == 0) return;
		for (int limb = 0; limb < n; limb++) {
			String name = LimbContact.LIMBS[limb];
			REPORT.add(String.format(Locale.ROOT, "INFO %s, %s foot: floats up to %.2f, sinks up to %.2f blocks", where, name, floating[limb], -sunk[limb]));
			check(floating[limb] <= CONTACT, String.format(Locale.ROOT, "on %s the %s foot comes down to the ground (%.2f above at worst)", where, name, floating[limb]));
			check(sunk[limb] >= -CONTACT, String.format(Locale.ROOT, "on %s the %s foot stays out of the ground (%.2f in at worst)", where, name, -sunk[limb]));
		}
	}

	static void writeLimbs(Minecraft mc, List<LimbContact.Sample> samples) {
		List<String> lines = new ArrayList<>();
		lines.add("tick,anim,gap_lh,gap_rh,gap_lf,gap_rf,ground_lh,ground_rh,ground_lf,ground_rf");
		for (LimbContact.Sample s : samples) {
			StringBuilder line = new StringBuilder(s.tick() + "," + s.anim());
			for (double g : s.gap()) line.append(String.format(Locale.ROOT, ",%.3f", g));
			for (double g : s.ground()) line.append(String.format(Locale.ROOT, ",%.2f", g));
			lines.add(line.toString());
		}
		try {
			Files.write(mc.gameDirectory.toPath().resolve("showcase-limbs.csv"), lines);
		} catch (IOException e) {
			LOG.error("Could not write the limb measurements", e);
		}
	}

	/** All four feet over the base ground. */
	static boolean flat(LimbContact.Sample s, int baseY) {
		for (double g : s.ground()) {
			if (Double.isNaN(g) || Math.abs(g - baseY) > 0.01) return false;
		}
		return true;
	}
}
