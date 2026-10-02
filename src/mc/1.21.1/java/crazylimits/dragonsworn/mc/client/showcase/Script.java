package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The showcase's harness: the queue of steps every stage adds to (each a wait in ticks and what to do then, on
 * the client), the report, and the helpers stages use: commands, camera views and screenshots, server-side
 * checks.
 */
final class Script {
	private Script() {
	}

	static final Logger LOG = LoggerFactory.getLogger("dragonsworn/showcase");

	record Step(int ticks, Consumer<Minecraft> action) {}

	static final List<Step> STEPS = new ArrayList<>();
	/** The next step to run; a step that waits for something ({@link #serverUntil}) jumps it on once that came. */
	static int index;
	static final List<String> REPORT = new ArrayList<>();
	static boolean failed;

	/** Runs on the integrated server's thread and waits for it (so checks happen in script order). */
	static void server(Consumer<ServerLevel> action) {
		STEPS.add(new Step(1, mc -> mc.getSingleplayerServer().executeBlocking(() -> action.accept(mc.getSingleplayerServer().overworld()))));
	}

	/**
	 * Runs {@code poll} on the server every tick until it returns true, for at most {@code max} ticks: the
	 * rest of the wait is skipped.
	 */
	static void serverUntil(int max, Predicate<ServerLevel> poll) {
		int end = STEPS.size() + max;
		for (int i = 0; i < max; i++) server(level -> {
			if (poll.test(level)) index = end;
		});
	}

	static EnderDragon aiDragon(ServerLevel level) {
		// a killed dragon lingers through its death: not it
		List<? extends EnderDragon> found = level.getEntities(EntityType.ENDER_DRAGON, e -> e.getTags().contains("df_ai") && !e.isDeadOrDying());
		return found.isEmpty() ? null : found.get(0);
	}

	/** The client's copy of the AI dragon (tags are not synced; it is the only one with AI). */
	static EnderDragon clientAiDragon(Minecraft mc) {
		for (var entity : mc.level.entitiesForRendering()) {
			if (entity instanceof EnderDragon dragon && !dragon.isNoAi() && !dragon.isDeadOrDying()) return dragon;
		}
		return null;
	}

	static int count(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			if (level.getBlockState(pos).is(block)) n++;
		}
		return n;
	}

	/** Moves the camera beside the AI dragon wherever it is now, waits, and grabs a frame. */
	static void track(String name, int ticks, double distance) {
		track(name, ticks, distance, 8);
	}

	/** {@link #track(String, int, double)} with the camera {@code height} blocks above the dragon's feet. */
	static void track(String name, int ticks, double distance, double height) {
		STEPS.add(new Step(Math.max(ticks, 3), mc -> {
			EnderDragon dragon = clientAiDragon(mc);
			if (dragon == null) return;
			double yaw = Math.toRadians(DragonswornDragon.brain(dragon).body.yaw(1.0F));
			// from the dragon's left side, a little ahead and above
			double cx = dragon.getX() - Math.cos(yaw) * distance + Math.sin(yaw) * 8;
			double cz = dragon.getZ() - Math.sin(yaw) * distance - Math.cos(yaw) * 8;
			mc.player.connection.sendCommand(view(cx, dragon.getY() + height, cz, dragon.getX(), dragon.getY() + Math.min(3, height), dragon.getZ()));
		}));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
	}

	static String view(double x, double y, double z, double tx, double ty, double tz) {
		return String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", x, y, z, tx, ty, tz);
	}

	static void command(String command, int wait) {
		STEPS.add(new Step(wait, mc -> mc.player.connection.sendCommand(command)));
	}

	/** Moves the camera, waits `ticks` for the pose to develop, then grabs the frame. */
	static void shoot(String view, String name, int ticks) {
		command(view, Math.max(ticks, 3));
		STEPS.add(new Step(2, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
	}

	static void check(boolean ok, String what) {
		REPORT.add((ok ? "PASS " : "FAIL ") + what);
		if (!ok) failed = true;
	}

	static EnderDragon nearestDragon(Minecraft mc, double x, double z) {
		EnderDragon best = null;
		double distance = 4.0;
		for (var entity : mc.level.entitiesForRendering()) {
			if (entity instanceof EnderDragon dragon && Math.hypot(dragon.getX() - x, dragon.getZ() - z) < distance) {
				best = dragon;
				distance = Math.hypot(dragon.getX() - x, dragon.getZ() - z);
			}
		}
		return best;
	}

	/** The AI dragon's ground fight, or null when it is not fighting on the ground. */
	static GroundFightPhase fight(ServerLevel level) {
		EnderDragon dragon = aiDragon(level);
		return dragon != null && dragon.getPhaseManager().getCurrentPhase() instanceof GroundFightPhase fight ? fight : null;
	}

	static List<? extends Husk> prey(ServerLevel level) {
		return level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
	}
}
