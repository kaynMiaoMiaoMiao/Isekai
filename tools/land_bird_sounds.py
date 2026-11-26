#!/usr/bin/env python3
"""Procedurally synthesize the land bird's sound effects.

Pure standard library: every clip is built from oscillators, noise and simple
filters, then written as WAV and (if `oggenc` from vorbis-tools is installed)
encoded to the Ogg Vorbis files Minecraft loads.

Usage:
    python3 tools/land_bird_sounds.py            # writes into the mod's assets
    python3 tools/land_bird_sounds.py --wav-only  # keep WAVs, skip encoding
"""

import argparse
import math
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

SR = 44100
ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "src/main/resources/assets/isekai/sounds/entity/land_bird"


# ---------------------------------------------------------------- primitives

def silence(seconds):
    return [0.0] * int(seconds * SR)


def mix(base, other, offset=0.0, gain=1.0):
    start = int(offset * SR)
    need = start + len(other)
    if need > len(base):
        base.extend([0.0] * (need - len(base)))
    for i, v in enumerate(other):
        base[start + i] += v * gain
    return base


def envelope(n, attack, release, curve=2.0):
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    out = []
    for i in range(n):
        if i < a:
            g = i / a
        elif i > n - r:
            g = max(0.0, (n - i) / r) ** curve
        else:
            g = 1.0
        out.append(g)
    return out


def noise(n, rng):
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def bandpass(sig, freq, q):
    w0 = 2 * math.pi * freq / SR
    alpha = math.sin(w0) / (2 * q)
    a0 = 1 + alpha
    b0, b2 = alpha / a0, -alpha / a0
    a1, a2 = -2 * math.cos(w0) / a0, (1 - alpha) / a0
    x1 = x2 = y1 = y2 = 0.0
    out = []
    for x in sig:
        y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, x, y1, y
        out.append(y)
    return out


def lowpass(sig, cutoff):
    k = 1 - math.exp(-2 * math.pi * cutoff / SR)
    y = 0.0
    out = []
    for x in sig:
        y += k * (x - y)
        out.append(y)
    return out


def highpass(sig, cutoff):
    low = lowpass(sig, cutoff)
    return [x - l for x, l in zip(sig, low)]


def normalize(sig, peak):
    m = max((abs(v) for v in sig), default=0.0) or 1.0
    return [v * peak / m for v in sig]


def fade_edges(sig, seconds=0.004):
    n = min(len(sig) // 2, int(seconds * SR))
    for i in range(n):
        g = i / n
        sig[i] *= g
        sig[-1 - i] *= g
    return sig


# ------------------------------------------------------------------- voices

def voice(seconds, pitch, rng, *, formants=((900, 4, 1.0), (2300, 6, 0.55)),
          vibrato=(0.0, 0.0), rough=(0.0, 0.0), breath=0.08,
          attack=0.02, release=0.08):
    """A buzzy syrinx-like tone shaped by vocal formants.

    pitch: f(progress 0..1) -> Hz
    vibrato: (rate Hz, depth fraction)
    rough: (AM rate Hz, depth 0..1) for squawky, gravelly calls
    """
    n = int(seconds * SR)
    phase = 0.0
    src = []
    vib_rate, vib_depth = vibrato
    rough_rate, rough_depth = rough
    for i in range(n):
        t = i / SR
        p = i / n
        f = pitch(p) * (1 + vib_depth * math.sin(2 * math.pi * vib_rate * t))
        phase += 2 * math.pi * f / SR
        harmonics = max(1, min(14, int(6000 / f)))
        s = 0.0
        for k in range(1, harmonics + 1):
            s += math.sin(k * phase) / (k ** 1.15)
        if rough_depth:
            s *= 1 - rough_depth * (0.5 + 0.5 * math.sin(2 * math.pi * rough_rate * t
                                                         + rng.uniform(-0.4, 0.4)))
        src.append(s)
    src = [s + breath * rng.uniform(-1, 1) * 4 for s in src]
    out = [0.0] * n
    for freq, q, gain in formants:
        for i, v in enumerate(bandpass(src, freq, q)):
            out[i] += v * gain
    dry = 0.12
    out = [o + dry * s for o, s in zip(out, src)]
    env = envelope(n, attack, release)
    return [o * e for o, e in zip(out, env)]


def glide(start, end, shape=1.0):
    return lambda p: start + (end - start) * (p ** shape)


def expo(start, end):
    return lambda p: start * (end / start) ** p


def arch(low, high, low_end=None):
    low_end = low if low_end is None else low_end
    def f(p):
        if p < 0.4:
            return low + (high - low) * math.sin(p / 0.4 * math.pi / 2)
        return high + (low_end - high) * ((p - 0.4) / 0.6) ** 1.3
    return f


# ------------------------------------------------------------------- clips

def ambient1(rng):
    """Two-syllable 'kru-ruu' greeting."""
    out = silence(0.0)
    mix(out, voice(0.17, glide(520, 700), rng, release=0.05))
    mix(out, voice(0.38, arch(690, 760, 470), rng, vibrato=(17, 0.04)), 0.22)
    return out


def ambient2(rng):
    """Soft rolling trill that rises at the end."""
    return voice(0.62, glide(590, 770, 2.5), rng, vibrato=(24, 0.11),
                 rough=(24, 0.35), attack=0.04, release=0.12)


def ambient3(rng):
    """Curious rising chirp followed by a short blip."""
    out = voice(0.3, expo(470, 880), rng, release=0.04)
    mix(out, voice(0.09, glide(860, 780), rng, attack=0.008, release=0.04), 0.36)
    return out


def hurt1(rng):
    return voice(0.27, glide(980, 610, 0.6), rng, rough=(46, 0.55), breath=0.18,
                 formants=((1100, 3, 1.0), (2600, 5, 0.7)), attack=0.005)


def hurt2(rng):
    out = voice(0.17, glide(1080, 760, 0.5), rng, rough=(52, 0.5), breath=0.2,
                formants=((1150, 3, 1.0), (2700, 5, 0.7)), attack=0.004)
    mix(out, voice(0.11, glide(900, 700), rng, rough=(40, 0.5), breath=0.2,
                   attack=0.004), 0.2, 0.7)
    return out


def death(rng):
    out = voice(1.15, expo(820, 230), rng, vibrato=(9, 0.06), rough=(30, 0.45),
                breath=0.14, attack=0.01, release=0.45)
    tail = bandpass(noise(int(0.5 * SR), rng), 1400, 0.8)
    tail = [v * e for v, e in zip(tail, envelope(len(tail), 0.08, 0.4))]
    return mix(out, tail, 0.85, 0.25)


def hiss(seconds, rng):
    n = int(seconds * SR)
    sig = bandpass(noise(n, rng), 3200, 1.2)
    env = envelope(n, seconds * 0.35, seconds * 0.4, 1.5)
    return [v * e for v, e in zip(sig, env)]


def angry1(rng):
    out = hiss(0.62, rng)
    growl = voice(0.55, glide(150, 125), rng, rough=(38, 0.7), breath=0.25,
                  formants=((500, 3, 1.0), (1500, 4, 0.4)), attack=0.08)
    return mix(normalize(out, 0.6), normalize(growl, 0.5), 0.04)


def angry2(rng):
    out = voice(0.2, glide(720, 620), rng, rough=(60, 0.65), breath=0.25,
                formants=((1000, 2.5, 1.0), (2500, 4, 0.8)), attack=0.004)
    return mix(normalize(out, 0.7), normalize(hiss(0.42, rng), 0.5), 0.12)


def thump(seconds, f_start, f_end, rng):
    n = int(seconds * SR)
    phase = 0.0
    out = []
    for i in range(n):
        p = i / n
        phase += 2 * math.pi * (f_start + (f_end - f_start) * p) / SR
        out.append(math.sin(phase) * math.exp(-p * 6))
    return out


def peck(rng):
    click = highpass(noise(int(0.006 * SR), rng), 2500)
    click = [v * (1 - i / len(click)) for i, v in enumerate(click)]
    out = mix(thump(0.035, 260, 140, rng), click, 0.0, 0.9)
    crunch = bandpass(noise(int(0.05 * SR), rng), rng.uniform(1800, 2600), 1.5)
    crunch = [v * e for v, e in zip(crunch, envelope(len(crunch), 0.002, 0.045))]
    return mix(out, crunch, 0.008, 0.8)


def eat(rng, count):
    out = silence(0.0)
    t = 0.0
    for _ in range(count):
        mix(out, peck(rng), t, rng.uniform(0.75, 1.0))
        t += rng.uniform(0.09, 0.14)
    return out


def step(rng):
    body = thump(0.09, rng.uniform(95, 115), 60, rng)
    claw = highpass(noise(int(0.018 * SR), rng), 3500)
    claw = [v * (1 - i / len(claw)) ** 2 for i, v in enumerate(claw)]
    dirt = lowpass(bandpass(noise(int(0.11 * SR), rng), 700, 0.7), 2500)
    dirt = [v * e for v, e in zip(dirt, envelope(len(dirt), 0.004, 0.09))]
    out = mix(normalize(body, 1.0), normalize(claw, 0.35), 0.003)
    return mix(out, normalize(dirt, 0.4), 0.0)


def flap(rng, beats=3, gain=1.0):
    out = silence(0.0)
    for b in range(beats):
        n = int(0.11 * SR)
        w = bandpass(noise(n, rng), rng.uniform(750, 1000), 0.6)
        w = [v * e for v, e in zip(w, envelope(n, 0.025, 0.08, 1.5))]
        mix(out, w, b * 0.12, gain * (1 - 0.15 * b))
    return out


def jump(rng):
    return flap(rng, 3)


def land(rng):
    out = normalize(thump(0.16, 90, 45, rng), 1.0)
    out = mix(out, normalize(step(rng), 0.6), 0.0)
    return mix(out, normalize(flap(rng, 2), 0.45), 0.03)


CLIPS = {
    "ambient1": (ambient1, 0.85),
    "ambient2": (ambient2, 0.85),
    "ambient3": (ambient3, 0.85),
    "hurt1": (hurt1, 0.9),
    "hurt2": (hurt2, 0.9),
    "death": (death, 0.9),
    "angry1": (angry1, 0.9),
    "angry2": (angry2, 0.9),
    "eat1": (lambda r: eat(r, 3), 0.75),
    "eat2": (lambda r: eat(r, 4), 0.75),
    "step1": (step, 0.8),
    "step2": (step, 0.8),
    "step3": (step, 0.8),
    "jump": (jump, 0.8),
    "land": (land, 0.85),
}


def write_wav(path, sig):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in sig))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--wav-only", action="store_true", help="write WAVs next to the OGGs and skip encoding")
    parser.add_argument("--seed", type=int, default=20261126)
    args = parser.parse_args()

    oggenc = shutil.which("oggenc")
    if not oggenc and not args.wav_only:
        sys.exit("oggenc not found: install it with `brew install vorbis-tools`, or pass --wav-only")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for i, (name, (make, peak)) in enumerate(CLIPS.items()):
            rng = random.Random(args.seed + i)
            sig = fade_edges(normalize(make(rng), peak) + silence(0.02))
            wav = (OUT_DIR if args.wav_only else Path(tmp)) / f"{name}.wav"
            write_wav(wav, sig)
            if not args.wav_only:
                subprocess.run([oggenc, "-Q", "-q", "5", "-o", str(OUT_DIR / f"{name}.ogg"), str(wav)], check=True)
            print(f"{name}: {len(sig) / SR:.2f}s")


if __name__ == "__main__":
    main()
