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

## 2. Orden de trabajo: cimientos primero, paralelo después

El problema que hay que evitar es concreto: si una persona mueve el paquete de la app
mientras otra toca pantallas, las dos están editando los mismos 76 archivos y el merge es
una pelea. Por eso el trabajo se parte en dos clases, y no se mezclan:

- **Cimientos**: lo que comparten todos y no se puede editar por partes. Nombres, rutas,
  contrato entre repos y configuración de build. **Va en serie, una sola persona, con
  `main` congelado.**
- **Módulos**: cada uno con su pedazo de archivos y su dueño. **Va en paralelo**, y solo
  después de que los cimientos estén quietos.

### Ola 0 — cimientos (una sola persona, `main` congelado)

| # | Qué | Repo | ¿Bloquea a los demás? |
|---|---|---|---|
| C0 | Renombrar los repositorios en GitHub y actualizar los `origin` | los cinco | ✔ hecho 2026-11-13 |
| C1 | `namespace`, `applicationId` → `com.signall.app` y el paquete Kotlin | android | **sí**: nadie toca `app/` |
| C2 | Identificadores internos: tags de logcat, `window.SignAI`, `SignAiBridge`, `SharedPreferences` | android | **sí**: mismos archivos que C1 |
| C3 | Nombres que cruzan el WebSocket (`rt_*`, frames) | android + backend | **sí**: es un solo cambio en dos repos |
| C4 | Sacar las evidencias pesadas de `docs/` y arreglar los enlaces | android | **sí**: mueve rutas que otros citan |
| C5 | `sonar-project.properties` y CI mínimo | los cuatro | no, pero toca la raíz de cada repo |

C1 y C2 se hacen **juntos, en un solo hilo**: son los mismos archivos y separarlos obliga a
pasar dos veces por 76 archivos.

Mientras la ola 0 corre, esto **sí** puede avanzar en paralelo, porque no comparte un solo
archivo con android:

- **backend**: sus propios nombres internos, su README y su configuración de Sonar.
- **stt**: lo mismo.
- **plan de pruebas y mediciones**: no toca código.
- **`sign-all-docs` y `sign-all-avatar`**: no se tocan por dentro; no hay nada que hacer.

### Ola 1 — módulos en paralelo

Con los cimientos quietos, cada persona toma un área y la mueve sin pisar a nadie. La
división es por **archivo**, no por tema: dos personas pueden trabajar en "la app" si una
toca `ui/` y la otra `assets/web/`.

| Área | Dueño toca | No toca |
|---|---|---|
| UI / Compose | `app/src/main/java/**/ui/**` | `assets/web/**`, `domain/**` |
| Escena 3D y avatar | `app/src/main/assets/web/**` | Kotlin |
| Pipeline, mic y socket | `**/data/**`, `**/domain/**` | `ui/**` |
| Documentos y tesis | `docs/**`, `openspec/**`, `*.md` | código |
| Backend | repo backend completo | los demás repos |
| STT | repo STT completo | los demás repos |

### Ola 2 — cierre

Verificación cruzada, READMEs finales, y una última pasada de Sonar y de gitleaks sobre
todo, con las credenciales ya rotadas.

### Reglas de convivencia (las cinco que evitan el desastre)

1. **Un archivo tiene un solo dueño a la vez.** Si dos lo necesitan, se reparte por
   archivo, no por tarea.
2. **Nadie hace `force-push`. Nunca.** Un `force-push` obliga a todos a re-clonar, que es
   justo lo que esta reorganización quiere evitar.
3. **`pull --rebase` antes de cada commit, commits chicos y un tema por commit.** Un commit
   de 76 archivos se revisa; uno de 300 no.
4. **Durante la ola 0, `main` está congelado para todos menos para quien hace los
   cimientos.** Los demás trabajan en su rama y no mergean hasta que termine.
5. **Nada de `push`.** Todo queda local hasta que el owner lo ordene, repo por repo. Esto
   vale para los cinco repos y también para las ramas de trabajo.

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
5. **Se renombra antes de que nadie más clone.** El renombre de los repositorios se
   adelantó a todo lo demás: es lo único que obliga a un clon nuevo, y hacerlo al final
   habría obligado a cada persona a re-apuntar su `origin`.

Tabla de renombre (**hecha** el 2026-11-13 en GitHub; los `origin` locales ya apuntan a
las URLs nuevas):

| Antes | Ahora | Carpeta local |
|---|---|---|
| `sign-ai-android` | `sign-all-android` | sigue siendo `sign-ai-android` |
| `sign-ai-backend` | `sign-all-backend` | sigue siendo `sign-ai-backend` |
| `sign-ai-stt` | `sign-all-stt` | sigue siendo `sign-ai-stt` |
| `sign-ai-docs` | `sign-all-docs` | sigue siendo `sign-ai-docs` |
| `signai-avatar` | `sign-all-avatar` | sigue siendo `signai-avatar` |

**Convención mientras dure la reestructuración:** el repositorio en GitHub ya se llama
`sign-all-*`, pero la carpeta local conserva el nombre viejo. Así las rutas relativas
`../sign-ai-backend` que están escritas en `AGENTS.md`, en `docs/` y en los scripts de
`tools/` siguen funcionando para todos, y un clon nuevo se hace con el nombre viejo:

```bash
git clone git@github.com:sign-ai-organization/sign-all-backend.git sign-ai-backend
```

Renombrar las carpetas es un `mv` y una pasada de enlaces; se hace al final, de una vez,
cuando ya no haya nadie escribiendo documentos (ver C4 y la ola 2).

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

## 5. Plan por bloques

Cada bloque es una sesión aparte, con su propio prompt en `prompts/`. La columna "ola"
dice cuándo se puede hacer: los de la ola 0 van en serie, los de la ola 1 pueden ir a la
vez.

| Bloque | Ola | Qué se hace | Repo | Entregable | Puede ir en paralelo con |
|---|---|---|---|---|---|
| R-01 | — | gitleaks sobre toda la historia | los 5 | **hecho** → `01-leaks.md` | — |
| R-08 | 0 · C0 | renombrar los repos en GitHub y los `origin` | los 5 | **hecho** · `sign-all-*` | — |
| R-02 | 0 · C1 | `namespace`, `applicationId` y paquete Kotlin → `com.signall.app` | android | app instalada con el nombre nuevo | backend, STT |
| R-03 | 0 · C2 | tags, `window.SignAI`, `SignAiBridge`, `SignAiState`, `SharedPreferences` | android | tags nuevos | backend, STT |
| R-04 | 0 · C3 | nombres que cruzan el socket, en los dos lados el mismo día | android + backend | tabla viejo → nuevo | **nada** |
| R-05 | 0 · C4 | sacar evidencias pesadas, dejar lo vivo, `docs/historia/` | android | `docs/` bajo 10 MB | backend, STT |
| R-07 | 0 · C5 | `sonar-project.properties` + GitHub Actions | los 4 | panel y CI | cualquiera |
| R-06 | 1 | README corto por repositorio | los 5 | 5 READMEs | todo, al final de la ola 0 |
| — | 2 | verificación cruzada y última pasada de Sonar y gitleaks | todos | informe de cierre | — |

R-02 y R-03 son **un solo hilo**: mismos 76 archivos, separados obligan a pasar dos veces.
R-08 se adelantó al principio porque es lo único que obliga a un clon nuevo.

**Advertencia que hay que decirle a cualquiera que se sume mientras la ola 0 esté
abierta:** en android no se toca `app/` ni `docs/`. Se trabaja en backend, en STT, en
mediciones o en la tesis. Tocar la app en ese momento es garantizar un conflicto en 76
archivos.

**Orden confirmado:** el de arriba. El owner delegó el orden y el criterio que manda es
"primero lo que obliga a re-clonar y lo que toca los cimientos". El paquete va primero
porque arrastra todo lo demás y así se hace una sola vez; el precio es reinstalar la app y
volver a conceder `RECORD_AUDIO`, que son dos minutos.

---

## 6. Bitácora

| Fecha | Qué |
|---|---|
| 2026-11-13 | Alineación de los cinco repos. `sign-ai-docs` fast-forward `2a9feb5` → `503a002`; `sign-ai-android` merge de `FableAvatar` → `main` (`9db21ad` → `64c4dd5`) y push. Árbol de trabajo de android intacto. |
| 2026-11-13 | Se crea esta carpeta y este documento. |
| 2026-11-13 | Decidido: los cinco repos se renombran a `sign-all-*`; docs y avatar solo cambian de nombre, no de contenido. Esta sesión es solo de planificación. |
| 2026-11-13 | El owner delega el orden al agente. Orden confirmado: cimientos en serie, módulos en paralelo. |
| 2026-11-13 | Herramientas instaladas: SonarQube 9.9.8 LTS Community en Docker (`:9000`, Postgres 17, solo en 127.0.0.1) y gitleaks 8.30.1 ya presente. Tres proyectos con su primer análisis. |
| 2026-11-13 | R-01 ejecutado: la historia está limpia salvo **dos credenciales reales** en android (Neon + OpenAI, dentro de evidencias de `PR-CAC-05`). Pendiente que el owner las rote. |
| 2026-11-13 | **Los cinco repositorios renombrados en GitHub** a `sign-all-*` y los `origin` locales reapuntados; `fetch` verificado en los cinco. Los proyectos de Sonar se rehicieron como `sign-all-*` y se borraron los viejos. |
| 2026-11-13 | Convención fijada: la carpeta local conserva el nombre viejo para no romper las rutas relativas de `AGENTS.md`, `docs/` y `tools/`. Un clon nuevo se hace con el nombre viejo. |
| 2026-11-13 | **Super regla del owner: nada de `push`.** Se commitea local y se espera la orden, repo por repo. Queda anotada en `AGENTS.md`, en el prompt de R-02 y en `prompts/R-00-arranque.md`. |

