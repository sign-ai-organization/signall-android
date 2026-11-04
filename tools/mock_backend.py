#!/usr/bin/env python3
"""Mock de `../sign-ai-backend` para probar la app sin Neon ni Whisper.

Habla exactamente el mismo protocolo que el backend real, en un solo puerto:

- `GET /health` → `{"status": "ok"}`
- `GET /static/<sign_id>.glb` → una animación real de las horneadas en
  `app/src/main/assets/web/signs/`, para que la app tenga algo real que
  descargar y reproducir
- `WS /ws/translate` → acepta `{"type": "text", "text": "<frase>"}` y `{"type":
  "end"}`, y responde como `Controller.handle_transcript`: `{"type":
  "transcript", ...}`, un `{"type": "segment", "index", "total", "segment"}` por
  oración y un `{"type": "done"}` al final

Todas las glosas vienen con `available: true` y una ruta de animación, así que la
app descarga el archivo por HTTP y lo reproduce: eso ejercita el camino que más
riesgo tiene (descarga nativa + `PathHandler` `/signs/` del WebView).

## Glosas: fijas o con el LLM real

Por defecto devuelve las glosas fijas de abajo, que hace la prueba determinista.
Con `--ollama` llama a tu Ollama local usando **el mismo prompt de glosado del
backend** (`../sign-ai-backend/app/nlp/llm_client.py`) y por el mismo endpoint
compatible con OpenAI, así que se ve la traducción real:

    python tools/mock_backend.py --ollama

Ojo con Ollama: la primera llamada carga el modelo en memoria y tarda ~1 min,
más que el `LLM_TIMEOUT_MS` del backend. Calentalo antes con
`ollama run qwen3:4b-instruct "hola"`, o la primera frase va a fallar y la
segunda anda.

## Uso

    pip install websockets
    python tools/mock_backend.py            # glosas fijas
    python tools/mock_backend.py --ollama   # glosas del LLM real

Y en la app. En el emulador `10.0.2.2` ya es el host, no hace falta nada:

    ./gradlew installDebug

En un celular físico, la IP de tu compu en la red local (nunca `127.0.0.1`):

    ./gradlew installDebug -Psignai.backendWsUrl=ws://<TU-IP-LAN>:8080/ws/translate

No escucha audio real: ignora los frames binarios. Es para ver la app, no para
medir nada.
"""

from __future__ import annotations

import asyncio
import json
import pathlib
import sys
import urllib.error
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import local_stt  # noqa: E402

try:
    import websockets
    from websockets.asyncio.server import serve
    from websockets.http11 import Headers, Response
except ImportError:  # pragma: no cover
    sys.exit("Falta la dependencia: pip install websockets")

HOST = "0.0.0.0"
PORT = 8080

OLLAMA_URL = "http://localhost:11434/v1/chat/completions"
OLLAMA_MODEL = "qwen3:4b-instruct"
# La primera llamada carga el modelo en memoria: hay que darle margen.
OLLAMA_TIMEOUT_S = 180

REPO = pathlib.Path(__file__).resolve().parent.parent
# Las animaciones horneadas en el APK (tools/split_signs.py). El mock sirve
# estos mismos archivos, así lo que se ve en la app es exactamente lo que se
# grabó — y cada glosa recibe una seña DISTINTA, que antes no pasaba: había un
SIGNS_DIR = REPO / "app/src/main/assets/web/signs"

def available_signs() -> list[str]:
    """Ids de las señas horneadas, ordenados."""
    if not SIGNS_DIR.is_dir():
        return []
    return sorted(p.stem for p in SIGNS_DIR.glob("*.glb"))

# Copia del SYSTEM_PROMPT de ../sign-ai-backend/app/nlp/llm_client.py. Si el
# backend lo cambia, hay que cambiarlo acá también.
GLOSS_SYSTEM_PROMPT = (
    "You translate short Spanish sentences into LSC (Lengua de Señas "
    "Colombiana) glosses, following these general sign-language glossing "
    "conventions:\n"
    "- Topic-comment order: the topic of the sentence goes first.\n"
    "- Drop articles (el, la, los, las, un, una) and the copula (ser/estar) "
    "when the meaning stays clear without them.\n"
    "- Use the base/infinitive form of verbs, never conjugated.\n"
    "- For content questions (qué, cómo, dónde, cuándo, quién, cuánto), "
    "place the question word at the end.\n"
    "- Place negation (no, nunca, tampoco) at the end of the sentence.\n"
    "\n"
    "A gloss is a single word representing one sign: UPPERCASE, no accent "
    "marks (á->A, é->E, í->I, ó->O, ú->U — keep Ñ as is), no punctuation. "
    "Respond with ONLY the glosses, separated by commas, nothing else — "
    "no explanation, no sentence-type classification, no separator other "
    "than a comma.\n"
    "\n"
    "Examples:\n"
    "Input: ¿Cuál es su nombre?\n"
    "Output: NOMBRE,TU,QUE\n"
    "Input: No tengo el documento conmigo.\n"
    "Output: YO,DOCUMENTO,TENER,NO\n"
    "Input: Firme aquí, por favor.\n"
    "Output: FAVOR,AQUI,FIRMAR"
)

# Glosas fijas, para que la prueba sea estable y no dependa del modelo.
FIXED_GLOSSES = ["YO", "UNIVERSIDAD", "IR"]

# Tipo de oración que se anuncia en el segmento. Puesto por `--type`; sirve para
# probar los marcadores faciales, que se eligen por tipo (RF-AVT-03).
FORCED_TYPE = "AFFIRMATIVE"

# Si la frase contiene esta palabra se responde con un error, para probar el
# camino de fallo del cliente.
FAIL_WITH = "FALLA"

# Puesto por --ollama.
USE_OLLAMA = False

# Dónde viven las animaciones que anuncia el catálogo.
#
# "local"  -> `signs/<id>.glb`, un path same-origin DENTRO del APK. Es lo que va
#             a devolver el backend real después de sembrar `catalog`, y la app
#             lo carga directo de sus assets sin descargar nada.
# "remote" -> `/static/<id>.glb`, que la app sí baja por HTTP y cachea. Sirve
#             para ejercitar el camino de descarga (el de un asset futuro que
#             viva en el backend).
ASSETS_LOCAL = True
# Puesto por --stt: transcribe el audio con whisper.cpp local.
USE_STT = False


def sign_bytes(sign_id: str) -> bytes | None:
    """Contenido del asset de una seña horneada, o `None` si no existe."""
    path = SIGNS_DIR / f"{sign_id}.glb"
    if not path.is_file():
        return None
    return path.read_bytes()


# ── catálogo ──────────────────────────────────────────────────────────────────
# El backend real resuelve glosa → seña contra la tabla `catalog` de Neon, que
# ya tiene las 50 glosas de LSC50 sembradas por tools/seed_catalog.py. Acá se
# usa la misma tabla para que el mock se parezca a producción: es lo único que
# permite ver el caso de una glosa anunciada SIN animación (0032–0049), que
# antes no se podía reproducir porque el mock solo anunciaba lo que había en
# disco, y por eso el 404 garantizado de esas 18 nunca se vio en el dispositivo.
sys.path.insert(0, str(REPO / "tools"))
from seed_catalog import LSC50, canonical  # noqa: E402

# Copia de normalize_gloss de ../sign-ai-backend/app/validation/catalog_repository.py.
# Mismo criterio que el SYSTEM_PROMPT de más arriba: si el backend lo cambia, hay
# que cambiarlo acá también.
_ACCENTS = str.maketrans({"Á": "A", "É": "E", "Í": "I", "Ó": "O", "Ú": "U", "Ü": "U"})
_PUNCTUATION = str.maketrans("", "", "¿?")


def normalize_gloss(gloss: str) -> str:
    return " ".join(gloss.strip().upper().translate(_ACCENTS).translate(_PUNCTUATION).split())


# glosa canónica -> id de seña, los 50.
CATALOG: dict[str, str] = {canonical(gloss): f"{n:04d}_0000_0000" for n, gloss, _ in LSC50}


def glosses_from_ollama(text: str) -> list[str]:
    """Traduce con el LLM local, igual que el backend real."""
    body = json.dumps(
        {
            "model": OLLAMA_MODEL,
            "messages": [
                {"role": "system", "content": GLOSS_SYSTEM_PROMPT},
                {"role": "user", "content": text},
            ],
            "temperature": 0.0,
        }
    ).encode()
    request = urllib.request.Request(
        OLLAMA_URL,
        data=body,
        headers={"Content-Type": "application/json", "Authorization": "Bearer ollama"},
    )
    with urllib.request.urlopen(request, timeout=OLLAMA_TIMEOUT_S) as response:
        payload = json.loads(response.read())
    content = payload["choices"][0]["message"]["content"]
    separator = "," if "," in content else None
    return [g.strip().upper() for g in content.split(separator) if g.strip()]


def build_result(text: str) -> list[dict]:
    """Un segmento con una glosa por palabra, como devuelve el backend."""
    if USE_OLLAMA:
        glosses = glosses_from_ollama(text)
    else:
        glosses = list(FIXED_GLOSSES)
    # Glosa → seña por el catálogo, igual que el backend real. Una glosa fuera
    # de LSC50 viene `available: false` y sin ruta; una glosa de LSC50 siempre
    # trae su ruta, incluso si la animación todavía no está exportada (0032–0049),
    # porque la tabla las anuncia igual. Que la app se entere de eso es el
    # propósito: reproduce el caso que hace caer al sign por defecto.
    entries = []
    for g in glosses:
        sign_id = CATALOG.get(normalize_gloss(g))
        if sign_id is None:
            entries.append({"text": g, "available": False, "glb_path": ""})
            continue
        entries.append(
            {
                "text": g,
                "available": True,
                "glb_path": (
                    f"signs/{sign_id}.glb" if ASSETS_LOCAL else f"/static/{sign_id}.glb"
                ),
            }
        )
    return [{"type": FORCED_TYPE, "glosses": entries}]


def handle_http(connection, request) -> Response | None:
    """Sirve /health y /static/* en el mismo puerto que el WebSocket.

    Devuelve `None` en la ruta del WebSocket para que el handshake continúe.
    """
    path = request.path.split("?", 1)[0]

    if path.startswith("/ws/"):
        return None
    if path == "/health":
        return Response(
            200,
            "OK",
            Headers([("Content-Type", "application/json")]),
            json.dumps(
                {"status": "ok", "mock": True, "llm": USE_OLLAMA and OLLAMA_MODEL or "fijas"}
            ).encode(),
        )
    if path.startswith("/static/"):
        # El nombre es el id de la seña, tal cual lo puso `build_result`.
        sign_id = path[len("/static/"):].removesuffix(".glb")
        data = sign_bytes(sign_id)
        if data is None:
            print(f"[mock] GET {path} -> 404 (no servida)")
            return Response(404, "Not Found", Headers(), b"no such sign")
        return Response(
            200, "OK", Headers([("Content-Type", "model/gltf-binary")]), data
        )
    # Cualquier otra cosa que parezca un asset. Loguearlo es lo que permite
    # comprobar que la app NO pide los `.glb` que ya no existen en el APK: antes
    # de isOwnAssetPath los pedía siempre y salía un 404 por cada glosa sin
    # animación, sin dejar rastro visible desde acá.
    if path.endswith(".glb"):
        print(f"[mock] GET {path} -> 404 (pedido de un asset que no servimos)")
        return Response(404, "Not Found", Headers(), b"not found")
    return Response(404, "Not Found", Headers(), b"not found")


async def send_translation(websocket, loop: asyncio.AbstractEventLoop, text: str) -> None:
    """LLM -> glosas -> la frase como la manda el backend.

    Un `segment` por oración, cada uno con su `index` y el `total`, y un `done`
    al final: es lo que produce `Controller.handle_transcript`, y lo que la app
    espera desde que el backend dejó de mandar un array pelado.

    La llamada al LLM bloquea, así que va fuera del event loop.
    """
    try:
        segments = await loop.run_in_executor(None, build_result, text)
    except (urllib.error.URLError, OSError, KeyError, TimeoutError) as error:
        print(f"[mock] LLM falló: {error}")
        print('[mock] ¿arrancaste Ollama? ¿lo calentaste con `ollama run '
              f'{OLLAMA_MODEL} "hola"`?')
        await websocket.send(
            json.dumps({"type": "error", "message": f"Mock: el LLM falló ({error})."})
        )
        return

    total = len(segments)
    for index, segment in enumerate(segments):
        glosses = [g["text"] for g in segment["glosses"]]
        print(f"[mock] segmento {index + 1}/{total}: {glosses}")
        await websocket.send(json.dumps({
            "type": "segment",
            "index": index,
            "total": total,
            "segment": segment,
        }))
    await websocket.send(json.dumps({"type": "done"}))


async def handle_session(websocket) -> None:
    print(f"[mock] sesión abierta desde {websocket.remote_address}")
    loop = asyncio.get_running_loop()
    utterance = local_stt.Utterance()
    transcribing: asyncio.Task | None = None
    warned_audio = False

    async def transcribe_and_translate(pcm: bytes) -> None:
        """Transcribe then translate, without ever stopping the read loop.

        Runs as a background task on purpose: Whisper takes seconds and the
        phone keeps streaming audio the whole time. If the loop stopped reading
        while it worked, the socket buffer would fill and the client would get
        a broken pipe in the middle of the phrase.
        """
        seconds = len(pcm) / 2 / local_stt.SAMPLE_RATE
        try:
            spoken = await loop.run_in_executor(None, local_stt.transcribe, pcm)
        except Exception as error:  # noqa: BLE001 - nunca debe tumbar la sesión
            print(f"[stt] falló: {error}")
            await websocket.send(
                json.dumps({"type": "error", "message": f"Mock: la transcripción falló ({error})."})
            )
            return

        spoken = spoken.strip()
        if not spoken:
            print(f"[stt] {seconds:.1f}s de audio, no se entendió nada")
            return

        print(f"[stt] {seconds:.1f}s de audio -> {spoken!r}")
        await websocket.send(
            json.dumps({"type": "transcript", "text": spoken, "final": True})
        )
        await send_translation(websocket, loop, spoken)

    try:
        async for message in websocket:
            if isinstance(message, bytes):
                if not USE_STT:
                    if not warned_audio:
                        print("[mock] audio ignorado; arrancá con --stt para transcribirlo")
                        warned_audio = True
                    continue
                utterance.add(message)
                if utterance.should_finalize:
                    pcm = utterance.pcm()
                    utterance.reset()
                    # One transcription at a time; a burst of audio while one
                    # is running is dropped rather than queued (and the next
                    # silence window picks up whatever comes next).
                    if transcribing is None or transcribing.done():
                        transcribing = asyncio.ensure_future(
                            transcribe_and_translate(pcm)
                        )
                continue

            try:
                payload = json.loads(message)
            except json.JSONDecodeError:
                print(f"[mock] frame no JSON, ignorado: {message!r}")
                continue

            if payload.get("type") == "end":
                # El cliente soltó el micrófono: los 1.5 s de silencio que
                # cierran una frase nunca van a llegar porque dejó de mandar
                # audio. Se cierra a la fuerza, que es para lo que existe este
                # frame en el protocolo del STT.
                if USE_STT and (utterance.has_voice or utterance.chunks):
                    print(f"[stt] 'end' del cliente, cerrando {len(utterance.chunks)} chunks")
                    pcm = utterance.pcm()
                    utterance.reset()
                    if transcribing is None or transcribing.done():
                        transcribing = asyncio.ensure_future(transcribe_and_translate(pcm))
                else:
                    utterance.reset()
                continue

            if payload.get("type") != "text":
                print(f"[mock] tipo ignorado: {payload.get('type')!r}")
                continue

            text = (payload.get("text") or "").strip()
            print(f"[mock] frase: {text!r}")

            if FAIL_WITH in text.upper():
                await websocket.send(
                    json.dumps({"type": "error", "message": "Mock: no se pudo traducir."})
                )
                continue

            # El eco de `text` como `transcript` **no** existe en el backend real:
            # un `text` es una frase a traducir, no voz. Cuando el mock lo hacía,
            # el modo en vivo la tomaba por una frase hablada y la reenviaba: el
            # mismo bucle que quemó el proceso con el backend viejo (~2.900
            # repeticiones). El `transcript` sale solo del camino de audio.
            await send_translation(websocket, loop, text)
    except websockets.exceptions.ConnectionClosed:
        # Normal: the app dropped the session, it reconnects on its own.
        print("[mock] sesión cerrada por el cliente")


async def main() -> None:
    # En modo local no hace falta ningún archivo: `signs/<id>.glb` es el
    # identificador del contrato y la app resuelve el clip dentro del avatar
    # empaquetado (`embeddedClipsFor`). Solo el modo remoto sirve archivos.
    if not ASSETS_LOCAL and not available_signs():
        sys.exit(
            f"No hay señas horneadas en {SIGNS_DIR}.\n"
            "Generá las con:  python3 tools/split_signs.py"
        )

    async with serve(handle_session, HOST, PORT, process_request=handle_http):
        print(f"[mock] escuchando en ws://{HOST}:{PORT}/ws/translate")
        if USE_OLLAMA:
            print(f"[mock] glosas: LLM real ({OLLAMA_MODEL}) vía {OLLAMA_URL}")
            print(f'[mock] calentá antes: ollama run {OLLAMA_MODEL} "hola"')
        else:
            print(f"[mock] glosas fijas: {FIXED_GLOSSES}")
        if USE_STT:
            if not local_stt.available():
                print("[mock] --stt pedido pero no hay motor de STT en "
                      f"{local_stt.CACHE}")
                return
            print(f"[mock] STT local: {local_stt.engine_name()}")
        else:
            print("[mock] STT apagado: el audio se ignora (--stt para transcribirlo)")
        if ASSETS_LOCAL:
            print("[mock] rutas: signs/<id>.glb (el clip vive dentro del avatar)")
        else:
            print(f"[mock] {len(available_signs())} animaciones desde {SIGNS_DIR.name}/")
        print("[mock] Ctrl+C para salir")
        await asyncio.Future()


if __name__ == "__main__":
    if "--ollama" in sys.argv:
        USE_OLLAMA = True
    if "--stt" in sys.argv:
        USE_STT = True
    if "--assets-remote" in sys.argv:
        ASSETS_LOCAL = False
    if "--type" in sys.argv:
        FORCED_TYPE = sys.argv[sys.argv.index("--type") + 1].strip().upper()
    if "--glosses" in sys.argv:
        FIXED_GLOSSES = [
            g.strip() for g in sys.argv[sys.argv.index("--glosses") + 1].split(",") if g.strip()
        ]
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[mock] cerrado")
