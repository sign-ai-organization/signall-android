# Ola 0 de la reestructuración: cimientos de SignAll

**Fecha:** 2026-11-13, noche · **Rama:** `main`

## Qué llegó

El owner pidió ejecutar la ola 0 del plan de `reestructuracion/00-general.md`: R-02 y R-03
como un solo hilo, después R-04, R-05 y R-07. A mitad de la sesión renombró las cinco
carpetas locales a `signall-*` (dos `l`, sin guion) y pidió migrar OpenCode.

## Qué se hizo

| Commit | Qué |
|---|---|
| `cf9fb5b` | R-02 + R-03: paquete `com.signall.app`, 75 archivos movidos con `git mv`, toda la familia `SignAi*`/`SignAI*` → `SignAll*` |
| `1dcefb0` | Rutas a los hermanos `../signall-*` en 119 archivos, incluidos `.opencode/` y las bitácoras |
| `52e0698` | R-04: `reestructuracion/03-contrato.md`, la tabla del contrato y el veredicto |
| `97a0eec` | R-05: evidencias fuera del árbol, `docs/historia/`, índices nuevos |
| `a61dfb7` | R-07: `sonar-project.properties`, `.gitleaksignore` y CI |
| `e66356d` | OpenCode migrado y bitácora de la ola 0 |
| `c51443e` | Referencias a los documentos que pasaron a `docs/historia/` |

En los hermanos: `signall-backend` `da38a77` (constancia de R-04) y `43ada1f` (CI y Sonar);
`signall-stt` `429019e` (CI y Sonar).

## Qué se decidió

- **Se renombra toda la familia `SignAi*`**, no solo los cuatro ejemplos del plan. Son los
  mismos 76 archivos de R-02 y dejar media marca adentro era peor. Lo decidió el owner.
- **Los `rt_*` y los frames no se tocan.** El cable no lleva la marca —los frames son
  `text`, `end`, `transcript`, `segment`, `done`, `error`, `capability`— y renombrar una
  métrica rompe la comparación con la línea base publicada. Lo decidió el owner.
- **Las evidencias van a `../data/evidencias/`**, que no es repositorio, igual que
  `../material-3d/`. Lo decidió el owner.
- **Se commitea local en los hermanos, sin push**, con la orden explícita del owner para
  esta sesión.
- **El nombre es `signall-*`**, corrección del owner sobre el `sign-all-*` que decía el
  plan.
- **No se desinstaló la app vieja** (`com.signai.webview`): borrarla se lleva el historial
  y los ajustes del owner. Las dos conviven.

## Qué se midió

En el Redmi Note 9S, app `com.signall.app` recién instalada, backend en Docker y
`adb reverse tcp:8080 tcp:8080`, con **una** frase:

| Qué | Valor | De dónde |
|---|---|---|
| Escena lista (Juan) | 3 307 ms | `SignAllWeb`, log de la escena |
| Frase «Hola, como estas?» | 1 segmento, 2 glosas | `SignAllNet` |
| frase #1 | 2 304 ms | `SignAllMetrics` |
| envío→primera seña | 2 308 ms | `SignAllMetrics` |
| oración→avatar | 163 ms | `SignAllMetrics` |
| representado | 2 señas en 4 673 ms | `SignAllMetrics` |

Son de una frase: comprueban que el camino funciona, **no** son medición de latencia. Esa
necesita las 28 frases y sigue pendiente.

`docs/` pasó de 246 MB y 1 736 archivos a **5,6 MB y 99**. SonarQube `signall-android`
salió idéntico a la línea base: 13 806 ncloc, 63 code smells, 1 bug, 4,8 % de duplicación.

## Qué quedó pendiente

- **Índice de esta bitácora**: la línea en `openspec/sessions/README.md` no se agregó
  porque ese archivo tiene trabajo sin commitear del owner (la entrada del video). Cuando
  él commitee lo suyo, hay que agregarla.
- **Rotar** la contraseña de Neon y la clave de OpenAI: salieron del árbol con R-05 pero
  siguen en la historia de git (ver `reestructuracion/01-leaks.md`).
- **Borrar los proyectos viejos** `sign-all-*` de SonarQube: los nuevos son `signall-*`.
- **Las URLs de GitHub** siguen siendo `sign-all-*` en los cinco `origin`.
- **CI de `signall-docs`**: el plan se contradice (§ 4 "no se toca por dentro" contra § 5
  "los cuatro"). No se creó; lo decide el owner.
- **Prompts de R-03, R-04, R-05 y R-07**: no existían y los bloques se corrieron con el
  alcance del § 5. Escribirlos queda para quien repita el trabajo.
- Nombres internos del **backend** y del **STT**: son su bloque, no el contrato.

---

# Segunda parte: ola 1 y los nombres de los hermanos

**Misma fecha, después de la ola 0.** El owner pidió seguir con la ola 1 y con el trabajo
0/1 que la ola 0 dejó permitido en paralelo: los nombres internos del backend y del STT.

## Qué se hizo

| Repo | Commit | Qué |
|---|---|---|
| `signall-backend` | `15c6a4e` | Rutas `../sign-ai-*` → `../signall-*` y marca en README y `CLAUDE.md` |
| `signall-stt` | `7359e7e` | Lo mismo, más el título de la app FastAPI y dos rutas muertas de sus bitácoras |
| `signall-android` | `a7746a5` | R-06: README de 325 a 79 líneas y `docs/ARRANQUE.md` con el detalle |
| `signall-backend` | `7d8585b` | R-06: README corto |
| `signall-stt` | `d5e5a89` | R-06: README corto y `docs/API-Y-CONFIGURACION.md` |

## El hallazgo que importaba

El `docker-compose.yml` del backend compilaba el STT desde `../sign-ai-stt`. Después del
renombre de carpetas esa ruta **ya no existía**, así que `docker compose up --build` fallaba
o usaba otra cosa. Ahora apunta a `../signall-stt` y se verificó con `docker compose config`
(sale 0) más la existencia de `../signall-stt/Dockerfile`.

Es el tipo de ruta que no se nota hasta que alguien clona de cero: en esta máquina los
contenedores estaban levantados desde antes del renombre.

## Qué se decidió

- **R-06 en tres repos, no en cinco.** El § 4 del plan prohíbe tocar `signall-docs` y
  `signall-avatar` por dentro, y el § 5 pedía un README en los cinco. El owner eligió los
  tres de código. Los otros dos quedan pendientes con la razón escrita en el plan.
- **La marca no se toca en `docs/sdd.md` ni en las bitácoras.** El SDD es el documento
  formal y sigue al nombre de la tesis, que es una decisión académica aparte. Lo mismo con
  los informes de una fecha: se les corrigieron las rutas muertas, no la prosa.
- **`.cache/signai-piper` se queda como está.** Es el directorio de caché local de Piper en
  el script de fixtures; renombrarlo obliga a bajar la voz otra vez y no se ve desde afuera.

## Qué se verificó

| Qué | Resultado |
|---|---|
| `./gradlew build` en android | en verde |
| `./gradlew installDebug` | instalada en el Redmi (`lastUpdateTime` 2026-11-13 21:10) |
| `docker compose config` en el backend | sale 0 |
| `python -m compileall` en el backend y en el STT | sin errores |
| Enlaces relativos de los cuatro README/documentos nuevos | todos resuelven |

## Qué sigue pendiente

Lo de la primera parte (rotar credenciales, push, Sonar viejo, URLs de GitHub, CI de
`signall-docs`), más:

- **README de `signall-docs` y `signall-avatar`**, si el owner levanta la regla del § 4.
- **La marca en los documentos de trabajo del backend** (`docs/plan-wifi.md`,
  `docs/performance.md`, `docs/decisions.md`, `docs/sdd-changes.md`): siguen diciendo
  SignAI. No se tocaron porque son documentos citados en la tesis y el SDD manda.
- **La ola 2** no se puede cerrar sin las credenciales rotadas y sin push.
