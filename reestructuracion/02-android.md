# 02 · Android — ola 0

Ejecutado el **2026-11-13** sobre `signall-android`, en el orden del § 5 del
[plan general](00-general.md): R-02 + R-03 en un solo hilo, después R-04, R-05 y R-07.
La migración de OpenCode entró en el mismo trabajo porque el owner renombró las carpetas
locales a `signall-*` en medio de la sesión.

## Qué se hizo, bloque por bloque

| Bloque | Commit | Qué |
|---|---|---|
| R-02 + R-03 | `cf9fb5b` | Paquete e identificadores a SignAll |
| Rutas de hermanos | `39d6a82` | `../sign-ai-*` y `../signai-avatar` → `../signall-*` |
| R-04 | `9bac1f7` | El contrato no lleva la marca: verificación y tabla |
| R-05 | `14b2c24` | Evidencias fuera del árbol y `docs/historia/` |
| R-07 | `8f90f1d` | `sonar-project.properties`, `.gitleaksignore` y CI |

### R-02 · paquete

- `namespace` y `applicationId`: `com.signai.webview` → `com.signall.app`.
- Paquete Kotlin movido con `git mv`: `app/src/main/java/com/signai/webview/` →
  `app/src/main/java/com/signall/app/`, **75 archivos `.kt`**. La historia de cada archivo
  se conserva.
- `rootProject.name`: `SignAI-WebView` → `SignAll`.
- `lumo.properties`: `ThemeName` y `ComponentsDir`/`PackageName` con los valores nuevos.
- Referencias que rompían si quedaban viejas: `tools/dev.sh` (`APP_PKG`),
  `tools/maestro/*.yaml` (`appId`), `tools/verificar-tiempo-real.sh` (`PKG`),
  `tools/split_signs.py` (el `package` que genera), `tools/README.md`,
  `tools/TIEMPO-REAL.md`, `tools/HERRAMIENTAS.md`, `AGENTS.md`, `README.md`,
  `openspec/config.yaml` y `openspec/specs/ui/spec.md`.
- **No** se reescribieron los informes ni las evidencias de `docs/plan-pruebas/`: dicen lo
  que se midió con el paquete que existía ese día.

Consecuencia dicha al owner: para el teléfono y para Play **es otra app**. No actualiza la
instalada, convive con ella, hay que conceder `RECORD_AUDIO` otra vez y el historial y los
ajustes viejos no pasan a la nueva.

### R-03 · identificadores internos

Se renombró **toda** la familia `SignAi*`/`SignAI*` → `SignAll*`, no solo los cuatro
ejemplos del plan: ~880 usos en 76 archivos y 19 archivos renombrados con `git mv`.

| Antes | Ahora |
|---|---|
| `window.SignAI` (objeto de la escena) | `window.SignAll` |
| `SignAIBridge` | `SignAllBridge` |
| tag `SignAIWeb` | `SignAllWeb` |
| tag `SignAiNet` | `SignAllNet` |
| tag `SignAiMetrics` | `SignAllMetrics` |
| `SignAiState`, `SignAiCommands`, `SignAiTheme`, `SignAiSizes`, … | `SignAll*` |
| `SharedPreferences "signai"` | `"signall"` |
| `-Psignai.backendWsUrl`, `-Psignai.devTools` | `-Psignall.*` |
| `SIGNAI_DEV_TOOLS`, `SIGNAI_STT_*`, `SIGNAI_BACKEND_URL` | `SIGNALL_*` |

Los dos lados del puente quedaron consistentes: `app.js` define `window.SignAll` y usa
`window.SignAllBridge`, y `AvatarWebView` registra el objeto con el mismo nombre.

**No se tocó** ningún identificador del contrato ni de los datos: `signs/<id>.glb`, los
clips `0000_afirmativo`..`0049_orden`, las glosas `GLS-*`, los frames del WebSocket y los
nombres `rt_*`. El detalle y el porqué están en [03-contrato.md](03-contrato.md).

### Migración de OpenCode

- `.opencode/agents/*.md` y `.opencode/skills/**`: rutas a los hermanos, glob de permisos
  del paquete (`app/src/main/java/com/signall/app/domain/*`), `appId` en los comandos y
  una ruta absoluta vieja que se cambió por `$(git rev-parse --show-toplevel)`.
- Se corrigió una referencia rota que dejó el rename mecánico: el parser se llama
  `SignAllWebViewController`, no `WebViewSignAllController`.
- La configuración global (`~/.config/opencode`) no tenía rutas del proyecto y no se tocó.

### R-05 · documentos

| Antes | Después |
|---|---|
| `docs/` 246 MB, 1 736 archivos | **5,6 MB, 99 archivos** |
| `docs/plan-pruebas/` 210 MB | 1,2 MB: índice, metodología, arnés y plantilla |
| `docs/evidencias/` 32 MB | fuera del árbol |

- Las evidencias, los audios, los libros y las vistas previas están en
  `../data/evidencias/plan-pruebas/`; las del avatar, en `../data/evidencias/avatar/`.
  `../data/` no es repositorio, igual que `../material-3d/`.
- `docs/historia/` reúne los documentos cerrados (`PLAN-SEMANA-13.md`,
  `PENDIENTES-AUDITORIA-2026-10-24.md`, `REVISION-DEVELOP.md`, los `CONTRASTE-*.md`, el
  `REPORTE-2026-11-03-*` y la preparación de ajustes de animación) y, en
  `docs/historia/plan-pruebas/`, los reportes de las rondas.
- Índices nuevos: `docs/plan-pruebas/INDICE-EVIDENCIAS.md` (dónde está cada cosa y cómo se
  traducen las rutas viejas que citan los informes) y `docs/historia/README.md`.
- Los enlaces vivos se corrigieron. Los enlaces **dentro** de los informes históricos se
  dejaron textuales: son el registro de lo que se corrió ese día.
- Con la limpieza salieron del árbol los dos archivos de `PR-CAC-05` que guardaban el
  `.env` del backend. **Siguen en la historia de git**: lo único que les quita el valor es
  rotar la contraseña de Neon y la clave de OpenAI, y eso sigue pendiente del owner.

### R-07 · calidad y CI

- `sonar-project.properties` con clave `signall-android` (antes `sign-all-android`).
- `.gitleaksignore` con los dos falsos positivos que quedan en el árbol: el `.glb` de
  Pauline y `LIVE_KEY_PERMANENT` en `tools/measure_face_markers.py`.
- `.github/workflows/ci.yml`: `./gradlew build` (que ya incluye ktlint y lint) y gitleaks
  sobre el árbol. SonarQube **no** corre en Actions: el servidor es local.

## Verificación

| Qué | Resultado |
|---|---|
| `./gradlew build` | en verde, tres veces (después de R-02, de R-03 y al cerrar) |
| `./gradlew installDebug` | instalada, después de habilitar "Instalar vía USB" en MIUI |
| App corriendo | sesión abierta contra el backend, escena lista en **3 307 ms** (Juan, 200 clips) |
| Frase "Hola, como estas?" | 1 segmento, **2 glosas** (`HOLA`, `COMO ESTAS`) representadas |
| `SignAllMetrics` | frase #1 **2 304 ms** · envío→primera seña **2 308 ms** · oración→avatar **163 ms** · 2 señas en **4 673 ms** |
| `SignAllNet` | `segment 0/1 turn=1` — el frame y el `turn` llegaron intactos |
| `SharedPreferences` | el historial quedó en `shared_prefs/signall.xml` |
| gitleaks sobre el árbol exportado de git | `no leaks found` (60 MB escaneados) |
| SonarQube `signall-android` | 13 806 ncloc · 63 code smells · 1 bug · 4,8 % duplicación · cobertura 0 % |

Los tiempos son de **una** frase en el Redmi Note 9S con el backend en Docker y
`adb reverse`: sirven para comprobar que el camino funciona, no como medición de
latencia, que necesita las 28 frases.

El análisis de Sonar salió **idéntico a la línea base**: el trabajo no agregó deuda.

## Pendiente

Cerrado en la ola 2 (2026-11-13):

1. ~~**Proyectos viejos en SonarQube.**~~ Borrados los tres `sign-all-*`; quedan solo
   `signall-android`, `signall-backend` y `signall-stt`.
2. ~~**Rotar las dos credenciales.**~~ Ya estaban rotadas: se comprobó contra Neon
   (`password authentication failed`) y OpenAI (HTTP 401). Ver `01-leaks.md`.
3. ~~**El nombre de los repos en GitHub.**~~ Renombrados a `signall-*` y los `origin`
   reapuntados; `git ls-remote` responde en los cinco.
4. ~~**CI en `signall-docs`.**~~ No se crea: el owner resolvió la contradicción a favor del
   § 4 ("solo cambia el nombre del repositorio").

Sigue abierto:

5. La app vieja (`com.signai.webview`) sigue instalada en el teléfono y **no se
   desinstaló**: borrarla se lleva el historial y los ajustes del owner. Las dos conviven
   sin problema hasta que él decida.
6. Los nombres internos del **backend** y del **STT** (`SignAI` en documentación, log de
   arranque y compose) son su bloque, no el contrato.
