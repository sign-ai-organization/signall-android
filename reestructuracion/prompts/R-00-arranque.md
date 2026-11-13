# Prompt de arranque (un párrafo)

Para abrir una sesión nueva que tome la reestructuración completa. Se copia y se pega tal
cual.

---

Trabajamos en el proyecto **SignAll** (tesis: español hablado → Lengua de Señas Colombiana
con avatar 3D). Estás en el repo `sign-ai-android`; los hermanos son `../sign-ai-backend`,
`../sign-ai-stt`, `../sign-ai-docs` y `../signai-avatar`. Los repositorios en GitHub ya se
llaman `sign-all-*`, pero las carpetas locales conservan el nombre viejo a propósito: no
las renombres. Leé primero `AGENTS.md` y después `reestructuracion/00-general.md`, que tiene
el plan completo, las decisiones tomadas, las abiertas y las herramientas; tu trabajo es
ejecutar la **ola 0 (cimientos)** en el orden del § 5: R-02 y R-03 como un solo hilo (pasar
`namespace` y `applicationId` a `com.signall.app` y renombrar los identificadores internos),
después R-04 (nombres que cruzan el WebSocket, android y backend el mismo día), R-05
(sacar las evidencias pesadas de `docs/` y arreglar los enlaces) y R-07
(`sonar-project.properties` y CI en cada repo), usando el prompt de cada bloque que está en
`reestructuracion/prompts/` y las herramientas de `reestructuracion/herramientas/`
(SonarQube local en `:9000` y gitleaks). Mientras dure la ola 0, nadie más toca `app/` ni
`docs/` en android: quien necesite trabajar va a backend, a STT o a la tesis. Reglas: un
dueño por archivo, nada de `force-push`, commits chicos y un tema por commit, y **nada de
`push`: todo queda local hasta que el owner lo ordene**. Verificá siempre con
`./gradlew build`, `./gradlew installDebug` en el device y
`reestructuracion/herramientas/escanear/sonar.sh sign-ai-android`. Si algo del plan no
cierra o falta una decisión, preguntá; no la resuelvas por tu cuenta.
