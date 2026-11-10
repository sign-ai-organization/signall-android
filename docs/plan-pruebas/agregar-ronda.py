#!/usr/bin/env python3
"""Agrega una ronda nueva a `resultados-consolidados.xlsx`.

El libro tiene una fila por prueba y por ronda. Este script inserta, para cada
prueba que aparezca en el directorio de resultados, una fila nueva **debajo de la
última fila de esa prueba** en su hoja de bloque, copiando el estilo de la fila
anterior y pisando las seis columnas de resultado (L a Q).

También deja el libro coherente:

- agrega el nombre de la ronda a la lista desplegable de la columna `Ronda`;
- agrega una fila a la tabla «Por ronda» del `Resumen`, con las mismas fórmulas
  que la ronda anterior pero cambiando el literal de la ronda;
- reescribe los conteos de la `Portada` y agrega una nota al pie.

Uso:
    ~/.venv/bin/python agregar-ronda.py \\
        --libro resultados-consolidados.xlsx \\
        --resultados resultados-p1-2026-11-10 \\
        --ronda "Ronda P1 10/11" \\
        --nota "Nota del 10/11/2026: la ronda P1 10/11 repite solo las 17 pruebas del carril 1 que no estaban cerradas."
"""

import argparse
import json
import sys
from copy import copy
from pathlib import Path

import openpyxl
from openpyxl.utils import get_column_letter

AQUI = Path(__file__).resolve().parent
COL_ESTADO, COL_RESULTADO, COL_EVIDENCIA, COL_FECHA, COL_EJECUTOR, COL_RONDA = range(12, 18)


def filas_de_prueba(ws) -> list[tuple[int, str]]:
    """(fila, ID) de cada fila de prueba, en el orden en que están en la hoja."""
    filas = []
    for r in range(4, ws.max_row + 1):
        valor = ws.cell(r, 1).value
        if isinstance(valor, str) and valor.startswith("PR-"):
            filas.append((r, valor))
    return filas


def insertar_fila(ws, idx: int) -> None:
    """Inserta una fila vacía en `idx` moviendo también los altos de fila.

    `openpyxl.insert_rows` mueve las celdas pero **no** `row_dimensions`, así que
    sin esto los altos quedan pegados a la fila equivocada y las filas nuevas
    salen con el alto por defecto (15), cortando el texto.
    """
    alturas = {r: ws.row_dimensions[r].height
               for r in range(idx, ws.max_row + 1)
               if ws.row_dimensions[r].height is not None}
    ws.insert_rows(idx, 1)
    for r in sorted(alturas, reverse=True):
        ws.row_dimensions[r + 1].height = alturas[r]
    ws.row_dimensions[idx].height = None


def copiar_fila(ws, origen: int, destino: int) -> None:
    """Copia valor y estilo de una fila. La ficha (columnas A–K) se repite tal cual."""
    for col in range(1, ws.max_column + 1):
        src = ws.cell(origen, col)
        dst = ws.cell(destino, col)
        dst.value = src.value
        if src.has_style:
            dst._style = copy(src._style)


def lineas(texto, ancho: float) -> int:
    """Cuenta líneas de un texto envuelto en una columna de `ancho` caracteres."""
    if not texto:
        return 1
    total = 0
    for parrafo in str(texto).split("\n"):
        usados, n = 0, 1
        for palabra in parrafo.split():
            largo = len(palabra)
            if usados == 0:
                usados = largo
            elif usados + 1 + largo <= ancho:
                usados += 1 + largo
            else:
                n += 1
                usados = largo
        total += n
    return total


def alto_necesario(ws, fila: int) -> float:
    """Alto en puntos para que el texto de las columnas que envuelven se vea entero.

    Calibrado contra el libro: con fuente 9 pt entran ~1,41 caracteres por unidad
    de ancho de columna y cada línea ocupa 12,4 pt.
    """
    necesarias = 1
    for col in (COL_RESULTADO, COL_EVIDENCIA, COL_EJECUTOR, COL_RONDA):
        letra = get_column_letter(col)
        ancho = ws.column_dimensions[letra].width or 8.43
        necesarias = max(necesarias, lineas(ws.cell(fila, col).value, ancho * 1.41))
    return necesarias * 12.4 + 4


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--libro", default=str(AQUI / "resultados-consolidados.xlsx"))
    p.add_argument("--resultados", required=True, help="carpeta con PR-*.json")
    p.add_argument("--ronda", required=True, help="valor de la columna Ronda")
    p.add_argument("--nota", default="", help="nota al pie de la Portada")
    p.add_argument("--salida", default="")
    args = p.parse_args()

    resultados = {}
    for f in sorted(Path(args.resultados).glob("PR-*.json")):
        resultados[f.stem] = json.loads(f.read_text(encoding="utf-8"))
    if not resultados:
        print("no hay PR-*.json en", args.resultados, file=sys.stderr)
        return 1

    wb = openpyxl.load_workbook(args.libro)
    insertadas, sin_hoja = [], []

    for nombre in wb.sheetnames:
        ws = wb[nombre]
        filas = filas_de_prueba(ws)
        if not filas:
            continue
        ultima = {}
        for r, prueba in filas:
            ultima[prueba] = r
        pendientes = [t for t in ultima if t in resultados]
        if not pendientes:
            continue
        # De abajo hacia arriba: insertar no invalida las filas que faltan.
        for prueba in sorted(pendientes, key=lambda t: ultima[t], reverse=True):
            origen = ultima[prueba]
            insertar_fila(ws, origen + 1)
            copiar_fila(ws, origen, origen + 1)
            d = resultados[prueba]
            valores = {
                COL_ESTADO: d["estado"],
                COL_RESULTADO: d["obtenido"],
                COL_EVIDENCIA: ", ".join(d.get("evidencias") or []) or "—",
                COL_FECHA: d.get("fecha", ""),
                COL_EJECUTOR: d.get("ejecutor", ""),
                COL_RONDA: args.ronda,
            }
            for col, val in valores.items():
                ws.cell(origen + 1, col).value = val
            # El alto de la fila anterior puede no alcanzar: el texto nuevo es otro.
            ws.row_dimensions[origen + 1].height = max(
                alto_necesario(ws, origen + 1), ws.row_dimensions[origen].height or 15)
            insertadas.append(prueba)
        # La lista desplegable de la columna Ronda tiene que aceptar el valor nuevo.
        for dv in ws.data_validations.dataValidation:
            if str(dv.sqref).startswith("Q"):
                opciones = dv.formula1.strip('"').split(",")
                if args.ronda not in opciones:
                    dv.formula1 = '"' + ",".join(opciones + [args.ronda]) + '"'

    faltan = sorted(set(resultados) - set(insertadas))
    if faltan:
        print("sin fila en el libro:", ", ".join(faltan), file=sys.stderr)

    # ── Resumen: una fila más en «Por ronda», copiando las fórmulas ──────────
    if "Resumen" in wb.sheetnames:
        ws = wb["Resumen"]
        filas_ronda = [r for r in range(4, ws.max_row + 1)
                       if isinstance(ws.cell(r, 2).value, str)
                       and ws.cell(r, 2).value.startswith("Ronda ")]
        if filas_ronda:
            ultima = filas_ronda[-1]
            insertar_fila(ws, ultima + 1)
            copiar_fila(ws, ultima, ultima + 1)
            ws.cell(ultima + 1, 2).value = args.ronda
            for col in range(4, 10):
                valor = ws.cell(ultima, col).value
                if isinstance(valor, str) and valor.startswith("="):
                    valor = valor.replace('"Ronda de evaluación"', f'"{args.ronda}"')
                ws.cell(ultima + 1, col).value = valor
            # «Sin ejecutar» es la resta de la propia fila, no la de la anterior.
            n = ultima + 1
            ws.cell(n, 9).value = f"=D{n}-E{n}-F{n}-G{n}-H{n}"

    # ── Portada: los conteos y la nota ───────────────────────────────────────
    if "Portada" in wb.sheetnames:
        ws = wb["Portada"]
        pruebas, filas_totales, rondas = set(), 0, []
        for nombre in wb.sheetnames:
            hoja = wb[nombre]
            filas = filas_de_prueba(hoja)
            if not filas:
                continue
            filas_totales += len(filas)
            pruebas.update(t for _, t in filas)
            for r, _ in filas:
                ronda = hoja.cell(r, COL_RONDA).value
                if ronda and ronda not in rondas:
                    rondas.append(ronda)
        ws["C7"] = (f"{len(pruebas)} pruebas en 14 bloques, en {len(rondas)} rondas "
                    f"({filas_totales} filas)")
        ws["C9"] = (", ".join(rondas[:-1]) + " y " + rondas[-1]) if len(rondas) > 1 else rondas[0]
        ws["C11"] = resultados[sorted(resultados)[0]].get("fecha", "")
        if args.nota:
            ultima = max(r for r in range(1, ws.max_row + 1)
                         if any(ws.cell(r, c).value for c in range(1, 4)))
            ws.cell(ultima + 2, 2).value = args.nota
            if ws.cell(ultima, 2).has_style:
                ws.cell(ultima + 2, 2)._style = copy(ws.cell(ultima, 2)._style)

    salida = args.salida or args.libro
    wb.save(salida)
    print(f"filas insertadas: {len(insertadas)}")
    print("salida:", salida)
    return 0


if __name__ == "__main__":
    sys.exit(main())
