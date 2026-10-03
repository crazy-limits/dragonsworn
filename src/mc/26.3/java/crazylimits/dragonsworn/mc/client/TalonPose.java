package crazylimits.dragonsworn.mc.client;

import crazylimits.dragonsworn.body.Grip;
import crazylimits.dragonsworn.limb.Affine;
import crazylimits.dragonsworn.limb.BodyFrame;
import crazylimits.dragonsworn.limb.Joint;
import crazylimits.dragonsworn.limb.LimbIK;
import crazylimits.dragonsworn.math.Maths;
import crazylimits.dragonsworn.mc.DragonBrain;
import crazylimits.dragonsworn.mc.PreyHold;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** The hind legs in a snatch ({@link LimbAnimator}'s talons): reaching for the prey, then holding it. */
final class TalonPose {
	private TalonPose() {
	}

	/**
	 * The snatch ({@link Grip}): as the dragon dives at its prey both hind legs are thrown forward under the
	 * chest, toes spread, as an eagle's are ({@link Grip#REACH_ANKLE}); over the last {@link Grip#REACH_NEAR}
	 * blocks the right foot reaches out for the prey, and once it is caught holds it, toes curled round its
	 * chest, while the left goes back to the animation. Each leg is solved by {@link LimbIK} onto its aim and
	 * blended in over the animation's leg; easing out when the hold ends.
	 */
	static void apply(GeoBones.Model model, DragonBrain brain, LimbAnimator.State state, BodyFrame frame, float partialTick, double dt) {
		Grip.Hold hold = brain.prey.hold();
		Entity prey = brain.prey.prey();
		boolean reach = prey != null && hold == Grip.Hold.REACH, held = prey != null && hold == Grip.Hold.TALON;
		boolean[] on = {reach, reach || held};
		for (int s = 0; s < 2; s++) {
			state.talon[s] += ((on[s] ? 1.0 : 0.0) - state.talon[s]) * (1.0 - Math.pow(held && s == 1 ? 0.5 : 0.82, dt));
			if (!on[s] && state.talon[s] < 0.01) state.talon[s] = 0.0;
		}
		state.reachLift += ((reach ? Grip.REACH_SPREAD : 0.0) - state.reachLift) * (1.0 - Math.pow(0.6, dt));
		state.clutch += ((held ? 1.0 : 0.0) - state.clutch) * (1.0 - Math.pow(0.7, dt));
		if (!held && state.clutch < 0.01) state.clutch = 0.0;
		if (held && state.clutch > 0.99) state.clutch = 1.0;   // closed: the toes freeze on it (Toes)
		if (state.talon[0] == 0.0 && state.talon[1] == 0.0) return;
		if (held) {
			System.arraycopy(Grip.TALON_ANKLE, 0, state.talonAim[1], 0, 3);
		} else if (reach) {
			for (int s = 0; s < 2; s++) {
				state.talonAim[s][0] = (s == 0 ? -1.0 : 1.0) * Grip.REACH_ANKLE[0];
				state.talonAim[s][1] = Grip.REACH_ANKLE[1];
				state.talonAim[s][2] = Grip.REACH_ANKLE[2];
			}
			// close in, the right ankle goes out from there to where its sole comes down on the prey's back
			double[] pad = PreyHold.padOffset(brain.body.yaw(partialTick));
			double[] ankle = {Mth.lerp(partialTick, prey.xo, prey.getX()) - pad[0],
					Mth.lerp(partialTick, prey.yo, prey.getY()) + prey.getBbHeight() / 2.0 + prey.getBbWidth() / 2.0 - pad[1],
					Mth.lerp(partialTick, prey.zo, prey.getZ()) - pad[2]};
			double[] forward = frame.toWorld(state.talonAim[1], new double[3]);
			double gap = Math.sqrt(Mth.lengthSquared(forward[0] - ankle[0], forward[1] - ankle[1], forward[2] - ankle[2]));
			double k = Maths.smoothstep(Mth.clamp(1.0 - gap / Grip.REACH_NEAR, 0.0, 1.0));
			double[] out = frame.toModel(ankle, new double[3]);
			for (int a = 0; a < 3; a++) state.talonAim[1][a] += (out[a] - state.talonAim[1][a]) * k;
		}
		GeoBones.Bone bodyBone = GeoBones.bone(model, "body");
		if (bodyBone == null) return;
		double[] bodyM = GeoBones.matrix(bodyBone);
		for (int s = 0; s < 2; s++) {
			if (state.talon[s] == 0.0) continue;
			GeoBones.Bone[] bones = new GeoBones.Bone[3];
			for (int k = 0; k < 3; k++) {
				bones[k] = GeoBones.bone(model, LimbAnimator.LEGS[s][k]);
				if (bones[k] == null) return;
			}
			Joint[] animated = new Joint[3], leg = new Joint[3];
			for (int k = 0; k < 3; k++) {
				animated[k] = GeoBones.joint(bones[k]);
				leg[k] = GeoBones.joint(bones[k]);
			}
			LimbIK.solveLeg(bodyM, leg[0], leg[1], leg[2], state.talonAim[s]);
			leg[2].rot[0] += state.reachLift;
			if (s == 1 && state.clutch > 0.0) {
				// holding: the foot level in the world and turned across the prey, its toes to curl round it
				double[] level = levelFoot(Affine.mul(Affine.mul(bodyM, leg[0].local()), leg[1].local()), brain, partialTick);
				double c = Maths.smoothstep(state.clutch);
				for (int a = 0; a < 3; a++) leg[2].rot[a] += Mth.wrapDegrees(level[a] - leg[2].rot[a]) * c;
			}
			double w = Maths.smoothstep(state.talon[s]);
			for (int k = 0; k < 3; k++) {
				for (int a = 0; a < 3; a++) leg[k].rot[a] = animated[k].rot[a] + (leg[k].rot[a] - animated[k].rot[a]) * w;
				GeoBones.setRotation(bones[k], leg[k]);
			}
		}
	}

	/**
	 * The foot's rotation (degrees, Z Y X as a bone's) under a shin of model matrix {@code shin} that holds
	 * its sole level in the world (against the body's pitch and roll) with its toes turned {@link Grip#TALON_YAW}.
	 */
	private static double[] levelFoot(double[] shin, DragonBrain brain, float partialTick) {
		// the body's pitch and roll as a rotation of the model's axes (no yaw: then model and world axes agree)
		BodyFrame tilt = new BodyFrame().set(0.0, 0.0, 0.0, 0.0, brain.body.pitch(partialTick), brain.body.roll(partialTick));
		double[] o = tilt.toWorld(new double[3], new double[3]);
		double[] tiltM = Affine.identity();
		for (int c = 0; c < 3; c++) {
			double[] e = new double[3];
			e[c] = 16.0;
			tilt.toWorld(e, e);
			for (int r = 0; r < 3; r++) tiltM[r * 4 + c] = e[r] - o[r];
		}
		// wanted in the model: the tilt undone, then the turn; in the shin's frame
		double[] want = Affine.mul(transpose(tiltM), Affine.rotationZYX(0.0, Grip.TALON_YAW, 0.0));
		double[] m = Affine.mul(transpose(shin), want);
		double b = Math.asin(Math.max(-1.0, Math.min(1.0, -m[8])));
		return new double[]{Math.toDegrees(Math.atan2(m[9], m[10])), Math.toDegrees(b), Math.toDegrees(Math.atan2(m[4], m[0]))};
	}

	/** The rotation part of {@code m}, transposed (inverted). */
	private static double[] transpose(double[] m) {
		double[] t = Affine.identity();
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) t[r * 4 + c] = m[c * 4 + r];
		}
		return t;
	}
}
