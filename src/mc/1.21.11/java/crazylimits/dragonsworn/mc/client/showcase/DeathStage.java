package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.ai.DeathFlight;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;

import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code death}: a wild dragon brought down in the air: the rise, then the cocoon. */
final class DeathStage {
	private DeathStage() {
	}


	/**
	 * The death of a wild dragon (no altar): brought down by a player in the air, it is not dead yet but takes
	 * its last flight ({@code ai/DeathFlight}): straight up {@link DeathFlight#RISE}, and only then dies, the
	 * cocoon closing round it (photographed as vanilla's light bursts out and it floats up).
	 */
	static void death(int x, int y, int z) {
		command(view(x - 34, y + 22, z, x, y + 22, z), 20);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[90f,0f]}", x, y + 18, z), 80);
		double[] start = {Double.NaN}, died = {Double.NaN};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, "death: a wild dragon in the air");
			if (dragon == null) return;
			start[0] = dragon.getY();
			dragon.hurt(dragon.damageSources().playerAttack(level.players().get(0)), 10000.0F);
			check(dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING && !dragon.isDeadOrDying(),
					"brought down, it takes its last flight before it dies");
		});
		serverUntil(500, level -> {
			List<? extends EnderDragon> found = level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_ai"));
			if (found.isEmpty() || !found.get(0).isDeadOrDying()) return false;
			died[0] = found.get(0).getY();
			return true;
		});
		STEPS.add(new Step(1, mc -> {
			REPORT.add(String.format(Locale.ROOT, "INFO death: rose %.1f blocks before it died", died[0] - start[0]));
			check(!Double.isNaN(died[0]), "the last flight ends in death");
			check(died[0] - start[0] > DeathFlight.RISE - 3.0, String.format(Locale.ROOT, "it rose before it died (%.1f blocks)", died[0] - start[0]));
		}));
		boolean[] cocoon = {false};
		int[] at = {6, 30, 55, 90, 130, 175};
		int last = 0;
		for (int i = 0; i < at.length; i++) {
			int shot = i;
			STEPS.add(new Step(Math.max(3, at[i] - last - 3), mc -> {
				EnderDragon dragon = null;
				for (var entity : mc.level.entitiesForRendering()) if (entity instanceof EnderDragon d && d.isDeadOrDying()) dragon = d;
				if (dragon == null) return;
				cocoon[0] |= DragonswornDragon.brain(dragon).clock.anim() == DragonAnim.DEATH;
				double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F)), d = shot % 2 == 0 ? 24 : 18;
				// alternately from its left side and from in front, a little below
				double cx = shot % 2 == 0 ? dragon.getX() - Math.cos(yaw) * d : dragon.getX() + Math.sin(yaw) * d;
				double cz = shot % 2 == 0 ? dragon.getZ() - Math.sin(yaw) * d : dragon.getZ() - Math.cos(yaw) * d;
				mc.player.connection.sendCommand(view(cx, dragon.getY() + 1, cz, dragon.getX(), dragon.getY() + 3, dragon.getZ()));
			}));
			STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "df-death-%d.png", shot),
					mc.getMainRenderTarget(), 1, message -> LOG.info("{}", message.getString()))));
			last = at[i];
		}
		STEPS.add(new Step(40, mc -> check(cocoon[0], "dead, it plays the cocoon")));
	}
}
