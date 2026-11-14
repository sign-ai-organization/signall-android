# SignAll — app Android

App del proyecto **SignAll**: español hablado → Lengua de Señas Colombiana representada
por un avatar 3D. Esta app es **una pieza del sistema**, no el sistema: la voz la
transcribe un servicio aparte, el texto lo traduce otro, y la app solo reproduce el
resultado.

```
../signall-stt ──PCM16/WS──▶ ../signall-backend ──WS/JSON──▶ esta app
                                      │
                                      └──▶ Neon (Postgres 16 + pgvector)
```

## Qué es

Una sola pantalla en Compose con un `WebView` que dibuja al avatar en Three.js. Recibe
oraciones por un WebSocket con el backend, las convierte en una cola de clips y las
representa. Los avatares (`Juan` y `Pauline`, 200 clips cada uno) y el catálogo viajan
dentro del APK.

## Cómo se corre

Los cuatro repos van bajo la misma carpeta padre (`SignAll/`), uno al lado del otro.

```bash
# 1. backend y STT (necesita ../signall-backend/.env con DATABASE_URL y LLM_API_KEY)
cd ../signall-backend && docker compose up -d

# 2. esperar a los dos servicios: el backend tarda ~50 s en cargar modelos y catálogo
curl -s http://127.0.0.1:8080/health
curl -s http://127.0.0.1:8000/health

# 3. compilar e instalar
./gradlew installDebug                       # emulador
./gradlew installDebug -Psignall.backendWsUrl=ws://<IP-del-PC>:8080/ws/translate   # teléfono
```

La dirección del backend se hornea en el APK al compilar: si cambia la IP, hay que
recompilar. El paso a paso completo, incluido todo por WiFi, está en
[`docs/ARRANQUE.md`](docs/ARRANQUE.md).

## Cómo se verifica

```bash
./gradlew build                    # compila; incluye ktlint y el lint de Android
./gradlew installDebug             # instala en el device o emulador conectado
adb logcat -s SignAllNet           # "sesión abierta: ws://…" y cada "segment i/n"
adb logcat -s SignAllMetrics       # latencias y señas representadas
```

## Qué necesita

| Necesitas | Detalle |
|---|---|
| JDK 17 y Android SDK 36 | el SDK se ubica por `local.properties` o `ANDROID_HOME` |
| `adb` | device físico o emulador; `minSdk` 26 |
| Backend y STT | `8080` y `8000`, levantados con el compose del backend |
| Credenciales | `DATABASE_URL` y `LLM_API_KEY` en `../signall-backend/.env` |
| Internet en el teléfono | Three.js se carga de un CDN |
| `RECORD_AUDIO` | permiso de runtime: `adb shell pm grant com.signall.app android.permission.RECORD_AUDIO` |

Teléfono por USB: `adb reverse tcp:8080 tcp:8080` y compilar con
`-Psignall.backendWsUrl=ws://127.0.0.1:8080/ws/translate`. Sin eso la app muestra "Sin
conexión con el servidor", porque el default `10.0.2.2` es el host del emulador.

## Adónde ir después

| Si vas a tocar… | Lee |
|---|---|
| Reglas de trabajo, git, UI, gotchas | [`AGENTS.md`](AGENTS.md) |
| Arranque completo, WiFi, demo cifrada, errores ya pisados | [`docs/ARRANQUE.md`](docs/ARRANQUE.md) |
| Los repos hermanos, el contrato del WebSocket | [`docs/SISTEMA.md`](docs/SISTEMA.md) |
| El micrófono, la cola de la escena, la latencia | [`docs/MODO-EN-VIVO.md`](docs/MODO-EN-VIVO.md) |
| Probar en el dispositivo, medir rendimiento | [`tools/README.md`](tools/README.md) |
| Colores, marca, contraste | [`docs/manual-identidad-visual.md`](docs/manual-identidad-visual.md) |
| El protocolo exacto del backend | `../signall-backend/docs/android-integration.md` |

Este repo commitea directo a `main`, sin PR ni rama de feature. **Nada de `push`**: el
push lo ordena el owner, repo por repo. En los hermanos no se commitea sin que lo pida.
