package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;

import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code grabs}: the snatch (talons) and the seize (jaws), and letting go. */
final class GrabsStage {
	private GrabsStage() {
	}


	/**
	 * The two holds, live. The snatch: a wild dragon is sent at a husk on open ground; it must dive, take
	 * it in its talons, carry it up and drop it from high up. The seize: a dragon on the ground takes a
	 * husk in its jaws and shakes and chews it; someone else hitting its head makes it drop it, and a
	 * hold left alone ends with the husk flung away.
	 */
	static void grabs(int gx, int y, int gz) {
		STEPS.add(new Step(1, mc -> Script.hideGui(mc, true)));
		command(view(gx - 40, y + 20, gz, gx, y + 8, gz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", gx, y, gz), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", gx, y + 24, gz + 70), 60);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && !prey(level).isEmpty() && SnatchPhase.start(dragon, prey(level).get(0)), "the dragon goes for a snatch at the husk");
		});
		boolean[] reached = new boolean[1], held = new boolean[1];
		double[] peak = {Double.NEGATIVE_INFINITY};
		for (int i = 0; i < 70; i++) {
			if (i < 12) closeUp(19, String.format(Locale.ROOT, "snatch-reach-%02d", i), 1, 10.0);    // the legs thrown forward
			if (i >= 12 && i % 3 == 0) closeUp(0, String.format(Locale.ROOT, "snatch-close-%02d", i), 1, 9.0);
			if (i >= 10) talonView(String.format(Locale.ROOT, "snatch-talon-%02d", i));
			track(String.format(Locale.ROOT, "snatch-%02d", i), 5, 18, 2);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null || prey(level).isEmpty()) return;
				Grip.Hold hold = DragonswornDragon.brain(dragon).prey.hold();
				reached[0] |= hold == Grip.Hold.REACH;
				held[0] |= hold == Grip.Hold.TALON;
				if (hold == Grip.Hold.TALON) peak[0] = Math.max(peak[0], prey(level).get(0).getY() - y);
			});
		}
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(reached[0], "it reached down for the husk as it dived");
			check(held[0], "it took the husk in its talons");
			check(peak[0] > 20.0, String.format(Locale.ROOT, "it carried the husk up (%.1f blocks)", peak[0]));
			check(dragon != null && !prey(level).isEmpty() && PreyHold.carrier(prey(level).get(0)) == null
					&& DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.NONE, "and dropped it");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);

		// the seize, on the ground: a husk with plenty of health to chew on
		int sx = gx + 100;
		command(view(sx - 20, y + 8, gz, sx, y + 3, gz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"],"
				+ "attributes:[{id:\"minecraft:max_health\",base:200.0}],Health:200f}", sx, y, gz + 8), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_helper\"]}", sx - 6, y, gz - 6), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[180f,0f]}", sx, y, gz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon != null && !prey(level).isEmpty()) GroundFightPhase.start(dragon, prey(level).get(0));
		});
		STEPS.add(new Step(70, mc -> {}));      // its roar on landing
		// a seize is a bite first: the husk steps out of the jaws' way once the aim is committed, and nothing is held
		int[] after = {-1};
		boolean[] dodgeHeld = new boolean[1];
		float[] health = new float[1];
		server(level -> {
			if (fight(level) != null) fight(level).seizeNext();
		});
		serverUntil(160, level -> {
			GroundFightPhase fight = fight(level);
			if (fight == null || prey(level).isEmpty()) return true;
			if (after[0] < 0 && fight.seizeCommitted()) {
				Husk husk = prey(level).get(0);
				health[0] = husk.getHealth();
				husk.teleportTo(husk.getX() + 9.0, husk.getY(), husk.getZ());
				after[0] = 0;
			}
			if (after[0] >= 0) dodgeHeld[0] |= DragonswornDragon.brain(aiDragon(level)).prey.hold() != Grip.Hold.NONE;
			// the jaws close REACTION_TICKS after the commit: a few more and it is decided
			return after[0] >= 0 && ++after[0] > 15;
		});
		server(level -> {
			check(after[0] > 15, "the dragon goes for a seize");
			check(!dodgeHeld[0] && !prey(level).isEmpty() && PreyHold.carrier(prey(level).get(0)) == null,
					"dodged, its jaws close on nothing: the husk is not taken");
			check(!prey(level).isEmpty() && prey(level).get(0).getHealth() == health[0], "nor hurt by it");
			// back in front of it, and the next bite is a seize again
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty() || fight(level) == null) return;
			// the dragon's head points along (sin yaw, -cos yaw), against vanilla's look vector
			double yaw = Math.toRadians(dragon.getYRot());
			prey(level).get(0).teleportTo(dragon.getX() + Math.sin(yaw) * 8.0, y, dragon.getZ() - Math.cos(yaw) * 8.0);
			fight(level).seizeNext();
		});
		serverUntil(140, level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty() || DragonswornDragon.brain(dragon).prey.hold() != Grip.Hold.JAW) return false;
			health[0] = prey(level).get(0).getHealth();
			return true;
		});
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.JAW,
					"a bite that lands takes the husk in its jaws");
		});
		for (int i = 0; i < 10; i++) closeUp(0, String.format(Locale.ROOT, "seize-%02d", i), 3, 7.0);
		for (int i = 0; i < 3; i++) feetView(String.format(Locale.ROOT, "seize-feet-%d", i));
		STEPS.add(new Step(20, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.JAW && !prey.isEmpty() && PreyHold.carrier(prey.get(0)) == dragon,
					"it holds the husk in its jaws");
			check(!prey.isEmpty() && prey.get(0).getY() > y + 2.0, String.format(Locale.ROOT, "the husk hangs from its jaws (%.1f up)",
					prey.isEmpty() ? 0.0 : prey.get(0).getY() - y));
			check(!prey.isEmpty() && prey.get(0).getHealth() < health[0], "and is chewed");
			// someone else strikes at the head
			List<? extends Husk> helper = level.getEntities(EntityTypes.HUSK, e -> e.entityTags().contains("df_helper"));
			if (dragon != null && !helper.isEmpty()) dragon.hurt(level, dragon.getSubEntities()[Parts.HEAD], level.damageSources().mobAttack(helper.get(0)), 1.0F);
		});
		STEPS.add(new Step(3, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.NONE && !prey(level).isEmpty() && PreyHold.carrier(prey(level).get(0)) == null,
					"a blow at its head makes it drop the husk");
		});
		// a hold left alone ends with the prey flung off
		STEPS.add(new Step(40, mc -> {}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty() || fight(level) == null) return;
			// the dragon's head points along (sin yaw, -cos yaw), against vanilla's look vector
			double yaw = Math.toRadians(dragon.getYRot());
			prey(level).get(0).teleportTo(dragon.getX() + Math.sin(yaw) * 8.0, y, dragon.getZ() - Math.cos(yaw) * 8.0);
			fight(level).seizeNext();
		});
		serverUntil(140, level -> aiDragon(level) != null && DragonswornDragon.brain(aiDragon(level)).prey.hold() == Grip.Hold.JAW);
		for (int i = 0; i < 6; i++) closeUp(0, String.format(Locale.ROOT, "seize-shake-%02d", i), 20, 7.0);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = prey(level);
			check(dragon != null && DragonswornDragon.brain(dragon).prey.hold() == Grip.Hold.NONE && !prey.isEmpty() && PreyHold.carrier(prey.get(0)) == null,
					"left alone, the hold ends: the husk is flung off");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
		command("kill @e[tag=df_helper]", 2);
	}

	/**
	 * A close look at what the AI dragon holds: the camera {@code distance} blocks off to the dragon's
	 * left of its held prey (or of part {@code part} when it holds nothing, or is still reaching), level with it.
	 */
	static void closeUp(int part, String name, int ticks, double distance) {
		STEPS.add(new Step(Math.max(ticks, 3), mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			var held = DragonswornDragon.brain(dragon).prey.holding() ? DragonswornDragon.brain(dragon).prey.prey() : null;
			var at = held != null ? held.position().add(0.0, held.getBbHeight() / 2.0, 0.0) : dragon.getSubEntities()[part].position();
			double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
			double cx = at.x - Math.cos(yaw) * distance + Math.sin(yaw) * 2.0, cz = at.z - Math.sin(yaw) * distance - Math.cos(yaw) * 2.0;
			mc.player.connection.sendCommand(view(cx, at.y + 1.5, cz, at.x, at.y, at.z));
		}));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
				message -> LOG.info("{}", message.getString()))));
	}

	/** The prey in the talons, from below and to the dragon's right, a little ahead: the gripping foot and its toes. */
	static void talonView(String name) {
		boolean[] holding = new boolean[1];
		STEPS.add(new Step(3, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			holding[0] = dragon != null && DragonswornDragon.brain(dragon).prey.holding();
			if (!holding[0]) return;
			var held = DragonswornDragon.brain(dragon).prey.prey();
			var at = held.position().add(0.0, held.getBbHeight() / 2.0, 0.0);
			// the dragon's head points along (sin yaw, -cos yaw); its right is (cos yaw, sin yaw)
			double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
			double cx = at.x + Math.cos(yaw) * 7.0 + Math.sin(yaw) * 1.0, cz = at.z + Math.sin(yaw) * 7.0 - Math.cos(yaw) * 1.0;
			mc.player.connection.sendCommand(view(cx, at.y - 4.0, cz, at.x, at.y, at.z));
		}));
		STEPS.add(new Step(2, mc -> {
			if (holding[0]) Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> LOG.info("{}", message.getString()));
		}));
	}

	/** The hind feet on the ground, at their height, from the dragon's right: the toes. */
	static void feetView(String name) {
		STEPS.add(new Step(3, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
			// the hips are about two blocks behind the dragon's position
			double hx = dragon.getX() - Math.sin(yaw) * 1.5, hz = dragon.getZ() + Math.cos(yaw) * 1.5;
			mc.player.connection.sendCommand(view(hx + Math.cos(yaw) * 5.0, dragon.getY() + 0.8, hz + Math.sin(yaw) * 5.0, hx, dragon.getY() + 0.3, hz));
		}));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
				message -> LOG.info("{}", message.getString()))));
	}
}
