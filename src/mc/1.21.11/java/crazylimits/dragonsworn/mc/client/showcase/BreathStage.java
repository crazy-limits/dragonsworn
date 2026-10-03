package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.attack.BreathAttack;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.breath.BreathParticles;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code breath}: the perched stream breath, its dragon fire, and a husk walking across the stream. */
final class BreathStage {
	private BreathStage() {
	}


	/**
	 * The stream breath: an AI dragon (NoAI skips phases) is put in the breath phase on the server, aiming
	 * north at three husks where its sweep lands (11-18 blocks out; right under its chin is out of reach)
	 * and one off to the side; then a dragon fireball is dropped to check
	 * its cloud burns with void flame.
	 */
	static void breath(int x, int y, int z) {
		int bx = x - 120, bz = z;
		command(view(bx - 22, y + 4, bz - 8, bx, y + 3, bz - 8), 40);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_breath\"],Rotation:[0f,0f]}", bx, y, bz), 20);
		for (int[] at : new int[][]{{0, -11}, {0, -15}, {1, -18}, {-9, -13}}) {
			command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"df_target\"]}", bx + at[0], y, bz + at[1]), 1);
		}
		server(level -> {
			for (EnderDragon d : level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_breath"))) {
				d.getPhaseManager().setPhase(DragonPhases.BREATH_STREAM);
			}
		});
		String breathSide = view(bx - 22, y + 4, bz - 8, bx, y + 3, bz - 8);
		String breathFront = view(bx + 10, y + 7, bz - 22, bx, y + 3, bz - 4);
		// the heat climbs over the 40-tick inhale (HeatGlowLayer): chest, throat, jaw, then the stream
		String breathClose = view(bx - 13, y + 3, bz - 6, bx, y + 4, bz - 4);
		shoot(breathClose, "breath-heat-chest", 10);  // ~tick 12
		shoot(breathClose, "breath-heat-neck", 8);    // ~tick 22
		shoot(breathClose, "breath-heat-jaw", 10);    // ~tick 34: embers in the parting jaw
		shoot(breathSide, "breath-stream-0", 12);     // stream
		shoot(breathFront, "breath-stream-front", 12);
		shoot(breathSide, "breath-stream-1", 14);
		STEPS.add(new Step(40, mc -> {}));
		server(level -> {
			var husks = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_target"));
			// the stream kills a husk outright, and a dead husk is gone from the list: count the unhurt ones
			long burned = 3 - husks.stream().filter(h -> h.getX() > bx - 4 && h.getHealth() >= h.getMaxHealth()).count();
			boolean sideSafe = husks.stream().filter(h -> h.getX() <= bx - 4).allMatch(h -> h.getHealth() >= h.getMaxHealth());
			check(burned == 3, "the stream breath burns the three husks in its line (" + burned + "/3)");
			check(sideSafe, "the husk beside the stream is untouched");
			int fire = dragonFire(level, bx, y, bz - 14, 8);
			check(fire > 0, "the stream leaves dragon fire where it splashes (" + fire + " blocks)");
		});
		shoot(view(bx - 10, y + 5, bz - 4, bx, y, bz - 14), "breath-dragon-fire", 2);
		command("kill @e[tag=df_breath]", 2);
		command("kill @e[tag=df_target]", 2);
		command(String.format(Locale.ROOT, "summon minecraft:dragon_fireball %d %d %d {Motion:[0.0,-0.5,0.0]}", bx, y + 8, bz), 40);
		shoot(view(bx - 8, y + 4, bz - 8, bx, y, bz), "fireball-cloud", 20);
		server(level -> {
			var clouds = level.getEntities(EntityType.AREA_EFFECT_CLOUD, e -> true);
			check(!clouds.isEmpty() && clouds.stream().allMatch(c -> ((AreaEffectCloud) c).getParticle() == BreathParticles.VOID_FLAME),
					"the dragon fireball's cloud burns with void flame (" + clouds.size() + " clouds)");
			int fire = dragonFire(level, bx, y, bz, 3), outer = fire - dragonFire(level, bx, y, bz, 1);
			check(fire > 0 && fire <= 6 && outer == 0,
					"the fireball leaves a little dragon fire, only right where it bursts (" + fire + " blocks, " + outer + " out of the middle)");
		});
		// dragon fire burns three times what fire does: a husk standing in it loses three times what one in
		// vanilla fire beside it does (no armor, which takes a flat bit off each; with AI: a NoAI mob never moves, so it never touches the blocks it stands in)
		int fx = bx + 30;
		command(String.format(Locale.ROOT, "setblock %d %d %d dragonsworn:dragon_fire", fx, y, bz), 1);
		command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:fire", fx + 6, y, bz), 1);
		command(String.format(Locale.ROOT, "summon minecraft:husk %.1f %d %.1f {Silent:1b,PersistenceRequired:1b,Tags:[\"df_target\"],attributes:[{id:\"minecraft:armor\",base:0.0}]}", fx + 0.5, y, bz + 0.5), 1);
		command(String.format(Locale.ROOT, "summon minecraft:husk %.1f %d %.1f {Silent:1b,PersistenceRequired:1b,Tags:[\"df_target\"],attributes:[{id:\"minecraft:armor\",base:0.0}]}", fx + 6.5, y, bz + 0.5), 4);
		server(level -> {
			float[] lost = new float[2];
			for (var husk : level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_target"))) {
				lost[husk.getX() < fx + 3 ? 0 : 1] = husk.getMaxHealth() - husk.getHealth();
			}
			REPORT.add(String.format(Locale.ROOT, "INFO first touch: dragon fire took %.2f health, vanilla fire %.2f", lost[0], lost[1]));
			check(lost[1] > 0.0F && Math.abs(lost[0] / lost[1] - 3.0F) < 0.05F,
					String.format(Locale.ROOT, "dragon fire hurts three times what fire does (%.2f vs %.2f)", lost[0], lost[1]));
		});
		shoot(view(fx - 4, y + 2, bz - 4, fx, y, bz), "dragon-fire", 2);
		command("kill @e[tag=df_target]", 2);
	}

	/**
	 * The stream breath at a moving target: a husk walks across in front of the dragon (17 blocks out,
	 * 0.15 blocks a tick, slower than the aim's {@code BreathAttack.AIM_SPEED}) through the inhale and the
	 * stream. The neck runs out straight with the head low, and the head must point at the husk:
	 * every tick of the stream the head's line (neck to head) is measured against the line to the
	 * husk, across and up/down.
	 */
	static void breathMoving(int x, int y, int z) {
		int bx = x - 120, bz = z;
		double out = 17.0, from = -10.0, speed = 0.15;
		command(view(bx - 20, y + 6, bz - 4, bx, y + 3, bz - 8), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %.1f %d %.1f {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"df_walker\"]}",
				bx + from, y, bz - out), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_breath\"],Rotation:[0f,0f]}", bx, y, bz), 20);
		server(level -> {
			List<? extends Husk> walker = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_walker"));
			for (EnderDragon d : level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_breath"))) {
				d.getPhaseManager().setPhase(DragonPhases.BREATH_STREAM);
				if (!walker.isEmpty()) d.getPhaseManager().getPhase(DragonPhases.BREATH_STREAM).setTarget(walker.get(0));
			}
		});
		String side = view(bx - 20, y + 6, bz - 4, bx, y + 3, bz - 8);
		String front = view(bx + 14, y + 8, bz - 26, bx, y + 3, bz - 6);
		double[] worstYaw = new double[1], worstPitch = new double[1];
		int[] measured = new int[1];
		float[] bodyYaw = {Float.NaN, Float.NaN};
		int total = BreathAttack.WINDUP_TICKS + BreathAttack.STREAM_TICKS;
		// one step a tick: the husk is where the breath's own clock puts it, the head measured as drawn
		for (int i = 0; i < total + 10; i++) {
			int step = i;
			STEPS.add(new Step(1, mc -> {
				mc.getSingleplayerServer().executeBlocking(() -> {
					ServerLevel level = mc.getSingleplayerServer().overworld();
					for (EnderDragon d : level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_breath"))) {
						if (!(d.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase breath)) continue;
						for (Husk walker : level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_walker"))) {
							walker.teleportTo(bx + from + speed * breath.ticks(), y, bz - out);
						}
					}
				});
				EnderDragon dragon = nearestDragon(mc, bx + 0.5, bz + 0.5);
				var walker = mc.level.getEntitiesOfClass(Husk.class, new AABB(bx - 30, y - 2, bz - 30, bx + 30, y + 4, bz + 10));
				if (step % 6 == 0) {
					String name = String.format(Locale.ROOT, "df-breath-moving-%03d.png", step);
					Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, message -> LOG.info("{}", message.getString()));
				}
				if (step == total / 2) mc.player.connection.sendCommand(front);
				if (step == total / 2 + 12) mc.player.connection.sendCommand(side);
				if (dragon == null || walker.isEmpty() || !(dragon.getPhaseManager().getCurrentPhase() instanceof BreathStreamPhase breath)) return;
				// the stream, once the neck has swung out straight (the pose's pour and the model's blend)
				if (breath.ticks() < BreathAttack.WINDUP_TICKS + 12 || breath.ticks() >= BreathAttack.WINDUP_TICKS + BreathAttack.STREAM_TICKS - 10) return;
				var head = dragon.getSubEntities()[Parts.HEAD].getBoundingBox().getCenter();
				// the neck's line, base (part 8) to head: the stream's neck is straight along it
				var neck = dragon.getSubEntities()[Parts.NECK_LOWER].getBoundingBox().getCenter();
				var at = walker.get(0).position().add(0.0, walker.get(0).getBbHeight() * 0.3, 0.0);
				double headYaw = Math.atan2(head.x - neck.x, head.z - neck.z), toYaw = Math.atan2(at.x - head.x, at.z - head.z);
				double headPitch = Math.atan2(head.y - neck.y, Math.hypot(head.x - neck.x, head.z - neck.z));
				double toPitch = Math.atan2(at.y - head.y, Math.hypot(at.x - head.x, at.z - head.z));
				worstYaw[0] = Math.max(worstYaw[0], Math.abs(Math.toDegrees(Math.atan2(Math.sin(headYaw - toYaw), Math.cos(headYaw - toYaw)))));
				worstPitch[0] = Math.max(worstPitch[0], Math.abs(Math.toDegrees(headPitch - toPitch)));
				measured[0]++;
				if (Float.isNaN(bodyYaw[0])) bodyYaw[0] = dragon.getYRot();
				bodyYaw[1] = dragon.getYRot();
			}));
		}
		STEPS.add(new Step(1, mc -> {
			REPORT.add(String.format(Locale.ROOT, "INFO moving target: over %d ticks of the stream the head's line is off the husk by up to %.1f deg across, %.1f deg up/down",
					measured[0], worstYaw[0], worstPitch[0]));
			check(measured[0] > 30, "the stream was measured (" + measured[0] + " ticks)");
			check(Math.abs(Mth.wrapDegrees(bodyYaw[1] - bodyYaw[0])) < 1.0F, String.format(Locale.ROOT,
					"the body stays put while the neck follows (it turned %.1f deg)", Mth.wrapDegrees(bodyYaw[1] - bodyYaw[0])));
			check(worstYaw[0] < 12.0, "the head follows the walking husk across");
			check(worstPitch[0] < 15.0, "and points down at it");
		}));
		command("kill @e[tag=df_breath]", 2);
		command("kill @e[tag=df_walker]", 2);
	}

	/** How many dragon fire blocks are within {@code r} (a box) of a point, a few blocks up and down. */
	static int dragonFire(ServerLevel level, int x, int y, int z, int r) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(x - r, y - 3, z - r, x + r, y + 4, z + r)) {
			if (level.getBlockState(pos).is(DragonFire.BLOCK)) n++;
		}
		return n;
	}
}
