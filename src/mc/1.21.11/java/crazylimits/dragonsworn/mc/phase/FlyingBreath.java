package crazylimits.dragonsworn.mc.phase;

import crazylimits.dragonsworn.attack.BreathPass;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.DragonSounds;
import crazylimits.dragonsworn.mc.DragonswornDragon;
import crazylimits.dragonsworn.mc.breath.BreathStreamPhase;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/**
 * The stream breath poured in flight, on {@link BreathPass}'s timing: the breath pass's (on the glide)
 * and the hover's. The server aims and burns ({@link #tick}); both sides time it off the animation clock
 * ({@link BreathPassPhase#breathTicks}), the client its sounds ({@link #sounds}).
 */
final class FlyingBreath {
	private FlyingBreath() {
	}

	/**
	 * One tick {@code ticks} into the breath: the aim's angles (off the facing, below level) swing from
	 * {@code angles} toward {@code want} (at {@code windupTurn} degrees a tick while inhaling, {@code streamTurn}
	 * after), the flames' landing point is synced as the strike's aim (null once the stream is over), and
	 * every {@link BreathPass#DAMAGE_INTERVAL} ticks of the stream a flame puff leaves the mouth
	 * ({@code BreathFlames}: it burns what it reaches, when it gets there). {@code base}: the
	 * neck's base (world), where the aim is measured from. Returns the new angles.
	 */
	static double[] tick(EnderDragon dragon, int ticks, Vec3 base, double[] angles, double[] want,
			double windupTurn, double streamTurn, double range, float damage) {
		DragonBrain brain = DragonswornDragon.brain(dragon);
		double[] aim = BreathPass.chase(angles, want, ticks < BreathPass.WINDUP_TICKS ? windupTurn : streamTurn);
		double[] d = BreathPass.direction(dragon.getYRot(), aim);
		Vec3 dir = new Vec3(d[0], d[1], d[2]);
		Vec3 point = ticks < BreathPass.WINDUP_TICKS + BreathPass.STREAM_TICKS
				? BreathStreamPhase.stream(dragon, base, dir, range).getLocation() : null;
		brain.aimStrike(point);
		if (point != null && BreathPass.streaming(ticks) && (ticks - BreathPass.WINDUP_TICKS) % BreathPass.DAMAGE_INTERVAL == 0) {
			Vec3 mouth = brain.partCenter(Parts.HEAD);
			Vec3 to = point.subtract(mouth);
			if (to.lengthSqr() > 1e-4) brain.flames.pour(mouth, to.normalize(), range, damage, true);
		}
		return aim;
	}

	/** Client: the inhale's roar as the stream starts, then the stream's hiss, on the animation clock. */
	static void sounds(EnderDragon dragon) {
		double ticks = BreathPassPhase.breathTicks(dragon, 0.0F);
		if (Double.isNaN(ticks)) return;
		int tick = (int) Math.round(ticks);
		if (tick == BreathPass.WINDUP_TICKS) {
			dragon.level().playLocalSound(dragon.getX(), dragon.getY(), dragon.getZ(), DragonSounds.BREATH,
					dragon.getSoundSource(), 4.0F, 0.7F, false);
		}
		if (BreathPass.streaming(tick) && tick % 5 == 0) {
			dragon.level().playLocalSound(dragon.getX(), dragon.getY(), dragon.getZ(), DragonSounds.FLAMES,
					dragon.getSoundSource(), 3.0F, 0.45F + dragon.getRandom().nextFloat() * 0.1F, false);
		}
	}
}
