# R-01 · Credenciales en la historia (gitleaks)

Corrida del **2026-11-13** con gitleaks 8.30.1 sobre los cinco repositorios, con
`--log-opts="--all"`: todas las ramas y todos los commits, no solo el estado actual.
El script es `herramientas/escanear/leaks.sh` y los reportes quedaron en
`herramientas/informes/` (carpeta ignorada por git).

## Resultado por repositorio

| Repositorio | Hallazgos | Reales | Falsos positivos |
|---|---|---|---|
| `sign-ai-android` | 9 | **2 credenciales** | 6 |
| `sign-ai-backend` | 0 | — | — |
| `sign-ai-stt` | 0 | — | — |
| `sign-ai-docs` | 0 | — | — |
| `signai-avatar` | 0 | — | — |

## Lo real

Dos credenciales de producción, **todavía presentes en el árbol de trabajo**, dentro de
las evidencias de la prueba `PR-CAC-05` (la prueba que justamente revisaba qué expone el
backend):

| Credencial | Archivo | Commit donde entró | Fecha |
|---|---|---|---|
| Contraseña de Neon (`neondb_owner`, prefijo `npg_`) | `docs/plan-pruebas/evidencias/CAC/PR-CAC-05_D_20261104_01.json` | `0d0420ff` | 2026-11-05 |
| La misma contraseña de Neon **y** una clave `sk-proj-…` de OpenAI | `docs/plan-pruebas/evidencias/CAC/PR-CAC-05_E_20261105_01.json` | `5b8c28db` | 2026-11-05 |

El vector es el mismo en los dos: el archivo guarda, en un campo `"env"`, el volcado
completo del `.env` del backend —`DATABASE_URL` con la clave de Neon adentro, y
`LLM_API_KEY` con la de OpenAI—. No es código: es evidencia de una prueba.

## Lo que hay que hacer, en orden

1. **Rotar las dos credenciales.** Es lo único que resuelve el problema:
   - clave de OpenAI → revocar en el panel de OpenAI y emitir otra;
   - contraseña del rol `neondb_owner` en Neon → resetear y actualizar el `.env` del
     backend.
   Borrar el archivo o reescribir la historia **no descompromete** una credencial que ya
   estuvo publicada. Una clave rotada no sirve para nada aunque siga escrita.
2. **Sacar los dos archivos del árbol** cuando se haga R-05: con la limpieza de
   `docs/plan-pruebas/` desaparecen los dos. Ahí ya no quedan.
3. **Decidir si se reescribe la historia.** Mi recomendación: **no**. El repo pesa
   330 MB y una reescritura con `git-filter-repo` obliga a que todos los clones se
   rehagan; con las credenciales rotadas no compra nada. Si el repositorio va a ser
   público, entonces sí hay que hablarlo.

## Los falsos positivos, para que no se repitan

Seis de los nueve no son credenciales. Quedan anotados porque van a volver a aparecer en
cada corrida si no hay un `.gitleaksignore`:

| Regla | Dónde | Por qué no es un secreto |
|---|---|---|
| `generic-api-key` × 4 | `docs/plan-pruebas/evidencias/SEC/PR-SEC-01_E_*.txt` | `Sec-WebSocket-Key:` es el nonce de un handshake de WebSocket. Cambia en cada conexión y no da acceso a nada. |
| `generic-api-key` × 1 | `tools/measure_face_markers.py` (commit `dc5adc67`) | Matcheó `LIVE_KEY_PERMANENT`, que es el nombre de un morph target del avatar. |

## Comprobaciones que salieron bien

- En android solo hay **un** archivo con credenciales trackeadas `.env.example`.
- `env` y `.envJulian` existen en disco y **no** están en git (los cubre `.gitignore`).
- Backend, STT, docs y avatar: historia limpia, sin un solo hallazgo.

## Pendiente

- Repetir la corrida después de rotar, para dejar el reporte limpio como línea base.
- Agregar `.gitleaksignore` con los falsos positivos de arriba cuando se monte el CI
  (R-07).
