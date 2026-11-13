#!/usr/bin/env python3
"""Genera plantilla-resultados.xlsx desde las fichas del Plan de Pruebas.

Fuente: ../signall-docs/latex/plan-pruebas/main.tex (la copia que quedo al retirar
Overleaf el 2026-11-04; tiene 72 fichas).
PR-REN-09 se agrega desde ../signall-docs/borradores/plan-pruebas/01-diseno/rendimiento/pruebas.md
porque todavia no esta en el plan formal (decision del owner, 2026-11-04).
"""

import math
import re
import sys

from openpyxl import Workbook
from openpyxl.formatting.rule import FormulaRule
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.worksheet.pagebreak import Break

import os

# El plan formal del repositorio y la ficha que todavia no esta en el.
AQUI = os.path.dirname(os.path.abspath(__file__))
TEX = sys.argv[1] if len(sys.argv) > 1 else os.path.join(AQUI, "../../../signall-docs/latex/plan-pruebas/main.tex")
MD_REN = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    AQUI, "../../../signall-docs/borradores/plan-pruebas/01-diseno/rendimiento/pruebas.md")
OUT = os.path.join(AQUI, "plantilla-resultados.xlsx")

# ── Paleta (la del documento LaTeX) ───────────────────────────────────────────
BLUE = "1F497D"
GRAY = "595959"
RULE = "C9C9C9"
BAND = "F4F5F7"
WHITE = "FFFFFF"

ESTADOS = [
    ("Pasa", "Cumplió el criterio de aceptación.", "D9EAD3", "1E4620"),
    ("Falla", "No cumplió el criterio de aceptación.", "F4CCCC", "7F1D1D"),
    ("Se reporta", "Es una prueba de caracterización: el resultado se registra sin juzgarlo.", "FFF2CC", "7F6000"),
    ("Bloqueada", "No se pudo ejecutar porque falta algo de lo que depende, como una funcionalidad que todavía no existe.", "D9D9D9", "404040"),
]

CAMPOS = [
    ("ID", "El identificador de la prueba."),
    ("Nombre", "Título corto."),
    ("Bloque", "Bloque al que pertenece."),
    ("Tipo", "Uno de los siete tipos de la sección 6.1."),
    ("Objetivo", "Qué se quiere saber con la prueba."),
    ("Trazabilidad", "Requerimientos y objetivo específico que cubre."),
    ("Entorno y precondiciones", "Dispositivo, configuración, modo de envío (manual o en vivo) y estado inicial."),
    ("Datos de entrada", "Conjuntos de datos de la sección 8 que usa, citados por su código."),
    ("Procedimiento", "Pasos resumidos."),
    ("Métrica", "Qué se mide y cómo se calcula."),
    ("Criterio de aceptación", "Cuándo pasa. En las pruebas de caracterización dice «se reporta»."),
    ("Evidencia", "Qué se guarda: registros, archivos o capturas."),
]

BLOQUES = [
    ("STT", "Transcripción"),
    ("SEG", "Segmentador de oraciones"),
    ("HUE", "Huella gramatical"),
    ("CAC", "Caché semántica"),
    ("LLM", "Cliente del LLM"),
    ("VAL", "Validador y catálogo"),
    ("INT", "Integración y extremo a extremo"),
    ("AND", "Aplicación Android"),
    ("AVT", "Avatar"),
    ("REN", "Rendimiento y tiempos"),
    ("RES", "Resiliencia"),
    ("SEC", "Seguridad"),
    ("TRA", "Transversales"),
    ("PER", "Evaluación con personas"),
]
BLOQUE_NOMBRE = dict(BLOQUES)

CAMPOS_TEX = ["ID", "Nombre", "Bloque", "Tipo", "Objetivo", "Trazabilidad",
              "Entorno y precondiciones", "Datos de entrada", "Procedimiento",
              "Métrica", "Criterio de aceptación", "Evidencia"]

COLUMNAS = [
    ("ID", "ID", 11),
    ("Nombre", "Nombre", 28),
    ("Tipo", "Tipo", 19),
    ("Objetivo", "Objetivo", 36),
    ("Trazabilidad", "Trazabilidad", 27),
    ("Entorno y precondiciones", "Entorno y precondiciones", 28),
    ("Datos de entrada", "Datos de entrada", 28),
    ("Procedimiento", "Procedimiento", 36),
    ("Métrica", "Métrica", 36),
    ("Criterio de aceptación", "Criterio de aceptación", 28),
    ("Evidencia", "Evidencia prevista", 25),
    (None, "Estado", 14),
    (None, "Resultado obtenido", 34),
    (None, "Evidencia ejecutada", 26),
    (None, "Fecha", 12),
    (None, "Ejecutor", 16),
    (None, "Ronda", 18),
]
NCOL = len(COLUMNAS)  # 17
COL_LETTER = {name: get_column_letter(i + 1) for i, (_, name, _) in enumerate(COLUMNAS)}
COL_INDEX = {name: i + 1 for i, (_, name, _) in enumerate(COLUMNAS)}
LAST_COL = get_column_letter(NCOL)


# ── Limpieza de LaTeX ─────────────────────────────────────────────────────────
def limpiar(s: str) -> str:
    if not s:
        return ""
    s = s.replace("\\hline", "")
    for cmd in ("texttt", "textbf", "emph", "textit", "textsc", "underline"):
        pat = re.compile(r"\\" + cmd + r"\{([^{}]*)\}")
        while pat.search(s):
            s = pat.sub(r"\1", s)
    s = s.replace("\\_", "_").replace("\\%", "%").replace("\\&", "&")
    s = s.replace("\\#", "#").replace("\\$", "$").replace("\\{", "{").replace("\\}", "}")
    s = s.replace("~", " ").replace("---", "—").replace("--", "–")
    s = s.replace("``", "«").replace("''", "»")
    s = s.replace("\\dots", "…").replace("\\ldots", "…")
    s = re.sub(r"\\ref\{[^}]*\}", "", s)
    s = re.sub(r"\\label\{[^}]*\}", "", s)
    s = re.sub(r"\\[a-zA-Z]+\s?", "", s)
    s = s.replace("\\\\", " ")
    s = re.sub(r"[ \t]+", " ", s)
    return s.strip()


# ── Lectura de las fichas ─────────────────────────────────────────────────────
def leer_tex(path):
    t = open(path, encoding="utf-8").read()
    fichas = [m for m in re.finditer(r"\\ficha\{([^}]*)\}\{([^}]*)\}", t) if m.group(1) != "ID"]
    out = {}
    for m in fichas:
        start = t.find("\nID & ", m.end())
        lines = t[start + 1:].split("\n")[:14]
        d = {}
        for line in lines:
            if " & " in line:
                k, v = line.split(" & ", 1)
                d[k.strip()] = limpiar(v.rstrip().rstrip("\\").strip())
            if len(d) == len(CAMPOS_TEX):
                break
        if list(d) != CAMPOS_TEX:
            sys.exit(f"ficha malformada: {m.group(1)} -> {list(d)}")
        out[m.group(1)] = d
    return out


def leer_md_prueba(path, pid):
    t = open(path, encoding="utf-8").read()
    start = t.find(f"## {pid}.")
    if start < 0:
        sys.exit(f"no encontre {pid} en {path}")
    end = t.find("\n## ", start + 5)
    body = t[start:end if end > 0 else len(t)]
    rows = re.findall(r"^\| ([^|]+?) \| (.+?) \|$", body, re.M)
    d = {k.strip(): v.strip() for k, v in rows if k.strip() not in ("Campo", "---")}
    falta = [c for c in CAMPOS_TEX if c not in d]
    if falta:
        sys.exit(f"{pid} sin campos: {falta}")
    return {c: d[c] for c in CAMPOS_TEX}


# ── Estilos ───────────────────────────────────────────────────────────────────
THIN = Side(style="thin", color=RULE)
BOX = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)


def fill(hexcolor):
    return PatternFill("solid", start_color=hexcolor, end_color=hexcolor)


def titulo_hoja(ws, texto, subtitulo):
    ws.merge_cells(f"A1:{LAST_COL}1")
    c = ws["A1"]
    c.value = texto
    c.font = Font(name="Calibri", size=15, bold=True, color=WHITE)
    c.fill = fill(BLUE)
    c.alignment = Alignment(horizontal="left", vertical="center", indent=1)
    ws.row_dimensions[1].height = 30

    ws.merge_cells(f"A2:{LAST_COL}2")
    c = ws["A2"]
    c.value = subtitulo
    c.font = Font(name="Calibri", size=9, italic=True, color=GRAY)
    c.alignment = Alignment(horizontal="left", vertical="center", indent=1)
    ws.row_dimensions[2].height = 18


def fila_encabezado(ws, row=3):
    for i, (_, name, width) in enumerate(COLUMNAS, start=1):
        c = ws.cell(row=row, column=i, value=name)
        c.font = Font(name="Calibri", size=9, bold=True, color=WHITE)
        c.fill = fill(BLUE)
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        c.border = Border(left=THIN, right=THIN, top=THIN,
                          bottom=Side(style="medium", color=BLUE))
        ws.column_dimensions[get_column_letter(i)].width = width
    ws.row_dimensions[row].height = 34


def altura(textos, anchos):
    lineas = 1
    for txt, w in zip(textos, anchos):
        if not txt:
            continue
        n = 0
        for parte in str(txt).split("\n"):
            n += max(1, math.ceil(len(parte) / max(8.0, w * 0.95)))
        lineas = max(lineas, n)
    return min(260, max(30, lineas * 13.0 + 8))


def estilos_datos(ws, desde, hasta):
    anchos = [w for _, _, w in COLUMNAS]
    for r in range(desde, hasta + 1):
        textos = []
        for i in range(1, NCOL + 1):
            c = ws.cell(row=r, column=i)
            textos.append(c.value)
            c.font = Font(name="Calibri", size=9, color="1A1A1A")
            c.alignment = Alignment(vertical="top", wrap_text=True,
                                    horizontal="center" if i in (1, 15) else "left")
            c.border = BOX
            if (r - desde) % 2 == 1 and i != 12:
                c.fill = fill(BAND)
        ws.row_dimensions[r].height = altura(textos, anchos)


def validacion_estado(ws, desde, hasta):
    dv = DataValidation(type="list",
                        formula1='"' + ",".join(e[0] for e in ESTADOS) + '"',
                        allow_blank=True,
                        showErrorMessage=True,
                        errorTitle="Estado no válido",
                        error="Elegí uno de los cuatro estados: Pasa, Falla, Se reporta o Bloqueada.",
                        promptTitle="Estado",
                        prompt="Pasa, Falla, Se reporta o Bloqueada (sección 9.1 del plan).")
    ws.add_data_validation(dv)
    dv.add(f"L{desde}:L{hasta}")

    dv2 = DataValidation(type="list",
                         formula1='"Ronda diagnóstica,Ronda de evaluación"',
                         allow_blank=True, showErrorMessage=False,
                         promptTitle="Ronda",
                         prompt="Ronda diagnóstica o ronda de evaluación (sección 6.2).")
    ws.add_data_validation(dv2)
    dv2.add(f"Q{desde}:Q{hasta}")

    for estado, _, bg, fg in ESTADOS:
        ws.conditional_formatting.add(
            f"L{desde}:L{hasta}",
            FormulaRule(formula=[f'$L{desde}="{estado}"'],
                        fill=fill(bg),
                        font=Font(name="Calibri", size=9, bold=True, color=fg),
                        stopIfTrue=False),
        )


def preparar_impresion(ws, ancho="A4", paginar=False):
    ws.page_setup.orientation = "landscape"
    ws.page_setup.paperSize = ws.PAPERSIZE_A4 if ancho == "A4" else ws.PAPERSIZE_A3
    if paginar:
        # 17 columnas de prosa no entran legibles en una pagina: se imprime a
        # escala real, se repite el encabezado y la columna del ID, y se corta
        # antes de «Estado» para que la ficha y el formulario queden separados.
        ws.page_setup.scale = 100
        ws.print_title_cols = "A:A"
    else:
        ws.sheet_properties.pageSetUpPr.fitToPage = True
        ws.page_setup.fitToWidth = 1
        ws.page_setup.fitToHeight = 0
    # Con paginado, el titulo va una sola vez: repetirlo en cada hoja lo corta
    # porque esta fusionado sobre 17 columnas.
    ws.print_title_rows = "3:3" if paginar else "1:3"
    ws.print_options.horizontalCentered = True
    ws.page_margins.left = ws.page_margins.right = 0.3
    ws.page_margins.top = ws.page_margins.bottom = 0.45


# ── Portada ───────────────────────────────────────────────────────────────────
def hoja_portada(wb, total, n_fichas_ol, tiene_ren09):
    ws = wb.create_sheet("Portada", 0)
    ws.sheet_view.showGridLines = False
    ws.column_dimensions["A"].width = 3
    ws.column_dimensions["B"].width = 26
    ws.column_dimensions["C"].width = 96

    r = 2
    ws.merge_cells(f"B{r}:C{r}")
    c = ws[f"B{r}"]
    c.value = "Plan de Pruebas"
    c.font = Font(name="Calibri", size=26, bold=True, color=BLUE)
    c.alignment = Alignment(vertical="center")
    ws.row_dimensions[r].height = 38

    r += 1
    ws.merge_cells(f"B{r}:C{r}")
    c = ws[f"B{r}"]
    c.value = "Plantilla de registro de resultados"
    c.font = Font(name="Calibri", size=14, color=GRAY)
    ws.row_dimensions[r].height = 24

    r += 1
    ws.merge_cells(f"B{r}:C{r}")
    c = ws[f"B{r}"]
    c.value = "SignAll · español hablado a Lengua de Señas Colombiana con un avatar 3D"
    c.font = Font(name="Calibri", size=10, italic=True, color=GRAY)
    r += 2

    datos = [
        ("Documento fuente", "Plan de Pruebas, ../signall-docs/latex/plan-pruebas/main.tex"),
        ("Pruebas incluidas", f"{total} en 14 bloques ({n_fichas_ol} fichas del plan"
                              + (" + PR-REN-09 desde el borrador" if tiene_ren09 else "") + ")"),
        ("Registro", "una fila por prueba, en cada ronda"),
        ("Rondas", "Ronda diagnóstica y ronda de evaluación (sección 6.2 del plan)"),
        ("Responsable", "A definir"),
        ("Última actualización", ""),
    ]
    for k, v in datos:
        ws.cell(row=r, column=2, value=k).font = Font(name="Calibri", size=9, bold=True, color=GRAY)
        c = ws.cell(row=r, column=3, value=v)
        c.font = Font(name="Calibri", size=9, color="1A1A1A")
        c.alignment = Alignment(wrap_text=True, vertical="top")
        ws.row_dimensions[r].height = 15
        r += 1
    r += 1

    def seccion(texto, bajada=""):
        nonlocal r
        ws.merge_cells(f"B{r}:C{r}")
        c = ws[f"B{r}"]
        c.value = texto
        c.font = Font(name="Calibri", size=12, bold=True, color=BLUE)
        c.alignment = Alignment(vertical="bottom")
        ws.row_dimensions[r].height = 24
        r += 1
        if bajada:
            ws.merge_cells(f"B{r}:C{r}")
            c = ws[f"B{r}"]
            c.value = bajada
            c.font = Font(name="Calibri", size=9, italic=True, color=GRAY)
            c.alignment = Alignment(wrap_text=True, vertical="top")
            ws.row_dimensions[r].height = 14
            r += 1

    seccion("1. Los cuatro estados de resultado",
            "Sección 9.1 del plan. Una prueba bloqueada no es una prueba fallida: falla la que se ejecuta "
            "y no cumple el criterio, y queda bloqueada la que no se puede ejecutar.")

    for estado, significado, bg, fg in ESTADOS:
        c = ws.cell(row=r, column=2, value=estado)
        c.font = Font(name="Calibri", size=10, bold=True, color=fg)
        c.fill = fill(bg)
        c.alignment = Alignment(horizontal="center", vertical="center")
        c.border = BOX
        c2 = ws.cell(row=r, column=3, value=significado)
        c2.font = Font(name="Calibri", size=9, color="1A1A1A")
        c2.alignment = Alignment(wrap_text=True, vertical="center")
        c2.border = BOX
        ws.row_dimensions[r].height = max(16, 13 * math.ceil(len(significado) / 96) + 6)
        r += 1
    r += 1

    seccion("2. Los doce campos de la ficha",
            "Sección 9.1 del plan. Los once primeros son los de la ficha; «Evidencia» se llama "
            "«Evidencia prevista» en la hoja del bloque, para no confundirla con la evidencia ejecutada.")

    for campo, contenido in CAMPOS:
        c = ws.cell(row=r, column=2, value=campo)
        c.font = Font(name="Calibri", size=9, bold=True, color="1A1A1A")
        c.alignment = Alignment(vertical="top", wrap_text=True)
        c.border = BOX
        c2 = ws.cell(row=r, column=3, value=contenido)
        c2.font = Font(name="Calibri", size=9, color="1A1A1A")
        c2.alignment = Alignment(wrap_text=True, vertical="top")
        c2.border = BOX
        ws.row_dimensions[r].height = max(15, 13 * math.ceil(len(contenido) / 96) + 4)
        r += 1
    r += 1

    seccion("3. Cómo se llena")
    reglas = [
        "Cada hoja de bloque trae una fila por prueba, con la ficha copiada del plan: no hay que retipear nada, "
        "solo agregar lo que se obtuvo.",
        "«Estado» se elige de una lista con los cuatro valores de arriba. La celda cambia de color sola.",
        "«Resultado obtenido» es el número o el texto que salió, no el juicio: el juicio está en «Estado».",
        "«Evidencia ejecutada» es el nombre del archivo guardado, con la convención del README que acompaña a esta plantilla.",
        "«Fecha» y «Ejecutor» se completan por fila. Si una prueba se repite en la otra ronda, se guarda una copia del libro "
        "por ronda y se cambia el valor de «Ronda».",
        "La hoja «Resumen» se recalcula sola: no se escribe a mano.",
    ]
    for texto in reglas:
        ws.merge_cells(f"B{r}:C{r}")
        c = ws[f"B{r}"]
        c.value = "·  " + texto
        c.font = Font(name="Calibri", size=9, color="1A1A1A")
        c.alignment = Alignment(wrap_text=True, vertical="top")
        ws.row_dimensions[r].height = max(14, 13 * math.ceil(len(texto) / 118) + 4)
        r += 1

    preparar_impresion(ws)
    ws.page_setup.orientation = "portrait"
    ws.print_title_rows = None
    ws.print_title_cols = None
    ws.print_area = f"A1:C{r}"
    return ws


# ── Resumen ───────────────────────────────────────────────────────────────────
def hoja_resumen(wb):
    ws = wb.create_sheet("Resumen", 1)
    ws.sheet_view.showGridLines = False
    for col, w in (("A", 3), ("B", 10), ("C", 34), ("D", 11), ("E", 10), ("F", 10),
                   ("G", 12), ("H", 12), ("I", 13)):
        ws.column_dimensions[col].width = w

    ws.merge_cells("B2:I2")
    c = ws["B2"]
    c.value = "Resumen de resultados"
    c.font = Font(name="Calibri", size=20, bold=True, color=BLUE)
    ws.row_dimensions[2].height = 30

    ws.merge_cells("B3:I3")
    c = ws["B3"]
    c.value = "Se recalcula con lo que se marque en las hojas de cada bloque. No escribir a mano."
    c.font = Font(name="Calibri", size=9, italic=True, color=GRAY)

    # Tabla 1: por bloque
    r0 = 5
    ws.merge_cells(f"B{r0}:I{r0}")
    c = ws[f"B{r0}"]
    c.value = "Por bloque"
    c.font = Font(name="Calibri", size=12, bold=True, color=BLUE)
    ws.row_dimensions[r0].height = 22

    head = ["Bloque", "Nombre", "Pruebas", "Pasa", "Falla", "Se reporta", "Bloqueada", "Sin ejecutar"]
    for i, h in enumerate(head, start=2):
        c = ws.cell(row=r0 + 1, column=i, value=h)
        c.font = Font(name="Calibri", size=9, bold=True, color=WHITE)
        c.fill = fill(BLUE)
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        c.border = Border(left=THIN, right=THIN, top=THIN, bottom=Side(style="medium", color=BLUE))
    ws.row_dimensions[r0 + 1].height = 30

    first = r0 + 2
    for k, (code, nombre) in enumerate(BLOQUES):
        r = first + k
        ws.cell(row=r, column=2, value=code).font = Font(name="Calibri", size=9, bold=True)
        ws.cell(row=r, column=3, value=nombre).font = Font(name="Calibri", size=9)
        ws.cell(row=r, column=4, value=f"=COUNTA({code}!$A$4:$A$200)")
        for j, estado in enumerate([e[0] for e in ESTADOS]):
            ws.cell(row=r, column=5 + j, value=f'=COUNTIF({code}!$L$4:$L$200,"{estado}")')
        ws.cell(row=r, column=9, value=f"=D{r}-E{r}-F{r}-G{r}-H{r}")
        for i in range(2, 10):
            c = ws.cell(row=r, column=i)
            if i > 3:
                c.font = Font(name="Calibri", size=9)
                c.alignment = Alignment(horizontal="center")
            c.border = BOX
            if k % 2 == 1:
                c.fill = fill(BAND)

    tot = first + len(BLOQUES)
    c = ws.cell(row=tot, column=2, value="")
    c.fill = fill(GRAY)
    c.border = BOX
    c = ws.cell(row=tot, column=3, value="Total")
    c.font = Font(name="Calibri", size=9, bold=True, color=WHITE)
    c.fill = fill(GRAY)
    c.alignment = Alignment(horizontal="right")
    for i, col in enumerate("DEFGHI", start=4):
        c = ws.cell(row=tot, column=i, value=f"=SUM({col}{first}:{col}{tot-1})")
        c.font = Font(name="Calibri", size=9, bold=True, color=WHITE)
        c.fill = fill(GRAY)
        c.alignment = Alignment(horizontal="center")
    for i in range(2, 10):
        ws.cell(row=tot, column=i).border = BOX

    # Tabla 2: por ronda
    r1 = tot + 3
    ws.merge_cells(f"B{r1}:I{r1}")
    c = ws[f"B{r1}"]
    c.value = "Por ronda"
    c.font = Font(name="Calibri", size=12, bold=True, color=BLUE)
    ws.row_dimensions[r1].height = 22

    head2 = ["Ronda", "", "Pruebas", "Pasa", "Falla", "Se reporta", "Bloqueada", "Sin ejecutar"]
    for i, h in enumerate(head2, start=2):
        c = ws.cell(row=r1 + 1, column=i, value=h)
        c.font = Font(name="Calibri", size=9, bold=True, color=WHITE)
        c.fill = fill(BLUE)
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        c.border = Border(left=THIN, right=THIN, top=THIN, bottom=Side(style="medium", color=BLUE))
    ws.row_dimensions[r1 + 1].height = 30
    ws.merge_cells(start_row=r1 + 1, start_column=2, end_row=r1 + 1, end_column=3)
    ws.cell(row=r1 + 1, column=3).fill = fill(BLUE)
    ws.cell(row=r1 + 1, column=3).border = Border(
        left=THIN, right=THIN, top=THIN, bottom=Side(style="medium", color=BLUE))

    rondas = ["Ronda diagnóstica", "Ronda de evaluación"]
    for k, ronda in enumerate(rondas):
        r = r1 + 2 + k
        ws.merge_cells(f"B{r}:C{r}")
        c = ws.cell(row=r, column=2, value=ronda)
        c.font = Font(name="Calibri", size=9, bold=True)
        c.alignment = Alignment(vertical="center")
        # COUNTIFS por hoja, sumados
        suma_total = "=" + "+".join(f'COUNTIF({code}!$Q$4:$Q$200,"{ronda}")' for code, _ in BLOQUES)
        ws.cell(row=r, column=4, value=suma_total)
        for j, estado in enumerate([e[0] for e in ESTADOS]):
            f = "=" + "+".join(
                f'COUNTIFS({code}!$L$4:$L$200,"{estado}",{code}!$Q$4:$Q$200,"{ronda}")'
                for code, _ in BLOQUES)
            ws.cell(row=r, column=5 + j, value=f)
        ws.cell(row=r, column=9, value=f"=D{r}-E{r}-F{r}-G{r}-H{r}")
        for i in range(2, 10):
            c = ws.cell(row=r, column=i)
            if i > 3:
                c.font = Font(name="Calibri", size=9)
                c.alignment = Alignment(horizontal="center")
            c.border = BOX

    r2 = r1 + 2 + len(rondas) + 2
    ws.merge_cells(f"B{r2}:I{r2}")
    c = ws[f"B{r2}"]
    c.value = ("«Sin ejecutar» es la diferencia entre las pruebas de la fila y las que ya tienen estado. "
               "Una prueba puede quedar sin estado en las dos rondas si nadie la corrió: eso es distinto de "
               "«Bloqueada», que se elige cuando se intentó y no se pudo.")
    c.font = Font(name="Calibri", size=8, italic=True, color=GRAY)
    c.alignment = Alignment(wrap_text=True, vertical="top")
    ws.row_dimensions[r2].height = 30

    preparar_impresion(ws)
    ws.page_setup.orientation = "portrait"
    ws.print_title_rows = None
    ws.print_title_cols = None
    ws.print_area = f"A1:I{r2}"
    return ws


# ── Hojas de bloque ───────────────────────────────────────────────────────────
def hoja_bloque(wb, code, nombre, filas):
    ws = wb.create_sheet(code)
    titulo_hoja(ws, f"PR-{code} · {nombre}",
                f"Plan de Pruebas · {len(filas)} prueba{'s' if len(filas) != 1 else ''} · "
                "la ficha viene copiada del plan; se completa de «Estado» en adelante")
    fila_encabezado(ws, 3)

    r = 4
    for pid, f in filas:
        valores = [f[c] for c, _, _ in COLUMNAS if c]
        for i, v in enumerate(valores, start=1):
            ws.cell(row=r, column=i, value=v)
        ws.cell(row=r, column=COL_INDEX["Fecha"]).number_format = "DD/MM/YYYY"
        r += 1

    ultima = r - 1
    estilos_datos(ws, 4, ultima)
    validacion_estado(ws, 4, max(ultima, 40))
    ws.auto_filter.ref = f"A3:{LAST_COL}{ultima}"
    ws.freeze_panes = "B4"
    preparar_impresion(ws, ancho="A3", paginar=True)
    ws.col_breaks.append(Break(id=11))
    # El titulo fusionado sobre 17 columnas se corta al repetirse en cada hoja:
    # se saca del area impresa y el nombre del bloque va en el encabezado.
    # LibreOffice repite siempre las filas 1:3 aunque se pida solo la 3, y el
    # titulo fusionado sobre 17 columnas se corta: se saca del area impresa.
    ws.print_area = f"A3:{LAST_COL}{ultima}"
    ws.oddHeader.center.text = f"PR-{code} · {nombre}"
    ws.oddHeader.center.size = 9
    ws.oddHeader.center.font = "Calibri,Bold"
    ws.oddFooter.right.text = "Página &P de &N"
    ws.oddFooter.right.size = 8
    return ws


def main():
    fichas = leer_tex(TEX)
    ren09 = leer_md_prueba(MD_REN, "PR-REN-09")
    ren09["Bloque"] = BLOQUE_NOMBRE["REN"]
    fichas["PR-REN-09"] = ren09

    por_bloque = {code: [] for code, _ in BLOQUES}
    for pid in sorted(fichas):
        code = pid.split("-")[1]
        por_bloque[code].append((pid, fichas[pid]))

    wb = Workbook()
    wb.remove(wb.active)
    hoja_portada(wb, len(fichas), len(fichas) - 1, True)
    hoja_resumen(wb)
    for code, nombre in BLOQUES:
        hoja_bloque(wb, code, nombre, por_bloque[code])

    wb.properties.title = "Plan de Pruebas — Plantilla de resultados"
    wb.properties.subject = "SignAll"
    wb.properties.creator = "SignAll"
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    wb.save(OUT)
    print(f"escrito {OUT}")
    print(f"pruebas: {len(fichas)}")
    for code, _ in BLOQUES:
        print(f"  {code}: {len(por_bloque[code])}")


if __name__ == "__main__":
    main()
