# Arranque y prueba del sistema completo

Cómo se levanta el sistema entero, dónde apunta la app, cómo se prueba por WiFi y
qué errores ya se pisaron. El [`README.md`](../README.md) es la puerta de entrada;
esto es el detalle operativo.

Los cuatro repos tienen que quedar bajo la misma carpeta padre:

```
SignAll/
├── signall-android/     ← este repo
├── signall-backend/     ← FastAPI: WebSocket, NLP de glosas, catálogo
├── signall-stt/         ← faster-whisper + VAD: audio → texto
├── signall-docs/        ← requerimientos y diagramas (no ejecuta nada)
└── signall-avatar/      ← solo si vas a regenerar los GLB de los avatares
```

`signall-docs` y `signall-avatar` no hacen falta para correr el sistema.
`signall-stt` **sí**, y no se levanta aparte: lo arrastra el compose del backend.

## 1. Antes de empezar

| Necesitas | Versión | Se comprueba con |
|---|---|---|
| JDK | 17 | `java -version` |
| Android SDK | platform 36, build-tools | `sdkmanager --list_installed` |
| `adb` | cualquiera reciente | `adb version` |
| Docker + Compose | con GPU opcional | `docker compose version` |

El proyecto compila con Gradle 8.11.1, AGP 8.9.3, Kotlin 2.3.21, `minSdk` 26,
`compileSdk`/`targetSdk` 36 y JVM 17. El SDK se ubica por `local.properties`
(`sdk.dir=...`, gitignored) o por la variable `ANDROID_HOME`.

La app carga Three.js desde un CDN, así que **el teléfono necesita internet** aunque el
avatar y los clips sean locales.

## 2. Arrancar el sistema completo

### Paso 1 — Credenciales

```bash
cd ../signall-backend
cp .env.example .env
```

Edita `.env` y llena, como mínimo, `DATABASE_URL` (la cadena de Neon, con
`sslmode=require`) y `LLM_API_KEY`. Sin esos dos el backend arranca y falla en la primera
frase. Son credenciales reales: `.env` está en `.gitignore` y no se sube.

### Paso 2 — Levantar backend, STT y proxy

```bash
cd ../signall-backend
docker compose -f docker-compose.yml -f docker-compose.host.yml up -d
```

Ese comando construye y levanta **los tres** servicios: el backend en el `8080`, el STT en
el `8000` y el proxy TLS en el `443`. La primera vez tarda bastante porque instala PyTorch y
descarga el modelo de Whisper; después queda cacheado en un volumen.

El proxy va en el segundo archivo de Compose y no en el primero, así que un `docker compose
up -d` a secas **no lo levanta**: para la app `debug`, que habla `ws://` contra el 8080,
alcanza; para la app `demo`, que habla `wss://`, no. El certificado de laboratorio tiene que
existir antes en `signall-backend/deploy/certs/` (se emite una vez por máquina, según
[`../signall-backend/deploy/README.md`](../signall-backend/deploy/README.md)), y se comprueba
así:

```bash
docker compose -f docker-compose.yml -f docker-compose.host.yml ps caddy
curl --cacert deploy/certs/lab-ca.crt https://127.0.0.1/health
```

**No está listo cuando el contenedor dice "Started".** Medido el 2026-10-29 en esta
máquina: el STT respondió a los ~10 s y el backend a los ~52 s, porque carga el modelo de
Whisper, spaCy y el catálogo completo de Neon. Espera a los dos:

```bash
curl -s http://127.0.0.1:8000/health   # {"status":"ok","model":"medium",...}
curl -s http://127.0.0.1:8080/health   # {"status":"ok","cache_loaded":true,...}
```

Si no tienes GPU NVIDIA, lee la nota de CPU en
[`../../signall-stt/README.md`](../../signall-stt/README.md).

### Paso 3 — Compilar e instalar la app

**Emulador** (no hay que configurar nada: el default apunta al host del emulador):

```bash
./gradlew installDebug
```

**Teléfono** (en la misma red que la PC; la app descubre la dirección sola):

```bash
./gradlew installDebug
```

Si el descubrimiento no aplica (backend por USB con `adb reverse`, o una IP fija que se
quiere forzar), la dirección se pasa como **último recurso**:

```bash
./gradlew installDebug -Psignall.backendWsUrl=ws://192.168.80.49:8080/ws/translate
```

Para verificar sin instalar: `./gradlew build`.

## 3. Dónde apunta la app

La app **descubre el backend sola**: al abrir y en cada reconexión prueba el último
endpoint exitoso, después mDNS (`_signall._tcp`) y por último la subred local con un
`GET /health` corto. `BuildConfig.BACKEND_WS_URL` quedó como **último recurso**, no como
la dirección normal. El detalle, y cómo anunciar el servicio desde la PC, está en
[`DESCUBRIMIENTO-BACKEND.md`](DESCUBRIMIENTO-BACKEND.md).

| Cómo te conectas | Qué hace la app | Comando |
|---|---|---|
| Emulador | No hay LAN que descubrir: usa el fallback `ws://10.0.2.2:8080/ws/translate` | `./gradlew installDebug` |
| Teléfono por USB, sin red | Usa el fallback `127.0.0.1` si se compila con él | `adb reverse tcp:8080 tcp:8080` y `./gradlew installDebug -Psignall.backendWsUrl=ws://127.0.0.1:8080/ws/translate` |
| Teléfono por WiFi | Encuentra la PC por mDNS o escaneando la subred; no hay que pasar ninguna IP | `./gradlew installDebug` |
| Demo cifrada (`demo`) | Busca por mDNS/escaneo en `https://…:443`; necesita la CA de laboratorio | ver § 6 |

`adb reverse` **solo existe por USB**: no hay equivalente por red. Si el teléfono está
conectado por USB **y** en la misma WiFi que la PC, el descubrimiento puede encontrar el
backend por la LAN y usar esa dirección; las dos funcionan.

`release` y `demo` **ignoran** la propiedad si no empieza por `wss://`, a propósito: el SRS
pide que todo lo que sale del dispositivo vaya cifrado. El descubrimiento respeta esa
regla: un build cifrado solo prueba `https://…:443`, nunca `http://…:8080`.

## 4. Todo por WiFi

Funciona completo: ADB, instalación y traducción. Medido el 2026-10-29 con un Redmi
Note 9S en `192.168.80.19` y el PC en `192.168.80.49`, los dos en la misma red.

> **Hay un plan para esto.** El camino WiFi tiene su propio documento,
> [`../../signall-backend/docs/plan-wifi.md`](../../signall-backend/docs/plan-wifi.md)
> (2026-10-27), y conviene leerlo antes de cambiar cualquier cosa de esta sección. Según su
> checklist, la fase 1 (`ufw` para el 443) y la fase 2 (APK servido por Caddy en `/apk/`)
> están hechas. **La fase 3 cambió de forma**: en vez del campo de URL en AVANZADO que
> proponía el plan, la app descubre el backend sola (mDNS + subred local), así que ya no hay
> que recompilar cuando cambia la IP. Ver [`DESCUBRIMIENTO-BACKEND.md`](DESCUBRIMIENTO-BACKEND.md).

### Linux (verificado en CachyOS/Arch)

```bash
sudo pacman -S android-tools        # una sola vez
```

**Primera vez, con el cable puesto** (o cada vez que reinicies el teléfono):

```bash
adb tcpip 5555
adb connect 192.168.80.19:5555     # IP del teléfono
adb devices                        # debe aparecer 192.168.80.19:5555
```

**Las siguientes veces** que solo cambió la red o se reinició `adb` en el PC, sin cable:

```bash
adb connect 192.168.80.19:5555
```

La IP del teléfono se lee con `adb shell ip -4 addr show wlan0` (con cable o por red) o en
Ajustes → WiFi. El teléfono también se anuncia por mDNS: `adb mdns services` lo lista como
`adb-<serial>  _adb._tcp  <ip>:5555`.

Ya sin cable, instalar y probar es igual que siempre:

```bash
./gradlew installDebug -Psignall.backendWsUrl=ws://192.168.80.49:8080/ws/translate
adb logcat -s SignAllNet
```

**Si el teléfono está conectado por USB y WiFi a la vez, hay dos dispositivos** y Gradle
falla con `more than one device/emulator`. Desconecta el cable o fija el destino:

```bash
ANDROID_SERIAL=192.168.80.19:5555 ./gradlew installDebug -Psignall.backendWsUrl=...
```

### Windows

No verificado en una máquina Windows; esto es el procedimiento esperado. Los comandos de
`adb` son idénticos, solo cambia cómo se instala la herramienta y cómo se escribe el path.

1. Instala Android Studio (o los *platform-tools* sueltos) y deja `adb` en el `PATH`.
2. Con Docker Desktop, los puertos publicados quedan accesibles desde la red local; la
   primera vez Windows pregunta si permites el tráfico para Docker y para `adb.exe`.
   Acepta **solo en redes privadas**.
3. En PowerShell:

```powershell
adb tcpip 5555
adb connect 192.168.80.19:5555
.\gradlew.bat installDebug "-Psignall.backendWsUrl=ws://192.168.80.49:8080/ws/translate"
```

Las comillas alrededor de `-P...` no son opcionales en PowerShell: sin ellas el argumento
se parte y Gradle no lo reconoce.

### Instalar o actualizar el APK sin adb

Si no quieres ni `adb`, el teléfono puede bajarse el APK desde el navegador:

```bash
tools/wifi/publicar-apk.sh
```

Compila el build `demo` con la IP actual adentro, lo copia a
`../../signall-backend/deploy/apk/` y muestra la URL y un QR. En el teléfono abres
`https://<IP>/apk/` y lo instalas. Necesita Caddy levantado (el `443`) y la CA de
laboratorio instalada en el teléfono.

Verificado el 2026-10-29: la página y el APK de 40,8 MB respondieron `200` desde el
teléfono. El APK que sirve ese camino es `demo`, así que reemplaza al `debug`.

### El cortafuegos: dos caminos que se comportan distinto

El teléfono puede llegar al PC por dos puertos, y `ufw` no los trata igual:

| Camino | Quién escucha | ¿Pasa por `ufw`? |
|---|---|---|
| `ws://<IP>:8080` (build `debug`) | el backend, publicado por Docker en la red del compose | **no**. Docker inserta sus reglas antes que `ufw`, así que el puerto queda abierto a la LAN aunque `ufw` esté en `DROP` y no haya ninguna regla |
| `wss://<IP>` (build `demo`, Caddy en el 443) | Caddy, en red de host | **sí**. Sin regla, el TCP no pasa |

Medido el 2026-10-29: con `ufw` activo y política de entrada `DROP`, **sin regla para
8080**, el teléfono alcanzó `http://192.168.80.49:8080/health` con `200`. Se comprobó
además con el `15672` de RabbitMQ, que no tiene nada que ver con el proyecto: también
respondió `200` desde el teléfono. El 443 sí necesita regla, y ya está puesta
(`ufw allow from 192.168.80.0/24 to any port 443`, fase 1 de `plan-wifi.md`).

Consecuencias prácticas:

- Para el camino `ws://` por WiFi **no hace falta abrir nada a mano**.
- Pero ese 8080 queda visible a **cualquier dispositivo de la red**. Ojo: `plan-wifi.md`
  §4, decisión 1, dice que "el 8080 no se abre para la LAN". Hoy lo abre Docker, no una
  regla. Es una diferencia entre el documento y el código, y no está resuelta.
- En una red pública (café, universidad, aeropuerto), apaga el stack con
  `docker compose down` al terminar.

## 5. Comprobar que de verdad funciona

```bash
# ¿la app abrió el socket y contra qué dirección?
adb logcat -s SignAllNet          # "sesión abierta: ws://…" y cada "segment i/n"

# ¿cuánto tardó y cuántas señas representó?
adb logcat -s SignAllMetrics      # "envío→primera seña", "representado: N señas en Xms"

# ¿los servicios están sanos desde el PC?
curl -s http://127.0.0.1:8080/metrics
curl -s http://127.0.0.1:8000/metrics
```

Medición de referencia, tomada el 2026-10-29 por WiFi con la frase "Hola como estas":
2 glosas, **envío→primera seña 205 ms** y 2 señas representadas en 3 956 ms. Son cifras de
una sola frase, no un promedio.

Prueba de punta a punta más completa (necesita los 28 fixtures de audio, que no están en
git):

```bash
tools/verificar-tiempo-real.sh todo
```

## 6. La demo cifrada (`wss://`)

Es el build que demuestra el canal cifrado, y **el que el plan elige para el camino WiFi**
(decisión 1 de `plan-wifi.md`): es el único que prohíbe el texto en claro y confía en la
CA de laboratorio. También es el que se publica en `https://<IP>/apk/` para instalar sin
cable. La app habla TLS contra Caddy, que corre en el host del backend escuchando en el
`443` y reenvía al backend en claro por `127.0.0.1:8080`.
El plan completo está en `../../signall-backend/docs/plan-proxy-tls.md`.

```bash
# por USB: Caddy en el 443, mapeado al 8443 del teléfono
adb reverse tcp:8443 tcp:443
./gradlew installDemo

# por WiFi: la app busca sola el 443 de Caddy por mDNS o escaneo
./gradlew installDemo
# si se quiere forzar una IP concreta (último recurso):
./gradlew installDemo -Psignall.backendWsUrl=wss://192.168.80.49/ws/translate
```

En el teléfono tiene que estar instalada la **CA de laboratorio**; si falta, el handshake
falla con `Trust anchor for certification path not found` y eso no es un problema de la
app. (Comprobado el 2026-10-29: el `443` responde por WiFi y el teléfono rechaza el
certificado porque no lo tiene instalado.)

`demo` comparte `applicationId` con `debug`, así que lo reemplaza: después de la demo,
vuelve con `./gradlew installDebug`.

## 7. Errores ya pisados

| Síntoma | Causa | Solución |
|---|---|---|
| "Sin conexión con el servidor" en un teléfono físico | no hay backend en la subred y se usó el fallback `10.0.2.2`, que es el host del **emulador** | revisa `adb logcat -s SignAllNet`: si dice `sin resultados`, el backend no está anunciado ni responde en la subred; ver [`DESCUBRIMIENTO-BACKEND.md`](DESCUBRIMIENTO-BACKEND.md) |
| `more than one device/emulator` | USB y WiFi conectados a la vez | desconecta el cable o usa `ANDROID_SERIAL` |
| El cambio de IP rompió todo | el backend no se está anunciando y el escaneo no lo encuentra | anúncialo por mDNS (§2 del documento de descubrimiento) o revisa el cortafuegos; no hace falta recompilar |
| "No se pudo abrir el micrófono" | `RECORD_AUDIO` es permiso de runtime y no viene otorgado | `adb shell pm grant com.signall.app android.permission.RECORD_AUDIO` |
| El backend responde en el `8080` pero las frases no cambian de glosa | `semantic_cache` no se invalida al cambiar prompt o modelo | `DELETE FROM semantic_cache;` |
| Sembraste el catálogo y no se ve | `CatalogRepository.load_all()` lo carga **una sola vez** al arrancar | reinicia el backend |
| `Trust anchor for certification path not found` en `demo` | falta la CA de laboratorio en el teléfono | instálala; no es un fallo de la app |
| La primera consulta tarda ~50 s | el backend aún está cargando modelos y catálogo | espera a `/health` antes de probar |
