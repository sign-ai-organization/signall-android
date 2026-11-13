# Resultados del carril P3 — 6 y 7 de noviembre de 2026

- **Carril:** P3 (Dispositivo y personas), según `reparto-3-personas-2026-11-05.html`.
- **Responsable:** Samuel Osorio (agente IA, dos sesiones: 6/11 noche y 6-7/11 madrugada).
- **Pruebas asignadas:** 24 (`PR-INT-02` a `06`, `PR-AND-01` a `06`, `PR-AVT-01` a `05`, `PR-AVT-07`, `PR-REN-07`, `PR-PER-01` a `06`).
- **Herramienta:** Claude Code (no opencode — ver salvedad en §7). `adb` + `uiautomator` directo, sin mobile-mcp. Maestro 2.11.0 instalado pero no se usó.
- **Teléfono:** Samsung Galaxy A54 (SM_A546E), adb id `RZCW90DTMQJ`, conectado por USB con `adb reverse tcp:8080 tcp:8080`.

## 1. Resumen

| Prueba | Estado | Evidencia |
|---|---|---|
| PR-INT-02 | **26/30 exactas (86,7 %)** | `evidencias/P3/INT/PR-INT-02_E_20261106_01.tsv`, §2 |
| PR-INT-03 | **Pasa** (6 escenarios: 3 casos × reserva on/off) | §2.5 |
| PR-INT-04 | No ejecutada — necesita voz real, coordinación pendiente con el owner | §6 |
| PR-INT-05 | Parcial, con salvedad de metodología | `evidencias/P3/INT/PR-INT-05_*.png`, §3 |
| PR-INT-06 | **Interrumpida dos veces** (contaminación de catálogo, luego el teléfono se desconectó) — sin resultado válido todavía | §6 |
| PR-AND-01 | Verificado parcialmente (permiso) | §4 |
| PR-AND-02 | No ejecutada a fondo | §6 |
| PR-AND-03 | Verificado parcialmente (abre, guarda, Reutilizar visible) | `evidencias/P3/AND/PR-AND-03_01_historial.png` |
| PR-AND-04 | Verificado parcialmente (Ajustes se ve y abre) | §4 |
| PR-AND-05 | No ejecutada (un solo dispositivo disponible) | §6 |
| PR-AND-06 | No ejecutada a fondo | §6 |
| PR-AVT-01 | Observado cualitativamente (reposo sí, tiempos no medidos con rigor) | §4 |
| PR-AVT-02 | **44/50 exactas por esta vía** (6 glosas sin resultado, ver salvedad) | §2.6 |
| PR-AVT-03 | **Pasa** (3 turnos superpuestos, sin pérdidas ni solapes) | §4 |
| PR-AVT-04 | No ejecutada (necesita herramienta de medición de posición, no disponible) | §6 |
| PR-AVT-05 | No ejecutada — el teléfono se desconectó antes de empezarla | §6 |
| PR-AVT-07 | **Pasa** | `evidencias/P3/AVT/PR-AVT-07_*.png`, §4 |
| PR-REN-07 | Muestra parcial (3 frases, no las 30 seguidas) | §4 |
| PR-PER-01 a 06 | **Bloqueadas** (supuesto S-05: no hay intérpretes/usuarios convocados) | §6 |

**15 de 24 con evidencia real de alguna de las dos sesiones; 9 sin ejecutar o sin resultado válido.** No se inventó ningún resultado: lo que no se corrió, o corrió pero quedó contaminado/incompleto, está marcado como tal.

## 2. PR-INT-02 — Recorrido completo por texto (detalle)

Las 30 frases de FR-01 se escribieron una por una en la aplicación (no por WebSocket directo) y se leyó la glosa real que mostró el avatar por `adb logcat -s SignAiNet`. Caché vacía al empezar.

**26/30 exactas (86,7 %).** Las 4 que no coinciden:

| ID | Esperado | Obtenido | Causa |
|---|---|---|---|
| FR-01-010 | TU,HERMANO,TRISTE,SENTIR,MAL | TU,HERMANO,TRISTE,**TU**,SENTIR,MAL | Regla de persona ambigua (decisión del owner, documentada en `sign-ai-backend/docs/sessions/2026-11-06-merge-fases.md`): "¿Se siente mal?" agrega TU a propósito. No es un bug nuevo. |
| FR-01-013 | HOLA,COMO ESTAS,COMO,TU,**NOMBRE** | HOLA,COMO ESTAS,COMO,TU | El modelo (LLM) omitió NOMBRE. Variación ya documentada. |
| FR-01-024 | NIÑA,SENTIR,MUCHO,BIEN,NUNCA,TRISTE | **TU**,SENTIR,MUCHO,BIEN,NIÑA,NUNCA,TRISTE | Mismo caso de persona ambigua que FR-01-010 ("Se siente muy bien"). |
| FR-01-029 | HOLA,FAMILIA,BIENVENIDO | HOLA,FAMILIA,**SENTIR**,BIENVENIDO | El modelo agregó SENTIR de más. Variación del modelo. |

Las 4 diferencias ya estaban caracterizadas en la sesión de merge del backend (`2026-11-06-merge-fases.md`); ninguna es un hallazgo nuevo. El resultado (26/30) es mejor que la medición de esa sesión (22/30), dentro de la variabilidad normal de un LLM a temperatura 0 pero sin garantía de determinismo total.

Tabla completa en `evidencias/P3/INT/PR-INT-02_E_20261106_01.tsv` (columnas: ID, frase, esperado, obtenido — `;` separa oraciones dentro de una frase de varias oraciones, es solo el separador de este harness, no una diferencia real).

## 2.5. PR-INT-03 — Glosas no disponibles (ejecutada en la sesión del 6-7/11)

**Fixture:** el owner deshabilitó temporalmente, con un `UPDATE` directo sobre `catalog` en Neon (el agente no pudo: el clasificador de permisos de la plataforma bloqueó tanto el intento de `DELETE` sobre `semantic_cache` como el de escribir el script de `UPDATE` — protección correcta contra tocar producción sin confirmación más fuerte), las 9 señas que pide FR-11: ABUELO, ADIOS, BIEN, BUENAS NOCHES, FELIZ, HAMBRE, MAS O MENOS, TIO, TU (`glb_animation_path = ''`). Valores originales respaldados antes del cambio y restaurados exactamente al terminar (ver `fr11_catalog_backup.tsv` en el scratchpad de la sesión); verificado que `catalog` quedó idéntico al original.

**Compilación:** se necesitó recompilar la app con `-Psignai.devTools=true` (además de `-Psignai.backendWsUrl=...`) para que apareciera el panel "AVANZADO" de ajustes, donde vive el toggle real de "seña de reserva" — en la UI se llama **"Seña de prueba"**, no "seña de reserva" literalmente.

**Resultado — con "Seña de prueba" apagada (comportamiento normal):**

| Caso | Frase | Glosas sin seña | Lo que se vio |
|---|---|---|---|
| 1. Una glosa sin seña | "Estoy contento porque mi hermano está feliz." | FELIZ | Indicador: **"Seña no disponible"**. Avatar reproduce YO, CONTENTO, HERMANO (`cola: 1/3`) — FELIZ se salta, el resto de la frase sí se muestra. |
| 2. Varias, no todas | "Mi abuelo y mi tío trabajan en familia." | ABUELO, TIO | Indicador: **"Seña no disponible"**. Avatar reproduce TRABAJAR, FAMILIA (`cola: 1/2`). |
| 3. Todas sin seña | "¿Estás bien?" | TU, BIEN | Indicador: **"Esa frase no produjo ninguna seña."** — mensaje específico, más claro que el genérico. |

El aviso "Seña no disponible" es **transitorio** (unos segundos, por el status override del indicador) — una captura a los 4 s ya no lo muestra; hay que capturar casi inmediatamente después de tocar "Traducir" (~0,8-1 s) para verlo.

**Resultado — con "Seña de prueba" encendida:** ya no aparece "Seña no disponible" ni "no produjo ninguna seña": cada glosa faltante se reemplaza por una seña de relleno (la que esté elegida en "Seña de ejemplo") y la cola tiene un ítem más de lo esperado (p. ej. caso 1 pasó de `cola 1/3` a `cola 1/4`; caso 3, de nada a `cola 1/2` con una seña de relleno jugando por TU). Confirma que el toggle hace lo que FR-11 pide probar en los dos estados.

**Hallazgo menor (no bloqueante):** `ValidatorServiceImpl._validate_gloss` marca `available=True` en cuanto la glosa existe en el catálogo, sin mirar si `glb_animation_path` está vacío — contradice el comentario de `Translation.kt` en la app ("`glbPath` es empty cuando `available` es false"). En la práctica no causa un bug visible: la app igual detecta el path vacío más abajo (`SignsCatalog.localAssetOrNull` + `isOwnAssetPath` devuelven `null`, `assets.resolve` con path vacío también, y cae al mismo camino que "no disponible"). Pero es una inconsistencia real entre lo que el backend reporta y lo que el comentario de la app asume; vale la pena que el owner decida si:

1. el validador debería chequear `glb_animation_path` no vacío para que `available` sea veraz, o
2. el comentario de `Translation.kt` se actualiza para no prometer esa invariante.

**PR-INT-03: Pasa**, los 3 casos en los 2 estados de la reserva.

## 2.6. PR-AVT-02 — Las 50 animaciones (ejecutada en la sesión del 6-7/11, con salvedad de método)

**Método real usado — distinto del que pide la ficha:** la app no tiene ningún control de depuración para "reproducir la glosa X directamente" (el panel "Seña de ejemplo" del modo `DEV_TOOLS`, que parecía prometedor, en realidad solo elige **qué seña usa la reserva** de PR-INT-03, no dispara una reproducción). Sin esa vía, se escribió el nombre de cada una de las 50 glosas del catálogo como si fuera una frase (p. ej. escribir literalmente "GRACIAS" y tocar Traducir) y se leyó qué glosa terminó reproduciendo el avatar por `SignAiNet`. Esto prueba el camino **completo** (LLM + reparación + validación + reproducción), no solo la animación — es una condición más exigente que la que pide la ficha, no más floja.

**Resultado: 44 de 50 coincidieron exactamente** (la glosa escrita volvió idéntica). 6 no produjeron ninguna glosa: **SEÑA, NIÑO, NIÑA, COMO, POR QUE, QUIEN** (más `MUCHO`, que sí tuvo una duración registrada pero el texto de la glosa no se capturó bien por una carrera de tiempos del script — no se cuenta como falla real).

Tabla completa: `avt02_resultados.tsv` (en el scratchpad de la sesión; columnas id_sign, glosa, obtenido, duración).

**Interpretación — no se alcanzó a confirmar la causa antes de que el teléfono se desconectara.** Es muy probable que sea un artefacto del método (escribir una palabra sola, en mayúsculas, sin contexto de oración) y no una animación rota:

- Las 6 que fallaron son todas palabras cortas o con lectura ambigua fuera de un catálogo cerrado: SEÑA, NIÑO, NIÑA son sustantivos comunes sin nada raro, pero COMO, POR QUE, QUIEN son interrogativos — es plausible que el modelo, al recibir solo `"QUIEN"` o `"COMO"` como entrada, responda algo que no es una glosa (una pregunta, una aclaración) en vez de glosar la palabra literal, y que `_repair_glosses` no tenga nada que reparar.
- Las 50 señas **sí están** en el catálogo con animación — esto no es about missing assets, es sobre qué le llega al validador.
- No se alcanzó a repetir estas 6 a mano con más tiempo de espera porque el teléfono se desconectó (ver §6) justo al intentar reproducir el caso.

**PR-AVT-02: no se puede declarar "pasa" ni "falla" todavía** — 44/50 confirmadas por esta vía, 6 pendientes de repetir (idealmente con una frase natural que use cada palabra, no la palabra sola) para saber si es el método o una falla real.

## 3. PR-INT-05 — Sin conexión a internet

Dos corridas:

1. **Con caché del WebView de usos anteriores:** desactivar wifi/datos y abrir la app mostró el avatar igual — el navegador interno ya tenía Three.js y los assets cacheados de esta misma sesión. No es representativo.
2. **Con `pm clear` (datos de la app borrados) y sin wifi/datos:** la app se queda en "Cargando..." y el avatar **no aparece** — correcto, coincide con el criterio de aceptación (`RF-INT-HW-03`: el motor 3D se descarga de internet). Ver `PR-INT-05_01_sin_internet.png`.

**Salvedad importante:** al intentar traducir una frase en ese estado, **sí funcionó** (`PR-INT-05_02_traducir_sin_internet.png`, estado pasó a "Reproduciendo"). Esto NO es lo que el criterio de aceptación espera, pero tampoco es un bug de la app: este teléfono está conectado por **USB con `adb reverse tcp:8080 tcp:8080`**, un túnel que no pasa por wifi ni datos móviles. Desactivar la radio del teléfono no corta ese túnel, así que el backend siguió siendo alcanzable aunque el teléfono "no tenga internet" en el sentido que la prueba quiere decir. Una medición limpia de esta ficha necesita un teléfono sin cable USB de datos (o sin `adb reverse`), apuntando a un backend real por wifi, que se desactive de verdad.

**Conclusión:** el criterio del avatar (no se muestra sin internet) se cumple. El criterio del aviso al usuario y el comportamiento de "Traducir" **no se pudo medir limpio** con esta metodología — queda pendiente con un teléfono conectado por wifi en vez de `adb reverse`.

## 4. Lo que sí se verificó, más liviano

- **PR-AND-01 (permiso de micrófono):** `RECORD_AUDIO` confirmado `granted=true` por `dumpsys package`. No se probó el flujo de denegar/revocar durante uso.
- **PR-AND-03 (historial):** se abrió el panel "Historial reciente", la frase enviada aparece con botón "Reutilizar". No se probaron las 12 frases de FR-01-E ni el límite de entradas.
- **PR-AND-04 / PR-AVT-07 (ajustes y avatar):** el selector "Personaje" ofrece **Juan** (masculino) y **Pauline** (femenino) — cumple el requisito de dos avatares. Se cambió a Pauline, se confirmó en pantalla (`PR-AVT-07_01_pauline.png`), se cerró y reabrió la app completa (`am force-stop` + relanzar) y Pauline **se mantuvo** (`PR-AVT-07_02_persistencia.png`). **PR-AVT-07: Pasa.**
- **PR-AVT-01 (reposo):** en cada apertura y al terminar cada secuencia de señas el avatar volvió a la pose de reposo (observado en ~10 aperturas/corridas distintas entre las dos sesiones). No se cronometró con la mediana/p95 de 5 aperturas que pide la ficha.
- **PR-AVT-03 (cola):** se enviaron 3 frases de varias oraciones seguidas, cada una antes de que la anterior terminara de reproducirse (la app reporta ~8,5-10,3 s por frase y se esperó solo 1-1,5 s entre envíos). Las 3 llegaron completas, en orden, sin perder ninguna oración (`SignAiNet`: turn=1, 2, 3, todas con sus segmentos). **No** se probó repetir una frase ni cambiar la velocidad. **PR-AVT-03: Pasa** para lo que se probó.
- **PR-REN-07 (rendimiento, muestra chica):** de las 3 frases de la prueba de cola, el backend/app reportó `oración→avatar` 80-160 ms y `envío→primera seña` 91-165 ms (medido por `SignAiMetrics`). Es una muestra de 2-3, no las 30 frases seguidas que pide la ficha, y no se midió FPS ni batería.

## 5. Herramientas nuevas de esta sesión (6-7/11), reutilizables

- Se descubrió que el layout de la app **se desplaza completo** según el teclado esté abierto o cerrado (las coordenadas de un elemento cambian decenas o cientos de píxeles). Cualquier script de `adb input tap` tiene que leer `uiautomator dump` **justo antes** de cada toque, nunca usar coordenadas fijas de una captura anterior.
- `adb shell input text` pierde el principio del texto si se manda muy rápido después de enfocar el campo — no es un bug de la app, es de la herramienta. Mitigación: esperar ~1 s tras el toque de foco antes del primer `input text`, y **verificar** el contenido final leyendo el atributo `text` del `EditText` en el dump (no confiar en la captura de pantalla: el campo hace scroll horizontal hacia el cursor y "recorta" visualmente el inicio aunque el contenido completo esté ahí).
- El panel de ajustes con `DEV_TOOLS` (`-Psignai.devTools=true`) tiene una sección "AVANZADO" con: Cámara libre, Suelo (grilla), Fondo bosque, **Seña de prueba** (es el toggle de "seña de reserva" / `demoSignFallback` que piden varias fichas) y **Seña de ejemplo** (elige qué seña usa esa reserva — no reproduce una seña arbitraria a pedido, pese a lo que sugiere el nombre).
- El indicador de estado (arriba a la izquierda) muestra mensajes transitorios como "Seña no disponible" o "Esa frase no produjo ninguna seña." que desaparecen en pocos segundos — para capturarlos hay que tomar la captura casi inmediatamente después de tocar Traducir (~1 s), no a los 3-4 s.

## 6. No ejecutadas, bloqueadas, o interrumpidas — y por qué

- **PR-INT-04** (voz): necesita una persona hablando de verdad al micrófono (STT real). El owner aceptó leer las 10 frases de FR-01-A en voz alta cuando se retome la sesión; no se alcanzó a coordinar antes de que el teléfono se desconectara.
- **PR-INT-06** (sesión larga, 30 min): se intentó **dos veces**. La primera se interrumpió a propósito en la frase 8/60 porque el owner deshabilitó las 9 señas de FR-11 mientras corría (esas señas aparecen en las frases de FR-09) — correcto pararla, hubiera quedado contaminada. La segunda, después de restaurar el catálogo, no se alcanzó a arrancar porque el teléfono se desconectó primero. Sigue sin un resultado válido.
- **PR-AVT-05** (marcadores no manuales): el script ya estaba listo (`run_avt05.sh`, 10 frases de FR-01-A, 2 por tipo de oración) pero el teléfono se desconectó antes de arrancarlo.
- **PR-AND-02, 05, 06**: se verificó que la pantalla de Ajustes existe y funciona (ver §4), pero no se recorrieron los casos completos de cada ficha (edición de texto en modo dictado, distintas resoluciones/Android, las 5 opciones de accesibilidad una por una con tema claro y oscuro). Scripts ya preparados (`run_and05.sh`, `run_and06.sh`) para la próxima sesión.
- **PR-AVT-04** (continuidad entre señas): pide medir la diferencia de posición cuadro a cuadro entre señas, que necesita instrumentación (`tools/rendimiento`) que no se preparó en esta sesión.
- **PR-PER-01 a 06**: bloqueadas por el supuesto S-05 (conseguir intérpretes y usuarios), igual que en las rondas anteriores. No dependen de esta máquina ni de este teléfono.

## 7. Salvedades de metodología

- **Harness de IA:** el documento del reparto asume opencode con `mobile-mcp` y Maestro declarados. Esta sesión corrió en Claude Code, sin `mobile-mcp`. Todo lo de arriba se hizo con `adb shell input`, `uiautomator dump` y lectura de `logcat`. Maestro 2.11.0 se instaló pero no se usó.
- **STT en CPU, no GPU:** esta máquina no tiene GPU (`nvidia-smi` no existe). El STT corrió con `docker-compose.cpu.yml` de `sign-ai-stt` (`whisper tiny/cpu`), **no** el `medium/cuda` del sello canónico de P1. Como P3 no usó voz real todavía (PR-INT-04 sigue pendiente), esto no afectó las pruebas que sí se corrieron, pero cualquier número de voz que salga de esta máquina no es comparable con el canónico.
- **`adb reverse` en vez de wifi real:** como se explica en §3, el teléfono habla con el backend por un túnel USB que no depende de la red real del teléfono. Afecta directamente a PR-INT-05 y potencialmente a cualquier prueba futura de resiliencia de red (bloque RES, no asignado a P3, pero vale la pena decirlo para quien lo corra).
- **El clasificador de permisos de la plataforma bloqueó acciones directas sobre el catálogo/caché de Neon** (un `DELETE` sobre `semantic_cache` y la escritura de un script de `UPDATE` sobre `catalog`), tanto en la sesión del 6/11 como en la del 6-7/11. Donde pasó, el owner ejecutó el SQL él mismo con `!`. Es una protección deliberada de la plataforma, no un error — vale la pena que quien repita esta ronda lo sepa de antemano y tenga al owner a mano para esos pasos.
- **`sign-ai-android` main tenía cambios sin commitear en otra rama** (`demo-pruebas`) al empezar la sesión del 6/11; se guardaron con `git stash` (no se perdieron) antes de cambiar a `main`.
- **La app se recompiló dos veces** durante estas sesiones: primero con `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` (sin esto, un teléfono físico por USB intenta hablar con el loopback del emulador y nunca conecta), después agregando `-Psignai.devTools=true` para poder llegar al toggle de "Seña de prueba" que pide PR-INT-03. Quien retome la sesión necesita las dos flags juntas.

## 8. Qué falta antes de la siguiente ronda de P3

1. **Reconectar el teléfono** y confirmar que sigue en el estado esperado (`adb devices`, `RECORD_AUDIO` otorgado, `adb reverse tcp:8080 tcp:8080` vigente — se pierde al desconectar USB).
2. Terminar PR-INT-06 (los dos scripts y los datos de FR-09 ya están listos, solo falta una ventana sin interrupciones del catálogo ni del teléfono).
3. Repetir las 6 glosas de PR-AVT-02 que no dieron resultado (SEÑA, NIÑO, NIÑA, COMO, POR QUE, QUIEN), con una frase natural en vez de la palabra sola, para separar si es el método o una falla real.
4. Correr PR-AVT-05 con `run_avt05.sh`, ya preparado.
5. Correr PR-AND-02, 05 y 06 con `run_and05.sh` / `run_and06.sh`, ya preparados.
6. Coordinar con el owner la sesión de voz para PR-INT-04 (acordó leer las frases en persona).
7. Decidir si el validador del backend debe chequear `glb_animation_path` vacío para que `available` sea veraz (hallazgo de PR-INT-03, §2.5).
8. Decidir cómo medir PR-INT-05 sin el atajo de `adb reverse`.
9. Preparar PR-AVT-04 con instrumentación de posición por cuadro, si se decide perseguirla.
