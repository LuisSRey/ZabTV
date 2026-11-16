#!/usr/bin/env python3
"""Fixtures sintéticos deterministas para ZabTV (Task 15).

Genera en tools/fixtures/ (rutas estables):
- m3u_10k.m3u   — 10 000 canales con attrs raros (BOM, attrs repetidos,
  comas en nombres, EXTVLCOPT, URLs acestream://, líneas basura, CRLF).
- xmltv_50k.xml — 50 000 programas con entidades HTML, offsets variados,
  ventana amplia alrededor de una fecha base fija.

`python tools/gen_fixtures.py --quick` genera versiones reducidas
(m3u_1k, xmltv_5k) en <10 s para iteración rápida.

Sin dependencias externas. Seed fijo → bytes idénticos entre corridas.
"""
import argparse
import random
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent / "fixtures"
BASE_DATE = "20261105"

GROUPS = ["General", "Deportes", "Noticias", "Cine", "Infantil", "Música", "Documentales"]
SUFFIXES = ["", " HD", " FHD", " 4K", " +1", " HD ES"]


def gen_m3u(count: int, path: Path) -> None:
    rng = random.Random(42)
    lines = ["#EXTM3U x-tvg-url=\"http://example.com/epg.xml\"", ""]
    for i in range(count):
        group = GROUPS[i % len(GROUPS)]
        name = f"Canal {i:05d}{rng.choice(SUFFIXES)}"
        if i % 97 == 0:
            name = f"Canal, con, comas {i}"
        tvg_id = f"c{i:05d}.ext"
        logo = f"http://example.com/logo/{i % 500}.png"
        # attrs repetidos: gana el último (spec Task 4)
        extinf = (
            f'#EXTINF:-1 tvg-id="{tvg_id}" tvg-id="duplicado-{i}" '
            f'tvg-name="Canal {i}" group-title="{group}" tvg-logo="{logo}",{name}'
        )
        lines.append(extinf)
        if i % 251 == 0:
            lines.append("#EXTVLCOPT:http-user-agent=ZabTV/0.1")
        mod = i % 1000
        if mod == 7:
            lines.append(f"acestream://{i:040x}")  # se preserva verbatim
        elif mod == 13:
            lines.append("file:///etc/hosts")  # UrlPolicer la salta en Task 6
        else:
            lines.append(f"http://cdn.example.com/{i % 50}/stream{i}.m3u8")
        if i % 499 == 0:
            lines.append("línea basura suelta")
    text = "\n".join(lines) + "\n"
    raw = text.encode("utf-8")
    if count >= 10000:
        raw = "﻿".encode("utf-8") + raw  # BOM solo en el fixture grande
        raw = raw.replace(b"stream1234.m3u8\n", b"stream1234.m3u8\r\n", 1)
    path.write_bytes(raw)
    print(f"{path.name}: {count} canales, {len(raw)/1_000_000:.1f} MB")


def gen_xmltv(programs: int, path: Path) -> None:
    rng = random.Random(1234)
    channels = 10
    per_channel = programs // channels
    out = ["<tv>"]
    for c in range(channels):
        out.append(f'<channel id="c{c}.ext"><display-name>Canal {c} HD</display-name></channel>')
    base_day = 20261105
    idx = 0
    for c in range(channels):
        # programas de 30 min desde 3 días antes, offsets rotando
        start_min = -3 * 24 * 60
        for _ in range(per_channel):
            day_shift, rem = divmod(start_min, 24 * 60)
            day = base_day + day_shift
            hh, mm = divmod(rem, 60)
            start = f"{day}{hh:02d}{mm:02d}00"
            stop_min = start_min + 30
            s_day, s_rem = divmod(stop_min, 24 * 60)
            s_hh, s_mm = divmod(s_rem, 60)
            stop = f"{base_day + s_day}{s_hh:02d}{s_mm:02d}00"
            off = ["+0000", "+0100", "+00:00"][idx % 3]
            title = f"Programa {idx} &quot;especial&quot; &amp; más"
            out.append(
                f'<programme channel="c{c}.ext" start="{start} {off}" stop="{stop} {off}">'
                f"<title>{title}</title><desc>Descripción {idx}</desc></programme>"
            )
            start_min += 30
            idx += 1
    out.append("</tv>")
    raw = "\n".join(out).encode("utf-8")
    path.write_bytes(raw)
    print(f"{path.name}: {idx} programas, {len(raw)/1_000_000:.1f} MB")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--quick", action="store_true", help="fixtures reducidos (1k/5k)")
    args = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if args.quick:
        gen_m3u(1000, OUT / "m3u_1k.m3u")
        gen_xmltv(5000, OUT / "xmltv_5k.xml")
    else:
        gen_m3u(10000, OUT / "m3u_10k.m3u")
        gen_xmltv(50000, OUT / "xmltv_50k.xml")
    return 0


if __name__ == "__main__":
    sys.exit(main())
