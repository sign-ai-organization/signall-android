# Pull de los cuatro repos y fin de la rama `develop`

**Fecha:** 2026-11-11, madrugada · **Rama:** `main`

## Qué llegó

Dos pedidos del owner, en orden:

1. **Traer los cambios de todos los repos.** Un `fetch --all --prune` en los cuatro y
   traer lo que había.
2. **Borrar la rama `develop`.** «Borralas todas las ramas develop acá en local, todo lo
   trabajaremos en main. Excepto el repositorio de avatar, ese no lo toques.»

## Qué se hizo

### El pull

Solo dos repos tenían commits nuevos:

| Repo | Antes | Después | Qué traía |
|---|---|---|---|
| `sign-ai-android` | `d72ec4e` | `704e0ac` | 12 commits: la ronda P3 del 10/11 (fichas `PR-AND-03`, `PR-AND-04`, `PR-INT-05`, `PR-REN-07`), las repeticiones y umbrales del carril 2, y 129 archivos de evidencia |
| `sign-ai-docs` | `b23692c` | `401b7d1` | 27 commits: umbrales del carril 2 en las fichas y el **documento nuevo `latex/vfp/`** (versión final de la propuesta), con sus borradores en `borradores/vfp/` |
| `sign-ai-backend` | `301e721` | `301e721` | nada (solo se borró la rama remota `fix/glosado-hue-cac-llm-p1`, ya fusionada) |
| `sign-ai-stt` | `4824979` | `4824979` | nada |

**En la app no cambió una línea de código**: los 12 commits son de `docs/plan-pruebas/` y
`openspec/sessions/`, verificado con `git diff --name-only d72ec4e..704e0ac` filtrando esas
dos carpetas → vacío.

`../signall-avatar` no se tocó: `dev` ya estaba en `fc6f372`, igual que `origin/dev`.

### El borrado de `develop`

Las cuatro ramas `develop` locales eran **ancestros de `main`**, sin commits propios
(comprobado con `git merge-base --is-ancestor develop main` antes de cada borrado), así que
se borraron con `git branch -d` y no se perdió nada:

- `sign-ai-android`: `7707da6` (venía 116 commits atrás)
- `sign-ai-backend`: `301e721` (igual a `main`)
- `sign-ai-stt`: `7a571c2` (5 atrás)
- `sign-ai-docs`: `8ff4fd2` (11 atrás)

Las **ramas remotas `origin/develop` no se borraron**: son historia, y borrarlas en el remoto
de los hermanos es cosa del owner. Quedaron así:

| Repo | `origin/main` | `origin/develop` |
|---|---|---|
| `sign-ai-android` | `704e0ac` | `704e0ac` |
| `sign-ai-backend` | `301e721` | `301e721` |
| `sign-ai-stt` | `4824979` | `4824979` |
| `sign-ai-docs` | `401b7d1` | `8ff4fd2` |

`signai-avatar` quedó como estaba: `dev` en `fc6f372`, `main` en `fc6f372`, y su cambio local
sin commitear (`openspec/specs/dataset/spec.md`, el typo `JUCIOSO` → `JUICIOSO`) intacto.

## Qué se decidió

- **El owner decidió que se abandona `develop`.** Todo el trabajo va a `main` en los cuatro
  repos y **no se vuelve a crear la rama**. La documentación se actualizó para que ninguna
  sesión futura la busque: la tabla de repos de `AGENTS.md` y de `docs/SISTEMA.md` ahora
  dice `main` para el backend y el STT, las reglas de PR de los hermanos apuntan a `main`, y
  la sección `## Git` de `AGENTS.md` cuenta qué se borró y por qué.
- **`../signall-avatar` queda excluido**: su rama de trabajo es `dev` y ahí no se toca nada.
- **No se borraron las ramas remotas.** La decisión del owner fue sobre las locales.

## Qué se verificó

- **Los seis documentos compilan** en `../signall-docs` con `make all`, incluido el `vfp`
  nuevo: `srs` 124 páginas, `sdd` 48, `memoria` 72, `plan-pruebas` 143, `administracion`
  43, `vfp` 35. Compilación local con `latexmk` (TeX Live instalado en la máquina).
- **La app compila**: `./gradlew build` → `BUILD SUCCESSFUL` en 1 min 18 s (144 tareas, 60
  ejecutadas), sin errores de lint.
- **No hay device conectado** (`adb devices` vacío), así que no se corrió `installDebug`.
  Queda pendiente por owner.

## Qué quedó pendiente

- **Borrar las ramas `origin/develop` del remoto**, si el owner lo quiere. En los tres
  repos de hermanos y en el de documentos.
- **Instalar la app en un device** para que el owner pueda probar (falta por el `adb devices`
  vacío).
- **Fichas `PR-REN-07` y `PR-AVT-05`**: la sesión `2026-11-10-p3-and03-and04.md` las
  dejó pendientes de actualizar en `../signall-docs` con los valores nuevos de la ronda
  diagnóstica. El pull del 11/11 trae cambios en `latex/plan-pruebas/main.tex`, pero **no
  verifiqué** si cierran esas dos fichas: es un pendiente de otro repo.
- **El `develop` remoto del backend sigue disparando el CI** (`.github/workflows/ci.yml`
  tiene `branches: [main, develop]`). Si la rama se borra, esa línea deja de ser necesaria.

## Seguimiento: preparación para ajustes de animaciones con IA

**Miércoles 2026-11-11, 10:24 (UTC−5), mañana · Rama:** `main`.

- El owner pidió investigar cómo mejorar movimientos existentes, sin contratar
  animadores ni aprender animación. Después limitó expresamente esta sesión a
  información y preparación documental: **no empezar las correcciones**.
- Se inspeccionaron en lectura los GLB de `../signall-avatar`, commit `fc6f372`
  (2026-11-04), y los empaquetados de Android: 200 clips y 102 articulaciones por
  avatar; canales de huesos idénticos entre variantes faciales y entre origen y
  empaquetado. No se hizo evaluación visual completa de las 50 señas.
- El owner instaló Blender; `blender --version` confirmó **5.2.3 LTS**. No se
  ejecutó una prueba de importación/exportación ni se instalaron complementos.
- Se dejó `docs/historia/PREPARACION-AJUSTES-ANIMACIONES.md`: entorno, alcance, fuentes,
  uso propuesto de Blender con scripts Python y pasos pendientes para otra sesión.
  No hace falta MCP, modelos locales ni CUDA adicional para empezar por esa vía.
- **Todo este seguimiento queda local, sin commit ni push**, por pedido del owner.
  No se tocaron assets, scripts, código de la app ni repos hermanos. No se compiló
  ni reinstaló Android: solo se modificó documentación; el APK no cambia.
- Pendiente: una orden posterior del owner para elegir un primer clip y autorizar
   trabajo sobre copias; la compatibilidad del flujo de Blender aún no está probada.

## Seguimiento: propuesta de reposo de Juan sobre copia

**Miércoles 2026-11-11, 10:37 (UTC−5), mañana · Rama:** `main`.

- El owner autorizó corregir únicamente el reposo de **Juan**, sobre copia y con
  comparación antes/después para aprobar antes de integrar. No autorizó cambiar señas.
- Trabajo aislado en `/tmp/opencode/juan-rest/`: `antes.glb`, `despues.glb`,
  `prepare.py`, `render.py`, `verificacion.json`, imágenes y escenas `.blend`.
  Son archivos temporales, no assets de producción.
- Base visual: el `juan.glb` empaquetado de la app (SHA-256
  `5ccf77f3aec4300e33e4d20b6e7edf41ccf577eff04379a87b6cd61dccddbd8c`).
  Original hermano comprobado sin cambios: `../signall-avatar/models/Juan.glb`,
  commit `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, SHA-256
  `cc89d41d925b5f52765fd8fdb322ff09c9d30b6f914fc8ecd3e50fc2a83e3945`.
- La copia cambia solo las rotaciones de `upper_arm.L/R` y `forearm.L/R`:
  baja los brazos y deja separación de los muslos. Conserva la forma de los dedos
  y las muñecas relativa al antebrazo. No cambia torso, cara ni piernas.
- Validación estructural por comparación: bloque binario idéntico, 200 animaciones
  idénticas y resto del JSON idéntico salvo esas cuatro rotaciones. Los 200 clips
  tienen canales explícitos de rotación para esos cuatro huesos. No se evaluaron
  visualmente todas las señas ni las transiciones dentro de la app.
- Blender 5.2.3 LTS se usó solo para importar copias de visualización sin clips y
  renderizar; **no se exportó desde Blender el GLB candidato**. Se evitó así alterar
  las curvas por conversión. La forma natural de las manos queda a revisión del owner.
- Referencia formal leída: `../signall-docs/latex/srs/main.tex:330–340`, corpus
  cerrado y reproducción de movimientos preparados. No se modificaron documentos.
- Pendiente: aprobación visual. No se integró, compiló ni instaló la app: el APK
  permanece intacto por el alcance solicitado. Sin commit ni push.

### Segunda propuesta: orientación de las manos

- El owner aprobó la posición de los brazos y pidió girar las manos: el dorso
  todavía miraba demasiado al frente. Se interpretó como palmas hacia los muslos
  y pulgares al frente; queda pendiente su aprobación visual.
- En la misma copia se añadieron giros de `hand.L/R` alrededor del eje del
  antebrazo (75° por lado, sentidos opuestos, parámetro del script de prueba).
  Se mantienen las rotaciones locales de los dedos y la posición de los brazos.
- Se repitió la comparación estructural: binario y 200 animaciones idénticos;
  solo seis rotaciones de nodos difieren del GLB de la app. Todos esos huesos
  tienen canales explícitos de rotación en las 200 animaciones.
- Vistas nuevas: `/tmp/opencode/juan-rest/manos-ajustadas.png` y
  `comparacion-manos.png` (izquierda propuesta anterior, derecha nuevo giro).
  `despues.glb` es ahora esta segunda propuesta, aún sin integrar.

### Tercera propuesta: punto medio del giro

- El owner consideró excesivo el giro anterior y pidió el punto medio entre las
  dos propuestas, con la palma algo al frente y principalmente hacia el cuerpo.
- Se redujo el giro de muñecas de 75° a **37,5°**, parámetro del script de prueba;
  brazos y dedos sin cambios. `despues.glb` contiene ahora esta tercera propuesta.
- Verificación estructural repetida: las 200 animaciones y el bloque binario
  siguen idénticos al asset; los originales conservan sus hashes.
- Vista `manos-punto-medio.png` y detalle `comparacion-punto-medio.png`, en
  `/tmp/opencode/juan-rest/`. El centro es la propuesta nueva, flanqueada por las
  dos anteriores. Pendiente aprobación; sin integración ni instalación.

### Pauline con el reposo aprobado de Juan

- El owner aprobó el punto medio de Juan y pidió la misma pose para Pauline.
- Ambos tienen 102 articulaciones, pero no son idénticos en sus transformaciones
  locales. Se transfirieron los cambios de orientación global de los seis huesos
  desde Juan aprobado a Pauline, respetando sus offsets, dedos, malla y esqueleto.
  No se copiaron ciegamente las rotaciones locales de un personaje al otro.
- Script `/tmp/opencode/juan-rest/prepare_pauline.py`; copias, informe, imágenes y
  escenas en `/tmp/opencode/pauline-rest/`. `comparacion.png` muestra antes/después.
- Comparación estructural: 200 animaciones idénticas, bloque binario idéntico,
  cambios únicamente en las seis rotaciones de brazos, antebrazos y muñecas.
  Todos esos huesos tienen canales de rotación explícitos en los 200 clips.
- Fuentes originales sin cambios: asset Pauline SHA-256
  `663139385dbf8f9fdce41d5c1753124460a95abed322f4fd9e136e087482722b`;
  `../signall-avatar/models/Pauline.glb` en `fc6f372`, SHA-256
  `fd2d27779726af385d1d0a7ac8a2e474e2cd51e185777a510acf8e6839d1c313`.
- Pendiente aprobación visual de Pauline e integración explícita posterior.
  No se modificó ni instaló la app; los candidatos continúan separados.

### Integración autorizada y verificación física

**Miércoles 2026-11-11, mañana · Rama:** `main`.

- El owner aprobó Pauline y autorizó integrar ambos reposos y probar el teléfono.
- `tools/approved_rest_pose.json` conserva las rotaciones exactas aprobadas y
  los hashes de origen. `tools/pack_avatars.py` las aplica después del frame base,
  valida la fuente y comprueba canales explícitos en todos los clips. Se verificó
  idempotencia y rechazo de fuente nueva/canal ausente para ambos personajes.
- Se regeneraron los dos GLB, `avatars.json` y `manifest.json`. `SignsCatalog.kt`
  no cambia. Los GLB resultantes coinciden byte a byte con las copias aprobadas:
  200 clips y binario idénticos al asset anterior; solo seis rotaciones cambiadas.
- Fuentes de `../signall-avatar` intactas. La advertencia histórica de bind de
  Pauline sigue presente, sin intentar cambiarla dentro de este alcance.
- `./gradlew build -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` pasó
  (incluido ktlint); `installDebug` con la misma propiedad instaló en el Redmi
  Note 9S, Android 11. Reverse de 8080 configurado para USB.
- Backend apagado: prueba **local**, no traducción. Desde CDP del WebView se
  reprodujo `0000` en cuatro tipos de oración por avatar: ocho reproducciones.
  En todas se observó reproducción y regreso a las seis rotaciones aprobadas,
  tolerancia 1e-5 por componente (q/−q equivalentes). Breakpoint temporal retirado.
  No se midió latencia ni se evaluaron visualmente las 50 señas.
- Evidencias durables: `docs/evidencias/reposo-2026-11-11/`; spec actualizado en
  `openspec/specs/avatar/spec.md`. No se tocó código Kotlin, JS ni repos hermanos.

### Prueba aislada de transición al nuevo reposo

- El owner reportó el recorrido por la pose vieja dentro de los clips y autorizó
  preparar copias con extremos adaptados, sin modificar el movimiento central.
- En la muestra `0000_afirmativo` de Juan, las rotaciones finales de los brazos
  difieren del reposo aprobado en 25,8° (L) y 29,2° (R), calculados con el producto
  de cuaterniones del GLB. Esto confirma una diferencia, no una evaluación de las
  50 señas. `0029` tampoco termina exactamente en su primer frame.
- Se preparó una propuesta experimental solo para `0000`, cuatro variantes por
  avatar: seis canales de rotación por variante; primer/último 20% adaptados con
  peso quíntico y slerp. El 60% central conserva sus muestras exactas. Esa ventana
  es una hipótesis de prueba, no una frontera lingüística comprobada.
- Copias en `/tmp/opencode/transition-test/`. Visor antes/después en
  `http://127.0.0.1:8766/`; render de Juan y Pauline verificado en Chromium headless.
  Evidencias y script reproducible: `docs/evidencias/transiciones-2026-11-11/`.
- No se tocaron los assets de la app, el empaquetador ni los originales hermanos.
  No se compiló ni reinstaló por esta prueba externa. Pendiente revisión del owner
  y prueba de encadenamiento; no se considera corregido ni integrado.

### Segunda prueba del regreso, por observación del owner

- El owner indicó que v1 todavía recorre el reposo viejo antes de bajar al nuevo
  y autorizó ajustar las copias. V2 sustituye el regreso de `0000`, no solo mezcla
  su extremo: desde la muestra de 1,375 s al reposo aprobado a 1,9167 s, con
  permanencia allí hasta el final. Ventana provisional, pendiente revisión LSC.
- Entrada igual a v1; muestras centrales hasta 1,375 s conservadas. Solo seis
  rotaciones por variante y avatar. V1 sigue disponible para comparación.
- Comprobados 24 canales por avatar: normalización, centro exacto, distancia
  angular decreciente al reposo y llegada exacta. Render de ambos avatares
  verificado en Chromium headless. Evidencias reproducibles en
  `docs/evidencias/transiciones-2026-11-11/v2/`; visor temporal `/v2/` en puerto 8766.
- App y originales intactos; sin build ni instalación nuevos por tratarse de
  copias externas. Pendiente aprobación visual y prueba entre señas; no integrado.

### Tercera prueba: suavizar el paso final al reposo estático

- El owner aprobó visualmente la mejora de v2 («muchísimo mejor») y pidió frenar
  con más suavidad el acomodo final del cuerpo. Se preparó v3 solo en el visor:
  ambos lados usan el mismo GLB v2, sin otra edición de muestras.
- Derecha: últimos 250 ms de clip repartidos en 700 ms, desaceleración Hermite
  y mezcla quíntica del cuerpo completo hacia el reposo. Izquierda: v2 y su
  salida lineal de 100 ms. La duración visible aumenta 350 ms frente a v2.
- El regreso de brazos de v2 y el movimiento anterior a 2,2083 s no cambian.
  Parámetros experimentales; pendiente aprobación visual del owner.
- Carga/render comprobados en ambos avatares con Chromium headless; 1001 muestras
  temporales verifican monotonía, pesos válidos y límites continuos. Evidencias
  en `docs/evidencias/transiciones-2026-11-11/v3/`; visor temporal `/v3/`, puerto 8766.
- Sin cambios en app, GLB ni hermanos; no hubo build/instalación nuevos por esta
  prueba externa. No es una medición de rendimiento ni prueba del controlador real.

### Aprobación de v3 y solicitud de extensión a todas las señas

- El owner aprobó v3 («Hermoso») y pidió documentar e integrar para todas las
  señas, con prueba en la app. Decisiones consolidadas en
  `docs/TRANSICIONES-REPOSO.md`; no se confunde aprobación de `0000` con revisión
  lingüística de todo el corpus.
- Auditoría reproducible de los 400 clips actuales en
  `docs/evidencias/transiciones-2026-11-11/audit_endings.py` e informe JSON adjunto.
  Las ventanas no se pueden copiar sin criterio: `0040` dura 1,417 s y `0018`
  termina con antebrazos a unos 61,8°/58,6° del reposo anterior, frente a 3,0°/1,5°
  en `0000` (Juan afirmativo; cálculo de cuaterniones, no revisión lingüística).
- No se extrapoló automáticamente la ventana aprobada ni se modificaron nuevos
  assets/código de app. Pendiente decisión del owner: revisar límites por seña
  en grupos o permitir adaptación automática experimental con revisión completa.
  Integración, build e instalación de las transiciones aún pendientes.

### Extensión por grupos: decisión y primera comparación

- El owner eligió revisar por grupos, anotando y viendo poco a poco; no aplicar
  automáticamente una ventana común. Decisión actualizada en
  `docs/TRANSICIONES-REPOSO.md`.
- Grupo 01: `0000` como referencia aprobada y propuestas `0001`/`0002`. Regresos
  distintos por seña: 1,3333–1,9167 s y 1,625–2,125 s respectivamente; límites
  provisionales inferidos de curvas, no validados lingüísticamente. Se conservan
  íntegras sus entradas, incluida la postura inicial flexionada de `0001`.
- Copias de ambos avatares y cuatro variantes; 72 canales verificados por avatar.
  Las otras 188 animaciones no cambian. Comprobados centro/entrada exactos,
  normalización y regreso monótono con llegada al reposo.
- Visor con selectores de seña, avatar y variante en `/group-01/`, puerto 8766;
  comparación actual/propuesta con acomodo final v3. Verificadas 24 selecciones
  y render en Chromium headless, sin mediciones de rendimiento.
- Registro de revisión y evidencia reproducible en
  `docs/evidencias/transiciones-2026-11-11/grupo-01/`. Pendiente revisión del owner
  de las dos propuestas; sin cambios nuevos en assets de app ni build/instalación.

### Aprobación del grupo 01

- El owner respondió «Las dos perfectas. todo perfecto»: se registran `0001` y
  `0002` aprobadas visualmente sin correcciones, con las ventanas propuestas
  conservadas. No especificó revisión individual por avatar/variante ni se
  atribuye validación lingüística formal a esa respuesta.
- Registro actualizado en `docs/TRANSICIONES-REPOSO.md` y en el README del
  grupo 01. Grupo aprobado; integración y encadenamiento en app pendientes.
  No se modificó la app ni se compiló/instaló por este registro documental.

### Grupo 02: cinco señas nuevas por pedido del owner

- El owner pidió grupos de cinco y autorizó preparar `0003`–`0007`. Propuestas
  sobre copias, sin cambiar la app ni las propuestas aprobadas del grupo 01.
- Ventanas por seña, segundos (inicio/llegada): `0003` 1,4583/2,000;
  `0004` 1,5833/2,000; `0005` 1,3333/1,875; `0006` 1,4583/1,9583;
  `0007` 1,1667/1,750. Inferidas de las curvas, pendientes de revisión del gesto.
  Entradas y muestras previas íntegras; solo seis rotaciones por clip.
- Ambos avatares y cuatro variantes. Comprobados 120 canales por avatar;
  otras 180 animaciones intactas. Verificadas 40 selecciones y render en
  Chromium headless. Sin mediciones de rendimiento ni validación lingüística.
- Visor temporal `/group-02/`, puerto 8766. Registro, scripts e informes en
  `docs/evidencias/transiciones-2026-11-11/grupo-02/`. Cinco revisiones pendientes.
  No hubo build/instalación nuevos por tratarse de copias externas.

### Aprobación del grupo 02 y preparación del grupo 03

- El owner aprobó `0003`–`0007`: «hermoso todo aprobado, siguientes». Registradas
  sin correcciones, no se presume un desglose de revisión por avatar/variante.
- Grupo 03 preparado: `0008` 1,375–1,9583 s; `0009` 1,3333–1,875 s;
  `0010` 1,375–1,9167 s; `0011` 1,3333–1,875 s; `0012` 1,2083–1,7917 s.
  Límites provisionales por seña tras inspección de curvas, pendientes del owner.
  Entradas exactas, incluso la postura inicial flexionada de `0012`.
- Comprobados 120 canales por avatar; otras 180 animaciones intactas. Se corrigió
  el cálculo angular del test para normalizar cuaterniones float32 antes de acos;
  no se cambiaron tolerancias ni assets por ese fallo de comprobación.
- Verificadas 40 selecciones/render en Chromium headless. Visor temporal
  `/group-03/`, puerto 8766; evidencia y registro en
  `docs/evidencias/transiciones-2026-11-11/grupo-03/`. Cinco revisiones pendientes.
  Sin integración, build ni instalación nuevos por esta prueba externa.

### Aprobación del grupo 03 y preparación del grupo 04

- El owner aprobó `0008`–`0012`: «hermoso todo aprobado, siguientes». Registradas
  sin correcciones; no se presume revisión individual por avatar/variante.
- Grupo 04: `0013` 1,6667–2,125 s; `0014` 1,4583–2,000 s;
  `0015` 1,4583–2,0417 s; `0016` y `0017` 1,8333–2,125 s. Propuestas tras
  inspección de curvas, no límites lingüísticos validados. `0016` señalada para
  revisar especialmente: termina flexionada en el original; se propone completar
  su regreso sin alterar muestras anteriores al ancla tardía.
- Comprobados 120 canales por avatar y 40 selecciones/render en Chromium headless;
  otras 180 animaciones intactas. Copias con acomodo v3 en el visor temporal
  `/group-04/`, puerto 8766. Registro/evidencia en
  `docs/evidencias/transiciones-2026-11-11/grupo-04/`; cinco revisiones pendientes.
- Sin cambios nuevos en app o hermanos ni build/instalación por esta prueba externa.

### Aprobación del grupo 04 y preparación del grupo 05

- El owner aprobó `0013`–`0017`, incluida `0016`: «hermoso todo aprobado TODO,
  siguientes». Sin correcciones; no se presume revisión desglosada por variante.
- Grupo 05: `0018` 1,9583–2,125 s; `0019` 1,9167–2,125 s;
  `0020` 1,9583–2,3333 s; `0021` 1,5833–2,0417 s; `0022` 2,2083–2,750 s.
  Hipótesis por curvas, no fronteras lingüísticas. `0018` señalada especialmente
  por acabar flexionada en el original. El acomodo v3 empieza antes del ancla en
  `0018/0019`: se documenta la diferencia entre datos exactos y reproducción.
- Comprobados 120 canales por avatar y 40 selecciones/render en Chromium headless;
  otras 180 animaciones intactas. Cinco revisiones pendientes. Visor temporal
  `/group-05/`, puerto 8766; evidencia/registro en
  `docs/evidencias/transiciones-2026-11-11/grupo-05/`.
- Sin integración ni cambios nuevos en app o hermanos; sin build/instalación por
  esta prueba externa. Los grupos aprobados mantienen sus ventanas intactas.

### Aprobación del grupo 05 y preparación del grupo 06

- El owner aprobó `0018`–`0022`, incluida `0018`: «hermoso todo aprobado TODO,
  siguientes». Registradas sin correcciones; no se presume revisión por variante.
- Grupo 06: `0023` 2,000–2,3333 s; `0024` 1,875–2,3333 s;
  `0025` 2,125–2,2917 s; `0026` 1,7917–2,125 s; `0027` 1,9583–2,2083 s.
  Propuestas por curvas, no límites lingüísticos. `0025` señalada especialmente
  por su movimiento tardío y flexión final; acomodo v3 anterior al ancla de esa
  seña, pendiente revisar el conjunto de movimiento y salida.
- 120 canales por avatar verificados, 180 animaciones restantes intactas;
  40 selecciones/render en Chromium headless. Visor `/group-06/`, puerto 8766.
  Registro/evidencia en `docs/evidencias/transiciones-2026-11-11/grupo-06/`.
- Cinco revisiones pendientes; sin integración ni cambios nuevos en app o hermanos.
  No hubo build/instalación por tratarse de copias externas.

### Aprobación del grupo 06 y preparación del grupo 07

- El owner aprobó `0023`–`0027`, incluida `0025`: «hermoso todo aprobado TODO,
  siguientes». Sin correcciones; no se presume revisión por avatar/variante.
- Grupo 07: `0028` 1,7083–2,0833 s; `0029` 1,9583–2,2083 s;
  `0030` 1,0833–1,7083 s; `0031/0032` 1,5833–2,0417 s. Ventanas propuestas
  por curvas, no límites lingüísticos. `0030` señalada por su regreso temprano;
  todas las entradas exactas, incluido el inicio flexionado de `0028`.
- 120 canales por avatar y 40 selecciones/render en Chromium headless verificados;
  otras 180 animaciones intactas. Visor `/group-07/`, puerto 8766. Cinco revisiones
  pendientes; evidencia en `docs/evidencias/transiciones-2026-11-11/grupo-07/`.
- Sin integración ni cambios nuevos en app o hermanos; sin build/instalación por
  esta prueba externa. Grupos aprobados anteriores sin cambios.

### Grupo 07 aprobado; incidencia aparte en el inicio de 0028

- El owner indicó «Muy bien, todas bien», pero observó un arranque abrupto de
  `0028` ajeno al cambio del regreso. Se registran los cinco regresos aprobados,
  sin atribuir revisión individual por avatar/variante ni validación LSC formal.
- Inspección de `0028_afirmativo` en ambos avatares: todos los canales del primer
  medio segundo son idénticos antes/después. En el original, distancia del codo
  al reposo nuevo: L 26,558°→1,305° y R 26,478°→0,699°, de 0,041667 a 0,083333 s
  (unos 42 ms entre muestras). Esto respalda la observación, no una corrección
  lingüísticamente validada. Evidencia `grupo-07/0028-start-diagnosis.json`.
- No se corrigió ese inicio ni se cambiaron propuestas aprobadas; pendiente
  preparar/autorizar copia específica. Sin cambios en app, build o instalación.

### Propuesta aislada para el inicio de 0028

- El owner autorizó «Si. Go.». La inspección ampliada encontró primera/segunda
  muestra distintas en brazos superiores, manos, dedos y otras partes del cuerpo,
  no solo codos. No se atribuye causa de exportación ni validez lingüística.
- Copias desde grupo 07 aprobado: reemplazar primera muestra TRS por segunda
  solo en canales con muestra a 1/24 y 2/24 s; constantes/compactados entre
  extremos y pesos faciales intactos. Todas las muestras posteriores exactas,
  incluidos gesto y regreso; otras 196 animaciones por avatar intactas.
- Verificadas 232 primeras muestras cambiadas en Juan y 384 en Pauline (cuatro
  variantes por avatar), con hashes y detalle en `0028-inicio/verification.json`.
  Ocho selecciones/render comprobados en Chromium headless. Ambos lados del
  visor usan el acomodo v3 para aislar el cambio del inicio.
- Evidencia reproducible en `docs/evidencias/transiciones-2026-11-11/0028-inicio/`;
  visor `/0028-start/`, puerto 8766. Pendiente aprobación visual, sin integración.
  Sin cambios en app/hermanos ni build/instalación por esta copia externa.

### 0028: salto aprobado y propuesta de entrada desde reposo nuevo

- El owner aprobó quitar el salto («Bien, excelente. Muy muy muy buena»), pero
  señaló el paso inicial por reposo viejo. Autorizó preparar nueva entrada solo
  en esta seña («Si. No lo habia notado tanto en el resto de señas, pero en esta
  sí. Vamos»). No se extrapola al resto.
- Copias desde `0028-start` aprobado: seis rotaciones por variante en reposo nuevo
  hasta 0,4167 s y preparación directa slerp/peso quíntico hasta 0,750 s. Desde la
  unión inclusive todas las muestras originales protegidas, incluido el regreso.
  Ventana provisional, pendiente revisión del gesto; cara/dedos/torso/tiempos intactos.
- 24 canales por avatar comprobados, otras 196 animaciones intactas; ocho selecciones
  y render en Chromium headless. Visor `/0028-entry/`, puerto 8766, con el mismo
  acomodo v3 en ambos lados. Evidencia `docs/evidencias/transiciones-2026-11-11/0028-entrada/`.
- Pendiente aprobación de entrada; sin integración ni cambios nuevos en app o
  hermanos, build/instalación no realizados por esta prueba externa.

### Entrada de 0028 aprobada y grupo 08 preparado

- El owner aprobó la entrada («hermoso, perfecto. documentamos y seguimos como
  antes»). Registrados salto, entrada y regreso aprobados de `0028`; no se
  generaliza esa corrección inicial a las otras señas.
- Grupo 08: `0033` 1,5833–2,000 s; `0034` 2,000–2,3333 s;
  `0035` 1,3333–1,875 s; `0036` 1,5833–2,000 s; `0037` 1,8333–2,125 s.
  Propuestas por curvas, no límites lingüísticos certificados. Entradas exactas.
- 120 canales por avatar y 40 selecciones/render en Chromium headless comprobados;
  otras 180 animaciones intactas. Cinco revisiones pendientes, visor `/group-08/`,
  puerto 8766; evidencia en `docs/evidencias/transiciones-2026-11-11/grupo-08/`.
- Sin integración ni cambios nuevos en app/hermanos; sin build/instalación por
  esta prueba externa. Propuestas aprobadas anteriores intactas.

### Cambio de prioridad: revisar entradas de todos los grupos anteriores

- El owner confirmó grupo 08 («ya revisé todo el grupo y está bien»), pero pidió
  pausar grupos nuevos y atender el reposo nuevo también al inicio de todas las
  señas revisadas, como en `0028`. Regresos `0000`–`0037` aprobados; entradas
  reabiertas, salvo correcciones específicas ya aprobadas de `0028` como referencia.
- La conservación de inicios originales había conservado el reposo viejo. Se
  documenta el límite de la comprobación anterior: datos intactos no demuestran
  coherencia de entrada con el reposo nuevo. No se culpa al export por este criterio.
- Autorización amplía la entrada antes específica de `0028`. Mantener grupos de
  cinco: primero `0000`–`0004`, límites por seña y regreso aprobado fijo; no aplicar
  su ventana automáticamente a otras, especialmente las que ya empiezan activas.
- Matriz de estados y prioridad en `docs/TRANSICIONES-REPOSO.md`; grupo 08 actualizado.
  Nuevas propuestas de entradas aún no generadas. Sin cambios de app, assets o
  hermanos, ni build/instalación por este registro de decisión.

El owner corrigió inmediatamente el tamaño de esta revisión: **diez por grupo,
no cinco**. Primer grupo de entradas `0000`–`0009`; finales aprobados y referencia
`0028` se conservan. Actualizada la prioridad en `docs/TRANSICIONES-REPOSO.md`.

### Primer grupo de diez entradas preparado

- El owner autorizó «Go». Copias `0000`–`0009` comparan entradas contra regresos
  aprobados combinados de grupos 01/02/03; mismo acomodo v3 en ambos lados.
- Uniones: `0000` 0,3333 s, `0002` 0,2083 s, `0003/4/5/7/8/9` 0,250 s.
  Propuestas por curvas, no límites lingüísticos. `0000` sustituye mezcla v1,
  protegiendo muestras aprobadas desde 0,500 s.
- `0001/0006` añaden 0,375/0,250 s de preparación para preservar todas las muestras;
  todos sus tiempos se desplazan, incluidos faciales/regreso. Duración aumenta,
  regresos conservan valores y tiempos relativos, no instante absoluto.
- Verificación roundtrip: 40 clips por avatar, reposo inicial de seis rotaciones,
  muestras protegidas exactas, conteos válidos y otras 160 animaciones intactas.
  80 selecciones/render en Chromium headless comprobados. Registro y evidencia en
  `docs/evidencias/transiciones-2026-11-11/entradas-01/`, visor `/entries-01/`, puerto 8766.
- Diez entradas pendientes, sin integración ni cambios nuevos en app/hermanos;
  sin build/instalación por tratarse de copias externas.

### Entradas 01 aprobadas y entradas 02 preparadas

- El owner aprobó `0000`–`0009` («hermoso todo aprobado TODO, siguientes»), sin
  correcciones, incluidos los prefijos de `0001/0006`. Aprobación registrada.
- Entradas `0010`–`0019` preparadas. `0010/0012` añaden 0,250/0,3333 s para
  conservar todas las muestras. Ocho uniones locales: `0011` 0,2917 s,
  `0013` 0,3333 s, `0014/0017` 0,750 s, `0015` 0,6667 s,
  `0016/0018` 0,7917 s y `0019` 0,250 s. `0014`–`0018` sostienen reposo nuevo
  durante la espera inicial con tiempos por seña. Límites provisionales, no LSC validado.
- Regresos aprobados de grupos 03/04/05 conservados, con tiempos desplazados en
  las dos con prefijo. 40 clips por avatar comprobados por roundtrip, otras 160
  animaciones intactas; 80 selecciones/render en Chromium headless verificados.
- Evidencia en `docs/evidencias/transiciones-2026-11-11/entradas-02/`, visor
  `/entries-02/`, puerto 8766. Diez revisiones pendientes; sin integración ni cambios
  nuevos en app/hermanos, build/instalación no realizados por esta prueba externa.

### Entradas 02 aprobadas y entradas 03 preparadas

- El owner aprobó `0010`–`0019` («hermoso todo aprobado TODO, siguientes»), sin
  correcciones, incluidos prefijos. No se presume revisión por avatar/variante.
- Entradas 03: `0020`–`0029`, nueve propuestas y referencia `0028` aprobada.
  Referencia copiada completa desde `0028-entry`: primera postura, entrada y
  regreso intactos, idénticos en ambos lados. No se vuelve a modificar.
- Esperas/uniones por seña en `entradas-03/README.md`; ninguna añade duración.
  Nueve entradas solo cambian seis rotaciones; desde la unión, muestras exactas.
  Límites provisionales por curvas, no fronteras LSC certificadas.
- 40 clips por avatar comprobados (36 nuevos + cuatro de referencia), otras 160
  animaciones intactas; 80 selecciones/render en Chromium headless verificados.
- Evidencia `docs/evidencias/transiciones-2026-11-11/entradas-03/`, visor
  `/entries-03/`, puerto 8766. Nueve revisiones pendientes. Sin integración ni
  cambios nuevos en app/hermanos, build/instalación no realizados por esta prueba externa.

### Entradas 03 aprobadas; diagnóstico aparte de sacudida de 0020

- El owner aprobó entradas («perfecto, todo. hermoso») pero señaló movimiento
  corporal extraño de `0020` después del gesto, «como si le diera un escalofrio».
  Entradas aprobadas, incidencia separada; no se corrige automáticamente.
- Auditoría de ocho clips: todos los canales fuera de seis rotaciones editadas
  idénticos al original. Juan afirmativo: anular L cambia 86,44° entre 1,9583 y
  2,000 s; muslos L/R cambian 16,59°/14,22° y hombros 11,63°/10,73° entre
  2,1667 y 2,2083 s. Datos de GLB, no medición de personas/rendimiento ni LSC validado.
- Visor `/0020-inspect/`, puerto 8766: original/revisado, velocidad lenta, pausa,
  desplazamiento temporal y modo por defecto sin mezcla al reposo para aislar clip.
  14 selecciones/tiempos verificados en Chromium headless, capturas de 47/48/52/53.
- Evidencia y auditoría reproducible en
  `docs/evidencias/transiciones-2026-11-11/0020-diagnostico/`. Pendiente confirmar
  momento percibido con el owner, sin modificar GLB/aprobaciones ni app/hermanos.
  No hubo build/instalación por esta inspección externa.

El owner confirmó después el momento del tirón corporal de `0020` alrededor de
2,2 s: «Si, es ese el que noto». Diagnóstico localizado; no se interpreta esa
confirmación como aprobación de una corrección aún no preparada. Pendiente
autorizar/preparar copia localizada, sin cambios nuevos en app ni GLB.

### Propuesta corporal de 0020 preparada

- El owner autorizó la copia («Si»; después «sigue»). Partiendo de entradas 03
  aprobado, propuesta solo en 2,0417–2,250 s: columna/cabeza/hombros/piernas,
  rotaciones slerp y traslaciones con peso quíntico, anclas/fuera de ventana exactos.
- Juan: 72 canales en cuatro variantes; Pauline: 64, sin inventar las traslaciones
  de hombros que no exporta. Seis curvas locales de brazos, dedos y cara intactos;
  hombros suavizados alteran trayectoria global durante ese tramo. No se toca el
  salto de dedos de 2,000 s ni se afirma validez LSC. Otras 196 animaciones intactas.
- Verificación roundtrip: canales no objetivo/timestamps exactos, cuaterniones
  normalizados y máximo angular/frame de las rotaciones seleccionadas no mayor.
  Juan afirmativo, muslo L 16,59°→6,64°, hombro L 11,63°→4,61° dentro de la ventana,
  cálculo de GLB, no medición de personas/rendimiento.
- 24 selecciones/tiempos comprobados en Chromium headless, con/sin mezcla final.
  Evidencia `docs/evidencias/transiciones-2026-11-11/0020-cuerpo/`, visor
  `/0020-smooth/`, puerto 8766. Pendiente aprobación visual, no se declara resuelto.
- App/fuentes/copias aprobadas intactas; sin integración, build o instalación por
  esta prueba externa. Revisión de entradas siguientes pausada hasta cerrar esta.

El owner detectó otro movimiento «como en el 1.8». Propuesta corporal no aprobada,
incidencia no resuelta. Inspección afirmativa de ambos avatares en 1,65–1,95 s:
meñique L cambia 47,65° en 1,6667→1,7083 s y 45,91° en 1,875→1,9167 s;
muñeca R cambia 8,09° en el primer intervalo. Datos anteriores a la corrección
corporal, preservados; evidencia `0020-cuerpo/early-motion-audit.json`. Pendiente
confirmar si el movimiento percibido es de manos/muñecas o cuerpo, sin filtrar
dedos ni extender la corrección a ciegas. No hubo cambios nuevos en app o GLB.

### Segunda prueba corporal de 0020 tras aclaración del owner

- El owner percibe el movimiento «en todo el cuerpo», no específicamente dedos.
  Propuesta v2 sobre v1 aún no aprobada: filtrado corporal local 1,500–2,125 s,
  cinco muestras ponderadas con alineación/normalización de cuaterniones y bordes
  quínticos de 0,125 s. No se filtra toda la seña ni dedos/cara/brazos/manos.
- Juan 88 canales, Pauline 76 en cuatro variantes. Fuera de ventana, incluida
  la salida de v1 después de 2,125 s, muestras exactas; otras 196 animaciones
  intactas. Suavizado de hombros altera trayectoria global y piernas pueden
  cambiar apoyos: se señala para revisión, sin prometer ausencia de deslizamiento.
- Roundtrip comprobado: canales no objetivo exactos, tiempos/nodos exactos,
  normalización y máximo angular/frame no mayor en rotaciones filtradas. Juan
  afirmativo en esta ventana: hombro L 6,90°→3,32°, muslo L 10,29°→4,10°; cifras
  calculadas del GLB, no medición de fluidez/personas ni comparación entre ventanas.
- Ocho selecciones/render en Chromium headless verificados. Evidencia en
  `docs/evidencias/transiciones-2026-11-11/0020-cuerpo-v2/`, visor `/0020-smooth-v2/`,
  puerto 8766. Pendiente aprobación visual de cuerpo/apoyos, incidencia no resuelta.
- App/hermanos/copias anteriores intactos; sin integración ni build/instalación
  por esta prueba externa. Próximo grupo de entradas sigue pausado.

### Tercera propuesta conjunta de 0020

- El owner precisó manos/dedos y cuerpo, autorizó («si») preparar copia conjunta.
  V3 aplica mismo criterio temporal de v2 en 1,500–2,125 s también a brazos,
  manos/palmas/dedos; no al clip entero. Configuraciones intermedias/contactos
  pueden cambiar y deben revisarse; no se declara validez lingüística por filtrado.
- Se calcula desde v1 y comprueba cuerpo exacto a v2 para no filtrar dos veces.
  Comparación izquierda v2/derecha v3. Cambios adicionales: 144 canales en Juan,
  164 en Pauline, cuatro variantes. Ahora sí cambian curvas locales de manos/
  brazos en el tramo, incluido inicio del regreso; entrada/cara/fuera de ventana
  exactos. Otras 196 animaciones intactas.
- Roundtrip comprobado: canales no objetivo exactos, tiempos/nodos, normalización
  y máximo angular/frame no mayor. Meñique L de Juan afirmativo 53,29°→12,21°
  en esta ventana; cálculo del GLB, no medición de personas/rendimiento/fluidez.
- Ocho selecciones/render en Chromium headless verificados; evidencia
  `docs/evidencias/transiciones-2026-11-11/0020-conjunto-v3/`, visor `/0020-smooth-v3/`,
  puerto 8766. Pendiente aprobación visual del conjunto, incidencia no resuelta.
- App/hermanos/copias previas intactos; sin integración ni build/instalación por
  esta prueba externa. Siguiente grupo de entradas sigue pausado.

### 0020 v3 elegida y entradas 04 preparadas

- El owner decidió «bien, ya sigamos con el resto. esa ya dejemosla asi».
  Se conserva `0020` v3, sin más ajustes/perfección/validez LSC declaradas.
  Decisión/hashes en `0020-conjunto-v3/accepted.json`; integrar esa versión,
  no entradas 03 anterior. Copias previas intactas.
- Entradas 04: `0030`–`0039`, ocho entradas nuevas con regresos aprobados intactos.
  `0038/0039` aún no tenían salida revisada: propuestas de ambos extremos,
  señaladas en visor. Nuevas salidas 1,500–2,0417 s y 0,875–1,4583 s.
  Sin duración añadida; ventanas por curvas, no fronteras LSC certificadas.
- 40 clips por avatar comprobados: reposo inicial, ocho regresos exactos, dos
  nuevos alcanzan reposo, muestras protegidas y otras 160 animaciones intactas.
  80 selecciones/render en Chromium headless verificados.
- Evidencia `docs/evidencias/transiciones-2026-11-11/entradas-04/`, visor
  `/entries-04/`, puerto 8766. Diez revisiones pendientes; sin integración ni
  cambios nuevos en app/hermanos, build/instalación no realizados por copias externas.

### Entradas 04 aprobadas y últimas diez preparadas

- El owner aprobó `0030`–`0039`, incluidas salidas nuevas de `0038/0039`: «todo
  bien, ya termina las señas, lo que queda». Sin correcciones/desglose por variante.
- Último grupo `0040`–`0049`: ambos extremos nuevos, tiempos por seña en
  `entradas-05/README.md`, ninguna duración añadida. Ventanas por curvas,
  no fronteras lingüísticas certificadas. Solo seis rotaciones; centro exacto.
- 40 clips por avatar comprobados: reposo inicial, diez salidas alcanzan reposo,
  muestras protegidas/conteos válidos y otras 160 animaciones intactas.
  80 selecciones/render en Chromium headless verificados. Evidencia
  `docs/evidencias/transiciones-2026-11-11/entradas-05/`, visor `/entries-05/`, puerto 8766.
- Las 50 tienen propuestas preparadas; últimas diez pendientes de revisión.
  Integración aún pendiente: combinar clips elegidos de entradas 01–05, conservar
  `0020` v3 según accepted.json y `0028` íntegra de entradas 03. No promover un GLB
  de grupo como si incorporara todos. App/hermanos/copias anteriores intactos;
  sin build/instalación por esta prueba externa.

### Integración de las 50 señas y de la salida final

- El owner confirmó las últimas diez («Todo confirmado. Sigue.») y pidió
  continuar. Se integraron las curvas aprobadas por **sello**, no copiando un GLB
  de grupo: `tools/approved_tracks.json.gz` (6,69 MB) + `tools/approved_tracks.py`.
  Incluye `0020` v3 y `0028` completa; el empaquetador verifica hash de base y de
  cada curva y falla si el export o el reposo cambian.
- `tools/export_approved_tracks.py` congela ese archivo; `check_approved_tracks.py`
  comprobó **200 clips por avatar exactos** a las copias elegidas (400 en total).
- Tamaño: al deduplicar accessors de animación antes de compactar, el APK **baja**
  frente a los assets con solo el reposo nuevo: Juan 30,32 → 27,73 MB,
  Pauline 27,85 → 27,59 MB. Hashes en el informe de integración.
- Reproductor: `playbackTiming.js` + `beginRestExit/tickRestExit` en `app.js`.
  Últimos 250 ms del clip en 700 ms, action pausada y muestreada a mano para que
  `LoopRepeat` no reinicie, peso quíntico a 0 y `onPlaybackDone` al peso cero.
  Entre señas siguen los 100 ms; con `reduceMotion`, corte seco; con velocidad,
  la salida escala con `dt * speed`.
- Pruebas: `node tools/test_playback_timing.mjs` (seis duraciones, monotonía,
  límites, igualdad con el visor) y `tools/verify_playback_cdp.py` sobre la escena
  real en Chromium: variantes, repetida, append y stop durante la salida,
  movimiento reducido, cambio de velocidad y recursos ausentes. Ocho casos OK.
- El Redmi se desconectó del USB durante el primer `installDebug`
  (`No connected devices!`); el owner lo reconectó y **se instaló**. En el
  teléfono pasaron los ocho casos de cola y **las 50 señas en Juan y Pauline**
  con las cuatro variantes alternadas; el informe está en `device-final.json`.
- Efecto medible: la salida dura 700 ms y empieza 250 ms antes del final, así que
  `onPlaybackDone` llega **~450 ms después del final nominal** a velocidad normal.
  Medido en el Redmi con `0028`: 2,574 s contra 2,125 s de clip; a 2×, 1,308 s;
  con `reduceMotion`, 2,138 s. `SignAiMetrics` incluye ese tramo y no es
  comparable con las cifras anteriores. No se midió rendimiento (CDP con debug JS).
- Evidencia: `docs/evidencias/transiciones-2026-11-11/integracion/`; spec
  actualizado en `openspec/specs/avatar/spec.md`. No hubo commits en hermanos.
