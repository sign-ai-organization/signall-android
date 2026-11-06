#!/usr/bin/env python3
"""Genera `resultados-consolidados.xlsx`: las 74 pruebas en las dos rondas.

Fuente de la ronda diagnóstica: el libro que se pasa con `--diagnostico`, con una
fila por prueba. Hoy es `resultados-consolidados.xlsx` mismo, que quedó con la
ronda del 05/11 renombrada como diagnóstica; antes era `plantilla-resultados.xlsx`
(la ronda del 04/11, con las 24 recuperadas con `gpt-4o-mini`).
Fuente de la ronda de evaluación: un directorio de resultados `PR-*.json` con la
misma forma que usa `rellenar-libro.py` (estado, obtenido, evidencias, fecha,
ejecutor, ronda).

Cada hoja de bloque queda con **dos filas por prueba**: la diagnóstica y la de
evaluación. La columna `Ronda` (Q) es la que distingue. El `Resumen` se reescribe
para no mentir: «Por bloque» cuenta solo la ronda diagnóstica —74 pruebas— y «Por
ronda» cuenta las dos.

Uso:
    ~/.venv/bin/python generar-consolidado.py \
        --diagnostico resultados-consolidados.xlsx \
        --evaluacion /tmp/opencode/pruebas/resultados-evaluacion \
        --salida resultados-consolidados.xlsx
"""

import argparse
import json
import os
import sys
from copy import copy
from datetime import date
from pathlib import Path

import openpyxl

AQUI = Path(__file__).resolve().parent
COL_ESTADO, COL_RESULTADO, COL_EVIDENCIA, COL_FECHA, COL_EJECUTOR, COL_RONDA = range(12, 18)
BLOQUES = ["STT", "SEG", "HUE", "CAC", "LLM", "VAL", "INT", "AND", "AVT", "REN", "RES",
           "SEC", "TRA", "PER"]


def filas_de_prueba(ws) -> list[int]:
    return [r for r in range(4, ws.max_row + 1)
            if isinstance(ws.cell(r, 1).value, str) and ws.cell(r, 1).value.startswith("PR-")]


def copiar_fila(ws, origen: int, destino: int, valores: dict | None = None) -> None:
    """Copia una fila (valor, estilo y alto) y pisa las columnas indicadas."""
    for col in range(1, ws.max_column + 1):
        src = ws.cell(origen, col)
        dst = ws.cell(destino, col)
        dst.value = valores.get(col, src.value) if valores else src.value
        if src.has_style:
            dst._style = copy(src._style)
    altura = ws.row_dimensions[origen].height
    if altura:
        ws.row_dimensions[destino].height = altura


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--diagnostico", default=str(AQUI / "plantilla-resultados.xlsx"))
    p.add_argument("--evaluacion", default="/tmp/opencode/pruebas/resultados-evaluacion")
    p.add_argument("--salida", default=str(AQUI / "resultados-consolidados.xlsx"))
    args = p.parse_args()

    wb = openpyxl.load_workbook(args.diagnostico)
    evaluacion = {}
    carpeta = Path(args.evaluacion)
    if carpeta.exists():
        for f in sorted(carpeta.glob("PR-*.json")):
            try:
                evaluacion[f.stem] = json.loads(f.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                print("JSON ilegible:", f, file=sys.stderr)

    escritas, sin_evaluacion = 0, []
    for nombre in BLOQUES:
        if nombre not in wb.sheetnames:
            continue
        ws = wb[nombre]
        originales = filas_de_prueba(ws)
        if not originales:
            continue
        # Se guarda valor, estilo y alto de cada fila **antes** de borrarlas:
        # después de `delete_rows` la celda de origen ya no existe.
        copias = []
        for r in originales:
            copias.append({
                "valores": {c: ws.cell(r, c).value for c in range(1, ws.max_column + 1)},
                "estilos": {c: copy(ws.cell(r, c)._style) if ws.cell(r, c).has_style else None
                            for c in range(1, ws.max_column + 1)},
                "alto": ws.row_dimensions[r].height,
            })
        for r in reversed(originales):
            ws.delete_rows(r)

        fila = 4
        for copia in copias:
            prueba = copia["valores"][1]
            for ronda in ("diagnostica", "evaluacion"):
                valores = dict(copia["valores"])
                if ronda == "diagnostica":
                    valores[COL_RONDA] = "Ronda diagnóstica"
                else:
                    d = evaluacion.get(prueba)
                    if d:
                        valores[COL_ESTADO] = d["estado"]
                        valores[COL_RESULTADO] = d["obtenido"]
                        valores[COL_EVIDENCIA] = ", ".join(d.get("evidencias") or []) or "—"
                        valores[COL_FECHA] = d.get("fecha", "")
                        valores[COL_EJECUTOR] = d.get("ejecutor", "")
                        # La etiqueta la pone este libro, no el JSON: el JSON
                        # trae la ronda con la que se corrió el colector y una
                        # tanda mal etiquetada dejaría dos filas idénticas.
                        valores[COL_RONDA] = "Ronda de evaluación"
                    else:
                        sin_evaluacion.append(prueba)
                        if copia["valores"].get(COL_ESTADO) == "Bloqueada":
                            # Una prueba bloqueada sigue bloqueada en la ronda nueva:
                            # se repite el motivo, no se marca "Sin ejecutar".
                            valores[COL_RESULTADO] = (
                                "Sigue bloqueada por el mismo motivo de la ronda "
                                "diagnóstica: " + str(copia["valores"].get(COL_RESULTADO) or "")
                            )
                        else:
                            valores[COL_ESTADO] = "Sin ejecutar"
                            for c in (COL_RESULTADO, COL_EVIDENCIA, COL_FECHA, COL_EJECUTOR):
                                valores[c] = ""
                        valores[COL_RONDA] = "Ronda de evaluación"
                for col, val in valores.items():
                    celda = ws.cell(fila, col)
                    celda.value = val
                    estilo = copia["estilos"].get(col)
                    if estilo is not None:
                        celda._style = copy(estilo)
                if copia["alto"]:
                    ws.row_dimensions[fila].height = copia["alto"]
                escritas += 1
                fila += 1

    # ── Resumen: «Por bloque» cuenta solo la ronda diagnóstica ──────────────
    if "Resumen" in wb.sheetnames:
        ws = wb["Resumen"]
        for r in range(7, 21):
            bloque = ws.cell(r, 2).value
            if bloque not in BLOQUES:
                continue
            ws.cell(r, 4).value = (
                f'=COUNTIFS({bloque}!$A$4:$A$400,"PR-*",{bloque}!$Q$4:$Q$400,'
                f'"Ronda diagnóstica")')
            for col, estado in ((5, "Pasa"), (6, "Falla"), (7, "Se reporta"), (8, "Bloqueada")):
                ws.cell(r, col).value = (
                    f'=COUNTIFS({bloque}!$L$4:$L$400,"{estado}",'
                    f'{bloque}!$Q$4:$Q$400,"Ronda diagnóstica")')
            ws.cell(r, 9).value = f"=D{r}-E{r}-F{r}-G{r}-H{r}"

    # ── Portada: el consolidado no es la plantilla, y Overleaf ya no es la fuente ──
    if "Portada" in wb.sheetnames:
        ws = wb["Portada"]
        ws["C6"] = "Plan de Pruebas, versión 39 (vive en ../sign-ai-docs/latex/plan-pruebas; Overleaf se retiró el 2026-11-04)"
        ws["C7"] = "74 pruebas en 14 bloques, en dos rondas (148 filas)"
        ws["C8"] = "una fila por prueba y por ronda; la columna Ronda dice cuál es cuál"
        ws["C9"] = ("Ronda diagnóstica (el libro que se pasa con --diagnostico) y ronda de "
                    "evaluación (esta corrida)")
        ws["C11"] = date.today().strftime("%d/%m/%Y")

    wb.save(args.salida)
    print(f"filas escritas: {escritas} (esperado 148)")
    print("salida:", args.salida)
    if sin_evaluacion:
        print(f"sin resultado de evaluación: {len(sin_evaluacion)}")
        print("  " + ", ".join(sin_evaluacion))
    return 0


if __name__ == "__main__":
    sys.exit(main())
