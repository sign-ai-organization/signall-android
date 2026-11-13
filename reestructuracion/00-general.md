# Reestructuración SignAI → SignAll — plan general

Documento de trabajo. Se llena durante la sesión del **2026-11-13** y es el índice de
todo lo que viene después. Cada repositorio tiene (tendrá) su propio archivo en esta
misma carpeta: `01-android.md`, `02-backend.md`, `03-stt.md`, `04-docs.md`,
`05-avatar.md`.

Ya hay dos archivos llenos y no son planes, son resultados:

- **`01-leaks.md`** — qué encontró gitleaks y qué hay que rotar (hay dos credenciales
  reales comprometidas).
- **`herramientas/README.md`** — el SonarQube local, cómo levantarlo y cómo escanear.

Los prompts de cada bloque se guardan en `prompts/`: se escriben una vez y se copian tal
cual en la sesión que ejecuta. Hoy está `prompts/R-02-paquete.md`.

> **Esta sesión solo planea. No se implementa nada.** Acá se deciden las cosas y se
> escribe el orden; la implementación va en sesiones aparte, una por bloque, y cada una
> recibe su propio prompt.

El objetivo, en una frase: **que el proyecto se llame SignAll en todas partes, que no
haya credenciales expuestas, que los documentos estén ordenados y que cada repositorio
tenga un README que se pueda leer en dos minutos.** Nada de lo que funciona puede dejar
de funcionar.

---

## 1. Línea base (medida el 2026-11-13)

Lo que hay hoy, para poder comparar después.

| Dato | Valor | Cómo se midió |
|---|---|---|
| Repos alineados con su remoto | 5 de 5 | `git status -sb` + `git fetch` |
| `sign-ai-android` | rama `main`, `64c4dd5` | `FableAvatar` se mergeó por fast-forward en esta sesión |
| `sign-ai-docs` | alineado, `503a002` | estaba 1 commit atrás |
| `signai-avatar` | rama `dev`, `1330cf1`, sin upstream | no se toca |
| Peso de `docs/` en android | **246 MB, 1736 archivos** | `du -sh`, `find \| wc -l` |
| — de eso, `docs/plan-pruebas/` | 210 MB | `du -sh` |
| — de eso, `docs/evidencias/` | 32 MB | `du -sh` |
| Documentos `.md` en `docs/` | 66 | `find docs -name '*.md'` |
| Archivos fuera de `docs/` que dicen `signai` | 168 | `rg -c -i signai -g '!docs/**'` |
| Peso de `.git` | 330 MB | `du -sh .git` |
| Credenciales trackeadas | **0** (solo `.env.example`) | `git ls-files` + filtro |
| Credenciales en disco, ignoradas | `env`, `.envJulian` | `.gitignore` |
| Credenciales reales en la historia | **2** (Neon + OpenAI) | gitleaks sobre `--all`, ver `01-leaks.md` |
| SonarQube local | arriba, `:9000`, 3 proyectos analizados | `api/system/status` |
| Android en Sonar | 13.806 ncloc · 63 code smells · 1 bug · cobertura 0 % | primer análisis |
| Backend en Sonar | 2.349 ncloc · 6 code smells · 1 bug · fiabilidad E | primer análisis |
| STT en Sonar | 853 ncloc · 2 code smells · todo A | primer análisis |

Desalineación que se corrigió en esta sesión: `main` de android estaba 3 commits atrás
de `FableAvatar` (los merges de avatar de 2026-11-12 y 2026-11-13) y `main` de docs
estaba 1 commit atrás. Los dos quedaron alineados y pusheados. No se perdió nada: el
árbol de trabajo de android quedó intacto.

---

## 2. Orden de trabajo propuesto

El rename toca los cinco repos y el contrato entre ellos, así que el orden importa.
**La regla es: primero se decide la marca, después se renombra el código, y el renombre
de los repositorios de GitHub va al final.**

| Fase | Qué | Repo | Estado |
|---|---|---|---|
| **0** | Línea base: alinear remotos, escanear leaks sobre la historia completa | todos | ✔ alinear · ✔ gitleaks (2 credenciales a rotar) |
| **1** | Decidir la marca y el alcance (ver § 3) | — | **pendiente del owner** |
| **2** | Rename técnico en la app: paquete, `applicationId`, recursos, textos | android | pendiente |
| **3** | Rename en backend y STT: títulos, nombres de servicio, logs, docker | backend, stt | pendiente |
| **4** | Limpieza de documentos + README por repo | los cinco | pendiente |
| **5** | Calidad: SonarQube en cada repo, CI mínimo | los cuatro | Sonar ✔ instalado y con línea base · CI pendiente |
| **6** | Renombrar los repositorios en GitHub | org | pendiente |

El detalle de cada bloque, con su repositorio y su entregable, está en § 5.

### Decisiones ya tomadas (2026-11-13)

1. **Los cinco repositorios cambian de nombre.** Todos dicen `sign-ai` o `signai` y el
   proyecto se llama SignAll. El de avatar también estaba mal.
2. **`sign-ai-docs` y `signai-avatar` no se tocan por dentro.** Ni contenido, ni
   documentos, ni `.glb`. De esos dos solo cambia el nombre del repositorio en GitHub.
3. **La implementación no va en esta sesión.** Acá solo se planea; después se ejecuta
   por bloques, con un prompt por bloque.
4. **Las herramientas de trabajo corren locales, gratis y sin cuenta.** SonarQube
   comunitario en Docker (no SonarCloud) y gitleaks en la máquina. Quedaron instaladas y
   probadas en esta sesión; ver `herramientas/README.md`.

Tabla de renombre (se aplica en la fase 6, de una sola vez):

| Hoy | Queda |
|---|---|
| `sign-ai-android` | `sign-all-android` |
| `sign-ai-backend` | `sign-all-backend` |
| `sign-ai-stt` | `sign-all-stt` |
| `sign-ai-docs` | `sign-all-docs` |
| `signai-avatar` | `sign-all-avatar` |

Lo que arrastra el renombre, para no olvidarlo: la URL de `origin` en cada clon, las
rutas relativas `../signai-avatar` que aparecen en `AGENTS.md`, `docs/` y los scripts de
`tools/`, y los enlaces entre documentos. GitHub redirige las URLs viejas, así que un
clon que no se actualice no se rompe, pero queda desprolijo.

Aparte: en el directorio hermano hay dos carpetas que **no son repositorios**
(`sign-ai-video/`, `sign-ai-video-vertical/`). No entran en el renombre de GitHub; si
hay que renombrar la carpeta local es una decisión aparte.

---

## 3. Decisiones abiertas (una por una, con mi recomendación)

Ninguna de estas la resuelve esta sesión: se presentan y se espera respuesta.

### 3.1 ¿SignAll es el nombre definitivo?

- **Recomiendo:** sí para el software (código, app, repos, README) y **dejar `SignAI`
  intacto en la tesis** hasta que se decida el título del documento. Cambiar el título
  de la tesis es una decisión académica, no técnica.
- Lo que se gana: todo el código queda coherente.
- Lo que se pierde: durante un tiempo el código dirá SignAll y el SRS dirá SignAI. Eso
  hay que anotarlo como deuda explícita, no silenciarla.
- Nota: `SignAll` ya existe en el repo como `tools/pack_signall.py` y `GLB SignALL/`, así
  que el nombre no es nuevo.

### 3.2 `applicationId` y paquete de Kotlin

- Hoy: `com.signai.webview` en `namespace` y en `applicationId`.
- **Decidido:** `com.signall.app` en los dos, y el renombre del paquete Kotlin de
  `com/signai/webview/` a `com/signall/app/`.
- Consecuencia obligatoria de avisar: **es otra app para el teléfono y para Play**. No
  actualiza la instalada; convive con ella y hay que desinstalar la vieja. Además cambian
  los comandos de siempre: `adb shell pm grant com.signall.app ...`, y el filtro
  `adb logcat` por proceso si alguno lo usa.
- Queda una sola decisión asociada: **hacerlo ya o dejarlo para el final**. Si se hace
  ahora, hay que reinstalar y volver a conceder `RECORD_AUDIO`; si se deja para el final,
  todo el trabajo de reestructuración se hace sobre el paquete viejo y el rename queda
  como último paso visible.

### 3.3 ¿Se renombra también la organización de GitHub?

- El rename de los cinco repos ya está decidido (§ Decisiones ya tomadas). Queda una
  sola duda: la organización se llama `sign-ai-organization`.
- **Recomiendo:** dejarla como está. Renombrar una organización cambia todas las URLs de
  golpe, obliga a actualizar cada clon y no es lo que se ve en una demostración de la
  tesis.
- Lo que se gana renombrándola: coherencia total.
- Lo que se pierde: todo lo que apunte a la org vieja deja de funcionar sin redirección
  y hay que tocarlo a mano.
- **Esto lo decide el owner**: es su cuenta, no del código.

### 3.4 Identificadores internos

Hay cuatro familias y no todas se tratan igual:

| Familia | Ejemplos | Qué hacer |
|---|---|---|
| Solo del device | `SignAIWeb` (tag de logcat), `SignAiBridge`, `window.SignAI`, `SignAiState` | **renombrar**: nadie más los usa |
| Cruza al hermano | nombres de métrica `rt_*`, frames del socket | **no tocar sin coordinar los dos lados en el mismo cambio** |
| Contrato de datos | `signs/<id>.glb`, clips `0000_afirmativo`, glosas `GLS-*` | **no tocar nunca**: son identificadores, no marca |
| Persistencia local | `SharedPreferences "signai"` | renombrar junto con el `applicationId`; al ser instalación nueva no hace falta migrar |

- **Recomiendo:** renombrar todo lo de la primera fila, y para la segunda hacer **un
  commit en cada repo el mismo día**, con el mismo nombre nuevo a los dos lados. Nunca
  en dos sesiones distintas.

### 3.5 Documentos: qué se queda y qué se va

- Hoy `docs/` son 246 MB y 1736 archivos, de los cuales 210 MB están en
  `plan-pruebas/` (hojas de cálculo, capturas) y 32 MB en `evidencias/`. Nadie lee eso
  desde un clon; se consulta cuando se arma un anexo.
- **Recomiendo:** tres movimientos, en este orden:
  1. **Sacar del repo** las evidencias pesadas (`.xlsx`, capturas, logcat sueltos) a un
     repositorio o unidad aparte, y dejar en `docs/plan-pruebas/` solo el índice y la
     metodología.
  2. **Dejar en `docs/` una decena de documentos vivos**: `SISTEMA.md`,
     `MODO-EN-VIVO.md`, `manual-identidad-visual.md` y poco más.
  3. **Mover a `docs/historia/`** los planes y reportes ya cerrados
     (`PLAN-SEMANA-13.md`, `PENDIENTES-AUDITORIA-*.md`, `REVISION-DEVELOP.md`, los
     `CONTRASTE-*.md` viejos). No se borran: git ya tiene la historia, pero un archivo
     marcado como historia no se vuelve a leer por error.
- Lo que **no** se toca acá: `../sign-ai-docs/latex/` es la fuente de verdad de la tesis
  y se edita con el flujo de `make`.

### 3.6 README por repositorio

- **Recomiendo:** un README corto, uno por repo, con cinco secciones: qué es, cómo se
  corre, cómo se verifica, qué necesita (puertos, device, credenciales) y a dónde ir
  después. Nada más.
- El `README.md` de android tiene hoy 325 líneas: la parte útil se queda y el resto se
  va a `docs/`.

### 3.7 Gitleaks

- **Hecho.** Corrido el 2026-11-13 sobre toda la historia de los cinco repositorios.
  Resultado: **dos credenciales reales** en android (contraseña de Neon y clave de
  OpenAI, dentro de las evidencias de `PR-CAC-05`) y seis falsos positivos. Detalle,
  clasificación y acciones en **`01-leaks.md`**.
- **Decidido:** cuando aparezca algo real, **se rota**. Borrar el archivo o reescribir la
  historia no descompromete una credencial que ya estuvo publicada.
- Después del primer escaneo limpio, queda como paso de CI para que no vuelva a pasar.

### 3.8 SonarQube

- **Decidido: SonarQube local, no SonarCloud.** Servidor comunitario en Docker, gratis,
  sin cuenta y sin mandar el código a nadie. Ya está instalado y corriendo
  (`http://localhost:9000`), con los tres repositorios de código analizados una vez.
  Las instrucciones están en `herramientas/README.md`.
- Qué se ganó: los datos quedan en la máquina y el análisis no depende de internet ni de
  que la organización sea pública.
- Qué cuesta: mantener un contenedor más y recordar levantarlo. Como está en
  `restart: unless-stopped`, vuelve solo cuando arranca Docker.
- Ojo con la expectativa: en este proyecto va a marcar mucha deuda de estilo y pocos
  bugs. Es útil como semáforo y para ver si empeora, no como meta.
- Línea base del 2026-11-13: android 13.806 líneas y 63 code smells; backend 2.349 líneas,
  1 bug y fiabilidad E; STT 853 líneas y 2 code smells. **Cobertura 0 % en los tres**,
  porque no hay un solo test.

### 3.9 Integración continua

- Hoy no hay CI. **Recomiendo** un GitHub Actions mínimo, por repo:
  - `sign-ai-android`: `./gradlew build` (ya corre ktlint) + gitleaks.
  - `sign-ai-backend` y `sign-ai-stt`: gitleaks + lo que el `CLAUDE.md`/`README`
    respectivo diga que ya se corre a mano.
- Sin eso, SonarQube no tiene datos y gitleaks depende de que alguien se acuerde. El
  análisis de SonarQube local **no puede correr en Actions**: el servidor vive en esta
  máquina. En CI iría el escaneo de leaks y las compilaciones; el análisis de calidad se
  corre a mano antes de cerrar una sesión.

---

## 4. Lo que no se toca en esta reestructuración

- **`sign-ai-docs` por dentro: nada.** Es la fuente de verdad de la tesis; su contenido se
  edita con su propio flujo (`make`), no para que diga SignAll. Solo cambia el nombre del
  repositorio.
- **`signai-avatar` por dentro: nada.** Ni los `.glb` de `../signai-avatar`
  (`models/Juan.glb`, `models/Pauline.glb`), ni `tools/pack_avatars.py`. Se regeneran solo
  cuando el owner lo pide.
- Los identificadores del contrato: frames del WebSocket, `signs/<id>.glb`, nombres de
  los 200 clips, glosas `GLS-*`.
- `../sign-ai-docs/latex/`: se edita con su flujo, no se renombra por estética.
- Las ramas remotas viejas (`origin/develop`, `respaldo/*`): son historia del owner.
- `android:forceDarkAllowed=false` y todos los "no lo saques" de `AGENTS.md`.

---

## 5. Plan por sesiones

Cada bloque es una sesión aparte, con su propio prompt. El orden es el que menos cosas
rompe: primero lo que no se ve, después lo que se ve, y el renombre de GitHub al final.

| Bloque | Sesión | Qué se hace | Repos | Entregable |
|---|---|---|---|---|
| R-01 | Leaks | gitleaks sobre toda la historia; rotar lo que aparezca | los 5 | **hecho**: 2 credenciales a rotar → `01-leaks.md` |
| R-02 | Paquete | `applicationId` y `namespace` → `com.signall.app`, renombre del paquete Kotlin, `app_name`, textos de UI | android | app compilada e instalada como app nueva |
| R-03 | Internos | tags de logcat, `SignAiBridge`, `window.SignAI`, `SignAiState`, `SharedPreferences` | android | tags nuevos documentados |
| R-04 | Métricas | nombres que cruzan el socket, cambiados el mismo día en los dos lados | android + backend | tabla viejo → nuevo |
| R-05 | Docs | sacar evidencias pesadas, dejar lo vivo, mover lo cerrado a `docs/historia/` | android | `docs/` bajo 10 MB |
| R-06 | README | un README corto por repositorio | los 5 | 5 READMEs |
| R-07 | Calidad | `sonar-project.properties` en cada repo + GitHub Actions mínimo | los 4 | análisis en el panel y CI verde |
| R-08 | GitHub | renombrar los cinco repositorios | org | URLs nuevas |

Los bloques R-02 a R-04 forman un solo hilo: el nombre. R-05 y R-06 son el otro hilo:
la presentación. R-07 es el tercero: la evidencia de calidad.

**Orden confirmado:** este, con el paquete en R-02. El owner delegó el orden. Va
temprano porque el rename del nombre arrastra todo lo demás y así se hace una sola vez;
el precio es reinstalar la app y volver a conceder `RECORD_AUDIO`, que son dos minutos.

---

## 6. Bitácora

| Fecha | Qué |
|---|---|
| 2026-11-13 | Alineación de los cinco repos. `sign-ai-docs` fast-forward `2a9feb5` → `503a002`; `sign-ai-android` merge de `FableAvatar` → `main` (`9db21ad` → `64c4dd5`) y push. Árbol de trabajo de android intacto. |
| 2026-11-13 | Se crea esta carpeta y este documento. |
| 2026-11-13 | Decidido: los cinco repos se renombran a `sign-all-*`; docs y avatar solo cambian de nombre, no de contenido. Esta sesión es solo de planificación. |
| 2026-11-13 | El owner delega el orden al agente. Orden confirmado: R-01 → R-08, con el paquete en R-02. |
| 2026-11-13 | Herramientas instaladas: SonarQube 9.9.8 LTS Community en Docker (`:9000`, Postgres 17, solo en 127.0.0.1) y gitleaks 8.30.1 ya presente. Tres proyectos con su primer análisis. |
| 2026-11-13 | R-01 ejecutado: la historia está limpia salvo **dos credenciales reales** en android (Neon + OpenAI, dentro de evidencias de `PR-CAC-05`). Pendiente que el owner las rote. |

