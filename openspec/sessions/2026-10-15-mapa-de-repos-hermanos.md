# Mapa de los repositorios hermanos en `AGENTS.md`

- **Día:** jueves
- **Fecha:** 2026-10-15
- **Hora:** 10:30–11:00

## Qué se hizo

`AGENTS.md` decía "este repo es solo la app WebView" y no nombraba nunca a los otros
cuatro repos, aunque tres de ellos están en la misma carpeta y dos dominan la latencia
que la app reporta. Se agregó al principio del archivo la sección **§ Mapa del sistema:
los repositorios hermanos**.

Recorrido de cada carpeta hermana (`ls`, `git log`, lectura de `CLAUDE.md`/`README.md`
del backend y del STT) para escribir lo que una sesión necesita antes de tocar el
pipeline: qué es cada repo, en qué rama está, qué puerto usa, cómo se levanta, dónde está
el contrato con la app y qué reglas de git tiene.

Contenido de la sección:

- Diagrama `stt → backend → app` con Neon colgando del backend, y tabla de los cuatro
  repos (qué es / rama / puerto / cómo se levanta).
- **`../sign-ai-backend`**: leer `CLAUDE.md` antes de tocarlo (código en inglés,
  nombres de clases según el SDD, nunca commitear). `docs/android-integration.md`
  (461 líneas) es la fuente de verdad del WebSocket `/ws/translate` y se actualiza en
  el mismo cambio que el protocolo. `semantic_cache` no se invalida al cambiar el
  prompt. `STT_WS_URL` apunta a `stt:8000` porque dentro de Docker `localhost` no es
  el STT. `load_all()` carga el catálogo al arrancar.
- **`../sign-ai-stt`**: protocolo binario PCM16LE 16 kHz → JSON de texto, `"end"` para
  cerrar; `words` y `audio_ms` aditivos; dos modelos + `InferenceScheduler` +
  `SILENCE_TIMEOUT_MS=600`; los 28 fixtures `.wav` no están en git y se generan con
  Piper; sin AGENTS.md ni CLAUDE.md, manda su README.
- **`../sign-ai-docs`**: requerimientos consolidados v0.15, diagramas del SDD
  (drawio + png) y plan de pruebas. `modificaciones/requerimientos.md` es la bandeja de
  cambios pendientes con estados Propuesto/Acordado/Hecho: lo implementado sin
  requisito se anota ahí.
- **`../signai-avatar`**: demo Flask de landmarks, **inactiva desde 2026-09-19** y
  **no** es la fuente de los GLB de la app (esos salen de `GLB Senas/` →
  `tools/split_signs.py`). No mezclar los dos pipelines de avatar.
- Las carpetas que **no** son repos: `../openspec/` (OpenSpec compartido de avatar,
  distinto del de la app y con copias que divergen), `../data/` (LSC50),
  `../Resources/` (scripts y docs del approach abandonado, incluido
  `split_avatar_for_filament.py`), `../signai_env/` (venv).
- **El contrato entre repos**: un solo WebSocket de la app al backend (`:8080`), nunca
  al STT ni a Neon; `stream: true`/`total: 0`, `dictation: true`, orden `end` antes de
  `mode`, `REALTIME_ENABLED=false` como segundo modo soportado, y nombres de métrica
  compartidos. Más las tres herramientas de la repo que dependen del código de los
  hermanos (`verificar-tiempo-real.sh`, `mock_backend.py`, `local_stt.py`).
- **Siete reglas de comunicación entre sesiones**: leer el contrato en los dos lados
  antes de cambiarlo, un cambio que cruza el contrato no está terminado hasta que el
  otro lado está actualizado, dejar rastro en `openspec/sessions/` de cada repo con el
  repo y el commit de origen del dato, no commitear en los hermanos sin que lo pida el
  owner, referenciarlos por ruta relativa, medir cada número donde está, y preguntar en
  vez de rellenar decisiones abiertas por inferencia.

## Además: la rama `tiempo-real` ya no existe

El archivo afirmaba que el trabajo de tiempo real vivía en la rama `tiempo-real` de los
tres repos. No es cierto desde el 2026-10-14: la rama no existe en ninguno, el backend y
el STT tienen el commit *"docs: la rama tiempo-real ya no existe"* y `main` de la app tiene
*"docs(git): la rama tiempo-real ya no existe; se deja develop en la app"*. Todo el
trabajo real quedó en **`develop`** en los tres repos.

Se corrigieron las dos referencias que quedaban (el título de § Real-time mode y el
párrafo de § Git workflow), que se contradecían con la sección nueva. También el índice
de `openspec/sessions/README.md`.

## Pendiente

- Las copias de `openspec/specs/avatar` y `dataset` divergen entre `../openspec/`,
  `../signai-avatar/openspec/` y el de la app. No se resolvió cuál es la buena: es una
  decisión del owner.
- `sign-ai-docs` y `signai-avatar` no tienen AGENTS.md ni CLAUDE.md. En la sección nueva
  se dijo "preguntarle al owner" para el de git, pero no hay escrito si se puede
  commitear directo ahí.