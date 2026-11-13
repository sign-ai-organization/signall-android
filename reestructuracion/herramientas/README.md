# Herramientas de trabajo de la reestructuración

Todo corre local. Nada de esto se sube a un servicio de terceros y nada de esto
necesita cuenta.

| Herramienta | Versión | Dónde vive |
|---|---|---|
| **gitleaks** | 8.30.1 | `~/.local/bin/gitleaks` (ya instalado en la máquina) |
| **SonarQube** (servidor) | 9.9.8 LTS Community | contenedor `signall-sonarqube`, `http://localhost:9000` |
| **sonar-scanner** | 8.4 | imagen `sonarsource/sonar-scanner-cli`, se baja sola |
| **Docker / Compose** | 29.9.3 / 5.7.1 | ya instalado |

---

## SonarQube

### Levantarlo y apagarlo

```bash
docker compose -f reestructuracion/herramientas/docker-compose.sonarqube.yml up -d
docker compose -f reestructuracion/herramientas/docker-compose.sonarqube.yml down
```

Arranca dos contenedores: SonarQube y un Postgres 17 que le da la base. Tarda entre uno
y dos minutos en quedar listo; se comprueba con:

```bash
curl -s http://localhost:9000/api/system/status
# {"id":"...","version":"9.9.8.100196","status":"UP"}
```

- Puerto publicado **solo en 127.0.0.1**: no queda expuesto a la red local.
- Los datos viven en volúmenes de Docker (`signall-calidad_sonar-*`), no en el repo.
  Con `down` se conservan; para borrarlos de verdad hace falta `down -v`.
- Primer ingreso: `admin` / `admin`. SonarQube pide cambiar la clave en el acto.
- No se pierde al reiniciar la máquina: los contenedores están con `restart:
  unless-stopped` y vuelven solos mientras Docker arranque.

### Mandar un repositorio a analizar

```bash
reestructuracion/herramientas/escanear/sonar.sh sign-ai-backend
```

El script lee el token de `SONAR_TOKEN` o de `reestructuracion/herramientas/.env.sonar`
(ignorado por git). El token ya está creado en esta instalación, con nombre
`reestructuracion`.

Necesita un `sonar-project.properties` en la raíz del repo analizado; la clave del
proyecto la manda ese archivo (`signall-*`), no el nombre de la carpeta local. Las plantillas
están en `plantillas/` y desde R-07 los tres repos de código tienen el archivo en su raíz.

Detalle que costó tiempo y está resuelto en el script: `. .env.sonar` deja el token como
variable de shell, y `docker run -e` solo copia variables **exportadas**. El script hace
el `export`.

### Estado del primer análisis (2026-11-13)

| Proyecto | ncloc | bugs | code smells | vulnerabilidades | fiabilidad | duplicación |
|---|---|---|---|---|---|---|
| `signall-android` | 13.806 | 1 | 63 | 0 | C | 4,8 % |
| `signall-backend` | 2.349 | 1 | 6 | 0 | E | 0 % |
| `signall-stt` | 1.347 | 0 | 5 | 0 | A | 0 % |

- Los tres tienen **cobertura 0 %**: no hay tests, ni en backend ni en STT ni en la app.
  SonarQube lo va a repetir en cada análisis hasta que exista el primero.
- El STT pasó de 853 a 1.347 líneas y de 2 a 5 code smells después de R-07 porque ahora
  también se analiza `scripts/`, no solo `app/`. No es una regresión: es que antes se
  miraba media casa.
- Las claves nuevas son `signall-android`, `signall-backend` y `signall-stt`. En el
  servidor quedaron los proyectos viejos de la primera corrida (`sign-all-*`); borrarlos
  es del owner.
- Hoy los tres repos de código tienen CI en `.github/workflows/ci.yml`: compilación o
  análisis estático, y gitleaks sobre el árbol. El análisis de SonarQube no puede correr
  en Actions porque el servidor vive en esta máquina.
- La fiabilidad del backend es E por un solo bug. Conviene mirarlo antes que la deuda de
  estilo.
- El análisis de Kotlin necesita los binarios compilados: `sonar.kotlin.binaries` apunta
  a `app/build/tmp/kotlin-classes/debug`, así que hay que compilar antes de escanear.

---

## gitleaks

```bash
reestructuracion/herramientas/escanear/leaks.sh
```

Recorre los cinco repositorios y escanea **toda la historia** (`--log-opts="--all"`, no
solo la rama actual). Deja un JSON por repo con hallazgos en
`reestructuracion/herramientas/informes/`, carpeta ignorada por git.

Sale 0 si está limpio y 1 si encontró algo, así que sirve tal cual en CI.

Los hallazgos de la primera corrida están clasificados en `../01-leaks.md`.

---

## Lo que falta preparar

- SonarLint en el IDE (opcional; replica las reglas sin pasar por el servidor).
- Un `pre-commit` que corra gitleaks sobre lo que se va a commitear. Se decide en R-07.
