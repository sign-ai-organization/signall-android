#!/usr/bin/env python3
"""PR-LLM-01, PR-LLM-02 y PR-LLM-03 · Cliente del LLM.

Corre dentro del contenedor del backend:

    docker compose exec -T backend python - \
        < docs/plan-pruebas/arnes/llm.py

- LLM-01: tres modelos traducen las 30 frases de FR-01 y se comparan con las
  glosas de referencia.
- LLM-02: se revisan las 30 respuestas reales y las 10 fabricadas de CO-04 para
  ver que ninguna se lea mal (mayúsculas, sin tildes, separadas por comas, sin
  texto adicional).
- LLM-03: cada frase se traduce cinco veces y se mira si el modelo devuelve
  siempre las mismas glosas.
"""

from __future__ import annotations

import asyncio
import csv
import json
import os
from pathlib import Path

import asyncpg

from app.config.settings import Settings
from app.nlp.llm_client import PROMPT_TEMPLATE, LLMClient, _clean_gloss
from app.validation.catalog_repository import normalize_gloss

DATOS = Path(os.environ.get("DATOS", "/datos"))
EVIDENCIA = Path(os.environ.get("EVIDENCIA", "/evidencia"))

MODELOS = {
    "gpt-4o-mini (sistema)": {"model": "gpt-4o-mini", "base_url": ""},
    "gpt-4o": {"model": "gpt-4o", "base_url": ""},
    "qwen3:4b-instruct": {"model": "qwen3:4b-instruct", "base_url": "http://host.docker.internal:11434/v1"},
}


def cargar(nombre: str) -> list[list[str]]:
    return json.loads((DATOS / f"{nombre}.json").read_text(encoding="utf-8"))


def esperadas(celda: str) -> list[str]:
    if "ninguna glosa" in celda.lower():
        return []
    return [g.strip() for g in celda.split(",") if g.strip()]


async def crudo(cliente: LLMClient, texto: str) -> str:
    respuesta = await cliente._chat.ainvoke([("system", PROMPT_TEMPLATE), ("human", texto)])
    return respuesta.content


async def vocabulario(settings: Settings) -> tuple[dict[str, str], dict[str, str]]:
    """El catálogo en memoria que usa `_repair_glosses`, leído de la base."""
    pool = await asyncpg.create_pool(settings.database_url, min_size=1, max_size=1)
    try:
        filas = await pool.fetch("SELECT gloss, semantic_category FROM catalog")
    finally:
        await pool.close()
    vocabulario = {normalize_gloss(f["gloss"]): f["gloss"] for f in filas}
    categorias = {f["gloss"]: f["semantic_category"] for f in filas}
    return vocabulario, categorias


def main() -> None:
    EVIDENCIA.mkdir(parents=True, exist_ok=True)
    ajustes = Settings()
    vocabulario_mapa, categorias_mapa = asyncio.run(vocabulario(ajustes))
    frases = cargar("FR-01")
    fabricadas = cargar("CO-04")

    solo = os.environ.get("SOLO", "")

    async def correr() -> dict:
        resumen_01 = {}
        crudos_sistema: dict[str, str] = {}
        for nombre, configuracion in ({} if solo else MODELOS).items():
            cliente = LLMClient(
                api_key=ajustes.llm_api_key if configuracion["base_url"] == "" else "ollama",
                model=configuracion["model"],
                temperature=0.0,
                timeout_ms=ajustes.llm_timeout_ms,
                max_retries=1,
                base_url=configuracion["base_url"],
                vocabulary=vocabulario_mapa,
                categories=categorias_mapa,
            )
            aciertos = 0
            detalle = []
            for fila in frases:
                referencia = esperadas(fila[4])
                try:
                    contenido = await crudo(cliente, fila[1])
                    obtenidas = cliente._repair_glosses(cliente._parse_glosses(contenido), fila[1])
                except Exception as error:  # noqa: BLE001 - un modelo caído se reporta
                    detalle.append({"id": fila[0], "error": str(error)[:200]})
                    continue
                acierto = [g.upper() for g in obtenidas] == [g.upper() for g in referencia]
                aciertos += int(acierto)
                detalle.append(
                    {
                        "id": fila[0],
                        "frase": fila[1],
                        "referencia": referencia,
                        "crudo": contenido,
                        "obtenido": obtenidas,
                        "acierto": acierto,
                    }
                )
                if nombre.startswith("gpt-4o-mini"):
                    crudos_sistema[fila[0]] = contenido
            resumen_01[nombre] = {"modelo": configuracion["model"], "aciertos": aciertos, "total": len(frases), "detalle": detalle}

        # --- LLM-02: formato de las respuestas reales y de las fabricadas ---
        if solo and not crudos_sistema:
            # Reutiliza las respuestas crudas de la corrida completa.
            previo = json.loads((EVIDENCIA / "PR-LLM-01_E_20261110_01.json").read_text(encoding="utf-8"))
            for fila in previo["gpt-4o-mini (sistema)"]["detalle"]:
                if "crudo" in fila:
                    crudos_sistema[fila["id"]] = fila["crudo"]
        cliente_sistema = LLMClient(
            api_key=ajustes.llm_api_key,
            model=ajustes.llm_model,
            temperature=0.0,
            timeout_ms=ajustes.llm_timeout_ms,
            max_retries=1,
            base_url=ajustes.llm_base_url,
            vocabulary=vocabulario_mapa,
            categories=categorias_mapa,
        )
        problemas_reales = [
            {"id": fila[0], "crudo": crudos_sistema.get(fila[0], "")}
            for fila in frases
            if crudos_sistema.get(fila[0], "")
            and not formato_ok(cliente_sistema, crudos_sistema[fila[0]])
        ]
        fabricadas_detalle = []
        for fila in fabricadas:
            contenido = "" if "vacía" in fila[1].lower() else fila[2]
            obtenidas = cliente_sistema._repair_glosses(cliente_sistema._parse_glosses(contenido), contenido)
            referencia = esperadas(fila[3])
            acierto = [g.upper() for g in obtenidas] == [g.upper() for g in referencia]
            fabricadas_detalle.append(
                {
                    "id": fila[0],
                    "caso": fila[1],
                    "entrada": contenido,
                    "esperado": referencia,
                    "obtenido": obtenidas,
                    "acierto": acierto,
                }
            )

        # --- LLM-03: consistencia con cinco respuestas por frase ---
        cliente = LLMClient(
            api_key=ajustes.llm_api_key,
            model=ajustes.llm_model,
            temperature=0.0,
            timeout_ms=ajustes.llm_timeout_ms,
            max_retries=1,
            base_url=ajustes.llm_base_url,
            vocabulary=vocabulario_mapa,
            categories=categorias_mapa,
        )
        consistentes = 0
        detalle_03 = []
        for fila in ([] if solo == '02' else frases):
            respuestas = []
            for _ in range(5):
                respuestas.append(tuple(cliente._repair_glosses(cliente._parse_glosses(await crudo(cliente, fila[1])), fila[1])))
            iguales = len(set(respuestas)) == 1
            consistentes += int(iguales)
            detalle_03.append({"id": fila[0], "respuestas": [list(r) for r in respuestas], "iguales": iguales})

        return {
            "LLM-01": resumen_01,
            "LLM-02": {
                "reales_con_problemas": len(problemas_reales),
                "reales_total": len(frases),
                "problemas": problemas_reales,
                "fabricadas_aciertos": sum(int(f["acierto"]) for f in fabricadas_detalle),
                "fabricadas_total": len(fabricadas_detalle),
                "fabricadas": fabricadas_detalle,
            },
            "LLM-03": {"consistentes": consistentes, "total": len(frases), "detalle": detalle_03},
        }

    datos = asyncio.run(correr())

    for nombre in ("PR-LLM-01", "PR-LLM-02", "PR-LLM-03"):
        clave = nombre.replace("PR-", "")
        if solo and not datos[clave]:
            continue
        if solo == "02" and clave == "LLM-03":
            continue
        (EVIDENCIA / f"{nombre}_E_20261110_01.json").write_text(
            json.dumps(datos[clave], ensure_ascii=False, indent=1), encoding="utf-8"
        )

    with (EVIDENCIA / "PR-LLM-01_E_20261110_01.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["modelo", "aciertos", "total", "detalle"])
        for nombre, d in datos["LLM-01"].items():
            w.writerow([nombre, d["aciertos"], d["total"], json.dumps(d["detalle"], ensure_ascii=False)])

    with (EVIDENCIA / "PR-LLM-02_E_20261110_01.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["id", "caso", "entrada", "esperado", "obtenido", "acierto"])
        for fila in datos["LLM-02"]["fabricadas"]:
            w.writerow([fila["id"], fila["caso"], fila["entrada"], json.dumps(fila["esperado"], ensure_ascii=False), json.dumps(fila["obtenido"], ensure_ascii=False), fila["acierto"]])

    with (EVIDENCIA / "PR-LLM-03_E_20261110_01.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["id", "iguales", "respuestas"])
        for fila in datos["LLM-03"]["detalle"]:
            w.writerow([fila["id"], fila["iguales"], json.dumps(fila["respuestas"], ensure_ascii=False)])

    resumen = {
        "LLM-01": {k: {"aciertos": v["aciertos"], "total": v["total"]} for k, v in datos["LLM-01"].items()},
        "LLM-02": {k: v for k, v in datos["LLM-02"].items() if k in ("reales_con_problemas", "reales_total", "fabricadas_aciertos", "fabricadas_total")},
        "LLM-03": {"consistentes": datos["LLM-03"]["consistentes"], "total": datos["LLM-03"]["total"]},
    }
    print(json.dumps(resumen, ensure_ascii=False, indent=1))


def formato_ok(cliente: LLMClient, contenido: str) -> bool:
    """Mayúsculas, sin tildes, sin puntos y sin texto alrededor."""
    glosas = cliente._parse_glosses(contenido)
    if not glosas:
        return False
    for glosa in glosas:
        if glosa != glosa.upper():
            return False
        if any(c in glosa for c in ".,;:!?¿¡…"):
            return False
        if any(c in glosa for c in "áéíóúÁÉÍÓÚ"):
            return False
        if _clean_gloss(glosa) != glosa:
            return False
    return True


if __name__ == "__main__":
    main()
