#!/usr/bin/env python3
"""Mide el glosado de punta a punta por el WebSocket del backend.

Es PR-INT-02 (las 30 frases de FR-01) generalizado para que también sirva de
conjunto de validación con FR-02, que trae las mismas convenciones de glosa y no
está en el prompt del modelo.

Las frases y sus glosas de referencia se leen del documento de datos de la tesis
(`../signall-docs/borradores/plan-pruebas/02-datos/`), así que no hay una segunda
copia que se pueda desincronizar. Cuenta:

- frases con glosas exactas (mismo contenido y mismo orden),
- glosas devueltas contra glosas declaradas, y
- de dónde vino cada frase (caché exacta, caché semántica o LLM), con el
  contador `rt_cache_total` y no con el reloj.

**Vaciar la caché antes de medir**, o la segunda corrida mide la caché y no el
modelo:

    psql "$DATABASE_URL" -c "DELETE FROM semantic_cache;"
    docker compose restart backend   # carga la tabla al arrancar

Ojo con FR-02: sus 90 frases son tres variantes de cada frase de FR-01, así que
una sustituye a otra por un solo sustantivo y el caché semántico las confunde.
Medido el 2026-11-06: 5 de los 20 fallos de FR-02 eran aciertos falsos del caché
(la frase de referencia pedía HERMANO y salió TIO, pedía CONTENTO y salió FELIZ),
y por eso el número por el socket sale 5 puntos abajo del que da el mismo
pipeline con la caché salteada. Cada línea dice de dónde vino, y el resumen
avisa cuántas frases no pasaron por el modelo.

Uso, con el backend levantado:

    python3 tools/medir-glosado.py            # FR-01, las 30 frases
    python3 tools/medir-glosado.py fr02       # FR-02, las 90 opciones
    python3 tools/medir-glosado.py fr01 fr02  # las dos, una tras otra
"""

from __future__ import annotations

import asyncio
import json
import re
import sys
import urllib.request
from pathlib import Path

import websockets

URL = "ws://127.0.0.1:8080/ws/translate"
METRICS = "http://127.0.0.1:8080/metrics.json"

DATA = Path(__file__).resolve().parent.parent.parent / "signall-docs/borradores/plan-pruebas/02-datos"
FR_01 = DATA / "FR-01-frases-dominio.md"
FR_02 = DATA / "FR-02-opciones-frases.md"

# `| FR-01-001 | frase | tipo | oraciones | glosas | n |`
_FR_01_ROW = re.compile(r"^\| (FR-01-\d{3}) \| (.+?) \| (.+?) \| (\d+) \| (.+?) \| (\d+) \|$")
# `| FR-02-001 | FR-01-002 | frase | glosas | cambio | vocabulario |`
_FR_02_ROW = re.compile(r"^\| (FR-02-\d{3}) \| (FR-01-\d{3}) \| (.+?) \| (.+?) \| (.+?) \| (.+?) \|$")


def _rows(path: Path) -> list[str]:
    if not path.exists():
        raise SystemExit(f"no está el documento de datos: {path}")
    return path.read_text(encoding="utf-8").splitlines()


def load_fr01() -> list[tuple[str, str, str, list[str]]]:
    rows = []
    for line in _rows(FR_01):
        m = _FR_01_ROW.match(line.strip())
        if m:
            phrase_id, text, kind, _sentences, glosses, _n = m.groups()
            rows.append((phrase_id, text, kind, [g.strip() for g in glosses.split(",")]))
    if len(rows) != 30:
        raise SystemExit(f"FR-01: esperaba 30 frases, leí {len(rows)}")
    return rows


def load_fr02() -> list[tuple[str, str, str, list[str]]]:
    rows = []
    for line in _rows(FR_02):
        m = _FR_02_ROW.match(line.strip())
        if m:
            phrase_id, source, text, glosses, _change, _vocabulary = m.groups()
            rows.append((phrase_id, text, source, [g.strip() for g in glosses.split(",")]))
    if len(rows) != 90:
        raise SystemExit(f"FR-02: esperaba 90 frases, leí {len(rows)}")
    return rows


def cache_counters() -> dict[str, float]:
    with urllib.request.urlopen(METRICS, timeout=10) as response:
        data = json.load(response)
    counters = data.get("counters", {})
    return {k: v for k, v in counters.items() if k.startswith("rt_cache_total")}


async def glosses_of(text: str) -> tuple[list[str], int, str]:
    async with websockets.connect(URL) as ws:
        await ws.send(json.dumps({"type": "text", "text": text}, ensure_ascii=False))
        glosses: list[str] = []
        segments = 0
        types: list[str] = []
        while True:
            msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=90))
            kind = msg.get("type")
            if kind == "segment":
                segments = msg["total"]
                types.append(msg["segment"]["type"])
                glosses.extend(g["text"] for g in msg["segment"]["glosses"])
            elif kind == "done":
                return glosses, segments, "+".join(types)
            elif kind == "error":
                return [f"ERROR: {msg.get('message')}"], 0, "ERROR"
            elif kind == "capability":
                continue


def classify(expected: list[str], got: list[str]) -> str:
    """Qué diferencia hay entre la referencia y lo que salió."""
    if got == expected:
        return "exacta"
    if sorted(got) == sorted(expected):
        return "reordenada"
    missing = [g for g in expected if g not in got]
    extra = [g for g in got if g not in expected]
    if missing and not extra:
        return f"omite {','.join(missing)}"
    if extra and not missing:
        return f"agrega {','.join(extra)}"
    return f"omite {','.join(missing) or '-'} / agrega {','.join(extra) or '-'}"


async def measure(name: str, phrases: list[tuple[str, str, str, list[str]]]) -> int:
    print(f"===== {name}: {len(phrases)} frases =====")
    print(f"caché antes: {cache_counters()}\n")

    exact = 0
    got_total = 0
    expected_total = 0
    from_cache = 0

    for phrase_id, text, kind, expected in phrases:
        previous = cache_counters()
        got, segments, types = await glosses_of(text)
        after = cache_counters()
        source = "llm"
        for key in ('rt_cache_total{source="exact"}', 'rt_cache_total{source="semantic"}'):
            if after.get(key, 0) - previous.get(key, 0) > 0:
                source = key.split('"')[1]
        if source != "llm":
            from_cache += 1
        status = classify(expected, got)
        exact += status == "exacta"
        got_total += len(got)
        expected_total += len(expected)
        print(f"{phrase_id} [{kind:20}] {status:32} cache={source:9} segs={segments} tipos={types}")
        print(f"   frase    : {text}")
        print(f"   esperado : {', '.join(expected)}")
        print(f"   obtenido : {', '.join(got)}")
        print()

    print("=" * 78)
    print(f"{name} FRASES EXACTAS: {exact}/{len(phrases)} ({100 * exact / len(phrases):.1f} %)")
    print(f"{name} GLOSAS: {got_total}/{expected_total} ({100 * got_total / expected_total:.1f} %)")
    print(
        f"{name} CACHE: {from_cache}/{len(phrases)} frases resueltas sin el modelo"
        + ("  <- la caché no estaba vacía: el número no mide el glosado" if from_cache else "")
    )
    print("=" * 78)
    return 0


async def main() -> int:
    which = [arg.lower() for arg in sys.argv[1:]] or ["fr01"]
    for arg in which:
        if arg not in {"fr01", "fr02"}:
            raise SystemExit(f"conjunto desconocido: {arg} (usá fr01 o fr02)")
    for arg in which:
        await measure(arg.upper(), load_fr01() if arg == "fr01" else load_fr02())
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
