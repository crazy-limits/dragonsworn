package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonPhases;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code stance}: the wild fight's ground, break in the air, ground again cycle. */
final class StanceStage {
	private StanceStage() {
	}


	/**
	 * The lazy fight: a wild dragon lands to fight a husk on foot. Hurt too much there, it takes a break in
	 * the air (the hits are given to its brain as the husk's: vanilla lets only players hurt a dragon);
	 * hurt again up there, it lands beside the husk once more and fights on, on the ground.
	 */
	static void stance(int sx, int y, int sz) {
		command(view(sx - 40, y + 20, sz, sx, y + 12, sz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", sx, y + 20, sz), 20);
		// invulnerable: a snatch's drop must not end the test; with its AI, so a drop falls to the ground
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {Invulnerable:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", sx - 30, y, sz + 10), 5);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tactics.tryGroundAssault(prey.get(0)),
					"stance: there is room to land beside the husk");
		});
		boolean[] landed = new boolean[3], lifted = new boolean[1];
		for (int i = 0; i < 30; i++) {
			track(String.format(Locale.ROOT, "stance-land-%02d", i), 10, 30);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				landed[0] |= dragon != null && DragonswornDragon.brain(dragon).onGround();
			});
		}
		// the husk hurts it, a fifth of its health and more: up for a break
		STEPS.add(new Step(80, mc -> {}));
		server(level -> stanceHit(level, DragonConfig.GROUND_HEALTH_LIMIT.get() + 0.01));
		// watched for less than the shortest break (DragonConfig.BREAK_MIN.get())
		for (int i = 0; i < 12; i++) {
			track(String.format(Locale.ROOT, "stance-up-%02d", i), 10, 34);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				lifted[0] |= dragon != null && dragon.getPhaseManager().getCurrentPhase().getPhase() == DragonPhases.LIFTOFF;
			});
		}
		Set<String> breakPhases = new LinkedHashSet<>();
		for (int i = 0; i < 5; i++) {
			track(String.format(Locale.ROOT, "stance-air-%02d", i), 20, 34);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				breakPhases.add(dragon.getPhaseManager().getCurrentPhase().getPhase().toString().replaceAll(" .*", ""));
				landed[1] |= DragonswornDragon.brain(dragon).onGround();
			});
		}
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			check(landed[0], "stance: it landed to fight the husk on foot");
			check(lifted[0], "stance: hurt too much on the ground, it took off");
			REPORT.add("INFO stance: phases on the break: " + breakPhases);
			check(!landed[1] && dragon != null && !DragonswornDragon.brain(dragon).stance.grounded(), "stance: it stayed in the air for its break");
		});
		// hurt in the air: back down to fight on foot
		server(level -> stanceHit(level, DragonConfig.AIR_HEALTH_LIMIT.get() + 0.01));
		for (int i = 0; i < 50; i++) {
			track(String.format(Locale.ROOT, "stance-down-%02d", i), 10, 34);
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				landed[2] |= dragon != null && DragonswornDragon.brain(dragon).onGround();
			});
		}
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			if (dragon != null && !prey.isEmpty()) {
				Husk husk = prey.get(0);
				REPORT.add(String.format(Locale.ROOT, "INFO stance: at the end %s, %s; husk %.0f %.0f %.0f on ground %b, %.0f blocks off",
						dragon.getPhaseManager().getCurrentPhase().getPhase(), DragonswornDragon.brain(dragon).stance.stance(),
						husk.getX(), husk.getY(), husk.getZ(), husk.onGround(), husk.distanceTo(dragon)));
			}
			check(landed[2], "stance: hurt in the air, it landed again to fight on the ground");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}

	/** The husk hurts the AI dragon, {@code fraction} of its health, as far as its brain knows. */
	static void stanceHit(ServerLevel level, double fraction) {
		EnderDragon dragon = aiDragon(level);
		List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
		if (dragon == null || prey.isEmpty()) return;
		var source = level.damageSources().mobAttack(prey.get(0));
		DragonBrain brain = DragonswornDragon.brain(dragon);
		brain.combat.hurtBy(source, 2);
		brain.combat.hit(source, (float) (fraction * dragon.getMaxHealth()));
	}
}
