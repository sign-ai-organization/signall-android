#!/usr/bin/env python3
# Reparte los fotogramas que tiro `gfxinfo.py` en cubos de tiempo.
#
# `gfxinfo.py` dice cuanto costo cada fotograma. Este dice CUANDO: cuanto duro
# la animacion que dispara una accion, y si la app se queda redibujando sola
# cuando ya no la toca nadie.
#
# ## Las dos preguntas
#
# 1. **Cuanto duro el efecto.** Si la animacion dura 40 fotogramas, es un
#    fotograma cada 16,67 ms y el ojo no la nota. Si dura 12 y cada uno tardó
#    40 ms, se ve a tirones aunque la mediana sea buena. Los dos son el mismo
#    "60 % de jank" con consecuencias opuestas.
#
# 2. **Se detiene sola.** Si despues del gesto hay fotogramas con una cadencia
#    regular, la app esta animando o repintando sin que nadie la toque. En esta
#    app el candidato obvio es el render loop de Three.js dentro del WebView,
#    que corre siempre. Que la app gaste GPU en reposo no es un bug; lo que
#    importa es ver si el numero es parejo (loop estable) o a tirones (jank en
#    reposo, que si lo es).
#
# ## Uso
#
#   frames.py <crudo.jsonl> [ancho_bucket_ms]
#
# El ancho de cubo por defecto es 100 ms, NO el presupuesto del fotograma: a
# 60 fps hay un fotograma por cubo y la forma del grafico no dice nada. Con
# 100 ms se ve de un vistazo cuando arranca el efecto, cuando termina y si
# hay tramos vacios (la app quedo quieta).
#
# El JSONL lo produce `gfxinfo.py --jsonl`:
#
#   tools/rendimiento/gfxinfo.py reproducir --sec 4 --jsonl /tmp/t.jsonl \
#       --run "adb -s 1ed6699f shell 'input tap 540 2000; sleep 3'"
#   tools/rendimiento/frames.py /tmp/t.jsonl
import json
import sys
from collections import Counter

BUDGET_MS = 1000.0 / 60.0


def bucket(t, ancho):
    return int(t // ancho) * ancho


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        raise SystemExit(2)
    ruta = sys.argv[1]
    ancho = float(sys.argv[2]) if len(sys.argv) > 2 else 100.0

    filas = [json.loads(l) for l in open(ruta) if l.strip()]
    if not filas:
        print(f"{ruta}: vacio. Correr primero gfxinfo.py --jsonl")
        raise SystemExit(1)

    # `intended` esta en ns monotonos del reloj de gfxinfo.
    t0 = filas[0]["intended"]
    filas.sort(key=lambda d: d["intended"])

    print(f"{len(filas)} fotogramas en {ancho:g} ms por cubo "
          f"(presupuesto {BUDGET_MS:.2f} ms)")
    print()

    cubos = Counter()
    for d in filas:
        cubos[bucket((d["intended"] - t0) / 1e6, ancho)] += 1

    for b in sorted(cubos):
        c = cubos[b]
        barra = "#" * min(60, c)
        print(f"  {b/1000:6.2f} s  {c:4d} fotogramas  {barra}")

    # Cuanto duro el efecto: desde el primer fotograma hasta el ultimo, en
    # tiempo de pared. No es lo mismo que la cantidad de fotogramas: si la app
    # dibuja 60 por segundo el efecto duro lo que dice el reloj.
    dur = (filas[-1]["intended"] - t0) / 1e6
    print()
    print(f"  duracion de la ventana medida: {dur/1000:.2f} s")
    print(f"  cadencia real: {len(filas)/dur*1000:.1f} fotogramas por segundo "
          f"(el panel da 60)")

    jank = sum(1 for d in filas if d["jank"])
    print(f"  jank en la ventana: {jank}/{len(filas)} ({100*jank/len(filas):.1f} %)")


if __name__ == "__main__":
    main()