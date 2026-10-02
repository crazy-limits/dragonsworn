package crazylimits.dragonfall.ai;

import crazylimits.dragonfall.nav.Foothold;

/**
 * What a landed dragon does next against its target. One blow at a time: the bite and the tail strike
 * share one recovery ({@code attackReady}), so the head and the tail never strike together.
 *
 * <p>Where the target is decides, by its bearing (degrees from straight ahead, right positive):
 * <ul>
 *   <li><b>In front</b> ({@link #FRONT_ARC}): the <b>bite</b>, when the jaws can reach it.</li>
 *   <li><b>To the side</b> (up to {@link #SIDE_ARC}): nearer the tail, the <b>tail strike</b>; nearer the
 *       head, a roll of the dice between the two. Whichever was picked must reach, else the other is
 *       tried.</li>
 *   <li><b>Behind</b>: out of sight, so the dragon turns round to face it. But if it is being hit from
 *       there ({@code provoked}), it answers with the tail.</li>
 * </ul>
 * Whether a blow reaches is the IK's answer ({@code body/Strike}): the jaws and the tail's tip cannot
 * reach everywhere (under the chin, close behind the hips), and those blind spots are left to the turn.
 * Further off in front: the <b>roar</b> now and then, else walk in.
 *
 * <p>Sat up on a narrow foothold ({@link Foothold#narrow}) it fights with the head alone: it bites what
 * is in reach and turns to face the rest, but never lashes its tail, roars or walks off its perch.
 */
public final class GroundTactics {
	public enum Action { NONE, BITE, TAIL_STRIKE, ROAR }

	public enum Zone { FRONT, SIDE, BEHIND }

	/** {@code walk}: step forward this tick; {@code turn}: turn toward the target this tick. */
	public record Decision(Action action, boolean walk, boolean turn) {
		static final Decision HOLD = new Decision(Action.NONE, false, false);
	}

	/** |bearing| up to this is in front of the jaws; up to {@link #SIDE_ARC} to the side; beyond, behind. */
	public static final double FRONT_ARC = 40.0, SIDE_ARC = 125.0;
	public static final double ROAR_RANGE = 18.0;
	/** It walks in until the target is this close. */
	public static final double CLOSE_IN = 5.5;
	/** It turns only when the target is further off its nose than this. */
	public static final double FACE_ARC = 10.0;
	/** Which end the target is nearer, dragon-local blocks (forward positive): the jaws, the tail's middle. */
	static final double HEAD_FORWARD = 5.5, TAIL_FORWARD = -7.0;

	private GroundTactics() {}

	public static Zone zone(double bearing) {
		double off = Math.abs(bearing);
		return off <= FRONT_ARC ? Zone.FRONT : off <= SIDE_ARC ? Zone.SIDE : Zone.BEHIND;
	}

	/** Whether a target {@code distance} blocks off at {@code bearing} is nearer the head than the tail. */
	public static boolean nearerHead(double distance, double bearing) {
		double b = Math.toRadians(bearing);
		double right = Math.sin(b) * distance, forward = Math.cos(b) * distance;
		return Math.hypot(right, forward - HEAD_FORWARD) <= Math.hypot(right, forward - TAIL_FORWARD);
	}

	/** As below, from a foothold: a narrow one only bites and turns. */
	public static Decision decide(Foothold foothold, double distance, double bearing, boolean biteReaches, boolean tailReaches,
			boolean attackReady, boolean roarReady, boolean provoked, double dice) {
		if (!foothold.narrow()) return decide(distance, bearing, biteReaches, tailReaches, attackReady, roarReady, provoked, dice);
		Decision d = decide(distance, bearing, biteReaches, false, attackReady, false, provoked, dice);
		return d.walk() ? new Decision(d.action(), false, d.turn()) : d;
	}

	/**
	 * @param biteReaches  the jaws can be put on the target ({@code Strike.solve} reaches)
	 * @param tailReaches  the tail's tip can
	 * @param attackReady  recovered from the last blow
	 * @param provoked     the target hurt the dragon just now
	 * @param dice         uniform 0..1, for the side choice
	 */
	public static Decision decide(double distance, double bearing, boolean biteReaches, boolean tailReaches,
			boolean attackReady, boolean roarReady, boolean provoked, double dice) {
		Zone zone = zone(bearing);
		if (attackReady) {
			Action blow = switch (zone) {
				case FRONT -> biteReaches ? Action.BITE : Action.NONE;
				case SIDE -> {
					boolean bite = nearerHead(distance, bearing) && dice < 0.5;
					if (bite) yield biteReaches ? Action.BITE : tailReaches ? Action.TAIL_STRIKE : Action.NONE;
					yield tailReaches ? Action.TAIL_STRIKE : biteReaches ? Action.BITE : Action.NONE;
				}
				case BEHIND -> provoked && tailReaches ? Action.TAIL_STRIKE : Action.NONE;
			};
			if (blow != Action.NONE) return new Decision(blow, false, false);
		}
		if (zone == Zone.FRONT) {
			if (roarReady && distance > CLOSE_IN + 2.0 && distance < ROAR_RANGE) return new Decision(Action.ROAR, false, false);
			boolean walk = distance > CLOSE_IN && !biteReaches;
			return new Decision(Action.NONE, walk, Math.abs(bearing) > FACE_ARC);
		}
		// to the side or behind: turn on the spot to face it
		return new Decision(Action.NONE, false, true);
	}
}
