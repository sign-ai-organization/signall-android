#!/usr/bin/env python3
"""PR-CAC-01, PR-CAC-02 y PR-CAC-03 · Caché semántica.

Corre dentro del contenedor del backend:

    docker compose exec -T backend python - \
        < docs/plan-pruebas/arnes/cac.py

La unidad de la caché es la **oración**, no la frase: el backend segmenta antes
de consultar. Por eso cada frase de FR-01 se guarda oración por oración y cada
variante se compara oración por oración; en las frases de varias oraciones solo
cuentan las que cambiaron, como pide la ficha de PR-CAC-03.

El cálculo replica `SemanticCache`: normalización, sinónimos del catálogo,
vectores normalizados a longitud 1, distancia = 1 - producto punto y partición
por tipo de frase.
"""

from __future__ import annotations

import asyncio
import csv
import json
import os
import time
from pathlib import Path

import asyncpg
import numpy as np

from app.config.sentence_segmenter import SentenceSegmenterSettings
from app.config.settings import Settings
from app.nlp.embedding_model import DEFAULT_MODEL_NAME, EmbeddingModel
from app.nlp.gloss_analyzer import GlossAnalyzer
from app.nlp.segmenter.sentence_segmenter import SentenceSegmenter
from app.nlp.text_normalizer import normalize, substitute_synonyms

DATOS = Path(os.environ.get("DATOS", "/datos"))
EVIDENCIA = Path(os.environ.get("EVIDENCIA", "/evidencia"))

MODELOS = {
    "MiniLM (sistema)": DEFAULT_MODEL_NAME,
    "mpnet": "paraphrase-multilingual-mpnet-base-v2",
    "e5-small": "multilingual-e5-small",
}


def cargar(nombre: str) -> list[list[str]]:
    return json.loads((DATOS / f"{nombre}.json").read_text(encoding="utf-8"))


async def sinonimos(settings: Settings) -> dict[str, str]:
    pool = await asyncpg.create_pool(settings.database_url, min_size=1, max_size=1)
    try:
        filas = await pool.fetch(
            "SELECT cs.synonym, c.gloss FROM catalog_synonym cs JOIN catalog c ON c.id_sign = cs.id_sign"
        )
        return {normalize(f["synonym"]): normalize(f["gloss"]) for f in filas}
    finally:
        await pool.close()


def unidad(vector) -> np.ndarray:
    arreglo = np.array(vector, dtype=np.float32)
    norma = float(np.linalg.norm(arreglo))
    return arreglo if norma <= 1e-12 else arreglo / norma


def clave(texto: str, sinonimos_mapa: dict[str, str]) -> str:
    return substitute_synonyms(normalize(texto), sinonimos_mapa)


def evaluar(modelo, oraciones_guardadas, variantes, sinonimos_mapa, segmentador, analizador) -> dict:
    """Distancias de cada oración cambiada a la oración guardada más cercana."""
    inicio = time.perf_counter()
    guardadas = [
        {**o, "vector": unidad(modelo.embed(clave(o["texto"], sinonimos_mapa)))}
        for o in oraciones_guardadas
    ]
    ms_embed = 1000 * (time.perf_counter() - inicio) / max(1, len(guardadas))

    por_tipo: dict[str, list[dict]] = {}
    exactas: dict[str, dict] = {}
    for oracion in guardadas:
        por_tipo.setdefault(oracion["tipo"], []).append(oracion)
        exactas[clave(oracion["texto"], sinonimos_mapa)] = oracion

    resultados = []
    for variante in variantes:
        originales = segmentador.segment(variante["original"])
        cambiadas = segmentador.segment(variante["texto"])
        if len(originales) != len(cambiadas):
            # Si la variante cambia el número de oraciones no hay pareo posible:
            # se compara la frase entera como una sola unidad.
            originales, cambiadas = [variante["original"]], [variante["texto"]]
        for indice, (original, cambiada) in enumerate(zip(originales, cambiadas)):
            # Una oración que no cambió no se cuenta (frases de varias
            # oraciones); una que cambió solo de forma sí, aunque su clave
            # normalizada coincida: la caché la resuelve por la vía exacta.
            if original.strip() == cambiada.strip():
                continue
            tipo = analizador.analyze(cambiada).category.value
            candidatas = por_tipo.get(tipo, [])
            if not candidatas:
                resultados.append(
                    {"id": variante["id"], "oracion": indice, "texto": cambiada, "tipo": tipo,
                     "clase": variante["clase"], "variante": variante["variante"],
                     "distancia": None, "mas_cercana": None, "propia": False}
                )
                continue
            llave = clave(cambiada, sinonimos_mapa)
            if llave in exactas:
                distancia, cercana = 0.0, exactas[llave]
            else:
                vector = unidad(modelo.embed(llave))
                distancia, cercana = min(
                    ((float(1 - np.dot(vector, o["vector"])), o) for o in candidatas),
                    key=lambda par: par[0],
                )
            resultados.append(
                {
                    "id": variante["id"],
                    "oracion": indice,
                    "texto": cambiada,
                    "tipo": tipo,
                    "clase": variante["clase"],
                    "variante": variante["variante"],
                    "distancia": round(distancia, 4),
                    "mas_cercana": f"{cercana['frase']}#{cercana['oracion']}",
                    "propia": cercana["frase"] == variante["frase"] and cercana["oracion"] == indice,
                }
            )
    return {"resultados": resultados, "ms_por_oracion": round(ms_embed, 2)}


def curva(resultados: list[dict], umbrales: list[float]) -> list[dict]:
    filas = []
    for umbral in umbrales:
        parafrasis = [r for r in resultados if r["clase"] == "parafrasis"]
        trampas = [r for r in resultados if r["clase"] == "trampa"]
        aciertos = sum(1 for r in parafrasis if r["propia"] and (9 if r["distancia"] is None else r["distancia"]) <= umbral)
        falsos = sum(1 for r in trampas if (9 if r["distancia"] is None else r["distancia"]) <= umbral)
        filas.append(
            {
                "umbral": round(umbral, 3),
                "parafrasis_aciertos": aciertos,
                "parafrasis_total": len(parafrasis),
                "trampas_falsos": falsos,
                "trampas_total": len(trampas),
            }
        )
    return filas


def mejor_umbral(tabla: list[dict]) -> dict:
    sin_trampas = [f for f in tabla if f["trampas_falsos"] == 0]
    if not sin_trampas:
        return tabla[0]
    return max(sin_trampas, key=lambda f: (f["parafrasis_aciertos"], f["umbral"]))


def main() -> None:
    EVIDENCIA.mkdir(parents=True, exist_ok=True)
    ajustes = Settings()
    mapa_sinonimos = asyncio.run(sinonimos(ajustes))
    analizador = GlossAnalyzer()
    segmentador = SentenceSegmenter(SentenceSegmenterSettings())

    guardadas = []
    for fila in cargar("FR-01"):
        for indice, oracion in enumerate(segmentador.segment(fila[1])):
            guardadas.append(
                {
                    "frase": fila[0],
                    "oracion": indice,
                    "texto": oracion,
                    "tipo": analizador.analyze(oracion).category.value,
                }
            )

    variantes = []
    for clase, nombre in (("parafrasis", "FR-03"), ("trampa", "FR-04")):
        for fila in cargar(nombre):
            # FR-03 y FR-04 traen la frase original en la columna 3 y la
            # variante en la 4.
            variantes.append(
                {
                    "id": fila[0],
                    "frase": fila[1],
                    "original": fila[2],
                    "texto": fila[3],
                    "clase": clase,
                    "variante": fila[4] if len(fila) > 4 else "",
                }
            )

    umbrales = [round(0.01 * i, 2) for i in range(1, 21)]
    resumen_01 = {}
    evaluaciones = {}
    for nombre, ruta in MODELOS.items():
        try:
            modelo = EmbeddingModel(ruta)
        except Exception as error:  # noqa: BLE001 - un modelo ausente no bloquea la prueba
            resumen_01[nombre] = {"disponible": False, "motivo": str(error)[:200]}
            continue
        datos = evaluar(modelo, guardadas, variantes, mapa_sinonimos, segmentador, analizador)
        evaluaciones[nombre] = datos
        tabla = curva(datos["resultados"], umbrales)
        mejor = mejor_umbral(tabla)
        resumen_01[nombre] = {
            "disponible": True,
            "modelo": ruta,
            "mejor_umbral": mejor["umbral"],
            "parafrasis_aciertos": mejor["parafrasis_aciertos"],
            "parafrasis_total": mejor["parafrasis_total"],
            "trampas_falsos": mejor["trampas_falsos"],
            "trampas_total": mejor["trampas_total"],
            "ms_por_oracion": datos["ms_por_oracion"],
            "curva": tabla,
        }

    sistema = "MiniLM (sistema)"
    resultados_sistema = evaluaciones[sistema]["resultados"]
    umbral_sistema = ajustes.semantic_cache_similarity_threshold
    resumen_02 = {
        "modelo": MODELOS[sistema],
        "umbral_del_sistema": umbral_sistema,
        "curva": curva(resultados_sistema, umbrales),
    }
    parafrasis = [r for r in resultados_sistema if r["clase"] == "parafrasis"]
    trampas = [r for r in resultados_sistema if r["clase"] == "trampa"]
    por_variante: dict[str, list[int]] = {}
    for resultado in parafrasis:
        acierto = resultado["propia"] and (9 if resultado["distancia"] is None else resultado["distancia"]) <= umbral_sistema
        por_variante.setdefault(resultado["variante"], []).append(int(acierto))
    resumen_03 = {
        "modelo": MODELOS[sistema],
        "umbral": umbral_sistema,
        "aciertos": sum(1 for r in parafrasis if r["propia"] and (9 if r["distancia"] is None else r["distancia"]) <= umbral_sistema),
        "total": len(parafrasis),
        "por_tipo_de_variante": {k: f"{sum(v)}/{len(v)}" for k, v in por_variante.items()},
        "falsos_aciertos": sum(1 for r in trampas if (9 if r["distancia"] is None else r["distancia"]) <= umbral_sistema),
        "trampas_total": len(trampas),
        "detalle": [
            {
                "id": r["id"],
                "oracion": r["oracion"],
                "texto": r["texto"],
                "variante": r["variante"],
                "distancia": r["distancia"],
                "mas_cercana": r["mas_cercana"],
                "acierto": r["propia"] and (9 if r["distancia"] is None else r["distancia"]) <= umbral_sistema,
            }
            for r in parafrasis
        ],
        "detalle_trampas": [
            {"id": r["id"], "texto": r["texto"], "distancia": r["distancia"], "mas_cercana": r["mas_cercana"]}
            for r in trampas
        ],
    }

    por_trampa: dict[str, list[int]] = {}
    for resultado in trampas:
        falso = (9 if resultado["distancia"] is None else resultado["distancia"]) <= umbral_sistema
        por_trampa.setdefault(resultado["variante"], []).append(int(falso))
    resumen_04 = {
        "modelo": MODELOS[sistema],
        "umbral": umbral_sistema,
        "falsos_aciertos": sum(1 for r in trampas if (9 if r["distancia"] is None else r["distancia"]) <= umbral_sistema),
        "trampas_total": len(trampas),
        "por_tipo_de_trampa": {k: f"{sum(v)}/{len(v)}" for k, v in por_trampa.items()},
        "detalle": [
            {
                "id": r["id"],
                "texto": r["texto"],
                "tipo": r["variante"],
                "distancia": r["distancia"],
                "mas_cercana": r["mas_cercana"],
                "falso_acierto": (9 if r["distancia"] is None else r["distancia"]) <= umbral_sistema,
            }
            for r in trampas
        ],
    }

    for nombre, datos in (
        ("PR-CAC-01", resumen_01),
        ("PR-CAC-02", resumen_02),
        ("PR-CAC-03", resumen_03),
        ("PR-CAC-04", resumen_04),
    ):
        (EVIDENCIA / f"{nombre}_E_20261110_01.json").write_text(
            json.dumps(datos, ensure_ascii=False, indent=1), encoding="utf-8"
        )
        with (EVIDENCIA / f"{nombre}_E_20261110_01.csv").open("w", newline="", encoding="utf-8") as f:
            w = csv.writer(f, delimiter=";")
            if nombre == "PR-CAC-01":
                w.writerow(["modelo", "disponible", "mejor_umbral", "parafrasis", "trampas", "ms_por_oracion"])
                for clave_modelo, d in datos.items():
                    w.writerow([
                        clave_modelo,
                        d.get("disponible"),
                        d.get("mejor_umbral"),
                        f"{d.get('parafrasis_aciertos')}/{d.get('parafrasis_total')}" if d.get("disponible") else d.get("motivo"),
                        f"{d.get('trampas_falsos')}/{d.get('trampas_total')}" if d.get("disponible") else "",
                        d.get("ms_por_oracion"),
                    ])
            elif nombre == "PR-CAC-02":
                w.writerow(["umbral", "parafrasis_aciertos", "parafrasis_total", "trampas_falsos", "trampas_total"])
                for fila in datos["curva"]:
                    w.writerow([fila["umbral"], fila["parafrasis_aciertos"], fila["parafrasis_total"], fila["trampas_falsos"], fila["trampas_total"]])
            elif nombre == "PR-CAC-04":
                w.writerow(["id", "texto", "tipo", "distancia", "mas_cercana", "falso_acierto"])
                for fila in datos["detalle"]:
                    w.writerow([fila["id"], fila["texto"], fila["tipo"], fila["distancia"], fila["mas_cercana"], fila["falso_acierto"]])
            else:
                w.writerow(["id", "oracion", "texto", "variante", "distancia", "mas_cercana", "acierto"])
                for fila in datos["detalle"]:
                    w.writerow([fila["id"], fila["oracion"], fila["texto"], fila["variante"], fila["distancia"], fila["mas_cercana"], fila["acierto"]])
    print(json.dumps({
        "CAC-01": resumen_01,
        "CAC-02": {k: v for k, v in resumen_02.items() if k != "curva"},
        "CAC-03": {k: v for k, v in resumen_03.items() if k not in ("detalle", "detalle_trampas")},
        "CAC-04": {k: v for k, v in resumen_04.items() if k != "detalle"},
    }, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
