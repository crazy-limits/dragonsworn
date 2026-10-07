"""Builds the texture of the rune ward round some of the End's crystals (CrystalWard): three rings of 16 flat panes,
one rune of the Standard Galactic Alphabet (the enchanting table's) on each pane.

    python3 tools/crystal_ward.py [preview.png]       (build_assets.py runs it too)

Reads the SGA glyphs (particle/sga_a..z.png) from a Minecraft client jar in Loom's cache (run a Gradle build first).

Layout (must match CrystalWard: RINGS, PANES, PANE_HEIGHT, DENSITY, TEXTURE_WIDTH/HEIGHT): one row per ring, innermost
first, ROW texels tall (PANE_HEIGHT x DENSITY); pane i of a ring is the cell at x = i x its cell width (the pane's
width x DENSITY, so a texel is square on every ring). Each ring reads its phrase left to right as seen from outside,
one letter per pane (a space: a pane without a rune). Each pane: a dark, see-through violet plate, bright rails
along its long edges, dimmer ones at its ends (so the panes read as panes), the rune twice the font's size in the
middle with a glow round it. Drawn fullbright and translucent.

Licence: the runes are drawn from Minecraft's Standard Galactic Alphabet glyphs, which are Mojang's, under the
Minecraft EULA. The script is LGPL and ships none of Mojang's files; its output is not LGPL: see LICENSE-ASSETS.md.
"""
import math
import os
import sys
import zipfile

from PIL import Image

from crystal_beam import client_jar, glyph_masks

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'dragonsworn', 'textures',
                   'entity', 'crystal_ward.png')

# CrystalWard's numbers
RADII = (1.4, 1.65, 1.9)
PANES, STEP = 16, 22.5
PANE_HEIGHT, DENSITY = 0.5625, 32
W, H = 384, 64
ROW = round(PANE_HEIGHT * DENSITY)
# one letter per pane, innermost ring first
TEXT = ('none shall pass ', 'return to sender', 'the void endures')
# the font's glyphs drawn this many texels per font texel
SCALE = 2

PLATE = (34, 8, 52, 120)
RAIL = (196, 120, 255, 230)
END = (120, 64, 190, 170)
GLOW = (170, 90, 255, 150)
RUNE = (246, 228, 255, 255)


def cell_width(radius):
	return round(2 * radius * math.tan(math.radians(STEP / 2)) * DENSITY)


def pane(img, x0, y0, w, mask):
	px = img.load()
	for y in range(ROW):
		for x in range(w):
			edge_y = y in (0, ROW - 1)
			edge_x = x in (0, w - 1)
			px[x0 + x, y0 + y] = RAIL if edge_y else END if edge_x else PLATE
	if not mask:
		return
	gx0, gx1 = min(x for x, _ in mask), max(x for x, _ in mask)
	gy0, gy1 = min(y for _, y in mask), max(y for _, y in mask)
	gw, gh = (gx1 - gx0 + 1) * SCALE, (gy1 - gy0 + 1) * SCALE
	ox, oy = (w - gw) // 2, (ROW - gh) // 2
	lit = {(ox + (gx - gx0) * SCALE + i, oy + (gy - gy0) * SCALE + j)
	       for gx, gy in mask for i in range(SCALE) for j in range(SCALE)}
	glow = {(x + dx, y + dy) for x, y in lit for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - lit
	for x, y in glow:
		if 0 < x < w - 1 and 0 < y < ROW - 1:
			px[x0 + x, y0 + y] = GLOW
	for x, y in lit:
		if 0 <= x < w and 0 <= y < ROW:
			px[x0 + x, y0 + y] = RUNE


def main():
	with zipfile.ZipFile(client_jar()) as jar:
		masks = glyph_masks(jar)
	assert len(RADII) * ROW <= H
	img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
	for ring, (radius, text) in enumerate(zip(RADII, TEXT)):
		w = cell_width(radius)
		assert len(text) == PANES and PANES * w <= W, (text, w)
		for i, c in enumerate(text):
			pane(img, i * w, ring * ROW, w, masks.get(c))
	out = sys.argv[1] if len(sys.argv) > 1 else DST
	os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
	img.save(out)
	print('  ->', os.path.relpath(out, os.path.dirname(HERE)))


if __name__ == '__main__':
	main()
