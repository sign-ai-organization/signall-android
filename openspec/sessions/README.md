# Sessions — bitácora de trabajo

Bitácora cronológica de lo que se hace en el proyecto. Es parte del context
engineering (ver `AGENTS.md`): mantenerla actualizada es tan importante como
el código.

## Convención

- Un archivo por sesión: `openspec/sessions/YYYY-MM-DD-<slug>.md`
  (ej. `2026-09-16-ui-direction-sessions.md`).
- Cada archivo empieza con encabezado: **título, día, fecha y hora**.
- Al final de cada cosa que se haga (tarea, fix, decisión), actualizar la
  sesión correspondiente: qué se hizo, qué se decidió y qué quedó pendiente.
- Si una sesión ya existe y se sigue trabajando el mismo día, extenderla en
  lugar de crear otro archivo.
- Corto y factual: solo lo que un futuro agente necesitaría para retomar.

## Índice

- `2026-09-16-setup-context-engineering.md` — Reescritura de `AGENTS.md` solo-WebView, dirección de UI, creación de esta bitácora.
- `2026-09-17-analisis-migracion-ui.md` — Análisis del proyecto + plan de migración a Lumo UI + Phosphor (8 fases). Plan completo en `openspec/changes/ui-migration-lumo/tasks.md`.
- `2026-09-23-prototipo-ui-vercel.md` — Investigación del prototipo Next/Vercel + plan para replicarlo en la app nativa (7 fases). Plan en `openspec/changes/prototipo-ui-vercel/tasks.md`.
- `2026-09-30-integracion-backend.md` — Análisis de `../sign-ai-backend`, plan de integración, y la implementación de los pasos 1-4 (sesión WS, frase escrita, cola de señas, descarga de assets) más `tools/mock_backend.py` para probar sin backend real.
- `2026-10-04-protocolo-backend.md` — El backend avanzó a frases por partes (`segment` + `done`) y la app seguía con el protocolo viejo, así que no señaba nada. Se alinearon los dos repos: la app parsea `segment`/`done` y acumula, el backend reenvía `{"type":"text"}` y `{"type":"end"}`. También se sacaron las credenciales reales del `env` fuera de git.
- `2026-10-13-merge-backend-a-main.md` — `feat/integracion-backend-v2` mergeada a `main` (fast-forward) y pusheada. Se aclara que "el máster" de este repo es `main`, no el `master` parado del repo padre. Build verificado e instalado en el Redmi Note 9S con la config de teléfono físico (`adb reverse` + `-Psignai.backendWsUrl`).
- `2026-10-13-rename-a-sign-ai-android.md` — La carpeta `webview/` pasa a llamarse `sign-ai-android/`, igual que el repo y que sus hermanos. Se commitea el tooling nuevo (skill, Maestro, perfilado) y se verifica build + reinstall desde la ruta nueva.
- `2026-10-13-tooling-agente-movil.md` — Se replicó el stack de control de móvil de `AppMovil` adaptado a Android nativo: skill `controlar-app-movil`, `tools/HERRAMIENTAS.md`, flows de Maestro, `tools/rendimiento/` y `tools/pinch.sh`. Se descubrió que el avatar 3D es invisible a toda esta instrumentación, que Maestro no anda en el Redmi (MIUI), y que acá `dumpsys gfxinfo` sí funciona (al revés que en Flutter).
- `2026-10-13-glb-animation-mixer.md` — El avatar y las señas dejan el motor procedural y los JSON de landmarks: ahora son GLB con la pose horneada, separados en `models/avatar.glb` + `signs/<id>.glb` y reproducidos con `THREE.AnimationMixer`. Nuevo `tools/split_signs.py`. 329.7 MB → 16.22 MB, separación validada sin pérdida (dif. 0.00e+0), jank 99.2% → 6.7%.
- `2026-10-14-seed-catalog-neon.md` — `catalog` de Neon sembrada con las 50 glosas de LSC50 (antes 3 filas con rutas que nadie servía). Verificada end-to-end con el `CatalogRepository` real y cruzada contra el disco y `SignsCatalog.kt` por sha256. El seed destapó un 404 garantizado por cada glosa de las 18 sin animación; agregado `SignsCatalog.isOwnAssetPath()` (`GLS-10`), medido en el Redmi: 2 peticiones 404 → 0.
- `2026-10-14-conectar-stt-y-backend-real.md` — Plan para los dos puntos que faltan: micrófono real (STT) y backend real. El cableado **ya existe** en los tres repos (app captura PCM16, backend reenvía a `ws://stt:8000/ws/transcribe`, `sign-ai-stt` expone `/ws/transcribe`): lo que falta es levantar `docker compose up --build` y apuntar la app. Incluye la lista de frases que funcionan con las 32 señas que hay, y las trampas de esta sesión.
- `2026-10-14-reposo-y-crossfade.md` — Investigación de las dos mejoras de animación de la sesión: **pose de descanso con los brazos abajo** (sí se puede sacar de los `.glb`: primer keyframe de `0029`, 851 frames de los 2424 tienen los brazos colgando) y **crossfade entre señas**. El hallazgo que reencuadra el trabajo: los clips ya son continuos entre sí (5.9 cm de mediana) y `TRANSITION_FRAMES` es lo que los rompe (dos saltos de ~46 cm por transición). Con la pose nueva + crossfade de 100 ms el salto máximo cae de 94.5 a 12.1 cm/frame y el turno dura ~8% menos. Plan en [`openspec/changes/reposo-y-crossfade/`](../changes/reposo-y-crossfade/tasks.md).
- `2026-10-14-traduccion-tiempo-real.md` — Traducción en tiempo real en los tres repos (rama `tiempo-real`): el avatar firma **mientras se habla**. STT con scheduler de inferencia y dos modelos, backend con traducción incremental guiada por timestamps por palabra, app con cola de fragmentos y adelanto automático. Primera seña p50 913 ms / p95 1336 ms sobre 28 fixtures de audio reales (antes 2.4–4.8 s de espera). Guía de pruebas en [`tools/TIEMPO-REAL.md`](../../tools/TIEMPO-REAL.md).
