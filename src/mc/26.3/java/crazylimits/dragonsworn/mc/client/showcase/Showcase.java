package crazylimits.dragonsworn.mc.client.showcase;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;

import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * The in-game test, on any loader. Off unless the JVM runs with {@code -Ddragonsworn.showcase=true}
 * ({@code ./gradlew :<target>:runClient -Pdragonsworn.showcase}).
 *
 * <p>It creates a fresh flat creative world, lays a stone yard, then runs every stage ({@link Stages}) in
 * turn, or only the one named ({@code -Pdragonsworn.showcase=<stage>}). Each stage queues its steps on the
 * {@link Script} and checks what it sees; screenshots go to {@code <run>/screenshots/df-*.png}, a pass/fail
 * report to {@code <run>/showcase-report.txt}, and the game quits when done ({@link TestRun}).
 */
public final class Showcase {
	public static final boolean ENABLED = Boolean.getBoolean("dragonsworn.showcase");
	/** Runs only this stage when set ({@code -Pdragonsworn.showcase=grabs}, {@code =landing}). */
	private static final String ONLY = System.getProperty("dragonsworn.showcase.only", "");
	private static final TestRun RUN = new TestRun(new TestRun.Setup("Showcase", "dragonsworn_showcase", Difficulty.NORMAL,
			true, 60, "showcase-report.txt", mc -> buildScript(mc.player.blockPosition())));

	private Showcase() {}

	public static void tick(Minecraft mc) {
		if (ENABLED) RUN.tick(mc);
	}

	/** The yard, then the stage asked for, or every stage. */
	private static void buildScript(BlockPos at) {
		int x = at.getX(), y = at.getY(), z = at.getZ() - 40;
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:smooth_stone", x - 24, y - 1, z - 24, x + 24, y - 1, z + 24), 2);
		command("time set 6000", 1);
		if (Stages.solo(ONLY, x, y, z)) return;
		Stages.full(x, y, z);
	}
}
