package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.anim.DragonVoice;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/**
 * One roar, coming from the dragon's head as it moves. Its volume follows the dragon's
 * {@link DragonVoice.Roar}: full while it lasts, ramped down when an attack cuts it short. It stops when
 * the roar is over, replaced by another, or the dragon is gone.
 */
final class RoarSound extends AbstractTickableSoundInstance {
	private final EnderDragon dragon;
	private final DragonVoice.Roar roar;
	private final int sequence;

	RoarSound(EnderDragon dragon, DragonVoice.Roar roar, int sequence) {
		super(DragonSounds.ROAR, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
		this.dragon = dragon;
		this.roar = roar;
		this.sequence = sequence;
		this.volume = 1.0F;
		this.pitch = (float) DragonVoice.ROAR_PITCH + (random.nextFloat() - 0.5F) * 0.06F;
		follow();
	}

	@Override
	public void tick() {
		float v = (float) roar.volume(dragon.tickCount);
		if (dragon.isRemoved() || roar.sequence() != sequence || v <= 0.0F) {
			stop();
			return;
		}
		volume = v;
		follow();
	}

	private void follow() {
		Vec3 head = DragonswornDragon.brain(dragon).partCenter(Parts.HEAD);
		x = head.x;
		y = head.y;
		z = head.z;
	}
}
