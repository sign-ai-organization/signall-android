# Índice de las evidencias del plan de pruebas

Las evidencias pesadas **no están en este repositorio**. Desde el **2026-11-14** viven
versionadas en `../signall-docs/evidencias/`, el repositorio de documentos de la tesis.
Pesan 240 MB y fueron 1 636 de los 1 736 archivos que tenía `docs/` antes de la limpieza.

Acá se quedan el índice, la metodología, el arnés y la plantilla: lo que se lee para
repetir una ronda. Lo que se guarda para armar un anexo está en `signall-docs`.

## Dónde está cada cosa

| Qué | Dónde | Qué es |
|---|---|---|
| Evidencias de las rondas | `../signall-docs/evidencias/plan-pruebas/evidencias/` | una carpeta por bloque (`AND`, `AVT`, `INT`, `REN`, `RES`, `SEC`, `TRA`, `VAL`, `CAC`, …) |
| Audios de origen y fixtures | `../signall-docs/evidencias/plan-pruebas/audios/` | los `.wav`/`.m4a` que se usaron como entrada |
| Libros de resultados y consolidados | `../signall-docs/evidencias/plan-pruebas/*.xlsx` | las copias llenas de la plantilla, por ronda |
| Vistas previas | `../signall-docs/evidencias/plan-pruebas/vista-previa/` | capturas de las hojas |
| Evidencias del avatar | `../signall-docs/evidencias/avatar/` | transiciones, reposo y humanización (videos y `README` por carpeta) |

Las evidencias son material de consulta: no se editan. La estructura, el origen y la nota
sobre las credenciales redactadas están en `../signall-docs/evidencias/README.md`.

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
| `evidencias/<BLOQUE>/…` | `../signall-docs/evidencias/plan-pruebas/evidencias/<BLOQUE>/…` |
| `audios/…` | `../signall-docs/evidencias/plan-pruebas/audios/…` |
| `resultados-*.xlsx` | `../signall-docs/evidencias/plan-pruebas/resultados-*.xlsx` |
| `docs/evidencias/<x>/…` | `../signall-docs/evidencias/avatar/<x>/…` |

No se reescribieron esos enlaces dentro de los informes: son el registro de lo que se
midió ese día y se citan textuales en la tesis.

## Dos archivos que ya no están

`PR-CAC-05_D_20261104_01.json` y `PR-CAC-05_E_20261105_01.json` guardaban el volcado
completo del `.env` del backend —contraseña de Neon y clave de OpenAI— dentro del campo
`env`. Salieron del árbol con esta limpieza y, el **2026-11-14**, también salieron de la
historia: se reescribió el repositorio con `git-filter-repo` para purgar
`docs/plan-pruebas/evidencias/`, `docs/plan-pruebas/audios/` y `docs/evidencias/`. Ya no
existen en ningún commit.

Las dos credenciales se rotaron y se comprobaron muertas antes de eso (Neon respondió
`password authentication failed` y OpenAI un HTTP 401), así que la reescritura fue una
limpieza, no un rescate. El mapa de hashes viejos → nuevos está en
[`../historia/commit-map-2026-11-14.txt`](../historia/commit-map-2026-11-14.txt).
