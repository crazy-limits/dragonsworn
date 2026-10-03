package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.phase.FlybyBitePhase;
import crazylimits.dragonsworn.mc.phase.HoverAttackPhase;
import crazylimits.dragonsworn.mc.phase.RoamPhase;
import crazylimits.dragonsworn.nav.LandingSite;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code air}: the attacks from the air on a husk on a lone pillar and one hanging in the air. */
final class AirStage {
	private AirStage() {
	}


	/**
	 * Attacks from the air, where the dragon cannot land by its prey: a husk on a lone 16-block pillar (a
	 * player pillaring up to a crystal) and one hanging in the air (a player on elytra). Each gets the
	 * fly-by bite (it must hurt the husk, and on the pillar knock it off), the hover bite and the hover
	 * breath. Then the wild AI must pick such an attack by itself at each of them, and never try to land.
	 */
	static void air(int x, int y, int z) {
		int top = y + 16, fx = x + 90, fy = y + 30;
		command(view(x - 34, y + 20, z - 10, x, top, z), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x, y, z, x, top - 1, z), 2);
		// with its AI (a knock must move it), slowed to a standstill so it stays on its pillar
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {PersistenceRequired:1b,Tags:[\"df_prey\"],"
				+ "attributes:[{id:\"minecraft:max_health\",base:500.0}],Health:500f,"
				+ "active_effects:[{id:\"minecraft:slowness\",amplifier:10b,duration:-1,show_particles:0b}]}", x, top, z), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Tags:[\"df_flier\"],"
				+ "attributes:[{id:\"minecraft:max_health\",base:500.0}],Health:500f}", fx, fy, z), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[180f,0f]}", x, y + 24, z + 70), 60);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && !prey(level).isEmpty() && !flier(level).isEmpty(), "air: a dragon, a husk on a pillar and a husk in the air");
			if (dragon == null || prey(level).isEmpty() || flier(level).isEmpty()) return;
			DragonBrain brain = DragonswornDragon.brain(dragon);
			check(!brain.tactics.airborne(prey(level).get(0)) && brain.tactics.airborne(flier(level).get(0)), "air: the husk on the pillar stands, the other is in the air");
			int[] site = new LandingSite(brain.grid()).find(x + 0.5, z + 0.5, 8, 17, 12, dragon.getX(), dragon.getZ());
			check(site == null || Math.abs(site[1] - top) > 5, "air: nowhere to land by the husk on the pillar");
		});
		Vec3[] pillar = {new Vec3(x + 0.5, top, z + 0.5)}, hanging = {new Vec3(fx + 0.5, fy, z + 0.5)};
		airAttack("flyby-pillar", Script::prey, pillar, (dragon, husk) -> FlybyBitePhase.start(dragon, husk), DragonAnim.GLIDE_BITE, true);
		airAttack("hoverbite-pillar", Script::prey, pillar, (dragon, husk) -> HoverAttackPhase.start(dragon, husk, HoverAttackPhase.Mode.BITE), DragonAnim.HOVER_BITE, false);
		airAttack("hoverbreath-pillar", Script::prey, pillar, (dragon, husk) -> HoverAttackPhase.start(dragon, husk, HoverAttackPhase.Mode.BREATH), DragonAnim.HOVER_BREATH, false);
		airAttack("flyby-air", AirStage::flier, hanging, (dragon, husk) -> FlybyBitePhase.start(dragon, husk), DragonAnim.GLIDE_BITE, false);
		airAttack("hoverbite-air", AirStage::flier, hanging, (dragon, husk) -> HoverAttackPhase.start(dragon, husk, HoverAttackPhase.Mode.BITE), DragonAnim.HOVER_BITE, false);
		airAttack("hoverbreath-air", AirStage::flier, hanging, (dragon, husk) -> HoverAttackPhase.start(dragon, husk, HoverAttackPhase.Mode.BREATH), DragonAnim.HOVER_BREATH, false);
		airChoice("pillar", Script::prey, pillar, Set.of(DragonPhases.FLYBY_BITE, DragonPhases.HOVER_ATTACK, DragonPhases.BREATH_PASS, DragonPhases.SNATCH));
		airChoice("air", AirStage::flier, hanging, Set.of(DragonPhases.FLYBY_BITE, DragonPhases.HOVER_ATTACK));
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
		command("kill @e[tag=df_flier]", 2);
	}

	static List<? extends Husk> flier(ServerLevel level) {
		return level.getEntities(EntityTypes.HUSK, e -> e.entityTags().contains("df_flier"));
	}

	/** Puts the husk back where it belongs, healed and still, and the dragon in the air 70 blocks off, roaming. */
	static Husk reset(ServerLevel level, java.util.function.Function<ServerLevel, List<? extends Husk>> husks, Vec3 at, boolean dragonToo) {
		EnderDragon dragon = aiDragon(level);
		List<? extends Husk> found = husks.apply(level);
		if (dragon == null || found.isEmpty()) return null;
		Husk husk = found.get(0);
		husk.teleportTo(at.x, at.y, at.z);
		husk.setDeltaMovement(Vec3.ZERO);
		husk.setHealth(husk.getMaxHealth());
		husk.clearFire();
		if (dragonToo) {
			// a fresh roam (setting the phase it is in already would keep whatever it was hunting)
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
			dragon.getPhaseManager().setPhase(DragonPhases.ROAM);
			dragon.teleportTo(at.x - 10, at.y + 10, at.z + 70);
			dragon.setDeltaMovement(new Vec3(0.0, 0.0, -0.8));
		}
		return husk;
	}

	/**
	 * One attack, started by hand at the husk: it must play its animation and hurt the husk (and, when
	 * {@code knock}, knock it off its pillar). Frames are grabbed from beside the husk while it plays.
	 */
	static void airAttack(String name, java.util.function.Function<ServerLevel, List<? extends Husk>> husks, Vec3[] at,
			java.util.function.BiPredicate<EnderDragon, Husk> start, DragonAnim anim, boolean knock) {
		float[] health = new float[2];
		boolean[] started = new boolean[1], played = new boolean[1];
		double[] moved = new double[1];
		String[] phase = {""};
		server(level -> {
			Husk husk = reset(level, husks, at[0], true);
			EnderDragon dragon = aiDragon(level);
			started[0] = husk != null && start.test(dragon, husk);
			if (husk != null) health[0] = health[1] = husk.getHealth();
			if (dragon != null) phase[0] = dragon.getPhaseManager().getCurrentPhase().getPhase().toString();
		});
		int max = 700, end = STEPS.size() + max;
		int[] frames = new int[1];
		for (int i = 0; i < max; i++) {
			STEPS.add(new Step(1, mc -> {
				mc.getSingleplayerServer().executeBlocking(() -> {
					ServerLevel level = mc.getSingleplayerServer().overworld();
					EnderDragon dragon = aiDragon(level);
					List<? extends Husk> found = husks.apply(level);
					if (dragon == null || found.isEmpty()) {
						index = end;
						return;
					}
					Husk husk = found.get(0);
					DragonBrain brain = DragonswornDragon.brain(dragon);
					played[0] |= brain.action() == anim;
					health[1] = Math.min(health[1], husk.getHealth());
					moved[0] = Math.max(moved[0], Math.hypot(husk.getX() - at[0].x, husk.getZ() - at[0].z));
					// over once the attack's phase is
					if (!dragon.getPhaseManager().getCurrentPhase().getPhase().toString().equals(phase[0])) index = end;
				});
				EnderDragon dragon = clientAiDragon(mc);
				if (dragon == null) return;
				// beside the husk, the dragon in view behind it
				Vec3 h = at[0];
				Vec3 away = new Vec3(dragon.getX() - h.x, 0.0, dragon.getZ() - h.z);
				Vec3 side = away.lengthSqr() > 1e-4 ? new Vec3(-away.z, 0.0, away.x).normalize() : new Vec3(1.0, 0.0, 0.0);
				mc.player.connection.sendCommand(view(h.x + side.x * 22 - away.normalize().x * 6, h.y + 6, h.z + side.z * 22 - away.normalize().z * 6,
						(h.x + dragon.getX()) / 2, (h.y + dragon.getY()) / 2 + 1, (h.z + dragon.getZ()) / 2));
				if (DragonswornDragon.brain(dragon).action() == anim && frames[0] < 30 && dragon.tickCount % 3 == 0) {
					Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "df-%s-%02d.png", name, frames[0]++), mc.gameRenderer.mainRenderTarget(), 1,
							message -> LOG.info("{}", message.getString()));
				}
			}));
		}
		server(level -> {
			check(started[0], name + ": the attack starts");
			check(played[0], name + ": it plays " + anim.name().toLowerCase(Locale.ROOT));
			check(health[1] < health[0], String.format(Locale.ROOT, "%s: the husk is hurt (%.0f of %.0f health lost)", name, health[0] - health[1], health[0]));
			if (knock) check(moved[0] > 1.5, String.format(Locale.ROOT, "%s: the hit knocks it off its pillar (%.1f blocks)", name, moved[0]));
		});
	}

	/**
	 * The wild AI's own choice: the husk hurts the dragon (so it is the target) and the dragon must attack
	 * it with one of {@code allowed} (or its fireball barrage), without ever trying to land.
	 */
	static void airChoice(String name, java.util.function.Function<ServerLevel, List<? extends Husk>> husks, Vec3[] at, Set<EnderDragonPhase<?>> allowed) {
		Set<String> seen = new LinkedHashSet<>();
		boolean[] landing = new boolean[1], chose = new boolean[1];
		server(level -> {
			Husk husk = reset(level, husks, at[0], true);
			EnderDragon dragon = aiDragon(level);
			if (husk == null || dragon == null) return;
			// still on its pillar for the choice (nothing shoves a mob without AI off it)
			husk.setNoAi(true);
			DragonswornDragon.brain(dragon).combat.hurtBy(level.damageSources().mobAttack(husk), 2);
		});
		serverUntil(900, level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null) return true;
			var phase = dragon.getPhaseManager().getCurrentPhase();
			seen.add(phase.getPhase().toString().replaceAll(" .*", ""));
			boolean lands = phase.getPhase() == DragonPhases.GROUND_APPROACH || DragonswornDragon.brain(dragon).onGround();
			if (lands && !landing[0] && !husks.apply(level).isEmpty()) {
				Husk husk = husks.apply(level).get(0);
				REPORT.add(String.format(Locale.ROOT, "INFO air choice at the %s husk: it lands, the husk at %.1f %.1f %.1f (placed at %.1f %.1f %.1f)",
						name, husk.getX(), husk.getY(), husk.getZ(), at[0].x, at[0].y, at[0].z));
			}
			landing[0] |= lands;
			chose[0] = allowed.contains(phase.getPhase()) || phase instanceof RoamPhase roam && !roam.idle();
			if (chose[0] && !husks.apply(level).isEmpty()) {
				Husk husk = husks.apply(level).get(0);
				REPORT.add(String.format(Locale.ROOT, "INFO air choice at the %s husk: %s%s, %.0f blocks off, target airborne %b",
						name, phase.getPhase(), phase instanceof RoamPhase roam ? " " + roam.hunt() : "", husk.distanceTo(dragon),
						DragonswornDragon.brain(dragon).tactics.airborne(husk)));
			}
			return chose[0];
		});
		server(level -> {
			REPORT.add("INFO air choice at the " + name + " husk: phases " + seen);
			check(chose[0], "air: the wild dragon attacks the " + name + " husk from the air by itself");
			check(!landing[0], "air: and never tries to land by it");
		});
	}
}
