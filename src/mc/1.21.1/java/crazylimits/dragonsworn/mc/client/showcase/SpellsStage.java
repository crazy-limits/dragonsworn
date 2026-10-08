package crazylimits.dragonsworn.mc.client.showcase;

import crazylimits.dragonsworn.config.DragonConfig;
import net.minecraft.client.CameraType;
import net.minecraft.core.Holder;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;

import java.lang.reflect.Method;
import java.util.Locale;

import static crazylimits.dragonsworn.mc.client.showcase.Script.*;

/**
 * Stage {@code spells}: Iron's Spells 'n Spellbooks' Dragon's Breath spell breathes the dragon's void flame
 * ({@code neoforge/irons}; run with {@code -Pdragonsworn.irons}, 1.21.1 NeoForge, the only target Iron's is built
 * for). The player casts it at a husk, seen first person and from behind; then the pool it
 * leaves; then a dragon counters a spell ({@code neoforge/irons/Counterspell}). Frames: {@code df-spell-*.png}. Without Iron's the stage only says so.
 */
final class SpellsStage {
	private static final String IRONS = "irons_spellbooks";
	private static final String CONE = "io.redspace.ironsspellbooks.entity.spells.dragon_breath.DragonBreathProjectile";
	private static final String POOL = "io.redspace.ironsspellbooks.entity.spells.dragon_breath.DragonBreathPool";

	private SpellsStage() {
	}

	static void spells(int x, int y, int z) {
		int[] end = {0};
		STEPS.add(new Step(1, mc -> {
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath(IRONS, "dragon_breath"))) return;
			REPORT.add("INFO Iron's Spells 'n Spellbooks is not loaded (-Pdragonsworn.irons on 1.21.1 NeoForge): stage skipped");
			index = end[0];
		}));
		double px = x + 0.5, pz = z + 0.5;
		command(String.format(Locale.ROOT, "summon minecraft:husk %.1f %d %.1f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"df_spell\"]}", px, y, pz + 5), 1);
		command(view(px, y, pz, px, y + 1.2, pz + 5), 3);
		command("cast @s " + IRONS + ":dragon_breath 5", 15);
		grab("spell-first-person");
		STEPS.add(new Step(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK)));
		STEPS.add(new Step(10, mc -> {}));
		grab("spell-behind");
		server(level -> {
			var player = level.players().get(0);
			EntityType<?> cone = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(IRONS, "dragon_breath"));
			check(!level.getEntities(cone, e -> e.isAlive()).isEmpty(), "the player breathes Dragon's Breath (its cone is alive)");
			check(player.getY() > y - 1, "the caster stands where it cast");
		});
		STEPS.add(new Step(1, mc -> {
			check(ours(CONE), "Dragonsworn's mixin changes the Dragon's Breath spell's particles");
			check(ours(POOL), "Dragonsworn's mixin changes the Dragon's Breath pool's particles");
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		}));
		// the cast runs out (5 s); then the pool, on its own
		STEPS.add(new Step(80, mc -> {}));
		command("kill @e[tag=df_spell]", 2);
		command(String.format(Locale.ROOT, "summon %s:dragon_breath_pool %.1f %d %.1f {Tags:[\"df_spell\"]}", IRONS, px, y, pz + 5), 15);
		shoot(view(px - 3, y + 2.5, pz + 1, px, y, pz + 5), "spell-pool", 3);
		command("kill @e[tag=df_spell]", 2);
		counter(x + 60, y, z);
		end[0] = STEPS.size();
	}

	/**
	 * The dragon counters a spell: a survival player in its sight casts with a magic effect on (counter chance 1).
	 * The spell fizzles (no breath) and the effect ends; at once again, within its cooldown, the spell goes through.
	 */
	private static void counter(int x, int y, int z) {
		double px = x + 0.5, pz = z + 0.5;
		Holder<MobEffect> evasion = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath(IRONS, "evasion")).orElseThrow();
		command(String.format(Locale.ROOT, "summon minecraft:ender_dragon %.1f %d %.1f {NoAI:1b,Tags:[\"df_counter\"]}", px, y + 6, pz + 25), 5);
		command(view(px, y, pz, px, y + 8, pz + 25), 2);
		command("gamemode survival", 1);
		command("effect give @s minecraft:resistance 60 255 true", 1);
		command("effect give @s " + IRONS + ":evasion 60 0", 2);
		server(level -> {
			DragonConfig.COUNTERSPELL_CHANCE.set(1.0);
			check(level.players().get(0).hasEffect(evasion), "the caster has a magic effect on (evasion)");
		});
		STEPS.add(new Step(1, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK)));
		command("cast @s " + IRONS + ":dragon_breath 5", 4);
		grab("spell-counter");
		server(level -> {
			check(cones(level) == 0, "the dragon counters the spell: no breath comes");
			check(!level.players().get(0).hasEffect(evasion), "the counter ends the caster's magic effect");
		});
		command("cast @s " + IRONS + ":dragon_breath 5", 5);
		server(level -> check(cones(level) > 0, "within its counter's cooldown the next spell goes through"));
		server(level -> DragonConfig.COUNTERSPELL_CHANCE.set(DragonConfig.COUNTERSPELL_CHANCE.defaultValue()));
		STEPS.add(new Step(1, mc -> mc.options.setCameraType(CameraType.FIRST_PERSON)));
		STEPS.add(new Step(100, mc -> {}));
		command("kill @e[tag=df_counter]", 2);
		command("effect clear @s", 1);
		command("gamemode creative", 1);
	}

	/** Living Dragon's Breath cones. */
	private static int cones(ServerLevel level) {
		EntityType<?> cone = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath(IRONS, "dragon_breath"));
		return level.getEntities(cone, e -> e.isAlive()).size();
	}

	private static void grab(String name) {
		STEPS.add(new Step(1, mc -> Screenshot.grab(mc.gameDirectory, "df-" + name + ".png", mc.getMainRenderTarget(),
				message -> LOG.info("{}", message.getString()))));
	}

	/** True once one of Dragonsworn's mixins is applied to {@code target}: its handler methods were merged in. */
	private static boolean ours(String target) {
		try {
			for (Method method : Class.forName(target).getDeclaredMethods()) {
				if (method.getName().contains("dragonsworn$")) return true;
			}
		} catch (ClassNotFoundException e) {
			return false;
		}
		return false;
	}
}
