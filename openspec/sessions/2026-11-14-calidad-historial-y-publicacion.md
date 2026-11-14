# Calidad, historial y publicación

**Día:** 14 de noviembre de 2026 · **Rama:** `main` · **Repositorios:** `signall-android`,
`signall-backend`, `signall-stt`, `signall-docs`

## Qué pidió el owner

1. Sacar del repositorio la carpeta `reestructuracion/`: era material de trabajo y ya no tiene
   por qué vivir en el clon.
2. Correr **gitleaks** y **SonarQube** en los tres repositorios de código y **arreglar todo lo
   que aparezca**, incluida la reescritura del historial si hacía falta.
3. Mostrar el listado de lo que efectivamente se reestructuró.
4. Llevar la **cobertura de pruebas al 80 %** en los tres.
5. Mover las **evidencias del plan de pruebas** a un repositorio, porque estaban fuera de todo
   repositorio.
6. Dejar los **README y los documentos para agentes** con tono público, sin exigir la carpeta
   padre, con licencia y con el proxy TLS como parte del sistema.

## 1. Historia reescrita (solo `signall-android`)

El `.gitleaksignore` enmascaraba dos credenciales reales —contraseña de Neon y clave de
OpenAI— dentro de las evidencias de `PR-CAC-05`. Estaban **rotadas y comprobadas muertas** el
2026-11-13 (Neon: `password authentication failed`; OpenAI: HTTP 401), pero seguían en la
historia.

Como el repositorio va a ser público, se reescribió **antes** de publicarlo, que es el único
momento barato:

```bash
git filter-repo --invert-paths \
  --path docs/plan-pruebas/evidencias \
  --path docs/plan-pruebas/audios \
  --path docs/evidencias
```

- `.git`: **334 MB → 65 MB**.
- Desaparecieron de todos los commits las dos credenciales y 1 636 archivos de evidencia.
- Los **79 hashes citados en 34 documentos** se remapearon a la historia nueva; el mapa completo
  quedó en `docs/historia/commit-map-2026-11-14.txt`.
- Se verificó que la credencial no aparece en ningún objeto del repositorio
  (`git rev-list --objects --all | git cat-file --batch | grep -c npg_…` → 0).
- **Costo explícito:** cualquiera con un clon anterior tiene que rehacerlo o hacer
  `git fetch && git reset --hard origin/main`, y todos los `origin/*` se reescribieron con
  `--force`. Fue autorizado por el owner en la misma sesión.

## 2. Las evidencias pasaron a `signall-docs`

Estaban en `../data/evidencias/`, fuera de todo repositorio, desde el 2026-11-13. El owner pidió
guardarlas versionadas.

- 240 MB y 1 636 archivos, en `signall-docs/evidencias/` (`plan-pruebas/` y `avatar/`).
- Los dos JSON de `PR-CAC-05` guardaban el `.env` del backend: sus valores quedaron como
  `[REDACTADA-2026-11-13]` antes de versionarlos. El resto del volcado quedó intacto.
- `.gitleaks.toml` en `signall-docs` deja pasar los nonces de `Sec-WebSocket-Key` y el `GPG_KEY`
  de la imagen oficial de Python, que no son secretos.
- Las rutas de `signall-android` (`INDICE-EVIDENCIAS.md`, los arneses de `docs/plan-pruebas/`,
  `TRANSICIONES-REPOSO.md`, `PLAN-AVATARES-Y-MARCADORES.md`) pasaron de `../data/evidencias/` a
  `../signall-docs/evidencias/`.

## 3. gitleaks

`tools/calidad/leaks.sh` (antes en `reestructuracion/herramientas/`) corre sobre la historia
completa de los **cinco** repositorios:

| Repositorio | Antes | Ahora |
|---|---|---|
| `signall-android` | 9 hallazgos (2 credenciales reales, 7 falsos positivos) | **0** |
| `signall-backend`, `signall-stt`, `signall-docs`, `signall-avatar` | 0 | **0** |

El `.gitleaksignore` quedó con un solo falso positivo, en dos formas porque `gitleaks git` y
`gitleaks dir` numeran la línea distinto: `LIVE_KEY_PERMANENT`, que es el nombre de un morph
target del avatar. `.gitleaks.toml` excluye los `.glb` y `app/build/`, que son binarios y copias
generadas. El CI pasó a escanear **la historia**, no solo el árbol, porque ya está limpia.

## 4. SonarQube

| Proyecto | Code smells antes | Ahora | Cobertura |
|---|---|---|---|
| `signall-android` | 63 | **0** | **81,3 %** (Sonar, sin el design system) |
| `signall-backend` | 6 | **0** | 0 % → **82,8 %** |
| `signall-stt` | 5 | **0** | **90,0 %** |

Los "bugs" que había eran uno real (una condición duplicada en `requestTranslation`, ya
corregida) y uno falso (un generador asíncrono que el analizador no leía).

Lo que se cambió, en grande:

- **Complejidad cognitiva**: se extrajeron funciones con nombre en `MainScreenState` (eventos del
  backend y entrega de la cola), `MainScreen`, `SignAllBottomBar`, `EdgeDrawer`, `AvatarWebView`,
  `AudioCapture`, `CharacterPicker`, `ConversationPanel`, `TextFieldLayout` y los cinco
  segmentadores del backend. La peor era `EdgeDrawer` con 92 de complejidad; quedó muy por debajo
  de 15.
- **Funciones con más de 7 parámetros**: se agruparon en `data class` por componente
  (`SurfaceStyle`, `ButtonAppearance`, `IconButtonAppearance`, `SwitchAppearance`,
  `TextFieldOptions/Decorations/Visuals/Input`, `DrawerConfig/DrawerOpenState/DrawerHandlers`,
  `BottomBarInput/Availability/Actions`, `SettingsActions`, `StageState`, `StepperConfig`,
  `AvatarBridgeCallbacks`…) y se actualizaron **63 call sites**.
- Literales duplicados, `if` anidados y código comentado: corregidos.

**Los avisos de "TODO" eran falsos positivos**: la regla matchea la palabra española «todo»
en los comentarios. Se cerraron como tales en el servidor.

**Segunda pasada (mismo día).** El análisis siguiente volvió a abrir los mismos 15 falsos
positivos, porque cerrarlos a mano no sobrevive a un análisis nuevo. La solución quedó en
`sonar-project.properties`: un `sonar.issue.ignore.multicriteria` que excluye `kotlin:S1135` para
todos los `.kt`, con la comprobación de que no hay ningún `TODO`/`FIXME` real
(`grep -rnE "\b(TODO|FIXME|XXX|HACK)\b" app/src/main/java` no devuelve nada). Esa pasada
encontró además cinco avisos reales que sí se corrigieron: la complejidad del `EdgeDrawer` (47, ya
por debajo de 15), la de `MainScreen` (16), dos parámetros `loading` que no hacían nada en
`Button`/`IconButton` y un parámetro `scene` que `BottomBarLayer` no usaba.

## 5. Cobertura

Ninguno de los tres repositorios tenía una sola prueba.

| Proyecto | Antes | Ahora | Base de medida |
|---|---|---|---|
| `signall-backend` | 0 % | **82,8 %** | `app/` |
| `signall-stt` | 0 % | **90,0 %** | `app/` |
| `signall-android` | 0 % | **92,2 %** (221 pruebas) | código propio, **sin** `ui/designsystem/` |

En el backend la cobertura se genera con `scripts/cobertura.sh` sobre los 28 scripts
`verify_*.py` (seis necesitan Neon o el LLM y se saltean con el motivo impreso). En el STT se
agregaron pruebas de API con `TestClient`, de las métricas y del planificador.

En Android se sumó JUnit, Robolectric, Mockito, `MockWebServer` y JaCoCo (`./gradlew test`,
`./gradlew jacocoTestReport`). **El design system de Lumo (`ui/designsystem/`) se excluye del
denominador**: son 4 759 de las 8 099 líneas ejecutables y es código copiado y adaptado, no
lógica de la app. Es una decisión del owner del 2026-11-14 y está escrita en
`sonar-project.properties`. Son **221 pruebas** y **3 087 de 3 350 líneas** del código propio
(92,2 %): la máquina de estados, los dos repositorios, el cliente del WebSocket contra
`MockWebServer`, el catálogo, el contrato con el backend y una prueba por pantalla y por
componente con Robolectric. Lo que queda flojo está en
`openspec/sessions/2026-11-14-cobertura-app-80.md`.

`./gradlew build` corre la suite **solo en `debug`**: las pruebas de composables necesitan el
`ComponentActivity` que declara `ui-test-manifest`, que es `debugImplementation`.

**Verificado en el emulador `VMApp_API36`** (2026-11-14): la frase «buenos dias» llegó al
backend, volvió como `segment 0/1 -> BUENOS DIAS`, se representó en 2 963 ms y los dos paneles
laterales abrían y cerraban. El micrófono grabó a 16 kHz y un `final` vacío se trató como «No se
escuchó nada», no como error. Evidencias en `signall-docs/evidencias/verificacion-app/`.

## 6. Documentación pública y licencia

- Los tres README se reescribieron con la misma estructura (qué es, enfoque, cómo se usa,
  requisitos, estructura, cómo se verifica, licencia) y ya no exigen la carpeta padre: los
  hermanos se nombran por nombre y URL.
- `AGENTS.md` (android) y `CLAUDE.md` (backend, STT y docs) se reescribieron con la misma idea y
  se agregó, como **estándar**, que cada cambio no trivial se documenta en `signall-docs/cambios/`.
  El de `signall-stt` es nuevo: ese repositorio no tenía documento de agentes y solo el README
  nombraba a los hermanos.
- Los cuatro documentos dicen que los repositorios hermanos son **fuentes de información** y que
  `signall-docs` manda para lo escrito.
- El **proxy TLS dejó de ser un extra**: el sistema se levanta con
  `docker compose -f docker-compose.yml -f docker-compose.host.yml up -d`, y hay
  `signall-backend/scripts/verify_proxy.sh` que comprueba certificado, contenedor y respuesta y
  falla con código 1. Probado en los dos sentidos.
- **Licencia**: el owner pidió que la confirme la Pontificia Universidad Javeriana. Mientras
  tanto los cuatro repositorios tienen un `LICENSE` que dice que no se concede ningún permiso y
  los README apuntan a él. Se reemplaza por el texto definitivo cuando la universidad responda.

## Decisiones y de quién salieron

| Decisión | Origen |
|---|---|
| Reescribir la historia ahora, antes de publicar | owner, en esta sesión |
| Medir el 80 % de cobertura sobre el código propio, sin el design system | owner, en esta sesión |
| Las evidencias van versionadas en `signall-docs` | owner, en esta sesión |
| La licencia la confirma la universidad | owner, en esta sesión |
| `tools/calidad/` conserva los dos scripts de escaneo; el resto de `reestructuracion/` se borra | owner, en esta sesión |

## Pendiente

1. **`signall-backend/AGENTS.md` no está versionado** (`.git/info/exclude`, decisión previa del
   owner). `CLAUDE.md` es el documento que viaja.
2. ~~Cuatro `verify_*.py` del backend~~: **resueltos**. Los cuatro esperaban el contrato anterior
   al frame `capability` (`8a31cc2`) y al orden no determinista entre el eco de voz y los
   `segment` (`8631115`). Se adaptaron los scripts, no el backend: hoy los cuatro terminan en
   `all OK`. Detalle en `signall-docs/cambios/verificacion-contrato-backend-2026-11-14.md`.
3. **`app/main.py` del STT y `app/metrics.py`** tienen partes sin cubrir que dependen de GPU o de
   servicios externos; está anotado en su bitácora.
4. **La licencia** queda a la espera de la universidad.
