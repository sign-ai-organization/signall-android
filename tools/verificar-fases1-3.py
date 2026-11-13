#!/usr/bin/env python3
"""Verifica de punta a punta las fases 1 y 3 del plan de implementación 2.

El escenario son seis cosas que tienen que seguir siendo verdad después de un
cambio, y ninguna se puede mirar en un solo lado:

    1. `catalog.gloss` es única (PR-VAL-02) y la base rechaza un duplicado.
    2. La glosa 29 dice `JUICIOSO`, no `JUCIOSO`.
    3. El catálogo que cargó el backend son 50 glosas.
    4. `EMBEDDING_MODEL_NAME` está publicado en `/metrics.json` (PR-CAC-01).
    5. Una pregunta de contenido **sin** signo de interrogación se clasifica
       como `CONTENT_QUESTION`, y una declaración que empieza con relativo
       (`cuando`, `donde`, `que`) **no** (PR-HUE-03).
    6. La caché semántica se sirve de la base nueva: una frase nueva se le
       pide al LLM (`rt_cache_total{source="miss"}`) y la misma frase repetida
       sale del caché (`exact` o `semantic`). Se mira el contador, no el reloj:
       una frase ya cacheada tarda lo mismo las dos veces.

La base se consulta con `psql` y `DATABASE_URL` de `.envJulian`, así que el
script corre desde la raíz del repo Android con el backend y el STT levantados.

Uso:

    python3 tools/verificar-fases1-3.py
    python3 tools/verificar-fases1-3.py --no-db        # sin psql ni Neon
    python3 tools/verificar-fases1-3.py --uri ws://127.0.0.1:8080/ws/translate

Sale con código 1 si algo falla, para que se pueda encadenar en un `&&`.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

import websockets

RAIZ = Path(__file__).resolve().parent.parent
CATALOGO_GLOSAS = 50
GLOSA_29 = "JUICIOSO"

# (frase, tipo esperado, glosa que tiene que aparecer o None)
FRASES = [
    # Fase 1: la errata del dataset ya no está en el catálogo.
    ("Mi hermano es juicioso.", "AFFIRMATIVE", "JUICIOSO"),
    # Fase 3: pregunta de contenido sin "?" (lo que entrega Whisper).
    ("cuál es su nombre", "CONTENT_QUESTION", None),
    ("dónde vive", "CONTENT_QUESTION", None),
    ("qué documento trajo", "CONTENT_QUESTION", None),
    # Fase 3: los relativos sin tilde no son preguntas.
    ("la dirección coincide con la que tenemos registrada", "AFFIRMATIVE", None),
    ("cuando termine el trámite le avisaremos por correo", "AFFIRMATIVE", None),
    # Sin regresión: la pregunta de sí/no con "?" sigue igual.
    ("¿Estás bien?", "YES_NO_QUESTION", None),
]

resultados: list[tuple[bool, str, str]] = []


def revisar(ok: bool, nombre: str, detalle: str = "") -> bool:
    resultados.append((ok, nombre, detalle))
    marca = "ok  " if ok else "FALLA"
    print(f"  {marca} {nombre}" + (f" — {detalle}" if detalle else ""))
    return ok


def http_json(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=10) as respuesta:
        return json.load(respuesta)


def database_url() -> str | None:
    if os.environ.get("DATABASE_URL"):
        return os.environ["DATABASE_URL"]
    for linea in (RAIZ / ".envJulian").read_text(encoding="utf-8").splitlines():
        if linea.startswith("DATABASE_URL="):
            return linea.split("=", 1)[1].strip().strip('"').strip("'")
    return None


def consultar(url: str, sql: str) -> tuple[bool, str]:
    """Corre un `-tAc` y devuelve (salió bien, salida o error)."""
    proceso = subprocess.run(
        ["psql", url, "-v", "ON_ERROR_STOP=1", "-tAc", sql],
        capture_output=True,
        text=True,
        timeout=60,
    )
    salida = (proceso.stdout or proceso.stderr).strip()
    return proceso.returncode == 0, salida


def verificar_base(url: str) -> None:
    print("\nBase de datos (Neon)")

    ok, salida = consultar(
        url,
        "select count(*) from pg_constraint where conname = 'uq_catalog_gloss';",
    )
    revisar(ok and salida == "1", "existe uq_catalog_gloss (UNIQUE gloss)", salida)

    ok, salida = consultar(url, "select gloss from catalog where id_sign = 29;")
    revisar(ok and salida == GLOSA_29, "la glosa 29 dice JUICIOSO", salida)

    ok, salida = consultar(url, "select count(*) from catalog;")
    revisar(ok and salida == str(CATALOGO_GLOSAS), "el catálogo tiene 50 filas", salida)

    ok, salida = consultar(url, "select count(*) from catalog where gloss <> upper(gloss);")
    revisar(ok and salida == "0", "ninguna glosa quedó fuera del canónico", salida)

    # El duplicado se intenta de verdad. Un INSERT que viola la restricción no
    # deja fila, así que no hace falta deshacer nada: lo que se mide es que la
    # base lo rechace.
    ok, salida = consultar(
        url,
        "insert into catalog (id_sign, gloss, semantic_category, glb_animation_path) "
        "values (99999, 'HERMANO', 'sustantivo', 'signs/9999_0000_0000.glb');",
    )
    rechazado = not ok and "uq_catalog_gloss" in salida
    revisar(rechazado, "la base rechaza una glosa repetida", salida.splitlines()[0] if salida else "")


def verificar_backend(base: str) -> None:
    print("\nBackend")

    salud = http_json(f"{base}/health")
    revisar(salud.get("status") == "ok", "el backend responde /health", json.dumps(salud))
    revisar(
        salud.get("catalog_glosses") == CATALOGO_GLOSAS,
        "cargó las 50 glosas del catálogo",
        str(salud.get("catalog_glosses")),
    )
    revisar(
        salud.get("cache_loaded") is True,
        "la caché semántica está cargada",
        f"{salud.get('cache_rows')} filas",
    )

    metricas = http_json(f"{base}/metrics.json")
    modelo = metricas.get("config", {}).get("embedding_model", "")
    revisar(bool(modelo), "publica el modelo de embeddings", modelo)


async def una_frase(uri: str, texto: str) -> tuple[str, list[str], float]:
    """Manda una frase y devuelve (tipo, glosas, ms hasta el cierre del turno)."""
    arranque = time.perf_counter()
    tipo = ""
    glosas: list[str] = []
    async with websockets.connect(uri, open_timeout=15) as ws:
        await ws.send(json.dumps({"type": "text", "text": texto}))
        while True:
            crudo = await asyncio.wait_for(ws.recv(), timeout=60)
            if isinstance(crudo, bytes):
                continue
            mensaje = json.loads(crudo)
            if mensaje.get("type") == "error":
                raise RuntimeError(mensaje.get("message", "error sin mensaje"))
            if mensaje.get("type") == "segment":
                segmento = mensaje["segment"]
                tipo = segmento.get("type", "")
                glosas += [
                    glosa.get("text", "") if isinstance(glosa, dict) else str(glosa)
                    for glosa in segmento.get("glosses", [])
                ]
            if mensaje.get("type") == "done":
                break
    return tipo, glosas, (time.perf_counter() - arranque) * 1000


def contadores(base: str) -> dict[str, float]:
    return http_json(f"{base}/metrics.json").get("counters", {})


def origen(contadores: dict[str, float], etiqueta: str) -> float:
    return contadores.get(f'rt_cache_total{{source="{etiqueta}"}}', 0.0)


def verificar_cache(base: str, uri: str) -> None:
    """Una frase nueva se pide al LLM; la misma frase repetida sale del caché.

    Se mide con el contador `rt_cache_total{source}` del backend y no con el
    reloj: si la frase ya estaba en la caché de una corrida anterior, los dos
    envíos tardan lo mismo y la comparación de tiempos no dice nada.
    """
    frases = [
        "El certificado se entrega el mismo día del trámite.",
        "Necesito actualizar mis datos personales en el registro.",
        "El horario de atención es de ocho a cuatro.",
    ]
    for frase in frases:
        antes = contadores(base)
        primera = asyncio.run(una_frase(uri, frase))
        despues = contadores(base)
        if origen(despues, "miss") - origen(antes, "miss") != 1:
            continue  # ya estaba cacheada: no sirve para medir en frío
        segunda = asyncio.run(una_frase(uri, frase))
        final = contadores(base)
        aciertos = (origen(final, "exact") - origen(despues, "exact")) + (
            origen(final, "semantic") - origen(despues, "semantic")
        )
        revisar(
            segunda[0] == primera[0] and segunda[1] == primera[1],
            "la frase repetida devuelve lo mismo",
            f"{segunda[0]}: {', '.join(segunda[1]) or '(ninguna)'}",
        )
        revisar(
            aciertos == 1,
            "la frase repetida sale del caché",
            f"{primera[2]:.0f} ms (LLM) contra {segunda[2]:.0f} ms (caché)",
        )
        return
    revisar(False, "la frase repetida sale del caché", "las tres frases de prueba ya estaban cacheadas")


def verificar_websocket(uri: str, base: str) -> None:
    print(f"\nWebSocket {uri}")

    for texto, tipo_esperado, glosa_esperada in FRASES:
        try:
            tipo, glosas, _ = asyncio.run(una_frase(uri, texto))
        except Exception as error:  # noqa: BLE001 — el fallo se reporta, no se propaga
            revisar(False, f"{texto!r} -> {tipo_esperado}", f"excepción: {error}")
            continue
        ok = tipo == tipo_esperado
        if glosa_esperada:
            ok = ok and glosa_esperada in glosas
        revisar(ok, f"{texto!r} -> {tipo_esperado}", f"{tipo}: {', '.join(glosas) or '(ninguna)'}")

    print("\nCaché semántica")
    verificar_cache(base, uri)


def main() -> int:
    parser = argparse.ArgumentParser(description="Verifica las fases 1 y 3 de punta a punta.")
    parser.add_argument(
        "--uri",
        default=os.environ.get("SIGNALL_WS", "ws://127.0.0.1:8080/ws/translate"),
        help="WebSocket del backend (default: ws://127.0.0.1:8080/ws/translate)",
    )
    parser.add_argument("--no-db", action="store_true", help="Salta la sección de base de datos.")
    argumentos = parser.parse_args()

    base = "http://" + argumentos.uri.split("//", 1)[1].split("/ws/")[0]

    if argumentos.no_db:
        print("\nBase de datos: saltada (--no-db)")
    else:
        url = database_url()
        if not url:
            revisar(False, "DATABASE_URL disponible", "no está en el entorno ni en .envJulian")
        else:
            verificar_base(url)

    verificar_backend(base)
    verificar_websocket(argumentos.uri, base)

    fallas = [nombre for ok, nombre, _ in resultados if not ok]
    print(f"\n{len(resultados) - len(fallas)}/{len(resultados)} comprobaciones pasaron")
    if fallas:
        print("Fallaron: " + "; ".join(fallas))
        return 1
    print("Todo en orden.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
