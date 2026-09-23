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
