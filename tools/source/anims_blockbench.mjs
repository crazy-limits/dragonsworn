// Generates the GeckoLib animations and pushes them into Blockbench through the MCP.
// Rotation conventions (verified visually in this rig):
//   +X pitches a bone's front (-Z end) up: neck/head up, tail end down, legs swing forward; -X opens the jaw.
//   left wing/tip: -Z raises;  right wing/tip: +Z raises.  Wingtip +Y (left) / -Y (right) folds it back.
import {execFileSync} from 'child_process';

const PI2 = Math.PI * 2;
const S = Math.sin, C = Math.cos;
const r2 = v => Math.round(v * 100) / 100;

// folded wing pose used on the ground
// arms raised in an arch, hands swept back and down, fingers closed like a fan (Ice and Fire style)
// Gap-free fold: only hinges that lie on shared membrane edges move (shoulder raise, elbow drape, both
// about Z), and the hand fan closes onto its trailing finger, which stays attached to the sail.
const FOLD = {lw: [0, 0, -70], lwt: [0, 0, 150], rw: [0, 0, 70], rwt: [0, 0, -150]};
const FAN_MAX = 30; // fingers sit 30deg apart; closing each by 30 stacks them completely
// fan > 0 closes the wing membrane like a fan, 0 = fully spread
// Accordion membrane (exact). The hand has 4 fingers radiating from one apex, 30deg apart at rest; each
// gap holds two 15deg wedge slices, one hinged on each finger. Tilting both slices of a gap by theta
// (mirrored) about their fingers moves their free edges to the horizontal angle atan(tan15 * cos theta)
// from each finger. Setting the gap to exactly twice that angle puts both free edges in the gap's
// bisector plane, where they are mirror images and therefore coincide from the apex to the tip:
//     gap(theta) = 2 * atan(tan 15deg * cos theta)   <=>   cos theta = tan(gap / 2) / tan 15deg
// Fingers and slices are always driven together from theta, so slices never cross or open a gap.
const D = Math.PI / 180, TAN15 = Math.tan(15 * D);
const gapFromPleat = th => 2 * Math.atan(TAN15 * Math.cos(th * D)) / D;
const pleatFromFan = fan => fan <= 0 ? 0 : Math.acos(Math.min(1, Math.tan((30 - fan) / 2 * D) / TAN15)) / D;
// th: pleat angle in degrees (0 = flat, ~80 = closed), either one number or [gap0, gap1, gap2]
const wingFan = (th, side = 'both') => {
	const t = Array.isArray(th) ? th : [th, th, th];
	const out = {}, fingersOf = ['tip3', 'tip5', 'tip6'];
	let total = 0;
	for (let g = 0; g < 3; g++) {
		const close = 30 - gapFromPleat(t[g]);       // how far this gap's outer finger rotates toward the inner one
		total += close;
		if (side !== 'right') {
			out[`left_wing_${fingersOf[g]}`] = {r: [0, -close, 0]};
			out[`left_wing_web${2 * g}`] = {r: [t[g], 0, 0]};
			out[`left_wing_web${2 * g + 1}`] = {r: [-t[g], 0, 0]};
		}
		if (side !== 'left') {
			out[`right_wing_${fingersOf[g]}`] = {r: [0, close, 0]};
			out[`right_wing_web${2 * g}`] = {r: [-t[g], 0, 0]};
			out[`right_wing_web${2 * g + 1}`] = {r: [t[g], 0, 0]};
		}
	}
	// The fan closes toward its trailing finger (tip6), which borders the sail and therefore must not move:
	// the leading finger swings back by the total closure and every later finger rotates back by its gap's
	// closure, so tip6's net rotation is exactly zero. All fingers turn about the same apex.
	if (side !== 'right') out.left_wing_tip2 = {r: [0, total, 0]};
	if (side !== 'left') out.right_wing_tip2 = {r: [0, -total, 0]};
	return out;
};
// legacy helper: close each finger gap by `fan` degrees (pleat follows exactly)
const fingers = fan => wingFan(pleatFromFan(fan));
const folded = (t, breathe = 0, fan = 24) => ({
	left_wing: {r: [0, 0, FOLD.lw[2] - breathe]}, left_wing_tip: {r: FOLD.lwt},
	right_wing: {r: [0, 0, FOLD.rw[2] + breathe]}, right_wing_tip: {r: FOLD.rwt},
	...fingers(fan),
});

const animations = {
	idle: {
		length: 4, loop: true,
		pose(t, L) {
			const w = PI2 * t / L;
			return {
				body: {p: [0, 0.6 * S(w), 0]},
				neck_rot1: {r: [3 * S(w), 0, 0]},
				neck_rot2: {r: [-2 * S(w + 0.6), 6 * S(w / 2 * 2), 0]},
				head_group: {r: [-3 * S(w + 1.2), 10 * S(w + 0.8), 0]},
				jaw_group: {r: [-1.5 - 1.5 * S(w * 2), 0, 0]},
				tail_rot1: {r: [-2 * S(w), 5 * S(w), 0]},
				tail_rot2: {r: [-2 * S(w - 0.6), 7 * S(w - 0.6), 0]},
				tail_rot3: {r: [-3 * S(w - 1.2), 10 * S(w - 1.2), 0]},
				...folded(t, 3 * S(w), 25 + 2 * S(w)),
			};
		}
	},
	walk: {
		length: 1.6, loop: true,
		pose(t, L) {
			const w = PI2 * t / L;
			const step = s => 28 * S(w + s);            // thigh swing
			const knee = s => -(18 + 18 * C(w + s));    // knee bends back while leg swings forward
			return {
				body: {p: [0, -1 + 1.2 * C(2 * w), 0], r: [0, 3 * S(w), 2.5 * S(w)]},
				upperleg_left: {r: [step(0), 0, 0]}, lowerleg_left: {r: [knee(0), 0, 0]}, foot_left: {r: [-(step(0) + knee(0)) * 0.8, 0, 0]},
				upperleg_right: {r: [step(Math.PI), 0, 0]}, lowerleg_right: {r: [knee(Math.PI), 0, 0]}, foot_right: {r: [-(step(Math.PI) + knee(Math.PI)) * 0.8, 0, 0]},
				neck_rot1: {r: [3 * C(2 * w + 0.5), -4 * S(w), 0]},
				neck_rot2: {r: [-2 * C(2 * w + 1), -3 * S(w + 0.4), 0]},
				head_group: {r: [-2 * C(2 * w + 1.5), 3 * S(w + 0.8), 0]},
				tail_rot1: {r: [0, -8 * S(w - 0.4), 0]},
				tail_rot2: {r: [2 * C(2 * w), -10 * S(w - 1.0), 0]},
				tail_rot3: {r: [3 * C(2 * w - 0.5), -14 * S(w - 1.6), 0]},
				...folded(t, 2 * C(2 * w), 20 + 2 * C(2 * w)),
			};
		}
	},
	fly: {
		// Heavy, Game of Thrones style wingbeat. Bending happens only on hinges that lie exactly on a shared
		// membrane edge (shoulder and elbow, both rotating about the Z line the membranes meet on), so no seam
		// can open. The hand fan stays rigid in-plane; a pleat ripple travels across it from the leading finger
		// to the trailing one, and the fan folds a little on the upstroke and spreads straight on the downstroke.
		length: 1.8, loop: true, step: 0.05,
		pose(t, L) {
			const w = PI2 * t / L;
			const up = phase => (0.5 + 0.5 * C(w - phase)) ** 2;    // 0..1 bump peaking during the upstroke
			const shoulder = 8 + 34 * S(w);
			const elbow = 4 + 20 * S(w - 0.6);
			const pleats = [0, 1, 2].map(g => 3 + 14 * up(0.6 + 0.35 * g) + 2.5 * S(w - 1.4 - 0.7 * g));
			const side = (s, n) => ({
				[`${n}_wing`]: {r: [0, -s * 4 * C(w), s * shoulder]},
				[`${n}_wing_tip`]: {r: [0, 0, s * elbow]},
			});
			return {
				body: {p: [0, 6 - 5 * S(w - 0.25), 0], r: [3 * S(w - 1.0), 0, 0]},
				...side(-1, 'left'), ...side(1, 'right'),
				...wingFan(pleats),
				upperleg_left: {r: [-50 - 3 * S(w - 0.8), 0, 0]}, lowerleg_left: {r: [-20, 0, 0]}, foot_left: {r: [-50 - 4 * S(w - 1.2), 0, 0]},
				upperleg_right: {r: [-50 - 3 * S(w - 0.8), 0, 0]}, lowerleg_right: {r: [-20, 0, 0]}, foot_right: {r: [-50 - 4 * S(w - 1.2), 0, 0]},
				neck_rot1: {r: [3 + 2.5 * S(w - 1.6), 0, 0]},
				neck_rot2: {r: [2 * S(w - 2.1), 0, 0]},
				head_group: {r: [-5 - 4 * S(w - 1.3), 0, 0]},
				jaw_group: {r: [-1 - S(w), 0, 0]},
				tail_rot1: {r: [3 * S(w - 1.5), 0, 0]},
				tail_rot2: {r: [5 * S(w - 2.1), 0, 0]},
				tail_rot3: {r: [7 * S(w - 2.7), 0, 0]},
			};
		}
	},
	glide: {
		length: 3, loop: true,
		pose(t, L) {
			const w = PI2 * t / L;
			return {
				body: {p: [0, 6 + 1.5 * S(w), 0], r: [0, 0, 4 * S(w)]},
				left_wing: {r: [0, 0, -8 - 3 * S(w + 0.5)]}, left_wing_tip: {r: [0, 0, -4 - 3 * S(w)]},
				right_wing: {r: [0, 0, 8 + 3 * S(w + 0.5)]}, right_wing_tip: {r: [0, 0, 4 + 3 * S(w)]},
				...wingFan([0, 1, 2].map(g => 4 + 2 * S(w - 0.8 * g))),
				upperleg_left: {r: [-55, 0, 0]}, lowerleg_left: {r: [-20, 0, 0]}, foot_left: {r: [-50, 0, 0]},
				upperleg_right: {r: [-55, 0, 0]}, lowerleg_right: {r: [-20, 0, 0]}, foot_right: {r: [-50, 0, 0]},
				neck_rot1: {r: [3, 3 * S(w), 0]}, head_group: {r: [-5, 4 * S(w + 0.5), 0]},
				tail_rot1: {r: [0, -4 * S(w - 0.5), 0]}, tail_rot2: {r: [2, -6 * S(w - 1), 0]}, tail_rot3: {r: [3, -9 * S(w - 1.5), 0]},
			};
		}
	},
	roar: {
		length: 2.5, loop: false, step: 0.025,
		pose(t, L) {
			// 0-0.6 rear up, 0.6-1.9 roar with shake, 1.9-2.5 settle
			const e = x => x * x * (3 - 2 * x);
			const k = t < 0.6 ? e(t / 0.6) : t < 1.9 ? 1 : 1 - e((t - 1.9) / 0.6);
			const shake = t > 0.6 && t < 1.9 ? 2 * S(t * 40) : 0;
			const wing = 35 * k;
			return {
				body: {r: [8 * k, 0, 0], p: [0, 2 * k, 0]},
				neck_rot1: {r: [28 * k, shake, 0]},
				neck_rot2: {r: [12 * k, 0, 0]},
				head_group: {r: [14 * k + shake, shake, 0]},
				jaw_group: {r: [-38 * k, 0, 0]},
				tail_rot1: {r: [6 * k, 0, 0]}, tail_rot2: {r: [8 * k, 0, 0]}, tail_rot3: {r: [10 * k, shake * 2, 0]},
				left_wing: {r: [0, 0, FOLD.lw[2] * (1 - k) - wing * 0.4]}, left_wing_tip: {r: [0, FOLD.lwt[1] * (1 - k), FOLD.lwt[2] * (1 - k) - 10 * k]},
				right_wing: {r: [0, 0, FOLD.rw[2] * (1 - k) + wing * 0.4]}, right_wing_tip: {r: [0, FOLD.rwt[1] * (1 - k), FOLD.rwt[2] * (1 - k) + 10 * k]},
				...fingers(24 * (1 - k)),
			};
		}
	},
	attack: {
		length: 1.0, loop: false,
		pose(t) {
			// wind up (0-0.35), lunge + bite (0.35-0.55), recover
			const e = x => Math.max(0, Math.min(1, x)) ** 2 * (3 - 2 * Math.max(0, Math.min(1, x)));
			const wind = e(t / 0.35) * (1 - e((t - 0.35) / 0.12));
			const lunge = e((t - 0.35) / 0.15) * (1 - e((t - 0.6) / 0.4));
			const jaw = t < 0.45 ? 32 * e(t / 0.4) : 32 * (1 - e((t - 0.45) / 0.08));
			return {
				body: {r: [4 * wind - 5 * lunge, 0, 0], p: [0, 0, 6 * wind - 8 * lunge]},
				neck_rot1: {r: [18 * wind - 22 * lunge, 0, 0]},
				neck_rot2: {r: [10 * wind - 6 * lunge, 0, 0]},
				head_group: {r: [8 * wind + 10 * lunge, 0, 0]},
				jaw_group: {r: [-jaw, 0, 0]},
				...folded(t, 6 * lunge),
			};
		}
	},
	death: {
		length: 2.5, loop: 'hold',
		pose(t, L) {
			const e = x => { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); };
			const fall = e(t / 1.2), slump = e((t - 0.8) / 1.2), wings = e((t - 0.3) / 1.4);
			return {
				body: {p: [0, -26 * fall, 0], r: [-3 * fall, 0, 0]},
				upperleg_left: {r: [-70 * fall, 0, -10 * fall]}, lowerleg_left: {r: [40 * fall, 0, 0]}, foot_left: {r: [30 * fall, 0, 0]},
				upperleg_right: {r: [-70 * fall, 0, 10 * fall]}, lowerleg_right: {r: [40 * fall, 0, 0]}, foot_right: {r: [30 * fall, 0, 0]},
				neck_rot1: {r: [-14 * slump, 12 * slump, 0]},
				neck_rot2: {r: [-8 * slump, 10 * slump, 0]},
				head_group: {r: [6 * slump, 8 * slump, -15 * slump]},
				jaw_group: {r: [-14 * slump, 0, 0]},
				left_wing: {r: [0, 0, FOLD.lw[2] * (1 - wings) + 5 * wings]}, left_wing_tip: {r: [0, 0, FOLD.lwt[2] * (1 - wings) + 3 * wings]},
				right_wing: {r: [0, 0, FOLD.rw[2] * (1 - wings) - 5 * wings]}, right_wing_tip: {r: [0, 0, FOLD.rwt[2] * (1 - wings) - 4 * wings]},
				...fingers(24 * (1 - wings) + 6 * wings),
				tail_rot1: {r: [4 * slump, -8 * slump, 0]}, tail_rot2: {r: [4 * slump, -12 * slump, 0]}, tail_rot3: {r: [2 * slump, -16 * slump, 0]},
			};
		}
	},
};

const STEP = 0.05;
const call = (tool, args) => execFileSync('node', ['mcp.mjs', 'call', tool, JSON.stringify(args)], {maxBuffer: 1e8}).toString();
const only = process.argv.slice(2);

for (const [key, a] of Object.entries(animations)) {
	if (only.length && !only.includes(key)) continue;
	const bones = {};
	const step = a.step || STEP;
	const steps = Math.round(a.length / step);
	for (let i = 0; i <= steps; i++) {
		const t = r2(i * step);
		// looping animations reuse frame 0 at the end so the loop is seamless
		const pose = a.pose(a.loop === true && i === steps ? 0 : t, a.length);
		for (const [bone, v] of Object.entries(pose)) {
			const kf = {time: t};
			if (v.r) kf.rotation = v.r.map(r2);
			if (v.p) kf.position = v.p.map(r2);
			(bones[bone] ||= []).push(kf);
		}
	}
	const name = `ender_dragon_reborn.${key}`;
	const full = `animation.${name}`;
	const res = call('create_animation', {name, loop: a.loop === true, animation_length: a.length, bones});
	if (a.loop === 'hold') call('animation_timeline', {animation_id: full, action: 'loop', loop_mode: 'hold'});
	console.log(key, '->', res.split('\n')[0].slice(0, 140));
}
