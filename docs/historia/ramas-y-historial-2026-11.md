# Ramas e historial hasta noviembre de 2026

Documento de historia: **no describe el presente**. Se conserva porque el SRS, el SDD y la
memoria citan commits y ramas de esta época, y porque un clon viejo puede seguir citando un
hash que ya no existe con ese nombre.

Lo que sigue vigente está en [`../../AGENTS.md`](../../AGENTS.md) § Git: todo el trabajo va a
`main` en los cuatro repositorios, y `signall-avatar` trabaja en `dev`.

## La reescritura del historial del 2026-11-14

El 2026-11-14 se reescribió la historia de `signall-android` con `git-filter-repo` para sacar
de todos los commits las credenciales y las evidencias pesadas de `docs/plan-pruebas/`. El
`.git` pasó de 334 MB a 65 MB.

Consecuencia: **los hashes de commit anteriores a esa fecha cambiaron**. El mapeo viejo → nuevo
está en [`commit-map-2026-11-14.txt`](commit-map-2026-11-14.txt), y los documentos ya citan los
hashes nuevos. Un clon anterior a esa fecha hay que rehacerlo, o `git fetch && git reset
--hard origin/main`.

Ese día también se cerró la reestructuración que había empezado el 2026-11-13: ese día hubo una
excepción temporal ("nada de `push`"), que ya no rige.

## La rama `develop` (abandonada el 2026-11-11)

El owner decidió el **2026-11-11** abandonar `develop`: no existe más en ninguno de los cuatro
repositorios y no se vuelve a crear. Las ramas `develop` locales se borraron (eran ancestros de
`main`, sin commits propios) y las remotas quedaron atrás:

| Repositorio | `origin/main` ese día | `origin/develop` (obsoleta) |
|---|---|---|
| `signall-android` | `69181d2` | `69181d2` (se igualó ese día) |
| `signall-backend` | `301e721` | `301e721` (igual) |
| `signall-stt` | `4824979` | `4824979` (igual) |
| `signall-docs` | `401b7d1` | `8ff4fd2` (11 commits atrás) |

Las ramas remotas `origin/develop` **no se borraron**: son historia, y borrarlas en el remoto
es cosa del owner. No se usan ni se actualizan.

## El flujo por oraciones (2026-10-21)

El flujo por oraciones entró a `main` como un fast-forward (`5b7329e` → `4aac5c2`), con el
merge de `origin/main` que ya traía. La rama `flujo-oraciones` dejó de existir porque pasó a ser
`main`. El punto de retorno de esa operación es `respaldo/main-2026-10-21`, que apunta al `main`
viejo y está en el remoto.

## La rama `tiempo-real`

El trabajo de tiempo real de los tres repositorios entró por fast-forward a `main` en Android y
a `develop` en los hermanos, y la rama se borró local y remoto. Donde aparezca escrita en una
bitácora o en un documento es el dato de dónde se hizo el trabajo, no una rama a la que volver.
