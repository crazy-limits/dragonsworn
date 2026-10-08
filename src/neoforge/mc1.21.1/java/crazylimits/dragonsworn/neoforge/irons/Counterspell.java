package crazylimits.dragonsworn.neoforge.irons;

import crazylimits.dragonsworn.config.DragonConfig;
import crazylimits.dragonsworn.mc.DragonSounds;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Iron's Spells 'n Spellbooks: now and then the dragon counters a spell a player starts casting in its sight,
 * as Iron's Counterspell does to a player it hits: the spell fizzles before it starts and goes on cooldown,
 * the player's magic effects end and their recasts are lost. A line of enchanting glyphs runs from its head
 * to the player (Counterspell's ray). Chance, cooldown and range: {@code [spells]} in the config.
 */
public final class Counterspell {
	/** When each dragon may counter again (game time). */
	private static final Map<EnderDragon, Long> READY = new WeakHashMap<>();

	private Counterspell() {}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(SpellPreCastEvent.class, Counterspell::preCast);
	}

	private static void preCast(SpellPreCastEvent event) {
		if (event.isCanceled() || !DragonConfig.COUNTERSPELL.get()) return;
		if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
		AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
		if (spell == SpellRegistry.none()) return;
		ServerLevel level = player.serverLevel();
		EnderDragon dragon = watcher(level, player);
		if (dragon == null || level.random.nextDouble() >= DragonConfig.COUNTERSPELL_CHANCE.get()) return;
		// other mods may shield a player from counterspells (as from Iron's own)
		if (NeoForge.EVENT_BUS.post(new CounterSpellEvent(dragon, player)).isCanceled()) return;
		READY.put(dragon, level.getGameTime() + DragonConfig.COUNTERSPELL_COOLDOWN.get());

		event.setCanceled(true);
		MagicHelper.MAGIC_MANAGER.addCooldown(player, spell, event.getCastSource());
		MagicData.getPlayerMagicData(player).getPlayerRecasts().removeAll(RecastResult.COUNTERSPELL);
		List<Holder<MobEffect>> magic = player.getActiveEffectsMap().keySet().stream()
				.filter(effect -> effect.value() instanceof MagicMobEffect).toList();
		magic.forEach(player::removeEffect);

		Vec3 head = dragon.head.getBoundingBox().getCenter(), eyes = player.getEyePosition();
		Vec3 step = eyes.subtract(head);
		double length = step.length();
		step = step.scale(1.0 / length);
		for (double d = 0.5; d < length; d += 0.5) {
			level.sendParticles(ParticleTypes.ENCHANT, head.x + step.x * d, head.y + step.y * d, head.z + step.z * d, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, head.x, head.y, head.z, DragonSounds.COUNTERSPELL, SoundSource.HOSTILE, 4.0F, 0.7F);
	}

	/** A living dragon in range that sees the player and may counter now, if any. */
	private static EnderDragon watcher(ServerLevel level, ServerPlayer player) {
		double range = DragonConfig.COUNTERSPELL_RANGE.get();
		for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, player.getBoundingBox().inflate(range))) {
			if (dragon.isDeadOrDying() || dragon.dragonDeathTime > 0) continue;
			if (READY.getOrDefault(dragon, 0L) > level.getGameTime()) continue;
			if (dragon.distanceTo(player) > range || !dragon.hasLineOfSight(player)) continue;
			return dragon;
		}
		return null;
	}
}
