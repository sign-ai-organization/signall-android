# Entradas 04: 0030–0039

**2026-11-11. Estado:** diez entradas aprobadas, incluidas salidas nuevas de 0038/0039. El owner eligió conservar
`0020` v3 y continuar. Copias aprobadas anteriores intactas.

| Seña | Reposo nuevo hasta | Unión al gesto | Revisión |
|---|---:|---:|---|
| `0030` | 1/24 s | 6/24 = 0,250 s | Aprobada; regreso anterior intacto |
| `0031` | 12/24 = 0,500 s | 17/24 = 0,7083 s | Aprobada; regreso anterior intacto |
| `0032` | 6/24 = 0,250 s | 12/24 = 0,500 s | Aprobada; regreso anterior intacto |
| `0033` | 10/24 = 0,4167 s | 16/24 = 0,6667 s | Aprobada; regreso anterior intacto |
| `0034` | 10/24 = 0,4167 s | 16/24 = 0,6667 s | Aprobada; regreso anterior intacto |
| `0035` | 2/24 = 0,0833 s | 8/24 = 0,3333 s | Aprobada; regreso anterior intacto |
| `0036` | 10/24 = 0,4167 s | 17/24 = 0,7083 s | Aprobada; regreso anterior intacto |
| `0037` | 10/24 = 0,4167 s | 16/24 = 0,6667 s | Aprobada; regreso anterior intacto |
| `0038` | 10/24 = 0,4167 s | 16/24 = 0,6667 s | Entrada y regreso aprobados |
| `0039` | 3/24 = 0,125 s | 9/24 = 0,375 s | Entrada y regreso aprobados |

El owner respondió **«todo bien, ya termina las señas, lo que queda»**. Se
registran las diez aprobadas sin correcciones, incluidas ambas salidas nuevas.
No se atribuye revisión desglosada por avatar/variante ni validación LSC formal.

`0038/0039` no tenían regreso revisado. Se proponen ambos extremos, señalado
en el visor: salidas **1,500–2,0417 s** y **0,875–1,4583 s**, respectivamente.
No se presentan como aprobadas. Ninguna añade duración; solo seis rotaciones
por clip, slerp/peso quíntico. Cara, dedos, otros canales, nodos/tiempos intactos.

Ventanas provisionales por inspección de codos de Juan, afirmativa, no fronteras
LSC certificadas. Preparación temprana en `0030/35/39`, espera más larga en otras.
`0038` sostiene postura hasta la zona de 1,458 s y baja después; `0039` es corta
y empieza a bajar hacia 0,9 s. Revisar gesto/contactos en ambas preparaciones y
salidas, sin reutilizar un tiempo común.

Desde la unión, las muestras son exactas al aprobado en las ocho primeras.
En las dos nuevas, exactas entre unión y ancla de regreso; después se sustituye
la salida, alcanzando reposo exacto. No se filtra movimiento central.

## Evidencia y reproducción

Visor: **http://127.0.0.1:8766/entries-04/**. Izquierda: ocho con regresos aprobados
y dos originales; derecha: propuestas. Mismo final v3, sin encadenamiento.
Generar antes `group-07/08` como carpetas hermanas; ejecutar `prepare.py` y
`check.py` desde raíz, servir por HTTP. Three.js requiere jsDelivr.

- `verification.json`: hashes, uniones, esperas y dos nuevas salidas no aprobadas.
- `checks.json`: 40 clips por avatar en GLB serializados; reposo inicial, ocho
  regresos exactos, dos nuevos alcanzan reposo, muestras protegidas/conteos válidos
  y otras 160 animaciones intactas.
- `browser-checks.json`: 80 selecciones/render en Chromium headless (diez señas,
  cuatro variantes, dos avatares). Capturas afirmativas a 0,05 s local; no son
  pruebas de fluidez/contactos ni validación LSC.
- Fuentes originales intactas: `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según inventario de empaquetado.

Diez aprobadas, integración pendiente. App intacta; sin
integración, build ni instalación por esta prueba externa.
