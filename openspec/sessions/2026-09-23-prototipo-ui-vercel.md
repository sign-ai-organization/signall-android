# Sesión: investigación del prototipo Vercel y plan de adaptación UI

- **Día:** miércoles
- **Fecha:** 2026-09-23
- **Hora:** ~ (local)

## Qué se hizo

1. **Investigación completa de `PrototipoFrontend/`** (Next.js 16 + React 19 +
   Tailwind 4 + shadcn, v0.app):
   - `app/page.tsx` (240 líneas): toda la UI y el estado en un solo
     componente — splash, main, about, bottom sheet de historial, drawer
     izquierdo (Conversación) y drawer derecho (Ajustes), máquina de estados
     `idle/listening/processing/translating/signing/done`, `glossFor()`,
     personajes, accesibilidad, theming.
   - `app/globals.css` (55 líneas): CSS variables de theming, clases
     `theme-dark` / `font-bold` / `font-large` / `high-contrast`, animaciones
     (`rise`, `slide`, `dropdown-in`) y `prefers-reduced-motion`.
   - `components/ui/button.tsx`, `lib/utils.ts`, `public/*` — scaffolding
     shadcn/v0, no relevante (nuestra base es Lumo).
2. **Inventario de la app actual** (`app/src/main/java/...`): shell Compose +
   `AvatarWebView` (GLB real), `SignAiBottomBar` (input + historial dropdown +
   mic/send), `SignAiEdgeDrawer` (solo derecha) + `SettingsSidebar` (4 toggles
   debug + sign picker), `SignAiSplash` (spinner), paleta Lumo dark con
   `primary = Blue900 #276EF1`.
3. **Verificación de iconos Phosphor 1.0.5**: listados los 1513 iconos del
   AAR `phosphor-regular`; **todos** los del mapeo lucide→Phosphor existen
   (ArrowLeft, Check, CaretDown/Right, Clock, ClockCounterClockwise, CircleHalf,
   Info, Minus, Microphone, Moon, Play, Plus, ArrowCounterClockwise,
   PaperPlaneTilt, SlidersHorizontal, Sparkle, Sun, TextAa, SpeakerHigh, X,
   Gear, Translate).
4. **Se levantó el prototipo** (`pnpm install` + `pnpm dev`, Next en
   `localhost:3000`) para tenerlo como referencia. No se pudo capturar
   pantallazos: **no hay navegador de escritorio conectado a la sesión**
   (`browser.tabs.open` → `browser.disconnected`). Server detenido al cierre.
5. **Plan escrito** en `openspec/changes/prototipo-ui-vercel/tasks.md`:
   enfoque (reconstruir nativo, no embeber), inventario feature-by-feature,
   capacidades nuevas, qué NO copiar, 7 fases (A–G), riesgos y decisiones
   abiertas.

6. **Catálogo completo de features** en `openspec/specs/ui/spec.md` (nuevo):
   todas las features de la app con IDs (`NAV`, `SPL`, `EDG`, `STG`, `CTL`,
   `HIS`, `CNV`, `SET`, `PER`, `ACC`, `APP`, `STA`, `GLS`, `BRG`, `ANI`, `SEM`,
   `RSP`, `BRD`, `OM`), criterios de aceptación, paleta del prototipo, mapa de
   iconos y features omitidas con su razón. Es la **fuente de verdad**; el plan
   mapea cada fase a los IDs.

## Decisiones tomadas

- **Reconstruir la UI en Compose nativo**, NO portar la app web ni embeberla.
  El prototipo es spec visual/funcional; el WebView sigue siendo solo el canvas
  3D. Alineado con `AGENTS.md` y con el rendimiento 3D.
- **Extender el contrato `window.SignAI` de forma aditiva** (`setSpeed`,
  `setCharacter`, evento `onPlaybackDone`) para la máquina de estados y la
  velocidad; los 5 comandos actuales y el guard `?.` se mantienen.
- **Gloss LSC como función pura** en `domain/Gloss.kt` (placeholder honesto;
  el motor real está fuera de alcance).
- **Omitir el "home indicator"** del prototipo (duplica la navegación por
  gestos de Android).

## Decisiones resueltas (2026-09-23)

1. **Branding — ABIERTO**: se mantiene "SignAI" por ahora, pero seguramente
   cambie; el wordmark se centraliza para cambiarlo en un solo lugar.
2. **Personajes — UI-only**: picker completo a nivel visual, sin efecto sobre
   el GLB (queda listo para futuros avatares).
3. **Light mode — switch real** dark/light con `LightColors`/`DarkColors` de Lumo.
4. **Settings de debug — sección "Avanzado"** al final del drawer de Ajustes.
5. **Ritmo — fase por fase con `installDebug`**; no arrancar sin OK explícito.

## Pendiente

- **OK del usuario para arrancar la Fase A** (design system extendido). El plan
  está completo y con las decisiones registradas en `tasks.md`.
- El usuario pidió **ver el planning antes de que se ejecute nada** — no se ha
  tocado código de la app (solo se crearon documentos de openspec).
