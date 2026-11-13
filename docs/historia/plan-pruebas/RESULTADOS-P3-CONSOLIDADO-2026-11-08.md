# Resultados del carril P3 — consolidado (6 al 8 de noviembre de 2026)

- **Carril:** P3 (Dispositivo y personas), según `reparto-3-personas-2026-11-05.html`.
- **Responsable:** Samuel Osorio (agente IA), cuatro sesiones: 6/11 noche, 6-7/11
  madrugada, 7/11 tarde-noche y 7-8/11 noche.
- **Pruebas asignadas:** 24 (`PR-INT-02` a `06`, `PR-AND-01` a `06`, `PR-AVT-01` a `05`,
  `PR-AVT-07`, `PR-REN-07`, `PR-PER-01` a `06`).
- **Herramienta:** Claude Code, `adb` + `uiautomator` directo (sin `mobile-mcp`, sin
  Maestro — ver salvedades al final). Scripts y helpers en
  `/tmp/claude-1000/p3-evidencia/` de la máquina de pruebas.
- **Teléfono:** Samsung Galaxy A54 (SM_A546E), adb id `RZCW90DTMQJ`, USB,
  `adb reverse tcp:8080 tcp:8080`.
- **Documentos fuente de este consolidado:** `RESULTADOS-P3-2026-11-06.md` (sesión del
  6 y 6-7/11) y `RESULTADOS-P3-2026-11-07.md` (sesión del 7 y 7-8/11). Este archivo resume
  los dos sin repetir todo el detalle — para la metodología exacta paso a paso, las dos
  fuentes siguen siendo la referencia completa.

## 1. Resumen de las 24 pruebas

| Prueba | Estado final | Notas |
|---|---|---|
| PR-INT-02 | Se reporta — 26/30 exactas (86,7 %) | Las 4 diferencias son variaciones del LLM ya caracterizadas, no bugs nuevos |
| PR-INT-03 | **Pasa** | 3 casos (una/varias/todas sin seña) × reserva on/off, 6 escenarios |
| PR-INT-04 | **Pasa** — 9/10 exactas (90 %) con voz real | 1 diferencia es variación ya conocida del LLM |
| PR-INT-05 | Parcial | El criterio del avatar se cumple; el aviso al usuario y "Traducir sin internet" no se pudieron medir limpio (ver salvedades) |
| PR-INT-06 | **Pasa** | 60 frases de FR-09, 51 min continuos, sin caídas |
| PR-AND-01 | **Pasa** | Ciclo completo de revocar/denegar/reintentar/aceptar permiso de micrófono |
| PR-AND-02 | **Pasa** | Dictado llena el campo editable sin auto-traducir; confirmado con voz real |
| PR-AND-03 | Verificado parcialmente | Abre, guarda, "Reutilizar" visible; faltan las 12 frases de FR-01-E y el límite de entradas |
| PR-AND-04 | Verificado parcialmente | Ajustes se ve y abre, selector de personaje confirmado |
| PR-AND-05 | **Pasa** | Funciona en 720×1280 y 1080×2400 una vez corregido el punto de toque real |
| PR-AND-06 | **Pasa** | Accesibilidad persiste entre reinicios de la app |
| PR-AVT-01 | **Pasa** | 5 aperturas limpias, p50=1606 ms hasta avatar listo, reposo confirmado |
| PR-AVT-02 | **Pasa** — 50/50 señas confirmadas | 44 por palabra sola + 6 por frase natural (retest) |
| PR-AVT-03 | **Pasa** | 3 frases superpuestas en cola, sin pérdidas ni solapes |
| PR-AVT-04 | **No ejecutada** | Necesita instrumentación de posición cuadro a cuadro, no existe |
| PR-AVT-05 | Pasa en 3/5 tipos; hallazgo en el tipo Orden | Ver §4 |
| PR-AVT-07 | **Pasa** | Selección de avatar (Juan/Pauline) persiste tras reinicio |
| PR-REN-07 | Se reporta | 30 frases de FR-01 seguidas; batería no medible por estar el teléfono cargando por USB |
| PR-PER-01 a 06 | **Bloqueadas** | Supuesto S-05: no hay intérpretes ni usuarios convocados |

**Totales: 14 Pasa + 3 Se reporta/caracterización con datos completos = 17 con resultado
sólido. 2 verificadas solo parcialmente (AND-03, AND-04). 1 parcial con salvedad de método
(INT-05). 7 sin poder cerrarse** (las 6 PR-PER por falta de personas, y PR-AVT-04 por falta
de instrumentación).

## 2. Hallazgos abiertos para que decida el owner

Estos dos no bloquean ningún resultado, pero valen una decisión explícita:

1. **Tipo de oración "Orden" clasificado como `AFFIRMATIVE`.** Las dos frases de Orden de
   `FR-01-A` ("Por favor, come poco." y "Con permiso. Déjame pasar, por favor.") se
   clasificaron y representaron con cara `afirmativo`, no con una variante de orden —
   confirmado **4 veces**: 2 por texto escrito (PR-AVT-05) y 2 por voz real (PR-INT-04).
   `AGENTS.md` dice que existen 4 variantes faciales (`afirmativo/pregunta/negacion/orden`),
   pero el overlay de diagnóstico nunca mostró `orden` en ninguna de las 4 observaciones.
   Dos hipótesis sin confirmar: (a) el LLM de glosado no distingue estas frases como
   imperativas y las lee como declarativas, lo cual es gramaticalmente razonable; o (b) la
   variante "orden" no se dispara nunca y cae siempre en "afirmativo". Falta probar una
   frase imperativa inequívoca (con exclamación clara) para aislar la causa.

2. **El validador marca `available: true` con `glb_animation_path` vacío.**
   `ValidatorServiceImpl._validate_gloss` no chequea que el path de animación no esté vacío
   antes de marcar la glosa como disponible — contradice el comentario de `Translation.kt`
   en la app Android, que asume que `available=false` cuando el path está vacío. No causa un
   bug visible (la app igual detecta el path vacío más abajo y cae al mismo camino de "no
   disponible"), pero es una inconsistencia real entre backend y comentario. El owner debe
   decidir si el validador se corrige o si el comentario de la app se actualiza.

## 3. Bloque INT — Integración

- **PR-INT-02 (recorrido completo por texto):** 26/30 exactas (mejor que una medición previa
  de 22/30 del backend, dentro de la variabilidad esperable del LLM a temperatura 0). Las 4
  diferencias: persona ambigua agregando TU de más (2 casos, decisión de diseño ya
  documentada), omisión de NOMBRE en una frase (variación del LLM), y SENTIR agregado de más
  en otra. Ninguna es un hallazgo nuevo.
- **PR-INT-03 (glosas no disponibles):** con la "Seña de prueba" (reserva) apagada, la app
  muestra "Seña no disponible" (transitorio, ~1 s) o "Esa frase no produjo ninguna seña"
  cuando aplica, y salta la glosa faltante sin romper el resto de la secuencia. Con la
  reserva encendida, cada glosa faltante se reemplaza por una seña de relleno. Los 3 casos
  (una/varias/todas sin seña) funcionan en los dos estados.
- **PR-INT-04 (voz real):** 9/10 exactas leyendo FR-01-A en voz alta. Hallazgos de método:
  el dictado llena el campo de texto editable (no traduce directo), el VAD de la app
  auto-detiene la grabación sola (tocar el botón de nuevo cuando ya se detuvo arranca una
  grabación nueva — ojo para quien repita), y la STT puede confundir palabras parecidas
  ("hambre"→"hombre" en un intento) sin que eso rompa el glosado posterior.
- **PR-INT-05 (sin internet):** con `pm clear` y sin wifi/datos, el avatar correctamente no
  aparece (criterio cumplido). Pero "Traducir" sí funcionó en ese estado porque el teléfono
  habla con el backend por `adb reverse` sobre USB, un túnel que no depende de la radio del
  teléfono — esto invalida medir limpio el aviso al usuario y el bloqueo de "Traducir" con
  esta metodología.
- **PR-INT-06 (sesión larga):** al tercer intento (los dos primeros se interrumpieron por
  contaminación de catálogo y desconexión del teléfono), corrió completa: 60 frases de
  FR-09 en 51 minutos seguidos, `app_pid` constante y `backend_http=200` en las 60 filas —
  sin caídas. No se midió memoria antes/después de forma sistemática.

## 4. Bloque AND — Android / dispositivo

- **PR-AND-01 (permiso de micrófono):** revocar el permiso con la app abierta mata el
  proceso (comportamiento normal de Android). Al reabrir, la app pide el permiso sola; al
  denegarlo muestra un mensaje claro ("Sin permiso de micrófono la app no puede escuchar.
  Activalo en Ajustes.") sin crash; al tocar el micrófono de nuevo vuelve a preguntar en vez
  de fallar en silencio; al aceptar, la grabación retoma sola la acción pendiente.
- **PR-AND-02 (edición en modo dictado):** confirmado con voz real — el texto transcrito
  llega al campo editable sin enviarse solo, se puede revisar y corregir antes de tocar
  "Traducir". También se confirmó sin voz: activar/detener dictado sin errores y edición
  manual del campo (borrar + reescribir) funcionan bien.
- **PR-AND-03 (historial):** se abre el panel, la frase enviada aparece con botón
  "Reutilizar". No se probaron las 12 frases de FR-01-E ni el límite de entradas.
- **PR-AND-04 (ajustes y avatar):** el selector "Personaje" ofrece Juan y Pauline. Ajustes
  abre y se ve bien. No se recorrieron todos los casos de la ficha.
- **PR-AND-05 (resoluciones):** `adb shell wm size` funciona sin root en este dispositivo.
  El primer intento falló al inyectar texto en 720×1280 y 1080×2400 — la causa real era que
  el toque caía en la zona de gesto del drawer lateral de "Conversación" (el problema "ORO"
  ya documentado en `AGENTS.md`), no una falla de la app. Corregido el punto de toque, el
  flujo de traducción completo funciona en las dos resoluciones no nativas, y la resolución
  se restauró bien al final.
- **PR-AND-06 (accesibilidad):** los 4 toggles (negrita, contraste, texto grande, reducir
  animaciones) se activan, **persisten exactamente igual tras cerrar y reabrir la app
  completa**, y se restauraron a su estado original al final.

## 5. Bloque AVT — Avatar

- **PR-AVT-01 (carga y reposo):** 5 aperturas limpias, tiempos leídos directamente del log
  propio de la app (`[SignAI] escena lista (juan) · N ms`): p50 = 1606 ms, p95 = 1661 ms,
  rango de apenas 89 ms entre la más rápida y la más lenta. Reposo confirmado en pantalla.
- **PR-AVT-02 (las 50 animaciones):** 50/50 confirmadas, combinando 44 que funcionaron
  escribiendo la glosa como palabra sola y 6 que solo funcionaron escribiéndolas dentro de
  una frase natural (SEÑA, NIÑO, NIÑA, COMO, POR QUE, QUIEN — las interrogativas no tenían
  suficiente señal sin contexto de oración).
- **PR-AVT-03 (cola de reproducción):** 3 frases de varias oraciones enviadas superpuestas
  (antes de que la anterior terminara) llegaron completas, en orden, sin perder ninguna.
- **PR-AVT-04 (continuidad entre señas):** no ejecutada — necesita instrumentación de
  posición cuadro a cuadro (`tools/rendimiento`) que no se construyó en ninguna sesión.
- **PR-AVT-05 (marcadores no manuales):** 3 de 5 tipos confirmados por el overlay de
  diagnóstico (Afirmación, Pregunta_contenido → `CONTENT_QUESTION`, Negación →
  `NEGATIVE`). El tipo Orden salió `AFFIRMATIVE` en las 2 frases probadas — ver hallazgo en
  §2. Pregunta_si_no se reprodujo sin error pero no se capturó el campo `tipo` en el
  momento exacto.
- **PR-AVT-07 (elección de avatar):** cambiar a Pauline, cerrar/reabrir la app completa, y
  la elección persiste.

## 6. Bloque REN — Rendimiento

- **PR-REN-07 (rendimiento en el móvil):** las 30 frases de FR-01 seguidas (todo el
  conjunto, como pide la ficha). Latencias propias de la app (n=30): representado
  p50=7709 ms / p95=12356 ms / max=14911 ms; oración→avatar p50=53 ms / p95=94 ms;
  envío→primera seña p50=64 ms / p95=128 ms. Fotogramas (`dumpsys gfxinfo`): 114.673 frames,
  17,33 % "janky", p50=24 ms/frame (~41 fps equivalente), p95=48 ms/frame (~21 fps
  equivalente). Memoria: PSS estable entre 646-654 MB antes/después, sin indicio de fuga
  (el número es alto porque ~450 MB es memoria de GPU, esperable para una escena 3D con 200
  clips cargados). **Batería no medible**: el teléfono estaba cargando por USB durante toda
  la prueba (nivel subió de 49 % a 53 %), lo que enmascara cualquier consumo real.

## 7. Bloque PER — Personas

**PR-PER-01 a 06, las 6, bloqueadas** por el supuesto S-05: necesitan intérpretes y usuarios
reales convocados. No dependen de la máquina ni del teléfono — es la única categoría que
requiere acción del owner para destrabarse, convocando a esas personas.

## 8. Salvedades de metodología (vigentes para toda la ronda)

- **Harness de IA:** las sesiones corrieron en Claude Code con `adb` + `uiautomator` directo,
  no con `mobile-mcp` ni Maestro como asume el documento de reparto original.
- **`adb reverse` en vez de wifi real:** el teléfono habla con el backend por un túnel USB
  que no depende de la radio real del teléfono. Afecta a PR-INT-05 (no se puede medir "sin
  internet real" de forma limpia) y a la batería de PR-REN-07 (el teléfono carga mientras
  está conectado, enmascarando el consumo real). La solución de fondo es `adb` inalámbrico o
  repetir con el owner presente para desconectar el cable en el momento preciso.
- **STT en CPU, no GPU:** la máquina de pruebas no tiene GPU; el STT corrió con
  `docker-compose.cpu.yml` (`whisper tiny/cpu`), no el `medium/cuda` del sello canónico de
  P1. No afectó las pruebas corridas (ninguna comparó cifras de voz contra el canónico), pero
  cualquier número de voz de esta máquina no es comparable.
- **El clasificador de permisos de la plataforma bloqueó acciones directas sobre el
  catálogo/caché de Neon** (un `DELETE` sobre `semantic_cache` y la escritura de un script de
  `UPDATE` sobre `catalog`). Donde pasó, el owner ejecutó el SQL él mismo. Protección
  deliberada de la plataforma, no un error.
- **El indicador de estado de la app muestra mensajes transitorios** ("Seña no disponible",
  "Sin permiso de micrófono...") que a veces desaparecen en segundos — hay que capturar la
  pantalla casi inmediatamente después de la acción para verlos.
- **El layout de la app se desplaza según el teclado esté abierto o cerrado**, y el drawer
  lateral de "Conversación" captura toques que caen en su zona de gesto incluso cuando está
  cerrado — cualquier automatización por coordenadas necesita releer el `uiautomator dump`
  justo antes de cada toque y evitar la franja izquierda de la pantalla.

## 9. Qué falta después de esta ronda

1. Decidir los dos hallazgos abiertos de §2 (tipo "Orden" y el validador con
   `glb_animation_path` vacío).
2. Conseguir `adb` inalámbrico (o coordinar con el owner desconectar el cable en el momento
   justo) para medir PR-INT-05 y la batería de PR-REN-07 de forma limpia.
3. Construir la instrumentación de posición cuadro a cuadro para PR-AVT-04.
4. Convocar intérpretes y usuarios para destrabar las 6 PR-PER.
5. Completar PR-AND-03 (12 frases de FR-01-E, límite de entradas) y PR-AND-04 (casos
   completos de la ficha).
