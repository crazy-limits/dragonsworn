"""Builds the void flame particle sprites: puffs of purple fire that burn out into smoke clouds.

    python3 tools/particles.py        (build_assets.py runs it too)

Two sets, drawn here (no source image), one puff's whole life each; the game picks the frame from the
particle's age (VoidFlameParticle), whose quad keeps one size: the puff grows inside the sprite, from a
small ball in the middle to the whole sprite.
  void_breath_0..15  48 x 48, the stream breath's puffs: a ball of purple fire, white-hot inside, that a
                     ring of smoke closes in on; from about 60 % of its life it is only smoke, billowing
                     clouds that thin out.
  void_flame_0..11   24 x 24, the same, smaller and quicker to smoke: the flames of the breath clouds (the
                     dragon fireball's, the perched breath's) and the embers in the mouth.

How a frame is made: a cloud of round billows (a heart, a ring, an outer ring, seeded per set) is drawn
at 4x, warped and turned a little by smooth noise that drifts from frame to frame (so the lumps churn);
the fire is the part near the heart (its share shrinks between the set's smoke start and fire end), colored in
bands from a white heart to a violet rim, cooling with age; the smoke is shaded per billow, lit from the
upper left and darker underneath (as vanilla's big smoke), and texels of it touching the fire glow violet.
Each 4x4 block then becomes one texel of the nearest palette color: clean pixel art, no noise speckle.
"""
import math
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'dragonsworn', 'textures', 'particle')

SS = 4                   # supersampling
# fire, hottest to coolest; smoke, lit to shadowed; smoke lit by the fire beside it
FIRE = np.array([(255, 250, 255), (252, 214, 255), (240, 160, 255), (214, 100, 250), (168, 56, 228), (120, 32, 182)])
SMOKE = np.array([(156, 146, 168), (126, 116, 140), (98, 89, 114), (74, 66, 90), (54, 47, 68)])
GLOW = np.array([(170, 112, 196), (132, 80, 164)])

# name: (sprite size, frames, seed, smoke starts eating in, no fire left from, size at birth); shares of life
# and of the full size. The particle's quad keeps one size: the puff grows inside the sprite (as the root of
# its age, from its size at birth), so its texels stay the same size all its life.
SETS = {
	'void_breath': (48, 16, 7, 0.30, 0.60, 0.17),
	'void_flame': (24, 12, 23, 0.15, 0.55, 0.30),
}


def value_noise(rng, cells, size):
	"""Smooth, tiling value noise: a cells x cells lattice, smoothstep-interpolated up to size x size."""
	g = rng.random((cells, cells))
	t = np.arange(size) / size * cells
	i0 = np.floor(t).astype(int) % cells
	i1 = (i0 + 1) % cells
	f = t - np.floor(t)
	f = f * f * (3 - 2 * f)
	a = g[i0][:, i0] * (1 - f)[None, :] + g[i0][:, i1] * f[None, :]
	b = g[i1][:, i0] * (1 - f)[None, :] + g[i1][:, i1] * f[None, :]
	return a * (1 - f)[:, None] + b * f[:, None]


def fbm(rng, octaves, size):
	return sum(w * value_noise(rng, cells, size) for cells, w in octaves)


def billows(rng):
	"""(x, y, radius) of each billow, in shares of the puff's size."""
	out = [(0.0, 0.0, 0.20)]
	for count, dist, radius in ((4, 0.13, 0.15), (6, 0.25, 0.12)):
		phase = rng.uniform(0, 2 * math.pi)
		for i in range(count):
			a = phase + i * 2 * math.pi / count + rng.uniform(-0.3, 0.3)
			out.append((math.cos(a) * dist * rng.uniform(0.85, 1.15), math.sin(a) * dist * rng.uniform(0.85, 1.15),
						radius * rng.uniform(0.8, 1.2)))
	return out


def nearest(palette, color):
	return palette[np.argmin(((palette - color) ** 2).sum(1))]


def frames(N, count, seed, smoke_start, fire_end, birth):
	M = N * SS
	rng = np.random.default_rng(seed)
	c = (M - 1) / 2
	yy, xx = np.mgrid[0:M, 0:M].astype(float)
	blobs = billows(rng)
	smooth = ((3, 0.7), (6, 0.3))
	n1, n2, n3 = fbm(rng, smooth, M), fbm(rng, smooth, M), fbm(rng, ((4, 0.6), (8, 0.4)), M)
	grain = fbm(rng, ((N // 4, 0.6), (N // 2, 0.4)), M)          # the smoke's mottling, a few texels across
	out = []
	for f in range(count):
		k = f / (count - 1)
		s = M * 0.86 * (birth + (1 - birth) * math.sqrt(k))     # the puff's size: it billows out
		drift = int(f * M / 40)
		X = xx + (np.roll(n1, drift, 0) - 0.5) * 0.05 * s
		Y = yy + (np.roll(n2, -drift, 1) - 0.5) * 0.05 * s
		# the billow each point is deepest in, and how lit that billow is there
		lobe = np.full((M, M), -9.0)
		lit = np.zeros((M, M))
		for bx, by, br in blobs:
			spread = 1 + 0.25 * k
			cx, cy, r = c + bx * s * spread, c + by * s * spread, br * s * (1 + 0.15 * k)
			score = 1 - np.hypot(X - cx, Y - cy) / r
			better = score > lobe
			lobe = np.where(better, score, lobe)
			lit = np.where(better, 1 - np.hypot(X - (cx - 0.35 * r), Y - (cy - 0.4 * r)) / (1.2 * r), lit)
		thin = max(0.0, (k - fire_end) / (1 - fire_end))         # the smoke tearing into wisps
		inside = lobe + 0.08 * (np.roll(n3, drift, 1) - 0.5) - 0.22 * thin * np.roll(n3, -drift, 0) > 0
		heart = 1 - np.hypot(xx - c, yy - c) / (s * 0.45)
		share = 1.0 if k <= smoke_start else max(0.0, 1 - (k - smoke_start) / (fire_end - smoke_start))
		fire = inside & (heart + 0.45 * (np.roll(n3, drift, 0) - 0.5) > 1 - share * 1.25) & (share > 0)
		heat = np.clip(heart * 1.25 + 0.35 * (n2 - 0.5) - 0.7 * k - 0.05, 0, 1)
		light = np.clip(0.8 * lit + 0.08 * (n1 - 0.5) + 0.22 * (grain - 0.5) + 0.35 - 0.2 * (yy - c) / s - 0.3 * k, 0, 1)
		rgb = np.where(fire[..., None], FIRE[np.clip(((1 - heat) * len(FIRE)).astype(int), 0, len(FIRE) - 1)],
					   SMOKE[np.clip(((1 - light) * len(SMOKE)).astype(int), 0, len(SMOKE) - 1)]).astype(float)

		# down to N x N: a texel is drawn where the cloud covers half of it, fire where fire covers half of that
		cover = inside.reshape(N, SS, N, SS).mean((1, 3))
		fire_cover = fire.reshape(N, SS, N, SS).mean((1, 3))
		w = inside.astype(float).reshape(N, SS, N, SS)
		avg = (rgb.reshape(N, SS, N, SS, 3) * w[..., None]).sum((1, 3)) / np.maximum(w.sum((1, 3)), 1)[..., None]
		hot = (cover >= 0.5) & (fire_cover >= 0.5 * cover)
		smoke_alpha = round(235 - 120 * thin)
		img = np.zeros((N, N, 4), np.uint8)
		for y in range(N):
			for x in range(N):
				if cover[y, x] < 0.5:
					continue
				if hot[y, x]:
					img[y, x] = (*nearest(FIRE, avg[y, x]), 255)
					continue
				beside = hot[max(0, y - 1):y + 2, max(0, x - 1):x + 2].any()
				img[y, x] = (*(GLOW[0 if avg[y, x].sum() > 330 else 1] if beside else nearest(SMOKE, avg[y, x])), smoke_alpha)
		out.append(Image.fromarray(img))
	return out


def build():
	os.makedirs(DST, exist_ok=True)
	for name, (size, count, seed, smoke_start, fire_end, birth) in SETS.items():
		for f, img in enumerate(frames(size, count, seed, smoke_start, fire_end, birth)):
			path = os.path.join(DST, f'{name}_{f}.png')
			img.save(path)
			print('  ->', os.path.relpath(path, os.path.dirname(HERE)))


if __name__ == '__main__':
	build()
