"""Rebuilds the dragon's model, texture and animations and copies them into the mod.

    python3 tools/build_assets.py

Needs Pillow and numpy. Steps: build_wings.py (geometry + texture with the fan-wing slices), anims.py (all
animations, the walk solved by IK), heat.py (the breath's heat glow), pack_uv.py (the right wing wears the
left's membranes, every texture repacked into a small atlas), then copy into the resource roots:

* src/gecko4/resources  -- GeckoLib 4 (Minecraft 1.21.1): assets/<ns>/geo, assets/<ns>/animations
* src/gecko5/resources  -- GeckoLib 5 (1.21.2+): assets/<ns>/geckolib/models, .../geckolib/animations
* src/mc/shared/resources -- textures, identical for both
"""
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(HERE, 'out')
NS = 'dragonsworn'


def copy(src, *dst):
	path = os.path.join(ROOT, *dst)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	shutil.copyfile(os.path.join(OUT, src), path)
	print('  ->', os.path.relpath(path, ROOT))


subprocess.run([sys.executable, os.path.join(HERE, 'build_wings.py')], check=True, cwd=HERE)
subprocess.run([sys.executable, os.path.join(HERE, 'anims.py')], check=True, cwd=HERE)
subprocess.run([sys.executable, os.path.join(HERE, 'particles.py')], check=True, cwd=HERE)
subprocess.run([sys.executable, os.path.join(HERE, 'egg.py')], check=True, cwd=HERE)
subprocess.run([sys.executable, os.path.join(HERE, 'dragon_fire.py')], check=True, cwd=HERE)
subprocess.run([sys.executable, os.path.join(HERE, 'crystal_beam.py')], check=True, cwd=HERE)
subprocess.run([sys.executable, os.path.join(HERE, 'icon.py')], check=True, cwd=HERE)
shutil.copyfile(os.path.join(HERE, 'source', 'dragon_eyes.png'), os.path.join(OUT, 'ender_dragon_glowmask.png'))
subprocess.run([sys.executable, os.path.join(HERE, 'heat.py')], check=True, cwd=HERE)
subprocess.run([sys.executable, os.path.join(HERE, 'pack_uv.py')], check=True, cwd=HERE)

copy('ender_dragon.geo.json', 'src', 'gecko4', 'resources', 'assets', NS, 'geo', 'entity', 'ender_dragon.geo.json')
copy('ender_dragon.animation.json', 'src', 'gecko4', 'resources', 'assets', NS, 'animations', 'entity', 'ender_dragon.animation.json')
copy('ender_dragon.geo.json', 'src', 'gecko5', 'resources', 'assets', NS, 'geckolib', 'models', 'entity', 'ender_dragon.geo.json')
copy('ender_dragon.animation.json', 'src', 'gecko5', 'resources', 'assets', NS, 'geckolib', 'animations', 'entity', 'ender_dragon.animation.json')
copy('ender_dragon.png', 'src', 'mc', 'shared', 'resources', 'assets', NS, 'textures', 'entity', 'ender_dragon.png')
copy('ender_dragon_glowmask.png', 'src', 'mc', 'shared', 'resources', 'assets', NS, 'textures', 'entity', 'ender_dragon_glowmask.png')
for k in range(1, 9):   # heat.FRAMES
	copy(os.path.join('heat', f'ender_dragon_heat_{k}.png'), 'src', 'mc', 'shared', 'resources', 'assets', NS, 'textures', 'entity', 'heat', f'ender_dragon_heat_{k}.png')
