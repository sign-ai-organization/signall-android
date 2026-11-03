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
| 1 | 8a · Datos de prueba | Grabar los **79 audios** con un celular: AU-01 (60), AU-02 (10), AU-03 (8) y AU-04 (1). AU-01 necesita **4 voces distintas**: el owner (H1) más tres personas (H2–H4). Lo demás lo graba el owner solo. CT-02 (videos) depende de personas con LSC, no del owner. | `../sign-ai-docs/borradores/plan-pruebas/02-datos/` | Tarda semanas | Pendiente |
| 2 | 2 · Rebote de los paneles | Quitar el rebote al abrir y al soltar el arrastre de los dos drawers. | `ui/designsystem/components/EdgeDrawer.kt` | Bajo | **Hecho** (`14f0500`, `3ba23d4`) |
| 3 | 3 · Ajustes: velocidad con número y modo debug | Revisar todas las filas de Ajustes y pasar la velocidad de señas, que hoy es un slider sin número, a un control de menos y más como «Tamaño de interfaz». El modo debug deja de ser un interruptor guardado en el teléfono y pasa a encenderse por variable de entorno o propiedad de compilación, así que el bloque AVANZADO no aparece en producción. | `ui/screens/settings/SettingsSidebar.kt` + `app/build.gradle.kts` | Bajo | Pendiente |
| 3b | 4b · SRS: AVANZADO solo de desarrollo | Proponer en el SRS el cambio de redacción del bloque AVANZADO, que hoy aparece como parte de la interfaz (contraste A11) y ahora es solo de desarrollo. Va con control de cambios y lo aprueba el owner. | Overleaf (SRS) | Bajo | Pendiente (permiso por tanda) |
| 4 | 4 · AVANZADO en release | Decidir si el bloque AVANZADO se oculta en release y alinear el SRS con esa decisión (contraste A11). | `SettingsSidebar.kt` + SRS | Bajo, pero es decisión | **Decidido** (se hace con el orden 3; el texto del SRS va en 3b) |
| 5 | 5 · Conversación | Pasar la lista «TÚ / TRADUCCIÓN» a burbujas de chat. | `ui/screens/conversation/ConversationPanel.kt` | Medio | Pendiente |
| 6 | 1 · Requerimientos de Julián | Bajar el artifact al SRS §3.2–3.6 y cerrar los 24 TODO del consolidado v0.16. | artifact + `../sign-ai-docs` + Overleaf | Medio | Pendiente (falta el texto del artifact) |
| 7 | 7 · Instrumentación | Llenar §7.3 del plan de pruebas (qué se mide, en qué repo, con qué tag o endpoint) y armar la **plantilla de resultados** con una fila por prueba: entorno, datos, métrica, resultado y evidencia. | `../sign-ai-docs` | Medio | Pendiente |
| 8 | 6 · Marcadores faciales | Medir si las variantes `_alegria`/`_tristeza`/`_sorpresa`/`_pregunta`/`_negacion` mueven la cara en `juan.glb` y `pauline.glb`, decidir el mapeo A/B/C y hacer el trabajo en Cascadeur. | `../signai-avatar` + app | Alto | Pendiente (medición + decisión) |
| 9 | 8b · Correr las pruebas | Ejecutar las **73 pruebas** con un agente sobre el celular, el backend y el STT, y anotar cada resultado en la plantilla. Depende del orden 1 (los audios) y del orden 7 (la plantilla). El número está en discusión: hay 73 fichas, la tabla de la sección 9 suma 72 y `resumen.md` dice 74. | `../sign-ai-docs` | Alto | Pendiente |

## Bitácora

Una entrada por sesión que vuelve, con fecha, tarjeta, qué quedó hecho y qué quedó abierto.
Sin prosa: renglones.

| Fecha | Tarjeta | Resultado | Quedó abierto |
|---|---|---|---|
| 2026-11-03 | 2 · Rebote de los paneles | Resorte crítico (ζ = 1) en lugar de `MediumBouncy`: sin sobrepaso (antes 16,3 % = ~49 dp). Fuera la franja `drawerOverflow` de 64 dp. El soltar decide primero por velocidad (125 dp/s) y después por la mitad del panel. De paso: borrada la copia duplicada del preview de arrastre y las filas de Ajustes se apilan bajo 270 dp (`SET-9`). Medido en el Redmi Note 9S: 1080 → 131 px en ~167 ms, sin un frame de retroceso; verificado a 392, 360 y 308 dp, en claro y oscuro. Specs `EDG-5`, `EDG-25`, `SET-9`. | Los iconos de la barra de estado al abrir el panel (decisión del owner, es layering e insets). `EDG-24`: mover la pestaña 12–24 dp adentro del borde. `EDG-6`: el label del preview nunca se ve, así que falta decidir si se borra o se dibuja encima. |

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
- **Conteo de pruebas**: 73 fichas, 72 en la tabla de la sección 9 y 74 en `resumen.md`. Se
  fija en el orden 7.
