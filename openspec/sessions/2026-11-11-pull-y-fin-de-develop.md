# Pull de los cuatro repos y fin de la rama `develop`

**Fecha:** 2026-11-11, madrugada · **Rama:** `main`

## Qué llegó

Dos pedidos del owner, en orden:

1. **Traer los cambios de todos los repos.** Un `fetch --all --prune` en los cuatro y
   traer lo que había.
2. **Borrar la rama `develop`.** «Borralas todas las ramas develop acá en local, todo lo
   trabajaremos en main. Excepto el repositorio de avatar, ese no lo toques.»

## Qué se hizo

### El pull

Solo dos repos tenían commits nuevos:

| Repo | Antes | Después | Qué traía |
|---|---|---|---|
| `sign-ai-android` | `d72ec4e` | `704e0ac` | 12 commits: la ronda P3 del 10/11 (fichas `PR-AND-03`, `PR-AND-04`, `PR-INT-05`, `PR-REN-07`), las repeticiones y umbrales del carril 2, y 129 archivos de evidencia |
| `sign-ai-docs` | `b23692c` | `401b7d1` | 27 commits: umbrales del carril 2 en las fichas y el **documento nuevo `latex/vfp/`** (versión final de la propuesta), con sus borradores en `borradores/vfp/` |
| `sign-ai-backend` | `301e721` | `301e721` | nada (solo se borró la rama remota `fix/glosado-hue-cac-llm-p1`, ya fusionada) |
| `sign-ai-stt` | `4824979` | `4824979` | nada |

**En la app no cambió una línea de código**: los 12 commits son de `docs/plan-pruebas/` y
`openspec/sessions/`, verificado con `git diff --name-only d72ec4e..704e0ac` filtrando esas
dos carpetas → vacío.

`../signai-avatar` no se tocó: `dev` ya estaba en `fc6f372`, igual que `origin/dev`.

### El borrado de `develop`

Las cuatro ramas `develop` locales eran **ancestros de `main`**, sin commits propios
(comprobado con `git merge-base --is-ancestor develop main` antes de cada borrado), así que
se borraron con `git branch -d` y no se perdió nada:

- `sign-ai-android`: `7707da6` (venía 116 commits atrás)
- `sign-ai-backend`: `301e721` (igual a `main`)
- `sign-ai-stt`: `7a571c2` (5 atrás)
- `sign-ai-docs`: `8ff4fd2` (11 atrás)

Las **ramas remotas `origin/develop` no se borraron**: son historia, y borrarlas en el remoto
de los hermanos es cosa del owner. Quedaron así:

| Repo | `origin/main` | `origin/develop` |
|---|---|---|
| `sign-ai-android` | `704e0ac` | `704e0ac` |
| `sign-ai-backend` | `301e721` | `301e721` |
| `sign-ai-stt` | `4824979` | `4824979` |
| `sign-ai-docs` | `401b7d1` | `8ff4fd2` |

`signai-avatar` quedó como estaba: `dev` en `fc6f372`, `main` en `fc6f372`, y su cambio local
sin commitear (`openspec/specs/dataset/spec.md`, el typo `JUCIOSO` → `JUICIOSO`) intacto.

## Qué se decidió

- **El owner decidió que se abandona `develop`.** Todo el trabajo va a `main` en los cuatro
  repos y **no se vuelve a crear la rama**. La documentación se actualizó para que ninguna
  sesión futura la busque: la tabla de repos de `AGENTS.md` y de `docs/SISTEMA.md` ahora
  dice `main` para el backend y el STT, las reglas de PR de los hermanos apuntan a `main`, y
  la sección `## Git` de `AGENTS.md` cuenta qué se borró y por qué.
- **`../signai-avatar` queda excluido**: su rama de trabajo es `dev` y ahí no se toca nada.
- **No se borraron las ramas remotas.** La decisión del owner fue sobre las locales.

## Qué se verificó

- **Los seis documentos compilan** en `../sign-ai-docs` con `make all`, incluido el `vfp`
  nuevo: `srs` 124 páginas, `sdd` 48, `memoria` 72, `plan-pruebas` 143, `administracion`
  43, `vfp` 35. Compilación local con `latexmk` (TeX Live instalado en la máquina).
- **La app compila**: `./gradlew build` → `BUILD SUCCESSFUL` en 1 min 18 s (144 tareas, 60
  ejecutadas), sin errores de lint.
- **No hay device conectado** (`adb devices` vacío), así que no se corrió `installDebug`.
  Queda pendiente por owner.

## Qué quedó pendiente

- **Borrar las ramas `origin/develop` del remoto**, si el owner lo quiere. En los tres
  repos de hermanos y en el de documentos.
- **Instalar la app en un device** para que el owner pueda probar (falta por el `adb devices`
  vacío).
- **Fichas `PR-REN-07` y `PR-AVT-05`**: la sesión `2026-11-10-p3-and03-and04.md` las
  dejó pendientes de actualizar en `../sign-ai-docs` con los valores nuevos de la ronda
  diagnóstica. El pull del 11/11 trae cambios en `latex/plan-pruebas/main.tex`, pero **no
  verifiqué** si cierran esas dos fichas: es un pendiente de otro repo.
- **El `develop` remoto del backend sigue disparando el CI** (`.github/workflows/ci.yml`
  tiene `branches: [main, develop]`). Si la rama se borra, esa línea deja de ser necesaria.
