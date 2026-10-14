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
| El servicio STT existe | ✅ hecho | repo `../sign-ai-stt`, `app/main.py:62` (`/ws/transcribe`) |
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

- **GPU:** NVIDIA GeForce RTX 4050 Laptop, 6 GB. El compose pide CUDA → **va**.
- **Docker:** 29.9.0 + compose 5.6.0. **Disponible.**
- **Puerto 8000** (STT): libre.
- **Puerto 8080** (backend): **ocupado por el mock** → hay que pararlo (§3).

---

## 3. Prender el backend real + el STT

Parar el mock primero, si está corriendo:

```bash
pkill -f mock_backend
```

Después, en `../sign-ai-backend`:

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
adb shell am start -n com.signai.webview/.MainActivity
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

---

## 9. Pendiente para después

- **Licencia de Hotgen** — sin verificar. Bloqueante para el uso en la tesis.
- **18 señas** — `0032`–`0049`. Cuando lleguen: a `GLB Senas/` + `split_signs.py`.
- **`bab1cd8`** del backend (endpoint LLM local compatible con OpenAI) quedó
  fuera de todas las ramas. Recuperable con `git fetch origin bab1cd8`.