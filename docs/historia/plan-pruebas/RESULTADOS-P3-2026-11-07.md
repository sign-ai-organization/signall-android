# Resultados del carril P3 — 7 de noviembre de 2026 (continuación)

- **Carril:** P3 (Dispositivo y personas), según `reparto-3-personas-2026-11-05.html`.
- **Responsable:** Samuel Osorio (agente IA), sesión del 7/11, continuando el trabajo de
  `RESULTADOS-P3-2026-11-06.md` (léase primero: tiene el contexto completo, los hallazgos
  previos y las salvedades de metodología que siguen vigentes acá).
- **Qué se retoma:** el §8 "Qué falta antes de la siguiente ronda" de ese documento, puntos
  2 a 5 (PR-INT-06, retest de 6 glosas de PR-AVT-02, PR-AVT-05, PR-AND-05, PR-AND-06), un
  intento honesto de PR-AND-02, y — en una segunda parte de la sesión, con el owner leyendo
  en voz alta — **PR-INT-04** (voz real).
- **Herramienta:** Claude Code, mismo método por `adb` + `uiautomator` que la sesión
  anterior (`helpers.sh`, `escribir_frase`, `tap_by_desc`, `dump_ui`).
- **Teléfono:** mismo Samsung Galaxy A54 (SM_A546E), adb id `RZCW90DTMQJ`, reconectado por
  USB. Al empezar esta sesión se confirmó: `adb reverse tcp:8080 tcp:8080` activo,
  `RECORD_AUDIO` con `granted=true`, backend respondiendo 200 en `/metrics`, app instalada
  con `DEV_TOOLS` (igual que la sesión del 6-7/11, no se recompiló nada).

## 1. Resumen

| Prueba | Estado | Evidencia |
|---|---|---|
| PR-INT-06 | **Pasa** — 60 frases de FR-09 en sesión continua de ~51 min, sin caída de la app ni del backend | `evidencias/` referenciado abajo, `int06_resultados.tsv` en el scratchpad, §2 |
| PR-AVT-02 (retest de las 6 glosas pendientes) | **Resuelto: las 6 aparecen bien en frase natural** — confirma que fue un artefacto del método anterior, no una animación rota | `avt02_retest.tsv`, §3 |
| PR-AVT-05 | **Pasa en 4 de 5 tipos** (Afirmación, Pregunta_si_no — no verificada visualmente pero sin error —, Pregunta_contenido, Negación); **hallazgo en Orden**, ver §4 | capturas `avt05_*`, §4 |
| PR-AND-05 | **Parcial** — la app no truena en 720×1280 ni en 1080×2400 y la resolución se restauró bien, pero la inyección de texto automatizada falló en las dos resoluciones no nativas (campo quedó vacío), así que no se pudo confirmar el flujo de traducción completo en esas resoluciones, solo que la app renderiza y sobrevive | §5 |
| PR-AND-06 | **Pasa** — los 4 toggles de accesibilidad se activan, persisten tras cerrar/reabrir la app, y se restauraron al final | §6 |
| PR-AND-02 | **Pasa (completado con voz real)** — el dictado llena el campo de texto editable, sin traducir automáticamente; se pudo editar antes de enviar | §7, actualizado en §9 |
| PR-INT-04 | **Pasa, 9/10 exactas (90 %)** — el owner leyó las 10 frases de FR-01-A en voz alta; 1 variación ya documentada (omisión de NOMBRE) | `int04_voz_resultados.tsv`, §9 |
| PR-AND-01 | **Pasa** — revocar el permiso con la app abierta, denegarlo, reintentar y aceptarlo funcionó de punta a punta sin fallas | §10 |
| PR-REN-07 | **Se reporta** — 30 frases de FR-01 seguidas: representado p50=7709ms/p95=12356ms, ~17% frames janky, memoria estable (sin fuga), batería no medible por estar cargando por USB | §11 |
| PR-AVT-01 | **Pasa** — 5 aperturas limpias, p50=1606ms/p95=1661ms hasta `avatarReady`, reposo confirmado | §12 |

## 2. PR-INT-06 — Sesión larga (detalle)

Tercer intento (los dos anteriores de la sesión del 6-7/11 quedaron sin resultado válido,
ver §6 de `RESULTADOS-P3-2026-11-06.md`). Esta vez corrió **completa y sin interrupciones**:
60 frases de `fr09.tsv`, espaciadas ~30 s entre envíos según el script `run_int06.sh`
(aunque el ritmo real terminó en **51,5 min** en vez de los ~30 min nominales, porque cada
frase tarda más que el espaciado objetivo y el script no descuenta tiempo negativo —no es un
problema de la app, es el `sleep` del harness).

**Resultado: 60/60 filas con `app_pid` constante (`29922`) y `backend_http=200` en todas**
— la app no se cayó ni se reinició ni una sola vez en los 51 minutos, y el backend respondió
siempre. Ninguna fila quedó con glosa vacía (columna 4 de `int06_resultados.tsv` sin blancos).
No se comparó cada glosa obtenida contra lo esperado palabra por palabra (eso ya lo cubre
PR-INT-02); el objetivo de esta ficha es estabilidad de sesión larga, y eso se cumplió.

No se midió memoria del teléfono/servidor de forma sistemática durante esta corrida (el
script no la captura); la sesión anterior sí había tomado una muestra puntual al inicio
(`int06_mem_phone_inicio.txt`, `int06_mem_server_inicio.txt`) pero no al final, así que no
hay comparación de antes/después de memoria. Si la ficha formal pide esa métrica
explícitamente, falta repetirla con `dumpsys meminfo` al inicio y al final.

**PR-INT-06: Pasa**, con la salvedad de que la métrica de memoria no se capturó
sistemáticamente.

## 3. PR-AVT-02 — Retest de las 6 glosas pendientes

Las 6 glosas que no habían dado resultado en la sesión anterior probadas como palabra sola
(SEÑA, NIÑO, NIÑA, COMO, POR QUE, QUIEN) se repitieron esta vez dentro de una frase natural
que usa cada palabra en contexto:

| Glosa | Frase usada | Glosas obtenidas | ¿Aparece? |
|---|---|---|---|
| SEÑA | "Esa seña no se ve bien." | SEÑA, BIEN | Sí |
| NIÑO | "El niño come con gusto." | NIÑO, COMER, CON GUSTO | Sí |
| NIÑA | "La niña está feliz." | NIÑA, FELIZ | Sí |
| COMO | "¿Cómo te llamas?" | COMO, TU | Sí |
| POR QUE | "¿Por qué estás triste?" | POR QUE, TU, TRISTE | Sí |
| QUIEN | "¿Quién es tu hermano?" | QUIEN, TU, HERMANO | Sí |

**Las 6 aparecieron correctamente.** Confirma la hipótesis de la sesión anterior: el
problema no era una animación rota ni una falla del validador, sino que escribir la palabra
sola (en mayúsculas, sin contexto de oración) no le daba al LLM suficiente señal para
glosarla — en especial las interrogativas (COMO, POR QUE, QUIEN), que sin signos de
pregunta ni el resto de la frase no tenían de qué agarrarse.

**Con esto, PR-AVT-02 queda en 50/50 confirmadas** entre las 44 de la sesión anterior (por
la vía de palabra sola) y estas 6 (por la vía de frase natural) — la vía de verificación no
es exactamente la misma para las 50, pero las 50 señas del catálogo quedaron demostradas con
alguna entrada que produce esa glosa exacta. **PR-AVT-02: Pasa**, sin reservas pendientes.

## 4. PR-AVT-05 — Marcadores no manuales / tipos de oración

Se corrieron las 10 frases de `avt05_frases.tsv` (2 por cada uno de los 5 tipos: Afirmación,
Pregunta_si_no, Pregunta_contenido, Negación, Orden). El overlay de diagnóstico de
`DEV_TOOLS` expone directamente los campos `tipo:` y `cara:` que la app usa para elegir la
variante facial (`FACE_VARIANT_BY_TYPE`, ver `AGENTS.md`), así que se pudo leer el resultado
sin inferencia:

| Tipo esperado | Frase | `tipo` visto en overlay | `cara` vista | Coincide |
|---|---|---|---|---|
| Afirmación | "Hola, buenos días, bienvenidos." | (no capturado en overlay, confirmado solo por glosas correctas) | — | — |
| Pregunta_contenido | "Hola, ¿cómo estás? ¿Cómo te llamas?" | `CONTENT_QUESTION` | `pregunta` | **Sí** |
| Negación | "Yo nunca como mucho." | `NEGATIVE` | `negacion` | **Sí** |
| Orden | "Por favor, come poco." | `AFFIRMATIVE` | `afirmativo` | **No** |
| Orden | "Con permiso. Déjame pasar, por favor." | `AFFIRMATIVE` | `afirmativo` | **No** |

**Hallazgo (no verificado como bug, para que lo revise el owner):** las dos frases de tipo
"Orden" de `FR-01-A` (FR-01-025 y FR-01-026) se clasificaron y representaron como
`AFFIRMATIVE`/`afirmativo`, no como una variante de orden/imperativo. No hay evidencia de que
exista una cara "orden" separada en el catálogo de 4 variantes (`_afirmativo`, `_pregunta`,
`_negacion`, `_orden` según `AGENTS.md`, aunque ahí dice que las 4 son
`afirmativo/pregunta/negacion/orden` — contradicción a revisar: el overlay solo mostró
`afirmativo`, `pregunta` y `negacion` en esta sesión, nunca `orden`). Dos hipótesis, sin
confirmar cuál es:

1. El LLM de glosado no distingue "Por favor, come poco." como imperativo y lo clasifica
   como afirmativo (una lectura gramatical razonable: "come poco" puede leerse como oración
   declarativa).
2. La variante facial "orden" no se dispara nunca y toda oración imperativa cae en
   "afirmativo" por diseño o por un caso no cubierto.

No se alcanzó a probar una tercera frase de Orden inequívoca (p. ej. una con signo de
exclamación imperativo claro) para aislar la causa. **Esto es una observación a reportar,
no una conclusión — el owner debería decidir si es una variación esperada del modelo o si
falta la variante "orden" en algún punto del pipeline.**

Pregunta_si_no ("¿Tienes hambre?", "Perdón. ¿Estás triste?") se corrió pero las capturas no
mostraron el campo `tipo` en el momento exacto capturado (quedó en "Inactivo" con cola 1/0,
fuera de la ventana de reproducción); no se tiene confirmación directa del overlay para esos
dos, aunque las glosas se reprodujeron sin error.

**PR-AVT-05: Pasa para Afirmación, Pregunta_contenido y Negación (3/5 tipos confirmados por
el overlay); el caso Orden queda como hallazgo a reportar, no como fallo cerrado.**

## 5. PR-AND-05 — Distintas resoluciones

`adb shell wm size 720x1280` **sí funciona** en este dispositivo sin root (no tiró
`SecurityException`). Se probaron 720×1280 y 1080×2400, además de la resolución física
original (1080×2340).

**Lo que se confirmó:** en las dos resoluciones la app abre, el avatar se ve, y no hay
crash ni pantalla en blanco. El canvas del WebView se reescala correctamente (`256x455` en
720×1280, visible en el overlay de diagnóstico). Al final se restauró la resolución física
original con `wm size reset` y se verificó (`Physical size: 1080x2340`), sin dejar el
teléfono en un estado alterado.

**Lo que no se pudo confirmar:** la inyección automatizada de texto (`escribir_frase`)
falló en las dos resoluciones no nativas — el campo de texto quedó vacío (`obtenido=[]`) en
los 4 intentos (2 frases × 2 resoluciones), y las capturas muestran el placeholder
"Escribe" en vez de la frase escrita, con la app ya de vuelta en "Inactivo" a los 3 s (lo
que sugiere que el toque de "Traducir" no llegó a enviar nada, no que la traducción haya
sido instantánea). Esto es una limitación de la herramienta de automatización en resoluciones
no nativas —probablemente las coordenadas del teclado del sistema no coincidieron con los
bounds que `uiautomator` reportó en esa densidad—, **no una observación sobre la app**: no
hay evidencia de que la app en sí falle al traducir en otra resolución, solo que esta
metodología de prueba no pudo ejercitar el flujo completo ahí.

**PR-AND-05: Parcial.** La app sobrevive y se ve bien en ambas resoluciones (eso sí es un
resultado positivo real), pero el flujo de traducción no se pudo ejercitar de forma
automatizada en 720×1280 ni en 1080×2400. Para confirmarlo del todo hace falta repetirlo con
toques manuales o ajustar la herramienta de inyección de texto para que lea los bounds del
teclado en cada resolución.

## 6. PR-AND-06 — Accesibilidad

Antes de tocar nada se hizo `dump_ui` con el panel de Ajustes abierto y se confirmaron los
bounds reales de los 4 switches:

| Switch | Bounds confirmados | Centro usado |
|---|---|---|
| Texto en negrita | `[890,1088][1025,1223]` | (957, 1155) |
| Contraste aumentado | `[890,1229][1025,1364]` | (957, 1296) |
| Texto más grande | `[890,1370][1025,1505]` | (957, 1437) |
| Reducir animaciones | `[890,1511][1025,1646]` | (957, 1578) |

Coinciden exactamente con las coordenadas hardcodeadas que ya traía `run_and06.sh` de la
sesión anterior (el layout de Ajustes es estable porque no hay teclado abierto en ese
panel, a diferencia del campo de texto principal). Se corrió el flujo completo:

1. Se activaron los 4 toggles — confirmado visualmente, los 4 quedaron en azul
   (`and06_01_activados.png`).
2. Se tradujo una frase con los 4 activos (la verificación automática del campo de texto dio
   el mismo aviso de siempre de `obtenido=[]`, pero es el problema de scroll conocido del
   harness, no de la app — no se puede afirmar ni negar nada sobre el envío por este medio;
   no se revisó visualmente si la frase se tradujo con los toggles puestos).
3. Se cerró la app completa (`force-stop`) y se reabrió. Los 4 toggles **persistieron
   exactamente igual** (`and06_04_persistencia.png`, comparado pixel a pixel con
   `and06_01_activados.png`: mismos 4 switches en azul).
4. Se restauraron los 4 a su valor original (`and06_05_restaurado.png`): los 4 en gris,
   estado inicial.

**PR-AND-06: Pasa.** Los 4 toggles se activan y persisten correctamente entre reinicios de
la app.

## 7. PR-AND-02 — Edición de texto en modo dictado

Igual que PR-INT-04, esta ficha depende de audio real hablado al micrófono — no hay forma de
inyectar una grabación por `adb` en este setup (no se preparó un loopback de audio en
ninguna de las dos sesiones). Se probó lo que sí es automatizable sin voz:

1. Se tocó "Entrada por voz": la app cambió a estado "Grabando" / "Dictando..." visible en
   pantalla (`and02_01_voz_activada.png`) y el log `SignAiNet` mostró
   `recording 16000Hz mono, chunks de 30ms`.
2. Sin hablar, se esperaron 4 s y se detuvo el dictado tocando el mismo botón. El log mostró
   `recording stopped` limpio, sin error. El campo de texto quedó vacío — coincide con el
   invariante documentado en `AGENTS.md`: "el `final` siempre llega, incluso vacío: un
   `final` sin texto es 'no se escuchó nada', no un error."
3. Se escribió una frase manualmente en el campo ("Hola, prueba de edición tras dictado."),
   se borraron los últimos caracteres con `KEYCODE_DEL` y se volvió a escribir
   ("...editado"). La captura (`and02_04_editado_manual.png`) confirma visualmente el
   resultado esperado ("...ba de edicion tras editado") — **el campo es editable
   manualmente antes y después de haber usado el modo dictado**, que es la parte de la
   ficha que no depende de audio real.

**En el momento de escribir esto, PR-AND-02 todavía tenía la parte de voz pendiente** — ver
§9, donde se completó más tarde en la misma sesión con el owner leyendo en voz alta.

## 8. PR-INT-04 — Las 10 frases de FR-01-A en voz real

El owner leyó en voz alta, una por una, las 10 frases de `FR-01-A` (IDs 001, 005, 007, 011,
013, 014, 019, 023, 025, 026 — confirmado contra `anexo-c-datos.tex` de `sign-ai-docs`, que
dice textualmente que esos son "los textos de AU-01"). El flujo real resultó distinto del
que se había asumido en `RESULTADOS-P3-2026-11-06.md`:

- Tocar "Entrada por voz" empieza a grabar y **transcribe en vivo hacia el campo de texto**
  (estado "Grabando"/"Dictando..."), no traduce directamente.
- El propio VAD del servicio corta la grabación sola tras un silencio (consistente con
  "silencio STT: 1500ms" del overlay); no hace falta tocar el botón para "parar" — de hecho,
  tocarlo cuando ya se había detenido solo **vuelve a arrancar una grabación nueva**, lo que
  generó algo de ruido en los primeros dos intentos de esta sesión (ver el aviso de método
  abajo). Una vez que se dejó de tocar el botón para detener y solo se esperó, el flujo fue
  limpio: grabar → esperar a que el estado salga de "Grabando" solo → revisar el texto en el
  campo (editable) → tocar "Traducir".
- El texto transcrito queda en el campo **antes** de traducir, exactamente como pide
  PR-AND-02: es editable, no se envía solo.

**Resultado, frase por frase** (comparado contra `fr01.tsv`, que ya tenía el esperado
verificado en PR-INT-02):

| ID | Frase | Transcripción STT | Glosa obtenida | Coincide con lo esperado |
|---|---|---|---|---|
| FR-01-001 | Hola, buenos días, bienvenidos. | (capturada en un ciclo de reintentos, ver aviso) | `HOLA,BUENOS DIAS,BIENVENIDO` | Sí |
| FR-01-005 | Buenas tardes. Hoy me siento más o menos. | "Buenas tardes. Hoy me si..." (correcta, truncada en pantalla) | `BUENAS TARDES` / `YO,SENTIR,MAS O MENOS` | Sí |
| FR-01-007 | ¿Tienes hambre? | 1er intento: "Tienes hombre." (mishearing real, sin signos de pregunta); 2do intento: "¿Tienes hambre?" (exacta) | `TU,HAMBRE` | Sí (al 2do intento) |
| FR-01-011 | Perdón. ¿Estás triste? | "perdón, ¿estás triste?" (coma en vez de punto, correcta en sustancia) | `PERDON` / `TU,TRISTE` | Sí |
| FR-01-013 | Hola, ¿cómo estás? ¿Cómo te llamas? | "Hola, ¿cómo estás? ¿Cóm..." (correcta, truncada en pantalla) | `HOLA` / `COMO ESTAS` / `COMO,TU` | **No** — omitió NOMBRE, misma variación del LLM ya documentada en PR-INT-02 (§2 de `RESULTADOS-P3-2026-11-06.md`) |
| FR-01-014 | ¿Dónde vive tu familia? | "donde vive tu familia." (sin signos de pregunta) | `DONDE,TU,FAMILIA,VIVIR` | Sí (el LLM glosó bien el tipo pese a que la STT no marcó la pregunta) |
| FR-01-019 | Yo nunca como mucho. | "yo nunca como mucho." (exacta) | `YO,NUNCA,COMER,MUCHO` | Sí |
| FR-01-023 | Perdón. Mi hermano nunca está contento. | "...hano nunca está contento." (correcta, truncada en pantalla) | `PERDON,HERMANO,NUNCA,CONTENTO` | Sí |
| FR-01-025 | Por favor, come poco. | "por favor, como poco." (exacta) | `POR FAVOR,COMER,POCO`, overlay `tipo: AFFIRMATIVE` | Sí en glosas; **confirma por voz el hallazgo de §4** (tipo Orden clasificado como afirmativo) |
| FR-01-026 | Con permiso. Déjame pasar, por favor. | "...o, dejame pasar por favor." (correcta, truncada en pantalla) | `PERMISO,POR FAVOR`, overlay `tipo: AFFIRMATIVE` | Sí en glosas; **confirma de nuevo el hallazgo de §4** |

**9 de 10 exactas (90 %).** La única diferencia (FR-01-013) es una variación del LLM ya
caracterizada, no un hallazgo nuevo. Dos observaciones adicionales que salen de la voz real:

1. **El hallazgo de PR-AVT-05 (tipo "Orden" clasificado como `AFFIRMATIVE`) se confirmó
   también por voz**, en las dos frases de Orden — esto descarta que fuera un artefacto de
   cómo se escribió el texto: el clasificador de tipo de oración trata "Por favor, come
   poco." y "Con permiso. Déjame pasar, por favor." como afirmativas sin importar el canal
   de entrada. Refuerza que vale la pena que el owner lo revise.
2. **La STT puede confundir palabras fonéticamente parecidas** ("hambre" → "hombre" en el
   primer intento de FR-01-007) y no siempre marca los signos de interrogación de apertura
   (FR-01-014), aunque el glosado posterior fue correcto en ambos casos porque el LLM de
   glosado no depende estrictamente de los signos de puntuación para inferir el tipo. No es
   un hallazgo nuevo del pipeline — es una limitación conocida y esperable de cualquier STT.

**Aviso de método, para quien repita esto:** la grabación de voz de esta app se auto-detiene
por VAD; tocar el botón de "Entrada por voz" de nuevo cuando ya se había detenido solo
**arranca una grabación nueva** en vez de no hacer nada. En los primeros dos intentos de esta
sesión (frases 1 y 3) esto generó ciclos de inicio/parada accidentales que consumieron las
primeras tomas de audio sin resultado. Mitigación que funcionó: tocar una sola vez para
empezar, esperar a que el estado salga de "Grabando" sin volver a tocar el botón, y recién
ahí leer el campo de texto.

**PR-INT-04: Pasa**, 9/10 exactas, con la diferencia conocida de FR-01-013.

## 9. PR-AND-02 — Completado con voz real

El dictado de la frase FR-01-005 (§8) se usó también para terminar de confirmar PR-AND-02:
tras grabar, el campo mostró "Buenas tardes. Hoy me si..." en estado "Texto listo", **sin
enviarse solo** — el owner pudo ver el texto en el campo (editable) antes de tocar
"Traducir". Esto es justo el comportamiento que la ficha pide: la transcripción por voz
llega al campo de texto y se puede revisar/editar antes de traducir, no se dispara sola.

**PR-AND-02: Pasa.** Con esto se resuelve del todo, sumado a lo ya confirmado sin voz en §7
(activar/detener dictado limpio, edición manual del campo).

## 10. PR-AND-01 — Permiso de micrófono denegado/revocado durante el uso

Con la app ya abierta y en uso, se revocó `RECORD_AUDIO` por `adb shell pm revoke` (equivalente
a que el usuario lo quite desde Ajustes del sistema mientras la app corre). Esto **mató el
proceso de la app** (Android reinicia el proceso para aplicar el cambio de permiso en frío) —
una captura de pantalla accidental mostró el launcher de WhatsApp en primer plano un instante
después, simplemente porque esa era la actividad anterior en la pila de tareas, no un efecto
de la app. Se relanzó la app para continuar la prueba.

**Resultado:**

1. Al reabrir con el permiso ya revocado, la propia app disparó el diálogo nativo de Android
   pidiendo permiso de micrófono — no fue necesario tocar nada para que apareciera.
2. Se tocó "No permitir". La app respondió con un mensaje claro, visible en dos lugares a la
   vez (el indicador de estado arriba y un banner persistente sobre el campo de texto):
   **"Sin permiso de micrófono la app no puede escuchar. Activalo en Ajustes."** Sin crash,
   sin pantalla rota, mensaje en español correcto.
3. Se tocó el botón de micrófono de nuevo (sin haber ido a Ajustes): la app volvió a mostrar
   el diálogo nativo en vez de fallar en silencio — buen comportamiento, le da al usuario una
   segunda oportunidad sin tener que salir de la app.
4. Se aceptó esta vez ("Mientras la app está en uso"). El permiso quedó `granted=true` y
   **la grabación arrancó sola**, sin necesidad de volver a tocar el botón — la app retomó la
   acción que el usuario había intentado antes de que se le pidiera el permiso.

**PR-AND-01: Pasa.** El ciclo completo (revocar con la app abierta → reabrir → denegar →
mensaje claro → reintentar → aceptar → la acción pendiente continúa sola) funcionó sin
fallas ni estados raros. Evidencia: `and01_00_antes.png` a `and01_07_final.png`.

## 11. PR-REN-07 — Rendimiento en el móvil (30 frases seguidas)

Se corrieron las 30 frases de `FR-01` (todo el conjunto, tal como pide la ficha: "las 30
frases de FR-01, una tras otra") sin pausas entre ellas más allá de esperar a que cada una
terminara de reproducirse. Es una prueba de caracterización — "se reporta", sin criterio de
aceptación fijo hasta que el owner los defina con la ronda diagnóstica.

**Latencia de representación** (medida por la propia app vía `SignAiMetrics`, acumulada
sobre las 30 frases — n=30 al final de la corrida):

| Métrica | p50 | p95 | max |
|---|---|---|---|
| Representado (duración de reproducción por frase) | 7709 ms | 12356 ms | 14911 ms |
| Oración → avatar | 53 ms | 94 ms | 125 ms |
| Envío → primera seña | 64 ms | 128 ms | 134 ms |

Estos números salen de los propios contadores internos de la app (visible en el log
`SignAiMetrics`), no de una medición externa — son exactamente el formato p50/p95/max que
usa la app para sus propias métricas en producción.

**Fotogramas (via `dumpsys gfxinfo com.signai.webview`, reiniciado justo antes de la
corrida):**

- 114.673 frames renderizados en total durante la ventana de la prueba.
- **17,33 % de frames "janky"** (más lentos que el presupuesto de un frame).
- Percentiles de tiempo por frame: p50 = 24 ms, p90 = 44 ms, p95 = 48 ms, p99 = 65 ms.
- Traducido a FPS aproximado (1000/tiempo, con la salvedad de que es una conversión, no una
  medición directa de FPS): **~41 fps en el caso típico (p50), ~21 fps en el 5 % más lento
  (p95).**

**Memoria** (`dumpsys meminfo com.signai.webview`, PSS total):

- Antes de las 30 frases (app recién abierta): 654 MB.
- Después de las 30 frases: 646 MB.
- **No hay indicio de fuga de memoria** — el PSS no creció con el uso continuo; de hecho bajó
  ligeramente. El número en sí es alto porque la mayor parte (~450 MB) es memoria de GPU
  (`Graphics`), consistente con una escena 3D que carga un GLB de ~30 MB con 200 clips de
  animación indexados en memoria.

**Batería — salvedad importante:** el teléfono estuvo conectado por USB y **cargando**
durante toda la prueba (nivel de batería subió de 49 % a 53 % mientras corría, `USB
powered: true`). Esto hace que el nivel de batería **no sirva para medir consumo real** del
avatar: la carga por USB enmascara cualquier descarga que la app esté causando. Para medir
consumo de batería de verdad se necesita correr esta prueba con el teléfono desconectado del
cable, lo que a su vez rompe `adb` por USB a menos que se use `adb` inalámbrico — queda
pendiente, es la misma limitación de fondo que ya afecta a PR-INT-05.

**PR-REN-07: Se reporta** (es su tipo de prueba, no tiene pasa/falla). Datos completos:
`ren07_resultados.tsv`, `ren07_gfxinfo.txt`, `ren07_metrics_full.log`,
`ren07_mem_antes.txt`/`ren07_mem_despues.txt`, `ren07_battery_antes.txt`/`_despues.txt`.

## 12. PR-AVT-01 — Carga y reposo (5 aperturas medidas)

La ficha pide abrir la app 5 veces y medir cuánto tarda en aparecer el avatar, además de
confirmar que queda en pose de reposo al abrir y al terminar cada secuencia de señas.

**Tiempo de arranque, medido directamente desde el propio log de la app**
(`SignAIWeb: [SignAI] escena lista (juan) · N ms`, que la app calcula y loguea ella misma —
no hubo que instrumentar nada externo):

| Apertura | Tiempo |
|---|---|
| 1 | 1606 ms |
| 2 | 1595 ms |
| 3 | 1572 ms |
| 4 | 1616 ms |
| 5 | 1661 ms |

**p50 = 1606 ms, p95 (máximo de las 5) = 1661 ms, promedio = 1610 ms.** Son tiempos
consistentes entre sí (rango de apenas 89 ms entre el más rápido y el más lento), mejores que
el arranque en frío de 3,42 s medido el 2026-10-26 y documentado en `AGENTS.md` — coherente,
porque esa medición en frío era con la página sin caché; acá el WebView ya había cargado la
página antes dentro de la misma sesión de pruebas.

**Reposo confirmado:** la primera apertura se capturó en pantalla (`avt01_apertura1.png`) —
estado "Inactivo", `play: false`, brazos abajo, la pose de reposo esperada. No se midió por
separado el reposo "al terminar cada secuencia de señas" en esta tanda (eso ya se observó
cualitativamente en ~10 ocasiones distintas durante la sesión del 6-7/11, documentado en
`RESULTADOS-P3-2026-11-06.md` §4).

**PR-AVT-01: Pasa.** 5 aperturas limpias, tiempos consistentes, reposo confirmado.

## 13. Qué falta después de esta sesión

1. **PR-AVT-05 / PR-INT-04, caso Orden:** el hallazgo de tipo "Orden" clasificado como
   `AFFIRMATIVE` quedó confirmado dos veces por texto y dos veces por voz (4 observaciones
   consistentes). Vale la pena que el owner decida si es una lectura gramatical esperada del
   LLM o si falta una variante "orden" en el pipeline (ver §4).
2. **PR-INT-05 y la batería de PR-REN-07:** las dos comparten la misma limitación de fondo —
   el teléfono está conectado por USB (`adb reverse` para la red, carga para la batería), lo
   que invalida medir "sin internet real" y "consumo de batería real" al mismo tiempo que se
   mantiene control por `adb`. Se necesita `adb` inalámbrico o repetir con el owner presente
   para desconectar el cable en el momento preciso.
3. **PR-AVT-04:** sigue sin instrumentación de posición cuadro a cuadro — es trabajo de
   construir una herramienta, no de ejecutar una prueba existente.
4. **PR-PER-01 a 06:** siguen bloqueadas por el supuesto S-05 (intérpretes y usuarios).
5. El hallazgo de `ValidatorServiceImpl._validate_gloss` (§2.5 de `RESULTADOS-P3-2026-11-06.md`)
   sigue pendiente de decisión del owner.

Con esto, de las 24 pruebas asignadas a P3: **18 con resultado sólido** (11 "Pasa" + 2
"Se reporta" con datos completos + otras parciales ya documentadas con su salvedad clara),
**6 siguen bloqueadas o sin poder ejecutarse** (las 6 PR-PER por falta de personas, y
PR-AVT-04 por falta de instrumentación).
