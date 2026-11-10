#!/usr/bin/env python3
"""PR-SEG-01 y PR-SEG-03 · Exactitud y alternativas del segmentador.

Corre dentro del contenedor del backend (necesita spaCy y el paquete `app`):

    docker compose exec -T backend python - \
        < docs/plan-pruebas/arnes/seg01.py

Lee los conjuntos extraídos del Anexo C y escribe la evidencia en $EVIDENCIA.

La división esperada de CO-01 trae tres marcas: `‖` corte obligatorio, `|` corte
opcional y `[texto]` muletilla que se puede pegar o descartar. Por eso cada caso
se compara contra todas sus variantes válidas, no contra una sola.
"""

from __future__ import annotations

import csv
import itertools
import json
import os
import re
import time
from pathlib import Path

from app.config.sentence_segmenter import SentenceSegmenterSettings
from app.nlp.segmenter._discourse_markers import split_by_markers
from app.nlp.segmenter._punctuation import split_by_punctuation
from app.nlp.segmenter.sentence_segmenter import SentenceSegmenter

DATOS = Path(os.environ.get("DATOS", "/datos"))
EVIDENCIA = Path(os.environ.get("EVIDENCIA", "/evidencia"))


def cargar(nombre: str) -> list[list[str]]:
    return json.loads((DATOS / f"{nombre}.json").read_text(encoding="utf-8"))


def texto_real(celda: str) -> str:
    """Los dos casos límite describen la entrada en vez de escribirla."""
    if celda.strip().lower().startswith("(texto vacío"):
        return ""
    if celda.strip().lower().startswith("(solo espacios"):
        return "   "
    return celda


def normalizar(oraciones: list[str]) -> list[str]:
    return [" ".join(o.split()) for o in oraciones if o.strip()]


def variantes_esperadas(celda: str) -> list[list[str]]:
    """Todas las divisiones válidas de una celda de CO-01."""
    if celda.strip().lower().startswith("sin fragmentos") or not celda.strip():
        return [[]]
    obligatorias = [parte.strip() for parte in celda.split("‖")]
    variantes: list[list[str]] = [[]]
    for oracion in obligatorias:
        if not oracion:
            continue
        # Cortes opcionales: se parte por `|` y se decide si se corta o no.
        piezas = [p.strip() for p in oracion.split("|") if p.strip()]
        opciones: list[list[str]] = []
        for corte in range(2 ** max(0, len(piezas) - 1)):
            actual = [piezas[0]]
            for i, pieza in enumerate(piezas[1:]):
                if corte & (1 << i):
                    actual.append(pieza)
                else:
                    actual[-1] = f"{actual[-1]} {pieza}"
            opciones.append(actual)
        # Muletillas entre corchetes: se pueden quedar o desaparecer.
        expandidas: list[list[str]] = []
        for opcion in opciones:
            con = [re.sub(r"[\[\]]", "", o).strip() for o in opcion]
            sin = [re.sub(r"\[[^\]]*\]", "", o).strip() for o in opcion]
            sin = [o for o in sin if o]
            expandidas.append(con)
            if sin != con:
                expandidas.append(sin)
        variantes = [v + e for v in variantes for e in expandidas]
        if len(variantes) > 64:
            variantes = variantes[:64]
    return [normalizar(v) for v in variantes]


def fronteras(oraciones: list[str]) -> set[int]:
    acumulado = ""
    marcas = set()
    for oracion in oraciones[:-1]:
        acumulado = f"{acumulado} {oracion}".strip()
        marcas.add(len(acumulado))
    return marcas


def main() -> None:
    EVIDENCIA.mkdir(parents=True, exist_ok=True)
    config = SentenceSegmenterSettings()
    sistema = SentenceSegmenter(config)
    # El modelo del sistema excluye el parser; para la alternativa "spaCy
    # sents" hace falta un pipeline con reconocedor de oraciones.
    import spacy

    nlp_oraciones = spacy.load(config.spacy_model, exclude=["ner", "lemmatizer"])
    casos = cargar("CO-01")

    # --- PR-SEG-01: el segmentador del sistema sobre los 200 casos ---
    filas = []
    grupos: dict[str, list[int]] = {}
    obtenidas_total: set[tuple[str, int]] = set()
    obligatorias_total: set[tuple[str, int]] = set()
    opcionales_total: set[tuple[str, int]] = set()
    for caso in casos:
        identificador, grupo, texto, esperado_txt = caso[:4]
        borde = caso[4] if len(caso) > 4 else ""
        variantes = variantes_esperadas(esperado_txt)
        obtenido = normalizar(sistema.segment(texto_real(texto))) if texto_real(texto).strip() else []
        exacto = any(obtenido == v for v in variantes)
        filas.append(
            {
                "id": identificador,
                "grupo": grupo,
                "texto": texto,
                "obtenido": json.dumps(obtenido, ensure_ascii=False),
                "esperado": json.dumps(variantes[0], ensure_ascii=False),
                "exacto": exacto,
                "borde": borde,
            }
        )
        grupos.setdefault(grupo, []).append(int(exacto))
        obtenidas_total |= {(identificador, f) for f in fronteras(obtenido)}
        conjuntos = [fronteras(v) for v in variantes]
        obligatorias_total |= {(identificador, f) for f in set.intersection(*conjuntos)}
        opcionales_total |= {(identificador, f) for f in set.union(*conjuntos)} - {
            (identificador, f) for f in set.intersection(*conjuntos)
        }

    exactos = sum(int(f["exacto"]) for f in filas)
    limite = [f for f in filas if int(f["id"].split("-")[-1]) > 191]
    no_limite = [f for f in filas if int(f["id"].split("-")[-1]) <= 191]
    aceptables = obligatorias_total | opcionales_total
    correctas = obtenidas_total & aceptables
    resumen_seg01 = {
        "casos": len(filas),
        "exactos": exactos,
        "porcentaje": round(100 * exactos / len(filas), 1),
        "por_grupo": {g: f"{sum(v)}/{len(v)}" for g, v in grupos.items()},
        "limite": f"{sum(int(f['exacto']) for f in limite)}/{len(limite)}",
        "no_limite": f"{sum(int(f['exacto']) for f in no_limite)}/{len(no_limite)}",
        "cortes": {
            "obtenidos": len(obtenidas_total),
            "obligatorios": len(obligatorias_total),
            "correctos": len(correctas),
            "precision": round(len(correctas) / len(obtenidas_total), 3) if obtenidas_total else 0,
            "cobertura": round(len(obtenidas_total & obligatorias_total) / len(obligatorias_total), 3)
            if obligatorias_total
            else 0,
        },
    }

    # --- PR-SEG-03: las cuatro alternativas sobre los 191 casos de uso corriente ---
    corrientes = [c for c in casos if int(c[0].split("-")[-1]) <= 191]
    metodos = {
        "hibrido_ligero (sistema)": lambda t: sistema.segment(t),
        "solo_puntuacion": lambda t: split_by_punctuation(t, config),
        "puntuacion_marcadores": lambda t: [
            oracion
            for fragmento in split_by_markers(t, config)
            for oracion in split_by_punctuation(fragmento, config)
        ],
        "spacy_sents_md": lambda t: [s.text for s in nlp_oraciones(t).sents],
    }
    resumen_seg03 = {}
    for nombre, funcion in metodos.items():
        inicio = time.perf_counter()
        aciertos = 0
        grupos_m: dict[str, list[int]] = {}
        borde_ok = 0
        borde_total = 0
        for caso in corrientes:
            variantes = variantes_esperadas(caso[3])
            obtenido = normalizar(funcion(texto_real(caso[2]))) if texto_real(caso[2]).strip() else []
            exacto = any(obtenido == v for v in variantes)
            aciertos += int(exacto)
            grupos_m.setdefault(caso[1], []).append(int(exacto))
            if caso[4].strip().lower() == "sí":
                borde_total += 1
                borde_ok += int(exacto)
        ms = 1000 * (time.perf_counter() - inicio) / len(corrientes)
        resumen_seg03[nombre] = {
            "casos": len(corrientes),
            "exactos": aciertos,
            "porcentaje": round(100 * aciertos / len(corrientes), 1),
            "por_grupo": {g: f"{sum(v)}/{len(v)}" for g, v in grupos_m.items()},
            "ms_por_texto": round(ms, 2),
            "borde": f"{borde_ok}/{borde_total}" if borde_total else "-",
        }

    # --- AU-05: divisiones de las transcripciones, si están disponibles ---
    ruta_transcripciones = DATOS / "au05_transcripciones.json"
    au05 = "-"
    if ruta_transcripciones.exists():
        datos = json.loads(ruta_transcripciones.read_text(encoding="utf-8"))
        ok = 0
        detalle = []
        for fila in datos:
            obtenido = normalizar(sistema.segment(fila["texto"]))
            esperado = normalizar([fila["oracion1"]] + ([fila["oracion2"]] if fila.get("oracion2") else []))
            acierto = obtenido == esperado
            ok += int(acierto)
            detalle.append(
                {"id": fila["id"], "texto": fila["texto"], "obtenido": obtenido, "esperado": esperado, "exacto": acierto}
            )
        au05 = f"{ok}/{len(datos)}"
        resumen_seg01["au05_detalle"] = detalle
    resumen_seg01["au05"] = au05

    with (EVIDENCIA / "PR-SEG-01_E_20261110_01.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(
            f, fieldnames=["id", "grupo", "texto", "obtenido", "esperado", "exacto", "borde"], delimiter=";"
        )
        w.writeheader()
        w.writerows(filas)
    (EVIDENCIA / "PR-SEG-01_E_20261110_01.json").write_text(
        json.dumps(resumen_seg01, ensure_ascii=False, indent=1), encoding="utf-8"
    )

    with (EVIDENCIA / "PR-SEG-03_E_20261110_01.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["metodo", "casos", "exactos", "porcentaje", "por_grupo", "ms_por_texto", "borde_29"])
        for nombre, datos in resumen_seg03.items():
            w.writerow(
                [
                    nombre,
                    datos["casos"],
                    datos["exactos"],
                    datos["porcentaje"],
                    json.dumps(datos["por_grupo"], ensure_ascii=False),
                    datos["ms_por_texto"],
                    datos["borde"],
                ]
            )
    (EVIDENCIA / "PR-SEG-03_E_20261110_01.json").write_text(
        json.dumps(resumen_seg03, ensure_ascii=False, indent=1), encoding="utf-8"
    )

    print(json.dumps({"SEG-01": resumen_seg01, "SEG-03": resumen_seg03}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
