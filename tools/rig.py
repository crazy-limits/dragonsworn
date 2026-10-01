"""Forward kinematics of the dragon rig, in Blockbench editor coordinates.

GeckoLib draws a model exactly as Blockbench's editor shows it, so this is the space every pose is
designed and checked in:

* the editor shows Bedrock geometry mirrored in X, so a file point (x, y, z) sits at (-x, y, z);
* a file rotation [rx, ry, rz] is the editor rotation [-rx, -ry, rz] (the animation exporter applies
  the same flip, see `anims.py`);
* a bone's rotation is Euler ZYX (three.js order 'ZYX', matrix Rz . Ry . Rx) about its pivot, and an
  animation adds its values to the rest rotation per axis.

Pure Python on purpose (no numpy): the tool chain must run anywhere `python3` does.
"""
import json
import math
import os

from chain import expand

HERE = os.path.dirname(os.path.abspath(__file__))


def mat_mul(a, b):
	return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
	return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rot_x(d):
	c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
	return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def rot_y(d):
	c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
	return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rot_z(d):
	c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
	return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def euler_zyx(r):
	return mat_mul(rot_z(r[2]), mat_mul(rot_y(r[1]), rot_x(r[0])))


def apply(m, p):
	return [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3)]


def to_editor(p):
	return [-p[0], p[1], p[2]]


class Rig:
	"""Bones of a Bedrock geometry file, converted to editor space."""

	def __init__(self, geo_path):
		g = json.load(open(geo_path))['minecraft:geometry'][0]
		self.bones = {}
		self.order = []
		for b in g['bones']:
			rest = b.get('rotation', [0, 0, 0])
			self.bones[b['name']] = {
				'parent': b.get('parent'),
				'pivot': to_editor(b['pivot']),
				'rest': [-rest[0], -rest[1], rest[2]],
				'cubes': [self._cube(c) for c in b.get('cubes', [])],
			}
			self.order.append(b['name'])

	@staticmethod
	def _cube(c):
		o, s = c['origin'], c['size']
		inf = c.get('inflate', 0)
		xs = sorted([-(o[0] - inf), -(o[0] + s[0] + inf)])
		return {'min': [xs[0], o[1] - inf, o[2] - inf], 'max': [xs[1], o[1] + s[1] + inf, o[2] + s[2] + inf],
				'rotation': c.get('rotation'), 'pivot': to_editor(c['pivot']) if 'pivot' in c else None}

	def matrices(self, pose):
		"""World matrix of every bone for `pose` = {bone: {'r': [x, y, z], 'p': [x, y, z]}} (editor values)."""
		pose = expand(pose)
		out = {}
		for name in self.order:
			b = self.bones[name]
			a = pose.get(name, {})
			r = [b['rest'][i] + a.get('r', [0, 0, 0])[i] for i in range(3)]
			p = a.get('p', [0, 0, 0])
			px, py, pz = b['pivot']
			local = mat_mul(translate(p[0] + px, p[1] + py, p[2] + pz), mat_mul(euler_zyx(r), translate(-px, -py, -pz)))
			out[name] = mat_mul(out[b['parent']], local) if b['parent'] else local
		return out

	def point(self, pose, bone, p, mats=None):
		mats = mats or self.matrices(pose)
		return apply(mats[bone], p)

	def corners(self, mats, bone):
		"""World positions of every cube corner of `bone`."""
		pts = []
		for c in self.bones[bone]['cubes']:
			lo, hi = c['min'], c['max']
			for x in (lo[0], hi[0]):
				for y in (lo[1], hi[1]):
					for z in (lo[2], hi[2]):
						pts.append(apply(mats[bone], [x, y, z]))
		return pts

	def subtree(self, root):
		names = [root]
		for n in self.order:
			p = self.bones[n]['parent']
			if p in names and n not in names:
				names.append(n)
		return names


def default_rig():
	return Rig(os.path.join(HERE, 'out', 'ender_dragon.geo.json'))
