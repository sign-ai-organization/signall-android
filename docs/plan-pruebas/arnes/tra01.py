#!/usr/bin/env python3
"""PR-TRA-01 · Separación de componentes.

Lee el código del backend y arma el grafo de importaciones entre los cuatro
componentes del SDD (sesión/transcripción, orquestación, procesamiento
semántico y catálogo/validación). Una arista pasa por interfaz cuando el
símbolo importado es una ABC o un Protocol; cualquier otra cosa —una clase
concreta, una función, un módulo entero— cuenta como salto.

    python3 docs/plan-pruebas/arnes/tra01.py --backend ../sign-ai-backend \
        --salida /tmp/opencode/p1-2026-11-10/evidencias

No importa el backend ni necesita sus dependencias: solo lee el AST.
"""

from __future__ import annotations

import argparse
import ast
import csv
import json
from datetime import date
from pathlib import Path

COMPONENTES = {
    # El cliente del STT es el componente de transcripción; el controlador que
    # lo usa es el de sesión. Se distinguen por módulo, como en la ronda del 6/11.
    "app.controller": "sesion",
    "app.orchestrator": "orquestacion",
    "app.nlp": "procesamiento_semantico",
    "app.validation": "catalogo_validacion",
}

# Los puntos de composición cablean las implementaciones a propósito: no son
# aristas entre componentes y la ronda del 6/11 tampoco los contó.
EXCLUIDOS = {"app/main.py", "app/controller/router.py"}


def componente_de(modulo: str) -> str | None:
    # El cliente del STT y su implementación son el componente de transcripción,
    # aunque vivan en la carpeta del controlador.
    if "stt_service_client" in modulo:
        return "transcripcion"
    for prefijo, nombre in COMPONENTES.items():
        if modulo == prefijo or modulo.startswith(prefijo + "."):  # límite de módulo, no de prefijo
            return nombre
    return None


def modulo_de_archivo(ruta: Path, raiz: Path) -> str:
    rel = ruta.relative_to(raiz).with_suffix("")
    partes = list(rel.parts)
    if partes and partes[-1] == "__init__":
        partes = partes[:-1]
    return ".".join(partes)


def interfaces_declaradas(arbol: ast.Module) -> set[str]:
    """Nombres de clase que son ABC, Protocol o tienen métodos abstractos."""
    nombres: set[str] = set()
    for nodo in ast.walk(arbol):
        if not isinstance(nodo, ast.ClassDef):
            continue
        bases = {ast.unparse(b).split(".")[-1] for b in nodo.bases}
        if bases & {"ABC", "Protocol"}:
            nombres.add(nodo.name)
            continue
        for hijo in nodo.body:
            if isinstance(hijo, ast.FunctionDef) and any(
                ast.unparse(d).endswith("abstractmethod") for d in hijo.decorator_list
            ):
                nombres.add(nodo.name)
                break
    return nombres


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--backend", required=True, type=Path)
    ap.add_argument("--salida", required=True, type=Path)
    # La evidencia se numera por corrida: la primera del día es _01 y las
    # siguientes _02, _03… (una prueba repetida después de un arreglo no
    # pisa la evidencia de la corrida anterior).
    ap.add_argument("--indice", type=int, default=1)
    args = ap.parse_args()
    args.salida.mkdir(parents=True, exist_ok=True)
    backend = args.backend.resolve()
    raiz = backend / "app"

    # Qué interfaces declara cada módulo, para clasificar el símbolo importado.
    interfaces: dict[str, set[str]] = {}
    arboles: dict[Path, ast.Module] = {}
    for ruta in sorted(raiz.rglob("*.py")):
        if "__pycache__" in ruta.parts:
            continue
        arbol = ast.parse(ruta.read_text(encoding="utf-8"))
        arboles[ruta] = arbol
        interfaces[modulo_de_archivo(ruta, raiz.parent)] = interfaces_declaradas(arbol)

    aristas = []
    for ruta, arbol in arboles.items():
        if str(ruta.relative_to(backend)) in EXCLUIDOS:
            continue
        origen_modulo = modulo_de_archivo(ruta, raiz.parent)
        origen_componente = componente_de(origen_modulo)
        if origen_componente is None:
            continue
        for nodo in ast.walk(arbol):
            if isinstance(nodo, ast.ImportFrom) and nodo.module:
                destino_modulo = nodo.module
                if nodo.level:
                    # Import relativo: se resuelve contra el paquete del archivo.
                    paquete = origen_modulo.split(".")[:-1]
                    subida = paquete[: len(paquete) - (nodo.level - 1)] if nodo.level > 1 else paquete
                    destino_modulo = ".".join(subida + ([nodo.module] if nodo.module else []))
                destino_componente = componente_de(destino_modulo)
                if not destino_componente or destino_componente == origen_componente:
                    continue
                for alias in nodo.names:
                    simbolo = alias.name
                    es_interfaz = simbolo in interfaces.get(destino_modulo, set())
                    if simbolo == "*":
                        es_interfaz = False
                    aristas.append(
                        {
                            "desde": str(ruta.relative_to(backend)),
                            "desde_componente": origen_componente,
                            "hacia": destino_modulo,
                            "hacia_componente": destino_componente,
                            "simbolo": simbolo,
                            "clase": "interfaz (ABC/Protocol)" if es_interfaz else "implementación concreta o función",
                            "pasa_por_interfaz": "sí" if es_interfaz else "no",
                        }
                    )
            elif isinstance(nodo, ast.Import):
                for alias in nodo.names:
                    destino_componente = componente_de(alias.name)
                    if not destino_componente or destino_componente == origen_componente:
                        continue
                    aristas.append(
                        {
                            "desde": str(ruta.relative_to(backend)),
                            "desde_componente": origen_componente,
                            "hacia": alias.name,
                            "hacia_componente": destino_componente,
                            "simbolo": alias.name.split(".")[-1],
                            "clase": "módulo completo",
                            "pasa_por_interfaz": "no",
                        }
                    )

    aristas.sort(key=lambda a: (a["desde"], a["hacia"], a["simbolo"]))
    saltos = [a for a in aristas if a["pasa_por_interfaz"] == "no"]
    resumen = {
        "aristas": len(aristas),
        "pasan_por_interfaz": len(aristas) - len(saltos),
        "se_saltan_la_interfaz": len(saltos),
        "saltos": saltos,
        "detalle": aristas,
    }

    etiqueta = f"PR-TRA-01_E_{date.today().strftime('%Y%m%d')}_{args.indice:02d}"
    with (args.salida / f"{etiqueta}.csv").open("w", newline="", encoding="utf-8") as f:
        campos = ["desde", "desde_componente", "hacia", "hacia_componente", "simbolo", "clase", "pasa_por_interfaz"]
        w = csv.DictWriter(f, fieldnames=campos, delimiter=";")
        w.writeheader()
        w.writerows(aristas)
    (args.salida / f"{etiqueta}.json").write_text(
        json.dumps(resumen, ensure_ascii=False, indent=1), encoding="utf-8"
    )
    print(json.dumps({k: resumen[k] for k in ("aristas", "pasan_por_interfaz", "se_saltan_la_interfaz")}))
    for s in saltos:
        print("  salto:", s["desde"], "->", s["hacia"], s["simbolo"])


if __name__ == "__main__":
    main()
