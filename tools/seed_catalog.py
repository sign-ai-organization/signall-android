#!/usr/bin/env python3
"""Siembra `catalog` en Neon con las 50 glosas de LSC50.

Las 50 salen de la **Table 2** del paper (doi 10.1038/s41597-024-04172-5), no
de los nombres de archivo: son las que definen el índice `NNNN`, que es lo que
después empareja `signs/NNNN_0000_0000.glb`. Verificado contra los 32 archivos
que hay hoy en la app: 32/32 coinciden, o sea que el modelador numeró bien.

Glosa **sin tilde y sin signos de interrogación** (`BUENOS DÍAS` → `BUENOS
DIAS`, `¿QUÉ?` → `QUE`). Es lo que produce el prompt del backend, que dice
explícitamente "UPPERCASE, no accent marks" y "no punctuation". La Ñ se
conserva, porque el prompt dice "keep Ñ as is".

Ese canonicado solo funciona si el backend normaliza antes de buscar: hoy hace
`self.cache.get(gloss)` sobre un dict, o sea comparación exacta, y en producción
el LLM escribe a veces con tilde y a veces sin (`COMO` y `CÓMO` conviven en
`semantic_cache`). El fix del backend es de una línea en `CatalogRepository`; sin
él, las glosas acentuadas caen al fallback de la app.

Uso:

    export $(grep -o '^DATABASE_URL=.*' .envJulian | head -1 | xargs)
    python3 tools/seed_catalog.py            # muestra el SQL, no ejecuta
    python3 tools/seed_catalog.py --apply    # lo ejecuta

Es idempotente: reemplaza la tabla entera por las 50 filas.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MANIFEST = REPO / "app/src/main/assets/web/manifest.json"
CREDENTIALS = REPO / ".envJulian"

# Table 2 del paper: (N, glosa del paper, categoría gramatical).
# El orden de las columnas es el del paper; N es el índice de la clase.
LSC50 = [
    (0,  "GRACIAS", "sustantivo"),
    (1,  "BUENOS DÍAS", "locución"),
    (2,  "BUENAS TARDES", "locución"),
    (3,  "BUENAS NOCHES", "locución"),
    (4,  "SEÑA", "sustantivo"),
    (5,  "NOMBRE", "sustantivo"),
    (6,  "TRABAJAR", "verbo"),
    (7,  "COMER", "verbo"),
    (8,  "VIVIR", "verbo"),
    (9,  "POCO", "cuantificador"),
    (10, "FAMILIA", "sustantivo"),
    (11, "PERSONAS", "sustantivo"),
    (12, "MUJER", "sustantivo"),
    (13, "HOMBRE", "sustantivo"),
    (14, "NIÑO", "sustantivo"),
    (15, "NIÑA", "sustantivo"),
    (16, "ABUELO", "sustantivo"),
    (17, "TÍO", "sustantivo"),
    (18, "HERMANO", "sustantivo"),
    (19, "HAMBRE", "sustantivo"),
    (20, "FELIZ", "adjetivo"),
    (21, "CONTENTO", "adjetivo"),
    (22, "TRISTE", "adjetivo"),
    (23, "ABURRIDO", "adjetivo"),
    (24, "BIEN", "adverbio"),
    (25, "MAL", "adverbio"),
    (26, "¿CÓMO ESTÁS?", "interjección"),
    (27, "MÁS O MENOS", "locución"),
    (28, "SENTIR", "verbo"),
    (29, "JUICIOSO", "adjetivo"),
    (30, "HOLA", "interjección"),
    (31, "ADIÓS", "interjección"),
    (32, "POR FAVOR", "locución"),
    (33, "CON GUSTO", "locución"),
    (34, "BIENVENIDO", "interjección"),
    (35, "PERDÓN", "sustantivo"),
    (36, "PERMISO", "sustantivo"),
    (37, "NUNCA", "adverbio"),
    (38, "YO", "pronombre"),
    (39, "TÚ", "pronombre"),
    (40, "USTEDES", "pronombre"),
    (41, "¿QUÉ?", "interrogativo"),
    (42, "¿CUÁNDO?", "interrogativo"),
    (43, "¿DÓNDE?", "interrogativo"),
    (44, "¿CÓMO?", "interrogativo"),
    (45, "¿POR QUÉ?", "interrogativo"),
    (46, "¿QUIÉN?", "interrogativo"),
    (47, "DIFERENTE", "adjetivo"),
    (48, "TODOS", "determinante"),
    (49, "MUCHO", "cuantificador"),
]


def canonical(gloss: str) -> str:
    """Quita tildes y signos de interrogación, conserva la Ñ."""
    for a, b in (("Á", "A"), ("É", "E"), ("Í", "I"),
                 ("Ó", "O"), ("Ú", "U"), ("Ü", "U")):
        gloss = gloss.replace(a, b)
    return gloss.replace("¿", "").replace("?", "").strip().upper()


def load_assets() -> dict[str, dict]:
    """Metadatos de las señas que sí están en el APK, por sign_id."""
    if not MANIFEST.is_file():
        return {}
    return {r["sign_id"]: r for r in json.loads(MANIFEST.read_text("utf-8"))}


def build_sql(assets: dict[str, dict]) -> str:
    values = []
    for n, paper_gloss, category in LSC50:
        gloss = canonical(paper_gloss)
        path = f"signs/{n:04d}_0000_0000.glb"
        asset = assets.get(f"{n:04d}")
        fps = asset["fps"] if asset else None
        frames = asset["frames"] if asset else None
        size = asset["bytes"] if asset else None
        sha = asset["sha256"] if asset else None
        values.append(
            f"({n}, {_q(gloss)}, {_q(category)}, {_q(path)}, "
            f"{fps or 'NULL'}, {frames or 'NULL'}, {size or 'NULL'}, "
            f"{_q(sha) if sha else 'NULL'})"
        )

    return f"""-- Seed de `catalog` con las 50 glosas de LSC50.
-- Generado por tools/seed_catalog.py desde la Table 2 del paper
-- (doi 10.1038/s41597-024-04172-5). No editar a mano: se regenera entero.
--
-- `glb_animation_path` es un path same-origin DENTRO del APK: la app resuelve
-- ese nombre contra las animaciones que vienen adentro del `.glb` del avatar
-- (`models/juan.glb` o `models/pauline.glb`), no lo descarga del backend. Por eso
-- las señas sin animación exportada
-- (fps/frames/bytes/sha256 en NULL) también están: el backend las anuncia igual
-- y la app cae al sign por defecto (RF-CAT-03).

BEGIN;

-- Las columnas de metadatos del asset. Nullable a propósito: una seña sin
-- animación exportada no tiene de dónde sacarlos.
ALTER TABLE catalog
    ADD COLUMN IF NOT EXISTS fps          integer,
    ADD COLUMN IF NOT EXISTS total_frames integer,
    ADD COLUMN IF NOT EXISTS asset_bytes  bigint,
    ADD COLUMN IF NOT EXISTS asset_sha256 text;

-- Reemplazo completo. Las 3 filas previas (CASA, COMER, CUADERNO) apuntaban a
-- /static/glb/*.glb, que el backend nunca sirvió; COMER además quedó con un
-- id_sign que no era el de su clase (0007) y un glb_path sin relación con el
-- nombre de archivo.
DELETE FROM catalog;

INSERT INTO catalog
    (id_sign, gloss, semantic_category, glb_animation_path,
     fps, total_frames, asset_bytes, asset_sha256)
VALUES
    {",\n    ".join(values)};

COMMIT;
"""


def _q(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="ejecuta (sin esto solo imprime)")
    args = ap.parse_args()

    if len(LSC50) != 50:
        sys.exit(f"la lista tiene {len(LSC50)} entradas, no 50")

    assets = load_assets()
    sql = build_sql(assets)

    have = len(assets)
    missing = [f"{n:04d}" for n, *_ in LSC50 if f"{n:04d}" not in assets]
    print(f"  glosas       : {len(LSC50)} (LSC50, Table 2 del paper)")
    print(f"  con animación: {have}")
    print(f"  sin animación: {len(missing)}"
          + (f"  ({missing[0]}–{missing[-1]}, aún no exportadas)" if missing else ""))
    print()

    if not args.apply:
        print(sql)
        print("  (no se ejecutó: usá --apply)")
        return

    url = database_url()
    proc = subprocess.run(
        ["psql", url, "-v", "ON_ERROR_STOP=1", "-q"],
        input=sql, text=True, capture_output=True,
    )
    if proc.returncode != 0:
        sys.exit(f"psql falló:\n{proc.stderr}")
    print("  aplicado.")

    check = subprocess.run(
        ["psql", url, "-At", "-c",
         "SELECT count(*), count(fps), count(*) FILTER (WHERE gloss <> upper(gloss)) "
         "FROM catalog;"],
        text=True, capture_output=True,
    )
    print(f"  filas | con asset | glosas no canónicas: {check.stdout.strip()}")


def database_url() -> str:
    """Lee DATABASE_URL de .envJulian sin usar `source` (tiene `&` sin comillas)."""
    if not CREDENTIALS.is_file():
        sys.exit(f"no está {CREDENTIALS}")
    for line in CREDENTIALS.read_text("utf-8").splitlines():
        if line.startswith("DATABASE_URL="):
            return line.split("=", 1)[1].strip().strip("'\"")
    sys.exit(f"{CREDENTIALS} no tiene DATABASE_URL")


if __name__ == "__main__":
    main()