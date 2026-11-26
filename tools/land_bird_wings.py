#!/usr/bin/env python3
"""Adds layered flight feathers to the adult land bird wings. Pure standard library.

Each wing keeps its original folded covert plane and gains two child bones:

* ``<side>_wing_primaries`` - long flight feathers hinged at the wrist. Folded, they lie along the side
  and cross over the tail like a resting bird's; animations swing them out into a wingtip fan.
* ``<side>_wing_secondaries`` - trailing-edge feathers along the back of the wing.

The feathers are painted into unused texture space (x >= 128) of every adult texture, coloured from that
plumage's existing wing so all six variants match. Re-running replaces the previous output.

Usage:
    python3 tools/land_bird_wings.py                 # update the mod's assets in place
    python3 tools/land_bird_wings.py --out DIR       # write copies to DIR (geo/ and textures/)
"""

import argparse
import json
import re
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from land_bird_preview import ASSETS, read_png  # noqa: E402

VARIANTS = range(1, 7)
# Opaque wing-covert area on the left wing's outer (east) face, used as the colour source.
PALETTE_SOURCE = (48, 0, 14, 13)

# Feather planes in left-wing rest coordinates (model pixels). z grows toward the tail.
PRIMARIES = {"x": 6.45, "y": (10.0, 16.0), "z": (-2.5, 17.5), "pivot": [6.3, 13.0, -1.5]}
SECONDARIES = {"x": 6.15, "y": (13.0, 22.0), "z": (8.5, 15.5), "pivot": [6.3, 18.0, 10.0]}
# Texture slots (u, v) for the outer and inner faces of each plane; right wing sits 32 px lower.
SLOTS = {"primaries": ((128, 0), (128, 8)), "secondaries": ((152, 0), (152, 10))}
RIGHT_V_OFFSET = 32


# ------------------------------------------------------------------ io

def dump_json(doc):
    """2-space JSON with number arrays kept on one line, matching Blockbench's output."""
    text = json.dumps(doc, indent=2, ensure_ascii=False)
    return re.sub(r"\[\s+([-+0-9.eE,\s]+?)\s+\]",
                  lambda m: "[" + ", ".join(v.strip() for v in m.group(1).split(",")) + "]", text) + "\n"


def write_png_rgba(path, w, h, px):
    raw = b"".join(b"\x00" + bytes(c for p in px[y * w:(y + 1) * w] for c in p) for y in range(h))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xffffffff)

    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# ------------------------------------------------------------------ painting

def luminance(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def palette(tex):
    w, _, px = tex
    u, v, pw, ph = PALETTE_SOURCE
    colours = sorted((px[(v + y) * w + u + x][:3] for y in range(ph) for x in range(pw)
                      if px[(v + y) * w + u + x][3] > 200), key=luminance)

    def pick(f):
        return colours[min(len(colours) - 1, int(f * len(colours)))]

    return {"outline": pick(0.04), "dark": pick(0.22), "mid": pick(0.5), "light": pick(0.8), "shine": pick(0.97)}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def paint_feathers(length, height, feathers, pal, inner=False):
    """A strip of overlapping feathers lying along +z (index 0 = front, tips toward the end).

    feathers: list of (row, thickness, start, tip) - rows from the top of the plane, start/tip along z.
    Returns rows (top to bottom) of RGBA tuples, index 0 at the front.
    """
    clear = (0, 0, 0, 0)
    img = [[clear] * length for _ in range(height)]
    for row, thick, start, tip in feathers:
        for z in range(start, tip):
            t = (z - start) / max(1, tip - start - 1)        # 0 at the base, 1 at the tip
            taper = 1 if tip - z <= 1 and thick > 1 else 0    # round the tip off by a pixel
            for k in range(thick - taper):
                y = row + k
                if not 0 <= y < height:
                    continue
                if k == thick - taper - 1:                    # lower edge of the vane: shadow line
                    c = pal["outline"] if t > 0.15 else pal["dark"]
                elif k == 0:                                  # shaft side: soft highlight
                    c = pal["light"] if 0.2 < t < 0.7 else pal["mid"]
                else:
                    c = pal["mid"] if t < 0.6 else pal["dark"]
                if tip - z <= 2 and k != thick - taper - 1:   # darker feather tips
                    c = pal["dark"]
                img[y][z] = shade(c, 0.78) + (255,) if inner else c + (255,)
    return img


def primaries_image(pal, inner):
    length, height = 20, 6
    # Staggered: the lowest (outermost) feather is the longest.
    feathers = [(0, 2, 0, 15), (1, 2, 0, 17), (3, 2, 0, 19), (4, 2, 1, 20)]
    return paint_feathers(length, height, feathers, pal, inner)


def secondaries_image(pal, inner):
    length, height = 7, 9
    feathers = [(0, 2, 0, 5), (2, 2, 0, 6), (4, 2, 0, 7), (6, 2, 0, 6), (8, 1, 0, 4)]
    return paint_feathers(length, height, feathers, pal, inner)


def blit(px, w, u, v, img, reverse):
    for y, row in enumerate(img):
        for x, c in enumerate(reversed(row) if reverse else row):
            px[(v + y) * w + u + x] = c


# ------------------------------------------------------------------ geometry

def plane(spec, side, outer_uv, inner_uv, length, height):
    """Zero-thickness plane in the YZ plane. East faces run tail-to-head in u, west faces head-to-tail."""
    sign = 1 if side == "left" else -1
    x = spec["x"] * sign
    y0, y1 = spec["y"]
    z0, z1 = spec["z"]
    east, west = (outer_uv, inner_uv) if side == "left" else (inner_uv, outer_uv)
    return {
        "origin": [x, y0, z0],
        "size": [0, y1 - y0, z1 - z0],
        "uv": {
            "east": {"uv": list(east), "uv_size": [length, height]},
            "west": {"uv": list(west), "uv_size": [length, height]},
        },
    }


def feather_bones(side):
    sign = 1 if side == "left" else -1
    v_off = 0 if side == "left" else RIGHT_V_OFFSET
    bones = []
    for name, spec, size in (("primaries", PRIMARIES, (20, 6)), ("secondaries", SECONDARIES, (7, 9))):
        (ou, ov), (iu, iv) = SLOTS[name]
        pivot = list(spec["pivot"])
        pivot[0] *= sign
        bones.append({
            "name": f"{side}_wing_{name}",
            "parent": f"{side}_wing",
            "pivot": pivot,
            "cubes": [plane(spec, side, (ou, ov + v_off), (iu, iv + v_off), *size)],
        })
    return bones


# ------------------------------------------------------------------ main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", help="write copies here instead of updating the assets")
    args = ap.parse_args()
    out = Path(args.out) if args.out else ASSETS
    (out / "geo/entity/yoki_bird").mkdir(parents=True, exist_ok=True)
    (out / "textures/entity/yoki_bird").mkdir(parents=True, exist_ok=True)

    for variant in VARIANTS:
        name = f"adult_{variant}"
        geo_path = ASSETS / f"geo/entity/yoki_bird/{name}.geo.json"
        doc = json.loads(geo_path.read_text())
        geo = doc["minecraft:geometry"][0]
        bones = [b for b in geo["bones"] if "_wing_" not in b["name"]]
        insert = [i for i, b in enumerate(bones) if b["name"] == "right_wing"][0] + 1
        geo["bones"] = bones[:insert] + feather_bones("left") + feather_bones("right") + bones[insert:]
        (out / f"geo/entity/yoki_bird/{name}.geo.json").write_text(dump_json(doc))

        tex_path = ASSETS / f"textures/entity/yoki_bird/{name}.png"
        w, h, px = read_png(tex_path)
        px = list(px)
        pal = palette((w, h, px))
        images = {
            ("primaries", False): primaries_image(pal, False),
            ("primaries", True): primaries_image(pal, True),
            ("secondaries", False): secondaries_image(pal, False),
            ("secondaries", True): secondaries_image(pal, True),
        }
        for side, v_off in (("left", 0), ("right", RIGHT_V_OFFSET)):
            for kind, ((ou, ov), (iu, iv)) in SLOTS.items():
                # u runs tail-to-head on east faces: the left outer and right inner faces.
                blit(px, w, ou, ov + v_off, images[(kind, False)], reverse=(side == "left"))
                blit(px, w, iu, iv + v_off, images[(kind, True)], reverse=(side == "right"))
        write_png_rgba(out / f"textures/entity/yoki_bird/{name}.png", w, h, px)
        print(f"{name}: palette {pal['dark']}..{pal['shine']}")


if __name__ == "__main__":
    main()
