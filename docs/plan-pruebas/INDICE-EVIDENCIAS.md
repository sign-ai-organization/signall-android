# Índice de las evidencias del plan de pruebas

Las evidencias pesadas **no están en el repositorio**. Desde el **2026-11-13** viven
fuera del clon, en `../data/evidencias/`, al lado de los repos hermanos y de
`../material-3d/`. Pesaban 242 MB y fueron 1 690 de los 1 736 archivos de `docs/`.

Acá se quedan el índice, la metodología, el arnés y la plantilla: lo que se lee para
repetir una ronda. Lo que se guarda para armar un anexo está afuera.

## Dónde está cada cosa

| Qué | Dónde | Qué es |
|---|---|---|
| Evidencias de las rondas | `../data/evidencias/plan-pruebas/evidencias/` | una carpeta por bloque (`AND`, `AVT`, `INT`, `REN`, `RES`, `SEC`, `TRA`, `VAL`, `CAC`, …) |
| Audios de origen y fixtures | `../data/evidencias/plan-pruebas/audios/` | los `.wav`/`.m4a` que se usaron como entrada |
| Libros de resultados y consolidados | `../data/evidencias/plan-pruebas/*.xlsx` | las copias llenas de la plantilla, por ronda |
| Vistas previas | `../data/evidencias/plan-pruebas/vista-previa/` | capturas de las hojas |
| Evidencias del avatar | `../data/evidencias/avatar/` | transiciones, reposo y humanización (videos y `README` por carpeta) |

`../data/` no es un repositorio y no se versiona: es la unidad de trabajo de la máquina,
igual que `../material-3d/`. Si algún día las evidencias tienen que viajar, se copian de
ahí a un repositorio aparte; no se vuelven a meter en este.

## Cómo se leen los nombres

```
PR-<BLOQUE>-<NN>_<R>_<AAAAMMDD>_<nn>.<ext>
```

`<R>` es `D` en la ronda diagnóstica y `E` en la de evaluación; `<nn>` es el consecutivo
de las varias piezas de una misma prueba el mismo día. La tabla completa, con los
códigos de bloque y las extensiones, está en
[`README.md`](README.md#cómo-se-nombran-las-evidencias).

## Los informes citan rutas viejas

Los reportes que se movieron a [`../historia/plan-pruebas/`](../historia/plan-pruebas/)
escriben sus enlaces relativos al `docs/plan-pruebas/` de ese día. Para leerlos hoy:

| Si el informe dice | El archivo está en |
|---|---|
| `evidencias/<BLOQUE>/…` | `../data/evidencias/plan-pruebas/evidencias/<BLOQUE>/…` |
| `audios/…` | `../data/evidencias/plan-pruebas/audios/…` |
| `resultados-*.xlsx` | `../data/evidencias/plan-pruebas/resultados-*.xlsx` |
| `docs/evidencias/<x>/…` | `../data/evidencias/avatar/<x>/…` |

No se reescribieron esos enlaces dentro de los informes: son el registro de lo que se
midió ese día y se citan textuales en la tesis.

## Dos archivos que ya no están

`PR-CAC-05_D_20261104_01.json` y `PR-CAC-05_E_20261105_01.json` guardaban el volcado
completo del `.env` del backend —contraseña de Neon y clave de OpenAI— dentro del campo
`env`. Salieron del árbol con esta limpieza, pero **siguen en la historia de git**:
descomprometen solo si el owner **rota las dos credenciales**, que es lo que pide
[`../../reestructuracion/01-leaks.md`](../../reestructuracion/01-leaks.md).
