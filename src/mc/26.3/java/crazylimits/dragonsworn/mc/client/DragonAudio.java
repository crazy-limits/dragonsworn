package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.anim.DragonVoice;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/** Plays the dragon's sounds on the client when {@link DragonVoice} cues them. Client only. */
public final class DragonAudio {
	/** Part indices (PoseTrack): the chest, between the wings. */
	private static final int CHEST = 2;

	private DragonAudio() {}

	public static void play(EnderDragon dragon, DragonVoice.Cue cue) {
		float jitter = dragon.getRandom().nextFloat() - 0.5F;
		switch (cue) {
			case ROAR -> roar(dragon, false);
			case WING -> at(dragon, DragonswornDragon.brain(dragon).partCenter(CHEST), DragonSounds.WING, 1.0F, 0.95F + 0.12F * jitter);
			// the hind feet carry the weight; the folded hands come down lighter
			case STEP_HIND -> at(dragon, dragon.position(), DragonSounds.STEP, 1.0F, 0.85F + 0.1F * jitter);
			case STEP_FRONT -> at(dragon, dragon.position(), DragonSounds.STEP, 0.55F, 1.0F + 0.1F * jitter);
		}
	}

	/** Starts a roar; {@code openJaw} when no animation opens the jaw for it (in flight). */
	public static void roar(EnderDragon dragon, boolean openJaw) {
		DragonVoice.Roar roar = DragonswornDragon.brain(dragon).roar;
		int sequence = roar.start(dragon.tickCount, openJaw);
		Minecraft.getInstance().getSoundManager().play(new RoarSound(dragon, roar, sequence));
	}

	private static void at(EnderDragon dragon, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		dragon.level().playLocalSound(pos.x, pos.y, pos.z, sound, dragon.getSoundSource(), volume, pitch, false);
	}
}
