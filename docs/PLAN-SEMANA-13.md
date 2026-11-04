# Semana 13 — orden de trabajo

Esta es la sesión maestra: **ordena y registra, no implementa**. Cada tarjeta se trabaja en
su propia sesión, y acá vuelve solo el resultado.

Reglas de la semana:

- Se hace **una tarjeta por sesión**, en el orden de la tabla.
- El alcance de una sesión es **exactamente lo que dice su fila**. Lo que no está escrito ahí
  no se hace, ni se agrega "de paso", sin preguntar primero.
- No se abre la siguiente hasta que la anterior esté cerrada: compilada, instalada si hay
  device conectado, y contada acá abajo.
- Si una tarjeta se traba por una decisión del owner, se para y se anota. No se reordena
  sola ni se salta a otra.

## Tabla

| Orden | Tarjeta | Qué se hace | Dónde | Costo | Estado |
|---|---|---|---|---|---|
| 1 | 8a · Datos de prueba | Grabar los **79 audios** con un celular: AU-01 (60), AU-02 (10), AU-03 (8) y AU-04 (1). AU-01 necesita **4 voces distintas**: el owner (H1) más tres personas (H2–H4). Lo demás lo graba el owner solo. CT-02 (videos) depende de personas con LSC, no del owner. | `../sign-ai-docs/borradores/plan-pruebas/02-datos/` | Tarda semanas | **69 de 79 organizados**: 39 de H1 (completos, con AU-04 regrabado y verificado) y 30 de los compañeros (los 20 de Julián y los 10 de Sergio). **Faltan los 10 de Samuel** (`AU-01-003`, `009`, `015`, `021`, `027`, `033`, `039`, `045`, `051`, `057`). Los diez con ruido están en la voz de Sergio y 6 dB bajo el bloque de H1: falta decidir si se quedan documentados o se regraban. |
| 2 | 2 · Rebote de los paneles | Quitar el rebote al abrir y al soltar el arrastre de los dos drawers. | `ui/designsystem/components/EdgeDrawer.kt` | Bajo | **Hecho** (`14f0500`, `3ba23d4`) |
| 3 | 3 · Ajustes: velocidad con número y modo debug | Revisar todas las filas de Ajustes y pasar la velocidad de señas, que hoy es un slider sin número, a un control de menos y más como «Tamaño de interfaz». El modo debug deja de ser un interruptor guardado en el teléfono y pasa a encenderse por variable de entorno o propiedad de compilación, así que el bloque AVANZADO no aparece en producción. | `SettingsSidebar.kt` + `app/build.gradle.kts` | Bajo | **Hecho en código** (`3be7256`, `ff6d47a`) |
| 3b | 4b · SRS: AVANZADO solo de desarrollo y velocidad con botones | Proponer en el SRS dos cambios de redacción: el bloque AVANZADO pasa a ser de desarrollo (hoy aparece como parte de la interfaz, contraste A11) y la velocidad deja de describirse como una barra. Va con control de cambios y lo aprueba el owner. | Overleaf (SRS) | Bajo | **Hecho** — versiones 639 → 641 con `tracked: true`; el owner aceptó los dos cambios en Overleaf el 2026-11-03 |
| 3c | 3c · Spec local al día | Actualizar `openspec/specs/ui/spec.md`: `SET-4`, `SET-8`, `SET-9`, `ACC-1` y la tabla de íconos siguen describiendo la barra, el switch de debug y los íconos viejos. | `openspec/specs/ui/spec.md` | Bajo | **Hecho** (`14d768e`) |
| 4 | 4 · AVANZADO en release | Decidir si el bloque AVANZADO se oculta en release y alinear el SRS con esa decisión (contraste A11). | `SettingsSidebar.kt` + SRS | Bajo, pero es decisión | **Decidido** (se hace con el orden 3; el texto del SRS va en 3b) |
| 5 | 5 · Conversación | Pasar la lista «TÚ / TRADUCCIÓN» a burbujas de chat. | `ui/screens/conversation/ConversationPanel.kt` | Medio | **Hecho** (`44264e5`, `1ab771d`) |
| 6 | 1 · Requerimientos de Julián | Revisar el artifact «Contraste del backend» fila por fila contra el código y los documentos de hoy (se armó el 21/10 contra `a03bbc9`), documentar qué cambió desde entonces y qué queda por decidir. Después vendrá el artifact del STT, que es corto, y el paso al SRS §3.2–3.6 y al consolidado v0.16. | artifact + `../sign-ai-backend` + `../sign-ai-docs` | Medio | **En curso**: el owner pasó el artifact el 2026-11-03 y se extrajo a `/tmp/opencode/contraste-backend-artifacto.md` |
| 7 | 7 · Instrumentación | Llenar §7.3 del plan de pruebas (qué se mide, en qué repo, con qué tag o endpoint) y armar la **plantilla de resultados** con una fila por prueba: entorno, datos, métrica, resultado y evidencia. | `../sign-ai-docs` | Medio | Pendiente |
| 8 | 6 · Marcadores faciales | Medir si las variantes `_alegria`/`_tristeza`/`_sorpresa`/`_pregunta`/`_negacion` mueven la cara en `juan.glb` y `pauline.glb`, decidir el mapeo A/B/C y hacer el trabajo en Cascadeur. | `../signai-avatar` + app | Alto | Pendiente (medición + decisión) |
| 9 | 8b · Correr las pruebas | Ejecutar las **73 pruebas** con un agente sobre el celular, el backend y el STT, y anotar cada resultado en la plantilla. Depende del orden 1 (los audios) y del orden 7 (la plantilla). El número está en discusión: hay 73 fichas, la tabla de la sección 9 suma 72 y `resumen.md` dice 74. | `../sign-ai-docs` | Alto | Pendiente |

## Bitácora

Una entrada por sesión que vuelve, con fecha, tarjeta, qué quedó hecho y qué quedó abierto.
Sin prosa: renglones.

| Fecha | Tarjeta | Resultado | Quedó abierto |
|---|---|---|---|
| 2026-11-03 | 2 · Rebote de los paneles | Resorte crítico (ζ = 1) en lugar de `MediumBouncy`: sin sobrepaso (antes 16,3 % = ~49 dp). Fuera la franja `drawerOverflow` de 64 dp. El soltar decide primero por velocidad (125 dp/s) y después por la mitad del panel. De paso: borrada la copia duplicada del preview de arrastre y las filas de Ajustes se apilan bajo 270 dp (`SET-9`). Medido en el Redmi Note 9S: 1080 → 131 px en ~167 ms, sin un frame de retroceso; verificado a 392, 360 y 308 dp, en claro y oscuro. Specs `EDG-5`, `EDG-25`, `SET-9`. | Los iconos de la barra de estado al abrir el panel (decisión del owner, es layering e insets). `EDG-24`: mover la pestaña 12–24 dp adentro del borde. `EDG-6`: el label del preview nunca se ve, así que falta decidir si se borra o se dibuja encima. |
| 2026-11-03 | 3 · Ajustes: velocidad y modo debug | Velocidad con dos botones − y +, valor visible al medio, paso 10 y rango 10–100; un valor viejo fuera de grilla (52 en el teléfono del owner) se alinea al primer toque, y el cambio entra en la seña siguiente sin cortar la actual. Ícono con sentido en cada fila, etiquetas de una línea y el selector de seña de ejemplo con el mismo peso que el de Personaje. AVANZADO y diagnóstico ya no son preferencia: los enciende `BuildConfig.DEV_TOOLS` (`-Psignai.devTools=true` o `SIGNAI_DEV_TOOLS=1`) y la preferencia vieja se ignora y se borra, así que la app de producción no los muestra. Verificado en el Redmi Note 9S, claro y oscuro, ancho y angosto, y con el build de producción. | `openspec/specs/ui/spec.md` sin actualizar (`SET-4`, `SET-8`, `SET-9`, `ACC-1`, tabla de íconos). El SRS describe la velocidad como barra y el bloque AVANZADO como parte de la interfaz: orden 3b. |
| 2026-11-03 | 3b + 3c · Spec local y SRS | Spec puesto al día contra el código de `3be7256`, no contra el mensaje del commit: `SET-4` (botones − / +, paso 10, rango 10–100, alineación al primer toque, cambio en la seña siguiente), `SET-8` (AVANZADO por `BuildConfig.DEV_TOOLS`, apagado por defecto incluso en `debug` y siempre falso en `release`/`demo`), `SET-9` (ícono por fila y el apilado nuevo), `BRG-6` y la tabla de íconos (`14d768e`). En el SRS, dos redacciones propuestas con control de cambios y `tracked: true` (versiones 639 → 641): AVANZADO como herramienta de desarrollo y velocidad con dos botones; `compile` con 0 errores. `./gradlew build` limpio. El owner aceptó los dos cambios en Overleaf el 2026-11-03, así que el SRS ya dice lo que hace la app. | Las 93 advertencias de `compile` no se compararon contra una línea base. |
| 2026-11-03 | 5 · Conversación | El panel se lee como conversación: la frase a la derecha sobre `soft` y la glosa a la izquierda sobre `surface`, las dos con borde `outline` y etiqueta chica; el azul de marca no entra. Repetir sigue pegado a la glosa (`CNV-5`) y la vista queda anclada al final del último turno, también cuando la glosa crece oración por oración y cuando cambia el tamaño de la tipografía (`CNV-4`); con «Reducir animaciones» el desplazamiento es instantáneo. Nuevos `cornerBubbleStart`/`cornerBubbleEnd` y `conversationBubbleMaxWidthFraction` (88 %, en fracción porque el panel es `min(88 % de la pantalla, 350 dp)`). Verificado en el Redmi Note 9S con frases cortas y largas, muchas frases para el scroll y el estado vacío, claro y oscuro. | El contraste de la etiqueta de 10 sp sobre `soft` en claro es 3,65:1, por debajo de AA para texto chico; el «CONTEXTO» de la cabecera ya estaba en 4,07:1, así que es una limitación que la app traía. Subir `muted` o el tamaño toca la paleta: lo decide el owner. |
| 2026-11-03 | 1 · Audios de los compañeros y habla continua | Llegaron 30 de los 40 audios de AU-01 (`AudiosSignAllCompaneros.zip`): los 20 de Julián y los 10 de Sergio. Samuel no los mandó. Se identificaron por contenido, se transcribieron los 30 y coinciden con la frase de su ID; los pares `002`…`060` son justamente H2 y H4. También llegó la toma nueva de habla continua (`habla.zip`, 96,1 s): da 19 pausas internas de 1,5 s o más con la más corta en 1,86 s, más 1,95 s de silencio al final, así que las 20 frases cierran por silencio y AU-04 queda cerrado. Corpus en `data/audios/` con 69 audios, copia a 16 kHz, `MANIFIESTO.md` y `manifiesto.csv` reconstruidos. | Como dijo el owner, los diez con ruido los grabó Sergio: la voz es de H4 y no de H2 (tono 120–137 Hz contra 86–107 Hz de H2; timbre más parecido a H4 en 9 de 10; estéreo a 135 kbps y todos con la misma hora de creación), y su ruido está 6 dB bajo el de H1. Decisión del owner: dejarlos documentados o regrabar los diez con la voz de H2 al nivel del bloque de H1. Sigue faltando Samuel y la ficha de grabación. |

## Decisiones

| Fecha | Decisión | Consecuencia |
|---|---|---|
| 2026-11-03 | El bloque AVANZADO y el modo debug existen solo en desarrollo. Se activan con una variable de entorno o una propiedad de compilación, nunca con un interruptor guardado en el teléfono. En producción no aparecen. | Se implementa con el orden 3. El consolidado v0.16 y el plan de pruebas no mencionan el bloque, así que el cambio de documento va solo al SRS: orden 3b, con control de cambios, y lo aprueba el owner. |

## Pendientes sueltos

Cosas que salieron de una sesión y no son tarjeta. Se resuelven cuando toque, no reordenan la
tabla.

- **Iconos de la barra de estado con el panel abierto**: en Gmail el panel corre por debajo de
  la barra y los iconos desaparecen; acá el drawer arranca debajo (`EDG-18`) y los iconos
  quedan. Cambia layering e insets, así que lo decide el owner.
- **`EDG-24`**: la pestaña podría dibujarse 12–24 dp adentro del borde para que el dedo caiga
  fuera de la franja del sistema.
- **`EDG-6`**: el preview de arrastre nunca se ve porque el panel real lo tapa. Falta decidir
  si se borra o se dibuja encima.
- **Contraste de la etiqueta de la burbuja en claro**: 3,65:1 sobre `soft` con 10 sp, por debajo
  de AA para texto chico. La cabecera del mismo panel ya está en 4,07:1. Arreglarlo es subir el
  color de `muted` o el tamaño de la etiqueta, y eso toca la paleta: lo decide el owner.
- **Conteo de pruebas**: 73 fichas, 72 en la tabla de la sección 9 y 74 en `resumen.md`. Se
  fija en el orden 7.
