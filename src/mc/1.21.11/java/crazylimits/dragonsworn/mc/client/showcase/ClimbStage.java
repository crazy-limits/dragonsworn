package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.HopPhase;
import crazylimits.dragonsworn.mc.phase.WallApproachPhase;
import crazylimits.dragonsworn.nav.Surface;
import crazylimits.dragonsworn.nav.SurfaceSites;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.zombie.Husk;

import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * Stage {@code climb}: the dragon on walls. A husk hides in a tunnel dug into a cliff, where nothing can
 * land beside it: on the sheer cliff the dragon finds nothing for its feet and does not land; given a ledge
 * under the tunnel it flies in, grips the cliff standing on the ledge and bites into the tunnel (or
 * breathes down it), and flies off again within {@code wall_time}. Then the ledge is broken away under it:
 * it falls and flies. Last a husk stands on top of an obsidian pillar: the dragon, on the ground at its
 * foot, never climbs onto the pillar's side: it takes off.
 */
final class ClimbStage {
	private ClimbStage() {
	}

	/** The scenes, side by side. */
	static void climb(int x, int y, int z) {
		tunnel(x, y, z);
		fall(x + 75, y, z);
		pillar(x + 150, y, z);
		faces(x + 225, y, z);
		shapes(x + 300, y, z);
	}

	/** A wall's face (blocks east of the wall's back, at height dy and along dz from the tunnel). */
	private interface Shape {
		int face(int dy, int dz);
	}

	/**
	 * Walls of other shapes, a husk in a tunnel 22 up in each: a diagonal one (a zigzag of whole blocks, its
	 * face looking south-east) with a ledge under the tunnel, one stepped back a block every two up, and a
	 * stair of cliffs (a block back every block up). The dragon flies in and grips each on its own frame
	 * (nav/Surface.Face: diagonal; a stepped one, 60 or 45 degrees, whichever it lies flatter in from where it
	 * comes), a hind heel on a foothold, upright, its tail hanging down the wall, and holds on. The report
	 * gets the phases and faces it went through.
	 */
	static void shapes(int x, int y, int z) {
		shape("diagonal", x, y, z, (dy, dz) -> 12 - Math.max(-14, Math.min(14, dz)), true,
				face -> face == Surface.Face.SOUTH_EAST);
		shape("stepped", x + 70, y, z, (dy, dz) -> 12 - dy / 2, false, face -> face.tilt < 90.0 && face.nx > 0.9);
		shape("stair", x + 140, y, z, (dy, dz) -> 24 - dy, false, face -> face.tilt < 90.0 && face.nx > 0.9);
	}

	/** Adds the dragon's phase and face to {@code trail} when they change. */
	private static void note(StringBuilder trail, String[] last, EnderDragon dragon) {
		if (dragon == null) return;
		DragonBrain brain = DragonswornDragon.brain(dragon);
		String now = dragon.getPhaseManager().getCurrentPhase().getPhase() + "/" + brain.face() + (brain.leaning().wall() ? "~" + brain.leaning() : "")
				+ (brain.face().wall() && !brain.standsOn() ? "/NO-FOOTHOLD" : "");
		if (now.equals(last[0])) return;
		last[0] = now;
		trail.append(" ").append(dragon.tickCount).append(':').append(now);
	}

	private static void shape(String name, int sx, int y, int sz, Shape shape, boolean ledge, java.util.function.Predicate<Surface.Face> frame) {
		int ty = y + 22, top = 40;
		// the wall, a column of blocks at a time (whichever way it varies), from 30 back to its face
		for (int dz = -20; dz <= 20; dz++) {
			for (int dy = 0; dy <= top; dy++) {
				int f = shape.face(dy, dz);
				int run = dy;
				while (run + 1 <= top && shape.face(run + 1, dz) == f) run++;
				command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", sx - 30, y + dy, sz + dz, sx + f - 1, y + run, sz + dz), 1);
				dy = run;
			}
		}
		int mouth = shape.face(22, 0);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", sx + mouth - 6, ty, sz, sx + mouth - 1, ty + 1, sz), 2);
		if (ledge) {
			for (int dz = -5; dz <= 5; dz++) {
				int f = shape.face(15, dz);
				command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:stone", sx + f, y + 15, sz + dz), 1);
			}
		}
		command(view(sx + mouth + 30, ty + 6, sz + 24, sx + mouth, ty, sz), 20);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Tags:[\"df_prey\"]}",
				sx + mouth - 5, ty, sz), 5);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", sx + mouth + 30, ty + 6, sz), 20);
		server(level -> {
			DragonConfig.WALL_TIME.set(1200);
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tactics.tryWall(prey.get(0)), name + ": it flies in to grip the wall");
		});
		boolean[] gripped = {false};
		// the phases it went through and the faces it was on, for the report
		StringBuilder trail = new StringBuilder();
		String[] last = {""};
		serverUntil(500, level -> {
			EnderDragon dragon = aiDragon(level);
			EnderDragonPhase<?> phase = dragon == null ? null : dragon.getPhaseManager().getCurrentPhase().getPhase();
			note(trail, last, dragon);
			gripped[0] = dragon != null && DragonswornDragon.brain(dragon).face().wall()
					&& (phase == DragonPhases.GROUND_FIGHT || phase == DragonPhases.BREATH_STREAM);
			return gripped[0];
		});
		for (int i = 0; i < 30; i++) server(level -> note(trail, last, aiDragon(level)));
		server(level -> REPORT.add("INFO " + name + ":" + trail));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(gripped[0], name + ": it gripped the wall");
			if (dragon == null || !gripped[0]) return;
			DragonBrain brain = DragonswornDragon.brain(dragon);
			check(frame.test(brain.face()), name + ": on the wall's own frame (" + brain.face() + ")");
			check(brain.standsOn(), name + ": a hind heel rests on a foothold");
			check(Math.abs(Mth.wrapDegrees(dragon.getYRot() - brain.face().upYaw())) < 10.0, name + ": upright, heading up the face");
		});
		// hanging between attacks (an attack swings the body and the tail with it)
		serverUntil(200, level -> {
			EnderDragon dragon = aiDragon(level);
			return dragon == null || DragonswornDragon.brain(dragon).action() == null
					&& dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.GROUND_FIGHT;
		});
		command("time set day", 20);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return;
			// the tail's tip (its last hitbox) hangs down along the face, not out from it
			double[] n = DragonswornDragon.brain(dragon).face().normal;
			var tip = dragon.getSubEntities()[5].getBoundingBox().getCenter();
			double oy = tip.y - dragon.getY();
			// how far its tip is off the wall: straight in along the face's normal to the first block
			double gap = 0.0;
			while (gap < 8.0 && level.getBlockState(net.minecraft.core.BlockPos.containing(tip.x - n[0] * gap, tip.y - n[1] * gap, tip.z - n[2] * gap)).isAir()) gap += 0.25;
			check(gap <= 3.0 && oy < 0.0, name + ": the tail hangs down along the wall (its tip " + String.format(Locale.ROOT, "%.1f", gap)
					+ " off it, " + String.format(Locale.ROOT, "%.1f", -oy) + " below)");
		});
		shoot(view(sx + mouth + 6, ty - 2, sz + 22, sx + mouth, ty - 2, sz), "climb-shape-" + name + "-side", 10);
		shoot(view(sx + mouth + 22, ty - 1, sz, sx + mouth, ty - 3, sz), "climb-shape-" + name + "-front", 30);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && DragonswornDragon.brain(dragon).face().wall() && DragonswornDragon.brain(dragon).standsOn(),
					name + ": still holding on, its foothold under it");
			DragonConfig.WALL_TIME.set(DragonConfig.WALL_TIME.defaultValue());
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/**
	 * A tower with a ledge round it: the dragon put on each of its four faces in turn, hanging with nobody to
	 * fight, its head photographed from in front of the face to either side (the camera is the player it
	 * watches): sideways along the face, level, crown up, on every face alike.
	 */
	static void faces(int px, int y, int pz) {
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", px - 5, y + 12, pz - 5, px + 5, y + 12, pz + 5), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:obsidian", px - 4, y, pz - 4, px + 4, y + 30, pz + 4), 2);
		server(level -> {
			DragonConfig.WALL_TIME.set(1200);
			DragonConfig.WALL_REST.set(1200);
		});
		for (Surface.Face face : new Surface.Face[]{Surface.Face.NORTH, Surface.Face.SOUTH, Surface.Face.WEST, Surface.Face.EAST}) {
			int nx = (int) Math.round(face.nx), nz = (int) Math.round(face.nz), tx = -nz, tz = nx;
			command(view(px + nx * 30, y + 20, pz + nz * 30, px, y + 16, pz), 10);
			// hovering in front of the face (as the wall approach leaves it), then it flies in and grips
			command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", px + nx * 11, y + 17, pz + nz * 11), 20);
			boolean[] put = {false};
			String[] got = {"no dragon"};
			// (tried until the dragon is there and placed)
			serverUntil(40, level -> {
				if (put[0]) return true;
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return false;
				got[0] = "no site";
				SurfaceSites.Site site = new SurfaceSites(DragonswornDragon.brain(dragon).grid()).near(px + nx * 5.0, y + 17.0, pz + nz * 5.0,
						0.0, 5.0, 0.0, 6.0, true, 0, dragon.getX(), dragon.getY(), dragon.getZ());
				if (site != null) got[0] = site.toString();
				if (site == null || site.face() != face) return false;
				HopPhase.start(dragon, site, null);
				put[0] = true;
				return true;
			});
			server(level -> check(put[0], "faces: put on the " + face + " face (" + got[0] + ")"));
			if (face == Surface.Face.EAST) {
				// the landing side on, frame by frame: hover, in, grip; no turn of the body beyond the poses'
				for (int f = 0; f < 24; f++) {
					shoot(view(px + nx * 7 + tx * 20, y + 17, pz + nz * 7 + tz * 20, px + nx * 5, y + 16, pz + nz * 5),
							String.format(Locale.ROOT, "climb-land-%02d", f), 2);
				}
			}
			serverUntil(100, level -> {
				EnderDragon dragon = aiDragon(level);
				return dragon != null && dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.GROUND_FIGHT;
			});
			for (int side = -1; side <= 1; side += 2) {
				int ex = px + nx * 13 + tx * 7 * side, ez = pz + nz * 13 + tz * 7 * side;
				command(view(ex, y + 18, ez, px + nx * 3, y + 17, pz + nz * 3), 30);
				shoot(view(ex, y + 18, ez, px + nx * 3, y + 17, pz + nz * 3),
						"climb-face-" + face.name().toLowerCase(Locale.ROOT) + (side < 0 ? "-a" : "-b"), 4);
			}
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				check(dragon != null && DragonswornDragon.brain(dragon).face() == face, "faces: hanging on the " + face + " face");
			});
			if (face == Surface.Face.EAST) {
				// and off again, frame by frame: out from the face into the flare, which eases out as it flies off
				server(level -> {
					EnderDragon dragon = aiDragon(level);
					if (dragon != null) dragon.getPhaseManager().setPhase(DragonPhases.LIFTOFF);
				});
				for (int f = 0; f < 12; f++) {
					shoot(view(px + nx * 7 + tx * 22, y + 19, pz + nz * 7 + tz * 22, px + nx * 7, y + 18, pz + nz * 7),
							String.format(Locale.ROOT, "climb-takeoff-%02d", f), 3);
				}
			}
			command("kill @e[tag=df_ai]", 5);
		}
		server(level -> {
			DragonConfig.WALL_TIME.set(DragonConfig.WALL_TIME.defaultValue());
			DragonConfig.WALL_REST.set(DragonConfig.WALL_REST.defaultValue());
		});
	}

	/** A cliff 40 high, its face looking east (at x = sx + 1), a 1 by 2 tunnel dug 6 blocks into it at ty, a husk deep in it. */
	private static void cliff(int sx, int y, int sz, int ty, boolean invulnerable) {
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", sx - 12, y, sz - 20, sx, y + 40, sz + 20), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", sx - 6, ty, sz, sx, ty + 1, sz), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,%sTags:[\"df_prey\"]}", sx - 5, ty, sz,
				invulnerable ? "Invulnerable:1b," : ""), 5);
	}

	/** A ledge a block out of the cliff's face, 7 below the tunnel, 9 long: where the dragon's feet go. */
	private static String ledge(int sx, int sz, int ty, String block) {
		return String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:%s", sx + 1, ty - 7, sz - 4, sx + 1, ty - 7, sz + 4, block);
	}

	/**
	 * A cliff 40 high, its face looking east, a 1 by 2 tunnel dug 6 blocks into it 22 up, a husk deep in
	 * it. Sheer, the dragon may not grip it; then a ledge a block out runs below the tunnel's mouth: it must
	 * come to grip the cliff standing on it (no ground near the tunnel), and hurt the husk from there.
	 */
	static void tunnel(int sx, int y, int sz) {
		int ty = y + 22;
		command(view(sx + 30, ty + 6, sz + 22, sx, ty, sz), 30);
		cliff(sx, y, sz, ty, false);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", sx + 40, ty + 6, sz), 20);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && !prey.isEmpty() && !DragonswornDragon.brain(dragon).tactics.tryWall(prey.get(0)),
					"tunnel: nothing for its feet on the sheer cliff, it does not grip it");
		});
		command(ledge(sx, sz, ty, "stone"), 5);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tactics.tryGroundAssault(prey.get(0)),
					"tunnel: somewhere to come down by the husk in the cliff");
			check(dragon != null && dragon.getPhaseManager().getCurrentPhase() instanceof WallApproachPhase, "tunnel: it flies in to grip the cliff");
		});
		// gripped the face, bit with its jaws, breathed down the tunnel, the hurt husk
		boolean[] seen = new boolean[3];
		float[] health = {Float.NaN};
		double[] heels = {Double.NaN};
		int[] stay = {0};
		for (int i = 0; i < 80; i++) {
			int n = i;
			if (i % 8 == 0) shoot(view(sx + 20, ty + 3, sz + 16, sx + 1, ty + 2, sz), String.format(Locale.ROOT, "climb-tunnel-%02d", n), 4);
			// the head from the side, close: crown up, looking out from the face
			else if (i % 8 == 4) shoot(view(sx + 7, ty + 1, sz + 9, sx + 3, ty, sz), String.format(Locale.ROOT, "climb-head-%02d", n), 4);
			else command("time set day", 6);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				DragonBrain brain = DragonswornDragon.brain(dragon);
				EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
				boolean onWall = brain.face() == Surface.Face.EAST && (phase == DragonPhases.GROUND_FIGHT || phase == DragonPhases.BREATH_STREAM);
				seen[0] |= onWall;
				if (onWall && Double.isNaN(heels[0])) heels[0] = dragon.getY() - SurfaceSites.HEEL;
				stay[0] = Math.max(stay[0], brain.wallTicks());
				seen[1] |= onWall && brain.action() != null && brain.action().bites();
				seen[2] |= onWall && phase == DragonPhases.BREATH_STREAM;
				List<? extends Husk> prey = prey(level);
				health[0] = prey.isEmpty() ? 0.0F : prey.get(0).getHealth();
			});
		}
		server(level -> {
			check(seen[0], "tunnel: it gripped the cliff's face");
			check(Math.abs(heels[0] - (ty - 6)) < 0.3, "tunnel: it stood on the ledge (heels at " + String.format(Locale.ROOT, "%.2f", heels[0]) + ", its top " + (ty - 6) + ")");
			check(seen[1] || seen[2], "tunnel: it bit into the tunnel or breathed down it (bite " + seen[1] + ", breath " + seen[2] + ")");
			check(health[0] < 20.0F, "tunnel: the husk in the tunnel was hurt (" + health[0] + ")");
			// an attack under way when the time is up is finished first
			check(stay[0] <= DragonConfig.WALL_TIME.get() + 40, "tunnel: only a short while on the wall (" + stay[0] + " ticks; wall_time "
					+ DragonConfig.WALL_TIME.get() + " and the attack under way)");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/**
	 * The tunnel's cliff and ledge again, the husk out of harm's way: once the dragon stands on the ledge the
	 * ledge is broken away under it. It must fall off the wall and fly.
	 */
	static void fall(int sx, int y, int sz) {
		int ty = y + 22;
		command(view(sx + 30, ty + 6, sz + 22, sx, ty, sz), 30);
		cliff(sx, y, sz, ty, true);
		command(ledge(sx, sz, ty, "stone"), 5);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", sx + 40, ty + 6, sz), 20);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tactics.tryGroundAssault(prey.get(0)),
					"fall: it flies in to grip the cliff by the husk");
		});
		boolean[] gripped = {false}, fell = {false};
		serverUntil(400, level -> {
			EnderDragon dragon = aiDragon(level);
			EnderDragonPhase<?> phase = dragon == null ? null : dragon.getPhaseManager().getCurrentPhase().getPhase();
			gripped[0] = dragon != null && DragonswornDragon.brain(dragon).face().wall()
					&& (phase == DragonPhases.GROUND_FIGHT || phase == DragonPhases.BREATH_STREAM);
			return gripped[0];
		});
		server(level -> check(gripped[0], "fall: it gripped the cliff standing on the ledge"));
		// nobody left to fight, the breath over: hanging, it looks sideways along the face (level, crown up), turning
		// a little toward the player (the camera); it stays a while longer for the photographs
		server(level -> {
			DragonConfig.WALL_TIME.set(1200);
			DragonConfig.WALL_REST.set(1200);
		});
		command("kill @e[tag=df_prey]", 2);
		serverUntil(200, level -> {
			EnderDragon dragon = aiDragon(level);
			return dragon != null && DragonswornDragon.brain(dragon).action() == null
					&& dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.GROUND_FIGHT;
		});
		int[][] eyes = {{sx + 16, y + 1, sz + 2}, {sx + 18, ty - 3, sz}, {sx + 7, ty - 3, sz + 16}, {sx + 9, ty + 10, sz - 8}};
		for (int k = 0; k < eyes.length; k++) {
			command(view(eyes[k][0], eyes[k][1], eyes[k][2], sx + 2, ty - 2, sz), 30);
			shoot(view(eyes[k][0], eyes[k][1], eyes[k][2], sx + 2, ty - 2, sz), "climb-look-" + k, 4);
		}
		shoot(view(sx + 20, ty + 3, sz + 16, sx + 1, ty - 4, sz), "climb-fall-0", 4);
		server(level -> {
			DragonConfig.WALL_TIME.set(DragonConfig.WALL_TIME.defaultValue());
			DragonConfig.WALL_REST.set(DragonConfig.WALL_REST.defaultValue());
		});
		command(ledge(sx, sz, ty, "air"), 1);
		serverUntil(40, level -> {
			EnderDragon dragon = aiDragon(level);
			fell[0] = dragon != null && !DragonswornDragon.brain(dragon).face().wall()
					&& dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.LIFTOFF;
			return fell[0];
		});
		shoot(view(sx + 20, ty + 3, sz + 16, sx + 1, ty - 4, sz), "climb-fall-1", 6);
		server(level -> check(fell[0], "fall: the ledge broken away under its feet, it fell off the wall and flew"));
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/**
	 * An obsidian pillar 9 by 9 and 25 high, a husk on top. The dragon, on the ground 14 blocks from it,
	 * fights the husk: it may not climb onto the pillar from the ground, so it must take off.
	 */
	static void pillar(int px, int y, int pz) {
		int top = y + 25;
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:obsidian", px - 4, y, pz - 4, px + 4, top - 1, pz + 4), 2);
		command(view(px + 34, top - 4, pz + 30, px, top - 12, pz), 30);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", px, top, pz), 5);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", px + 18, y, pz), 20);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			if (dragon != null && !prey.isEmpty()) GroundFightPhase.start(dragon, prey.get(0), false);
			check(fight(level) != null, "pillar: it fights the husk from the ground");
		});
		// on the pillar's side before it ever left the ground; off the ground
		boolean[] seen = new boolean[2];
		for (int i = 0; i < 80; i++) {
			int n = i;
			if (i % 10 == 0) shoot(view(px + 24, top - 6, pz + 20, px, top - 12, pz), String.format(Locale.ROOT, "climb-pillar-%03d", n), 4);
			else command("time set day", 6);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
				seen[0] |= DragonswornDragon.brain(dragon).face().wall();
				seen[1] |= phase == DragonPhases.LIFTOFF || phase == EnderDragonPhase.HOLDING_PATTERN;
			});
		}
		server(level -> {
			check(!seen[0], "pillar: it never climbed onto the pillar's side from the ground");
			check(seen[1], "pillar: out of its reach up there, it took off");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}
}
