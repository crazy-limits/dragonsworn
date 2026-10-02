"""The standing (perched) pose: chest raised 30 degrees, propped on the wrists of the folded wings,
the neck curved like a swan's. All four limbs stand on the ground: each wing's upper arm reaches out
from the shoulder up to a high elbow, the forearm arches back down to the wrist, and the half spread
hand lies on the ground along its leading finger, out to the tip, its membrane fanned up from there to
the forearm: two great arches round the dragon, its biggest face to whoever stands in front of it.

Every frame is solved, not keyed by hand: the four feet stay planted exactly on their marks while the
body breathes, rears (roar) or lunges (attack). The tail is not keyed: the game lays it on the real
ground (body/Tail.java).
"""
from fan import fingers
from walk import solve_limbs

PITCH = 30.0            # chest up
LIFT = 10.0             # body raised so the hips sit at ~35 px with the chest up
# planted feet (editor space, model y = 0 is the ground): hind ankles under the hips, wrist claws forward
# and out to the side
FEET = {'lh': [-16.0, 3.0, 16.0], 'rh': [16.0, 3.0, 16.0], 'lf': [-100.0, 0.0, -60.0], 'rf': [100.0, 0.0, -60.0]}
# reference arm (shoulder x, y, z, elbow z, wrist twist) with the chest up: the elbow above the shoulder
STAND_ARM = [-71.1, -20.4, 2.2, 60.2, 6.9]
FAN = 12                # the hand half spread

# The swan neck: the base rises steeply out of the raised chest, the upper neck arches forward and
# the head looks down its nose. Values per segment (base to head), editor X (+ = front up).
SWAN = [22.0, 14.0, -30.0, -36.0]
HEAD = -12.0


def neck(segments, yaw=(0, 0, 0, 0)):
	return {f'neck_{i + 1}': {'r': [segments[i], yaw[i], 0]} for i in range(4)}


class Stand:
	"""Solves standing frames in order, each seeded by the last, so the motion stays continuous."""

	def __init__(self):
		self.sol = {}

	def pose(self, body_pitch=0.0, body_lift=0.0, body_z=0.0, fan=FAN, swan=None, head=None,
			 neck_yaw=(0, 0, 0, 0), head_yaw=0.0, jaw=-1.5, wings=None, body_yaw=0.0, feet=None):
		"""A standing pose. body_* are offsets from the stand; `wings` replaces the solved arms
		(e.g. spread for a roar) with a dict of wing bone rotations; `feet` moves feet off their marks
		(limb -> target, e.g. the hind feet of a landing, planted ahead and passed over by the body)."""
		pose = dict(fingers(fan))
		pose['body'] = {'p': [0, LIFT + body_lift, body_z], 'r': [PITCH + body_pitch, body_yaw, 0]}
		targets = dict(FEET, **(feet or {}))
		if wings is not None:
			targets = {k: v for k, v in targets.items() if k[1] == 'h'}
		solve_limbs(pose, targets, self.sol, arm_ref=STAND_ARM, shoulder_pitch=STAND_ARM[0], hand_laid=True)
		if wings is not None:
			pose.update(wings)
		pose.update(neck(swan or SWAN, neck_yaw))
		pose['head_group'] = {'r': [HEAD if head is None else head, head_yaw, 0]}
		pose['jaw_group'] = {'r': [jaw, 0, 0]}
		return pose
