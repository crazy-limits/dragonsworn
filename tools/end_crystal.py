"""Builds the End crystal's textures (overrides minecraft:entity/end_crystal/end_crystal and item/end_crystal):
its glass is amethyst, as the crystal's recipe now asks (amethyst shards for glass:
src/mc/<version>/resources/data/minecraft/recipe/end_crystal.json).

    python3 tools/end_crystal.py        (build_assets.py runs it too)

Reads vanilla's crystal and amethyst block textures from a Minecraft client jar in Loom's cache (run a Gradle
build first; 1.21+, where the entity texture is 128 x 64).

Each glass face is an amethyst frame: the amethyst block's own texels on a band along the edges and a facet
in each corner (a triangle cut off by a diagonal), the rim darkened, the facets' inner edge lit (light
catching the cut), the centre clear so the core shows. The crystal draws cutout (entityCutoutNoCull), so the
frame is opaque and the centre fully clear.
Entity: the glass cube's net (both shells share uv 0,0) is a 4 x 2 net of 16 x 16 faces in the texture's
top-left 64 x 32; each face is cut from the amethyst block. The core and the bedrock base are vanilla's.
Item: vanilla's glass outline (a square round the crystal) becomes the same frame, thinner; the crystal's own
texels stay on top.

Licence: the textures it writes are drawn from Minecraft's, so they stay Mojang's, under the Minecraft EULA.
The script is LGPL and ships none of Mojang's files; its output is not LGPL: see LICENSE-ASSETS.md.
"""
import glob
import io
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'minecraft', 'textures')
JARS = os.path.expanduser('~/.gradle/caches/fabric-loom/*/minecraft-client.jar')
# the glass net's faces (column, row) of 16 x 16 texels: top and bottom above, the four sides below
FACE = 16
FACES = [(1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (3, 1)]
# (edge band, corner facet) in texels: a texel is frame when it lies within band of an edge, or when its
# distances to the corner's two edges add up to at most facet
ENTITY_FRAME, ITEM_FRAME = (2, 5), (1, 3)
# how far the rim darkens towards amethyst's darkest shade, and the facets' inner edge lightens towards its
# lightest violet (its pink sparkle is left to the block's own texels)
RIM, LIT = 0.5, 0.45


def client_jar():
	"""A 1.21+ client jar (older ones have the 64 x 32 crystal texture)."""
	jars = [j for j in sorted(glob.glob(JARS)) if not os.path.basename(os.path.dirname(j)).startswith('1.20')]
	if not jars:
		raise SystemExit(f'no Minecraft 1.21+ client jar in Loom\'s cache ({JARS}): run a Gradle build first')
	return jars[-1]


def load(jar, name):
	return Image.open(io.BytesIO(jar.read(f'assets/minecraft/textures/{name}.png'))).convert('RGBA')


def luma(p):
	return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def mix(a, b, t):
	return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def frame(w, h, band, facet):
	"""Texel (x, y) of a w x h face -> 'rim', 'lit' or 'fill'; clear texels are left out."""
	def inside(x, y):
		if not (0 <= x < w and 0 <= y < h):
			return False
		dx, dy = min(x, w - 1 - x), min(y, h - 1 - y)
		return dx < band or dy < band or dx + dy <= facet
	kind = {}
	for y in range(h):
		for x in range(w):
			if not inside(x, y):
				continue
			if x in (0, w - 1) or y in (0, h - 1):
				kind[x, y] = 'rim'
			elif not all(inside(x + i, y + j) for i, j in ((1, 0), (-1, 0), (0, 1), (0, -1))):
				kind[x, y] = 'lit'
			else:
				kind[x, y] = 'fill'
	return kind


def amethyst_frame(amethyst, w, h, band, facet):
	"""A w x h amethyst frame, its texels cut from the block (wrapping)."""
	shades = sorted(set(amethyst.getdata()), key=luma)
	dark, light = shades[0], shades[-2]
	src = amethyst.load()
	out = Image.new('RGBA', (w, h))
	px = out.load()
	for (x, y), kind in frame(w, h, band, facet).items():
		p = src[x % amethyst.width, y % amethyst.height]
		px[x, y] = mix(p, dark, RIM) if kind == 'rim' else mix(p, light, LIT) if kind == 'lit' else p
	return out


def glassy(p):
	"""Vanilla's glass: opaque, pale, cyan to white (green over red; the crystal is violet and red)."""
	return p[3] and min(p[:3]) > 140 and p[1] >= p[0]


def entity(crystal, amethyst):
	out = crystal.copy()
	face = amethyst_frame(amethyst, FACE, FACE, *ENTITY_FRAME)
	for c, r in FACES:
		out.paste(face, (c * FACE, r * FACE))
	return out


def item(crystal, amethyst):
	out = crystal.copy()
	px = out.load()
	glass = [(x, y) for y in range(out.height) for x in range(out.width) if glassy(px[x, y])]
	x0, x1 = min(x for x, _ in glass), max(x for x, _ in glass)
	y0, y1 = min(y for _, y in glass), max(y for _, y in glass)
	for x, y in glass:
		px[x, y] = (0, 0, 0, 0)
	ring = amethyst_frame(amethyst, x1 - x0 + 1, y1 - y0 + 1, *ITEM_FRAME).load()
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			p = ring[x - x0, y - y0]
			if p[3] and not px[x, y][3]:
				px[x, y] = p
	return out


def save(img, *path):
	dst = os.path.join(TEX, *path)
	os.makedirs(os.path.dirname(dst), exist_ok=True)
	img.save(dst)
	print('  ->', os.path.relpath(dst, os.path.dirname(HERE)))


def main():
	with zipfile.ZipFile(client_jar()) as jar:
		amethyst = load(jar, 'block/amethyst_block')
		save(entity(load(jar, 'entity/end_crystal/end_crystal'), amethyst), 'entity', 'end_crystal', 'end_crystal.png')
		save(item(load(jar, 'item/end_crystal'), amethyst), 'item', 'end_crystal.png')


if __name__ == '__main__':
	main()
