package crazylimits.dragonsworn.mc;

import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.body.Parts;
import crazylimits.dragonsworn.limb.BodyFrame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the dragon holds in its talons or jaws ({@link Grip}), both sides. The prey does not ride the
 * dragon (no mount, no dismount key, no vehicle health bar): it is <i>carried</i>, ticked by the dragon
 * right after the dragon's own tick, as a passenger would be ({@link #carry}; the level skips it,
 * {@code ServerLevelMixin}/{@code ClientLevelMixin}), and then put where the hold is ({@link #holdPoint}),
 * so it cannot move but can still use items. Each side places it itself (the server ignores a held
 * player's own moves, {@code ServerGamePacketListenerImplMixin}). An ender pearl (anything that moves it
 * out of the hold) frees it, as do its death, the dragon's, or the attack letting go.
 * The hold and the prey are synced in {@link DragonData#GRIP}; the prey knows its carrier ({@link Carried}).
 */
public final class PreyHold {
	/** Moved further than this (blocks) from where the hold put it, the prey got away (an ender pearl, /tp). */
	private static final double ESCAPE = 1.0;

	private final EnderDragon dragon;
	private final DragonBrain brain;
	/** What is carried now, and where it was last put. */
	@Nullable
	private Entity carried;
	@Nullable
	private Vec3 placed;
	/** Client: the hold the local player got out of (teleported away), not taken up again until the server's hold changes. */
	private int lost;

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

	/** Whether {@code entity} is the prey held, carried by the dragon. */
	public boolean holds(Entity entity) {
		return entity == carried && holding() && entity.getId() == Grip.entity(dragon.getEntityData().get(DragonData.GRIP));
	}

	/** The dragon carrying {@code entity} now, null for none. */
	@Nullable
	public static EnderDragon carrier(Entity entity) {
		EnderDragon dragon = ((Carried) entity).dragonsworn$carrier();
		if (dragon == null) return null;
		if (dragon.isRemoved() || dragon.level() != entity.level() || !DragonswornDragon.brain(dragon).prey.holds(entity)) {
			((Carried) entity).dragonsworn$setCarrier(null);
			return null;
		}
		return dragon;
	}

	/**
	 * Whether {@code entity} could be held at all: alive, not too big ({@link Grip#fits}), not riding, carrying or
	 * held by something else. Its size is its kind's full standing size times its scale (a baby's is half), not its
	 * hitbox now: a warden digging or emerging is only a block tall, a crouching mob lower.
	 */
	public static boolean holdable(LivingEntity entity) {
		EntityDimensions size = entity.getType().getDimensions().scale(entity.getAgeScale() * entity.getScale());
		if (!entity.isAlive() || !Grip.fits(size.width(), size.height()) || entity.isVehicle()) return false;
		if (carrier(entity) != null) return false;
		return !Targets.untouchable(entity);
	}

	/**
	 * Where the prey's position (its feet) goes now (world). It is drawn lying flat, turned about its
	 * middle, so the middle is what the hold puts in the talons or the jaws: the feet are half its height
	 * below that.
	 */
	public Vec3 holdPoint(Entity prey) {
		if (hold() == Grip.Hold.JAW) {
			Vec3 head = brain.partCenter(Parts.HEAD);
			return head.subtract(0.0, Grip.JAW_BELOW + prey.getBbHeight() / 2.0, 0.0);
		}
		return talonPoint(prey);
	}

	/**
	 * Where the talons would put {@code prey}'s feet right now, whatever is held (the snatch's aim): its back
	 * against the gripping foot's sole, which is held level ({@link #padOffset}), its middle half its width
	 * (it lies flat) below that.
	 */
	public Vec3 talonPoint(Entity prey) {
		double[] ankle = frame().toWorld(Grip.TALON_ANKLE, new double[3]);
		double[] pad = padOffset(brain.body.yaw(1.0F));
		return new Vec3(ankle[0] + pad[0], ankle[1] + pad[1] - prey.getBbWidth() / 2.0 - prey.getBbHeight() / 2.0, ankle[2] + pad[2]);
	}

	/** {@link Grip#talonPad} in the world (blocks) for a dragon of body yaw {@code yaw}: the foot is held level, only turned with it. */
	public static double[] padOffset(double yaw) {
		// pitch and roll 0, position 0: the frame only turns the point
		return new BodyFrame().set(0.0, 0.0, 0.0, yaw, 0.0, 0.0).toWorld(Grip.talonPad(), new double[3]);
	}

	/**
	 * The yaw (Minecraft's, degrees) a held prey lies along, head first: the dragon's length in the
	 * talons, across the head (pointing out to the dragon's right) in the jaws. NaN when not held.
	 */
	public double lyingYaw(Entity prey, float partialTick) {
		if (!holds(prey)) return Double.NaN;
		// the dragon's yaw points backward by Minecraft's convention (its facing is (sin, -cos))
		if (hold() == Grip.Hold.TALON) return brain.body.yaw(partialTick) + 180.0;
		Vec3 head = brain.partCenter(Parts.HEAD), neck = brain.partCenter(Parts.NECK_UPPER);
		double dx = head.x - neck.x, dz = head.z - neck.z;
		return Math.toDegrees(Math.atan2(-dx, dz)) + 90.0;
	}

	/**
	 * Where a held prey's eyes are as it lies (world), between ticks: from its middle toward its head along
	 * {@link #lyingYaw}, its eye height less half its height. The first-person camera goes there
	 * ({@code CameraMixin}), not inside the dragon where its standing eyes would be. Null when not held.
	 */
	@Nullable
	public Vec3 lyingEyes(Entity prey, float partialTick) {
		double yaw = lyingYaw(prey, partialTick);
		if (Double.isNaN(yaw)) return null;
		double out = prey.getEyeHeight() - prey.getBbHeight() / 2.0, r = Math.toRadians(yaw);
		return new Vec3(Mth.lerp(partialTick, prey.xo, prey.getX()) - Math.sin(r) * out,
				Mth.lerp(partialTick, prey.yo, prey.getY()) + prey.getBbHeight() / 2.0,
				Mth.lerp(partialTick, prey.zo, prey.getZ()) + Math.cos(r) * out);
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
		set(hold, prey);
		take(prey);
		place(prey);
		prey.resetFallDistance();
		return true;
	}

	/**
	 * Lets go (or stops reaching): the prey drops from the hold, moving with {@code velocity} (the dragon's own when null: a
	 * dropped prey keeps the carry's momentum).
	 */
	public void release(@Nullable Vec3 velocity) {
		Entity prey = carried;
		boolean held = holding();
		Vec3 v = velocity != null ? velocity : dragon.getDeltaMovement();
		set(Grip.Hold.NONE, null);
		take(null);
		if (held && prey != null && prey.isAlive()) {
			// the player's client placed it by its own view of the dragon: it starts falling from the server's
			if (prey instanceof ServerPlayer player) player.connection.teleport(prey.getX(), prey.getY(), prey.getZ(), prey.getYRot(), prey.getXRot());
			prey.setDeltaMovement(v);
			prey.needsSync = true;
		}
	}

	/** Every tick (server): a prey that got away (ender pearl, death) is no longer held. */
	void tick() {
		Grip.Hold hold = hold();
		if (hold == Grip.Hold.NONE) return;
		Entity prey = prey();
		boolean gone = prey == null || !prey.isAlive();
		if (gone || hold != Grip.Hold.REACH && carried != prey) {
			set(Grip.Hold.NONE, null);
			take(null);
		}
	}

	/**
	 * Right after the dragon's own tick in the level's entity loop, both sides: the held prey's tick (the
	 * level skipped it), then it is put in the hold, as a vehicle ticks and places its passengers. A prey
	 * found away from where it was put (teleported: an ender pearl) has got out.
	 */
	public void carry() {
		if (dragon.level().isClientSide()) follow();
		Entity prey = carried;
		if (prey == null) return;
		if (prey.isRemoved() || prey.level() != dragon.level() || prey.isPassenger() || !holds(prey)) {
			take(null);
			return;
		}
		if (placed != null && prey.position().distanceToSqr(placed) > ESCAPE * ESCAPE) {
			escaped();
			return;
		}
		prey.setOldPosAndRot();
		prey.tickCount++;
		prey.tick();
		if (!prey.isRemoved() && holds(prey)) place(prey);
	}

	/** Client: carries what the server says is held, unless the local player already got out of it. */
	private void follow() {
		int bits = dragon.getEntityData().get(DragonData.GRIP);
		if (lost != 0 && bits != lost) lost = 0;
		Entity prey = holding() && bits != lost ? prey() : null;
		if (prey != carried) take(prey);
	}

	private void escaped() {
		if (dragon.level().isClientSide()) lost = dragon.getEntityData().get(DragonData.GRIP);
		else set(Grip.Hold.NONE, null);
		take(null);
	}

	private void take(@Nullable Entity prey) {
		if (carried != null && ((Carried) carried).dragonsworn$carrier() == dragon) ((Carried) carried).dragonsworn$setCarrier(null);
		carried = prey;
		placed = null;
		if (prey != null) ((Carried) prey).dragonsworn$setCarrier(dragon);
	}

	private void place(Entity prey) {
		Vec3 at = holdPoint(prey);
		prey.setPos(at);
		prey.setDeltaMovement(Vec3.ZERO);
		prey.resetFallDistance();
		placed = at;
	}

	private void set(Grip.Hold hold, @Nullable Entity prey) {
		int bits = Grip.encode(hold, prey == null ? -1 : prey.getId());
		if (dragon.getEntityData().get(DragonData.GRIP) != bits) dragon.getEntityData().set(DragonData.GRIP, bits);
	}
}
