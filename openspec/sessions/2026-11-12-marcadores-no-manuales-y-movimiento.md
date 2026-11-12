# Marcadores no manuales y movimiento humano del avatar

**Fecha:** 2026-11-12 · **Rama:** `main` · **Sin commit** (lo decide el owner)

## Qué llegó

El owner pidió mejorar el avatar «lo mejor posible», centrado en el movimiento y no en el
aspecto: cómo representa la seña, los marcadores no manuales (cabeza, labios, ojos, boca,
cejas, pómulos, cachetes), y que se vea más humano y se mueva en el espacio, porque «está
muy robótico».

## Qué se encontró

- **La cabeza casi no se mueve en los clips.** Medido sobre `juan.glb` y los puntos de cara
  de LSC50: en `0000` el clip cabecea 2,1° y el intérprete 9,7°; en `0007` el clip gira
  0,2° y el intérprete más de 16°.
- **La cara es una pose fija por tipo de oración** (ya medido el 2026-11-04), y en reposo
  el avatar es una estatua: sin acción en el mixer no se mueve nada.
- **El dataset ya trae lo que falta.** `FACE_LANDMARKS/<id>_0000_0000.csv` tiene los 468
  puntos de la cara a 50 fps y `BODY_LANDMARKS` los 33 del cuerpo, sin huecos.
- **Los clips no conservan el reloj del video**: se recortaron (52 fotogramas de clip
  contra 70–100 de video). Hay que alinear cada seña.
- **El empaquetado podaba la boca**: `jawOpen`, `mouthPucker`, `mouthFunnel` y otros ocho
  no los anima ningún clip, así que no estaban en los `.glb` de la app.

## Qué se hizo

1. `tools/extract_nmm.py` → `assets/web/nmm.json` (104 KB, 50 señas). Quita el giro de la
   cabeza con un ajuste rígido sobre 16 puntos que no cambian con la expresión, y mide
   sobre la cara ya enderezada. Descarta los fotogramas donde una mano tapa la cara.
   Alinea clip y video por la altura de las dos muñecas.
2. `assets/web/humanize.js`: la capa que suma lo medido, la prosodia del tipo de oración,
   la mirada, el parpadeo, la respiración, el balanceo y el tronco que sigue a las manos.
3. `app.js`: crea la capa por avatar, la corre alrededor del mixer y agrega
   `SignAI.setHumanize(nivel)` (aditivo). Sin cambios en Kotlin ni en el contrato.
4. `tools/pack_avatars.py`: conserva 11 morph targets de la boca. La firma de las curvas
   aprobadas se comprueba sobre el empaquetado sin ellos y cada curva sigue verificando
   su propio hash; los canales de pesos se comparan proyectados a las columnas firmadas.

## Qué se verificó y cómo

- **Los 400 clips no cambiaron**: comparación valor a valor contra los `.glb` anteriores,
  81.800 canales de hueso idénticos y 1.600 de pesos idénticos en sus columnas originales.
- **La capa no acumula**: `tools/test_humanize.mjs`, 400 fotogramas con fundido, corte a
  mitad, pausa y parada → 0 diferencias después de cada `restore()` y con nivel 0.
- **Se ve en la escena real**: Chromium de escritorio sin interfaz, con la misma página de
  la app. Capturas en `docs/evidencias/humanizacion-2026-11-12/`.
- `./gradlew assembleDebug --offline` compila; APK debug de 40,2 MB.
- `tools/test_playback_timing.mjs` sigue pasando.

## Qué NO se verificó

- **Nada en el teléfono.** `adb devices` no mostró ninguno: no se instaló ni se midió el
  costo por fotograma en el Redmi Note 9S. La capa hace unas veinte lecturas de
  orientación de hueso por fotograma; es una cuenta, no una medición.
- **La validez LSC** de la tabla `PROSODY` y de sumar la cara de la seña a la del tipo.
- `tools/check_approved_tracks.py` no se corrió: necesita la carpeta de copias aprobadas,
  que no está en esta máquina. La comparación contra los `.glb` anteriores cubre lo mismo.
- `./gradlew build` completo (con ktlint) no se corrió; no se tocó Kotlin.

## Pendiente para el owner

- Mirarlo en el teléfono y decidir las ganancias (`TUNING` y `PROSODY` en `humanize.js`).
- Decidir si RF-AVT-03 y PR-AVT-05 se actualizan: la escena ahora hace más de lo que
  describen. No se tocó ningún documento de `../sign-ai-docs`.
- Las cifras de tamaño de PR-AVT-06 y los hashes de los `.glb` cambiaron.
- `../sign-ai-avatar` no se tocó: los originales siguen en `fc6f372`.
