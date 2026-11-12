# Grupo 08: 0033–0037

**2026-11-11. Estado:** regresos aprobados; entradas reabiertas por criterio nuevo. Se retoman los
grupos tras aprobar salto y entrada desde reposo nuevo de `0028`. Las propuestas
aprobadas anteriores no se modifican.

| Seña | Ancla del regreso (tiempo de clip) | Llegada al reposo | Revisión | Integración |
|---|---:|---:|---|---|
| `0033` | 38/24 = 1,5833 s | 48/24 = 2,000 s | Regreso aprobado; entrada pendiente | Pendiente |
| `0034` | 48/24 = 2,000 s | 56/24 = 2,3333 s | Regreso aprobado; entrada pendiente | Pendiente |
| `0035` | 32/24 = 1,3333 s | 45/24 = 1,875 s | Regreso aprobado; entrada pendiente | Pendiente |
| `0036` | 38/24 = 1,5833 s | 48/24 = 2,000 s | Regreso aprobado; entrada pendiente | Pendiente |
| `0037` | 44/24 = 1,8333 s | 51/24 = 2,125 s | Regreso aprobado; entrada pendiente | Pendiente |

El owner confirmó «ya revisé todo el grupo y está bien», pero pidió pausar los
grupos nuevos y atender también el reposo nuevo **al inicio** de este grupo y de
todos los anteriores. No se interpreta la aprobación del regreso como aprobación
de esas entradas. Criterio/seguimiento en `../../../TRANSICIONES-REPOSO.md`.

Límites provisionales, no fronteras lingüísticas certificadas. Inspección de
curvas de antebrazos de Juan, afirmativa: `0033` conserva una meseta de flexión
hasta aproximadamente 1,542 s; `0034` sostiene postura hasta la zona de 1,917 s,
por lo que se propone regresar más tarde; `0035` mantiene flexión hasta 1,292 s;
`0036` conserva el movimiento del codo izquierdo hasta la zona de 1,542 s;
`0037` mantiene postura hasta cerca de 1,792 s. Revisar que los regresos no
eliminen contacto ni componentes del gesto.

Solo seis rotaciones por variante (`upper_arm`, `forearm`, `hand`, ambos lados),
regreso directo por slerp/peso quíntico al reposo aprobado. Entradas y muestras
hasta el ancla exactas. No se aplica la corrección inicial específica de `0028`.
Dedos, cara, otros canales, nodos, geometría y tiempos intactos; otras 180
animaciones por avatar sin cambios. Copias externas, no assets de la app.

El visor compara actual (izquierda) con propuesta y acomodo final v3 de 700 ms
(derecha). Puede solaparse con el regreso; revisar el conjunto de movimiento y
salida. No se prueba encadenamiento entre señas.

## Evidencia y reproducción

Visor temporal: **http://127.0.0.1:8766/group-08/**.
Ejecutar `prepare.py` y `check.py` desde la raíz del repo; generan/verifican copias
junto al script. Servir carpeta por HTTP. Enlaces anteriores requieren carpetas
hermanas; Three.js necesita jsDelivr.

- `verification.json`: hashes de entrada y ventanas de ambos avatares.
- `checks.json`: 120 canales por avatar; normalización, entradas/muestras
  protegidas exactas, regreso angular monótono y extremo en reposo.
- `browser-checks.json`: 40 selecciones/render en Chromium headless (cinco señas,
  cuatro variantes, dos avatares). Comprobador reutilizable en `../grupo-04/`.
- Capturas afirmativas a tiempo local 1,75 s (visor 2,45 s); no certifican
  continuidad del movimiento ni significado LSC.
- Fuentes originales intactas de `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según procedencia del empaquetado.

Pendientes revisión de entradas y eventual integración con prueba en dispositivo.
Sin cambios en la app ni build/instalación nuevos por esta prueba externa.
