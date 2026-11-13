# Resultados del carril P3 — PR-AND-03, PR-AND-04, PR-INT-05 y PR-REN-07 (10 de noviembre de 2026)

- **Carril:** P3 (Dispositivo y personas), según `reparto-3-personas-2026-11-05.html`.
- **Responsable:** Samuel Osorio, con un agente IA que ejecutó y registró la sesión (tarde del 10/11, de 16:44 a 17:10, hora local).
- **Pruebas:** `PR-AND-03` (Historial) y `PR-AND-04` (Ajustes), que habían quedado a medias en las sesiones del 6 al 8/11, `PR-INT-05` (Sin conexión a internet), que no se había podido medir limpio (§6), y `PR-REN-07` (Rendimiento en el móvil), a la que le faltaba la batería (§7).
- **Ronda:** diagnóstica (`D`), como se acordó para todo lo de P3.
- **Teléfono:** Samsung Galaxy A54 (SM-A546E), Android 16, 1080×2340, adb id `RZCW90DTMQJ`, por USB, con `adb reverse tcp:8080 tcp:8080`.
- **App:** APK `debug` compilado el 10/11 a las 16:44 con `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate`. Antes de `PR-AND-03` se borraron los datos de la app (`pm clear`) y se concedió `RECORD_AUDIO`.
- **Backend:** `gpt-4o-mini` con `llm_base_url` vacío (el oficial), catálogo de 75 glosas y caché semántica con 6 filas al arrancar. `/health` en `ok`.
- **No se levantó el STT:** esta máquina no tiene driver de NVIDIA y el `docker-compose.yml` del backend le pide GPU. Ninguna de las dos pruebas usa voz, y la app lo avisa con «Sin servicio de voz: puedes seguir traduciendo por texto». El backend registra un error de conexión al STT cada 5 s por ese motivo; no afecta a estas pruebas.
- **Herramienta:** `adb` y `uiautomator2` (solo para escribir frases con tildes y «¿», que `adb input text` no admite). El estado de la app se leyó con `run-as com.signai.webview` sobre `shared_prefs/signai.xml`. Los archivos auxiliares de `uiautomator2` se borraron del teléfono al terminar.

## 1. Resumen

| Prueba | Estado | Idea |
|---|---|---|
| PR-AND-03 | **Pasa**, con una salvedad (§2.4) | 12 de 12 frases en el historial, en orden; Reutilizar la envía de nuevo; el límite es de 20 entradas y se conserva al reabrir |
| PR-INT-05 | **Pasa**, con observaciones (§6) | Sin conexión la app indica «Desconectado», no se cuelga e impide traducir; el avatar no aparece, como prevé el SRS |
| PR-REN-07 | **Se reporta** (§7) | 3 corridas con el teléfono descargándose: 25 a 28 fps, 38,6 a 42,8 % de cuadros lentos, transición entre señas de 2,0 s; consumo de 1.003 a 1.519 mA según la temperatura, igual que con la app quieta |
| PR-AND-04 | **Pasa** | Personaje y velocidad se aplican al momento y se conservan al reabrir; los extremos 10 y 100 se respetan; un cambio durante la reproducción no corta la seña, pero no acelera lo que queda de esa frase |

## 2. PR-AND-03 — Historial

**Criterio de la ficha:** todas las frases aparecen, la reutilización funciona y el límite de entradas se reporta.

### 2.1 Punto de partida

Con la app recién limpiada, el panel «Historial reciente» decía «Aún no hay frases recientes.»
(`PR-AND-03_D_20261110_01.png`, `..._02_historial_inicial.txt`).

### 2.2 Envío de las 12 frases de FR-01-E

Las 12 se escribieron en el campo (se comprobó que el texto quedó exacto antes de cada envío), se tocó
«Traducir» y se esperó a que el estado volviera a «Inactivo». Tiempos medidos con consulta cada 0,5 s
(`PR-AND-03_D_20261110_06_envio.csv`).

| # | Frase | Segundos hasta «Inactivo» | Estados vistos |
|---|---|---|---|
| 1 | FR-01-001 | 7,2 | Reproduciendo → Inactivo |
| 2 | FR-01-003 | 10,8 | Procesando → Reproduciendo → Inactivo |
| 3 | FR-01-007 | 5,3 | Reproduciendo → Inactivo |
| 4 | FR-01-010 | 12,9 | Procesando → **Error** → Inactivo |
| 5 | FR-01-013 | 13,8 | Procesando → Reproduciendo → Inactivo |
| 6 | FR-01-014 | 10,1 | Procesando → Reproduciendo → Inactivo |
| 7 | FR-01-019 | 10,0 | Procesando → Reproduciendo → Inactivo |
| 8 | FR-01-021 | 8,8 | Procesando → Reproduciendo → Inactivo |
| 9 | FR-01-024 | 15,8 | Procesando → Reproduciendo → Inactivo |
| 10 | FR-01-026 | 6,3 | Procesando → Reproduciendo → Inactivo |
| 11 | FR-01-028 | 11,9 | Procesando → Reproduciendo → Inactivo |
| 12 | FR-01-029 | 10,5 | Procesando → Reproduciendo → Inactivo |

En la frase 9, la lectura del estado capturó una notificación del sistema del teléfono, ajena a la app. Se
quitó del CSV y del resto de la evidencia; no cambia el resultado.

### 2.3 La frase 4 dio error

`FR-01-010` («Tu hermano está triste. ¿Se siente mal?») terminó con «No se pudo traducir el texto.».
El backend la separó en dos oraciones y mandó las dos llamadas al LLM a la vez; las dos agotaron el tiempo
(`LLM_TIMEOUT_MS=5000`, con 1 reintento) y la frase falló a los 8,5 s
(`PR-AND-03_D_20261110_01.txt`, líneas de `SignAiState` de las 16:53:45). Las frases siguientes funcionaron.
Es un fallo pasajero del proveedor, no de la app, y al reenviar la frase, 15,1 s después, funcionó.

### 2.4 Qué guardó el historial (y la salvedad)

Después de enviar las 12 frases, el historial tenía **11 entradas, no 12**: faltaba justo `FR-01-010`
(`PR-AND-03_D_20261110_07_historial.png`, `..._08_historial_lista.txt`). Al leer el código
(`MainScreenState.kt`, alrededor de la línea 1832), la app guarda la frase **cuando empieza a
representarse**, no cuando se envía. Una frase cuya traducción falla no llega a representarse y por eso no
queda en el historial.

Se reenvió `FR-01-010`: funcionó y entró al tope (`..._09_historial_tras_reintento.png`). Con eso las 12
frases quedaron guardadas.

**Salvedad:** el criterio dice «todas las frases aparecen». Aparecieron todas las que se tradujeron; la que
falló solo apareció después del reintento. Se marca **Pasa**, y la decisión de si guardar solo lo que se
representa es el comportamiento esperado queda para el owner (§4).

### 2.5 Orden de la lista

La lista va de la más reciente a la más antigua. Con las 12 en el historial, la primera era `FR-01-010`
(la reenviada) y la última `FR-01-001`, la primera que se envió.

### 2.6 Reutilizar

Se tocó «Reutilizar» en «Yo nunca como mucho.» (una entrada del medio de la lista):

- El panel se cierra y el avatar empieza a representar la frase de inmediato (se ve la seña YO,
  `..._10_tras_reutilizar_1s.png`); el campo de texto queda vacío.
- El backend recibió la frase una segunda vez (`Phrase received: 'Yo nunca como mucho.'` dos veces), así que
  **Reutilizar la envía de nuevo**; no repite lo guardado sin traducir.
- La frase subió a la primera posición y no se duplicó: siguió habiendo 12 entradas
  (`..._11_historial_tras_reutilizar.txt`).

### 2.7 Se conserva al reabrir

Se cerró la app con `force-stop` y se volvió a abrir (el proceso cambió de 11430 a 15004). El historial tenía
las mismas 12 entradas, con la misma primera y la misma última (`..._12_historial_tras_reabrir.png`,
`..._13_historial_tras_reabrir.txt`).

### 2.8 Límite de entradas

El código fija el máximo en 20 (`SignHistoryRepository.kt`, `MAX_ITEMS = 20`) y no vuelve a registrar una
frase que ya esté entre las 5 primeras. Se enviaron 9 frases distintas más de FR-01 (002, 004, 005, 006,
008, 009, 011, 012 y 015), todas a la primera (`..._14_limite_envio.csv`):

- **El historial quedó en 20 entradas**, con 21 frases distintas enviadas.
- Se descartó la más antigua («Hola, buenos días, bienvenidos.»); la entrada más antigua que quedó fue
  «Mi abuelo y mi tío trabajan en familia.» (`..._15_historial_limite.png`, `..._16_historial_limite.txt`).

**El límite de entradas que se reporta es 20.**

## 3. PR-AND-04 — Ajustes

**Criterio de la ficha:** los dos ajustes se aplican al momento y se conservan al reabrir la aplicación.
Los dos ajustes de la sección AVATAR son **Personaje** (Juan o Pauline) y **Velocidad de señas** (10 a 100,
de 10 en 10, por defecto 50). La frase de la ficha (FR-01-C) fue FR-01-001, «Hola, buenos días, bienvenidos.»
(3 glosas).

### 3.1 La velocidad se aplica y escala

Medido con `signStart` en `SignAiState` (el momento en que el avatar empieza cada seña), misma frase en cada
velocidad (`..._05_velocidad.csv`):

| Velocidad | Segundos entre señas | Frase completa |
|---|---|---|
| 100 | 1,0 | 3,8 s |
| 50 (por defecto) | 2,0 | 7,1 s |
| 10 | 10,2 | 32,0 s |

Con una frase nueva a 80 las señas van a 1,3 y 1,26 s (4,2 s en total); a 70, tras reabrir, a 1,33 y 1,46 s
(4,5 s). Son coherentes con la proporción 50/velocidad sobre los 2,0 s de la base.

### 3.2 Los extremos

- En 100, tocar «+» de nuevo deja el valor en 100.
- En 10, tocar «−» de nuevo deja el valor en 10.
- La app sigue funcionando y representa en los dos extremos; a 10, cada seña tarda unos 10 s.

### 3.3 Un cambio durante la reproducción

Con la frase FR-01-006 («Gracias a todos. Los niños y las niñas comen con gusto.») a velocidad 50, se abrió
Ajustes y se subió a 80 mientras la frase se representaba (el cambio se hizo entre las 17:05:07 y las
17:05:14; `..._06_cambio_durante_reproduccion.png`, `..._07_cambio_durante.csv`).

- **No corta la seña en curso:** las señas 0 a 4 siguen en orden, sin reiniciar la frase.
- **No acelera lo que queda de esa frase:** las señas 2, 3 y 4 empezaron durante o después del cambio y
  siguieron separadas por 2,0 s (2,37; 2,01; 2,04; 2,04). El valor nuevo se aplicó recién en la frase
  siguiente (§3.1, 80 → 1,3 s).

### 3.4 Personaje

Se cambió de Juan a Pauline: `cambio de avatar: juan -> pauline` y `avatar listo: pauline en 1624 ms`, con el
valor guardado en 1,3 s (`..._09_selector_personaje.png`, `..._10_avatar_pauline.png`,
`..._10_cambio_avatar.txt`).

### 3.5 Se conservan al reabrir

Con Pauline y velocidad 70, se cerró la app con `force-stop` y se volvió a abrir. Los dos valores seguían
(`character=Pauline`, `speed=70`; `..._11_tras_reabrir_avatar.png`, `..._12_tras_reabrir_ajustes.png`,
`..._14_prefs_final.xml`), y una frase enviada después se representó a 1,33 y 1,46 s por seña, que es lo
esperado para 70 (≈1,46).

Al terminar se restauraron los valores por defecto (Juan, 50).

## 4. Hallazgos para que decida el owner

1. **Una frase cuya traducción falla no se guarda en el historial.** Es coherente con el código (se guarda
   al empezar a representar), pero no está dicho en la ficha. Hay que decidir si es lo esperado o si la
   frase fallida debería quedar para poder reintentarla desde el historial.
2. **Un cambio de velocidad no afecta a lo que queda de la frase que se está representando.** Aplica desde
   la frase siguiente. No contradice el criterio, pero conviene decir si es el comportamiento buscado.
3. **El LLM agotó el tiempo en una frase de dos oraciones** (`LLM_TIMEOUT_MS=5000`, 1 reintento). Las dos
   llamadas van en paralelo; si el proveedor tarda, fallan las dos a la vez. Fue 1 caso en los 22 envíos de
   `PR-AND-03` (12, el reintento y 9 del límite), sin patrón visible.
4. **El texto de las entradas largas del historial se recorta en pantalla** sin «…» (por ejemplo «Con
   permiso. Déjame pasar, por», en `..._07_historial.png`). El texto completo sí está guardado: se leyó
   entero con `uiautomator`.

## 5. Salvedades de método

- **«Aplicación recién instalada»:** se usó `pm clear` sobre una instalación nueva del APK; deja la app con
  los datos de fábrica, que es la condición que pide la ficha.
- **Los segundos son de reloj de pared**, con una consulta cada 0,4 a 0,5 s; tienen esa resolución. Los
  intervalos entre señas salen de `signStart` en el log de la app y son más finos.
- **El panel de historial se leyó con `uiautomator`** desplazándolo para ver todas las entradas. Las capturas
  muestran solo las primeras.
- **Todo se midió en un solo teléfono, el Galaxy A54.** Las rondas anteriores de P1 y P2 usaron el Redmi
  Note 9S.
- **Las claves del backend** (`sign-ai-backend/.env`) se habían rotado y las anteriores daban 401 en OpenAI y
  fallo de conexión con Neon. Con las nuevas, el backend quedó sano. Las claves no se guardaron en ningún
  archivo de evidencia.

## 6. PR-INT-05 — Sin conexión a internet

**Criterio de la ficha:** la aplicación avisa que no hay conexión y no se cuelga. Que el avatar se muestre
o no depende de si el motor 3D pasa a ir dentro de la aplicación (`RF-INT-HW-03`). El SRS dice además que,
sin conexión, la app «debe notificar al usuario e impedir el inicio del proceso de traducción», y que no
distingue entre no tener internet y que el servidor esté caído.

### 6.1 Cómo se cortó la conexión

En la sesión del 6/11 esta prueba no se pudo medir limpio porque el teléfono hablaba con el backend por un
túnel USB (`adb reverse`) que no depende de su red. Esta vez se cortó así, sin `adb` inalámbrico:

1. **Modo avión** con `cmd connectivity airplane-mode enable` (más `svc wifi disable` y `svc data disable`).
   Comprobado: `Active default network: none` y `ping` desde el teléfono dice `Network is unreachable`.
2. **`adb reverse --remove tcp:8080`**, para que el teléfono no tenga ningún camino al backend. El cable USB
   quedó solo para controlar el teléfono.
3. **`pm clear`** de la app, para que el WebView no conserve el motor 3D en caché (con caché el avatar
   aparecía igual, que fue lo que falseó la corrida del 6/11), y se volvió a conceder `RECORD_AUDIO`.

La app, entonces, falla al conectar con `127.0.0.1:8080` (conexión rechazada) y no con «red inalcanzable».
Para la app es lo mismo —el SRS dice que no lo distingue— pero es una diferencia de método.

### 6.2 Qué se vio (`PR-INT-05_D_20261110_10_observaciones.txt`)

| Momento | Qué se vio |
|---|---|
| t+3 s | Pantalla de carga (40 %) |
| t+10 s | Indicador **«Desconectado»** con punto rojo; campo, micrófono, historial, Traducir y TR presentes; **sin avatar** |
| t+30 s | Igual, con «Cargando...» arriba a la izquierda; el avatar no aparece |

- **No se cuelga:** el proceso fue el mismo en todas las lecturas (pid 19519) y no hubo cierre de la app.
  Reintenta conectar cada 5 s (`SignAiNet: session failed: Failed to connect to /127.0.0.1:8080`).
- **El aviso es el estado «Desconectado»** (`RF-UI-05`: señal visual distinta, punto rojo). No hay un
  mensaje de texto aparte que diga «no hay conexión a internet».
- **Avatar:** no se muestra, porque el motor 3D se baja de una CDN al iniciar. Es lo que el SRS prevé.

### 6.3 Traducir y micrófono

- **Frase escrita** (FR-01-025, «Por favor, come poco.»): el botón **Traducir se ve habilitado** (azul) con
  la frase en el campo. Al tocarlo no pasa nada: la frase se queda en el campo, no aparece ningún aviso nuevo
  y el log no registra envío ni fallo. En el código, `requestTranslation` sale sin hacer nada si el estado es
  *Desconectado* (`MainScreenState.kt`, alrededor de la línea 1560). Es decir, **impide traducir, pero en
  silencio**.
- **Frase hablada:** el botón de micrófono se ve apagado y no responde. **La parte hablada de la ficha no se
  pudo ejecutar**: con el micrófono bloqueado no hay forma de hablarle a la app.

### 6.4 Al volver la conexión

Se desactivó el modo avión, se activaron wifi y datos y se restableció `adb reverse`:

- **La app se reconectó sola** a los 12 s: el indicador volvió a «Inactivo».
- **El avatar no reapareció:** «Cargando...» siguió en pantalla. La página 3D no se vuelve a cargar sola.
- **Traducir funcionó** sin reiniciar: Procesando (2,5 s) y Reproduciendo (5,3 s); 1 oración, 3 glosas,
  1.719 ms. Pero con el avatar sin cargar, la frase se representa **sin que se vea nada**.
- **Al cerrar y reabrir la app** con conexión, el avatar y «Inactivo» estuvieron en 8 s.

### 6.5 Hallazgos de esta prueba

1. **Sin conexión, Traducir parece habilitado pero no hace nada y no avisa.** Cumple «impedir el inicio de
   la traducción», pero no «notificar al usuario» en el momento en que lo intenta; el único aviso es la
   etiqueta «Desconectado». Habría que decidir si Traducir debe verse apagado, como el micrófono, o mostrar un
   mensaje al tocarlo.
2. **Si la app arrancó sin internet, el avatar no se recupera cuando vuelve la red** hasta que se reinicie la
   app; mientras tanto la traducción corre sin avatar visible. El SRS no pide la recuperación, pero el
   usuario no tiene forma de saber que debe reabrir.
3. **No hay un texto que diga «sin conexión a internet»:** «Desconectado» es el nombre del estado y es lo que
   define el SRS (`RF-UI-05`).

### 6.6 Salvedades de método

- La parte «hablada» de la ficha no se ejecutó (§6.3). La prueba se dio por cerrada con la parte escrita y
  con lo que muestra el micrófono sin conexión.
- Se midió en el Galaxy A54, con el cable USB conectado todo el tiempo. El corte de red es real (modo avión),
  pero el fallo de conexión al backend es de rechazo local, no de red caída.
- Los tiempos de §6.2 son de lecturas puntuales, no de un cronómetro continuo; el momento exacto en que
  aparece «Desconectado» está entre los 3 y los 10 s.

## 7. PR-REN-07 — Rendimiento en el móvil

**Criterio de la ficha:** se reporta (prueba de caracterización). Métricas: fotogramas por segundo (promedio,
mínimo y proporción de cuadros lentos), memoria, consumo de batería y duración de la transición entre señas.
En la medición del 6 al 8/11 la batería no se pudo medir porque el teléfono cargaba por USB. Esta vez sí.

### 7.1 Cómo se midió

- **Sin cable.** Se activó `adb` inalámbrico (`adb tcpip 5555`, red `192.168.0.x`) y se desconectó el USB. Se
  comprobó por esa conexión que el teléfono no cargaba (`USB powered: false`, `AC powered: false`, estado 3,
  descargando). El túnel `adb reverse` se puso sobre la conexión inalámbrica.
- **Pantalla:** encendida todo el tiempo, con el brillo manual que ya tenía el teléfono (86) y 30 minutos de
  espera de pantalla. No se cambió ningún ajuste.
- **Secuencia** (`PR-REN-07_D_20261110_01.txt`): app limpiada (`pm clear`), 30 s para asentarse, **5 min con la
  app quieta (línea base)** y luego **las 30 frases de FR-01 seguidas por texto** (323 s).
- **Fuentes:** el contador de carga de la batería (`dumpsys battery`), `dumpsys gfxinfo` reiniciado al empezar
  la secuencia, `dumpsys meminfo`, `signStart` de `SignAiState` y la estimación por app de `batterystats`.

### 7.2 Resultados

| Métrica | Valor | Fuente |
|---|---|---|
| Representación por frase (n=29) | p50 7.687 ms, p95 10.623 ms, máx. 12.912 ms | resumen de `SignAiState` |
| Del envío a «Inactivo» (n=30) | p50 9,2 s, p95 13,9 s, máx. 15,4 s; sin errores ni reintentos | `..._02_frases.csv` |
| Cuadros lentos (janky) | **42,53 %** (9.226 de 21.694) | `gfxinfo` |
| Tiempo por cuadro | mediana 38 ms, p90 57 ms, p95 65 ms, p99 73 ms; medio 39,5 ms (**≈25 fps**) | `gfxinfo` |
| Cuadros por encima de 16,67 ms / 33,3 ms | 96,6 % / 67,5 % | histograma de `gfxinfo` |
| Cuadro más lento | ≈105 ms (≈10 fps) | histograma de `gfxinfo` |
| App en reposo (60 s, 5.087 cuadros) | janky 33,16 %, mediana 30 ms, p95 46 ms | `..._08_gfxinfo_reposo_60s.txt` |
| Transición entre señas (82 intervalos) | p50 2.030 ms, p95 2.116 ms, mín. 1.322 ms, máx. 2.703 ms | `signStart` |
| Memoria PSS | entre 601 y 667 MB; inicio 650 MB, final 610–627 MB; sin tendencia de crecimiento | `..._04_memoria.txt` |
| **Batería, prueba (323 s)** | **90 mAh ≈ 1.003 mA** | contador de carga |
| **Batería, app quieta (302 s)** | **95 mAh ≈ 1.132 mA** | contador de carga |
| Consumo atribuido a la app | +56 mAh durante la prueba (13,8 mAh de pantalla) | `batterystats` |
| Temperatura del teléfono | 37,9 a 38,6 °C | `dumpsys battery` |

### 7.3 Lectura de la batería

- **No hay diferencia detectable entre representar y estar quieta.** La app quieta gastó incluso un poco más
  por minuto que la prueba (1.132 contra 1.003 mA). El contador de carga avanza de a **5 mAh**, es decir
  ±5 mAh en cada tramo (≈±1 mAh por minuto), así que la diferencia entre los dos tramos está dentro del error.
- **El gasto es de la pantalla encendida y de la escena 3D, que renderiza aunque no haya señas.** Con 90 mAh en
  5,4 minutos, la app consumiría la batería de 5.000 mAh en unas 5 h con la pantalla encendida a este brillo.
  Es una extrapolación lineal, no una medición de autonomía.
- La estimación por app de `batterystats` (+56 mAh de 90) viene del perfil de potencia del fabricante, no de una
  medición directa; se cita como estimación.

### 7.4 Los cuadros salieron peores que en la medición anterior

| | 6 al 8/11 (cargando por USB) | 10/11 (descargándose) |
|---|---|---|
| Cuadros lentos | 17,33 % | 42,53 % |
| Mediana por cuadro | 24 ms (≈41 fps) | 38 ms (≈26 fps) |
| p95 por cuadro | 48 ms | 65 ms |
| Memoria PSS | 646–654 MB | 601–667 MB |
| Representación por frase p50 / p95 | 7.709 / 12.356 ms | 7.687 / 10.623 ms |

La representación por frase y la memoria son parecidas. **Los cuadros no se pueden comparar de forma directa**,
y no se determinó la causa. Diferencias entre las dos corridas, sin que se haya aislado ninguna:

- **Cargando contra descargándose:** el sistema puede limitar el rendimiento en batería.
- **Temperatura:** el teléfono estaba a 37,9–38,6 °C en esta corrida; en la anterior no se registró.
- **Qué se contó:** la medición del 6 al 8/11 es el acumulado del proceso, que incluye tiempo con la app quieta;
  ésta cuenta solo la secuencia. Pero en reposo también salió alto (33,16 % janky), así que eso no explica
  por sí solo la diferencia.
- Esta vez hubo conexión inalámbrica de `adb` y una app limpiada justo antes.

La transición entre señas es consistente con las otras medidas (2,0 s a velocidad 50; en el Redmi Note 9S la
diagnóstica del 05/11 dio p50 2,1 s).

### 7.5 Qué se hizo con la ficha

Los valores de referencia de la ficha (`criterio`) pasaron a ser los de las tres corridas de §7.6, como
rango, porque es la medición completa: incluye la batería y se hizo sin cable. Los valores del 6 al 8/11 (17,33 % janky, mediana de 24 ms,
646–654 MB) quedan registrados arriba. Si el owner prefiere conservar los anteriores como referencia, hay que
revertirlo; el respaldo `12-antes-de-cargar-REN-07.xlsx` tiene el libro antes del cambio.

### 7.6 Repeticiones: tres corridas

Se repitió la secuencia dos veces más, con el mismo procedimiento (app limpiada, 30 s de espera, `gfxinfo`
reiniciado, 30 frases de FR-01 seguidas, y 60 s de reposo para medir los cuadros). No se repitió la línea
base de batería de 5 min. Cada corrida está en `PR-REN-07_D_20261110_<nn>` (corrida 2: 20 a 25; corrida 3: 30
a 35; la corrida 1 es la de §7.2).

| Métrica | Corrida 1 | Corrida 2 | Corrida 3 |
|---|---|---|---|
| Temperatura del teléfono | 38,3–38,6 °C | 38,8 °C | **33,5–34,2 °C** |
| Cuadros lentos (janky) | 42,53 % | 42,81 % | 38,63 % |
| Mediana / p95 por cuadro | 38 / 65 ms | 38 / 65 ms | 36 / 57 ms |
| Tiempo medio por cuadro | 39,5 ms (≈25,3 fps) | 39,5 ms (≈25,3 fps) | 36,1 ms (≈27,7 fps) |
| Cuadros lentos con la app en reposo | 33,16 % | 35,34 % | 21,51 % |
| Transición entre señas p50 / p95 | 2.030 / 2.116 ms | 2.030 / 2.132 ms | 2.028 / 2.116 ms |
| Representación por frase p50 / p95 | 7.687 / 10.623 ms | 6.892 / 10.597 ms | 6.866 / 10.624 ms |
| Del envío a «Inactivo» p50 | 9,2 s | 9,0 s | 8,9 s |
| Memoria PSS | 601–667 MB | 603–667 MB | 593–691 MB |
| Consumo en la secuencia | 90 mAh, ≈1.003 mA | 90 mAh, ≈1.029 mA | **130 mAh, ≈1.519 mA** |
| Estimación del sistema para la app | +56 mAh | +51 mAh | +83 mAh |

**Qué se lee**
- **Lo estable:** la transición entre señas (2,03 s), la duración de la representación por frase (p95 de
  10,6 s en las tres), el tiempo hasta «Inactivo» y que las 90 frases se tradujeron sin errores.
- **Las dos corridas calientes** (38 °C) dieron prácticamente lo mismo: 42,5 y 42,8 % de cuadros lentos.
- **La corrida fría** (33,5 °C) tuvo menos cuadros lentos (38,6 %; en reposo 21,5 % contra 33 a 35 %) y un
  consumo **un 50 % mayor** (≈1.519 mA contra ≈1.000 a 1.030 mA).
- **Hipótesis, sin verificar:** al calentarse, el sistema limita el rendimiento del teléfono (menos
  consumo y más cuadros lentos). Encaja con las tres corridas, pero con tres puntos y sin medir las
  frecuencias del procesador no se puede afirmar. Con 5 °C de diferencia y un solo teléfono, tampoco se separa
  del efecto de lo que hubiera corriendo en segundo plano.
- **Descartado en la corrida 3:** el brillo no cambió (86, manual) y el GPS casi no se usó (1 mAh en total).
- **Consecuencia para la ficha:** los cuadros y el consumo dependen del estado térmico. Por eso la referencia
  quedó como **rango** y pide registrar la temperatura de cada corrida.

**La corrida 3 se hizo dos veces.** El primer intento se interrumpió a los pocos segundos: se abrió otra app
(una aplicación bancaria) sobre la nuestra y el script no encontró el campo de texto. No se tocó esa app. Se
descartaron los archivos de ese intento y se rehízo la corrida con una verificación de que la app esté al
frente. Entre los dos intentos el teléfono se enfrió de 38,8 a 33,7 °C, y por eso la corrida 3 salió fría.

### 7.7 Salvedades

- **Un solo teléfono y tres corridas** de unos 5 min cada una. Las dos primeras coinciden entre sí y la tercera
  difiere por la temperatura (§7.6); con tres puntos no se puede separar el efecto de la temperatura de otros.
- **Las 30 frases se tradujeron sin errores** (30 de 30 a la primera). La caché ya tenía frases de FR-01 de las pruebas de hoy; eso acorta el tiempo del backend
  (`envio_a_primera_sena` p50 83 ms) pero no cambia el trabajo del avatar.
- **Resolución del contador de carga: 5 mAh.** Ver §7.3.
- **El túnel de `adb`** iba por wifi y el teléfono tenía wifi y datos activos; el tráfico de `adb` en sí es
  pequeño, pero es radio encendida durante la medición, como en el uso real.
- **El aviso de seguridad:** `adb tcpip` se devolvió a modo USB al terminar (`adb usb`) y se borraron los
  archivos auxiliares de `uiautomator2` del teléfono.

## 8. Evidencias

Todo con el nombre `PR-AND-0N_D_20261110_<nn>.<ext>`:

- `PR-AND-03_D_20261110_*`: 01 (png, txt y json), 02 a 16.
- `PR-AND-04_D_20261110_*`: 01 (png, txt y json), 02 a 14.
- `PR-INT-05_D_20261110_*` (en `evidencias/INT/`): 01 (logcat), 01 a 09 (capturas) y 10 (observaciones).
- `PR-REN-07_D_20261110_*` (en `evidencias/REN/`): corrida 1 con 01 a 08 (registro de la sesión, frases, `gfxinfo`, memoria, `logcat`, batería, consumo por UID y `gfxinfo` en reposo); corrida 2 con 20 a 25 y corrida 3 con 30 a 35 (registro, frases, `gfxinfo`, `gfxinfo` en reposo, `logcat` y resumen en `json`).
- Los `01.txt` son el `logcat` de `SignAiState`, `SignAiNet` y `SignAiMetrics`; los `01.json`, el
  `/metrics.json` del backend.
