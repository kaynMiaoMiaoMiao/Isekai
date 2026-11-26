#!/usr/bin/env python3
"""Offline preview / analysis of the land bird GeckoLib model. Pure standard library.

Renders a textured, posed model (optionally with a rider in the vanilla riding pose) to PNG,
and samples bone positions over an animation (e.g. foot heights to find footfalls).

Coordinates follow the Bedrock geo file (model pixels, y up, head toward -z). GeckoLib mirrors X
and negates X/Y rotations when baking; mapped back to these coordinates that is a right-handed
rotation of (-rx, ry, -rz), applied X first, then Y, then Z.

Examples:
    python3 tools/land_bird_preview.py render --anim adult.walk --time 0.2 --rider --view side -o walk.png
    python3 tools/land_bird_preview.py feet --anim adult.walk
"""

import argparse
import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/isekai"
MODEL_SCALE = {"adult": 0.8, "baby": 0.72}


# ------------------------------------------------------------------ PNG io

def read_png(path):
    data = Path(path).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, palette, trns = 8, b"", None, None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", chunk[:10])
        elif kind == b"PLTE":
            palette = [tuple(chunk[i:i + 3]) for i in range(0, len(chunk), 3)]
        elif kind == b"tRNS":
            trns = chunk
        elif kind == b"IDAT":
            idat += chunk
    assert depth == 8, "only 8-bit PNGs"
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * channels
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    px = []
    for line in rows:
        for x in range(w):
            if ctype == 6:
                px.append(tuple(line[x * 4:x * 4 + 4]))
            elif ctype == 2:
                px.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            elif ctype == 3:
                idx = line[x]
                alpha = trns[idx] if trns and idx < len(trns) else 255
                px.append(palette[idx] + (alpha,))
            elif ctype == 4:
                g, al = line[x * 2], line[x * 2 + 1]
                px.append((g, g, g, al))
            else:
                g = line[x]
                px.append((g, g, g, 255))
    return w, h, px


def write_png(path, w, h, rgb):
    raw = b"".join(b"\x00" + bytes(rgb[y * w * 3:(y + 1) * w * 3]) for y in range(h))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xffffffff)

    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# ------------------------------------------------------------------ math

def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rot_x(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def rot_y(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rot_z(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def bedrock_rotation(r):
    """Rotation matrix for a Bedrock [rx, ry, rz] as GeckoLib renders it, in geo-file coordinates."""
    rx, ry, rz = r
    return mat_mul(rot_z(-rz), mat_mul(rot_y(ry), rot_x(-rx)))


def about(pivot, m):
    return mat_mul(translate(*pivot), mat_mul(m, translate(-pivot[0], -pivot[1], -pivot[2])))


def apply(m, p):
    x, y, z = p
    return (m[0][0] * x + m[0][1] * y + m[0][2] * z + m[0][3],
            m[1][0] * x + m[1][1] * y + m[1][2] * z + m[1][3],
            m[2][0] * x + m[2][1] * y + m[2][2] * z + m[2][3])


# ------------------------------------------------------------------ animation sampling

def keyframe_value(v):
    if isinstance(v, dict):
        v = v.get("post", v.get("pre"))
    if isinstance(v, dict):
        v = v.get("vector", [0, 0, 0])
    if isinstance(v, (int, float)):
        v = [v, v, v]
    return [float(c) if isinstance(c, (int, float)) else 0.0 for c in v]


def sample_channel(channel, t, length, loop):
    if not isinstance(channel, dict) or not channel or any(k in channel for k in ("post", "pre", "vector")):
        return keyframe_value(channel) if channel else [0.0, 0.0, 0.0]
    keys = sorted((float(k), keyframe_value(v)) for k, v in channel.items())
    if loop and length:
        t = t % length
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return keys[-1][1]


def sample_pose(animation, t):
    """{bone: (rotation delta, position delta)} at time t."""
    pose = {}
    if not animation:
        return pose
    length = animation.get("animation_length", 0)
    loop = animation.get("loop", False) in (True, "loop")
    for bone, channels in animation.get("bones", {}).items():
        rot = sample_channel(channels.get("rotation"), t, length, loop) if "rotation" in channels else [0, 0, 0]
        pos = sample_channel(channels.get("position"), t, length, loop) if "position" in channels else [0, 0, 0]
        pose[bone] = (rot, pos)
    return pose


# ------------------------------------------------------------------ model

class Model:
    def __init__(self, stage="adult", variant=1, geo_path=None, anim_path=None, tex_path=None):
        name = f"{stage}_{variant}"
        self.stage = stage
        geo_path = geo_path or ASSETS / f"geo/entity/yoki_bird/{name}.geo.json"
        anim_path = anim_path or ASSETS / f"animations/entity/yoki_bird/{name}.animation.json"
        geo = json.loads(Path(geo_path).read_text())["minecraft:geometry"][0]
        self.tex_w = geo["description"]["texture_width"]
        self.tex_h = geo["description"]["texture_height"]
        self.bones = {b["name"]: b for b in geo["bones"]}
        self.order = [b["name"] for b in geo["bones"]]
        self.animations = json.loads(Path(anim_path).read_text())["animations"] if Path(anim_path).exists() else {}
        tex = Path(tex_path) if tex_path else ASSETS / f"textures/entity/yoki_bird/{name}.png"
        self.texture = read_png(tex) if tex.exists() else None

    def bone_matrices(self, pose):
        mats = {}

        def resolve(name):
            if name in mats:
                return mats[name]
            bone = self.bones[name]
            parent = resolve(bone["parent"]) if bone.get("parent") in self.bones else translate(0, 0, 0)
            drot, dpos = pose.get(name, ([0, 0, 0], [0, 0, 0]))
            base = bone.get("rotation", [0, 0, 0])
            rot = [base[i] + drot[i] for i in range(3)]
            local = mat_mul(translate(dpos[0], dpos[1], dpos[2]), about(bone.get("pivot", [0, 0, 0]),
                                                                          bedrock_rotation(rot)))
            mats[name] = mat_mul(parent, local)
            return mats[name]

        for name in self.order:
            resolve(name)
        return mats

    def quads(self, pose, skip=("hitbox",)):
        """Yields (corners[4], uv-rect or None, colour or None) in model pixels."""
        mats = self.bone_matrices(pose)
        for name in self.order:
            if name in skip:
                continue
            for cube in self.bones[name].get("cubes", []):
                m = mats[name]
                if "rotation" in cube:
                    m = mat_mul(m, about(cube.get("pivot", [0, 0, 0]), bedrock_rotation(cube["rotation"])))
                yield from cube_quads(m, cube)


FACES = {
    # face: corner selectors (x, y, z) as 0=min/1=max, ordered top-left, top-right, bottom-right, bottom-left
    "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    "east": [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    "west": [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    "down": [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}


def cube_quads(m, cube):
    inflate = cube.get("inflate", 0.0)
    o = [cube["origin"][i] - inflate for i in range(3)]
    s = [cube["size"][i] + 2 * inflate for i in range(3)]
    uv = cube.get("uv")
    for face, sel in FACES.items():
        rect = None
        if isinstance(uv, dict):
            f = uv.get(face)
            if not f:
                continue
            rect = (f["uv"][0], f["uv"][1], f.get("uv_size", [0, 0])[0], f.get("uv_size", [0, 0])[1])
        corners = [apply(m, (o[0] + sx * s[0], o[1] + sy * s[1], o[2] + sz * s[2])) for sx, sy, sz in sel]
        yield corners, rect, cube.get("_colour")


# ------------------------------------------------------------------ rider (vanilla player, riding pose)

PLAYER_PX = 0.9375 / 16  # blocks per player-model pixel


def rider_boxes(seat_feet_y_blocks, seat_back_blocks, bird_scale):
    """Steve-sized boxes in bird model pixels. Riding pose: legs pitched 81 degrees forward,
    arms 36 degrees, as BipedEntityModel#setAngles does."""
    k = PLAYER_PX / (bird_scale / 16)  # player px -> bird px
    feet_y = seat_feet_y_blocks / (bird_scale / 16)
    z0 = seat_back_blocks / (bird_scale / 16)
    hip = feet_y + 12 * k

    def box(xmin, ymin, zmin, xmax, ymax, zmax, colour, pivot=None, rot=None):
        cube = {"origin": [xmin, ymin, zmin], "size": [xmax - xmin, ymax - ymin, zmax - zmin],
                "uv": {f: {"uv": [0, 0], "uv_size": [1, 1]} for f in FACES}, "_colour": colour}
        m = translate(0, 0, 0)
        if rot:
            m = about(pivot, bedrock_rotation(rot))
        return list(cube_quads(m, cube))

    q = []
    q += box(-4 * k, hip, z0 - 2 * k, 4 * k, hip + 12 * k, z0 + 2 * k, (40, 160, 170))           # torso
    q += box(-4 * k, hip + 12 * k, z0 - 4 * k, 4 * k, hip + 20 * k, z0 + 4 * k, (200, 150, 110))  # head
    for side in (-1, 1):
        x0 = 0 if side > 0 else -4 * k
        q += box(x0, hip - 12 * k, z0 - 2 * k, x0 + 4 * k, hip, z0 + 2 * k, (50, 60, 160),
                 pivot=[x0 + 2 * k, hip, z0], rot=[-81, 0, 0])                                    # legs forward
        ax = 4 * k if side > 0 else -8 * k
        q += box(ax, hip, z0 - 2 * k, ax + 4 * k, hip + 12 * k, z0 + 2 * k, (200, 150, 110),
                 pivot=[ax + 2 * k, hip + 10 * k, z0], rot=[-36, 0, 0])                          # arms
    return q


# ------------------------------------------------------------------ rasteriser

VIEWS = {"side": (90, 0), "side_r": (-90, 0), "front": (180, 0), "back": (0, 0), "top": (0, 89.9),
         "three_quarter": (135, 20), "rear_quarter": (35, 25)}


def render(quads, texture, tex_size, size=480, view="side", zoom=None, centre=None, bg=(34, 36, 44)):
    yaw, pitch = VIEWS[view] if isinstance(view, str) else view
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p
        x, z = x * cy - z * sy, x * sy + z * cy      # yaw around Y
        y, z = y * cp - z * sp, y * sp + z * cp      # pitch around X
        return x, y, z

    tris = []
    for corners, rect, colour in quads:
        pts = [project(c) for c in corners]
        uvs = None
        if rect:
            u, v, w, h = rect
            uvs = [(u, v), (u + w, v), (u + w, v + h), (u, v + h)]
        tris.append((pts[0], pts[1], pts[2], uvs and (uvs[0], uvs[1], uvs[2]), rect, colour, corners))
        tris.append((pts[0], pts[2], pts[3], uvs and (uvs[0], uvs[2], uvs[3]), rect, colour, corners))

    allp = [p for t in tris for p in t[:3]]
    if centre is None:
        xs, ys = [p[0] for p in allp], [p[1] for p in allp]
        centre = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
        span = max(max(xs) - min(xs), max(ys) - min(ys)) * 1.1
        zoom = size / span
    zbuf = [-1e9] * (size * size)
    img = list(bg) * (size * size)
    tw, th, tpx = texture if texture else (1, 1, [(255, 255, 255, 255)])
    sx_, sy_ = tw / tex_size[0], th / tex_size[1]
    light = (0.35, 0.8, 0.5)
    for a, b, c, uvs, rect, colour, corners in tris:
        # face normal (model space) for simple lambert shading
        e1 = [corners[1][i] - corners[0][i] for i in range(3)]
        e2 = [corners[3][i] - corners[0][i] for i in range(3)]
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(v * v for v in n)) or 1
        shade = 0.55 + 0.45 * abs(sum(n[i] / nl * light[i] for i in range(3)))
        P = [((p[0] - centre[0]) * zoom + size / 2, size / 2 - (p[1] - centre[1]) * zoom, p[2]) for p in (a, b, c)]
        minx, maxx = max(0, int(min(p[0] for p in P))), min(size - 1, int(max(p[0] for p in P)) + 1)
        miny, maxy = max(0, int(min(p[1] for p in P))), min(size - 1, int(max(p[1] for p in P)) + 1)
        (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = P
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-9:
            continue
        for py in range(miny, maxy + 1):
            for px in range(minx, maxx + 1):
                fx, fy = px + 0.5, py + 0.5
                w0 = ((y1 - y2) * (fx - x2) + (x2 - x1) * (fy - y2)) / den
                w1 = ((y2 - y0) * (fx - x2) + (x0 - x2) * (fy - y2)) / den
                w2 = 1 - w0 - w1
                if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                    continue
                z = w0 * z0 + w1 * z1 + w2 * z2
                idx = py * size + px
                if z <= zbuf[idx]:
                    continue
                if uvs:
                    u = w0 * uvs[0][0] + w1 * uvs[1][0] + w2 * uvs[2][0]
                    v = w0 * uvs[0][1] + w1 * uvs[1][1] + w2 * uvs[2][1]
                    tx = min(tw - 1, max(0, int(u * sx_)))
                    ty = min(th - 1, max(0, int(v * sy_)))
                    r, g, bb, al = tpx[ty * tw + tx] if not colour else colour + (255,)
                    if al < 128:
                        continue
                else:
                    r, g, bb = colour or (200, 200, 200)
                zbuf[idx] = z
                img[idx * 3:idx * 3 + 3] = [int(r * shade), int(g * shade), int(bb * shade)]
    return size, img, centre, zoom


def draw_hline(img, size, y, colour):
    if 0 <= y < size:
        for x in range(size):
            if (x // 4) % 2 == 0:
                img[(y * size + x) * 3:(y * size + x) * 3 + 3] = list(colour)


# ------------------------------------------------------------------ cli

def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("render")
    r.add_argument("--stage", default="adult")
    r.add_argument("--variant", type=int, default=1)
    r.add_argument("--geo")
    r.add_argument("--tex")
    r.add_argument("--anim-file")
    r.add_argument("--anim", default=None)
    r.add_argument("--time", type=float, default=0.0)
    r.add_argument("--view", default="side")
    r.add_argument("--size", type=int, default=480)
    r.add_argument("--rider", action="store_true")
    r.add_argument("--seat", type=float, default=None, help="getMountedHeightOffset in blocks")
    r.add_argument("--seat-back", type=float, default=5 / 16 * 0.8)
    r.add_argument("--ground", action="store_true", help="draw the ground line")
    r.add_argument("-o", "--out", required=True)
    f = sub.add_parser("feet")
    f.add_argument("--stage", default="adult")
    f.add_argument("--anim", required=True)
    f.add_argument("--geo")
    f.add_argument("--anim-file")
    f.add_argument("--steps", type=int, default=40)
    args = ap.parse_args()

    if args.cmd == "render":
        model = Model(args.stage, args.variant, args.geo, args.anim_file, args.tex)
        pose = sample_pose(model.animations.get(args.anim), args.time) if args.anim else {}
        quads = list(model.quads(pose))
        if args.rider:
            scale = MODEL_SCALE[args.stage]
            seat = args.seat if args.seat is not None else 24.6 / 16 * scale
            quads += rider_boxes(seat - 0.35, args.seat_back, scale)
        size, img, centre, zoom = render(quads, model.texture, (model.tex_w, model.tex_h), args.size, args.view)
        if args.ground:
            draw_hline(img, size, int(size / 2 - (0 - centre[1]) * zoom), (120, 200, 120))
        write_png(args.out, size, size, img)
        print(args.out)
    else:
        model = Model(args.stage, 1, args.geo, args.anim_file)
        anim = model.animations[args.anim]
        length = anim["animation_length"]
        print(f"{'t':>6} {'L foot y':>9} {'L foot z':>9} {'R foot y':>9} {'R foot z':>9}")
        for i in range(args.steps + 1):
            t = length * i / args.steps
            mats = model.bone_matrices(sample_pose(anim, t))
            out = []
            for foot in ("left_foot", "right_foot"):
                # lowest point of the foot's cubes
                best = None
                for cube in model.bones[foot].get("cubes", []):
                    m = mats[foot]
                    if "rotation" in cube:
                        m = mat_mul(m, about(cube.get("pivot", [0, 0, 0]), bedrock_rotation(cube["rotation"])))
                    for corners, _, _ in cube_quads(m, cube):
                        for p in corners:
                            if best is None or p[1] < best[1]:
                                best = p
                out += [best[1], best[2]]
            print(f"{t:6.3f} {out[0]:9.2f} {out[1]:9.2f} {out[2]:9.2f} {out[3]:9.2f}")


if __name__ == "__main__":
    main()
