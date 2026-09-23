# Sesión: setup de context engineering

- **Día:** miércoles
- **Fecha:** 2026-09-16
- **Hora:** ~23:55 (-05)

## Qué se hizo

1. `AGENTS.md` reescrito como solo-WebView. Eliminado todo lo obsoleto
   (appKotlin, Flask `app/`, Unity, límite de 256 huesos de Filament,
   Hilt, `CONTEXT.md`/`PLAN.md`). Verificado contra `build.gradle.kts`,
   `MainActivity.kt`, `index.html`, `app.js` y el puente `window.SignAI`.
2. Añadida sección `UI direction`: Compose + Lumo UI + Phosphor Icons,
   Design System propio centralizado, dark mode minimalista con el avatar
   como protagonista, efectos/animaciones sutiles y moderadas, UI ligera
   para no degradar el WebView 3D. Sin nuevos componentes Material 3 ni
   `material-icons-extended`; migración progresiva de lo existente.
   (Solo documentación, sin cambios de código.)
3. Creada esta bitácora (`openspec/sessions/` + `README.md`) y documentado
   en `AGENTS.md` que OpenSpec es el context engineering del proyecto.

## Pendiente

- Empezar la migración UI a Lumo UI / Design System en próximas tareas.
- Documentar con qué script se hornea `sign.json` (pendiente, solo el usuario lo sabe).

## Actualización (~00:10)

4. **Purga del context engineering.** `specs/avatar/spec.md` reescrito: origen
   en el prototipo `../app/static/` (Flask con video y `/api/*`, muerto), nombres
   de huesos corregidos a sin-puntos (`app.js` manda — verificado por grep en ambos
   `app.js`), Bug 1 reencuadrado como error del spec viejo, sync cara/body movida a
   tiempo de horneado. `specs/pipeline/spec.md` reescrito como flujo real
   (CSVs → `sign.json` → WebView), eliminada toda la API Flask/`run.sh`/Flutter-Filament.
   `config.yaml`: bloque `context` reescrito a realidad WebView, reglas sin Filament.
   `specs/dataset` se dejó intacto (vigente).
5. **Lumo UI aclarado en `AGENTS.md`:** es nomanr/lumo-ui (Compose, estilo shadcn),
   NO el Lumo de Vaadin, con repo y docs como referencia obligada. Sin código.
6. **Decisiones registradas:** `sign-ai-backend` SÍ se integrará a futuro (hoy cero
   integración); `sign-ai-gloss-engine` NO, fuera de alcance.

## Pendiente (actualizado)

- Empezar la migración UI a Lumo UI / Design System en próximas tareas.
- Documentar con qué script se hornea `sign.json` (ver pregunta pendiente al usuario).
- Dar estado a `openspec/changes/` (`avatar-fixes-y-mejoras`, `mejoras-animacion-manos`):
  ¿vigentes, hechas o descartadas?
