# Último grupo: 0040–0049, ambos extremos

**2026-11-11. Estado:** diez aprobadas e integradas con el resto.
El owner aprobó entradas 04, incluidas salidas de `0038/0039`, pidió terminar
las restantes y después respondió **«Todo confirmado. Sigue.»**: se registran
las diez sin correcciones y se integran. No se atribuye revisión desglosada por
avatar/variante ni validación lingüística formal.

| Seña | Reposo inicial hasta | Unión al gesto | Regreso nuevo (inicio → llegada) | Revisión |
|---|---:|---:|---:|---|
| `0040` | 1/24 s | 0,250 s | 0,9583 → 1,3750 s | Pendiente |
| `0041` | 0,3333 s | 0,5417 s | 1,5000 → 2,0833 s | Pendiente |
| `0042` | 0,1667 s | 0,4167 s | 1,5833 → 2,1667 s | Pendiente |
| `0043` | 0,1667 s | 0,4167 s | 1,5000 → 2,0417 s | Pendiente |
| `0044` | 0,3750 s | 0,6250 s | 1,7500 → 2,1250 s | Pendiente |
| `0045` | 0,2500 s | 0,5000 s | 1,8750 → 2,5000 s | Pendiente |
| `0046` | 0,2917 s | 0,5000 s | 1,7083 → 2,1667 s | Pendiente |
| `0047` | 0,1250 s | 0,3333 s | 1,7917 → 2,1667 s | Pendiente |
| `0048` | 0,1667 s | 0,4167 s | 1,6250 → 2,0000 s | Pendiente |
| `0049` | 0,3750 s | 0,6250 s | 1,5000 → 2,0000 s | Pendiente |

Tiempos de clip; fracciones exactas de 24 fps en `verification.json` y scripts.
Estas diez **no tenían regresos aprobados**: se revisan entrada y salida, no se
da ninguna por aprobada antes de mostrarla. Sin duración añadida.

Ventanas provisionales tras inspección de curvas de codos de Juan, afirmativa,
no fronteras lingüísticas certificadas. `0040` es corta y conserva la actividad
del codo derecho hasta su meseta de la zona de 0,917 s. `0045` es más larga y
sostiene flexión hasta la zona de 1,792 s. Las otras anclas se eligen según sus
posturas sostenidas y comienzo del descenso, no por un porcentaje global.
Revisar forma/contactos y que no se recorte parte del gesto en los extremos.

Solo seis rotaciones por clip (`upper_arm`, `forearm`, `hand`, ambos lados):
reposo nuevo durante la espera y preparación directa al gesto; salida directa al
reposo nuevo con slerp/peso quíntico. Muestras entre unión y ancla de regreso
exactas al original; dedos, cara, otros canales, geometría, nodos y tiempos intactos.
La corrección específica de `0020` no se aplica al resto de señas por inercia.

## Evidencia y reproducción

Visor temporal: **http://127.0.0.1:8766/entries-05/**.
Izquierda: asset anterior; derecha: propuesta de ambos extremos. Mismo acomodo
final v3, ambos avatares/cuatro variantes. No se prueba encadenamiento.

Ejecutar `prepare.py` y `check.py` desde raíz: generan/verifican copias junto al
script desde los assets actuales. Servir esta carpeta por HTTP; Three.js requiere
jsDelivr. No depende de candidatos de otros grupos para generar estas diez.

- `verification.json`: hash de entrada, esperas, uniones y diez salidas nuevas.
- `checks.json`: 40 clips por avatar en GLB serializados, seis rotaciones iniciales
  en reposo, diez regresos alcanzan reposo, muestras protegidas/conteos válidos y
  otras 160 animaciones intactas.
- `browser-checks.json`: 80 selecciones/render en Chromium headless (diez señas,
  cuatro variantes, dos avatares). Capturas afirmativas a 0,05 s local; no son
  prueba de fluidez, contactos ni validez LSC.
- Fuentes originales intactas: `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según inventario del empaquetado.

Las diez quedaron aprobadas y **ya están integradas** por sello con entradas
01–04, `0020` v3 y `0028` completa. La integración no copia un GLB de grupo: usa
`tools/approved_tracks.json.gz` y lo verifica `tools/check_approved_tracks.py`.
Evidencia: `../integracion/README.md`.

Pendiente la prueba en el teléfono: el Redmi se desconectó del USB durante el
`installDebug` de la integración. No se validaron lingüísticamente las señas:
la aprobación es visual.
