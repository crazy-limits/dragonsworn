"""The standing (perched) pose: chest raised 30 degrees, propped on the wrists of the folded wings,
the neck curved like a swan's. The arms are straight (elbow at zero): each wing runs in one line from
the shoulder down and out to its wrist, and the folded hand lies flat on the ground behind it.

Every frame is solved, not keyed by hand: the four feet stay planted exactly on their marks while the
body breathes, rears (roar) or lunges (attack). The tail is not keyed: the game lays it on the real
ground (body/Tail.java).
"""
from fan import fingers
from walk import solve_limbs

PITCH = 30.0            # chest up
LIFT = 10.0             # body raised so the hips sit at ~35 px with the chest up
# planted feet (editor space, model y = 0 is the ground): hind ankles under the hips, wrists forward and
# out. A straight arm is long (~140 px from a shoulder ~80 px up), so it reaches the ground only splayed:
# the hand lies flat no closer in than ~118 px (~128 px when the bite lowers the chest).
FEET = {'lh': [-16.0, 3.0, 16.0], 'rh': [16.0, 3.0, 16.0], 'lf': [-132.0, 0.0, -58.0], 'rf': [132.0, 0.0, -58.0]}
STAND_ARM = [-20.0, 24.0, 27.0, 0.0]   # seed for the arm solve with the chest up
ELBOW = 0.0             # straight arms
FAN = 28                # the hand nearly shut, so the fingers lie in one bundle along the ground

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
			 neck_yaw=(0, 0, 0, 0), head_yaw=0.0, jaw=-1.5, wings=None, body_yaw=0.0):
		"""A standing pose. body_* are offsets from the stand; `wings` replaces the solved arms
		(e.g. spread for a roar) with a dict of wing bone rotations."""
		pose = dict(fingers(fan))
		pose['body'] = {'p': [0, LIFT + body_lift, body_z], 'r': [PITCH + body_pitch, body_yaw, 0]}
		targets = dict(FEET) if wings is None else {k: v for k, v in FEET.items() if k[1] == 'h'}
		solve_limbs(pose, targets, self.sol, arm_ref=STAND_ARM, shoulder_pitch=STAND_ARM[0], elbow=ELBOW)
		if wings is not None:
			pose.update(wings)
		pose.update(neck(swan or SWAN, neck_yaw))
		pose['head_group'] = {'r': [HEAD if head is None else head, head_yaw, 0]}
		pose['jaw_group'] = {'r': [jaw, 0, 0]}
		return pose
