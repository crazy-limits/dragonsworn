"""Builds the End crystal beam's texture (overrides minecraft:entity/end_crystal/end_crystal_beam): twice
vanilla's resolution (32 x 512), its sparkle at that finer grain, and a double helix of runes in the
Standard Galactic Alphabet (the enchanting table's) winding along the beam.

    python3 tools/crystal_beam.py       (build_assets.py runs it too)

Reads the SGA glyphs (particle/sga_a..z.png, 5 x 7 each) and vanilla's beam (for its sparkle's shades)
from a Minecraft client jar in Loom's cache (run a Gradle build first).

How the game maps it (EnderDragonRenderer.renderCrystalBeams, also used by the crystals' own beams): an
8-sided tube of radius 0.75, u once round it (~4.6 blocks), v once every 32 blocks along it, repeating and
scrolling. So a texel is ~0.145 blocks round and 0.0625 along: the glyphs are drawn twice as tall as they
are in the font to keep their shape. Each strand puts SLOTS glyphs per turn round the tube, a step of
STEP texels along between them, so a turn climbs SLOTS x STEP texels; TURNS turns fill the tile, which
therefore repeats seamlessly. The two strands are half a turn apart along the beam.

Licence: the runes are drawn from Minecraft's Standard Galactic Alphabet glyphs, which are Mojang's, under the
Minecraft EULA. The script is LGPL and ships none of Mojang's files; its output is not LGPL: see
LICENSE-ASSETS.md.
"""
import glob
import io
import os
import random
import string
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'minecraft', 'textures',
                   'entity', 'end_crystal', 'end_crystal_beam.png')
JARS = os.path.expanduser('~/.gradle/caches/fabric-loom/*/minecraft-client.jar')

W, H = 32, 512
# glyph slots per turn (8 texels round each), texels along between neighbours, glyph height (font's x 2)
SLOTS, STEP, TALL = 4, 16, 2
TURNS = H // (SLOTS * STEP)
STRANDS = 2
# what the strands say, read glyph after glyph round the helix (a space leaves its slot empty)
TEXT = ('the end is never the end ', 'the dragon wakes from the void ')
# the share of open texels lit, how bright the sparkle is against the runes (pure white), and the seed (the
# texture is the same every build)
SPARKLE, DIM, SEED = 0.07, 0.72, 7


def client_jar():
	jars = sorted(glob.glob(JARS))
	if not jars:
		raise SystemExit(f'no Minecraft client jar in Loom\'s cache ({JARS}): run a Gradle build first')
	return jars[0]


def load(jar, name):
	return Image.open(io.BytesIO(jar.read(name))).convert('RGBA')


def glyph_masks(jar):
	"""Each letter's lit texels (x, y) in the font's 8 x 8 cell."""
	masks = {}
	for c in string.ascii_lowercase:
		g = load(jar, f'assets/minecraft/textures/particle/sga_{c}.png')
		px = g.load()
		masks[c] = [(x, y) for y in range(g.height) for x in range(g.width) if px[x, y][3] > 0]
	return masks


def shades(beam):
	"""The greys vanilla's sparkle uses."""
	return sorted({p[0] for p in beam.getdata() if p[3] > 0})


def main():
	with zipfile.ZipFile(client_jar()) as jar:
		masks = glyph_masks(jar)
		greys = shades(load(jar, 'assets/minecraft/textures/entity/end_crystal/end_crystal_beam.png'))

	rune = set()
	for s in range(STRANDS):
		text, base = TEXT[s], s * (SLOTS * STEP) // STRANDS
		for i in range(TURNS * SLOTS):
			c = text[i % len(text)]
			if c == ' ':
				continue
			x0, y0 = (i % SLOTS) * (W // SLOTS) + 1, base + i * STEP
			for gx, gy in masks[c]:
				for k in range(TALL):
					rune.add((x0 + gx, (y0 + gy * TALL + k) % H))

	# a texel round every rune (two along the beam, where texels are half as long) stays dark, so the runes
	# read against the sparkle
	halo = {((x + dx) % W, (y + dy) % H) for x, y in rune for dx in (-1, 0, 1) for dy in range(-2, 3)}
	out = Image.new('RGBA', (W, H), (255, 255, 255, 0))
	px = out.load()
	rnd = random.Random(SEED)
	for y in range(H):
		for x in range(W):
			if (x, y) in rune:
				px[x, y] = (255, 255, 255, 255)
			elif (x, y) not in halo and rnd.random() < SPARKLE:
				g = round(rnd.choice(greys) * DIM)
				px[x, y] = (g, g, g, 255)
	os.makedirs(os.path.dirname(DST), exist_ok=True)
	out.save(DST)
	print('  ->', os.path.relpath(DST, os.path.dirname(HERE)))


if __name__ == '__main__':
	main()
