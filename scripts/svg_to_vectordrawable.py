#!/usr/bin/env python3
"""Convert simple official SVG logos to Android VectorDrawable XML.

Preserves path data and fill colors. Supports <path> (incl. fill-rule) and
basic <g transform="translate(x,y)">. Skips masks/filters (caller must use PNG).
"""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"svg": "http://www.w3.org/2000/svg"}


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def parse_color(raw: str | None, default: str = "#FF000000") -> str | None:
    if raw is None or raw == "none":
        return None
    if raw.startswith("url("):
        return None  # gradients unsupported
    if raw == "currentColor":
        return "#FF000000"
    if raw.startswith("#"):
        h = raw[1:]
        if len(h) == 3:
            h = "".join(c * 2 for c in h)
        if len(h) == 6:
            return "#FF" + h.upper()
        if len(h) == 8:
            return "#" + h.upper()
    if raw.startswith("rgb"):
        nums = [int(x) for x in re.findall(r"\d+", raw)[:3]]
        if len(nums) == 3:
            return "#FF{:02X}{:02X}{:02X}".format(*nums)
    return default


def parse_translate(transform: str | None) -> tuple[float, float]:
    if not transform:
        return 0.0, 0.0
    m = re.search(r"translate\(\s*([-\d.]+)[,\s]+([-\d.]+)\s*\)", transform)
    if m:
        return float(m.group(1)), float(m.group(2))
    return 0.0, 0.0


def viewbox(root: ET.Element) -> tuple[float, float, float, float]:
    vb = root.get("viewBox")
    if vb:
        parts = [float(x) for x in re.split(r"[\s,]+", vb.strip()) if x]
        if len(parts) == 4:
            return parts[0], parts[1], parts[2], parts[3]
    w = float(re.sub(r"[^\d.]", "", root.get("width", "24") or "24") or 24)
    h = float(re.sub(r"[^\d.]", "", root.get("height", "24") or "24") or 24)
    return 0.0, 0.0, w, h


def collect_paths(
    el: ET.Element,
    out: list[tuple[str, str, str | None, float, float]],
    tx: float = 0.0,
    ty: float = 0.0,
) -> None:
    tag = local(el.tag)
    if tag == "g":
        dx, dy = parse_translate(el.get("transform"))
        for child in el:
            collect_paths(child, out, tx + dx, ty + dy)
        return
    if tag == "mask" or tag == "defs" or tag == "clipPath":
        return
    if tag != "path":
        for child in el:
            collect_paths(child, out, tx, ty)
        return
    d = el.get("d")
    if not d:
        return
    fill = parse_color(el.get("fill"), "#FF000000")
    if fill is None and el.get("fill") == "none":
        return
    if fill is None:
        fill = "#FF000000"
    fill_type = None
    fr = el.get("fill-rule") or el.get("fillRule")
    if fr == "evenodd":
        fill_type = "evenOdd"
    # Android pathData: commas/spaces already OK; strip newlines
    d = re.sub(r"\s+", " ", d).strip()
    out.append((d, fill, fill_type, tx, ty))


def to_vector(svg_text: str, width_dp: int = 24, height_dp: int = 24) -> str:
    root = ET.fromstring(svg_text)
    min_x, min_y, vw, vh = viewbox(root)
    paths: list[tuple[str, str, str | None, float, float]] = []
    for child in root:
        collect_paths(child, paths)
    if not paths:
        raise ValueError("no convertible <path> elements")

    # If viewBox origin is non-zero, wrap in a translating group
    group_tx = -min_x
    group_ty = -min_y
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{width_dp}dp"',
        f'    android:height="{height_dp}dp"',
        f'    android:viewportWidth="{vw}"',
        f'    android:viewportHeight="{vh}">',
    ]
    need_group = abs(group_tx) > 1e-6 or abs(group_ty) > 1e-6 or any(
        abs(tx) > 1e-6 or abs(ty) > 1e-6 for *_, tx, ty in paths
    )
    if need_group:
        # One group per path when individual translates differ; else single group
        for d, fill, fill_type, tx, ty in paths:
            gtx, gty = group_tx + tx, group_ty + ty
            lines.append(
                f'    <group android:translateX="{gtx:g}" android:translateY="{gty:g}">'
            )
            ft = f'\n            android:fillType="{fill_type}"' if fill_type else ""
            lines.append(
                f'        <path\n'
                f'            android:fillColor="{fill}"{ft}\n'
                f'            android:pathData="{d}" />'
            )
            lines.append("    </group>")
    else:
        for d, fill, fill_type, *_ in paths:
            ft = f'\n        android:fillType="{fill_type}"' if fill_type else ""
            lines.append(
                f'    <path\n'
                f'        android:fillColor="{fill}"{ft}\n'
                f'        android:pathData="{d}" />'
            )
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("svg")
    ap.add_argument("-o", "--output", required=True)
    ap.add_argument("--width", type=int, default=24)
    ap.add_argument("--height", type=int, default=24)
    args = ap.parse_args()
    text = Path(args.svg).read_text(encoding="utf-8")
    # Drop XML declarations / DOCTYPE that break ElementTree sometimes
    text = re.sub(r"<\?xml[^?]*\?>", "", text, count=1).strip()
    try:
        vd = to_vector(text, args.width, args.height)
    except Exception as e:
        print(f"FAIL {args.svg}: {e}", file=sys.stderr)
        return 1
    Path(args.output).write_text(vd, encoding="utf-8")
    print(f"OK {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
