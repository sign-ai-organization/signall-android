# Puesta en marcha: ver el proceso completo por partes

Para entender qué pasa con cada pieza, correr **una por terminal** y mirar lo que
imprime cada una. Las piezas son independientes: el oído (STT), el cerebro
(backend) y la app.

Hoy, sin la base de datos de Neon, se corre el **cerebro simulado**
(`tools/mock_backend.py`), que habla el mismo protocolo y usa el **LLM real**.

---

## Antes de empezar, una sola vez

Ollama ya está instalado, pero hay que **calentarlo** (cargar el modelo en
memoria):

```bash
ollama run qwen3:4b-instruct "hola"
```

Contestó → listo. La primera vez tarda ~1 minuto y eso supera el límite de 10 s
del backend, así que sin calentar la primera frase falla y la segunda anda.

---

## Terminal 1 — el oído (STT)

No hace falta mientras se pruebe el camino de texto, pero para verlo:

```bash
cd ../sign-ai-stt
python -m scripts.mic_test_client.py
```

Habla al micrófono. Si no querés el STT real, el backend trae uno falso que
contesta cualquier cosa:

```bash
cd ../sign-ai-backend
python -m scripts.fixtures.fake_stt_server
```

Se ve: `[fake-stt] received chunk #1 (960 bytes)`.

> Ojo: el backend **pide el oído antes de abrir cualquier sesión**, incluso si
> solo vas a mandar texto. Con el mock esto no aplica.

---

## Terminal 2 — el cerebro (mock con LLM real)

```bash
cd ../webview
pip install websockets        # solo la primera vez
python tools/mock_backend.py --ollama
```

Imprime:

```
[mock] escuchando en ws://0.0.0.0:8080/ws/translate
[mock] glosas: LLM real (qwen3:4b-instruct) vía http://localhost:11434/v1/chat/completions
[mock] calentá antes: ollama run qwen3:4b-instruct "hola"
```

Cada vez que la app manda una frase se ve:

```
[mock] sesión abierta desde ('127.0.0.1', 44884)
[mock] frase: 'no tengo el documento conmigo'
[mock] glosas: ['YO', 'DOCUMENTO', 'TENER', 'NO']
```

Para probar el camino de error: escribí una frase que contenga **FALLA**
("esto FALLA") y se mandará un error en vez de glosas.

Sin `--ollama` devuelve tres glosas fijas (`YO`, `UNIVERSIDAD`, `IR`), que es
más rápido y sirve cuando el problema no es el modelo.

Comprobar que vive: `curl http://127.0.0.1:8080/health`

---

## Terminal 3 — la app

**En el emulador** no hay que pasar nada: `10.0.2.2` ya apunta a la compu.

```bash
cd ../webview
./gradlew installDebug
```

**En un celular físico**, con la IP de tu compu en la red local
(`hostname -I | awk '{print $1}'`):

```bash
./gradlew installDebug -Psignai.backendWsUrl=ws://192.168.1.50:8080/ws/translate
```

Nunca `127.0.0.1`: eso apunta al propio celular.

### Qué mirar en la app

| Ves | Significa |
|---|---|
| "Conectando con el servidor..." | está abriendo la sesión |
| "Sin conexión con el servidor" | no hay nada escuchando en esa dirección |
| "Listo para traducir" | conectado, listo |
| "Traduciendo..." | la frase ya viaja, esperando el LLM |
| "YO · DOCUMENTO · TENER · NO" | son las glosas que se están reproduciendo |
| "Reproducción completada" | terminó la cola, aparece *Repetir* |

Probá escribir una frase con **FALLA** para ver el mensaje de error.

---

## Ver los logs de la app mientras corre

Otra terminal:

```bash
cd ../webview
./tools/dev.sh log
```

O más filoso, solo lo de la app:

```bash
./tools/dev.sh logcat-tag SignAiNet
```

Lo que sirve para mirar:

- `SignAiNet` — conexión, descargas, errores de red
- `SignAiState` — cuándo se abre/cierra la sesión
- `SignAIWeb` — la consola del JS (errores de `app.js`)

Si se traba, mirá el log del mock: si no aparece `[mock] frase:`, el problema
está entre la app y el puerto 8080 (dirección o cleartext). Si aparece la frase
pero no `glosas:`, el problema es Ollama.

---

## El proceso completo, en orden

```
1. Escribís la frase y tocás Traducir
2. La app manda {"type":"text","text":"…"} por el WebSocket
3. El backend/cerebro la recibe            ← se ve en Terminal 2
4. Pregunta al LLM por las glosas          ← se ve en Terminal 2
5. Valida cada glosa contra el catálogo
6. Devuelve los segmentos
7. La app descarga la animación de cada glosa que no tenga
8. La escena 3D las reproduce una por una
9. El JS avisa que terminó → "Reproducción completada"
```

Lo que **no** está todavía: el micrófono (paso 5 del plan) y el catálogo real
(la tabla de las 50 señas en Neon). Por eso hoy todas las glosas comparten la
misma animación: el mock sirve el `sign.json` del repo para todas.

---

## Cuando esté el backend de verdad

```bash
cd ../sign-ai-backend
cp .env.example .env      # y completar DATABASE_URL y LLM_*
docker compose up --build # levanta el backend + el STT
```

Y en el `.env`, para usar el LLM local en vez de OpenAI:

```
LLM_BASE_URL=http://localhost:11434/v1
LLM_API_KEY=ollama
LLM_MODEL=qwen3:4b-instruct
```

Después, la app apunta igual: `ws://<tu-ip>:8080/ws/translate`.
