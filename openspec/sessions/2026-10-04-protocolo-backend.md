# Sesión: alinear la app con el protocolo real del backend

- **Día:** domingo
- **Fecha:** 2026-10-04
- **Hora:** ~16:30–17:30 (local)

## Qué se hizo

Se revisó el estado de los dos repos, se corrigió la integración rota y se dejó
todo compilando, instalado y probado en el emulador. El hallazgo central: **la
app no podía funcionar contra el backend actual**, y no era un bug de la app
sino un desfase de protocolo en las dos puntas.

### El desfase

`sign-ai-backend` avanzó en `develop` (PR #8 y #9) y su `main` ahora manda la
frase **en partes**:

```
{"type":"transcript", …}          # parcial y final
{"type":"segment","index":0,"total":2,"segment":{…}}   # una por oración
{"type":"done"}
```

La app seguía contra el protocolo viejo: `SignAiWebSocketClient.parseMessage`
solo entendía `transcript`, `error` y **un array pelado** de segmentos. Los
`segment` y el `done` se descartaban en silencio → el avatar no señaba nunca y
el turno se quedaba en "Traduciendo…" para siempre.

Al revés también: la app mandaba `{"type":"text"}` y `{"type":"end"}`, y el
`_forward_audio` del backend solo miraba `message.get("bytes")`. **Todo frame
de texto se descartaba**, así que las frases escritas y el cierre del
micrófono no hacían nada.

La rama `feat/android-client-support` del backend había empezado a resolver lo
segundo (`handle_text`), pero **nunca se mergeó a `main`** y quedó 17 commits
atrás; además no contemplaba el frame `end`.

## Cambios

### `webview` (rama `feat/integracion-backend-v2`)

- `domain/BackendEvent.kt`: `Translation` (la lista completa) se reemplaza por
  `Segment(index, total, segment)` + `PhraseDone`. El evento refleja lo que el
  backend manda de verdad.
- `data/SignAiWebSocketClient.kt`: parsea `segment` y `done`; sigue ignorando
  cualquier `type` desconocido (`trace`, `sentence_trace`). Acepta también el
  array pelado de backends viejos y lo convierte a `Segment`, así nada aguas abajo
  tiene que saber qué versión del backend encontró.
- `ui/state/MainScreenState.kt`: acumula los segmentos y arma la cola cuando la
  frase está completa. Arranca en cuanto llega el último (no espera al `done`,
  que solo se manda después) y también al recibir `done`, para una frase que el
  backend no cierre. `flushSegments()` es idempotente. Los segmentos se limpian
  al fallar y al empezar una frase nueva, para que una frase muerta no se
  mezcle con la siguiente.
- `tools/mock_backend.py`: manda `segment` + `done` como el backend real.
- `.gitignore` + `.env.example`: `env` contenía la contraseña real de Neon y una
  API key del LLM, y **no estaba ignorado** — un `git add .` lo filtraba. Ahora
  `env`/`.env` están ignorados y `.env.example` va con placeholders.
- `android-integration.md`: frames de texto documentados, la sección del
  micrófono reescrita (ya no manda 2 s de silencio: manda `end`), y las
  limitaciones al día.

### `sign-ai-backend` (rama `feat/android-client-support-v2`, **sin commitear**)

- `router.py`: `_parse_client_frame` clasifica los frames de texto y `_forward_audio`
  los reenvía. Un frame malformado, de tipo desconocido o con frase vacía se
  ignora; nunca tumba la sesión.
- `controller.py`: `handle_text` (frase escrita, por el mismo pipeline que una
  transcrita) y `handle_end` (cierre forzado, con el fallo logueado y no lanzado:
  corre en el camino de lectura de la sesión).
- `stt_service_client{,_impl}.py`: `send_end()` envía el `end` pelado que el STT
  espera (`sign-ai-stt/app/main.py` compara `text_msg.strip().lower() == "end"`).
- `scripts/verify_client_frames.py`: 18 checks sin tocar Neon, LLM ni STT.

**Nada commiteado ni pusheado todavía**, por si querés revisarlo antes.

## Verificación

- `./gradlew assembleDebug` OK; instalado y corriendo en `emulator-5554`.
- Contra el mock: la frase escrita "hola como estas" hizo que la app mostrara
  `YO · UNIVERSIDAD · IR` en el hint, descargara los 3 assets a `cache/signs/` y
  terminara en "Reproducción completada". Antes de este trabajo ese mismo caso no
  llegaba ni al avatar.
- `python -m scripts.verify_client_frames`: 18/18 PASS. Cubre el clasificador de
  frames, la sesión completa con un doble de STT, y que un frame basura no tumbe
  la sesión.

## Decisiones

- **Acumular los segmentos y reproducir de una vez**, en vez de empezar a
  ejecutar cada oración apenas llega. El doc del backend sugiere lo segundo
  ("el avatar puede empezar la primera mientras se traducen las siguientes"),
  pero la app tiene una sola cola y un solo estado de reproducción; reproducir
  en streaming obliga a manejar estado parcial y es un cambio mucho más grande
  para una ganancia de latencia de ~1 s. Queda anotado como mejora posible.
- **Aceptar el array viejo en el parser.** El backend se despliega desde otro
  repo y puede ir atrasado respecto a la app; tolerar las dos formas cuesta diez
  líneas y evita que un desfasamiento de despliegue rompa la app en producción.
- **Los secretos a `.env.example`.** El `env` de la base era una copia local del
  `.env` del backend; nada lo leía, solo estaba ahí.

## Pendiente

- **El backend no sirve archivos de animación.** No hay endpoint `/static/…`, así
  que `SignAssetRepository` siempre recibe 404 y se cae al camino alterno. No se
  nota porque la app trae 50 señas horneadas y el fallback de demostración está
  activo. Hay que decidir el formato de `glb_path` con el equipo (ver
  `android-integration.md` §7).
- **`catalog` en Neon sigue con 3 de 50 señas.** Casi toda glosa llega
  `available: false`.
- **Confirmación del texto (RF-STT-03 / RF-UI-02)** sigue sin implementarse; el
  contrato va a cambiar y hay que acordarlo antes.
- Probar contra el stack real (`docker compose up --build`, con STT en la RTX
  4050) quedó a medias: la build descargaba torch cuando se cortó la sesión.