# Plan: conectar el móvil con el backend

Vamos a hacer que la app deje de inventarse las señas y hable con el backend
real de `../sign-ai-backend`. Lo hacemos **por partes**, en orden, para que cada
parte se pueda probar sola en el celular.

La idea es no tocar casi nada de lo que ya funciona: la pantalla, los drawers, el
tema y la escena 3D se quedan igual. Solo cambiamos **de dónde salen las glosas** y
**cuántas señas se reproducen**.

---

## Cómo correrlo ahora (terminales)

La guía completa, paso a paso y con una terminal por proceso, está en
[`puesta-en-marcha.md`](puesta-en-marcha.md). Resumen:

```bash
# 1. Calentar Ollama (solo la primera vez: tarda ~1 min)
ollama run qwen3:4b-instruct "hola"

# 2. Terminal del "cerebro" (mock con el LLM real)
cd ../webview && python tools/mock_backend.py --ollama

# 3. Terminal de la app (en el emulador no hace falta pasar la IP)
cd ../webview && ./gradlew installDebug
```

## Los tres repos, en una línea

```
🎙️ Micrófono → 📱 App móvil → 🧠 Backend → 📱 App móvil → 🧑‍🦱 Avatar 3D
                        └── por dentro llama al 👂 STT (audio → texto)
```

- **👂 `../sign-ai-stt`** — el oído. Convierte audio en texto. Ya está clonado.
- **🧠 `../sign-ai-backend`** — el cerebro. Convierte texto en glosas, las revisa
  contra el catálogo, pregunta al LLM.
- **📱 este repo (`webview`)** — la boca y la cara. Captura el audio, muestra el
  texto, hace las señas.

Regla importante: **el celular nunca habla directo con el oído.** El audio pasa
siempre por el cerebro, porque el proyecto pide un solo canal entre la app y el
backend.

## Qué nos manda el backend

Ya tiene listo el canal: un WebSocket en `ws://IP-DEL-BACKEND:8080/ws/translate`.

| Qué nos manda | Qué le hacemos |
|---|---|
| El texto que entendió (`transcript`) | Lo mostramos en la barra de transcripción |
| El resultado: lista de glosas | Las hacemos una tras otra en el avatar |
| Un error | Se lo mostramos al usuario |

---

## Estado de los PR

| Repo | Qué | Estado |
|---|---|---|
| `sign-ai-backend` | [PR #7](https://github.com/sign-ai-organization/sign-ai-backend/pull/7): frases escritas + LLM local (Ollama) | Abierto, listo para revisar. **Falta mergearlo** |
| `sign-ai-stt` | — | **No hace falta PR.** El camino de texto nunca pasa por el oído |
| `webview` (este) | Este plan + la implementación paso a paso | Rama `feat/integracion-backend` |

---

## Paso 0 — Dejar el backend listo (en el otro repositorio)

- [ ] **0.1 — Llenar la tabla de las 50 señas.** Hoy la tabla está vacía: si está
      vacía, el backend responde "esta seña no existe" para todo y no hay nada que
      ver. Es correr un script que inserte las 50. **Es lo único que bloquea.**
- [x] **0.2 — Que el backend acepte texto escrito.** Hoy solo entendía audio: si le
      mandábamos una frase escrita, la tiraba sin avisar. **Hecho en el PR #7.**
      Verificado con `python -m scripts.verify_text_frames`.
- [x] **0.3 — Poder correr el oído.** Ya está. Dos formas:
      - **De verdad:** `docker compose up --build` desde `../sign-ai-backend`
        levanta los dos servicios (el `docker-compose.yml` del backend ya sabe
        construir `../sign-ai-stt`). Cuesta descargar el modelo la primera vez.
      - **Para el camino de texto:** el oído falso del backend
        (`python -m scripts.fixtures.fake_stt_server`) alcanza y no cuesta nada.
        La frase escrita nunca pasa por el oído, solo necesita que acepte la
        conexión.
      - Ojo: **no levantes los dos compose por separado**, los dos publican el
        puerto 8000 y se pisan. Levantar todo desde el del backend.
      - Ojo también: la decisión de usar Whisper `medium` se tomó midiendo en una
        RTX 3050. Esta máquina tiene una **RTX 4050 de 6 GB**, más justa. Debería
        caber, pero conviene verificarlo antes de dar por buena esa decisión.
- [x] **0.4 — Correr el LLM en local (Ollama).** El backend ataba el LLM a OpenAI.
      Ahora `LLM_BASE_URL` apunta a cualquier servidor compatible. Con Ollama y
      `qwen3:4b-instruct` corre gratis, sin API key, y tardó 0.2 s por frase
      (OpenAI: 1-3 s). **Hecho en el PR #7.**
      - Configurar: `LLM_BASE_URL=http://localhost:11434/v1`, `LLM_API_KEY=ollama`,
        `LLM_MODEL=qwen3:4b-instruct`.
      - **Calentar el modelo antes** con `ollama run qwen3:4b-instruct`, o la
        primera llamada se corta por timeout (tarda ~1 min en cargar).
      - Usar `qwen3:4b-instruct`, **no** `qwen3:4b`: la segunda piensa antes de
        responder y rompe el formato que el backend espera.

---

## Antes de tocar el celular — probar la cadena completa

Esto no es código, es un control. Si esto anda, el celular solo tiene que hablar
el mismo idioma.

Con el backend y el oído levantados, el repo del STT trae un cliente de prueba que
toma el micrófono de la laptop:

```bash
cd ../sign-ai-stt
pip install -r requirements-dev.txt
python scripts/mic_test_client.py
```

Hablás, y tendrías que ver el texto transcrito y la lista de glosas. Si eso anda,
la cadena **micrófono → oído → cerebro → glosas** está completa y recién ahí
empezamos a escribir Kotlin.

> Con la frase escrita se puede probar sin micrófono: mandando
> `{"type": "text", "text": "quiero ir a la universidad"}` por el WebSocket.

---

## Paso 1 — Que la app hable con el backend

Acá ya no hay backend: todo es local y de mentira (escribes una frase, la app se
la inventa). En este paso solo agregamos el canal.

**Qué se toca:**

- `app/build.gradle.kts` → agregar la librería de red (OkHttp).
- `AndroidManifest.xml` → permitir conexiones en claro (`ws://`) hacia el host del
  backend, que Android 9+ bloquea por defecto. Preferible un
  `network_security_config.xml` que autorice solo el host de desarrollo, antes que
  `usesCleartextTraffic="true"` como atajo.
- `data/SignAiWebSocketClient.kt` (nuevo) → se conecta, manda, recibe, y avisa
  qué pasa.
- `domain/BackendEvent.kt`, `domain/ValidatedSegment.kt` (nuevos) → los tipos que
  viajan por el socket.
- `domain/BackendConfig.kt` (nuevo) → la IP del backend, en un solo lado.
- `ui/state/PlaybackState.kt` → dos estados nuevos: *conectando* y *sin conexión*.

**Cómo se prueba:** en Ajustes, el hint muestra si está conectado o no. Nada más
todavía.

**Trampa importante:** el backend corre en tu compu, no en el celular. Hay que
poner la IP de la red local de tu compu (ej. `192.168.1.50`), **nunca
`127.0.0.1`** — esa apunta al propio celular.

---

## Paso 2 — Que la frase escrita viaje al backend

Hoy al darle *Traducir* la app se inventa las glosas con una función de ejemplo
(`Gloss.glossFor`). Ahora la frase viaja, vuelve el texto, y volvemos a tener
glosas reales.

**Qué se toca:**

- `ui/state/MainScreenState.kt` → `play()` deja de inventarse la respuesta y
  espera la real. De paso se borran los tiempos falsos: hoy la app espera 850 ms y
  dice "Traduciendo..." por puro adorno.
- El turno de conversación guarda la glosa que vino del backend, no la inventada.

**Cómo se prueba:** escribes "quiero ir a la universidad", le das Traducir, y ves
el texto transcrito y las glosas que devuelve el backend.

**Aquí termina el primer milestone**: no queda ni un dato inventado en el camino de
las glosas.

---

## Paso 3 — Que el avatar haga varias señas seguidas

Hoy el avatar repite **siempre la misma seña**. Hay que enseñarle a seguir una
lista.

**Qué se toca:**

- `assets/web/app.js` → agregar una lista de señas a reproducir. La función que
  aplica los frames (`applyFrame`) **no se toca**: se le da una lista y se la
  recorre.
- Entre una seña y otra, una transición corta contra la pose de reposo.
- Si una glosa no existe (el backend la marca como no disponible), se muestra
  *"Seña no disponible"* y se salta, sin intentar reproducirla.
- Las cejas y la cara cambian según el tipo de frase (afirmativa, pregunta,
  negativa...), como pide el proyecto.

**Ojo — simplificación honesta:** la transición entre señas va a ser corta y
simple, no la interpolación suave que pide la especificación. Lo anotamos como
pendiente, no lo damos por hecho.

**Cómo se prueba:** mandas una frase que devuelva varias glosas y el avatar las
hace una tras otra, con una pausa corta entre cada una.

---

## Paso 4 — Que las animaciones se bajen del backend

Este es el paso con el único truco técnico del plan, y hay que hacerlo bien desde
el principio.

**El problema:** la escena 3D se carga desde un origen seguro de Android, así que
si el código de la escena intenta bajarse un archivo del backend, Android lo
bloquea. Y no es cuestión de "activar un permiso": no se puede.

**La solución:** las descargas las hace el código nativo (Kotlin), nunca el de la
escena. Kotlin baja el archivo y lo deja en una carpeta del celular; la escena 3D
lo lee desde ahí, pero por la misma puerta que ya usa hoy para el avatar (sin
pedir nada y sin cambiar una línea del visor).

**Qué se toca:**

- `ui/webview/AvatarWebView.kt` → registrar una ruta extra para servir esos
  archivos. Son 4 líneas, porque esa puerta ya existe.
- `data/SignAssetRepository.kt` (nuevo) → baja el archivo si no lo tiene, y avisa
  si ya estaba.

**El `sign.json` que está hoy embebido en la app se queda** como respaldo cuando
no hay red. La app tiene que abrir y verse bien aunque el backend esté caído.

---

## Paso 5 — El micrófono de verdad

Al final, porque no hace falta para probar nada de lo anterior.

- [ ] Grabar audio de verdad (`AudioRecord`, 16 kHz, mono) y mandarlo en pedacitos
      de 30 ms — el mismo tamaño que usa el cliente de prueba del backend.
- [ ] Pedir el permiso de micrófono con su explicación (la app no funciona sin él,
      así que hay que decirlo).
- [ ] El botón del micrófono deja de hacer el autocompletado de ejemplo y pasa a
      usar lo que realmente escucha.
- [ ] Si se cae la conexión, reconectar solo y dejar el avatar quieto.

---

## Dudas que se resuelven más adelante

**Resultados cruzados.** Hoy, si escribes dos frases muy rápido, el backend puede
devolver los dos resultados mezclados. No pasa nada, porque la app ya ignora el
segundo envío mientras está traduciendo. Pero en el paso 5 se vuelve real: al
reconectar puede llegar un resultado viejo. Ahí va a hacer falta que cada frase
lleve un número. **Se decide con el backend antes de llegar ahí.**

**Conexión perezosa al oído.** Hoy el backend se conecta al oído al abrir la
sesión, así que sin él no abre nada — ni siquiera para texto. Se podría conectar
solo al primer fragmento de audio. **No lo vamos a hacer ahora**: con el oído real
o el falso ya no estorba, y es un cambio de diseño que le corresponde al equipo.

---

## Qué NO vamos a tocar

- Los archivos GLB de animación por seña (se decide después, cuando tengamos las
  50 señas; hoy seguimos con los frames de landmarks que ya funcionan).
- Cambiar Whisper por Parakeet en el oído. El punto de cambio está limpio (todo
  Whisper vive en dos funciones de `app/transcriber.py`), pero los modelos GGUF
  que tenés no los puede cargar `faster-whisper`, y el filtro de calidad usa datos
  que son propios de Whisper. Es un trabajo aparte, no un cambio de configuración.
- Login, contraseñas y cifrado. Se anota como pendiente, no se hace ahora.
- La estructura de `MainScreenState` (sigue sin ViewModel, por regla del repo).

---

## Orden de ataque

| Paso | Qué desbloquea | Se puede probar sin |
|---|---|---|
| 0 | Backend listo | — |
| — | Cadena completa probada desde la laptop | Micrófono de la laptop |
| 1 | Canal con el backend | Red |
| 2 | Frase escrita → glosas reales | Red |
| 3 | Avatar reproduce la lista | Red |
| 4 | Animaciones bajadas del backend | Red |
| 5 | Micrófono real | Permiso de micrófono |

Cada fila se prueba en el celular antes de pasar a la siguiente.
