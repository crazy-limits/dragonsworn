"""Builds the dragon egg's block texture in the dragon's own scale palette.

    python3 tools/egg.py        (build_assets.py runs it too)

Replaces minecraft:block/dragon_egg (vanilla's random purple-speckled noise) with overlapping scales
shaded like the dragon's hide, cracked by a few glowing magenta veins like the runes on its belly.
16x16, the same texel density as the dragon (one texel per model pixel) and every other block.

Vanilla's egg model maps each layer's side to the texture row at its own height (uv v = model y), so
the texture is upside down: row 15 is the egg's tip. The art below is drawn tip-up and flipped.
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'minecraft', 'textures', 'block', 'dragon_egg.png')
SIZE = 16

# the dragon texture's palette (tools/out/ender_dragon.png), darkest to lightest, then its rune glow
HIDE = [(2, 0, 2), (8, 7, 14), (21, 17, 21), (32, 24, 32), (41, 33, 45), (49, 39, 53), (74, 63, 71), (90, 79, 87)]
GLOW = {'m': (166, 50, 167), 'M': (241, 56, 241), 'p': (224, 118, 224), 'w': (209, 172, 209)}

# scales: CELL_W x CELL_H, every other row shifted half a scale; each shows its lower lobe (the upper
# part tucks under the row above), lit from the upper right, rimmed dark where it overlaps its neighbors
CELL_W, CELL_H = 4, 3
LOBE = [
	[1, 5, 6, 4],
	[2, 4, 5, 3],
	[1, 2, 3, 1],
]
# some scales a shade darker, so the rows do not read as a grid
VARY = [0, 1, 0, 0, 1, 0, 1, 0, 0]
# rows at the egg's base sit in the shade
SHADE = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -2, -2]

# the veins, tip up: one crack winding down from the shoulder, forking over the belly ('.' = scales);
# bright where it is open, fading to the dim rune purple at its ends
VEINS = [
	'................',
	'................',
	'................',
	'.........m......',
	'........M.......',
	'........p.......',
	'.......M.m......',
	'.......w..m.....',
	'......Mp........',
	'.....M..........',
	'.....pM.........',
	'....m..wM.......',
	'...m.....m......',
	'................',
	'................',
	'................',
]


def build():
	out = Image.new('RGBA', (SIZE, SIZE))
	for y in range(SIZE):
		row = y // CELL_H
		for x in range(SIZE):
			shift = CELL_W // 2 if row % 2 else 0
			scale = (x + shift) // CELL_W
			shade = LOBE[y % CELL_H][(x + shift) % CELL_W] + SHADE[y] - VARY[(row * 3 + scale) % len(VARY)]
			color = GLOW.get(VEINS[y][x]) or HIDE[max(0, min(len(HIDE) - 1, shade))]
			out.putpixel((x, SIZE - 1 - y), (*color, 255))
	os.makedirs(os.path.dirname(DST), exist_ok=True)
	out.save(DST)
	print('  ->', os.path.relpath(DST, os.path.dirname(HERE)))


if __name__ == '__main__':
	build()
