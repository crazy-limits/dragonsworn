package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.phase.GroundFightPhase;
import crazylimits.dragonsworn.mc.phase.RoamPhase;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * Stage {@code aim}: fireballs that fly at their target with the head on it, no staring at a target it
 * cannot reach, the wing buffet at what stands in a blind spot, and the roar at what stays far off.
 */
final class AimStage {
	/** A fireball counts as on target when it passes this close to the husk's middle (blocks): its cloud covers that. */
	private static final double ON_TARGET = 4.0;
	/** The husk walks sideways across the dragon's way this fast (blocks a tick; a player sprints 0.28). */
	private static final double STRIDE = 0.2;

	private AimStage() {
	}

	/**
	 * The fireball pass and the barrage, each at a husk standing still and at one walking across the way:
	 * every fireball must pass within {@link #ON_TARGET} of it. Then a husk on a pillar the dragon cannot
	 * bite up to: landed beside it, the dragon must give up within {@code unreached_patience} and take off.
	 */
	static void aim(int x, int y, int z) {
		command(view(x - 50, y + 25, z - 20, x, y + 4, z), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", x, y, z), 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", x, y + 24, z - 80), 40);
		fireballs("pass-still", x, y, z, 0.0, RoamPhase::startPass);
		fireballs("pass-moving", x, y, z, STRIDE, RoamPhase::startPass);
		fireballs("barrage-still", x, y, z, 0.0, RoamPhase::startBarrage);
		fireballs("barrage-moving", x, y, z, STRIDE, RoamPhase::startBarrage);
		command("kill @e[tag=df_prey]", 2);
		unreachable(x + 150, y, z);
		command("kill @e[tag=df_prey]", 2);
		blindSpot(x + 300, y, z);
		command("kill @e[tag=df_prey]", 2);
		farRoar(x + 450, y, z);
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/** One attack's fireballs at the husk, which walks {@code stride} blocks a tick across the dragon's way (east-west). */
	private static void fireballs(String name, int x, int y, int z, double stride, BiConsumer<RoamPhase, Husk> start) {
		Map<Integer, Double> closest = new HashMap<>();
		int[] ticks = new int[1], watched = new int[2];   // hunting ticks with the head hard on the husk, all hunting ticks
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty()) return;
			Husk husk = prey(level).get(0);
			husk.teleportTo(x + 0.5, y, z + 0.5);
			// a fresh roam, 80 blocks off to the north, flying at it
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
			dragon.getPhaseManager().setPhase(DragonPhases.ROAM);
			dragon.teleportTo(x + 0.5, y + 24, z - 80);
			dragon.setYRot(180.0F);
			dragon.setDeltaMovement(new Vec3(0.0, 0.0, 0.8));
			start.accept(dragon.getPhaseManager().getPhase(DragonPhases.ROAM), husk);
		});
		serverUntil(900, level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty()) return true;
			Husk husk = prey(level).get(0);
			// back and forth across the way, 12 blocks either side
			double walked = ++ticks[0] * stride % 48.0;
			if (stride > 0.0) husk.teleportTo(x + 0.5 + (walked < 24.0 ? walked - 12.0 : 36.0 - walked), y, z + 0.5);
			Vec3 middle = husk.position().add(0.0, husk.getBbHeight() * 0.5, 0.0);
			DragonBrain brain = DragonswornDragon.brain(dragon);
			if (dragon.getPhaseManager().getCurrentPhase() instanceof RoamPhase roam && !roam.idle() && ticks[0] > 2) {
				watched[1]++;
				if (brain.lookTarget() == husk && (brain.lookingHard() || brain.fireballs.charging())) watched[0]++;
			}
			for (DragonFireball fireball : level.getEntities(EntityTypes.DRAGON_FIREBALL, e -> e.getOwner() == dragon)) {
				closest.merge(fireball.getId(), fireball.position().distanceTo(middle), Math::min);
			}
			// over once the attack is and its last fireball has burst
			return dragon.getPhaseManager().getCurrentPhase() instanceof RoamPhase roam && roam.idle()
					&& !DragonswornDragon.brain(dragon).fireballs.charging()
					&& level.getEntities(EntityTypes.DRAGON_FIREBALL, e -> e.getOwner() == dragon).isEmpty();
		});
		server(level -> {
			List<String> misses = new ArrayList<>();
			for (double d : closest.values()) misses.add(String.format(Locale.ROOT, "%.1f", d));
			REPORT.add("INFO aim " + name + ": fireballs passed the husk at " + misses + " blocks");
			check(!closest.isEmpty(), "aim " + name + ": it fires");
			check(watched[1] > 0 && watched[0] == watched[1], String.format(Locale.ROOT, "aim %s: its head is on the husk all through the attack (%d of %d ticks)",
					name, watched[0], watched[1]));
			check(closest.values().stream().allMatch(d -> d < ON_TARGET), "aim " + name + ": every fireball passes within " + ON_TARGET + " blocks of the husk");
		});
	}

	/**
	 * A husk on top of a 1-block pillar 9 blocks high (within the 10 a target may be above it), the dragon
	 * landed 8 blocks off: no bite reaches up there. It must take off within {@code unreached_patience} (and
	 * the time to walk up to it), and not come down by that husk again for a while.
	 */
	private static void unreachable(int x, int y, int z) {
		int top = y + 9;
		command(view(x - 34, y + 16, z - 20, x, y + 6, z), 40);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x, y, z, x, top - 1, z), 2);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", x, top, z), 2);
		int[] took = {-1};
		boolean[] walled = new boolean[1];
		Set<String> after = new LinkedHashSet<>();
		int[] start = new int[1];
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty()) return;
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
			dragon.teleportTo(x + 0.5, y, z - 7.5);
			dragon.setYRot(180.0F);
			// the husk hurts it: the target it comes back to from the air
			DragonswornDragon.brain(dragon).combat.hurtBy(level.damageSources().mobAttack(prey(level).get(0)), 2);
			GroundFightPhase.start(dragon, prey(level).get(0), false);
			start[0] = dragon.tickCount;
		});
		int patience = DragonConfig.UNREACHED_PATIENCE.get() + 300;
		for (int i = 0; i < patience; i++) {
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null || took[0] >= 0) return;
				if (dragon.getPhaseManager().getCurrentPhase().getPhase() != DragonPhases.LIFTOFF || prey(level).isEmpty()) return;
				took[0] = dragon.tickCount - start[0];
				walled[0] = DragonswornDragon.brain(dragon).tactics.isWalled(prey(level).get(0));
			});
			if (i % 40 == 0) track(String.format(Locale.ROOT, "aim-unreachable-%02d", i / 40), 1, 30, 10);
		}
		serverUntil(400, level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon != null) after.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", ""));
			return false;
		});
		server(level -> {
			REPORT.add("INFO aim unreachable: took off after " + took[0] + " ticks; phases after: " + after);
			check(took[0] >= 0, "aim unreachable: it does not stand staring at a husk it cannot bite: it takes off");
			check(walled[0], "aim unreachable: and counts the husk as out of reach on the ground");
			check(!after.contains(DragonPhases.GROUND_APPROACH.toString().replaceAll(" .*", "")), "aim unreachable: and does not land by it again at once");
		});
	}

	/**
	 * A husk under its chin (too close for the jaws: the bite cannot reach under its chin), hitting it: the
	 * dragon must answer with the wing buffet and throw the husk off (a husk with its AI: one without
	 * is never moved by a push).
	 */
	private static void blindSpot(int x, int y, int z) {
		command(view(x - 24, y + 12, z - 20, x, y + 2, z), 40);
		freshDragon(x, y, z);
		Set<String> phases = new LinkedHashSet<>();
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", x, y, z - 3), 2);
		boolean[] buffeted = new boolean[1];
		double[] thrown = new double[1];
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty()) return;
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
			// facing north (-z), the husk 3 blocks ahead, under its chin
			dragon.teleportTo(x + 0.5, y, z + 0.5);
			dragon.setYRot(0.0F);
			GroundFightPhase.start(dragon, prey(level).get(0), false);
		});
		serverUntil(300, level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty()) return true;
			Husk husk = prey(level).get(0);
			// it keeps hitting the dragon from there
			// (slowly: hit too often at once it would take to the air)
			if (dragon.tickCount % 20 == 0 && !buffeted[0]) {
				DragonBrain brain = DragonswornDragon.brain(dragon);
				brain.combat.hurtBy(level.damageSources().mobAttack(husk), 0);
				brain.combat.hit(level.damageSources().mobAttack(husk), 1.0F);
			}
			buffeted[0] |= DragonswornDragon.brain(dragon).action() == DragonAnim.WING_BUFFET;
			phases.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", "") + "/" + DragonswornDragon.brain(dragon).action());
			thrown[0] = Math.max(thrown[0], Math.hypot(husk.getX() - x - 0.5, husk.getZ() - z + 2.5));
			return buffeted[0] && thrown[0] > 3.0;
		});
		for (int i = 0; i < 6; i++) track(String.format(Locale.ROOT, "aim-buffet-%02d", i), 4, 24, 6);
		server(level -> {
			REPORT.add(String.format(Locale.ROOT, "INFO aim blind spot: buffet %b, the husk thrown %.1f blocks; %s", buffeted[0], thrown[0], phases));
			check(buffeted[0], "aim blind spot: hit from under its chin, it beats its wings at the husk");
			check(thrown[0] > 3.0, "aim blind spot: the wing buffet throws the husk off");
		});
	}

	/** A new AI dragon at x, y, z (the last one's moods, a break in the air, stay with it). */
	private static void freshDragon(int x, int y, int z) {
		command("kill @e[tag=df_ai]", 2);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", x, y + 10, z), 20);
	}

	/**
	 * A husk in front of a landed dragon, out of reach (2 blocks inside {@code roar_range}), nobody close:
	 * it must roar, and the roar slow the husk for long.
	 */
	private static void farRoar(int x, int y, int z) {
		int off = (int) Math.max(DragonConfig.ROAR_QUIET.get() + 1, DragonConfig.ROAR_RANGE.get() - 2);
		command(view(x - 30, y + 14, z - 30, x, y + 2, z - 10), 40);
		freshDragon(x, y, z);
		Set<String> phases = new LinkedHashSet<>();
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", x, y, z - off), 2);
		boolean[] roared = new boolean[1];
		int[] slowed = {-1};
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty()) return;
			dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
			dragon.teleportTo(x + 0.5, y, z + 0.5);
			dragon.setYRot(0.0F);
			GroundFightPhase.start(dragon, prey(level).get(0), false);
		});
		serverUntil(200, level -> {
			EnderDragon dragon = aiDragon(level);
			if (dragon == null || prey(level).isEmpty()) return true;
			roared[0] |= DragonswornDragon.brain(dragon).action() == DragonAnim.ROAR;
			phases.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", "") + "/" + DragonswornDragon.brain(dragon).action());
			MobEffectInstance slow = prey(level).get(0).getEffect(MobEffects.SLOWNESS);
			if (slow != null) slowed[0] = Math.max(slowed[0], slow.getDuration());
			return slowed[0] > 0;
		});
		server(level -> {
			REPORT.add("INFO aim far roar: roared " + roared[0] + ", the husk slowed for " + slowed[0] + " ticks; " + phases);
			check(roared[0], "aim far roar: it roars at the husk out of its reach");
			check(slowed[0] > 100, "aim far roar: the roar slows the husk " + off + " blocks off, for long");
		});
	}
}
