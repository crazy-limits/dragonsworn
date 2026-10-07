package crazylimits.dragonsworn.ai;

import crazylimits.dragonsworn.config.DragonConfig;

/**
 * What a landed dragon does next against its target. One blow at a time: the bite, the tail strike and
 * the wing buffet share one recovery ({@code attackReady}), so the head and the tail never strike together.
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
 * reach everywhere (under the chin, close beside the flanks, close behind the hips). Those blind spots get
 * the <b>wing buffet</b> (it rears and beats both wings once, throwing everything round its body away),
 * and when that is not ready either, the turn. Further off: the <b>roar</b> now and then at a target out of
 * reach (only with nobody close: it slows whoever runs off or shoots from afar), else walk in.
 *
 * <p>Mobbed ({@code mobbed}: {@code mob_buffet} players within the buffet's reach, {@link Crowd}) it
 * buffets first: the wings throw them all off at once, where a bite or the tail takes one.
 *
 * <p>Sat up on a narrow foothold ({@link Foothold#narrow}) it fights with the head alone: it bites what
 * is in reach and turns to face the rest, but never lashes its tail, roars or walks off its perch.
 */
public final class GroundTactics {
	public enum Action { NONE, BITE, TAIL_STRIKE, WING_BUFFET, ROAR }

	public enum Zone { FRONT, SIDE, BEHIND }

	/** {@code walk}: step forward this tick; {@code turn}: turn toward the target this tick. */
	public record Decision(Action action, boolean walk, boolean turn) {
		static final Decision HOLD = new Decision(Action.NONE, false, false);
	}

	/** |bearing| up to this is in front of the jaws; up to {@link #SIDE_ARC} to the side; beyond, behind. */
	public static final double FRONT_ARC = 40.0, SIDE_ARC = 125.0;
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

	/**
	 * As below, from a foothold: a narrow one only bites and turns. {@code mobbed}: players all round it,
	 * enough of them within the buffet's reach ({@code mob_buffet}), get the buffet before any other blow.
	 */
	public static Decision decide(Foothold foothold, double distance, double bearing, boolean biteReaches, boolean tailReaches,
			boolean buffetReaches, boolean mobbed, boolean attackReady, boolean roarReady, boolean provoked, double dice) {
		if (!foothold.narrow()) {
			if (mobbed && attackReady) return new Decision(Action.WING_BUFFET, false, false);
			return decide(distance, bearing, biteReaches, tailReaches, buffetReaches, attackReady, roarReady, provoked, dice);
		}
		Decision d = decide(distance, bearing, biteReaches, false, false, attackReady, false, provoked, dice);
		return d.walk() ? new Decision(d.action(), false, d.turn()) : d;
	}

	/**
	 * @param biteReaches    the jaws can be put on the target ({@code Strike.solve} reaches)
	 * @param tailReaches    the tail's tip can
	 * @param buffetReaches  it is within the wing buffet's reach round the body
	 * @param attackReady    recovered from the last blow
	 * @param roarReady      the roar has cooled down and nobody is close ({@code roar_quiet_range})
	 * @param provoked       the target hurt the dragon just now
	 * @param dice           uniform 0..1, for the side choice
	 */
	public static Decision decide(double distance, double bearing, boolean biteReaches, boolean tailReaches, boolean buffetReaches,
			boolean attackReady, boolean roarReady, boolean provoked, double dice) {
		Zone zone = zone(bearing);
		if (attackReady) {
			Action blow = switch (zone) {
				case FRONT -> biteReaches ? Action.BITE : Action.NONE;
				case SIDE -> {
					boolean bite = nearerHead(distance, bearing) && dice < DragonConfig.SIDE_BITE_CHANCE.get();
					if (bite) yield biteReaches ? Action.BITE : tailReaches ? Action.TAIL_STRIKE : Action.NONE;
					yield tailReaches ? Action.TAIL_STRIKE : biteReaches ? Action.BITE : Action.NONE;
				}
				case BEHIND -> provoked && tailReaches ? Action.TAIL_STRIKE : Action.NONE;
			};
			// a blind spot of the jaws and the tail, close to the body: the wings throw it off
			if (blow == Action.NONE && buffetReaches) blow = Action.WING_BUFFET;
			if (blow != Action.NONE) return new Decision(blow, false, false);
		}
		// out of reach and nobody close: the roar slows it down (running away, shooting from afar)
		if (zone != Zone.BEHIND && roarReady && distance >= DragonConfig.ROAR_QUIET.get() && distance < DragonConfig.ROAR_RANGE.get()) {
			return new Decision(Action.ROAR, false, false);
		}
		if (zone == Zone.FRONT) {
			boolean walk = distance > CLOSE_IN && !biteReaches;
			return new Decision(Action.NONE, walk, Math.abs(bearing) > FACE_ARC);
		}
		// to the side or behind: turn on the spot to face it
		return new Decision(Action.NONE, false, true);
	}
}
