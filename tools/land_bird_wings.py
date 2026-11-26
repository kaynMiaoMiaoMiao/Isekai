#!/usr/bin/env python3
"""Adds layered flight feathers to the adult land bird wings. Pure standard library.

Each wing keeps its original folded covert plane and gains two child bones:

* ``<side>_wing_primaries`` - a broad hand of long flight feathers hinged at the wrist, with a band of
  greater coverts over their bases. Folded, it lies along the side and crosses over the tail like a resting
  bird's; animations swing it out into a wide, slotted wingtip.
* ``<side>_wing_secondaries`` - a curtain of trailing-edge feathers along the back of the arm.

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
# Both sit just inside the covert plane (x 6.3), so folded the coverts lie over the flight feathers.
PRIMARIES = {"x": 6.15, "y": (6.0, 19.0), "z": (-3.0, 21.0), "pivot": [6.2, 13.0, -1.5]}
SECONDARIES = {"x": 6.0, "y": (11.0, 24.0), "z": (6.0, 17.0), "pivot": [6.2, 18.0, 8.0]}
SIZES = {"primaries": (24, 13), "secondaries": (11, 13)}
# Texture slots (u, v) for the outer and inner faces of each plane; right wing sits 32 px lower.
SLOTS = {"primaries": ((0, 128), (0, 144)), "secondaries": ((32, 128), (48, 128))}
RIGHT_V_OFFSET = 32
# Earlier versions painted narrower feathers here; cleared so the texture holds no stale art.
OLD_SLOTS = (128, 0, 48, 64)


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


def paint_feathers(length, height, feathers, coverts, pal, inner=False):
    """A fan of overlapping feathers lying along +z (index 0 = front/base, tips toward the end).

    feathers: list of (row, width, tip) - top row of the vane, its width, and where its tip ends along z.
    coverts:  (length at the top row, length at the bottom row) of the covert band over the feather bases.
    Returns rows (top to bottom) of RGBA tuples, index 0 at the base.
    """
    clear = (0, 0, 0, 0)
    img = [[clear] * length for _ in range(height)]
    for row, width, tip in feathers:
        for z in range(tip):
            t = z / max(1, tip - 1)                           # 0 at the base, 1 at the tip
            left = tip - z                                    # pixels to the tip
            # rounded, slightly pointed tip: the last pixels narrow toward the shaft
            narrow = 2 if left == 1 else (1 if left == 2 else 0)
            for k in range(width):
                if k < narrow // 2 or k >= width - (narrow - narrow // 2):
                    continue
                y = row + k
                if not 0 <= y < height:
                    continue
                if k == 0:                                    # leading edge: thin highlight
                    c = pal["light"] if t < 0.75 else pal["mid"]
                elif k == width - 1:                          # trailing edge: shadow where the next one overlaps
                    c = pal["outline"]
                elif k == 1:                                  # shaft
                    c = pal["shine"] if t < 0.5 else pal["light"]
                else:
                    c = pal["mid"] if t < 0.55 else pal["dark"]
                if left <= 3 and k != 1:                      # dark feather tips
                    c = pal["dark"] if left == 3 else pal["outline"]
                img[y][z] = c
    top, bottom = coverts
    for y in range(height):
        end = round(top + (bottom - top) * y / max(1, height - 1))
        for z in range(end):
            scallop = (y % 3 == 2) and z == end - 1           # scalloped covert edge
            if z == end - 1 or scallop:
                c = pal["dark"]
            elif (z + y) % 4 == 0:
                c = pal["light"]
            else:
                c = pal["mid"] if z < end - 2 else pal["light"]
            img[y][z] = c
    return [[(shade(c, 0.72) if inner else c) + (255,) if c != clear else clear for c in r] for r in img]


def primaries_image(pal, inner):
    length, height = SIZES["primaries"]
    # Folded, row 0 sits against the arm and the lowest row hangs outermost; spread, the lower rows form
    # the leading edge. The outer primaries are the longest, with gaps between the tips (slotted wingtip).
    feathers = [(0, 3, 16), (2, 3, 18), (4, 3, 20), (6, 3, 22), (8, 3, 24), (10, 3, 23)]
    return paint_feathers(length, height, feathers, (7, 5), pal, inner)


def secondaries_image(pal, inner):
    length, height = SIZES["secondaries"]
    feathers = [(0, 3, 9), (2, 3, 10), (4, 3, 11), (6, 3, 11), (8, 3, 10), (10, 3, 9)]
    return paint_feathers(length, height, feathers, (4, 3), pal, inner)


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
    for name, spec in (("primaries", PRIMARIES), ("secondaries", SECONDARIES)):
        size = SIZES[name]
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
        u0, v0, cw, ch = OLD_SLOTS
        for y in range(v0, v0 + ch):
            for x in range(u0, u0 + cw):
                px[y * w + x] = (0, 0, 0, 0)
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
