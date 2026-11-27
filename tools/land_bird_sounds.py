#!/usr/bin/env python3
"""Procedurally synthesize the land bird's sound effects.

Pure standard library: every clip is built from oscillators, noise and simple filters, then written as
WAV and (if `oggenc` from vorbis-tools is installed) encoded to the Ogg Vorbis files Minecraft loads.

Sound design: a large, gentle fantasy bird. Calls are warm and rounded (soft harmonic roll-off, low
formants, a slightly detuned second voice and a little room reverb) rather than buzzy; footsteps are
padded thuds built from filtered noise, with no pitched ringing; wing beats are soft rushes of air.
Clips are levelled by loudness (RMS) as well as peak, so quiet sounds stay quiet.

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
OUT_DIR = ROOT / "src/main/resources/assets/isekai_creature/sounds/entity/yokibird"


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
            g = math.sin(i / a * math.pi / 2)  # smooth onset
        elif i > n - r:
            g = max(0.0, (n - i) / r) ** curve
        else:
            g = 1.0
        out.append(g)
    return out


def apply_env(sig, env):
    return [v * e for v, e in zip(sig, env)]


def noise(n, rng):
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def biquad(sig, b0, b1, b2, a1, a2):
    x1 = x2 = y1 = y2 = 0.0
    out = []
    for x in sig:
        y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, x, y1, y
        out.append(y)
    return out


def bandpass(sig, freq, q):
    w0 = 2 * math.pi * freq / SR
    alpha = math.sin(w0) / (2 * q)
    a0 = 1 + alpha
    return biquad(sig, alpha / a0, 0.0, -alpha / a0, -2 * math.cos(w0) / a0, (1 - alpha) / a0)


def lowpass(sig, cutoff, q=0.707):
    w0 = 2 * math.pi * cutoff / SR
    alpha = math.sin(w0) / (2 * q)
    c = math.cos(w0)
    a0 = 1 + alpha
    return biquad(sig, (1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)


def highpass(sig, cutoff, q=0.707):
    w0 = 2 * math.pi * cutoff / SR
    alpha = math.sin(w0) / (2 * q)
    c = math.cos(w0)
    a0 = 1 + alpha
    return biquad(sig, (1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)


def sweep_bandpass(sig, f_start, f_end, q):
    """Band-pass whose centre glides (exponentially) across the clip: wing rushes."""
    out = []
    x1 = x2 = y1 = y2 = 0.0
    n = len(sig)
    for i, x in enumerate(sig):
        f = f_start * (f_end / f_start) ** (i / max(1, n - 1))
        w0 = 2 * math.pi * f / SR
        alpha = math.sin(w0) / (2 * q)
        a0 = 1 + alpha
        b0, b2 = alpha / a0, -alpha / a0
        a1, a2 = -2 * math.cos(w0) / a0, (1 - alpha) / a0
        y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, x, y1, y
        out.append(y)
    return out


def reverb(sig, wet=0.18, room=0.72, damp=0.35, tail=0.45):
    """Small Schroeder room: four damped combs into two all-passes."""
    sig = sig + silence(tail)
    combs = (0.0297, 0.0371, 0.0411, 0.0437)
    out = [0.0] * len(sig)
    for d in combs:
        delay = int(d * SR)
        buf = [0.0] * delay
        idx = 0
        lp = 0.0
        for i, x in enumerate(sig):
            y = buf[idx]
            lp = y * (1 - damp) + lp * damp
            buf[idx] = x + lp * room
            idx = (idx + 1) % delay
            out[i] += y / len(combs)
    for d, g in ((0.005, 0.7), (0.0017, 0.7)):
        delay = int(d * SR)
        buf = [0.0] * delay
        idx = 0
        res = []
        for x in out:
            b = buf[idx]
            y = -g * x + b
            buf[idx] = x + g * y
            idx = (idx + 1) % delay
            res.append(y)
        out = res
    return [d * (1 - wet) + w * wet for d, w in zip(sig, out)]


def rms(sig):
    return math.sqrt(sum(v * v for v in sig) / max(1, len(sig)))


def level(sig, peak, loudness):
    """Scale to the lower of a peak and an RMS target, so dense clips do not end up louder."""
    m = max((abs(v) for v in sig), default=0.0) or 1.0
    r = rms(sig) or 1.0
    g = min(peak / m, loudness / r)
    return [v * g for v in sig]


def fade_edges(sig, seconds=0.006):
    n = min(len(sig) // 2, int(seconds * SR))
    for i in range(n):
        g = i / n
        sig[i] *= g
        sig[-1 - i] *= g
    return sig


# ------------------------------------------------------------------- voice

def voice(seconds, pitch, rng, *, formants=((620, 2.2, 1.0), (1500, 3.0, 0.45), (2700, 4.0, 0.12)),
          vibrato=(5.5, 0.012), trill=(0.0, 0.0), breath=0.05, tilt=1.6, detune=0.006,
          attack=0.04, release=0.12):
    """Warm syrinx-like call: soft harmonics (1/k^tilt) through vocal formants, doubled by a slightly
    detuned second voice for a gentle shimmer.

    pitch: f(progress 0..1) -> Hz; vibrato: (rate Hz, depth fraction); trill: (rate Hz, depth 0..1)
    """
    n = int(seconds * SR)
    vib_rate, vib_depth = vibrato
    trill_rate, trill_depth = trill
    drift = [rng.uniform(-1, 1) for _ in range(8)]
    src = [0.0] * n
    for voice_i, cents in enumerate((0.0, detune)):
        phase = rng.uniform(0, 2 * math.pi)
        gain = 1.0 if voice_i == 0 else 0.55
        for i in range(n):
            t = i / SR
            p = i / n
            # slow random pitch drift keeps the call alive
            seg = p * (len(drift) - 1)
            k = int(seg)
            wobble = drift[k] + (drift[min(k + 1, len(drift) - 1)] - drift[k]) * (seg - k)
            f = pitch(p) * (1 + cents) * (1 + vib_depth * math.sin(2 * math.pi * vib_rate * t) + 0.004 * wobble)
            phase += 2 * math.pi * f / SR
            harmonics = max(1, min(10, int(4500 / f)))
            s = 0.0
            for h in range(1, harmonics + 1):
                s += math.sin(h * phase) / (h ** tilt)
            if trill_depth:
                s *= 1 - trill_depth * (0.5 + 0.5 * math.sin(2 * math.pi * trill_rate * t))
            src[i] += s * gain
    air = lowpass(noise(n, rng), 2200)
    src = [s + breath * 6 * a for s, a in zip(src, air)]
    out = [0.0] * n
    for freq, q, g in formants:
        for i, v in enumerate(bandpass(src, freq, q)):
            out[i] += v * g
    out = [o + 0.18 * s for o, s in zip(out, lowpass(src, 1800))]
    return apply_env(out, envelope(n, attack, release))


def glide(start, end, shape=1.0):
    return lambda p: start + (end - start) * (p ** shape)


def expo(start, end):
    return lambda p: start * (end / start) ** p


def arch(low, high, low_end=None, peak=0.4):
    low_end = low if low_end is None else low_end

    def f(p):
        if p < peak:
            return low + (high - low) * math.sin(p / peak * math.pi / 2)
        return high + (low_end - high) * ((p - peak) / (1 - peak)) ** 1.3
    return f


def notes(*segments):
    """Legato phrase: (start Hz, end Hz, share of the clip)."""
    total = sum(s for _, _, s in segments)

    def f(p):
        acc = 0.0
        for a, b, s in segments:
            share = s / total
            if p <= acc + share:
                q = (p - acc) / share
                q = q * q * (3 - 2 * q)  # ease between notes
                return a + (b - a) * q
            acc += share
        return segments[-1][1]
    return f


# ------------------------------------------------------------------- calls

def ambient1(rng):
    """'Kweh-roo': a soft rising coo answered by a warm falling note (a fourth apart)."""
    out = voice(0.16, glide(392, 470), rng, release=0.05)
    mix(out, voice(0.42, arch(523, 560, 392, 0.3), rng, vibrato=(6, 0.018)), 0.17)
    return out


def ambient2(rng):
    """Contented purring trill that lifts at the end."""
    return voice(0.75, glide(330, 400, 2.2), rng, trill=(17, 0.45), vibrato=(4, 0.01),
                 formants=((520, 2.0, 1.0), (1300, 3.0, 0.35)), attack=0.08, release=0.2)


def ambient3(rng):
    """Curious 'kwi?' rising chirp and a little reply blip."""
    out = voice(0.24, expo(440, 700), rng, release=0.05, vibrato=(7, 0.008))
    mix(out, voice(0.1, glide(660, 600), rng, attack=0.015, release=0.05), 0.31, 0.7)
    return out


def ambient4(rng):
    """Three-note lilting song, falling through a major triad."""
    return voice(0.85, notes((587, 587, 1.0), (587, 494, 0.3), (494, 494, 0.9), (494, 392, 0.3),
                             (392, 380, 1.4)), rng, vibrato=(5.5, 0.016), attack=0.05, release=0.22)


def ambient5(rng):
    """Low, mellow two-tone hoot."""
    out = voice(0.3, arch(262, 294, 262), rng, formants=((450, 1.8, 1.0), (1100, 3.0, 0.3)), release=0.1)
    mix(out, voice(0.36, arch(262, 277, 220), rng, formants=((450, 1.8, 1.0), (1100, 3.0, 0.3)),
                   release=0.16), 0.36, 0.85)
    return out


def hurt1(rng):
    """Startled yelp: a quick bright note that drops away."""
    return voice(0.24, glide(880, 560, 0.5), rng, trill=(38, 0.25), breath=0.1, tilt=1.3,
                 formants=((800, 2.2, 1.0), (1900, 3.0, 0.5)), attack=0.006, release=0.12)


def hurt2(rng):
    out = voice(0.14, glide(940, 700, 0.5), rng, breath=0.1, tilt=1.3,
                formants=((820, 2.2, 1.0), (2000, 3.0, 0.5)), attack=0.005, release=0.06)
    mix(out, voice(0.16, glide(760, 560), rng, breath=0.1, tilt=1.4, attack=0.01, release=0.1), 0.15, 0.75)
    return out


def death(rng):
    """Long, fading sigh of a call sinking down, with breath trailing off."""
    out = voice(1.3, expo(600, 190), rng, vibrato=(5, 0.03), trill=(9, 0.2), breath=0.08,
                attack=0.02, release=0.6)
    sigh = apply_env(lowpass(bandpass(noise(int(0.8 * SR), rng), 900, 0.7), 1500),
                     envelope(int(0.8 * SR), 0.2, 0.5))
    return mix(out, sigh, 0.75, 0.35)


def puff(seconds, rng, centre=1600):
    n = int(seconds * SR)
    sig = lowpass(bandpass(noise(n, rng), centre, 0.9), 3500)
    return apply_env(sig, envelope(n, seconds * 0.3, seconds * 0.5, 1.5))


def angry1(rng):
    """Low warning: a breathy puff over a soft throaty rattle."""
    growl = voice(0.55, glide(150, 130), rng, trill=(28, 0.6), breath=0.12, tilt=1.4,
                  formants=((420, 2.0, 1.0), (1100, 3.0, 0.35)), attack=0.07, release=0.2)
    return mix([v * 0.8 for v in puff(0.6, rng)], growl, 0.03, 0.9)


def angry2(rng):
    out = voice(0.22, glide(520, 430), rng, trill=(34, 0.5), breath=0.12, tilt=1.4,
                formants=((700, 2.0, 1.0), (1700, 3.0, 0.45)), attack=0.01, release=0.1)
    return mix(out, puff(0.4, rng, 1400), 0.1, 0.7)


# ------------------------------------------------------------------- body sounds

def nibble(rng):
    """One soft beak nibble: a muted tick and a little crunch."""
    n = int(0.05 * SR)
    tick = apply_env(lowpass(bandpass(noise(n, rng), rng.uniform(1100, 1500), 1.2), 2400),
                     envelope(n, 0.001, 0.045, 3))
    crunch = apply_env(lowpass(noise(int(0.07 * SR), rng), 1800), envelope(int(0.07 * SR), 0.004, 0.06, 2))
    return mix(tick, crunch, 0.006, 0.5)


def eat(rng, count):
    out = silence(0.0)
    t = 0.0
    for _ in range(count):
        mix(out, nibble(rng), t, rng.uniform(0.7, 1.0))
        t += rng.uniform(0.11, 0.17)
    return out


def step(rng):
    """Padded footfall: a low, soft thud of filtered noise with a faint brush of claws on the ground."""
    n = int(0.16 * SR)
    thud = lowpass(lowpass(noise(n, rng), rng.uniform(170, 230), 0.9), 260)
    thud = apply_env(thud, envelope(n, 0.004, 0.14, 2.6))
    m = int(0.09 * SR)
    brush = apply_env(lowpass(bandpass(noise(m, rng), rng.uniform(900, 1300), 0.8), 1800),
                      envelope(m, 0.006, 0.08, 2))
    out = mix([v * 1.0 for v in thud], brush, 0.008, 0.12)
    return lowpass(out, 1600)


def flap(rng, strength=1.0):
    """A single soft wing beat: a rising-then-falling rush of air with a gentle low 'fwmp'."""
    n = int(rng.uniform(0.2, 0.26) * SR)
    rush = sweep_bandpass(noise(n, rng), rng.uniform(380, 450), rng.uniform(900, 1100), 0.8)
    rush = lowpass(apply_env(rush, envelope(n, 0.07, 0.12, 1.6)), 2200)
    m = int(0.12 * SR)
    body = apply_env(lowpass(noise(m, rng), 140), envelope(m, 0.02, 0.09, 2))
    return mix([v * strength for v in rush], body, 0.04, 0.6 * strength)


def jump(rng):
    out = flap(rng, 1.0)
    return mix(out, flap(rng, 0.7), 0.17)


def land(rng):
    """Soft landing: a padded double footfall and the wings settling."""
    out = step(rng)
    mix(out, step(rng), 0.05, 0.8)
    rustle = apply_env(lowpass(bandpass(noise(int(0.3 * SR), rng), 1200, 0.7), 2400),
                       envelope(int(0.3 * SR), 0.03, 0.22, 1.8))
    return mix(out, rustle, 0.04, 0.25)


# name: (generator, peak, rms, reverb wet)
CLIPS = {
    "ambient1": (ambient1, 0.7, 0.16, 0.18),
    "ambient2": (ambient2, 0.7, 0.15, 0.18),
    "ambient3": (ambient3, 0.7, 0.16, 0.18),
    "ambient4": (ambient4, 0.7, 0.16, 0.2),
    "ambient5": (ambient5, 0.7, 0.16, 0.18),
    "hurt1": (hurt1, 0.8, 0.2, 0.12),
    "hurt2": (hurt2, 0.8, 0.2, 0.12),
    "death": (death, 0.8, 0.17, 0.22),
    "angry1": (angry1, 0.7, 0.16, 0.12),
    "angry2": (angry2, 0.7, 0.16, 0.12),
    "eat1": (lambda r: eat(r, 3), 0.55, 0.1, 0.05),
    "eat2": (lambda r: eat(r, 4), 0.55, 0.1, 0.05),
    "step1": (step, 0.6, 0.14, 0.04),
    "step2": (step, 0.6, 0.14, 0.04),
    "step3": (step, 0.6, 0.14, 0.04),
    "step4": (step, 0.6, 0.14, 0.04),
    "flap1": (flap, 0.6, 0.13, 0.08),
    "flap2": (flap, 0.6, 0.13, 0.08),
    "flap3": (flap, 0.6, 0.13, 0.08),
    "jump": (jump, 0.6, 0.13, 0.08),
    "land": (land, 0.65, 0.14, 0.06),
}


def write_wav(path, sig):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in sig))


def trim_tail(sig, threshold=0.0015):
    end = len(sig)
    while end > 0 and abs(sig[end - 1]) < threshold:
        end -= 1
    return sig[:end]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--wav-only", action="store_true", help="write WAVs next to the OGGs and skip encoding")
    parser.add_argument("--seed", type=int, default=20261126)
    parser.add_argument("--only", nargs="*", help="only these clips")
    args = parser.parse_args()

    oggenc = shutil.which("oggenc")
    if not oggenc and not args.wav_only:
        sys.exit("oggenc not found: install it with `brew install vorbis-tools`, or pass --wav-only")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for i, (name, (make, peak, loud, wet)) in enumerate(CLIPS.items()):
            if args.only and name not in args.only:
                continue
            rng = random.Random(args.seed + i)
            sig = make(rng)
            sig = reverb(sig, wet=wet) if wet else sig
            sig = fade_edges(trim_tail(level(sig, peak, loud)) + silence(0.02))
            wav = (OUT_DIR if args.wav_only else Path(tmp)) / f"{name}.wav"
            write_wav(wav, sig)
            if not args.wav_only:
                subprocess.run([oggenc, "-Q", "-q", "5", "-o", str(OUT_DIR / f"{name}.ogg"), str(wav)], check=True)
            print(f"{name}: {len(sig) / SR:.2f}s  peak {max(abs(v) for v in sig):.2f}  rms {rms(sig):.3f}")


if __name__ == "__main__":
    main()
