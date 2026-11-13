# Conectar el micrófono (STT) y el backend real — puntos 1 y 3

- **Fecha:** 2026-10-14
- **Para:** la sesión que siga
- **Objetivo:** que la app con el **micrófono de verdad** (hablar) y contra el **backend real** (no el mock) complete el pipeline entero: voz → STT → glosas → Neon → avatar.

> **El punto 2 (18 señas faltantes) queda fuera de alcance.** Cuando tu compañero
> mande los `.glb` van en `GLB Senas/` y se corre `tools/split_signs.py`. Mientras
> tanto se prueba solo con las 32 que sí existen (ver §5).

---

## 1. La buena noticia: el 80% ya está hecho

Esto no es empezar de cero. **El cableado entero existe** en los tres repos.
Verificado hoy, leyendo el código:

| Pieza | Estado | Dónde |
|---|---|---|
| La app captura audio PCM16 del micro | ✅ hecho | `MainScreenState.toggleMic()`, `SignAiWebSocketClient` |
| La app manda los chunks por el WebSocket | ✅ hecho | binario, por la misma sesión que el texto |
| La app cierra el segmento al soltar | ✅ hecho | manda `{"type":"end"}` |
| La app espera la transcripción y la usa | ✅ hecho | con red de seguridad por si nunca llega |
| El backend recibe audio binario | ✅ hecho | `app/controller/router.py:60` |
| El backend reenvía al STT | ✅ hecho | `stt_service_client_impl.py` → `ws://stt:8000/ws/transcribe` |
| El servicio STT existe | ✅ hecho | repo `../signall-stt`, `app/main.py:62` (`/ws/transcribe`) |
| `docker-compose.yml` con ambos servicios | ✅ hecho | el del backend ya declara `backend` + `stt` |

**O sea: lo que falta es prender dos contenedores y apuntar la app.** No hay
que escribir el pipeline.

### La app NO tiene placeholder en el micro

Esto se cambió y vale aclararlo: el micro ya **no** es un autocompletado con
frase fija. Escucha de verdad, manda de verdad, y si no llega transcripción
muestra un fallo en vez de quedarse colgado. El único camino ainda no probado de
punta a punta es este.

---

## 2. Antes de arrancar — la máquina

Ya comprobado:

- **GPU:** NVIDIA GeForce RTX 4050 Laptop, 6 GB. Driver 615.78.08 cargado y sano.
  > **OJO: la GPU no alcanza sola.** El host es **CachyOS** y para que Docker le
  > pase la GPU hace falta `nvidia-container-toolkit`, que **no venía instalado**.
  > Sin él, `docker compose up` falla con
  > `could not select device driver "nvidia" with capabilities: [[gpu]]`
  > al arrancar el contenedor, no al cargar el modelo. Instalación:
  > `sudo pacman -S nvidia-container-toolkit` (está en `extra`; si pide proveedor,
  > elegir `extra`, no `cachyos-extra-v3`) + `sudo nvidia-ctk runtime configure
  > --runtime=docker` + `sudo systemctl restart docker`.
  > Verificado: con el toolkit instalado, `medium` carga en **cuda** sin problema.
- **Docker:** 29.9.0 + compose 5.6.0. **Disponible.**
- **Puerto 8000** (STT): libre.
- **Puerto 8080** (backend): **ocupado por el mock** → hay que pararlo (§3).

---

## 3. Prender el backend real + el STT

Parar el mock primero, si está corriendo:

```bash
pkill -f mock_backend
```

Después, en `../signall-backend`:

```bash
docker compose up --build
```

Eso levanta los dos servicios:

- **backend** en `:8080` — lee `.env` (que ya tiene `DATABASE_URL` a **Neon**,
  `LLM_API_KEY`, `LLM_MODEL` y `STT_WS_URL=ws://stt:8000/ws/transcribe`).
- **stt** en `:8000` — faster-whisper `medium` sobre CUDA, `LANGUAGE=es`,
  `SILENCE_TIMEOUT_MS=1500`.

> **El primer arranque tarda**: descarga el modelo de HuggingFace y lo cachea en
> el volumen `stt-hf-cache`. Los arranques siguientes son rápidos. Con 6 GB de
> VRAM y `medium` + `float16` entra; si se queda sin memoria, cambiar a
> `DEVICE=cpu COMPUTE_TYPE=int8` en `docker-compose.yml`.

Esperar a que salga el log de FasterWhisper cargando el modelo antes de probar.

### Verificar que los dos responden

```bash
curl -s localhost:8080/health
docker compose logs -f stt | head -40
```

### ⚠️ Reiniciar el backend después de cualquier cambio en Neon

`CatalogRepository.load_all()` corre **una sola vez al arrancar**. El catálogo
sembrado hoy (las 50 glosas de LSC50) no se ve hasta que el proceso arranca. Si
tocás la base, `docker compose restart backend`.

Comprobar que leyó bien las 50:

```bash
docker compose exec backend python -c \
  "import asyncio; from app.validation.catalog_repository import CatalogRepository; ..."
```

O más simple, del lado de Neon:

```bash
export $(grep -o '^DATABASE_URL=.*' .envJulian | head -1 | xargs)   # en sign-ai-android
psql "$DATABASE_URL" -At -c "SELECT count(*) FROM catalog;"        # debe dar 50
```

---

## 4. Apuntar la app al backend real

Desde `sign-ai-android`:

```bash
adb reverse tcp:8080 tcp:8080
./gradlew installDebug -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate
adb shell am start -n com.signall.app/.MainActivity
```

**Ojo con esto:** la URL por defecto del build es `ws://10.0.2.2:8080/ws/translate`,
que es el loopback del **emulador**. En el Redmi físico hay que pasar `-P` o no
se conecta. Después de compilar **sin** `-P`, el `BuildConfig` vuelve al default
aunque el APK instalado siga apuntando al anterior — para no concluir mal,
revisar con una conexión abierta real:

```bash
ss -tn | grep 8080
```

---

## 5. Frases para probar (solo con las 32 que existen)

> **Corrección (2026-10-14, tarde).** Esta tabla **no era correcta** tal como
> está. Las frases de una sola palabra (`hola`, `gracias`) andan de punta a
> punta, pero **las frasales no**: el LLM las parte en palabras sueltas y el
> resultado no matchea el catálogo (ver §10.2-§10.4). O sea que de esta lista solo
> conviene usar de momento las que dan una glosa única exportada. La lista de
> abajo está para cuando se arregle el prompt.

Estas **sí** deberían funcionar de punta a punta:

| Frase | Glosas esperadas |
|---|---|
| hola | HOLA |
| buenos días | BUENOS DIAS |
| buenas tardes | BUENAS TARDES |
| buenas noches | BUENAS NOCHES |
| cómo estás | COMO ESTAS |
| gracias | GRACIAS |
| adiós | ADIOS |
| cómo estás, bien | COMO ESTAS · BIEN |
| hola, cómo estás | HOLA · COMO ESTAS |
| me siento feliz | SENTIR · FELIZ |
| estoy triste | TRISTE |
| me siento aburrido | SENTIR · ABURRIDO |
| estoy contento | CONTENTO |
| tengo hambre | HAMBRE |
| cómo estás, más o menos | COMO ESTAS · MAS O MENOS |
| vive bien | VIVIR · BIEN |
| trabaja poco | TRABAJAR · POCO |
| mi familia | FAMILIA |
| mi hermano | HERMANO |
| niño | NIÑO |
| nombre | NOMBRE |

Estas **no** van a funcionar todavía — usar **solo para comprobar que el camino
de error no rompe nada**:

| Frase | Por qué falla |
|---|---|
| por favor | `0032`, sin animación exportada |
| mucho | `0049`, sin animación exportada |
| qué / cuándo / dónde / quién | `0041`–`0046`, todas interrogativas, sin exportar |

> Con `demoSignFallback` en `true` (default) una glosa sin animación **no rompe
> nada**: hace la seña de por defecto. Con el ajuste apagado, esa glosa no se
> reproduce y se reporta en el caption. Las dos cosas están bien; lo que hay que
> verificar es que **no se cuelgue**.

---

## 6. Qué mirar para decir que funcionó

1. `docker compose logs -f backend` → debe aparecer el `transcript` con lo que se
   habló, y los `segment` con las glosas.
2. `docker compose logs -f stt` → debe transccribir.
3. En el teléfono: el input se completa solo con lo que hablaste, y el avatar
   hace la cola de señas.
4. El estado pasa a **"Reproducción completada"** y aparece **Repetir**.

**Prueba de que el STT es real:** hablar "cómo estás" y ver que la transcripción
devuelta dice "cómo estás" — no una frase fija.

---

## 7. Trampas ya pisadas en esta sesión

Saven para no perder tiempo:

- **El `curl localhost:8080/health` da 404** si pegás a `/`. El endpoint es
  `/health`.
- **El log del mock vacío** con `python3 tools/mock_backend.py > log`: Python
  bloquea el stdout sin TTY. Con `python3 -u` aparece. (No aplica a Docker, que
  sí line-bufea.)
- **El teclado abierto mueve el botón `Traducir`**: con teclado abierto está en
  `y≈1550`, sin él en `y≈2255`. Un tap en 2258 con el teclado abierto no toca
  nada y parece un botón muerto.
- **`adb reverse` se cae solo a veces.** Siempre `adb reverse --list` después de
  reconectar el teléfono.
- **El avatar 3D no aparece en el árbol de accesibilidad.** Salió como
  `android.webkit.WebView` sin hijos, y su canvas de Three.js no publica nada.
  No se puede leer ni tocar con `mobile-mcp` ni con Maestro. Para el estado de
  la escena: el tag `SignAIWeb` de logcat, o el modo debug de la app
  (Ajustes → AVATAR → depurar).
- **MIUI "smart dark" tiene que seguir apagado**
  (`android:forceDarkAllowed=false` en `res/values/themes.xml`): con él prendido
  MIUI invierte el texto dentro de contenedores con scroll.
- **`RECORD_AUDIO` no viene concedido.** Hay que grantingarlo antes de probar el
  micro: `adb shell pm grant com.signall.app android.permission.RECORD_AUDIO`.
  Sin eso el micro no abre y la app muestra "No se pudo abrir el micrófono.",
  que parece un bug del STT y no es.
- **El STT es una dependencia dura, no opcional.** `router.py:107-112` conecta al
  STT **antes** de despachar nada y hace `return` si falla. O sea que **tampoco
  funcionan las frases escritas** con el STT caído. No hay prueba parcial: o está
  el STT, o no hay pipeline. (Esto corrige la premisa del §8, ver abajo.)
- **`docker compose up` con la GPUreserved y sin toolkit** deja el contenedor en
  estado `Created` para siempre, sin error visible en `docker compose ps` salvo
  el estado. El error real solo aparece en el `up`.

---

## 8. Orden sugerido de ataque

1. Levantar `docker compose up --build` y esperar que el STT cargue el modelo.
2. Probar `/health` y ver el log del STT.
3. Verificar que el backend leyó 50 glosas de Neon.
4. Reinstalar la app apuntando al backend real.
5. Probar por **texto** primero (más fácil de depurar que el audio): escribir
   "cómo estás" → Traducir. Si eso anda, el backend + catálogo + LLM están bien.
6. Recién ahí probar el **micro**.
7. Comparar: ¿el texto da la misma glosa que la voz? Si difieren, el problema
   es el STT o el VAD, no el pipeline de glosas.

El paso 5 separado del 6 es deliberado: separa "el backend anda" de "el audio
anda", y ahorra mucho rato de depuración mezclados.

> **Corrección (2026-10-14, tras ejecutarlo).** La separación del paso 5 **no se
> puede hacer**: como el STT es dependencia dura (ver §7), con el STT caído
> tampoco hay traducción de texto. El paso 5 solo sirve una vez que el STT está
> arriba, y ahí el valor real es otro: **el texto es el que delata los errores de
> glosa**, porque el audio encima mete una variable más (STT/VAD). Por eso el
> orden texto→audio sigue siendo el correcto, pero por otro motivo.

---

## 9. Pendiente para después

- **Licencia de Hotgen** — sin verificar. Bloqueante para el uso en la tesis.
- **18 señas** — `0032`–`0049`. Cuando lleguen: a `GLB Senas/` + `split_signs.py`.
- **`bab1cd8`** del backend (endpoint LLM local compatible con OpenAI) quedó
  fuera de todas las ramas. Recuperable con `git fetch origin bab1cd8`.

---

## 10. Resultado de ejecutar el plan (2026-10-14, tarde)

El plan se ejecutó completo. **El pipeline funciona de punta a punta** — y en el
camino apareció un bug que invalida parte de la §5.

### 10.1 Lo que quedó verificado

| Qué | Resultado |
|---|---|
| Backend `/health` | `{"status":"ok"}`, conectado a Neon |
| STT `/health` | `{"status":"ok","model":"medium","device":"cuda","language":"es"}` |
| Glosas cargadas en el backend vivo | **50** (contadas adentro del contenedor, no desde Neon) |
| `normalize_gloss` | `COMO ESTAS` y `NIÑO` matchean; `NINO` → `None` (la Ñ no se pliega, por diseño) |
| App → backend | `SignAiNet: ... http://127.0.0.1:8080` — confirma el `-P`, no el default `10.0.2.2` |
| Texto "hola" → Traducir | `HOLA` (0030), se reproduce, "Reproducción completada" + Repetir |
| **Micrófono de verdad** | transcripción real, no frase fija |

**Prueba de que el STT es real** (la de §6): hablándole al micro tres veces y
preguntando, el backend recibió

```
Final transcript: 'Hola, hola, hola ¿cómo estás?' -> translating
Segmented 'Hola, hola, hola ¿cómo estás?' into 2 sentence(s)
```

Salieron `¿` y mayúsculas desde Whisper, y el `SemanticCache` distingue
`HIT` para `¿cómo estás?` y `MISS` para `Hola, hola, hola`. Es transcripción de
verdad.

### 10.2 El bug: el LLM descompone las glosas frasales

`cómo estás` debería dar `COMO ESTAS` (0026, **sí** está en el APK). El backend
devolvió en cambio:

```
glosses=[TÚ (0039), ESTAR (available=False, sin path), CÓMO (0044)]
```

Las tres están **sin animación**: el APK solo trae `0000`–`0031`, así que 0039 y
0044 no existen en disco y `ESTAR` ni está en el catálogo. O sea que **de ese
test no se reprodujo ninguna seña real**, y como `demoSignFallback` está en
`true` la app igual reporta "Reproducción completada": **el fallo queda
silenciado**.

Es sistemático, no un caso raro. De la §5:

- **De una sola palabra sí funciona**: `hola`→`HOLA`(0030), `gracias`→`GRACIAS`(0000).
- **Las frasales no**: `cómo estás`, `buenos días`, `me siento feliz`,
  `tengo hambre`, `vive bien` caen todas en glosas que no tienen animación.

### 10.3 Causa raíz: el prompt, no el modelo

`app/nlp/llm_client.py` instruye:

> "A gloss is a **single word** representing one sign"

LSC50 usa glosas **frasales** (`COMO ESTAS`, `BUENOS DIAS`, `MAS O MENOS`).
Esa regla le **manda** partir la frase en palabras sueltas, y el resultado
contrasta contra un catálogo donde la frase entera ya existe.

Probado con los dos modelos y el prompt real:

| Frase | `gpt-4o-mini` | `qwen3:4b-instruct` |
|---|---|---|
| `cómo estás` | `TÚ,ESTAR,CÓMO` | `ESTÁS,CÓMO` |
| `me siento feliz` | `SENTIR,FELIZ` | `YO,ME,SIENTO,FEELIZ` |
| `tengo hambre` | — | `YO,HAMBRE,TENER` |

Los dos parten. Qwen además viola otras reglas del mismo prompt (acentos en
`ESTÁS`/`DÍAS`, conjugados `SIENTO`/`VIVE`, y un typo `FEELIZ`). **El prompt es
la causa; cambiar de modelo no lo arregla.**

### 10.4 La corrección, medida

Prompt nuevo: vocabulario **cerrado** (las 50 glosas del catálogo, inyectadas) +
regla explícita de que una glosa **puede** ser de varias palabras + "si el
vocabulario tiene `COMO ESTAS`, nunca respondas `TU,ESTAR,COMO`".

> **Después, la decisión D-7 (owner, 2026-10-16) quitó el vocabulario cerrado
> del prompt**: la lista sale, porque va contra el plan de pruebas y no escala.
> La regla de las glosas frasales y la reparación se quedan. Ver
> `docs/PLAN-MEDICION.md` §3 (D-7).

| Frase | Salida | ¿En §5? |
|---|---|---|
| cómo estás | `COMO ESTAS` (0026) | ✅ |
| buenos días | `BUENOS DIAS` (0001) | ✅ |
| me siento feliz | `SENTIR,FELIZ` (0028,0020) | ✅ |
| tengo hambre | `HAMBRE` (0019) | ✅ |
| vive bien | `VIVIR,BIEN` (0008,0024) | ✅ |
| estoy triste | `SENTIR,TRISTE` | ✅ |
| muchas gracias | `GRACIAS` (0000) | ✅ |

**Las 7 caen dentro de `0000`–`0031`, o sea que ahora sí se animan.** Comparación
de los dos modelos con el prompt corregido, sobre 10 frases:

| Modelo | Glosas dentro del vocabulario | Latencia media |
|---|---|---|
| `gpt-4o-mini` | 9/10 | 1.09 s |
| `qwen3:4b-instruct` | 9/10 | **0.20 s** |

Calidad equivalente, Qwen **5.4× más rápido**, gratis y sin internet. El fallo
restante de cada uno es del mismo tipo (una palabra inventada: `ESTAR,TRISTE` en
gpt, `MI,HERMANO` en qwen) y lo arregla un paso de reparación post-validación,
porque el backend ya valida contra el catálogo y hoy solo marca
`available=False` en vez de corregir.

### 10.5 Sobre usar un LLM local (lo que pregunta el owner)

**Por qué estaba `gpt-4o-mini`:** es lo único que el backend sabía hacer.
`LLMClient` no tenía `base_url`, así que estaba clavado en OpenAI.

**Se puede usar el local, y ya hay trabajo hecho:** la rama `bab1cd8` del backend
("allow pointing LLMClient at a local OpenAI-compatible endpoint", del 30-sep)
ya mete el `LLM_BASE_URL`. **Cuidado: esa rama NO se puede mergear entera** —
está muy atrasada respecto de `main` y su diff borra `debug_trace_enabled`, los
`db_keepalive_*`, los settings anidados del segmenter, y cambia
`semantic_cache_similarity_threshold` de `0.05` a `0.15`. Lo único que
interesa es el `llm_base_url`, que se puede portar a mano en un rato.

**Faltan dos cosas para que el contenedor vea a Ollama** (verificado):

1. Ollama escucha en `127.0.0.1:11434` → el contenedor **no** llega. Hay que
   bound-earlo a la interfaz externa (`OLLAMA_HOST=0.0.0.0:11434` + restart).
2. `host.docker.internal` no resuelve en Linux: hace falta
   `extra_hosts: ["host.docker.internal:host-gateway"]` en el compose.

### 10.6 Decisiones que faltaban tomar

1. **¿Arreglar el prompt?** Es lo que desbloquea las frases frasales. Va en
   `sign-ai-backend` (repo hermano) → hay que decidir si se toca ahí.
2. **¿Migrar a `qwen3:4b-instruct`?** Más rápido, gratis, reproducible offline
   (la demo de tesis no dependería de internet ni de una API key). Costo:
   weakest-link en calidad, y el Ollama necesita estar prendido.
3. **¿Reparación post-validación** para las glosas fuera de vocabulario?

> **Las tres: sí.** Resueltas y pusheadas en `sign-ai-backend` `develop`
> (commit `ac013d4`). Ver §10.8.

### 10.7 Pendiente de esta corrida

- [ ] `ollama` + `qwen3:4b-instruct` quedan prendidos fuera de Docker; si se
      apaga, el backend se queda sin LLM.
- [ ] `demoSignFallback` en `true` **esconde** este tipo de bug. Para la demo
      conviene al menos revisar el caption, o probarlo una vez en `false`.

---

## 11. Arreglo aplicado y verificado (cierre)

Todo lo de §10.2–§10.5 quedó implementado en `sign-ai-backend` y verificado de
punta a punta. Commit **`ac013d4`**, pusheado a `develop`.

### 11.1 Qué se cambió

| Cambio | Dónde |
|---|---|
| Prompt con vocabulario **cerrado** (las 50 glosas del catálogo) | `app/nlp/llm_client.py` |
| Glosas frasales permitidas + contraejemplo explícito del split malo | idem |
| `_repair_glosses`: rearma fragmentos, canonicaliza, descarta lo intratable | idem |
| `LLM_BASE_URL` para elegir proveedor (vía de `bab1cd8`, portada a mano) | `app/config/settings.py`, `app/main.py` |
| `CatalogRepository.vocabulary()` + orden estable por `id_sign` | `app/validation/catalog_repository.py` |
| Backend en `network_mode: host` | `docker-compose.yml` |
| Trampa del caché documentada | `CLAUDE.md` |

### 11.2 Por qué el prompt solo no alcanzaba

Después del arreglo, **`gpt-4o-mini` siguió partiendo** "cómo estás" en
`COMO,ESTAS`. Lo rescued la reparación post-hoc:

```
Merged ['COMO', 'ESTAS'] into catalog gloss 'COMO ESTAS'
```

También con Qwen: `Merged ['POR', 'FAVOR'] into 'POR FAVOR'`. O sea que la
capa de reparación no es un extra defensivo, es la que sostiene el resultado.

### 11.3 Resultado con los dos modelos

Sobre las 23 frases de §5: **21/23 dan glosas animables** con cada modelo. Las
2 que quedan son las que §5 ya anticipaba (`POR FAVOR`=0032, `MUCHO`=0049, sin
`.glb` exportado). Antes del arreglo solo funcionaban las de una sola palabra.

Latencia de la etapa LLM, misma máquina:

| Modelo | En vocabulario | LLM |
|---|---|---|
| `gpt-4o-mini` | 9/10 | 1248 ms |
| `qwen3:4b-instruct` (local) | 9/10 | **139 ms** |

Quedó **configurado el local**: `LLM_BASE_URL=http://127.0.0.1:11434/v1`,
`LLM_MODEL=qwen3:4b-instruct`. Cambiar a OpenAI es dos líneas en `.env`
(`LLM_BASE_URL=` vacío y `LLM_MODEL=gpt-4o-mini`).

### 11.4 Lo que costó más: que el contenedor no llegaba a Ollama

`OLLAMA_HOST=0.0.0.0:11434` **no alcanzó**. El mismo contenedor da HTTP 200 con
`--network host` y **timeout** en el bridge: Docker instala una política DROP
para tráfico bridge→host, así que ni siquiera el gateway del bridge es
alcanzable. Por eso el backend quedó en `network_mode: host`, y el STT pasó a
`STT_WS_URL=ws://127.0.0.1:8000/ws/transcribe` (su puerto publicado) en vez de
resolverse por DNS de Docker.

Vale la pena, porque corrige una suposición que está en la doc de los dos lados:
**"escuchá en 0.0.0.0 y el contenedor te ve" es falso en esta máquina.**

### 11.5 Verificación final en el teléfono

Con el stack completo (Qwen local + Whisper `medium` en CUDA + Neon), en el
Redmi Note 9S: `buenos días` → Traducir → `BUENOS DIAS` (`signs/0001_0000_0000.glb`)
→ "Reproducción completada" + Repetir. Y el micrófono devolvió transcripción
real (§10.1).

### 11.6 Pendientes que quedan

- [ ] **Las 18 señas (`0032`–`0049`)**: es lo que separa 21/23 de 23/23. `POR
      FAVOR` y `MUCHO` son justamente dos de ellas, y `YO`/`TU` (los pronombres
      que el modelo insiste en emitir) también.
- [ ] **`semantic_cache` no se invalida solo** al cambiar el prompt. Documentado en
      el `CLAUDE.md` del backend, pero sigue siendo una trampa manual.
- [ ] **Ollama tiene que estar prendido** antes que el backend, o no hay LLM.
      Podría añadirse un healthcheck en el compose que lo detecte.

