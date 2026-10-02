package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.attack.BreathPass;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.flight.FlightModel;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.phase.BreathPassPhase;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code pass}: the breath pass over a husk. */
final class BreathPassStage {
	private BreathPassStage() {
	}


	/**
	 * The breath pass, live: a wild dragon is sent at a husk on open ground. It must come in over it,
	 * glide through the breath (no beats), keep its height, point its straight neck down at where the
	 * flames land, and burn the husk.
	 */
	static void breathPass(int px, int y, int pz) {
		STEPS.add(new Step(1, mc -> mc.options.hideGui = true));
		command(view(px - 30, y + 14, pz + 20, px, y + 6, pz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"],"
				+ "attributes:[{id:\"minecraft:generic.max_health\",base:200.0}],Health:200f}", px, y, pz), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[180f,0f]}", px, y + 18, pz + 80), 60);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null && !prey(level).isEmpty() && BreathPassPhase.start(dragon, prey(level).get(0)), "the dragon goes for a breath pass at the husk");
		});
		boolean[] breathed = new boolean[1], beat = new boolean[1];
		float[] health = {200.0F};
		double[] low = {Double.MAX_VALUE}, worst = new double[1];
		int[] measured = new int[1];
		for (int i = 0; i < 280; i++) {
			int step = i;
			STEPS.add(new Step(1, mc -> {
				mc.getSingleplayerServer().executeBlocking(() -> {
					ServerLevel level = mc.getSingleplayerServer().overworld();
					EnderDragon dragon = aiDragon(level);
					if (dragon == null || prey(level).isEmpty()) return;
					DragonBrain brain = DragonswornDragon.brain(dragon);
					if (brain.action() != DragonAnim.GLIDE_BREATH) return;
					breathed[0] = true;
					health[0] = Math.min(health[0], prey(level).get(0).getHealth());
					double ticks = BreathPassPhase.breathTicks(dragon, 0.0F);
					if (BreathPass.streaming((int) Math.round(ticks))) {
						low[0] = Math.min(low[0], dragon.getY() - y);
						beat[0] |= brain.flightPlan().mode() != FlightModel.Mode.GLIDE;
					}
				});
				EnderDragon dragon = clientAiDragon(mc);
				if (dragon == null) return;
				// the camera beside the flight path, a little behind the dragon and below it
				double yaw = Math.toRadians(dragon.getYRot());
				double cx = dragon.getX() - Math.cos(yaw) * 26 + Math.sin(yaw) * 10, cz = dragon.getZ() - Math.sin(yaw) * 26 - Math.cos(yaw) * 10;
				mc.player.connection.sendCommand(view(cx, y + 6, cz, dragon.getX() - Math.sin(yaw) * 6, dragon.getY() - 2, dragon.getZ() + Math.cos(yaw) * 6));
				double ticks = BreathPassPhase.breathTicks(dragon, 0.0F);
				if (Double.isNaN(ticks)) return;
				if (step % 4 == 0) {
					Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "df-pass-%03d.png", step), mc.getMainRenderTarget(),
							message -> LOG.info("{}", message.getString()));
				}
				var aim = BreathPassPhase.aimPoint(dragon);
				// the neck's line once it has swung down straight (the pose's lunge and the model's blend)
				if (aim == null || ticks < BreathPass.WINDUP_TICKS + 8 || ticks >= BreathPass.WINDUP_TICKS + BreathPass.STREAM_TICKS - 6) return;
				var head = dragon.getSubEntities()[Parts.HEAD].getBoundingBox().getCenter();
				var neck = dragon.getSubEntities()[Parts.NECK_LOWER].getBoundingBox().getCenter();
				var line = head.subtract(neck).normalize();
				var to = aim.subtract(head).normalize();
				worst[0] = Math.max(worst[0], Math.toDegrees(Math.acos(Mth.clamp(line.dot(to), -1.0, 1.0))));
				measured[0]++;
			}));
		}
		STEPS.add(new Step(1, mc -> {
			REPORT.add(String.format(Locale.ROOT, "INFO breath pass: lowest %.1f blocks over the ground while pouring; the neck's line is off the aim by up to %.1f deg (%d ticks)",
					low[0], worst[0], measured[0]));
			check(breathed[0], "it glided in and breathed");
			check(!beat[0], "it glides through the stream (no wingbeats)");
			check(low[0] > 6.0 && low[0] < 16.0, String.format(Locale.ROOT, "it keeps to the pass's height (%.1f)", low[0]));
			check(measured[0] > 20 && worst[0] < 15.0, "the straight neck points down at where the flames land");
			check(health[0] < 200.0F, String.format(Locale.ROOT, "the flames burned the husk (%.0f health left)", health[0]));
		}));
		server(level -> {
			int fire = BreathStage.dragonFire(level, px, y, pz, 16);
			check(fire > 0, "the breath pass leaves dragon fire on the ground (" + fire + " blocks)");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}
}
