# Grupo 01: referencia 0000 y propuestas 0001/0002

**2026-11-11.** El owner eligió avanzar por grupos, anotando y revisando poco a
poco. No eligió una adaptación automática indiscriminada de las 50 señas.

| Seña | Inicio del regreso propuesto | Llegada al reposo en el clip | Estado |
|---|---:|---:|---|
| `0000` | 1,375 s | 1,9167 s | Referencia visual aprobada en v3; no integrada |
| `0001` | 1,3333 s | 1,9167 s | Propuesta, pendiente revisión |
| `0002` | 1,625 s | 2,125 s | Propuesta, pendiente revisión |

Las ventanas nuevas se proponen desde las muestras originales: en `0001`, los
antebrazos se mantienen cerca de 95°/100° hasta 1,292 s y después bajan; en
`0002`, cerca de 71°/79° hasta 1,542 s y bajan después. Cálculo de distancia
angular respecto al reposo nuevo, variante afirmativa de Juan. **No es evidencia
de dónde acaba el contenido lingüístico**: la revisión del owner debe confirmar
que no se recorta ni cambia el gesto. Los límites son distintos por seña.

`0001` ya empieza con los codos flexionados: se conserva íntegra la entrada de
esa seña y también la de `0002`. No se extiende a ellas el ajuste del primer 20%
usado en `0000`. Solo se reemplazan seis rotaciones a partir del inicio indicado.
El resto de muestras/canales permanecen intactos. Son copias, no assets de app.

El visor compara asset actual (izquierda) con propuesta (derecha); permite elegir
las tres señas, ambos avatares y cuatro variantes faciales. Derecha usa el
acomodo final v3 (700 ms), misma regla temporal documentada en `../v3/`.
Ese acomodo empieza en los últimos 250 ms del clip y puede solaparse con el
regreso en `0002`: también debe revisarse visualmente. No se encadenan señas.

## Comprobaciones técnicas

- `prepare.py` genera las copias sin cambiar los assets de entrada.
- `check.py`: 72 canales por avatar, 24 por seña; cuaterniones normalizados,
  muestras protegidas exactas, entradas de `0001/0002` exactas, distancia angular
  al reposo no creciente durante el regreso y reposo exacto en su extremo.
- Las otras **188 animaciones** por avatar permanecen intactas, además de nodos,
  geometría, tiempos y el binario anterior (se anexan outputs nuevos).
- `verification.json` registra hashes de entrada y ventanas. `checks.json` guarda
  resultados técnicos. `browser-checks.json`: 24 selecciones con render verificado
  en Chromium headless (tres señas × cuatro variantes × dos avatares).
- Capturas afirmativas a tiempo local 1,75 s del clip; no certifican fluidez ni LSC.

## Abrir/reproducir

Prueba temporal: `http://127.0.0.1:8766/group-01/`.
Fuera de esa carpeta: copiar scripts y visor juntos; ejecutar los dos scripts
desde la raíz del repo y servir la carpeta resultante con `python3 -m http.server`.
Los enlaces de referencia necesitan las carpetas de pruebas hermanas; los GLB
del grupo son autocontenidos. Three.js se carga desde jsDelivr.

## Registro de revisión

| Seña | Revisión del owner | Corrección solicitada | Integración |
|---|---|---|---|
| `0000` | V3 aprobada antes de este grupo | Ninguna pendiente de esa muestra | Pendiente |
| `0001` | Aprobada visualmente el 2026-11-11 | Ninguna | Pendiente |
| `0002` | Aprobada visualmente el 2026-11-11 | Ninguna | Pendiente |

El owner respondió: **«Las dos perfectas. todo perfecto»**. Se aprueban las dos
propuestas presentadas, con sus ventanas registradas arriba. No se interpreta
como una constancia de revisión individual de cada avatar/variante facial ni
como validación lingüística formal; no especificó ese desglose.

No hay cambios nuevos en la app ni compilación/instalación por este grupo externo.
Grupo 01 cerrado en cuanto a aprobación de las propuestas. La integración en la
app y las pruebas de encadenamiento siguen pendientes. En los siguientes grupos,
si una variante necesita otro ajuste, registrarla por separado.
