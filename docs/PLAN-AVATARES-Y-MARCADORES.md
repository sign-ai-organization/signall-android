# Plan: avatares Juan y Pauline, y marcadores faciales

> **Documento vivo — obligatorio mantenerlo actualizado.** Toda persona o agente que
> trabaje en esta iniciativa debe actualizar este plan en cada paso que realice: al
> investigar, cambiar código o assets, tomar una decisión, cambiar un documento o detectar
> una diferencia. Debe anotar qué cambió, en qué repo/archivo, con qué evidencia y qué
> queda pendiente. También debe actualizar la matriz de diferencias y el registro de
> cambios de este documento antes de terminar. **No se hacen cambios silenciosos ni se
> deja el plan para “actualizar después”.**

**Estado:** investigación y plan; no se implementó nada.  
**Fecha:** 2026-10-24  
**Alcance pedido:** usar Juan y Pauline desde el selector existente, y determinar cómo integrar las animaciones faciales sin apartarse de la tesis.

## Resumen de lo encontrado

- `Juan_final.glb` y `Pauline_final.glb` están en la raíz del repositorio, sin rastrear por Git. Se trataron como entradas originales: no se editaron.
- Cada archivo contiene las 50 señas (`0000`–`0049`) y seis clips por seña: base, `_alegria`, `_tristeza`, `_sorpresa`, `_pregunta` y `_negacion`. Son **300 clips por avatar**.
- Ambos tienen un esqueleto de 102 articulaciones y canales de animación facial para cejas, ojos, mandíbula y boca. La lectura de los GLB encontró, en las 50 señas de cada archivo, que los tracks de huesos coinciden entre la versión base y sus cinco variantes, mientras cambian los tracks de pesos de malla. Esto confirma que los archivos ofrecen variaciones faciales sin cambiar los movimientos de huesos; no confirma que cada expresión sea lingüísticamente adecuada para una categoría.
- Cada archivo ocupa decenas de MB: Juan, **37.872.312 bytes**; Pauline, **35.272.616 bytes**. Juntos son **73.144.928 bytes** antes del empaquetado. Son cifras leídas de los archivos, no el tamaño del APK.
- La app **no carga todavía ninguno de esos dos archivos**. `app.js` carga `app/src/main/assets/web/models/avatar.glb` (12.978.476 bytes) y resuelve una sola animación por seña. El selector de Ajustes muestra Julián, Samuel y Ximena, pero solo persiste esa elección; no cambia el modelo.
- El backend sí entrega el **tipo gramatical** de cada oración. No clasifica alegría, tristeza ni sorpresa. La app ya recibe el tipo, pero no lo usa para seleccionar una variante facial.

### Identificación de las entradas

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `Juan_final.glb` | 37.872.312 bytes | `2d87e29c3dce58d78dc1a6f3e48bbad456d3cb2511e462d120999949dfdd8748` |
| `Pauline_final.glb` | 35.272.616 bytes | `c91afb2be2d7888aa334f518e61fea75bd9d954bbd2f60f77bc05de1395c8f34` |

El análisis fue estático: leyó las cabeceras, nombres de clips, articulaciones y canales del GLB. No se hizo una inspección visual de las animaciones ni una prueba en el dispositivo.

## Qué dice cada fuente

| Fuente revisada | Qué aporta | Consecuencia para el plan |
|---|---|---|
| Código de la app (`main`, commit `1c027f002af29c5bc67a2a67671546bc704d66d6`) | `CharacterPicker` persiste el personaje, pero `MainScreenState.setCharacter()` no lo comunica a la escena. `app.js` carga una URL fija. El elemento de cola ya lleva `type` hasta JavaScript, donde hoy solo se conserva para depuración. | El selector necesita controlar la carga del modelo. La selección facial puede aprovechar el campo `type` existente **si** se decide una tabla de correspondencias gramaticales; no hace falta cambiar el WebSocket solo para eso. El resolutor actual no reconoce sufijos como `_pregunta` o `_alegria`. |
| Requerimientos consolidados v0.16, fechados 2026-10-21 (`../sign-ai-docs`) | RF-NLP-03 define cinco tipos gramaticales. RF-AVT-03 acota el comportamiento requerido a mover las cejas según el tipo; aún lo marca pendiente. PR-AVT-05 pide una tabla de expresiones esperadas, escrita por el equipo antes de la ronda, y plantea probar dos frases de cada uno de los cinco tipos. | Antes de activar expresiones hay que acordar y documentar esa tabla. No se debe inferir que una categoría gramatical equivale a una emoción. |
| Overleaf SRS, `main.tex`, versión 524 consultada el 2026-10-24 | El resumen habla de expresiones faciales según el tipo de frase; define cinco tipos gramaticales y el ajuste «Personaje» como selector del personaje que ejecuta las señas. No proporciona una correspondencia entre tipos y expresiones emocionales. | Confirma la finalidad general del selector y de los marcadores, pero deja abierto el mapeo concreto. |
| Overleaf SDD, `main.tex`, versión 212 consultada el 2026-10-24 | Describe «los 50 archivos GLB» incluidos en la app y contiene una nota pendiente para agregar avatares, señas y archivos faciales. | El owner decidió usar dos GLB completos, uno por avatar, cada uno con 50 señas y sus variantes. El SDD debe actualizarse para describir ese empaquetado; este agente no escribe en Overleaf. |
| Overleaf Memoria, `sections/descripcionGeneral.tex`, versión 98 consultada el 2026-10-24 | Presenta las expresiones faciales y los marcadores no manuales como necesarios para la coherencia del mensaje, pero no especifica cuándo seleccionar cada clip. | Aporta el objetivo general, no una tabla de ejecución. |
| Plan de pruebas de `../sign-ai-docs`, bloque Avatar, PR-AVT-05 | La tabla esperada para la cara y las frases de prueba son dependencias previas de la verificación. | Esa tabla debe ser un entregable antes de dar por implementados o validados los marcadores. El plan de pruebas debe ampliarse si el owner lo decide. |

## Matriz de diferencias y seguimiento documental

Esta tabla registra cómo se relacionan el código, los assets, los requisitos, las pruebas y la tesis. Toda persona que haga trabajo posterior debe actualizar su estado y agregar cualquier diferencia nueva, incluso si el resultado es que no hace falta modificar el documento.

| ID | Fuente y fecha/versión | Lo que dice la fuente | Código, assets o decisión del owner | Diferencia / estado | Acción y responsable |
|---|---|---|---|---|---|
| DOC-1 | Código app `main`, commit `1c027f0`; `app.js` y `Settings.kt` | La escena carga un `avatar.glb`; el selector actual guarda Julián/Samuel/Ximena, pero no cambia el modelo. | El owner decidió Juan y Pauline, dos GLB completos, con Juan predeterminado. | **Diferencia confirmada.** | Implementación futura: conectar el selector con el modelo. Actualizar `openspec/specs/ui/spec.md` y `openspec/specs/avatar/spec.md`; responsable: quien implemente en este repo. |
| DOC-2 | Overleaf SDD, `main.tex`, versión 212; consultada 2026-10-24 | Describe 50 archivos GLB en la app y deja pendiente documentar avatares, señas y archivos faciales. | Dos archivos recibidos; cada uno contiene 50 señas y 300 clips. El owner eligió empaquetarlos como un GLB completo por avatar. | **Divergencia; dirección resuelta por el owner.** | Actualizar la descripción de assets del SDD para reflejar dos GLB completos. Solo el owner edita Overleaf. |
| DOC-3 | `../sign-ai-docs/documentos/requerimientos/SignAI_Requerimientos_Consolidados_v0.16.md`, 2026-10-21, RF-AVT-03 | El alcance actual del requisito es mover las cejas según el tipo de oración. | El owner decidió usar expresiones de todo el rostro disponibles en los GLB. | **Divergencia; dirección resuelta por el owner.** | Proponer al owner ampliar RF-AVT-03 y alinear la documentación consolidada antes de implementar. El agente no edita el documento de requisitos. |
| DOC-4 | Overleaf SRS, `main.tex`, versión 524; consultada 2026-10-24 | El resumen pide marcadores faciales según el tipo de frase y el ajuste Personaje selecciona el personaje. No fija clips ni una tabla de expresiones. | El owner eligió Juan/Pauline y rostro completo; falta el mapeo tipo→variante. | **Compatible en el objetivo general; detalle abierto.** | Mantener la decisión de mapeo pendiente y reflejarla en la tabla aprobada de PR-AVT-05. Overleaf sigue siendo de solo lectura para el agente. |
| DOC-5 | `../sign-ai-docs/plan-pruebas/01-diseno/avatar/pruebas.md`, PR-AVT-05 | Hace falta una tabla de expresiones esperadas por los cinco tipos gramaticales; la tabla se escribe antes de la ronda. | Aún no se decidió qué variante representa cada tipo. | **Pendiente.** | El owner consultará la correspondencia. Luego actualizar PR-AVT-05, sin cambiarla por inferencia técnica. |
| DOC-6 | Backend, commit `78006a5`; `PhraseType`, `GlossAnalyzer`, contrato WS | Entrega un tipo gramatical entre cinco; no clasifica emociones. `secondary_signals` es de colisiones gramaticales y no viaja en `segment`. | Los clips incluyen sufijos de alegría, tristeza y sorpresa, además de pregunta y negación. | **Sin correspondencia emocional disponible.** | Opción A/C: mapear solo tipos gramaticales con tabla aprobada. Opción B: diseñar una clasificación emocional y nuevo campo WS, coordinando backend y app. Decisión pendiente del owner. |
| DOC-7 | OpenSpec local, `specs/ui/spec.md` y `specs/avatar/spec.md` | Describe el selector como UI-only y una escena con un único GLB sin animación de expresiones. | El owner decidió selector funcional con dos avatares y rostro completo. | **Desactualizado frente a la decisión del owner.** | Actualizar ambos specs al implementar, después de alinear los documentos de tesis. Responsable: quien implemente en este repo. |
| DOC-8 | Overleaf SRS, versión 524; `../sign-ai-docs` PR-AVT-06 | El tamaño del APK con todos los GLB está pendiente de medir; PR-AVT-06 pide medir los assets y el instalador. | Los dos GLB recibidos suman 73.144.928 bytes en crudo. | **Medición pendiente; el tamaño crudo no es el del APK.** | Medir instalador, memoria y carga en dispositivo durante verificación; registrar build, modelo y dispositivo. Responsable: quien implemente/pruebe. |

### Registro de cambios del plan

Agregar una fila por cada trabajo posterior relacionado con esta iniciativa. No borrar filas; corregirlas solo si se demuestra que el dato original era erróneo y explicar el motivo.

| Fecha | Trabajo realizado | Repo/archivos y fuentes | Diferencias/decisiones actualizadas | Pendiente |
|---|---|---|---|---|
| 2026-10-24 | Investigación inicial de GLB, backend, selector y documentos; `git pull --ff-only` de `../sign-ai-docs` (ya estaba al día). | Ver “Fuentes técnicas y procedencia” al final. | Se registraron DOC-1–DOC-8; owner decidió dos GLB completos, rostro completo y cambio solo en reposo. | Correspondencia tipo gramatical→variante, por consultar con validadores LSC. |

### Qué significa «el backend decide cuándo» en el estado actual

El backend calcula una categoría gramatical con `GlossAnalyzer`; no se la pregunta al LLM. Los valores son `AFFIRMATIVE`, `YES_NO_QUESTION`, `CONTENT_QUESTION`, `NEGATIVE` e `IMPERATIVE`. El analizador usa señales gramaticales y una prioridad cuando detecta más de una. El frame enviado a Android incluye el tipo principal y las glosas.

`secondary_signals` no significa emoción: son otras categorías gramaticales detectadas cuando hay una colisión. Se guardan en la caché del backend, pero no se incluyen en el `segment` que recibe Android. No existe hoy un campo de emoción o de expresión facial, ni un clasificador de alegría, tristeza o sorpresa.

Por lo tanto, el backend puede decir «esta oración es una pregunta» o «es negativa»; la app necesita una tabla acordada que traduzca ese tipo a uno de los clips del avatar. Usar los sufijos `_alegria`, `_tristeza` o `_sorpresa` por su significado exigiría una señal de emoción que hoy no existe. Agregar esa clasificación sería una ampliación del backend y del contrato entre repositorios, no un cambio exclusivo del selector.

## Decisiones ya indicadas por el owner

- El avatar solo se puede cambiar en **reposo absoluto**. Mientras haya captura, una frase lista o enviada, traducción, cola o representación en curso, el selector no debe permitir el cambio. La recomendación del plan es habilitarlo únicamente en `Inactivo`, con micrófono y modo en vivo apagados y sin turno, cola ni repetición pendientes.
- Juan es el avatar actual y Pauline la nueva. El valor inicial para instalaciones nuevas será Juan. Las preferencias guardadas con las opciones antiguas se normalizarán a Juan, ya que esas opciones nunca cambiaron el modelo real.
- Se usarán **dos GLB completos**, uno por avatar, y el alcance de los marcadores será el **rostro completo**, no solo las cejas.
- Sigue abierta la correspondencia entre el tipo de oración y el clip que se debe reproducir. El owner consultará esa decisión; el plan conserva las alternativas en la sección siguiente.

## Plan de implementación propuesto

### 0. Alinear la documentación y cerrar la tabla de expresiones

1. Antes de implementar, proponer la actualización de RF-AVT-03 en `../sign-ai-docs`: el alcance decidido es rostro completo, mientras v0.16 solo pide cejas. El owner gestiona los documentos de requisitos.
2. Proponer la actualización del SDD de Overleaf: el empaquetado decidido es dos GLB completos (Juan y Pauline), con 50 señas × seis variantes cada uno, no 50 GLB por seña. El owner debe aplicar los cambios en Overleaf.
3. La tabla rostro completo ↔ tipo de oración sigue abierta. PR-AVT-05 ya exige escribir lo esperado para cada tipo antes de probar; el owner consultará esa correspondencia con quien valide LSC.

#### Opciones que quedan abiertas para elegir cuándo usar los clips

| Opción | Qué haría | Ventaja | Coste o límite |
|---|---|---|---|
| **A. Tabla gramatical revisada** | Mantener los cinco tipos que ya entrega el backend y acordar para cada tipo qué variante facial usar. `_pregunta` y `_negacion` son candidatos por nombre, no decisiones; afirmativa e imperativa tampoco tienen un sufijo equivalente. | No cambia el backend ni el WebSocket. Está dentro del flujo gramatical que la tesis ya define. | El equipo LSC debe validar y documentar la expresión de cada tipo. No permite seleccionar alegría, tristeza o sorpresa si el tipo no las informa. |
| **B. Agregar clasificación emocional** | Definir cómo reconocer alegría, tristeza y sorpresa, y enviar esa señal a la app con un campo nuevo del segmento WebSocket. | Permite elegir explícitamente los clips con esos sufijos. | Amplía el alcance y requiere diseño y validación lingüística, cambios coordinados en backend y app, pruebas del contrato y actualización de los documentos. El backend actual no ofrece esa clasificación. |
| **C. Habilitar primero solo las categorías validadas** | Activar únicamente las variantes para las que el equipo haya aprobado una correspondencia; usar el clip base para las demás hasta que exista una definición. | Evita inventar expresiones y permite entregar por etapas. | La cara no reflejará todos los tipos desde el primer corte. |

### 1. Preparar y verificar los assets

- Conservar los dos GLB originales como entradas inmutables y registrar hash, tamaño, cantidad de clips, articulaciones y canales faciales en un inventario reproducible.
- Confirmar que las 50 señas y las seis variantes estén presentes en ambos archivos; validar que los nombres de nodo de cada clip puedan resolverse en su modelo.
- Definir el mecanismo de empaquetado para **dos modelos completos**. `tools/pack_signall.py` produce hoy un único avatar y no se debe asumir que puede procesar estos dos archivos sin cambios.
- No editar manualmente los binarios ni regenerarlos desde la fuente del avatar actual: los archivos nuevos son una entrada distinta y deben conservarse como tal.

### 2. Hacer funcional el selector existente

- Reemplazar las opciones actuales por Juan y Pauline, conservando el desplegable existente y sus accesibilidades.
- Persistir la selección y cargar ese avatar al iniciar. Normalizar los valores antiguos al valor Juan.
- Enviar el identificador de avatar a la escena mediante una vía aditiva del puente Kotlin–JavaScript; no cambiar nombres o comandos existentes.
- Deshabilitar el selector fuera de reposo absoluto. Mantener el modelo anterior hasta confirmar el criterio de reposo y evitar que se cambie de avatar durante una operación activa.

### 3. Cargar el modelo seleccionado en la escena

- Sustituir la URL única por un catálogo local de rutas seguras para Juan y Pauline. Los archivos permanecen dentro de la app; no se descargan del backend.
- Cargar un único modelo en memoria gráfica a la vez. Al cambiar en reposo, detener y limpiar el modelo anterior, construir el mezclador y el índice del nuevo, reaplicar velocidad, tema, cámara y ajustes, y señalar el fin de carga a Compose.
- Conservar el contrato de las rutas `signs/<id>.glb` como identificadores de seña. Modificar el resolutor para asociar cada identificador a los clips `0000`–`0049` del avatar seleccionado y a la variante que autorice la tabla de marcadores.
- Mostrar un estado de carga durante el cambio y no habilitar el envío de una frase hasta que el nuevo avatar esté listo. Mantener `avatarReady` como señal de escena lista al inicio; definir una señal aditiva para las cargas posteriores.

### 4. Activar los marcadores que se aprueben

- En la primera versión, usar solo el tipo gramatical ya enviado, salvo que el owner autorice ampliar el contrato.
- Aplicar el mapeo aprobado por oración a todos los clips de sus glosas; si falta una variante para una seña, usar el clip base y registrar el caso, no inventar otra expresión.
- Confirmar en pantalla qué cambia por variante y que las manos y el cuerpo mantengan el gesto previsto. La inspección de los GLB encontró los tracks articulares iguales entre base y variantes, pero la legibilidad lingüística requiere validación con personas que conocen LSC.
- No usar `alegria`, `tristeza` o `sorpresa` por inferencia del texto mientras no exista una clasificación emocional validada.

### 5. Medir y verificar antes de cerrar

- Antes de la función, agregar instrumentación local para tiempo de carga por avatar, tiempo de cambio, primera seña y resultado de resolución de clips. Los valores se reportarán con su modelo, dispositivo y build; no se asumirán umbrales que los documentos no fijan.
- Probar las 50 señas en Juan y Pauline; probar los seis nombres de clip donde el mapeo los permita; revisar errores de pistas no resueltas y limpieza al alternar varias veces.
- Ejecutar PR-AVT-05 con la tabla aprobada, dos frases por tipo según el plan actual, grabación de pantalla y resultado por tipo. Validar comprensión/legibilidad con el bloque de pruebas con personas, no solo con una inspección del GLB.
- Medir tamaño final del APK y carga en un dispositivo real. Comparar con el modelo actual de 12.978.476 bytes; los dos nuevos GLB suman 73.144.928 bytes en crudo. No concluir que el cambio es aceptable hasta observar tamaño del paquete, memoria y tiempos reales.
- Verificar que la app compile, se instale y que el selector solo se habilite en el estado acordado.

### 6. Actualizar la documentación después de resolver las divergencias

- Actualizar los specs locales `openspec/specs/avatar/spec.md` y `openspec/specs/ui/spec.md`, que hoy describen un avatar único y un selector UI-only con nombres anteriores.
- Proponer al owner las correcciones correspondientes en `../sign-ai-docs`: RF-AVT-03, la descripción de los GLB, la tabla de marcadores y PR-AVT-05. El owner edita los requisitos y diagramas.
- El Overleaf es de solo lectura para esta sesión. Si el SRS o el SDD necesitan ajustes, se informa al owner; no se escriben desde el agente.
- Si se incorpora una señal emocional al backend, revisar y actualizar el contrato en ambos repositorios antes de implementar el cambio.

## Situación después de las respuestas del owner

- Resueltos: dos GLB completos; rostro completo; selector bloqueado hasta el reposo absoluto; Juan como predeterminado.
- Abierta: cuál de las opciones A, B o C se usará para relacionar tipo de oración y variante facial. El owner consultará la decisión. No implementar ese mapeo hasta tener respuesta.
- `../sign-ai-docs` se actualizó mediante `git pull --ff-only` el 2026-10-24. `main` ya estaba al día, sin cambios nuevos: commit `dc8d49c79285489815164adaf8a1fa7ef6e83b24`.

## Fuentes técnicas y procedencia

- App en `main`, commit `1c027f002af29c5bc67a2a67671546bc704d66d6`: `Settings.kt`, `SettingsRepository.kt`, `CharacterPicker.kt`, `MainScreenState.kt`, `SignAiWebViewController.kt`, `SignAiCommands.kt`, `assets/web/app.js`.
- Backend, commit `78006a523875adaa11c72831e4997310d47191ab`: `app/domain/models.py`, `app/nlp/gloss_analyzer.py`, `app/controller/controller.py`, `docs/android-integration.md` y `docs/sdd.md`.
- Documentación consolidada, `main` actualizado por pull sin cambios, commit `dc8d49c79285489815164adaf8a1fa7ef6e83b24`: `documentos/requerimientos/SignAI_Requerimientos_Consolidados_v0.16.md`, `modificaciones/requerimientos.md` y `plan-pruebas/01-diseno/avatar/`.
- Tesis consultada el 2026-10-24: Overleaf SRS (`main.tex`, versión 524), SDD (`main.tex`, versión 212) y Memoria (`sections/descripcionGeneral.tex`, versión 98). Las versiones consultadas no indican por sí mismas la fecha en que el owner editó cada archivo.
- Reglas de trabajo aplicadas: `AGENTS.md`, y los skills `tres-fuentes-de-verdad`, `contrato-websocket`, `overleaf-lectura`, `compose-best-practices` y `sesion-openspec`.
