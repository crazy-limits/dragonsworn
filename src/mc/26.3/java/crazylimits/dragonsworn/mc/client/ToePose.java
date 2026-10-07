package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.anim.DragonAnim;
import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.limb.Affine;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.limb.Joint;
import crazylimits.dragonsworn.limb.Toes;
import crazylimits.dragonsworn.math.Maths;
import crazylimits.dragonsworn.mc.DragonBrain;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.joml.Vector3f;
import java.util.function.DoublePredicate;

/** The hind toes ({@link Toes}), set last on the feet as drawn: straight on the ground, hanging, open, gripping. */
final class ToePose {
	/** Each hind foot's toes (see {@link Toes}): the three in front, then the back toe. */
	private static final String[][] TOES = {{"foot_left_toe1", "foot_left_toe2", "foot_left_toe3", "foot_left_back_toe"},
			{"foot_right_toe1", "foot_right_toe2", "foot_right_toe3", "foot_right_back_toe"}};
	/** A foot this far over the ground (pixels) is off it: its toes hang. */
	private static final double TOE_CONTACT = 4.0;
	/** The curls a toe is tried at for what stops it (see {@link #lowestClear}), degrees. */
	private static final double TOE_TOP = 50.0, TOE_STEP = 6.0;

	private ToePose() {
	}

	/** How far a point (world) is inside something, blocks: positive inside. */
	private interface Inside {
		double at(double[] world);
	}

	/**
	 * The toes ({@link Toes}), last, on the feet as drawn: planted they are straight, in the air they hang
	 * and stir, reaching for the ground to land or for prey they open wide, and the right foot's close round
	 * what it holds, each as far as the prey lets it, and stay so until it lets go.
	 */
	static void apply(GeoBones.Model model, EnderDragon dragon, DragonBrain brain, LimbAnimator.State state, BodyFrame frame, Footing footing, float partialTick, double dt) {
		Grip.Hold hold = brain.prey.hold();
		Entity prey = brain.prey.prey();
		boolean reach = prey != null && hold == Grip.Hold.REACH, held = prey != null && hold == Grip.Hold.TALON;
		boolean landing = brain.clock.anim() == DragonAnim.LAND && !brain.footing();
		state.landingOpen += ((landing ? 1.0 : 0.0) - state.landingOpen) * (1.0 - Math.pow(0.85, dt));
		GroundClearance ground = new GroundClearance(footing, frame);
		Inside inPrey = null;
		if (held) {
			double w = prey.getBbWidth() / 2.0 + 0.02, h = prey.getBbHeight() / 2.0 + 0.02;
			double mx = Mth.lerp(partialTick, prey.xo, prey.getX()), my = Mth.lerp(partialTick, prey.yo, prey.getY()) + prey.getBbHeight() / 2.0;
			double mz = Mth.lerp(partialTick, prey.zo, prey.getZ());
			double yaw = brain.prey.lyingYaw(prey, partialTick);
			double r = Math.toRadians(Double.isNaN(yaw) ? brain.body.yaw(partialTick) : yaw), fx = -Math.sin(r), fz = Math.cos(r);
			// it lies flat along its yaw: as long as it is tall, as thick as it is wide
			inPrey = p -> {
				double dx = p[0] - mx, dy = p[1] - my, dz = p[2] - mz;
				double along = dx * fx + dz * fz, across = dx * fz - dz * fx;
				return Math.min(Math.min(w - Math.abs(across), w - Math.abs(dy)), h - Math.abs(along));
			};
		}
		double time = dragon.tickCount + partialTick;
		double[] side = new double[Toes.COUNT - 1], stop = new double[Toes.COUNT];
		for (int s = 0; s < 2; s++) {
			GeoBones.Bone foot = GeoBones.bone(model, LimbAnimator.LEGS[s][2]);
			if (foot == null || foot.getParent() == null) return;
			GeoBones.Bone[] toes = new GeoBones.Bone[Toes.COUNT];
			for (int i = 0; i < Toes.COUNT; i++) {
				toes[i] = GeoBones.bone(model, TOES[s][i]);
				if (toes[i] == null) return;
			}
			double[] footM = GeoBones.matrix(foot);
			double over = -ground.of(foot, GeoBones.matrix(foot.getParent()));
			double contact = Double.isNaN(over) ? 0.0 : (1.0 - Maths.smoothstep(over * 16.0 / TOE_CONTACT)) * state.footing;
			double grip = s == 1 ? Maths.smoothstep(state.clutch) : 0.0;
			double open = Math.max(state.landingOpen, reach ? Maths.smoothstep(state.talon[s]) : 0.0) * (1.0 - grip);
			Toes t = state.toes[s];
			for (int i = 0; i < Toes.COUNT; i++) {
				// outward: a toe on the foot's +x side swings its tip to +x with a negative turn about Y
				if (i < Toes.BACK) side[i] = -Math.signum(toes[i].getPivotX() - foot.getPivotX());
				// where it meets the prey, while it closes (closed, it is frozen)
				stop[i] = inPrey != null && grip > 0.0 && !t.frozen() ? lowestClear(toes[i], footM, t.spread(i), frame, inPrey) : Double.NEGATIVE_INFINITY;
			}
			t.update(dt, time, contact, open, grip, side, stop);
			// from the rest pose, not on top of the bone's last turn: no animation keys the toes, and GeckoLib
			// does not reset an unkeyed bone every frame, so adding would wind them round and round
			for (int i = 0; i < Toes.COUNT; i++) {
				var rest = toes[i].getInitialSnapshot();
				toes[i].setRotX(rest.getRotX() + (float) Math.toRadians(t.curl(i)));
				toes[i].setRotY(rest.getRotY() + (float) Math.toRadians(t.spread(i)));
				toes[i].setRotZ(rest.getRotZ());
			}
		}
	}

	/**
	 * The lowest curl (degrees, as {@link Toes}) at which no corner of {@code toe} (its rest pose turned by
	 * the curl and {@code spread}, on a foot of model matrix {@code footM}) is inside anything; {@code -Infinity}
	 * when it curls all the way ({@link Toes#GRIP_CURL}) clear, {@link #TOE_TOP} when even that is inside.
	 */
	private static double lowestClear(GeoBones.Bone toe, double[] footM, double spread, BodyFrame frame, Inside inside) {
		Joint rest = GeoBones.joint(toe);
		var snapshot = toe.getInitialSnapshot();
		rest.rot[0] = Math.toDegrees(snapshot.getRotX());
		rest.rot[1] = Math.toDegrees(snapshot.getRotY());
		rest.rot[2] = Math.toDegrees(snapshot.getRotZ());
		DoublePredicate clear = c -> {
			double[] m = Affine.mul(footM, rest.local(rest.rot[0] + c, rest.rot[1] + spread, rest.rot[2]));
			double[] p = new double[3];
			for (GeoBones.Cube cube : toe.getCubes()) {
				double[] cm = Affine.mul(m, GeoBones.cubeMatrix(cube));
				for (Vector3f v : cube.vertices()) {
					p[0] = v.x * 16.0;
					p[1] = v.y * 16.0;
					p[2] = v.z * 16.0;
					if (inside.at(frame.toWorld(Affine.apply(cm, p, p), p)) > 0.0) return false;
				}
			}
			return true;
		};
		if (!clear.test(TOE_TOP)) return TOE_TOP;
		double hi = TOE_TOP;
		for (double c = TOE_TOP - TOE_STEP; c >= Toes.GRIP_CURL; c -= TOE_STEP) {
			if (!clear.test(c)) {
				double lo = c;
				for (int k = 0; k < 6; k++) {
					double mid = (hi + lo) / 2.0;
					if (clear.test(mid)) hi = mid;
					else lo = mid;
				}
				return hi;
			}
			hi = c;
		}
		return Double.NEGATIVE_INFINITY;
	}
}
