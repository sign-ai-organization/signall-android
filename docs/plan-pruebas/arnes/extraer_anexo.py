#!/usr/bin/env python3
"""Extrae los conjuntos del Anexo C del plan de pruebas a JSON.

Lee `../signall-docs/latex/plan-pruebas/anexo-c-datos.tex` y escribe un JSON por
conjunto en el directorio de salida. Se ejecuta desde cualquier ruta:

    python3 docs/plan-pruebas/arnes/extraer_anexo.py --salida /tmp/opencode/p1/datos

No transcribe nada a mano: el .tex es la fuente y este script solo lo lee, para
que no exista una segunda copia que se desincronice.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[4]
ANEXO = RAIZ / "signall-docs" / "latex" / "plan-pruebas" / "anexo-c-datos.tex"


def limpiar(texto: str) -> str:
    """Quita los comandos de LaTeX que quedan dentro de una celda.

    Los tres marcadores de la división esperada de CO-01 se conservan como
    caracteres: `‖` corte obligatorio, `|` corte opcional y `…` muletilla.
    """
    texto = texto.replace(r"$\Vert$", "‖")
    texto = texto.replace(r"\textbrokenbar{}", "|")
    texto = texto.replace(r"\ldots{}", "…")
    texto = texto.replace("\\textordmasculine{}", "º")
    texto = texto.replace("\\textquotesingle{}", "'")
    texto = texto.replace("\\textasciitilde{}", "~")
    texto = texto.replace("\\textbackslash{}", "\\")
    texto = texto.replace("\\texttt{", "").replace("\\emph{", "").replace("\\textbf{", "")
    texto = texto.replace("~", " ")
    texto = re.sub(r"\\[a-zA-Z]+\{([^{}]*)\}", r"\1", texto)
    texto = texto.replace("{", "").replace("}", "")
    texto = texto.replace("\\%", "%").replace("\\_", "_").replace("\\#", "#")
    texto = re.sub(r"\\[a-zA-Z]+", "", texto)
    return " ".join(texto.split())


def filas_de_tabla(bloque: str) -> list[list[str]]:
    filas: list[list[str]] = []
    for linea in bloque.splitlines():
        linea = linea.strip()
        if not linea.endswith(r"\\ \hline"):
            continue
        if "&" not in linea:
            continue
        cuerpo = linea[: -len(r"\\ \hline")].strip()
        celdas = [limpiar(c) for c in cuerpo.split("&")]
        filas.append(celdas)
    return filas


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--salida", required=True, type=Path)
    args = ap.parse_args()
    args.salida.mkdir(parents=True, exist_ok=True)

    texto = ANEXO.read_text(encoding="utf-8")
    partes = re.split(r"\\subsubsection\{([A-Z]{2}-\d{2})\.", texto)
    # partes = [preambulo, codigo1, cuerpo1, codigo2, cuerpo2, ...]
    resumen = {}
    for i in range(1, len(partes), 2):
        codigo, cuerpo = partes[i], partes[i + 1]
        tablas = re.findall(r"\\begin\{longtable\}.*?\\end\{longtable\}", cuerpo, flags=re.S)
        filas: list[list[str]] = []
        for tabla in tablas:
            filas.extend(filas_de_tabla(tabla))
        # La primera fila de cada tabla es el encabezado; se descarta por prefijo.
        filas = [f for f in filas if not f[0].startswith("ID") and f[0] != ""]
        # FR-01 trae dos tablas: las 30 frases y los seis subconjuntos fijos.
        subconjuntos = [f for f in filas if re.fullmatch(r"FR-01-[A-F]", f[0])]
        if subconjuntos:
            filas = [f for f in filas if f not in subconjuntos]
            args.salida.joinpath("FR-01-subconjuntos.json").write_text(
                json.dumps(subconjuntos, ensure_ascii=False, indent=1), encoding="utf-8"
            )
            resumen["FR-01-subconjuntos"] = len(subconjuntos)
        args.salida.joinpath(f"{codigo}.json").write_text(
            json.dumps(filas, ensure_ascii=False, indent=1), encoding="utf-8"
        )
        resumen[codigo] = len(filas)

    print(json.dumps(resumen, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
