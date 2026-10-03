package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.ai.Foothold;
import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.phase.GroundApproachPhase;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.zombie.Husk;

import java.util.List;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/** Stage {@code narrow}: the dragon comes down on a narrow foothold beside a husk on a pillar. */
final class NarrowStage {
	private NarrowStage() {
	}


	/**
	 * A narrow foothold: a husk on top of a lone 1-block pillar, 12 blocks up, and beside it (6 blocks east)
	 * the only place to come down: a 3 by 3 platform ({@link Foothold#UPRIGHT}) or another lone pillar
	 * ({@link Foothold#CLING}). The dragon must land there with that foothold, stay on it (no walking), and
	 * bite the husk with that foothold's bite, never its tail.
	 */
	static void narrow(int sx, int y, int sz, Foothold foothold) {
		String name = foothold.name().toLowerCase(Locale.ROOT);
		int top = y + 12, half = foothold == Foothold.UPRIGHT ? 1 : 0, px = sx + 6;
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", sx, y, sz, sx, top - 1, sz), 2);
		command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", px - half, y, sz - half, px + half, top - 1, sz + half), 2);
		command(view(sx - 30, top + 12, sz - 10, sx + 3, top, sz), 40);
		command(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {NoAI:1b,PersistenceRequired:1b,Tags:[\"df_prey\"]}", sx, top, sz), 5);
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %d %d %d {Tags:[\"df_ai\"]}", sx - 25, top + 15, sz), 20);
		server(level -> {
			EnderDragon dragon = aiDragon(level);
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(dragon != null && !prey.isEmpty() && DragonswornDragon.brain(dragon).tactics.tryGroundAssault(prey.get(0)),
					name + ": somewhere to come down beside the husk");
			check(dragon != null && dragon.getPhaseManager().getCurrentPhase() instanceof GroundApproachPhase approach
					&& approach.foothold() == foothold, name + ": it comes down " + name);
		});
		boolean[] seen = new boolean[4];   // landed with it, bit with its bite, struck with the tail or roared, walked off
		double[] at = {Double.NaN, 0.0};
		for (int i = 0; i < 70; i++) {
			if (i == 30 || i == 50) {
				shoot(view(px - 14, top + 6, sz - 12, px, top + 4, sz), "narrow-" + name + "-" + i, 2);
			} else {
				track(String.format(Locale.ROOT, "narrow-%s-%02d", name, i), 6, 26, 6);
			}
			server(level -> {
				EnderDragon dragon = aiDragon(level);
				if (dragon == null) return;
				DragonBrain brain = DragonswornDragon.brain(dragon);
				if (!brain.onGround()) return;
				seen[0] |= brain.foothold() == foothold;
				DragonAnim action = brain.action();
				seen[1] |= action == (foothold == Foothold.UPRIGHT ? DragonAnim.UPRIGHT_BITE : DragonAnim.CLING_BITE);
				seen[2] |= action == DragonAnim.TAIL_SWEEP || action == DragonAnim.ROAR || action == DragonAnim.ATTACK;
				if (Double.isNaN(at[0])) {
					at[0] = dragon.getX();
					at[1] = dragon.getZ();
				}
				seen[3] |= Math.hypot(dragon.getX() - at[0], dragon.getZ() - at[1]) > 1.0;
			});
		}
		server(level -> {
			List<? extends Husk> prey = level.getEntities(EntityType.HUSK, e -> e.getTags().contains("df_prey"));
			check(seen[0], name + ": it landed on the foothold " + name);
			check(seen[1], name + ": it bit with the " + name + " bite");
			check(!seen[2], name + ": no tail strike, roar or four-legged bite up there");
			check(!seen[3], name + ": it stayed on its foothold");
			check(prey.isEmpty() || prey.get(0).getHealth() < prey.get(0).getMaxHealth(), name + ": the bite hurt the husk");
		});
		command("kill @e[tag=df_ai]", 2);
		command("kill @e[tag=df_prey]", 2);
	}
}
