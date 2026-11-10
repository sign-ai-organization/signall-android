#!/usr/bin/env python3
"""PR-LLM-01 · el modelo local, medido desde el host.

Ollama escucha en `127.0.0.1:11434` y el contenedor del backend no lo alcanza
(el cortafuegos filtra el puente de Docker), así que el tercer modelo de la
comparación se consulta desde el host con el mismo prompt, la misma temperatura
y la misma reparación contra el catálogo:

    PYTHONPATH=../sign-ai-backend ../tra03/venv/bin/python \
        docs/plan-pruebas/arnes/qwen01.py --datos /tmp/p1/datos --salida /tmp/p1/evidencias/LLM
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
from pathlib import Path

from app.config.settings import Settings
from app.nlp.llm_client import PROMPT_TEMPLATE, LLMClient
from app.validation.catalog_repository import normalize_gloss

import asyncpg

MODELO = "qwen3:4b-instruct"
BASE_URL = "http://127.0.0.1:11434/v1"


async def vocabulario(settings: Settings) -> tuple[dict[str, str], dict[str, str]]:
    pool = await asyncpg.create_pool(settings.database_url, min_size=1, max_size=1)
    try:
        filas = await pool.fetch("SELECT gloss, semantic_category FROM catalog")
    finally:
        await pool.close()
    return (
        {normalize_gloss(f["gloss"]): f["gloss"] for f in filas},
        {f["gloss"]: f["semantic_category"] for f in filas},
    )


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--datos", required=True, type=Path)
    ap.add_argument("--salida", required=True, type=Path)
    args = ap.parse_args()
    args.salida.mkdir(parents=True, exist_ok=True)

    ajustes = Settings()
    vocabulario_mapa, categorias_mapa = await vocabulario(ajustes)
    cliente = LLMClient(
        api_key="ollama",
        model=MODELO,
        temperature=0.0,
        timeout_ms=30000,
        max_retries=1,
        base_url=BASE_URL,
        vocabulary=vocabulario_mapa,
        categories=categorias_mapa,
    )

    frases = json.loads((args.datos / "FR-01.json").read_text(encoding="utf-8"))
    aciertos = 0
    detalle = []
    for fila in frases:
        referencia = [g.strip() for g in fila[4].split(",") if g.strip()]
        try:
            respuesta = await cliente._chat.ainvoke([("system", PROMPT_TEMPLATE), ("human", fila[1])])
            obtenidas = cliente._repair_glosses(cliente._parse_glosses(respuesta.content), fila[1])
        except Exception as error:  # noqa: BLE001
            detalle.append({"id": fila[0], "error": str(error)[:200]})
            continue
        acierto = [g.upper() for g in obtenidas] == [g.upper() for g in referencia]
        aciertos += int(acierto)
        detalle.append(
            {"id": fila[0], "frase": fila[1], "referencia": referencia,
             "crudo": respuesta.content, "obtenido": obtenidas, "acierto": acierto}
        )

    resumen = {"modelo": MODELO, "base_url": BASE_URL, "aciertos": aciertos, "total": len(frases), "detalle": detalle}
    (args.salida / "PR-LLM-01-qwen_E_20261110_01.json").write_text(
        json.dumps(resumen, ensure_ascii=False, indent=1), encoding="utf-8"
    )
    print(json.dumps({"modelo": MODELO, "aciertos": aciertos, "total": len(frases)}))


if __name__ == "__main__":
    asyncio.run(main())
