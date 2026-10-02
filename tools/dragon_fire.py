"""Builds the dragon fire block's textures: vanilla's soul fire, tinted from cyan to the void flame's violet.

    python3 tools/dragon_fire.py        (build_assets.py runs it too)

Reads soul_fire_0/1.png (+ .mcmeta, the frame order) from a Minecraft client jar in Loom's cache (run a
Gradle build first). Soul fire is cyan (red ~0) shading to a white core; the tint swaps its red and green
channels, dimmed a little: cyan becomes violet, the white core and pale edges pale lavender, so every
texel keeps its shading and the animation stays vanilla's.
"""
import glob
import io
import os
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.path.join(os.path.dirname(HERE), 'src', 'mc', 'shared', 'resources', 'assets', 'dragonfall', 'textures', 'block')
JARS = os.path.expanduser('~/.gradle/caches/fabric-loom/*/minecraft-client.jar')
# how much of soul fire's green becomes red (below 1 leans the hue from magenta to violet), and how much of
# its red becomes green (below 1 turns the pale, nearly white texels lavender instead of periwinkle)
RED, GREEN = 0.82, 0.75


def client_jar():
	jars = sorted(glob.glob(JARS))
	if not jars:
		raise SystemExit(f'no Minecraft client jar in Loom\'s cache ({JARS}): run a Gradle build first')
	return jars[0]


def tint(image):
	out = image.convert('RGBA')
	px = out.load()
	for y in range(out.height):
		for x in range(out.width):
			r, g, b, a = px[x, y]
			px[x, y] = (round(g * RED), round(r * GREEN), b, a)
	return out


def build():
	os.makedirs(DST, exist_ok=True)
	with zipfile.ZipFile(client_jar()) as jar:
		for k in (0, 1):
			src = f'assets/minecraft/textures/block/soul_fire_{k}.png'
			path = os.path.join(DST, f'dragon_fire_{k}.png')
			tint(Image.open(io.BytesIO(jar.read(src)))).save(path)
			with open(path + '.mcmeta', 'wb') as f:
				f.write(jar.read(src + '.mcmeta'))
			print('  ->', os.path.relpath(path, os.path.dirname(HERE)))


if __name__ == '__main__':
	build()
