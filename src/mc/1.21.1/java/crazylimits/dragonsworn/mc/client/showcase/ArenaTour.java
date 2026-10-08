package crazylimits.dragonsworn.mc.client.showcase;

import com.google.common.collect.ImmutableList;
import crazylimits.dragonsworn.arena.Monolith;
import crazylimits.dragonsworn.mc.arena.Monoliths;
import crazylimits.dragonsworn.mc.arena.SpikeLayout;
import crazylimits.dragonsworn.mc.arena.Stellarity;
import crazylimits.dragonsworn.mc.arena.Wards;
import crazylimits.dragonsworn.mc.breath.DragonFire;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * The in-game test of the End's monoliths. Off unless the JVM runs with {@code -Ddragonsworn.arena=true}
 * ({@code ./gradlew :<target>:runClient -Pdragonsworn.arena}).
 *
 * <p>Creates a fresh world, goes to the End, and for every spike checks that the world holds exactly the
 * {@link Monolith} (and its crystal at vanilla's spot) and photographs it: its top from the altar's side
 * and the whole spire. Then it rebuilds every spike the way the respawn ritual does (clear around the top,
 * an explosion, the spike feature again, over the old one) and checks again that nothing is left over or
 * missing. Screenshots go to {@code <run>/screenshots/df-arena-*.png}, the report to
 * {@code <run>/arena-report.txt}; then the game quits.
 */
public final class ArenaTour {
	public static final boolean ENABLED = Boolean.getBoolean("dragonsworn.arena");
	private static final TestRun RUN = new TestRun(new TestRun.Setup("Arena tour", "dragonsworn_arena", Difficulty.PEACEFUL,
			false, 40, "arena-report.txt", ArenaTour::buildScript));

	private ArenaTour() {}

	public static void tick(Minecraft mc) {
		if (ENABLED) RUN.tick(mc);
	}

	private static void buildScript(Minecraft mc) {
		command("execute in minecraft:the_end run tp @s 0 110 0", 100);
		// whose dragon the End fight has (Stellarity's own while its fight is on, else vanilla's) and whose spike layout
		STEPS.add(new Step(1, m -> onServer(m, server -> {
			ServerLevel end = end(server);
			long stellarity = end.getDragons().stream().filter(d -> d.getTags().contains(Stellarity.BOSS)).count();
			REPORT.add(String.format(Locale.ROOT, "  End: %d dragon(s), %d of them Stellarity's; %d spikes, %s layout",
					end.getDragons().size(), stellarity, SpikeFeature.getSpikesForLevel(end).size(), SpikeLayout.island(end) != null ? "the island's own" : "vanilla's"));
			return null;
		})));
		command("kill @e[type=minecraft:ender_dragon]", 20);
		STEPS.add(new Step(1, m -> m.options.hideGui = true));
		List<SpikeFeature.EndSpike> spikes = onServer(mc, server -> SpikeFeature.getSpikesForLevel(end(server)));

		shoot(view(95, 115, 95, 0, 75, 0), "arena-overview", 80);
		for (SpikeFeature.EndSpike spike : spikes) {
			int x = spike.getCenterX(), z = spike.getCenterZ(), h = spike.getHeight();
			double len = Math.hypot(x, z), ix = -x / len, iz = -z / len;   // toward the altar
			// the top, from the altar's side and a little to the left, then the whole spire from further out
			String name = String.format(Locale.ROOT, "h%d", h);
			shoot(view(x + ix * 18 - iz * 8, h + 2, z + iz * 18 + ix * 8, x, h + 1, z), "arena-" + name + "-top", 60);
			shoot(view(x + ix * 40 + iz * 22, h - 12, z + iz * 40 - ix * 22, x, h - 22, z), "arena-" + name + "-spire", 20);
		}
		STEPS.add(new Step(1, m -> verify(m, spikes, "generated")));
		// every crystal on the island, and how many the fight has warded (on anyone's spires)
		STEPS.add(new Step(1, m -> onServer(m, server -> {
			ServerLevel end = end(server);
			List<EndCrystal> all = end.getEntitiesOfClass(EndCrystal.class, new AABB(-120, end.getMinBuildHeight(), -120, 120, end.getMaxBuildHeight(), 120));
			REPORT.add(String.format(Locale.ROOT, "  island crystals: %d, warded %d", all.size(), all.stream().filter(Wards::warded).count()));
			return null;
		})));
		// what the respawn ritual's pillar stage does to each spike, then the feature over what is left
		STEPS.add(new Step(40, m -> onServer(m, server -> {
			ServerLevel end = end(server);
			for (SpikeFeature.EndSpike spike : spikes) {
				for (BlockPos pos : BlockPos.betweenClosed(spike.getCenterX() - 10, spike.getHeight() - 10, spike.getCenterZ() - 10,
						spike.getCenterX() + 10, spike.getHeight() + 10, spike.getCenterZ() + 10))
					end.removeBlock(pos, false);
				for (EndCrystal crystal : end.getEntitiesOfClass(EndCrystal.class, spike.getTopBoundingBox())) crystal.discard();
				end.explode(null, spike.getCenterX() + 0.5F, spike.getHeight(), spike.getCenterZ() + 0.5F, 5.0F, Level.ExplosionInteraction.BLOCK);
				Feature.END_SPIKE.place(new SpikeConfiguration(true, ImmutableList.of(spike), new BlockPos(0, 128, 0)), end,
						end.getChunkSource().getGenerator(), RandomSource.create(), new BlockPos(spike.getCenterX(), 45, spike.getCenterZ()));
			}
			return null;
		})));
		STEPS.add(new Step(1, m -> verify(m, spikes, "rebuilt")));
		shoot(view(95, 115, 95, 0, 75, 0), "arena-overview-rebuilt", 20);
		// the crystal beam's runes, close up: a crystal over the altar beaming at a point 24 blocks off
		command("execute in minecraft:the_end run summon minecraft:end_crystal 0.5 100 0.5 {beam_target:[I;24,98,0],ShowBottom:0b}", 5);
		shoot(view(8, 101.5, 4.5, 8, 99.5, 0.5), "arena-beam", 40);
		shoot(view(-2, 100, 5, 14, 98.5, 0.5), "arena-beam-along", 20);
	}

	/** The world holds the monolith exactly (nothing missing, nothing stray around it) and its crystal. */
	private static void verify(Minecraft mc, List<SpikeFeature.EndSpike> spikes, String when) {
		onServer(mc, server -> {
			ServerLevel end = end(server);
			for (SpikeFeature.EndSpike spike : spikes) {
				Monolith m = Monoliths.build(end, spike);
				int[] wrong = {0};
				List<String> samples = new ArrayList<>();
				m.forEach((x, y, z, block) -> {
					if (x == m.centerX && z == m.centerZ && y == m.crystalY()) return;   // the crystal's fire
					BlockState s = end.getBlockState(new BlockPos(x, y, z));
					if (!matches(s, block)) {
						wrong[0]++;
						if (samples.size() < 4) samples.add((x - m.centerX) + "," + y + "," + (z - m.centerZ) + " " + block + "/" + s.getBlock().getDescriptionId());
					}
				});
				int stray = 0;
				int reach = (int) Math.ceil(m.extent()) + 1;
				for (BlockPos pos : BlockPos.betweenClosed(m.centerX - reach, m.groundY, m.centerZ - reach,
						m.centerX + reach, m.height + 30, m.centerZ + reach)) {
					BlockState s = end.getBlockState(pos);
					if (s.is(Blocks.OBSIDIAN) && m.at(pos.getX(), pos.getY(), pos.getZ()) == null) {
						stray++;
						if (samples.size() < 8) samples.add("stray " + (pos.getX() - m.centerX) + "," + pos.getY() + "," + (pos.getZ() - m.centerZ));
					}
				}
				AABB at = new AABB(m.centerX, m.crystalY(), m.centerZ, m.centerX + 1, m.crystalY() + 1, m.centerZ + 1).inflate(0.5);
				int crystals = end.getEntitiesOfClass(EndCrystal.class, at).size();
				boolean warded = end.getEntitiesOfClass(EndCrystal.class, at).stream().anyMatch(Wards::warded);
				String what = String.format(Locale.ROOT, "%s %s spike at %d, %d (h %d, r %d, %s)", when, m.kind, m.centerX, m.centerZ, m.height, m.radius,
						warded ? "warded" : "bare");
				check(wrong[0] == 0, what + ": the world holds the monolith (" + wrong[0] + " of " + m.size() + " blocks differ)");
				check(stray == 0, what + ": no stray blocks around it (" + stray + ")");
				check(crystals == 1, what + ": one crystal at vanilla's spot (" + crystals + ")");
				boolean fire = end.getBlockState(new BlockPos(m.centerX, m.crystalY(), m.centerZ)).is(DragonFire.BLOCK);
				check(fire, what + ": dragon fire under the crystal");
				if (!samples.isEmpty()) REPORT.add("     e.g. " + String.join("; ", samples));
				if (crystals == 0) {
					List<EndCrystal> near = end.getEntitiesOfClass(EndCrystal.class, new AABB(m.centerX - 12, end.getMinBuildHeight(), m.centerZ - 12,
							m.centerX + 12, end.getMaxBuildHeight(), m.centerZ + 12));
					REPORT.add(String.format(Locale.ROOT, "     no crystal: entities %s there; %d crystal(s) within 12 blocks%s",
							end.isPositionEntityTicking(new BlockPos(m.centerX, m.crystalY(), m.centerZ)) ? "ticking" : "not ticking", near.size(),
							near.isEmpty() ? "" : ", first at y " + (int) near.get(0).getY()));
				}
			}
			return null;
		});
	}

	private static boolean matches(BlockState state, Monolith.Block block) {
		return switch (block) {
			case AIR -> state.isAir();
			case OBSIDIAN -> state.is(Blocks.OBSIDIAN);
			case BEDROCK -> state.is(Blocks.BEDROCK);
		};
	}

	private static ServerLevel end(MinecraftServer server) {
		return server.getLevel(Level.END);
	}

	private static <T> T onServer(Minecraft mc, java.util.function.Function<MinecraftServer, T> task) {
		MinecraftServer server = mc.getSingleplayerServer();
		return server.submit(() -> task.apply(server)).join();
	}

	/** As {@link Script#view}, in the End. */
	private static String view(double x, double y, double z, double tx, double ty, double tz) {
		return String.format(Locale.ROOT, "execute in minecraft:the_end run tp @s %.1f %.1f %.1f facing %.1f %.1f %.1f", x, y, z, tx, ty, tz);
	}
}
