# Transiciones al reposo: decisiones y estado

**Fecha:** 2026-11-11. **Estado:** las 50 señas y la salida final están
**integradas en los assets y en el reproductor**; falta la prueba en el teléfono.

## Integrado: qué quedó en la app

- **Entrada y regreso de las 50 señas**, con las curvas de las propuestas que el
  owner aprobó. Se guardan, no se recalculan: `tools/approved_tracks.json.gz` y
  `tools/approved_tracks.py` las aplican con hash de base y de cada curva.
- **`0020`** conserva su v3 (manos, dedos y cuerpo suavizados en 1,500–2,125 s).
  **`0028`** conserva sus tres correcciones (salto, entrada y regreso).
- **Salida final suave en la app**: `playbackTiming.js` reparte los últimos
  250 ms del clip en 700 ms. No se aplica entre señas (siguen a 100 ms), ni con
  movimiento reducido (corte seco).
- Los **400 clips** integrados coinciden con las copias aprobadas; los verifica
  `tools/check_approved_tracks.py`. El APK **baja** de tamaño gracias a la
  deduplicación de curvas de animación.
- **`onPlaybackDone` llega ~450 ms después del final nominal del clip** a
  velocidad normal: la salida dura 700 ms y empieza 250 ms antes del final.
  Medido en el Redmi (2,574 s para `0028`, que sin salida dura 2,125 s). Las
  cifras anteriores de duración de la representación no son comparables.

Evidencia, tamaños, hashes y pruebas: `../data/evidencias/avatar/transiciones-2026-11-11/integracion/`.
Prueba en el dispositivo pendiente: el Redmi se desconectó del USB en el
`installDebug`, así que no se instaló esta versión.

## Cómo se llegó hasta acá

### Prioridad posterior: reposo nuevo tanto al entrar como al salir

**Decisión posterior del owner, 2026-11-11:** confirmó que revisó grupo 08 y está
bien, pero pidió pausar la extensión a grupos nuevos: observó el reposo viejo al
inicio de las señas y quiere corregirlo en **este grupo y todos los anteriores**,
igual que se hizo con la entrada de `0028`. Esta autorización amplía el alcance
de entrada antes limitado a `0028`; no invalida ni reemplaza los regresos aprobados.

El criterio anterior preservaba entradas originales y por eso conservaba también
su reposo viejo. Comprobar muestras intactas no comprobaba que la entrada fuera
coherente con el reposo nuevo. Se separan ambos estados de revisión:

| Señas | Regreso | Entrada con criterio nuevo | Integración |
|---|---|---|---|
| `0000`–`0009` | Aprobado | Entrada nueva aprobada, incluidos prefijos de 0001/0006 | Pendiente |
| `0010`–`0019` | Aprobado | Entradas nuevas aprobadas, incluidos prefijos | Pendiente |
| `0020`–`0027` | Aprobado; conservar 0020 v3 por decisión del owner | Entradas aprobadas | Pendiente |
| `0028` | Aprobado | Salto y entrada nueva aprobados, conservar como referencia | Pendiente |
| `0029` | Aprobado | Entrada aprobada | Pendiente |
| `0030`–`0037` | Aprobado | Entradas nuevas aprobadas | Pendiente |
| `0038`–`0039` | Nuevos regresos aprobados | Entradas nuevas aprobadas | Pendiente |
| `0040`–`0049` | Propuestas preparadas, pendientes al revisar | Propuestas preparadas, pendientes al revisar | Aprobadas después («Todo confirmado. Sigue.»); integradas con las demás |

El owner corrigió después el tamaño a **diez por grupo**. Primera revisión de entradas:
`0000`–`0009`. No se reutiliza automáticamente la ventana 0,4167–0,750 s de `0028`:
hay señas que ya empiezan con los brazos flexionados (p. ej. `0001`), y sustituir
esa preparación a ciegas podría eliminar parte del gesto. Elegir límites por
seña y comparar el inicio manteniendo fijo el regreso aprobado.

Resultado requerido: **reposo nuevo → preparación → gesto → regreso → reposo
nuevo**, sin paso por el reposo viejo en los extremos. No se modifica el gesto
central por inercia. Las aprobaciones anteriores quedan como evidencia de los
regresos; la entrada se registra por separado, sin atribuir revisión de variantes
o validez LSC formal. Primeros tres grupos de entradas aprobados; incidencia
corporal de `0020` aparte. No se integraron transiciones nuevas en la app.

### Entradas 01: 0000–0009

Visor: `http://127.0.0.1:8766/entries-01/`. Diez entradas aprobadas sin correcciones
(«hermoso todo aprobado TODO, siguientes»), incluidos los prefijos. Con regresos
aprobados y mismo final v3 en ambos lados. Ocho uniones locales; `0001/0006`
añaden preparación de 0,375/0,250 s para preservar todas las muestras originales.
Duran más; regresos con valores y tiempos relativos exactos, desplazados junto
al clip. `0000` reemplaza su mezcla v1 y protege muestras aprobadas desde 0,500 s.
Registro y comprobaciones: `../data/evidencias/avatar/transiciones-2026-11-11/entradas-01/README.md`.

### Entradas 02: 0010–0019

Visor: `http://127.0.0.1:8766/entries-02/`. Diez entradas aprobadas sin correcciones
(«hermoso todo aprobado TODO, siguientes»), incluidos prefijos. Ocho uniones
por seña, cinco con espera inicial en reposo nuevo. `0010/0012` añaden preparación
de 0,250/0,3333 s para conservar sus muestras activas; duración aumenta, valores
y tiempos relativos del regreso intactos. Registro:
`../data/evidencias/avatar/transiciones-2026-11-11/entradas-02/README.md`.

### Entradas 03: 0020–0029

Visor: `http://127.0.0.1:8766/entries-03/`. Nueve entradas nuevas aprobadas
(«perfecto, todo. hermoso») y
`0028` como referencia aprobada, con todos sus canales corregidos intactos en
ambos lados. Esperas/uniones por seña; ninguna duración añadida. Regresos aprobados
conservados. Registro: `../data/evidencias/avatar/transiciones-2026-11-11/entradas-03/README.md`.

### Entradas 04: 0030–0039

Visor: `http://127.0.0.1:8766/entries-04/`. Diez aprobadas sin correcciones
(«todo bien, ya termina las señas, lo que queda»): ocho
entradas con regresos aprobados y `0038/0039` con ambos extremos nuevos, señalados
para revisión. Sin duración añadida. Registro:
`../data/evidencias/avatar/transiciones-2026-11-11/entradas-04/README.md`.

### Último grupo: 0040–0049

Visor: `http://127.0.0.1:8766/entries-05/`. Diez propuestas de entrada y regreso
preparadas, pendientes de revisión. Sin duración añadida. Registro y ventanas:
`../data/evidencias/avatar/transiciones-2026-11-11/entradas-05/README.md`.
Las 50 ya tienen propuestas; últimas diez no aprobadas todavía. Integración
posterior debe combinar clips elegidos de entradas 01–05 y selección `0020` v3;
no usar un GLB de grupo como paquete final de todos. `0028` íntegra en entradas 03.

### Incidencia aparte: movimiento corporal de 0020

El owner pide inspeccionar una sacudida después del gesto, sin rechazar entradas
ni regresos aprobados. Se identifican cambios rápidos ya presentes en dedos,
muslos y hombros originales hacia 2,000 y 2,2083 s. Canales fuera de las seis
rotaciones editadas idénticos al original en ocho clips de ambos avatares.
El owner confirmó el tirón corporal hacia 2,2 s («Si, es ese el que noto»).
El owner autorizó preparar copia localizada («Si»; «sigue»). Propuesta corporal
2,0417–2,250 s preparada, pendiente aprobación visual: curvas locales de brazos,
manos/dedos y cara intactas; suavizar hombros cambia la trayectoria global de
los brazos durante ese tramo. Registro `0020-cuerpo/README.md` dentro de las
evidencias; visor `http://127.0.0.1:8766/0020-smooth/`. Sin integración.

Primera devolución sobre esa propuesta: «como en el 1.8, sigue habiendo un
movimiento raro». No está aprobada ni resuelta. Se inspeccionó también
1,65–1,95 s, fuera de la corrección corporal: hay saltos originales en dedos y
muñecas. Pendiente confirmar con el owner la parte del cuerpo percibida antes
de modificar posibles componentes del gesto. Evidencia `0020-cuerpo/early-motion-audit.json`.

El owner aclaró que lo percibe **en todo el cuerpo**. Segunda prueba sobre v1:
suavizado corporal local de 1,500–2,125 s, cinco muestras con bordes quínticos,
sin editar dedos, cara ni curvas locales de brazos/manos. Mantiene exacta v1
fuera de esa ventana; los apoyos de pies y movimiento global deben revisarse.
Fuente v1 aún no aprobada, segunda prueba también pendiente. Registro
`../data/evidencias/avatar/transiciones-2026-11-11/0020-cuerpo-v2/README.md`, visor
`http://127.0.0.1:8766/0020-smooth-v2/`. Sin integración.

Después precisó manos/dedos y cuerpo, autorizando otra copia conjunta. V3 extiende
el mismo suavizado temporal de 1,500–2,125 s a brazos/manos/dedos; recalcula desde
v1 y comprueba que el cuerpo sea exacto a v2, sin doble filtrado. Ahora sí varían
curvas locales y configuraciones intermedias de manos en el tramo, incluido el
inicio del regreso: pendientes revisión de contactos y significado, no se afirman
intactos. Entrada/cara/fuera de ventana exactos. Registro
`../data/evidencias/avatar/transiciones-2026-11-11/0020-conjunto-v3/README.md`, visor
`http://127.0.0.1:8766/0020-smooth-v3/`. Tres propuestas aún sin aprobación final.

Decisión posterior: **«bien, ya sigamos con el resto. esa ya dejemosla asi»**.
Se conserva v3 de `0020`, sin más ajustes ni afirmación de perfección/validez LSC.
Selección/hashes en `0020-conjunto-v3/accepted.json` de las evidencias; integrar
esa versión, no el archivo anterior sin corrección de entradas 03. Se retoman grupos.
Visor lento con pausa/tiempo: `http://127.0.0.1:8766/0020-inspect/`.
Registro: `../data/evidencias/avatar/transiciones-2026-11-11/0020-diagnostico/README.md`.

## Qué se aprobó

El owner aprobó sobre copias de `0000`, en Juan y Pauline:

1. Reposo con brazos a los lados y giro intermedio de manos. Este reposo sí está
   integrado en los assets y en la app instalada; originales intactos.
2. Sustituir el recorrido hacia el reposo viejo por un regreso directo al nuevo.
   V1 mezclaba solo los extremos y fue rechazada: aún se veía la pose vieja.
   V2 conserva el movimiento hasta 1,375 s y sustituye las seis rotaciones de
   brazos/antebrazos/manos hasta llegar al reposo a 1,9167 s. El owner la valoró
   como «muchísimo mejor».
3. Suavizar el último acomodo del cuerpo completo. V3 usa los mismos GLB de V2;
   solo cambia el visor: desacelera los últimos 250 ms del clip durante 700 ms
   y desvanece su peso hacia el reposo con curva quíntica. El owner aprobó V3
   como «Hermoso» y pidió aplicarlo a todas las señas y verlo en la aplicación.

Estos tiempos pertenecen a la muestra `0000`, no son límites lingüísticos de las
otras señas. V2/V3 aún no están en el empaquetador ni en el controlador de la app.
No se debe confundir la aprobación del resultado de la muestra con validación
lingüística de los 400 clips.

## Integridad y reproducción

- Scripts, visores, comprobaciones y capturas en
  `../data/evidencias/avatar/transiciones-2026-11-11/`, subcarpetas `v2/` y `v3/`.
- V2 modifica solo seis rotaciones por variante de `0000`. V3 no cambia GLB.
- Fuentes: `../signall-avatar/models/`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`; fuentes intactas.
- La prueba del visor no es una prueba del controlador de reproducción real,
  ni una medición de latencia, ni una revisión de cada seña.
- El SRS vigente, `../signall-docs/latex/srs/main.tex:339–340`, establece
  concatenación de señas grabadas mediante interpolación; no se crean señas.

## Hallazgo al preparar la extensión

Se auditaron los **400 clips** de los dos assets actuales (50 señas × cuatro
variantes × dos avatares), sin editarlos. Script reproducible:

```bash
python3 ../data/evidencias/avatar/transiciones-2026-11-11/audit_endings.py
```

El informe `all-clips-audit.json` registra hashes, duración, distancia angular
final al reposo anterior (primer frame de `0029_afirmativo`) y desplazamiento
angular del último paso de brazos y antebrazos. No detecta significado LSC.

Los extremos no son equivalentes. Ejemplos de Juan, variante afirmativa:

- `0000`: dura 2,458 s; antebrazos a unos 3,0°/1,5° del reposo anterior al final.
- `0018`: dura 2,125 s; antebrazos a unos 61,8°/58,6° de ese reposo al final.
- `0040`: dura 1,417 s; dura menos que la ventana completa de regreso de `0000`.

Por tanto, no hay fundamento para copiar 1,375–1,9167 s ni para asumir que el
mismo porcentaje del final sea siempre un tramo sin contenido lingüístico.
Una modificación automática indiscriminada podría recortar parte de una seña.
La observación angular no demuestra que `0018` esté incompleta ni incorrecta:
solo demuestra que termina en una postura distinta de `0000`.

## Decisión para extender los regresos

El owner decidió el 2026-11-11: **«vamos por grupos, anotando y viendo poco a
poco»**. Se revisan propuestas por seña y se registran aprobaciones/correcciones;
no se aplica automáticamente una ventana común al corpus.
El suavizado final de cuerpo es una decisión separada del límite lingüístico.

### Grupo 01

Referencia `0000` y dos propuestas nuevas, `0001`/`0002`, en ambos avatares.
Registro por seña, ventanas, scripts y resultados en
`../data/evidencias/avatar/transiciones-2026-11-11/grupo-01/README.md`.
`0001` y `0002` conservan íntegramente su entrada; sus regresos tienen límites
distintos. El owner aprobó ambas propuestas el 2026-11-11: «Las dos perfectas.
todo perfecto», sin correcciones. No especificó una revisión desglosada por
avatar/variante. Grupo 01 aprobado, pendiente de integración. Visor temporal:
`http://127.0.0.1:8766/group-01/`. Ninguna transición nueva está integrada aún.

### Tamaño de grupo y grupo 02

El owner pidió **cinco señas nuevas por grupo**. Grupo 02: `0003`–`0007`,
propuestas preparadas con entradas intactas y ventanas de regreso distintas.
Registro individual y evidencia en
`../data/evidencias/avatar/transiciones-2026-11-11/grupo-02/README.md`.
Visor temporal: `http://127.0.0.1:8766/group-02/`. El owner aprobó las cinco
el 2026-11-11: «hermoso todo aprobado, siguientes», sin correcciones.
Integración pendiente; no se atribuye revisión individual por avatar/variante.

### Grupo 03

`0008`–`0012`: cinco propuestas con entradas intactas y tiempos separados,
aprobadas el 2026-11-11 («hermoso todo aprobado, siguientes»), sin correcciones.
Sin desglose de revisión por avatar/variante. Registro y evidencia en
`../data/evidencias/avatar/transiciones-2026-11-11/grupo-03/README.md`.
Visor temporal: `http://127.0.0.1:8766/group-03/`. La app sigue sin transiciones
nuevas integradas; los grupos anteriores mantienen sus aprobaciones.

### Grupo 04

`0013`–`0017`: cinco propuestas aprobadas el 2026-11-11, sin correcciones
(«hermoso todo aprobado TODO, siguientes»), incluida `0016`. Con entradas intactas y
anclas elegidas por seña. `0016` requiere especial revisión: el original termina
con el codo aún flexionado; no se presume que sea un error ni se valida su gesto
por análisis angular. Registro y evidencia en
`../data/evidencias/avatar/transiciones-2026-11-11/grupo-04/README.md`.
Visor temporal: `http://127.0.0.1:8766/group-04/`. Sin integración aún.

### Grupo 05

`0018`–`0022`: cinco propuestas aprobadas el 2026-11-11, sin correcciones
(«hermoso todo aprobado TODO, siguientes»), incluida `0018`. Con anclas por seña y entradas
exactas en los GLB. `0018` requiere especial revisión por acabar el original con
codos flexionados. Registro y evidencia en
`../data/evidencias/avatar/transiciones-2026-11-11/grupo-05/README.md`.
Visor temporal: `http://127.0.0.1:8766/group-05/`. Sin integración aún.

### Grupo 06

`0023`–`0027`: cinco propuestas aprobadas el 2026-11-11, sin correcciones
(«hermoso todo aprobado TODO, siguientes»), incluida `0025`. Entradas exactas y anclas propias.
`0025` requiere especial revisión por su movimiento tardío y postura final
flexionada; no se asume que sea incorrecta ni que sea igual a otros regresos.
Registro en `../data/evidencias/avatar/transiciones-2026-11-11/grupo-06/README.md`.
Visor temporal: `http://127.0.0.1:8766/group-06/`. Sin integración aún.

### Grupo 07

`0028`–`0032`: cinco regresos aprobados el 2026-11-11 («Muy bien, todas bien»),
incluido el regreso temprano de `0030`. El owner señaló aparte el inicio abrupto
de `0028`: sus canales iniciales están idénticos al original. En ambos avatares,
afirmativa, el codo pasa de unos 26,5° a unos 0,7–1,3° del reposo entre muestras
separadas por unos 42 ms. Incidencia previa al ajuste; corrección aún pendiente,
sin modificar ventanas aprobadas. Informe `0028-start-diagnosis.json` del grupo.
Registro en `../data/evidencias/avatar/transiciones-2026-11-11/grupo-07/README.md`.
Visor temporal: `http://127.0.0.1:8766/group-07/`. Sin integración aún.

### Corrección inicial aislada de 0028

Autorizada como prueba tras señalar el owner el arranque extraño. La inspección
ampliada mostró un salto de la primera postura de cuerpo completo, no solo codos.
Propuesta: reemplazar las primeras muestras TRS por las segundas (1/24→2/24 s),
sin cambiar los tiempos ni ninguna muestra posterior ni el regreso aprobado.
No se tocan canales compactados entre extremos ni pesos de la cara. Copias de
ambos avatares y cuatro variantes; pendiente aprobación visual.
Registro: `../data/evidencias/avatar/transiciones-2026-11-11/0028-inicio/README.md`.
Visor temporal: `http://127.0.0.1:8766/0028-start/`. App intacta.

La corrección del salto fue aprobada después («Bien, excelente. Muy muy muy
buena»). El owner señaló aparte el paso por el reposo viejo al inicio y autorizó
una entrada nueva solo para `0028`: reposo nuevo hasta 0,4167 s y preparación
directa hasta la muestra de 0,750 s; desde ahí, gesto/regreso exactos a la copia
sin salto aprobada. Seis rotaciones por variante, resto intacto. Ventana propuesta,
pendiente aprobación; no se generaliza a otras señas.
Registro en `../data/evidencias/avatar/transiciones-2026-11-11/0028-entrada/README.md`;
visor `http://127.0.0.1:8766/0028-entry/`. Sin integración todavía.

El owner aprobó esta entrada el 2026-11-11: «hermoso, perfecto. documentamos y
seguimos como antes», sin correcciones. Quedan aprobados por separado salto,
entrada y regreso de `0028`, pendientes de integración. No se generaliza esa entrada.

### Grupo 08

`0033`–`0037`: cinco regresos aprobados por el owner («ya revisé todo el grupo y
está bien»); entradas pendientes de revisión con el criterio nuevo, ver prioridad actual.
Registro: `../data/evidencias/avatar/transiciones-2026-11-11/grupo-08/README.md`.
Visor temporal: `http://127.0.0.1:8766/group-08/`. Sin integración aún.

La integración deberá preservar nombres y rutas de clips, las cuatro variantes
faciales, ajustes de velocidad y movimiento reducido; atender cola que crece,
pausa/reanudación, reset y callback final; y verificarse tras build e instalación
en el dispositivo. No se afirma que ese trabajo ya se haya realizado.
