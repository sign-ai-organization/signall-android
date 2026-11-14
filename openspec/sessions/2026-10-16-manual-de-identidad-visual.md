# Manual de identidad visual: la paleta grafito y el ámbar de captura

**Fecha:** 2026-10-16, noche · **Rama:** `flujo-oraciones` ·
**Autoría del cambio de paleta:** Samuel Osorio (`e6b0280`)

## Qué llegó

`docs/manual-identidad-visual.md`, con su PDF para compartir, las capturas de
antes/después en oscuro y los dos modos, y el icono. Es la referencia de marca
de la app: dice qué valor va dónde, cuánto contraste da cada uno, cómo se usa el
logotipo y qué no se hace.

## El cambio

La app se veía demasiado azul porque el fondo, las superficies y los bordes eran
todos navy. Ahora el azul de marca queda solo en la acción principal y los
controles, el resto es grafito neutro, y un ámbar marca cuando la app está
escuchando.

| | Antes | Ahora |
|---|---|---|
| Fondo | `#0C142C` navy | `#121316` grafito |
| Superficie / borde | `#141D3A` / `#263463` | `#1A1B1F` / `#33363D` |
| Mic y TR encendidos | Azul | Ámbar `#F2B84B` (tokens `live`/`onLive`) |
| Traducir, interruptores, slider | Azul `#1C4CFC` | Sin cambio |
| Acción principal en claro | Negro `#171717` | Azul `#1C4CFC` |

## Verificado contra el código (esta sesión)

Los dieciséis valores del manual existen y coinciden con `ui/designsystem/Color.kt`,
paleta clara y oscura. Los tres puntos donde el manual dice que vive cada cosa
también:

- **Canvas 3D**: `app.js` `scene.background` y `BG_COLOR` en `0x121316`, grilla en
  `0x25272c` (= `soft`), e `index.html` en `#121316`. El canvas es la superficie
  más grande de la app, así que si se hubiera dejado atrás el manual se vería
  solo en los controles.
- **`live`/`onLive`**: `SignAiBottomBar` los usa en los dos interruptores
  (micrófono y TR). Es lo único que significa "escuchando ahora".
- **Accesibilidad**: el ámbar **no es la única señal**, y el manual lo promete.
  Los dos controles cambian su `stateDescription` con el estado
  ("Encendido. Cada frase se representa sola al terminar." contra "Apagado. El
  micrófono está dictando."), así que un lector de pantalla no depende del color.

**Veredicto: el manual está aplicado.** No quedó nada pendiente de aplicar.

## Lo que faltaba y se agregó

El log de sesión que ese cambio no dejó, y su línea en el índice.

Una corrección sobre lo que parecía faltar: `docs/marca/logo-signall.png`, la
imagen con la que abre el manual, **ya estaba commiteada** (mismo md5 que el
master `Logo-SingALL.png` de la raíz). El listado de archivos del directorio
hizo pensar que faltaba; `git ls-files` y `git cat-file` dijeron lo contrario. Los
dos assets de `marca/` están.

## Documentación que todavía describía la paleta anterior

- `AGENTS.md` § UI direction: el bullet de dark seguía con los seis valores navy,
  y el de light decía que `strong` era `#171717`.
- `openspec/specs/ui/spec.md`: `APP-2` pedía la paleta navy. Ahora pide la de
  grafito, y se agregó `APP-2b` para el ámbar de captura (no existía ningún ID
  que lo nombrara) y `live`/`onLive` entraron en la lista de tokens de `APP-4`.
- Comentarios que quedaron viejos: `app.js` (el del fondo y el de la grilla) y
  el KDoc de `SignAiBranding.logo()`.

**`2026-10-16-marca-signall.md` no se tocó.** Es el log de la mañana: su navy es
historia y los logs de sesión no se reescriben. Los valores que mandan hoy están
en el manual y en la tabla de `Color.kt`.

## Un número que parece contradicción y no lo es

El manual dice que el azul `#1C4CFC` da **2,86:1** sobre grafito; `Color.kt` dice
**3,09:1**. Los dos están bien y miden contra fondos distintos: 2,86:1 es contra
`surface #1A1B1F`, que es donde de verdad va un trazo fino, y 3,09:1 contra
`background #121316`. Recalculado a mano para confirmarlo.

**No "corregir" el manual sin decir contra qué fondo está medido**, que es
justamente el error que hace que un número correcto parezca equivocado.

Del mismo modo: si el manual cambia, hay que **regenerar el PDF**. Hoy
`manual-identidad-visual.md` y `manual-identidad-visual.pdf` son dos versiones
del mismo documento y no se actualizan solas.

## Lo que el manual declara y no tiene captura

El ámbar de micrófono y TR encendido. Aparece con el micrófono prendido, y hace
falta el servidor de traducción corriendo para verlo.