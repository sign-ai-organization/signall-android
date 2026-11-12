# Grupo 06: 0023–0027

**2026-11-11. Estado:** cinco propuestas aprobadas visualmente, integración pendiente. Grupo 05 aprobado
completo, incluida `0018`; ventanas aprobadas anteriores intactas.

| Seña | Ancla del regreso (tiempo de clip) | Llegada al reposo | Revisión | Integración |
|---|---:|---:|---|---|
| `0023` | 48/24 = 2,000 s | 56/24 = 2,3333 s | Aprobada | Pendiente |
| `0024` | 45/24 = 1,875 s | 56/24 = 2,3333 s | Aprobada | Pendiente |
| `0025` | 51/24 = 2,125 s | 55/24 = 2,2917 s | Aprobada, incluida revisión especial | Pendiente |
| `0026` | 43/24 = 1,7917 s | 51/24 = 2,125 s | Aprobada | Pendiente |
| `0027` | 47/24 = 1,9583 s | 53/24 = 2,2083 s | Aprobada | Pendiente |

El owner respondió **«hermoso todo aprobado TODO, siguientes»** el 2026-11-11:
las cinco propuestas, incluida `0025`, aprobadas sin correcciones. No especificó
revisión por avatar/variante; no se atribuye validación lingüística formal.

Hipótesis por inspección de curvas de antebrazos de Juan, afirmativa, no límites
lingüísticos demostrados. `0023` mantiene flexión hasta aproximadamente 1,917 s;
`0024` hasta 1,792 s; `0026` empieza a descender en la zona de 1,792 s;
`0027` sostiene hasta la zona de 1,917 s. Se proponen anclas distintas para
conservar esas partes del movimiento antes de sustituir la salida.

**`0025` requiere especial revisión:** presenta un movimiento tardío, con aumento
de flexión cerca de 2,042 s y termina flexionada. Se preservan sus muestras hasta
2,125 s y se propone completar el regreso después. No se afirma que el original
esté incorrecto. El owner debe comprobar que no se recorta un componente del
gesto y que el resultado sea natural.

Solo se reemplazan seis rotaciones por clip, slerp/peso quíntico hacia el reposo
aprobado. Todas las entradas y muestras hasta el ancla son exactas en el GLB;
otros canales, dedos, cara, nodos, geometría y tiempos intactos. Otras 180
animaciones por avatar sin cambios. Son copias externas, no assets de producción.

Visor: izquierda actual, derecha propuesta con acomodo final v3 de 700 ms. En
`0025` el acomodo empieza antes del ancla y afecta velocidad/peso del tramo final:
muestras intactas no equivalen a reproducción intacta en ese tramo. Revisar el
conjunto y no solo su último fotograma. No se prueba encadenamiento entre señas.

## Evidencia y reproducción

Visor temporal: **http://127.0.0.1:8766/group-06/**.
Copiar scripts/HTML juntos; ejecutar `prepare.py` y `check.py` desde la raíz del
repo y servir la carpeta por HTTP. Enlace al grupo anterior requiere carpeta
hermana; Three.js necesita jsDelivr.

- `verification.json`: hashes de entrada y ventanas, ambos avatares.
- `checks.json`: 120 canales por avatar, normalización, muestras protegidas
  exactas, regreso angular monótono y extremo en reposo. Otras 180 animaciones
  por avatar intactas.
- `browser-checks.json`: 40 selecciones/render en Chromium headless (cinco señas
  × cuatro variantes × dos avatares). Comprobador reutilizable en `../grupo-04/`.
- Capturas afirmativas a tiempo local 1,75 s (visor 2,45 s); no bastan para evaluar
  los regresos tardíos ni certificar movimiento o significado LSC.
- Fuentes originales intactas de `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según procedencia del empaquetado.

Pendiente integración de las cinco aprobadas y prueba en dispositivo.
Sin cambios en la app ni build/instalación nuevos por esta prueba externa.
