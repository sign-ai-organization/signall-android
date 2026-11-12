# Integración de las transiciones aprobadas — 2026-11-11

**Estado:** assets y reproductor integrados; build correcto. Falta la prueba en
el teléfono: el Redmi se desconectó del USB al terminar de compilar.

## Qué se integró

| Pieza | Archivo | Qué hace |
|---|---|---|
| Curvas aprobadas | `tools/approved_tracks.json.gz` (6,69 MB) | Solo los samplers distintos de las copias que el owner aprobó, con hash de base y de cada curva |
| Aplicador | `tools/approved_tracks.py` | Verifica base y curvas; falla si el export o el reposo cambian |
| Exportador | `tools/export_approved_tracks.py` | Regenera el archivo; solo con aprobación explícita |
| Empaquetador | `tools/pack_avatars.py` | Aplica curvas, deduplica accessors de animación y compacta |
| Verificador | `tools/check_approved_tracks.py` | Compara los 400 clips integrados contra las copias elegidas |
| Salida final | `app/src/main/assets/web/playbackTiming.js` | Curva aprobada: últimos 250 ms del clip en 700 ms |
| Reproductor | `app/src/main/assets/web/app.js` | Pausa y muestrea la salida; espera al peso cero para `onPlaybackDone` |
| Prueba de la curva | `tools/test_playback_timing.mjs` | Seis duraciones, monotonía, límites y equivalencia con el visor |
| Prueba de la cola | `tools/verify_playback_cdp.py` | Escena real por CDP: variantes, repetida, append, stop, velocidad, ausentes |
| Sonda de tiempos | `tools/measure_exit_cdp.py` | Cuánto suma la salida a `onPlaybackDone`, por velocidad y con movimiento reducido |

Las cinco copias elegidas del grupo de entradas se combinaron **por sello**:
`0020` usa su v3 y `0028` entra completa desde `0028-entry`. El resultado no se
copió a mano: se congela en el archivo de curvas y el empaquetador lo aplica.

Los `despues.glb` de los visores **no** se versionan: quedan como evidencia en
las carpetas de este mismo árbol solo los scripts, los informes y las capturas.
El archivo congelado es la fuente de lo aprobado; regenerarlo exige esas copias
y una aprobación nueva del owner.

## Verificación

- **400 clips** (200 por avatar) idénticos a las copias aprobadas. Informe:
  `integrated-checks.json`; hashes de los GLB finales ahí mismo.
- **Empaquetado idempotente en la práctica:** aplicar dos veces se rechaza por
  hash de base, no se duplican prefijos ni suavizados.
- **Tamaño:** el APK no crece; baja respecto de los assets con solo el reposo
  nuevo. Juan 30,32 → **27,73 MB**; Pauline 27,85 → **27,59 MB**. La deducción
  viene de reescribir los accessors de animación vivos, con las cuatro variantes
  faciales compartiendo los huesos, y compactar después.
- **Reproductor real en el Redmi Note 9S** (build `debug` instalado, herramientas
  de desarrollo apagadas): ocho casos de cola más **las 50 señas con las cuatro
  variantes alternadas, en Juan y en Pauline**. Pasan todos. Informe:
  `device-final.json`.
- **La misma batería en Chromium de escritorio**, sobre los assets finales:
  `scene-50.json`. Sirve para separar un problema del WebView de uno del GLB.
- Los ocho casos: cuatro variantes, la misma seña repetida, añadir durante la
  salida, cortar durante la salida, movimiento reducido, cambio de velocidad
  aplicado recién a la siguiente, un recurso ausente y todos ausentes. La seña
  ausente no corta la frase y el turno termina igual.
- **Sonda de tiempos:** `device-exit-times.json`, con la tabla de arriba.
- **Curva de salida:** igualdad con la función aprobada del visor y monotonía en
  seis duraciones (`node tools/test_playback_timing.mjs`).

## Lo que cambia en lo medible

La salida dura **700 ms** y **empieza 250 ms antes del final del clip**, así que
`onPlaybackDone` llega **~450 ms después del final nominal** del clip (a velocidad
normal). `SignAiMetrics` mide la representación de punta a punta: el último tramo
de cada frase suma esos 450 ms y **no es comparable con las cifras anteriores**
sin decirlo.

Medido en el Redmi con `tools/measure_exit_cdp.py`, una seña `0028` (clip de
2,125 s desde el primer keyframe), de `signStart` a `onPlaybackDone`:

| Condición | Medido | Esperado |
|---|---:|---:|
| Normal, dos corridas | 2,574 / 2,584 s | 2,125 − 0,250 + 0,700 = 2,575 s |
| Velocidad 2× | 1,308 s | 2,575 / 2 = 1,288 s |
| Movimiento reducido | 2,138 s | 2,125 s (sin mezcla) |

Es el mismo criterio del visor aprobado; la cifra no incluye carga del clip.
La salida escala con la velocidad elegida y desaparece con `reduceMotion`.
No se midió rendimiento: la sonda usa CDP con el diagnóstico JS.

## Cómo repetir

```bash
python3 tools/check_approved_tracks.py --candidates /ruta/a/los/visores
node tools/test_playback_timing.mjs
python3 tools/pack_avatars.py            # idempotente; exige el hash de base
```

Para la escena: servir `app/src/main/assets/web` por HTTP, abrir Chromium con
`--remote-debugging-port` y correr `tools/verify_playback_cdp.py --url ...`.
Sin `--url` apunta al WebView del teléfono por CDP y envuelve el bridge temporal,
restaurándolo al terminar.

## Estado en el teléfono

Primero el Redmi se desconectó del USB durante `installDebug` (`No connected
devices!`); el owner lo reconectó y **la instalación se completó**. La app quedó
abierta en Juan con el reposo aprobado y «Desconectado», como corresponde con el
backend apagado.

Pendiente para el owner: mirar la salida final **a ojo** en el teléfono, en su
velocidad habitual. La prueba automática confirma la cola, el encadenamiento y
los tiempos, no que el movimiento se vea bien.

No se midió rendimiento (la prueba por CDP activa el diagnóstico JS) y no se
validó lingüísticamente ninguna seña: la aprobación es visual.
