
---
name: android-performance-best-practices
description: Performance en Android — arranque, memoria, batería, tamaño de app, Baseline Profiles y jank de Compose. Usar cuando la app se sienta lenta, cuando haya que medir jank o latencia de toque, o en una revisión de performance.
metadata:
  opencode/slash: "true"
---

# Performance en Android

Checklist genérica. **Lo específico de SignAll está en el skill `compose-best-practices`** y
en `tools/rendimiento/`.

## Las dos reglas que acá importan más

1. **Medir en build release, nunca en debug.** En debug no hay R8 y el número no extrapala.
2. **El presupuesto de GPU lo tiene el WebView 3D, no Compose.** `renderer.setPixelRatio`
   está capado en 1.75 a propósito: sin cap, el Redmi Note 9S renderiza 19,6 MP/frame y el
   GPU queda en ~37 ms/frame con 99 % de jank. **Cualquier animación de Compose se mide
   contra ese presupuesto**, no al revés.

## Hard Rules
- TTID (Time To Initial Display) < 2 seconds.
- TTFD (Time To Full Display) < 4 seconds.
- No heavy initialization on the main thread during Application.onCreate() or Activity.onCreate().
- Use App Startup library instead of ContentProvider-based init.
- Generate and ship Baseline Profiles for critical user journeys.
- No static references to Activity or Context — causes memory leaks.
- Enable StrictMode in debug builds.
- Enable R8 + shrinkResources for release builds.
- Use WebP for raster assets, vector drawables where possible.
- Batch network requests to respect Doze mode.

## Core Patterns
- Call reportFullyDrawn() when content is ready.
- Defer non-critical init with lazy or background threads.
- Use Macrobenchmark for startup and scroll performance measurement.
- Monitor memory with LeakCanary in debug builds.
- Use Android Vitals / Firebase Performance for production monitoring.
- Prefer WindowInsets over deprecated system UI flags.

## Qué hay que mirar en este proyecto

- **El splash no es decorativo**: aparece hasta `avatarReady` del bridge, no hasta
  `onPageFinished`, porque el GLB de 10 MB todavía se está bajando. Hay un timeout de 8 s
  como red de seguridad.
- **No pongas `DoubleSide` en las mallas**: es overdraw puro en mallas cerradas y costó 4 %
  más de jank para un cambio visual cero.
- **Herramientas**: `tools/rendimiento/gfxinfo.py` (reparto por fotograma y latencia de
  toque), `sf-medir.sh` (cuántos fotogramas llegaron a pantalla), `frames.py` (duración de
  la animación y repintado en reposo), `perfetto.sh` (trace, para atribuir cuando `gfxinfo`
  ya dijo que hay jank).

## References
- references/app_startup.md
- references/baseline_profiles.md
- references/memory_management.md
- references/battery_optimization.md
- references/strict_mode.md
- references/app_size.md
- references/compose_performance.md
- references/tracing.md
