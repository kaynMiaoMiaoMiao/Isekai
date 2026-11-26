#!/usr/bin/env python3
"""Generates the land bird's derived animations and sound cues. Pure standard library.

For every adult_N / baby_N animation file (each variant keeps its own other animations):

* adult.walk_back - the walk played backwards with a slightly shorter stride, leaning back with the head up.
* adult.air       - airborne body pose: legs tucked back, gentle bob (wings are left to the wing layer).
* adult.glide     - wings and flight feathers spread, slowly rippling.
* adult.flap      - the wing beat, now also opening the flight feathers on the downstroke.
* sound cues      - "step" at every footfall (found with forward kinematics) and "flap" on each downstroke.

Re-running regenerates the same output. Run tools/land_bird_wings.py first: the wing animations drive
its feather bones.

Usage:
    python3 tools/land_bird_animations.py
"""

import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import land_bird_preview as preview  # noqa: E402

ANIM_DIR = preview.ASSETS / "animations/entity/yoki_bird"
LEG_BONES = ("leg_left", "leg_right", "left_tibia", "right_tibia", "left_foot", "right_foot")
STEP = 0.025


# ------------------------------------------------------------------ json in the files' own style

def fmt(v, ind=0):
    """Matches the existing files: time-keyed channels expanded, keyframes and vectors on one line."""
    pad = "  " * ind
    if isinstance(v, dict):
        if not v:
            return "{}"
        if ind > 0 and all(not isinstance(x, dict) for x in v.values()) and not any(k[:1].isdigit() for k in v):
            return "{" + ", ".join(json.dumps(k, ensure_ascii=False) + ": " + fmt(x, ind + 1) for k, x in v.items()) + "}"
        inner = ",\n".join(pad + "  " + json.dumps(k, ensure_ascii=False) + ": " + fmt(x, ind + 1) for k, x in v.items())
        return "{\n" + inner + "\n" + pad + "}"
    if isinstance(v, list):
        if all(not isinstance(x, (dict, list)) for x in v):
            return "[" + ", ".join(json.dumps(x, ensure_ascii=False) for x in v) + "]"
        inner = ",\n".join(pad + "  " + fmt(x, ind + 1) for x in v)
        return "[\n" + inner + "\n" + pad + "]"
    return json.dumps(v, ensure_ascii=False)


def time_key(t):
    s = f"{t:.4f}".rstrip("0")
    return s + "0" if s.endswith(".") else s


def num(x):
    x = round(x, 3)
    return int(x) if x == int(x) else x


def key(vec):
    return {"post": [num(c) for c in vec], "lerp_mode": "catmullrom"}


def channel(length, fn, step=STEP):
    """Dense looping channel sampled from fn(t) -> [x, y, z]."""
    n = max(1, round(length / step))
    return {time_key(length * i / n): key(fn(length * i / n)) for i in range(n + 1)}


def wave(t, period, phase=0.0):
    return math.sin(2 * math.pi * (t / period + phase))


# ------------------------------------------------------------------ derived animations

def keyframe_vec(v):
    return preview.keyframe_value(v)


def walk_back(walk):
    """Walking backwards: the walk reversed in time, with an 85% stride, body leaning back, head up."""
    length = walk["animation_length"]
    bones = {}
    for bone, channels in walk["bones"].items():
        out = {}
        for name, frames in channels.items():
            items = sorted((float(t), keyframe_vec(v)) for t, v in frames.items())
            if name == "rotation" and bone in LEG_BONES:
                mean = [sum(v[i] for _, v in items) / len(items) for i in range(3)]
                items = [(t, [mean[0] + (v[0] - mean[0]) * 0.85, v[1], v[2]]) for t, v in items]
            if name == "rotation" and bone == "body":
                items = [(t, [v[0] - 4.0, v[1], v[2]]) for t, v in items]
            if name == "rotation" and bone == "h_head":
                items = [(t, [v[0] - 7.0, v[1], v[2]]) for t, v in items]
            reversed_frames = sorted(((length - t) % length if t < length else 0.0, v) for t, v in items)
            # the old t = 0 frame becomes t = length (loop closure)
            first = [v for t, v in reversed_frames if t == 0.0]
            frames_out = {time_key(t): key(v) for t, v in reversed_frames}
            if first:
                frames_out[time_key(length)] = key(first[-1])
            out[name] = dict(sorted(frames_out.items(), key=lambda kv: float(kv[0])))
        bones[bone] = out
    return {"loop": True, "animation_length": length, "bones": bones}


def air():
    """Airborne: legs folded back under the body, toes trailing, a slow bob; no wing keys."""
    length = 1.2
    return {"loop": True, "animation_length": length, "bones": {
        "body": {
            "rotation": channel(length, lambda t: [-3 + 1.2 * wave(t, length), 0, 0], 0.05),
            "position": channel(length, lambda t: [0, 0.5 * wave(t, length, 0.25), 0], 0.05),
        },
        "h_head": {"rotation": channel(length, lambda t: [-6 + 2 * wave(t, length, 0.1), 0, 0], 0.05)},
        "leg_left": {"rotation": channel(length, lambda t: [52 + 3 * wave(t, length), 0, 0], 0.05)},
        "leg_right": {"rotation": channel(length, lambda t: [52 + 3 * wave(t, length, 0.15), 0, 0], 0.05)},
        "left_tibia": {"rotation": channel(length, lambda t: [-48, 0, 0], 0.05)},
        "right_tibia": {"rotation": channel(length, lambda t: [-48, 0, 0], 0.05)},
        "left_foot": {"rotation": channel(length, lambda t: [55, 0, 0], 0.05)},
        "right_foot": {"rotation": channel(length, lambda t: [55, 0, 0], 0.05)},
    }}


def glide():
    """Wings held out with the flight feathers fanned, rippling slowly."""
    length = 2.0

    def wing(sign):
        return {"rotation": channel(length, lambda t: [-4 + 2 * wave(t, length, 0.3), 0,
                                                        sign * (-58 + 5 * wave(t, length))], 0.05)}

    def feathers(base, amp, phase):
        return {"rotation": channel(length, lambda t: [base + amp * wave(t, length, phase), 0, 0], 0.05)}

    return {"loop": True, "animation_length": length, "bones": {
        "left_wing": wing(1), "right_wing": wing(-1),
        "left_wing_primaries": feathers(-52, 5, -0.15), "right_wing_primaries": feathers(-52, 5, -0.15),
        "left_wing_secondaries": feathers(-12, 3, -0.2), "right_wing_secondaries": feathers(-12, 3, -0.2),
    }}


def flap(old):
    """Keeps the authored wing beat and opens the flight feathers on the downstroke."""
    length = old["animation_length"]
    primaries = {"0.0": key([-30, 0, 0]), "0.06": key([-58, 0, 0]), "0.15": key([-52, 0, 0]),
                 "0.28": key([-24, 0, 0]), time_key(length): key([-30, 0, 0])}
    secondaries = {"0.0": key([-8, 0, 0]), "0.15": key([-15, 0, 0]), time_key(length): key([-8, 0, 0])}
    bones = {name: old["bones"][name] for name in ("left_wing", "right_wing")}
    for side in ("left", "right"):
        bones[f"{side}_wing_primaries"] = {"rotation": dict(primaries)}
        bones[f"{side}_wing_secondaries"] = {"rotation": dict(secondaries)}
    # downstroke starts at t = 0 (wings up)
    return {"loop": True, "animation_length": length, "bones": bones, "sound_effects": {"0.02": {"effect": "flap"}}}


# ------------------------------------------------------------------ footfalls

def footfalls(model, animation, samples=240):
    """Times at which a foot comes back down to the ground, from the feet's lowest points."""
    length = animation["animation_length"]
    heights = {"left_foot": [], "right_foot": []}
    for i in range(samples):
        mats = model.bone_matrices(preview.sample_pose(animation, length * i / samples))
        for foot in heights:
            lowest = None
            for cube in model.bones[foot].get("cubes", []):
                m = mats[foot]
                if "rotation" in cube:
                    m = preview.mat_mul(m, preview.about(cube.get("pivot", [0, 0, 0]),
                                                         preview.bedrock_rotation(cube["rotation"])))
                for corners, _, _ in preview.cube_quads(m, cube):
                    lowest = min([lowest] + [p[1] for p in corners] if lowest is not None else [p[1] for p in corners])
            heights[foot].append(lowest)
    times = []
    for ys in heights.values():
        ground = min(ys)
        lift = max(ys) - ground
        if lift < 0.5:
            continue  # this foot never leaves the ground
        down, up = ground + 0.2 * lift, ground + 0.5 * lift
        # Walk one full cycle starting at the foot's highest point, where it is certainly in the air.
        start = ys.index(max(ys))
        lifted = True
        for k in range(samples):
            i = (start + k) % samples
            if lifted and ys[i] <= down:
                times.append(round(length * i / samples, 3))
                lifted = False
            elif ys[i] > up:
                lifted = True
    return sorted(times)


def foot_floor(model, animation, samples=120):
    """Lowest point any foot reaches over the cycle (model pixels)."""
    length = animation["animation_length"]
    low = None
    for i in range(samples):
        mats = model.bone_matrices(preview.sample_pose(animation, length * i / samples))
        for foot in ("left_foot", "right_foot"):
            for cube in model.bones[foot].get("cubes", []):
                m = mats[foot]
                if "rotation" in cube:
                    m = preview.mat_mul(m, preview.about(cube.get("pivot", [0, 0, 0]),
                                                         preview.bedrock_rotation(cube["rotation"])))
                for corners, _, _ in preview.cube_quads(m, cube):
                    for p in corners:
                        low = p[1] if low is None else min(low, p[1])
    return low


def settle(model, animation, floor):
    """Shifts the body so the feet reach the same floor as the reference gait."""
    shift = floor - foot_floor(model, animation)
    frames = animation["bones"]["body"]["position"]
    for t, v in frames.items():
        vec = preview.keyframe_value(v)
        frames[t] = key([vec[0], vec[1] + shift, vec[2]])
    return shift


def step_cues(times):
    return {time_key(t): {"effect": "step"} for t in times}


# ------------------------------------------------------------------ main

def main():
    for stage in ("adult", "baby"):
        for variant in range(1, 7):
            path = ANIM_DIR / f"{stage}_{variant}.animation.json"
            doc = json.loads(path.read_text())
            anims = doc["animations"]
            model = preview.Model(stage, variant)
            if stage == "adult":
                anims["adult.walk_back"] = walk_back(anims["adult.walk"])
                shift = settle(model, anims["adult.walk_back"], foot_floor(model, anims["adult.walk"]))
                anims["adult.air"] = air()
                anims["adult.glide"] = glide()
                anims["adult.flap"] = flap(anims["adult.flap"])
                gaits = ("adult.walk", "adult.sprint", "adult.walk_back")
            else:
                gaits = ("baby.walk", "baby.run")
            report = []
            for gait in gaits:
                anims[gait].pop("sound_effects", None)
                times = footfalls(model, anims[gait])
                anims[gait]["sound_effects"] = step_cues(times)
                report.append(f"{gait} {times}")
            path.write_text(fmt(doc) + "\n")
            if variant == 1:
                extra = f" (walk_back body raised {shift:+.2f}px)" if stage == "adult" else ""
                print(f"{stage}: " + "; ".join(report) + extra)


if __name__ == "__main__":
    main()
