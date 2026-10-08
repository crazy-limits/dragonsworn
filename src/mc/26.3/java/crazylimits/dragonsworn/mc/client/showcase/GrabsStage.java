package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.PreyHold;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.SnatchPhase;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

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
		command("kill @e[tag=df_prey]", 2);
		preyCamera(y);
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
		command("kill @e[tag=df_helper]", 2);
	}

	/**
	 * The held player's camera ({@code CameraMixin}, {@code PreyView}): the dragon seizes the player. In first person
	 * it sees from its lying head; in third person behind, and in Shoulder Surfing Reloaded's over-the-shoulder view
	 * when the dev client has it ({@code -Pdragonsworn.shoulderSurfing}), the camera keeps its place round the lying
	 * head. Let go, the camera is the game's (or Shoulder Surfing's) again, nothing left over from the hold.
	 */
	static void preyCamera(int y) {
		command("gamemode survival", 1);
		command("effect give @s minecraft:resistance infinite 255 true", 1);
		command("effect give @s minecraft:saturation infinite 255 true", 1);
		// a seize can miss (the player stands still, but the dragon may still be turning): up to three tries
		for (int attempt = 0; attempt < 3; attempt++) {
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				ServerPlayer player = level.players().get(0);
				if (dragon == null || PreyHold.carrier(player) != null) return;
				// the dragon's head points along (sin yaw, -cos yaw), against vanilla's look vector
				double yaw = Math.toRadians(dragon.getYRot());
				player.teleportTo(dragon.getX() + Math.sin(yaw) * 8.0, y, dragon.getZ() - Math.cos(yaw) * 8.0);
				GroundFightPhase.start(dragon, player, false);
				if (fight(level) != null) fight(level).seizeNext();
			});
			serverUntil(200, level -> PreyHold.carrier(level.players().get(0)) != null);
		}
		server(level -> check(PreyHold.carrier(level.players().get(0)) != null, "the dragon takes the player in its jaws"));
		boolean shoulder = ShoulderSurfing.loaded();
		if (!shoulder) REPORT.add("INFO Shoulder Surfing Reloaded is not loaded (-Pdragonsworn.shoulderSurfing): its view is not checked");
		STEPS.add(new Step(3, mc -> perspective(mc, "FIRST_PERSON")));
		STEPS.add(new Step(1, mc -> {
			Vec3[] view = placeCamera(mc);
			check(view[2] != null && view[0].distanceTo(view[2]) < 1.0E-3 && view[2].distanceTo(view[3]) > 0.3, String.format(Locale.ROOT,
					"held, first person: the camera is at the lying head (%.4f off it; %.2f from the standing eyes)", distance(view[0], view[2]), distance(view[0], view[3])));
		}));
		STEPS.add(new Step(3, mc -> perspective(mc, "THIRD_PERSON_BACK")));
		STEPS.add(new Step(1, mc -> {
			Vec3[] view = placeCamera(mc);
			if (shoulder && ShoulderSurfing.active()) {
				REPORT.add("INFO Shoulder Surfing replaces third person: vanilla's is not checked");
				return;
			}
			// vanilla's: straight back from the eyes along the look, up to 4 blocks
			Vec3 off = view[2] == null ? Vec3.ZERO : view[0].subtract(view[2]);
			double back = -off.dot(view[1]), aside = off.add(view[1].scale(back)).length();
			check(view[2] != null && back > 0.5 && back < 4.001 && aside < 1.0E-3, String.format(Locale.ROOT,
					"held, third person: the camera is %.2f behind the lying head along the look (%.4f aside)", back, aside));
		}));
		if (shoulder) {
			STEPS.add(new Step(3, mc -> perspective(mc, "SHOULDER_SURFING")));
			STEPS.add(new Step(1, mc -> {
				Vec3[] view = placeCamera(mc);
				Vec3 offset = ShoulderSurfing.offset(), placed = shoulderOffset(mc);
				// its offset round the lying head, not round the standing eyes inside the dragon
				check(ShoulderSurfing.active() && view[2] != null && view[0].distanceTo(view[2].add(placed)) < 0.01 && Math.hypot(offset.x, offset.y) > 0.1,
						String.format(Locale.ROOT, "held, Shoulder Surfing: the camera is its offset (%.2f, %.2f, %.2f) from the lying head (%.4f off; %.2f off the standing eyes')",
						offset.x, offset.y, offset.z, distance(view[0], view[2] == null ? null : view[2].add(placed)), view[0].distanceTo(view[3].add(placed))));
			}));
		}
		// let go: flung off, then on the ground; the camera follows the eyes the game's way again
		serverUntil(400, level -> PreyHold.carrier(level.players().get(0)) == null);
		serverUntil(200, level -> level.players().get(0).onGround());
		STEPS.add(new Step(10, mc -> perspective(mc, "FIRST_PERSON")));
		STEPS.add(new Step(1, mc -> {
			Vec3[] view = placeCamera(mc);
			check(view[2] == null && view[0].distanceTo(view[3]) < 1.0E-3, String.format(Locale.ROOT,
					"let go, first person: the camera is back at the eyes (%.4f off)", view[0].distanceTo(view[3])));
		}));
		if (shoulder) {
			STEPS.add(new Step(3, mc -> perspective(mc, "SHOULDER_SURFING")));
			STEPS.add(new Step(1, mc -> {
				Vec3[] view = placeCamera(mc);
				double off = view[0].distanceTo(view[3].add(shoulderOffset(mc)));
				check(view[2] == null && ShoulderSurfing.active() && off < 0.01, String.format(Locale.ROOT,
						"let go, Shoulder Surfing: the camera is its offset from the eyes again (%.4f off)", off));
			}));
		}
		STEPS.add(new Step(1, mc -> perspective(mc, "FIRST_PERSON")));
		command("effect clear @s", 1);
		command("gamemode creative", 1);
	}

	/** The camera's view by name: through Shoulder Surfing when it is loaded (else it would keep its own). */
	private static void perspective(Minecraft mc, String name) {
		if (ShoulderSurfing.loaded()) ShoulderSurfing.perspective(name);
		else mc.options.setCameraType(CameraType.valueOf(name));
	}

	/**
	 * Places the game's camera for the player now, at the end of the tick (every camera mixin runs, as for a frame):
	 * where it is, its look, the player's lying eyes (null when not held) and its standing ones.
	 */
	private static Vec3[] placeCamera(Minecraft mc) {
		Camera camera = mc.gameRenderer.mainCamera();
		camera.update(DeltaTracker.ONE);
		EnderDragon dragon = PreyHold.carrier(mc.player);
		Vec3 lying = dragon == null ? null : DragonswornDragon.brain(dragon).prey.lyingEyes(mc.player, 1.0F);
		return new Vec3[]{camera.position(), new Vec3(camera.forwardVector()), lying, mc.player.getEyePosition(1.0F)};
	}

	/** Shoulder Surfing's offset as its redirect of {@code Camera.move} places it: its camera axes turned by the camera's rotation. */
	private static Vec3 shoulderOffset(Minecraft mc) {
		Vec3 o = ShoulderSurfing.offset();
		return new Vec3(new Vector3f((float) -o.x, (float) o.y, (float) o.z).rotate(mc.gameRenderer.mainCamera().rotation()));
	}

	private static double distance(Vec3 a, Vec3 b) {
		return a == null || b == null ? Double.NaN : a.distanceTo(b);
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
