# Plugins y skills de Android para el agente

**Día:** martes
**Fecha:** 2026-10-13
**Hora:** 23:40

## Qué se hizo

Se sacó el plugin `@dietrichgebert/ponytail` de `opencode.json` y se eligieron
sustitutos que encajen con lo que este repo hace realmente: Compose + WebView con
Three.js, builds de Gradle largos, y verificación en device.

### `opencode.json`

```jsonc
"plugin": ["opencode-pty"],
```

`ponytail` estaba solo en la config de este proyecto (no estaba en
`~/.config/opencode`), así que quitar la línea fue toda la limpieza.

### Archivos nuevos

| Archivo | Qué es |
|---|---|
| `.opencode/skills/compose-best-practices/` | Skill de Compose (del pack `android-ai-skills` 1.7.0) |
| `.opencode/skills/android-performance-best-practices/` | Skill de performance Android |
| `.opencode/skills/android-accessibility-best-practices/` | Skill de accesibilidad (Compose semantics, talkback, touch targets) |

Las tres se copiaron del tarball de npm al `.opencode/skills/` **a mano**, con
`npm pack` + `tar`. No se corrió `npx android-ai-skills init`, porque ese comando
no deja elegir skills individuales: `--android-only` instala 21 de las 23 y
`--compose-mp-only` una sola. Las 3 copiadas a mano son las únicas que no
contradicen este repo.

## Lo que se decidió

- **`opencode-pty` primero, no Context7.** Es el que más rinde acá. `AGENTS.md`
  obliga a correr `./gradlew installDebug` después de cada cambio y el build es
  de minutos: con el `bash` tool sincrónico el agente queda clavado esperando. El
  mismo motivo sirve para `adb logcat -s SignAIWeb` y `tools/rendimiento/perfetto.sh`,
  que son procesos largos de streaming — y son, según los § Gotchas, las **únicas**
  formas de leer el estado de la escena 3D. `notifyOnExit` evita el polling del build.
  No choca con los permisos: el global ya es `allow: *`, que es lo que pide el
  plugin (trata `ask` como `deny`).
- **Del pack de skills se tomó un tercio.** Las skills de DI / navegación /
  arquitectura asumen MVVM + Hilt + ViewModel, y este repo por diseño **no tiene
  ViewModel ni DI** (`MainScreenState` se construye en `MainActivity` y se pasa
  hacia abajo). Instalar el pack entero metía al agente a sugerir un
  `ViewModel` que acá no existe.

## Lo que se descartó y por qué

| Candidato | Motivo |
|---|---|
| `opencode-android-tdd` | Pone una compuerta en **código** que impide escribir producción sin un test fallando primero. `AGENTS.md` dice "No tests, no CI, no lint". Contraproducente. |
| `figma-to-android` (de `android-claude-code-skills`) | Prohíbe Jetpack Compose explícitamente y emite XML + ViewBinding. Es lo contrario de este repo. |
| `oh-my-opencode` / `-slim` | Capas grandes de orquestación de agentes. `AGENTS.md` ya define el workflow con precisión (orden de capas, reglas de `EdgeDrawer`, cuándo instalar) y compite con esas instrucciones. |
| `@tarquinen/opencode-dcp` | Tentador porque las sesiones de device tiran cientos de líneas de logcat, pero es AGPL-3.0, su autor ahora recomienda Sleev (un proxy aparte), y podar contexto es justo lo que borra los detalles de `openspec/` y los gotchas que hacen que el proyecto funcione. |
| Wakatime y similares | Métricas de uso; nada que ver con desarrollar la app. |
| `@upstash/context7-opencode` | **Se instaló y al rato se sacó.** Ver abajo. |

## Context7: instalado y removido en la misma sesión

Se evaluó `@upstash/context7-opencode` porque cubría bien el stack — contra su
API de búsqueda se verificó Jetpack Compose (74.6), Three.js (82.9), Android
Developers y WebView, que es exactamente lo que usa la app. Se instaló.

**A los minutos pidió un login**, y resultó que el plugin registra el servidor
**remoto alojado** (`https://mcp.context7.com/mcp`), que autentica por OAuth por
omisión y abre el navegador en la primera consulta de documentación. Se decidió
sacarlo y dejar `opencode-pty` solo.

**El error fue de verificación, no del plugin.** Se comprobó *qué* indexa
Context7 usando su API de búsqueda — que es anónima — y se dio por hecho que el
MCP entraba igual. Nunca se miró el requisito de auth. Cuando se revisó el README
del paquete ya instalado estaba escrito: *"On the first documentation lookup,
OpenCode opens a browser window so you can log in to Context7 via OAuth"*.

### Si vuelve a hacer falta

Las dos vías, para no volver a tropezar:

- **Login OAuth** — gratis, 1.000 llamadas/mes. Corta a las 1.000 y quedan 20
  por día hasta el reset.
- **API key** — clave `ctx7sk` del dashboard, que el plugin levanta sola de
  `CONTEXT7_API_KEY` en el entorno y manda como `Authorization` en vez de hacer
  OAuth. **Va en `~/.zshrc`, nunca en `opencode.json`**: ese archivo está
  versionado y ahí la clave queda commiteada.

## Verificado

- `opencode.json` sigue siendo JSON válido tras las ediciones.
- OpenCode resolvió y descargó `opencode-pty` a
  `~/.cache/opencode/npm/opencode-pty@latest`.
- Al quitar Context7, su skill `context7-mcp` desapareció de la sesión.

## Pendiente

- **Reiniciar OpenCode para que queden expuestos los tools de `opencode-pty`**
  (`pty_spawn`, `pty_read`, `pty_write`, `pty_list`, `pty_kill`). En la sesión en
  la que se instalaron todavía no figuraban: el catálogo de tools viene
  snapshotteado al arrancar.
- **Probar los 3 skills nuevos contra una tarea real** antes de darlos por buenos.
  En particular `compose-best-practices` puede tener reglas de Material 3 que
  choquen con la migración a Lumo que está en curso en `AGENTS.md` (§ UI direction).