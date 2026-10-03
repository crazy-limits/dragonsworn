package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.LevelGrid;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import crazylimits.dragonsworn.nav.BlockGrid;
import crazylimits.dragonsworn.nav.LandingSite;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code landing}: the live AI's ground assault and takeoff, and the running landing. */
final class LandingStage {
	private LandingStage() {
	}


	/**
	 * The AI, live: a wild dragon summoned inside a cage of leaves next to a stone pillar. It must smash
	 * out through the leaves and leave the stone alone; then it is sent to land beside a husk, bite it on
	 * the ground, and take off again with the jump. Hitboxes are photographed on the ground and in flight.
	 */
	static void liveDragon(int lx, int y, int lz) {
		int pillar = 9 * 41 * 9;
		// stand there first: commands only build in loaded chunks
		command(view(lx - 40, y + 20, lz, lx, y + 12, lz), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", lx + 12, y, lz - 4, lx + 20, y + 40, lz + 4), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:oak_leaves[persistent=true] hollow", lx - 7, y + 12, lz - 7, lx + 7, y + 26, lz + 7), 2);
		int[] leaves = new int[1];
		server(level -> leaves[0] = count(level, lx - 7, y + 12, lz - 7, lx + 7, y + 26, lz + 7, Blocks.OAK_LEAVES));
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", lx, y + 17, lz), 40);
		for (int i = 0; i < 12; i++) track(String.format(Locale.ROOT, "live-roam-%02d", i), 15, 34);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, "the wild dragon is alive");
			if (dragon == null) return;
			int left = count(level, lx - 7, y + 12, lz - 7, lx + 7, y + 26, lz + 7, Blocks.OAK_LEAVES);
			check(left < leaves[0], "it smashed through the leaves around it (" + (leaves[0] - left) + " broken)");
			int stone = count(level, lx + 12, y, lz - 4, lx + 20, y + 40, lz + 4, Blocks.STONE);
			check(stone == pillar, "the stone pillar beside it is intact (" + stone + "/" + pillar + ")");
			check(DragonswornDragon.brain(dragon).context() == DragonBrain.Context.WILD, "a summoned dragon is wild");
			check(dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING, "it left its hover to roam");
		});

		// the ground assault: the husk stands still, so the aimed blows land
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", lx - 40, y, lz + 10), 5);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityTypes.HUSK, e -> e.entityTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tactics.tryGroundAssault(prey.get(0)),
					"there is room to land beside the husk");
		});
		Set<String> phases = new LinkedHashSet<>();
		for (int i = 0; i < 44; i++) {
			track(String.format(Locale.ROOT, "live-ground-%02d", i), 10, 30);
			STEPS.add(new Step(1, mc -> {
				EnderDragon dragon = clientAiDragon(mc);
				if (dragon != null) phases.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", ""));
			}));
		}
		STEPS.add(new Step(1, mc -> mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON)));
		track("live-ground-hitboxes", 3, 26);
		STEPS.add(new Step(1, mc -> mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER)));
		server(level -> {
			REPORT.add("INFO phases seen: " + phases);
			check(phases.contains("DragonswornGroundApproach"), "it flew down to the landing site");
			check(phases.contains("DragonswornGroundFight"), "it landed and fought on the ground");
			List<? extends Husk> prey = level.getEntities(EntityTypes.HUSK, e -> e.entityTags().contains("df_prey"));
			check(prey.isEmpty() || prey.get(0).getHealth() < prey.get(0).getMaxHealth(), "an aimed blow (bite or tail) hit the husk, which stood still");
		});

		// the takeoff: leaving the ground is always the jump, then a climb on the wings
		double[] groundY = new double[1];
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return;
			groundY[0] = dragon.getY();
			dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
			check(dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.LIFTOFF, "a takeoff from the ground is the jump");
		});
		for (int i = 0; i < 10; i++) track("live-takeoff-" + i, 5, 30);
		for (int i = 0; i < 6; i++) track("live-climb-" + i, 15, 34);
		STEPS.add(new Step(1, mc -> mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON)));
		track("live-flight-hitboxes", 3, 34);
		STEPS.add(new Step(1, mc -> mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER)));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && dragon.getY() > groundY[0] + 6, "it climbed after the jump ("
					+ (dragon == null ? "gone" : String.format(Locale.ROOT, "%.1f", dragon.getY() - groundY[0])) + " blocks)");
			int stone = count(level, lx + 12, y, lz - 4, lx + 20, y + 40, lz + 4, Blocks.STONE);
			check(stone == pillar, "the pillar is still intact at the end (" + stone + "/" + pillar + ")");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/**
	 * The landing at speed: a wild dragon in fast flight is sent to land on flat ground 90 blocks ahead of
	 * it. It must line up, glide in and land running ({@link DragonAnim#LAND}): feet striking the ground,
	 * a skid, and stopping on the site, then stand there.
	 */
	static void runningLanding(int rx, int y, int rz) {
		command(view(rx - 40, y + 20, rz, rx, y + 12, rz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", rx, y + 26, rz), 20);
		// let it get going
		for (int i = 0; i < 4; i++) track("landing-cruise-" + i, 20, 34);
		int[] site = new int[3];
		boolean[] sent = new boolean[1];
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return;
			double yaw = Math.toRadians(dragon.getYRot());
			int sx = Mth.floor(dragon.getX() - Math.sin(yaw) * 90), sz = Mth.floor(dragon.getZ() + Math.cos(yaw) * 90);
			LevelGrid grid = new LevelGrid(level);
			int sy = new LandingSite(grid).fits(sx, sz);
			REPORT.add(String.format(Locale.ROOT, "INFO landing: speed %.2f, site %d %d %d", DragonswornDragon.brain(dragon).horizontalSpeed(), sx, sy, sz));
			if (sy == BlockGrid.NO_GROUND) return;
			site[0] = sx;
			site[1] = sy;
			site[2] = sz;
			GroundApproachPhase.start(dragon, new int[]{sx, sy, sz}, null);
			sent[0] = true;
		});
		boolean[] ran = new boolean[1], struck = new boolean[1], planned = new boolean[1];
		double[] fastest = new double[1], stop = {Double.NaN, 0.0};
		String[] why = new String[1];
		for (int i = 0; i < 45; i++) {
			track(String.format(Locale.ROOT, "landing-%02d", i), 6, 24, 5);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				DragonBrain brain = DragonswornDragon.brain(dragon);
				if (dragon.getPhaseManager().getCurrentPhase() instanceof GroundApproachPhase approach) {
					planned[0] |= approach.runningIn();
					if (approach.fallback() != null && why[0] == null) why[0] = approach.fallback();
				}
				if (dragon.getPhaseManager().getCurrentPhase() instanceof GroundApproachPhase approach && approach.landing()) {
					if (!ran[0]) fastest[0] = brain.horizontalSpeed();
					ran[0] = true;
					struck[0] |= approach.grounded() && Math.abs(dragon.getY() - site[1]) < 0.01;
				}
				// where the landing left it (afterwards it rests, and may walk about)
				if (ran[0] && Double.isNaN(stop[0]) && brain.onGround()) {
					stop[0] = dragon.getX();
					stop[1] = dragon.getZ();
				}
			});
		}
		for (int i = 0; i < 3; i++) track("landing-stand-" + i, 10, 30, 5);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(sent[0], "there is a landing site ahead of the dragon");
			check(planned[0], "fast enough, with a clear runway, it came in to land running");
			if (why[0] != null) REPORT.add("INFO the running approach hovered down instead: " + why[0]);
			check(ran[0], String.format(Locale.ROOT, "it landed running (the landing started at %.2f blocks/tick)", fastest[0]));
			check(struck[0], "its feet struck the ground on the site's level");
			check(dragon != null && DragonswornDragon.brain(dragon).onGround(), "it stands on the ground after the landing");
			double off = Math.hypot(stop[0] - site[0] - 0.5, stop[1] - site[2] - 0.5);
			check(off < 2.5, String.format(Locale.ROOT, "it skidded to a stop on the site (%.1f blocks off)", off));
		});
		command("kill @e[tag=df_ai]", 2);
	}
}
