package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.client.LimbAnimator;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code hitboxes}: the head and neck hitboxes stay on the drawn model as the head turns. */
final class HitboxesStage {
	private HitboxesStage() {
	}


	static void hitboxes(int hx, int y, int hz) {
		command(view(hx - 14, y + 6, hz - 14, hx, y + 3, hz), 40);
		// yaw 0: the dragon faces north (-z); +x is its right
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"],Rotation:[0f,0f]}", hx, y, hz), 6);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(dragon != null, "a dragon rests on the ground for the hitbox test");
			if (dragon != null) GroundFightPhase.start(dragon, null);
		});
		double[][] views = {{-12, 4, -14}, {-17, 3, -2}, {16, 2, -5}, {4, 14, -17}, {11, 1, -12}, {-6, 9, -20}};
		double[] worst = new double[3], sum = new double[3], most = {0.0};
		int[] samples = {0};
		for (int v = 0; v < views.length; v++) {
			double[] at = views[v];
			STEPS.add(new Step(1, mc -> {
				EnderDragon dragon = clientAiDragon(mc);
				double cx = dragon == null ? hx : dragon.getX(), cz = dragon == null ? hz : dragon.getZ();
				mc.player.connection.sendCommand(view(cx + at[0], y + at[1], cz + at[2], cx, y + 4, cz));
			}));
			for (int t = 0; t < 30; t++) {
				boolean shot = t == 29;
				int view = v;
				STEPS.add(new Step(1, mc -> {
					EnderDragon dragon = clientAiDragon(mc);
					double[] off = dragon == null ? null : LimbAnimator.hitboxOffsets(dragon);
					if (off == null) return;
					for (int k = 0; k < off.length; k++) {
						worst[k] = Math.max(worst[k], off[k]);
						sum[k] += off[k];
					}
					samples[0]++;
					most[0] = Math.max(most[0], Math.abs(DragonswornDragon.brain(dragon).look.yaw(1.0)));
					if (shot) Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "df-hitbox-look-%d.png", view),
							mc.getMainRenderTarget(), 1, message -> LOG.info("{}", message.getString()));
				}));
			}
			// settled on the second view (the dragon still): server and client turn the head alike
			if (v == 1) lookAgrees();
		}
		STEPS.add(new Step(1, mc -> {
			int n = Math.max(1, samples[0]);
			REPORT.add(String.format(Locale.ROOT, "INFO hitboxes: drawn anchor to hitbox centre over %d frames, mean / worst (blocks):"
					+ " head %.3f / %.3f, neck %.3f / %.3f, mid neck %.3f / %.3f; the look turned the head up to %.0f degrees",
					samples[0], sum[0] / n, worst[0], sum[1] / n, worst[1], sum[2] / n, worst[2], most[0]));
			check(samples[0] > 100, "the head was measured (" + samples[0] + " frames)");
			check(most[0] > 25.0, String.format(Locale.ROOT, "the head turned to watch (%.0f degrees)", most[0]));
			check(sum[0] / n < 0.2 && worst[0] < 0.5, "the head's hitbox stays on the drawn head as it turns");
			check(sum[1] / n < 0.2 && worst[1] < 0.5 && sum[2] / n < 0.2 && worst[2] < 0.5, "the neck's hitboxes stay on the drawn neck");
		}));
		command("kill @e[tag=df_ai]", 2);
	}

	/** Server and client turn the head alike (the server's hitboxes are the ones that are hit). */
	static void lookAgrees() {
		double[] clientLook = new double[2];
		STEPS.add(new Step(1, mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			clientLook[0] = DragonswornDragon.brain(dragon).look.yaw(1.0);
			clientLook[1] = DragonswornDragon.brain(dragon).look.pitch(1.0);
		}));
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			double yaw = dragon == null ? Double.NaN : DragonswornDragon.brain(dragon).look.yaw(1.0);
			double pitch = dragon == null ? Double.NaN : DragonswornDragon.brain(dragon).look.pitch(1.0);
			REPORT.add(String.format(Locale.ROOT, "INFO hitboxes: head look yaw %.1f / %.1f, pitch %.1f / %.1f (server / client)",
					yaw, clientLook[0], pitch, clientLook[1]));
			check(Math.abs(yaw - clientLook[0]) < 4.0 && Math.abs(pitch - clientLook[1]) < 4.0, "server and client turn the head alike");
		});
	}
}
