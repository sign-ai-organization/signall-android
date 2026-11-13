
---
name: android-accessibility-best-practices
description: Accesibilidad en Android — descripciones de contenido, TalkBack, objetivos táctiles, contraste y semántica de Compose. Usar al revisar accesibilidad de la UI, cuando un control no se anuncia bien, cuando un color es la única señal, o en una revisión de UI.
metadata:
  opencode/slash: "true"
---

# Accesibilidad en Android

Checklist genérica, aplicable a cualquier Compose. **Lo específico de SignAll está en el
skill `compose-best-practices`** (los tokens, los drawers, el WebView) y en
`references/design_tokens.md` (los ratios de contraste reales).

**Por qué acá no es decorativa**: la app es para un público con LSC. Un control que no se
anuncia no existe para parte de quien la usa.

## Hard Rules
- Provide contentDescription on all interactive non-text elements.
- Set contentDescription = null on purely decorative images.
- Minimum touch target size: 48dp × 48dp.
- Meet WCAG AA contrast ratios: 4.5:1 for normal text, 3:1 for large text.
- **Never convey information by color alone** — add icons, patterns, or labels. En esta app
  el caso concreto es el ámbar `live`: va con cambio de etiqueta o de
  `stateDescription`, nunca solo el color.
- Ensure logical TalkBack reading order matches visual layout.
- Apply correct semantics (Role, stateDescription) on custom composables.
- Time-based content must be pausable or extendable.
- All form fields must have associated labels.
- Use sp (not dp) for text sizes to respect user font scaling.

## Core Patterns
- Use Modifier.semantics {} to expose custom component meaning.
- Use mergeDescendants to group related elements for TalkBack.
- Test with TalkBack enabled on a real device — `tools/dev.sh launch`, después el árbol de
  accesibilidad.
- Run Accessibility Scanner for automated checks.
- Support dynamic font sizes — test at maximum font scale. Ojo: la app tiene además su
  propio escalado (`fontScale` y `uiScale` en Ajustes → ACCESIBILIDAD), que hay que probar
  por separado del escalado del sistema.
- Verify dark theme meets contrast requirements separately.

## Lo que no se puede verificar con estas herramientas

**El avatar 3D no existe para la accesibilidad**: sale en el árbol como
`android.webkit.WebView` **sin ningún hijo**, porque el canvas de Three.js no publica
nada. No se puede tocar ni leer. Para el estado de la escena: el tag `SignAllWeb` de
logcat, o el modo debug de Ajustes → AVANZADO.

## References
- references/content_descriptions.md
- references/talkback_navigation.md
- references/touch_targets.md
- references/color_contrast.md
- references/compose_semantics.md
- references/automated_testing.md
