package crazylimits.dragonfall.mc;

import crazylimits.dragonfall.body.Grip;
import crazylimits.dragonfall.limb.BodyFrame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * What the dragon holds in its talons or jaws ({@link Grip}), both sides. The prey rides the dragon:
 * every tick, on both sides, it is put where the hold is ({@link #holdPoint}), so it cannot move (and is
 * drawn standing, not seated), but it can still use items: an ender pearl takes it out of the hold, as
 * does anything that dismounts it, the dragon dying, or the attack letting go. Shift does not
 * ({@code PlayerMixin}). The hold and the prey are synced in {@link DragonData#GRIP}.
 */
public final class PreyHold {
	/** Size of what can be held: a player, a zombie, a skeleton; not a ravager or a horse. */
	static final double MAX_WIDTH = 1.0, MAX_HEIGHT = 2.2;

	private final EnderDragon dragon;
	private final DragonBrain brain;

	PreyHold(EnderDragon dragon, DragonBrain brain) {
		this.dragon = dragon;
		this.brain = brain;
	}

	public Grip.Hold hold() {
		return Grip.hold(dragon.getEntityData().get(DragonData.GRIP));
	}

	/** The prey reached for or held, null for none (or not known on this side). */
	@Nullable
	public Entity prey() {
		int id = Grip.entity(dragon.getEntityData().get(DragonData.GRIP));
		return id < 0 ? null : dragon.level().getEntity(id);
	}

	/** Holding something now (in the talons or the jaws, not just reaching). */
	public boolean holding() {
		Grip.Hold hold = hold();
		return hold == Grip.Hold.TALON || hold == Grip.Hold.JAW;
	}

	/** Whether {@code entity} is the prey held, riding the dragon. */
	public boolean holds(Entity entity) {
		return holding() && entity.getVehicle() == dragon && entity.getId() == Grip.entity(dragon.getEntityData().get(DragonData.GRIP));
	}

	/** Whether {@code entity} could be held at all: alive, not too big, not riding or carrying something else. */
	public static boolean holdable(LivingEntity entity) {
		if (!entity.isAlive() || entity.getBbWidth() > MAX_WIDTH || entity.getBbHeight() > MAX_HEIGHT || entity.isVehicle()) return false;
		return !(entity instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/**
	 * Where the prey's position (its feet) goes now (world). It is drawn lying flat, turned about its
	 * middle, so the middle is what the hold puts in the talons or the jaws: the feet are half its height
	 * below that.
	 */
	public Vec3 holdPoint(Entity prey) {
		if (hold() == Grip.Hold.JAW) {
			Vec3 head = brain.partCenter(0);
			return head.subtract(0.0, Grip.JAW_BELOW + prey.getBbHeight() / 2.0, 0.0);
		}
		return talonPoint(prey);
	}

	/** Where the talons would put {@code prey}'s feet right now, whatever is held (the snatch's aim). */
	public Vec3 talonPoint(Entity prey) {
		double[] ankle = frame().toWorld(Grip.TALON_ANKLE, new double[3]);
		return new Vec3(ankle[0], ankle[1] - Grip.TALON_BELOW - prey.getBbHeight() / 2.0, ankle[2]);
	}

	/**
	 * The yaw (Minecraft's, degrees) a held prey lies along, head first: the dragon's length in the
	 * talons, across the head (pointing out to the dragon's right) in the jaws. NaN when not held.
	 */
	public double lyingYaw(Entity prey, float partialTick) {
		if (!holds(prey)) return Double.NaN;
		// the dragon's yaw points backward by Minecraft's convention (its facing is (sin, -cos))
		if (hold() == Grip.Hold.TALON) return brain.body.yaw(partialTick) + 180.0;
		Vec3 head = brain.partCenter(0), neck = brain.partCenter(1);
		double dx = head.x - neck.x, dz = head.z - neck.z;
		return Math.toDegrees(Math.atan2(-dx, dz)) + 90.0;
	}

	/** The model as placed this tick (as the server's hitboxes and the client's model are). */
	private BodyFrame frame() {
		return new BodyFrame().set(dragon.getX(), dragon.getY() + brain.body.lift(1.0F), dragon.getZ(),
				brain.body.yaw(1.0F), brain.body.pitch(1.0F), brain.body.roll(1.0F));
	}

	// ---------------------------------------------------------------- server

	/** The talons reach for {@code prey} (the swoop): the leg comes down for it on every client. */
	public void reach(LivingEntity prey) {
		set(Grip.Hold.REACH, prey);
	}

	/** Takes hold of {@code prey}; false when it cannot be held. */
	public boolean seize(LivingEntity prey, Grip.Hold hold) {
		if (!holdable(prey)) return false;
		if (prey.isPassenger()) prey.stopRiding();
		if (!prey.startRiding(dragon, true)) return false;
		set(hold, prey);
		prey.resetFallDistance();
		return true;
	}

	/**
	 * Lets go (or stops reaching): the prey drops from the hold, moving with {@code velocity} (the dragon's own when null: a
	 * dropped prey keeps the carry's momentum).
	 */
	public void release(@Nullable Vec3 velocity) {
		Entity prey = prey();
		boolean held = holding();
		Vec3 v = velocity != null ? velocity : dragon.getDeltaMovement();
		// dismounted while still held, so it is left where the hold put it (getDismountLocationForPassenger)
		if (prey != null && prey.getVehicle() == dragon) prey.stopRiding();
		set(Grip.Hold.NONE, null);
		if (held && prey != null && prey.isAlive()) {
			prey.setDeltaMovement(v);
			prey.hurtMarked = true;
		}
	}

	/** Every tick: a prey that got away (ender pearl, death, any dismount) is no longer held. */
	void tick() {
		Grip.Hold hold = hold();
		if (hold == Grip.Hold.NONE) return;
		Entity prey = prey();
		boolean gone = prey == null || !prey.isAlive();
		if (gone || hold != Grip.Hold.REACH && prey.getVehicle() != dragon) set(Grip.Hold.NONE, null);
	}

	private void set(Grip.Hold hold, @Nullable Entity prey) {
		int bits = Grip.encode(hold, prey == null ? -1 : prey.getId());
		if (dragon.getEntityData().get(DragonData.GRIP) != bits) dragon.getEntityData().set(DragonData.GRIP, bits);
	}
}
