# Builds out/ender_dragon.geo.json + out/ender_dragon.png with accordion ("fan") wings:
# each wing-tip membrane becomes 6 wedge slices, each its own bone hinged on a finger,
# textured with a wedge cut from the original membrane art (transparent outside the wedge).
#
# Input: source/dragon_raw.geo.json (Blockbench's conversion of the pack's OptiFine CEM model) and
# source/dragon.png (the pack's texture). Run `python3 tools/build_assets.py` rather than this
# directly; it also writes the animations and copies everything into the mod's resources.
import json, math, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'source', 'dragon.png')
OUT = os.path.join(HERE, 'out')
os.makedirs(OUT, exist_ok=True)
geo = json.load(open(os.path.join(HERE, 'source', 'dragon_raw.geo.json')))
g = geo['minecraft:geometry'][0]
g['description']['identifier'] = 'geometry.ender_dragon'
g['bones'] = [b for b in g['bones'] if b['name'] not in ('head', 'mirrored', 'jaw')]
g['bones'].insert(0, {'name': 'root', 'pivot': [0, 0, 0]})
parent = {'body': 'root', 'left_wing': 'body', 'right_wing': 'body', 'left_wing_tip': 'left_wing', 'right_wing_tip': 'right_wing'}
by = {b['name']: b for b in g['bones']}
for n, p in parent.items(): by[n]['parent'] = p

# The source rig has the right knee pivot 12px higher than the left one. Neither has a rest rotation,
# so moving the pivot changes no geometry, only where the knee bends: make both knees bend at y=25.
by['lowerleg_right']['pivot'][1] = by['lowerleg_left']['pivot'][1]

# lower wings onto the shoulders
DROP = 12
def in_wing(b):
    while b:
        if b['name'] in ('left_wing', 'right_wing'): return True
        b = by.get(b.get('parent'))
    return False
for b in g['bones']:
    if in_wing(b):
        b['pivot'][1] -= DROP
        for c in b.get('cubes', []): c['origin'][1] -= DROP

# the leading finger turns about the fan apex like the others (no rest rotation, so geometry is unchanged)
for side in ('left', 'right'):
    by[f'{side}_wing_tip2']['pivot'] = list(by[f'{side}_wing_tip3']['pivot'])

# remove the flat membranes on the hands
for side in ('left', 'right'):
    t2 = by[f'{side}_wing_tip2']
    t2['cubes'] = [c for c in t2['cubes'] if c['size'][1] != 0]

src = Image.open(SRC).convert('RGBA'); sp = src.load()
out = src.copy(); op = out.load()
R, WD = 124, 34            # slice length along the finger; width = ceil(R * tan 15deg)
SLOTS = [(198, 0), (650, 0), (236, 40), (650, 40), (244, 80), (650, 80), (644, 120), (246, 128), (694, 160), (236, 168), (612, 200), (202, 208)]
BETA = 15.0                # every slice is exactly 15deg wide: two slices per 30deg finger gap
TOL = 0.7072               # include every texel that touches the crease line (half a texel diagonal), so the pixel staircases of the two slices leave no pinholes
FINGERS = ['wing_tip2', 'wing_tip3', 'wing_tip5', 'wing_tip6']   # rest angles 0, 30, 60, 90 around the common apex
# Common apex of the hand fan (= pivot of tip3/tip5/tip6, which the rest rotations turn about).
APEX = {s: by[f'{s}_wing_tip3']['pivot'] for s in ('left', 'right')}
# Original membrane box-UV up faces, inverted: texture coordinate of a point at polar (r, phi) from the apex.
# left  membrane: origin (146.76604, -42.64279), uv [0,0]   -> u = 126 + (x - 146.766), v = 126 - (z + 42.643)
# right membrane: origin (21.35721, -150.23396), uv [0,126] -> u = 126 + (x - 21.357),  v = 126 + (-24.234 - z)
def src_uv(side, r, phi):
    ax, _, az = APEX[side]
    if side == 'left':
        x, z = ax + r * math.cos(phi), az + r * math.sin(phi)
        return 126 + (x - 146.76604), 126 - (z + 42.64279)
    x, z = ax + r * math.cos(phi), az - r * math.sin(phi)
    return 126 + (x - 21.35721), 126 + (-24.23396 - z)
slot = 0
for side in ('left', 'right'):
    sgn = 1 if side == 'left' else -1      # left fan opens toward +z, right toward -z
    ax, ay, az = APEX[side]
    for j in range(6):
        gap = j // 2
        k = gap if j % 2 == 0 else gap + 1   # hinge finger
        finger = by[f'{side}_{FINGERS[k]}']
        toward = 1 if j % 2 == 0 else -1     # even: from finger toward the crease at +15deg; odd: back from the next finger
        zdir = sgn * toward
        zmin = az if zdir > 0 else az - WD
        zmax = zmin + WD
        ux, uy = SLOTS[slot]; slot += 1
        cosb, sinb = math.cos(math.radians(BETA)), math.sin(math.radians(BETA))
        for r in range(WD):
            for c in range(R):
                xl = c + 0.5                             # along the finger from the apex
                zl = (zmax - r - 0.5) - az
                a = sgn * zl * toward                    # distance-ish across the finger toward the crease (>= 0 inside)
                if xl <= 0 or a < -0.5: continue
                # signed distance past the crease line (positive = outside the wedge)
                past = a * cosb - xl * sinb
                if past > TOL: continue
                psi = math.degrees(math.atan2(a, xl)) * toward
                phi = math.radians(30 * k + psi)
                rad = math.hypot(xl, zl)
                su, sv = src_uv(side, rad, phi)
                si, sj = int(math.floor(su)), int(math.floor(sv))
                if 0 <= si < src.width and 0 <= sj < src.height:
                    px_ = sp[si, sj]
                    if px_[3] > 0: op[ux + c, uy + r] = px_
        g['bones'].append({
            'name': f'{side}_wing_web{j}', 'parent': finger['name'],
            'pivot': [ax, ay, az],
            'cubes': [{'origin': [ax, ay, round(zmin, 5)], 'size': [R, 0, WD], 'uv': [ux - WD, uy]}],
        })
# four neck segments instead of two, nine tail segments instead of three (see chain.py)
from chain import split_chains
g['bones'] = split_chains(g['bones'])

for b in g['bones']:
    for c in b.get('cubes', []):
        c['origin'] = [round(v, 5) for v in c['origin']]; c['size'] = [round(v, 5) for v in c['size']]
json.dump(geo, open(os.path.join(OUT, 'ender_dragon.geo.json'), 'w'))
out.save(os.path.join(OUT, 'ender_dragon.png'))
print(len(g['bones']), 'bones', sum(len(b.get('cubes', [])) for b in g['bones']), 'cubes')
