"""Builds the dragon's sounds from vanilla's, cut into single events the game times to the animations.

    python3 tools/sounds.py        (needs numpy and soundfile: pip3 install --user numpy soundfile)

Vanilla's dragon has two sounds it repeats: `growl` (a 3 s roar with a long rumbling tail, played every
10-20 s and as the ambient sound) and `wings` (two swings per file, played on vanilla's own flap timer,
not on the model's wingbeat). Sources are read from Loom's asset cache (any Minecraft version whose
index has them, newest first). Out, in src/mc/shared/resources/assets/dragonsworn/sounds/entity/ender_dragon:

* roar1-4   the roar itself: from the growl's onset, loud while the roar animation holds the jaw open
            (ROAR_LOUD), then faded out by the time it closes (anims.py ROAR_AT .. ROAR_CLOSE + 0.4 s).
* wing1-6   one swing each (the first of each file, cut where it is quietest before the second),
            slightly deeper: pitched down WING_PITCH and a low shelf brought up.
* step1-5   a heavy foot on hard ground: vanilla's stone step pitched far down, a ravager step for the
            weight, and a short low thump for the body of it.

And sounds.json, with the hearing distance of each (the game plays them at volume <= 1, so a roar can
fade: above 1, volume only stretches the distance).

Licence: the sounds it writes are cut from Minecraft's own, so they stay Mojang's, under the Minecraft EULA. The
script is LGPL and ships none of Mojang's files; its output is not LGPL: see LICENSE-ASSETS.md.
"""
import glob
import json
import os

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, 'src', 'mc', 'shared', 'resources', 'assets', 'dragonsworn')
DST = os.path.join(ASSETS, 'sounds', 'entity', 'ender_dragon')
CACHE = os.path.expanduser('~/.gradle/caches/fabric-loom/assets')
SR = 44100

# The roar plays at a pitch around 0.9 (DragonVoice.ROAR_PITCH): source seconds = game seconds * 0.9.
# The jaw is wide open for 1.5 s (ROAR_AT 0.6 .. ROAR_CLOSE 2.1) and shut 0.4 s later.
ROAR_LOUD = 1.5 * 0.9
ROAR_FADE = 0.4 * 0.9
WING_PITCH = 0.85        # ~2.8 semitones down: a bigger wing
WING_SHELF = (300.0, 4.0)  # +4 dB below ~300 Hz
STEP_PITCH = 0.5         # the stone step an octave down: a far heavier foot


def vanilla(name):
	for index in sorted(glob.glob(os.path.join(CACHE, 'indexes', '*.json')), reverse=True):
		objects = json.load(open(index))['objects']
		entry = objects.get('minecraft/sounds/' + name)
		if entry is None:
			continue
		h = entry['hash']
		path = os.path.join(CACHE, 'objects', h[:2], h)
		if os.path.exists(path):
			x, sr = sf.read(path, dtype='float64')
			if x.ndim > 1:
				x = x.mean(axis=1)
			return resample(x, sr / SR) if sr != SR else x
	raise SystemExit(f'{name} not in the Loom asset cache ({CACHE}): run a Gradle build first')


def resample(x, rate):
	"""Plays x at `rate` (0.85: slower and 0.85 the pitch)."""
	t = np.arange(0, len(x) - 1, rate)
	return np.interp(t, np.arange(len(x)), x)


def envelope_db(x, window=0.01):
	w = int(SR * window)
	frames = len(x) // w
	rms = np.sqrt(np.mean(x[:frames * w].reshape(frames, w) ** 2, axis=1))
	return 20 * np.log10(rms / rms.max() + 1e-9), w


def shelf(x, corner, gain_db):
	"""Low shelf: frequencies well below `corner` raised by gain_db, a smooth step around it."""
	spectrum = np.fft.rfft(x)
	f = np.fft.rfftfreq(len(x), 1 / SR)
	gain = 1 + (10 ** (gain_db / 20) - 1) / (1 + (f / corner) ** 2)
	return np.fft.irfft(spectrum * gain, len(x))


def lowpass(x, corner):
	spectrum = np.fft.rfft(x)
	f = np.fft.rfftfreq(len(x), 1 / SR)
	return np.fft.irfft(spectrum / np.sqrt(1 + (f / corner) ** 4), len(x))


def fade(x, fade_in, fade_out):
	x = x.copy()
	n_in, n_out = int(SR * fade_in), int(SR * fade_out)
	if n_in:
		x[:n_in] *= np.sin(np.linspace(0, np.pi / 2, n_in)) ** 2
	if n_out:
		x[-n_out:] *= np.cos(np.linspace(0, np.pi / 2, n_out)) ** 2
	return x


def normalize(x, peak_db=-1.0):
	return x * (10 ** (peak_db / 20) / np.abs(x).max())


def roar(i):
	x = vanilla(f'mob/enderdragon/growl{i}.ogg')
	db, w = envelope_db(x)
	start = max(0, (np.argmax(db > -30) - 2) * w)       # 20 ms before the onset
	x = x[start:start + int(SR * (ROAR_LOUD + ROAR_FADE))]
	return normalize(fade(x, 0.01, ROAR_FADE))


def wing(i):
	x = vanilla(f'mob/enderdragon/wings{i}.ogg')
	db, w = envelope_db(x)
	# the second swing starts past ~0.45 s: cut at the quietest point between the two
	lo, hi = int(0.26 / 0.01), int(0.46 / 0.01)
	cut = (lo + int(np.argmin(db[lo:hi]))) * w
	x = fade(x[:cut], 0.005, 0.12)
	x = shelf(resample(x, WING_PITCH), *WING_SHELF)
	return normalize(fade(x, 0.0, 0.03))


def step(i):
	stone = resample(vanilla(f'step/stone{i}.ogg'), STEP_PITCH)
	heavy = resample(vanilla(f'mob/ravager/step{i}.ogg'), 0.7)
	n = int(SR * 0.7)
	t = np.arange(n) / SR
	rng = np.random.default_rng(i)
	# a low thump: a sine falling 70 -> 38 Hz, gone in ~0.25 s, struck by a short dull click
	freq = 38 + 32 * np.exp(-t / 0.06)
	thump = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-t / 0.09)
	click = lowpass(rng.standard_normal(n), 900) * np.exp(-t / 0.012)
	mix = 1.0 * thump + 0.25 * click / np.abs(click).max()
	for layer, gain in ((stone, 0.9), (heavy, 0.35)):
		layer = layer[:n] / np.abs(layer).max()
		mix[:len(layer)] += gain * layer
	return normalize(fade(shelf(mix, 120, 3.0), 0.002, 0.15))


# event: (variants, hearing distance in blocks, subtitle)
EVENTS = {
	'roar': ([roar(i) for i in range(1, 5)], 96, 'Ender Dragon roars'),
	'wing': ([wing(i) for i in range(1, 7)], 64, 'Ender Dragon flaps'),
	'step': ([step(i) for i in range(1, 6)], 40, 'Ender Dragon steps'),
}

# event: (vanilla event it plays, subtitle). The attacks keep vanilla's sounds but get events (and so
# subtitles) of their own: a deaf player reads what the dragon does, never "Ravager bites".
ALIASES = {
	'bite': ('entity.ravager.attack', 'Ender Dragon bites'),
	'tail': ('entity.player.attack.sweep', 'Ender Dragon lashes its tail'),
	'snatch': ('entity.ravager.attack', 'Ender Dragon snatches'),
	'chew': ('entity.ravager.attack', 'Ender Dragon chews'),
	'fling': ('entity.player.attack.sweep', 'Ender Dragon flings its prey'),
	'breath': ('entity.ender_dragon.shoot', 'Ender Dragon breathes fire'),
	'flames': ('entity.blaze.shoot', 'Void flames roar'),
	'buffet': ('entity.ender_dragon.flap', 'Ender Dragon buffets with its wings'),
}
# events of the End crystals' (entity.end_crystal.<name>), each pointing at vanilla's: (vanilla event, subtitle)
CRYSTAL_ALIASES = {
	'ward': ('entity.breeze.deflect', 'Crystal ward deflects'),
}


def build():
	os.makedirs(DST, exist_ok=True)
	for old in glob.glob(os.path.join(DST, '*.ogg')):
		os.remove(old)
	sounds = {}
	for event, (clips, distance, subtitle) in EVENTS.items():
		names = []
		for k, clip in enumerate(clips, 1):
			path = os.path.join(DST, f'{event}{k}.ogg')
			sf.write(path, clip.astype(np.float32), SR, format='OGG', subtype='VORBIS')
			names.append({'name': f'dragonsworn:entity/ender_dragon/{event}{k}', 'attenuation_distance': distance})
			print(f'  {os.path.relpath(path, ROOT)}  {len(clip) / SR:.2f}s')
		sounds[f'entity.ender_dragon.{event}'] = {'subtitle': f'subtitles.dragonsworn.entity.ender_dragon.{event}', 'sounds': names}
	for event, (vanilla, _) in ALIASES.items():
		sounds[f'entity.ender_dragon.{event}'] = {'subtitle': f'subtitles.dragonsworn.entity.ender_dragon.{event}',
			'sounds': [{'name': f'minecraft:{vanilla}', 'type': 'event'}]}
	for event, (vanilla, _) in CRYSTAL_ALIASES.items():
		sounds[f'entity.end_crystal.{event}'] = {'subtitle': f'subtitles.dragonsworn.entity.end_crystal.{event}',
			'sounds': [{'name': f'minecraft:{vanilla}', 'type': 'event'}]}
	with open(os.path.join(ASSETS, 'sounds.json'), 'w') as f:
		json.dump(sounds, f, indent='\t')
		f.write('\n')
	lang = os.path.join(ASSETS, 'lang', 'en_us.json')
	entries = json.load(open(lang)) if os.path.exists(lang) else {}
	subtitles = {event: subtitle for event, (_, _, subtitle) in EVENTS.items()}
	subtitles.update({event: subtitle for event, (_, subtitle) in ALIASES.items()})
	for event, subtitle in subtitles.items():
		entries[f'subtitles.dragonsworn.entity.ender_dragon.{event}'] = subtitle
	for event, (_, subtitle) in CRYSTAL_ALIASES.items():
		entries[f'subtitles.dragonsworn.entity.end_crystal.{event}'] = subtitle
	os.makedirs(os.path.dirname(lang), exist_ok=True)
	with open(lang, 'w') as f:
		json.dump(entries, f, indent='\t')
		f.write('\n')


if __name__ == '__main__':
	build()
