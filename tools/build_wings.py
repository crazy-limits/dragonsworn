# Builds out/ender_dragon.geo.json + out/ender_dragon.png with accordion ("fan") wings:
# each wing-tip membrane becomes 6 wedge slices, each its own bone hinged on a finger,
# textured with a wedge cut from the original membrane art (transparent outside the wedge).
# Only the left wing's slices are drawn: pack_uv.py gives the right wing the same art, mirrored.
#
# Input: source/dragon_raw.geo.json (Blockbench's conversion of the pack's OptiFine CEM model) and
# source/dragon.png (the pack's texture). Run `python3 tools/build_assets.py` rather than this
# directly; it also writes the animations and copies everything into the mod's resources.
import json, math, os
import numpy as np
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
BETA = 15.0                # every slice is exactly 15deg wide: two slices per 30deg finger gap
TOL = 0.7072               # include every texel that touches the crease line (half a texel diagonal), so the pixel staircases of the two slices leave no pinholes
# Slice length along the finger and width = ceil(R * tan 15deg): the hand's gaps, then the sail's (long enough for all its art).
SIZES = [(124, 34)] * 6 + [(130, 35)] * 2
# Texture slots of the left slices (the right wears the same art, mirrored: pack_uv.py); pack_uv.py repacks them all.
SLOTS = [(198, 0), (650, 0), (236, 40), (650, 40), (244, 80), (650, 80), (40, 900), (40, 940)]
# Rest angles 0, 30, 60, 90 around the common apex; tip7 (120, no cube) is the sail's crease where the fan meets it.
FINGERS = ['wing_tip2', 'wing_tip3', 'wing_tip5', 'wing_tip6', 'wing_tip7']
# Common apex of the hand fan (= pivot of tip3/tip5/tip6, which the rest rotations turn about).
# Only the left wing is built; the right one is its exact mirror (see below).
APEX = by['left_wing_tip3']['pivot']
tip6 = by['left_wing_tip6']
by['left_wing_tip7'] = {'name': 'left_wing_tip7', 'parent': tip6['name'], 'pivot': list(APEX), 'rotation': list(tip6['rotation'])}
g['bones'].append(by['left_wing_tip7'])
# Original membrane box-UV up face, inverted: texture coordinate of a point at polar (r, phi) from the apex.
# membrane: origin (146.76604, -42.64279), uv [0,0] -> u = 126 + (x - 146.766), v = 126 - (z + 42.643)
def src_uv(r, phi):
    ax, _, az = APEX
    x, z = ax + r * math.cos(phi), az + r * math.sin(phi)
    return 126 + (x - 146.76604), 126 - (z + 42.64279)
sail_texels = []           # (u, v, slice bone, local point): the sail's slices are drawn once the rig exists
for side in ('left',):
    sgn = 1                                # the left fan opens toward +z
    ax, ay, az = APEX
    for j in range(8):
        gap = j // 2
        R, WD = SIZES[j]
        k = gap if j % 2 == 0 else gap + 1   # hinge finger
        finger = by[f'{side}_{FINGERS[k]}']
        toward = 1 if j % 2 == 0 else -1     # even: from finger toward the crease at +15deg; odd: back from the next finger
        zdir = sgn * toward
        zmin = az if zdir > 0 else az - WD
        zmax = zmin + WD
        ux, uy = SLOTS[j]
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
                if gap == 3:
                    sail_texels.append((ux + c, uy + r, f'{side}_wing_web{j}', (ax + xl, ay, az + zl)))
                    continue
                psi = math.degrees(math.atan2(a, xl)) * toward
                phi = math.radians(30 * k + psi)
                rad = math.hypot(xl, zl)
                su, sv = src_uv(rad, phi)
                si, sj = int(math.floor(su)), int(math.floor(sv))
                if 0 <= si < src.width and 0 <= sj < src.height:
                    px_ = sp[si, sj]
                    if px_[3] > 0: op[ux + c, uy + r] = px_
        g['bones'].append({
            'name': f'{side}_wing_web{j}', 'parent': finger['name'],
            'pivot': [ax, ay, az],
            'cubes': [{'origin': [ax, ay, round(zmin, 5)], 'size': [R, 0, WD], 'uv': [ux - WD, uy]}],
        })

# Root web: the inner membrane folds down at FOLD_X (just outside the body's flank, x = 18) into a strip
# ROOT_H deep, tilted ROOT_TILT degrees in under the body (its own texture: the membrane's art, mirrored
# across the fold, so the web runs on round the bend). The membrane's part inside the body is cut off. The
# renderer turns the strip about the fold so it keeps pointing where it points at rest (body/WingRoot),
# which closes the gap a lifted, swept or twisted shoulder opens there.
FOLD_X, ROOT_H, ROOT_TILT, ROOT_SLOT = 19, 24, 30, (320, 900)
inner = next(c for c in by['left_wing']['cubes'] if c['size'][1] == 0)
by['left_wing_root_web'] = {
    'name': 'left_wing_root_web', 'parent': 'left_wing', 'pivot': [FOLD_X, inner['origin'][1], by['left_wing']['pivot'][2]],
    'rotation': [0, 0, ROOT_TILT],     # editor +z swings the strip's lower edge toward the body
    'cubes': [{'origin': [FOLD_X, inner['origin'][1] - ROOT_H, inner['origin'][2]], 'size': [0, ROOT_H, inner['size'][2]], 'uv': list(ROOT_SLOT)}],
}
g['bones'].append(by['left_wing_root_web'])

# Toes: every toe (with the claw under its tip) is its own bone, hinged at its knuckle (KNUCKLE_Y up, just
# inside the pad's front edge), so the renderer can curl and spread them (mc/client/LimbAnimator: toes).
# Each hind foot also gets a back toe (hallux): its middle toe and claw again (same texture) on a bone
# turned 180 degrees about y at its knuckle just inside the pad's back edge, so it points back and sticks
# out BACK_TOE px behind the pad. Turned or not, +X on a toe bone lifts its tip.
BACK_TOE, KNUCKLE_Y, KNUCKLE_IN = 10, 3.5, 1
for side in ('left', 'right'):
    foot = by[f'foot_{side}']
    pad, toes, claws = foot['cubes'][0], foot['cubes'][1:4], foot['cubes'][4:7]
    pad_front, pad_back = pad['origin'][2], pad['origin'][2] + pad['size'][2]
    for i, toe in enumerate(toes, 1):
        claw = next(c for c in claws if c['origin'][0] == toe['origin'][0])
        g['bones'].append({
            'name': f'foot_{side}_toe{i}', 'parent': foot['name'],
            'pivot': [toe['origin'][0] + toe['size'][0] / 2, KNUCKLE_Y, pad_front + KNUCKLE_IN],
            'cubes': [toe, claw],
        })
    toe, claw = toes[1], next(c for c in claws if c['origin'][0] == toes[1]['origin'][0])
    knuckle = pad_back - KNUCKLE_IN
    shift = 2 * knuckle - (pad_back + BACK_TOE) - toe['origin'][2]   # turned about the knuckle, the tip lands BACK_TOE behind the pad
    back = [json.loads(json.dumps(c)) for c in (toe, claw)]
    for c in back:
        c['origin'][2] += shift
    g['bones'].append({
        'name': f'foot_{side}_back_toe', 'parent': foot['name'], 'rotation': [0, 180, 0],
        'pivot': [toe['origin'][0] + toe['size'][0] / 2, KNUCKLE_Y, knuckle],
        'cubes': back,
    })
    foot['cubes'] = [pad]

# The arm, forearm and fingers all share their top (and the fingers their bottom) height and overlap at the
# wrist, more so as the hand folds: raise each a little above the one before so their faces never z-fight.
for k, name in enumerate(['left_wing_tip1', 'left_wing_tip2', 'left_wing_tip3', 'left_wing_tip5', 'left_wing_tip6']):
    for c in by[name]['cubes']:
        c['origin'][1] += 0.1 * (k + 1)

# The right wing: every left_wing* bone mirrored across x = 0 (pivots and cubes at -x, rotations (x, -y, -z)).
# Its textures are the left's: pack_uv.py points each right face at the left face it mirrors.
def mirror_name(n):
    return 'right' + n[4:] if n.startswith('left_wing') else n
g['bones'] = [b for b in g['bones'] if not b['name'].startswith('right_wing')]
for b in list(g['bones']):
    if not b['name'].startswith('left_wing'):
        continue
    m = {'name': mirror_name(b['name']), 'parent': mirror_name(b['parent']), 'pivot': [-b['pivot'][0], *b['pivot'][1:]]}
    if 'rotation' in b:
        m['rotation'] = [b['rotation'][0], -b['rotation'][1], -b['rotation'][2]]
    m['cubes'] = [{**c, 'origin': [-(c['origin'][0] + c['size'][0]), *c['origin'][1:]]} for c in b.get('cubes', [])]
    for c in m['cubes']:
        assert 'rotation' not in c
    g['bones'].append(m)
by = {b['name']: b for b in g['bones']}

# four neck segments instead of two, nine tail segments instead of three (see chain.py)
from chain import split_chains
g['bones'] = split_chains(g['bones'])

for b in g['bones']:
    for c in b.get('cubes', []):
        c['origin'] = [round(v, 5) for v in c['origin']]; c['size'] = [round(v, 5) for v in c['size']]

# Every cube gets a name (Blockbench shows it): the bone's for a lone cube, else what it is.
PART_NAMES = {
    'body': ['body', 'body_fin'],
    'left_wing': ['left_wing_arm', 'left_wing_membrane'], 'right_wing': ['right_wing_arm', 'right_wing_membrane'],
    'left_wing_tip6': ['left_wing_tip6', 'left_wing_claw', 'left_wing_claw_tip'],
    'right_wing_tip6': ['right_wing_tip6', 'right_wing_claw', 'right_wing_claw_tip'],
    'jaw_group': ['jaw_group_1', 'jaw_group_2', 'jaw_group_3'], 'jaw_upper': ['jaw_upper_1', 'jaw_upper_2', 'jaw_upper_fin', 'jaw_upper_3'],
    'horn_left': ['horn_left', 'horn_left_tip'], 'horn_right': ['horn_right', 'horn_right_tip'],
}
for side in ('left', 'right'):
    PART_NAMES[f'foot_{side}_back_toe'] = [f'foot_{side}_back_toe', f'foot_{side}_back_claw']
    for i in (1, 2, 3):
        PART_NAMES[f'foot_{side}_toe{i}'] = [f'foot_{side}_toe{i}', f'foot_{side}_claw{i}']
for b in g['bones']:
    cubes = b.get('cubes', [])
    names = PART_NAMES.get(b['name'])
    for i, c in enumerate(cubes):
        if names:
            c['name'] = names[i]
        elif len(cubes) == 1:
            c['name'] = b['name']
        elif c['size'][0] == 0:
            c['name'] = f"{b['name']}_fin"       # the spine fins along the neck and tail
        else:
            c['name'] = b['name']
    assert len({c['name'] for c in cubes}) == len(cubes), b['name']
by['left_wing_tip']['cubes'][0]['name'] = 'left_wing_sail'
by['right_wing_tip']['cubes'][0]['name'] = 'right_wing_sail'
GEO = os.path.join(OUT, 'ender_dragon.geo.json')
json.dump(geo, open(GEO, 'w'))

# The sail's slices and the root webs are cut from art on other bones: map each texel through the rest pose.
from rig import Rig, apply
from pack_uv import quads, face_params, corners
mats = Rig(GEO).matrices({})
bones = {b['name']: b for b in g['bones']}

def face(bone, cube, f):
    """(world corners, uv corners) of a cube's face, at rest."""
    w = np.array([apply(mats[bone], p) for p in quads(cube)[f]])
    return w, np.array(corners(face_params(cube)[f]), float)

def up_face(bone, cube):
    return face(bone, cube, 'up')

def to_uv(face, p):
    """Texture point of world point p on the face (its plane, at rest)."""
    w, uv = face
    m = np.linalg.lstsq(np.c_[w[[0, 1, 3]][:, [0, 2]], np.ones(3)], uv[[0, 1, 3]], rcond=None)[0]
    return np.array([p[0], p[2], 1.0]) @ m

def to_world(face, uv_point):
    w, uv = face
    m = np.linalg.lstsq(np.c_[uv[[0, 1, 3]], np.ones(3)], w[[0, 1, 3]], rcond=None)[0]
    return np.array([uv_point[0], uv_point[1], 1.0]) @ m

def copy_texel(dst, face, p):
    su, sv = np.floor(to_uv(face, p)).astype(int)
    px_ = sp[int(su), int(sv)]
    if px_[3] > 0: op[dst] = px_

sail = up_face('left_wing_tip', bones['left_wing_tip']['cubes'][0])
for u, v, bone, (x, y, z) in sail_texels:
    copy_texel((u, v), sail, apply(mats[bone], (-x, y, z)))
# erase the folded wedge from the sail: every texel wholly past the tip7 crease, toward the hand
apex = np.array(apply(mats['left_wing_tip7'], [-APEX[0], APEX[1], APEX[2]]))
along = np.array(apply(mats['left_wing_tip7'], [-(APEX[0] + 1), APEX[1], APEX[2]])) - apex
tip6 = np.array(apply(mats['left_wing_tip6'], [-(APEX[0] + 1), APEX[1], APEX[2]])) - apex
across = tip6 - along * (tip6 @ along)
across /= np.linalg.norm(across)
(u0, v0), (u1, v1) = np.floor(sail[1].min(0)).astype(int), np.ceil(sail[1].max(0)).astype(int)
left_over = 0
for v in range(v0, v1):
    for u in range(u0, u1):
        d = to_world(sail, (u + 0.5, v + 0.5)) - apex
        if d @ across > TOL and op[u, v][3] > 0:
            if np.linalg.norm(d) <= SIZES[6][0] - 0.5:
                op[u, v] = (0, 0, 0, 0)
            else:
                left_over += 1
assert left_over == 0, f'{left_over} sail texels beyond the sail slices'
# the root web: the membrane's art mirrored across the fold; then cut the membrane off at the fold
membrane = up_face('left_wing', inner)
root_cube = bones['left_wing_root_web']['cubes'][0]
w = np.array(quads(root_cube)['east'], float)            # the strip's own frame: untilted, hanging straight down
uv = np.array(corners(face_params(root_cube)['east']), float)
(u0, v0), (u1, v1) = np.floor(uv.min(0)).astype(int), np.ceil(uv.max(0)).astype(int)
for v in range(v0, v1):
    for u in range(u0, u1):
        p = to_world((w, uv), (u + 0.5, v + 0.5))
        depth = membrane[0][0][1] - p[1]
        copy_texel((u, v), membrane, (-(FOLD_X + depth), membrane[0][0][1], p[2]))
(u0, v0), (u1, v1) = np.floor(membrane[1].min(0)).astype(int), np.ceil(membrane[1].max(0)).astype(int)
for v in range(v0, v1):
    for u in range(u0, u1):
        if -to_world(membrane, (u + 0.5, v + 0.5))[0] < FOLD_X:
            op[u, v] = (0, 0, 0, 0)
# The source paints the inner membrane's underside too: two opaque faces in one plane z-fight. Like every
# other membrane it keeps only its top face (drawn from both sides).
down = np.array(corners(face_params(inner)['down']), float)
(u0, v0), (u1, v1) = np.floor(down.min(0)).astype(int), np.ceil(down.max(0)).astype(int)
for v in range(v0, v1):
    for u in range(u0, u1):
        op[u, v] = (0, 0, 0, 0)
out.save(os.path.join(OUT, 'ender_dragon.png'))
print(len(g['bones']), 'bones', sum(len(b.get('cubes', [])) for b in g['bones']), 'cubes')
