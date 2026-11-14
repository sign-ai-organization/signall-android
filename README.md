# signall-android

Aplicación Android del proyecto **SignAll**: español hablado → Lengua de Señas Colombiana
(LSC) representada por un avatar 3D.

## Qué es

Una sola pantalla en Kotlin con Jetpack Compose y un `WebView` donde se dibuja el avatar en
Three.js. La app abre un WebSocket con el backend, le manda el texto escrito o el audio
capturado, recibe las oraciones ya traducidas a glosas y las representa en orden con el
avatar elegido. Los avatares y el catálogo de señas viajan dentro del APK, así que la escena
no depende de la red.

Es la última pieza del sistema: la captura de voz y la traducción viven en otros dos
servicios.

## Enfoque

**Hace:** captura el micrófono, envía audio y texto por un único WebSocket, muestra la
transcripción que devuelve el servidor y representa las señas con uno de los dos avatares
(Juan o Pauline, 50 señas × 4 marcas faciales cada uno).

**No hace:** no transcribe voz, no traduce español a glosas y no decide qué se representa.
La transcripción es de [signall-stt](https://github.com/sign-ai-organization/signall-stt) y
la traducción, de [signall-backend](https://github.com/sign-ai-organization/signall-backend).
Sin el backend la app arranca y muestra la escena, pero no puede representar nada.

## Cómo se usa

La app necesita el backend para funcionar y, para dictar por voz, el servicio de
transcripción. El backend trae un `docker-compose.yml` que levanta los dos servicios:

```bash
# desde la carpeta que contiene este repositorio
git clone https://github.com/sign-ai-organization/signall-backend
git clone https://github.com/sign-ai-organization/signall-stt
cd signall-backend
cp .env.example .env          # completar DATABASE_URL y LLM_API_KEY
docker compose up -d
```

Los dos clones van juntos porque el `docker-compose.yml` del backend construye el servicio de
voz desde una carpeta hermana. Si se prefiere correr el STT por separado, el
[README del backend](https://github.com/sign-ai-organization/signall-backend) explica cómo.

El backend queda en `http://localhost:8080` y el STT en `http://localhost:8000`. La primera
vez tarda varios minutos (instala dependencias y descarga el modelo de voz); no está listo
cuando el contenedor arranca, sino cuando responde:

```bash
curl -s http://localhost:8080/health
curl -s http://localhost:8000/health
```

Después se compila e instala la app, desde la carpeta de este repositorio:

```bash
./gradlew installDebug
```

Por defecto el APK de `debug` apunta a `ws://10.0.2.2:8080/ws/translate`, que es la forma en
que el emulador ve a la máquina anfitriona.

**En un teléfono físico** hay dos caminos:

```bash
# por USB
adb reverse tcp:8080 tcp:8080
./gradlew installDebug -Psignall.backendWsUrl=ws://127.0.0.1:8080/ws/translate

# por WiFi, con la IP de la computadora
./gradlew installDebug -Psignall.backendWsUrl=ws://192.168.1.20:8080/ws/translate
```

La dirección del backend se graba en el APK al compilar: si cambia la IP hay que recompilar.
El paso a paso completo, con los errores ya conocidos, está en
[`docs/ARRANQUE.md`](docs/ARRANQUE.md).

## Requisitos

| Necesitas | Detalle |
|---|---|
| JDK 17 | el proyecto usa AGP 8.9.3 y Kotlin 2.3.21 |
| Android SDK 36 | con `build-tools 36.0.0`; se ubica por `local.properties` o `ANDROID_HOME` |
| `adb` y un dispositivo o emulador | `minSdk` 26 |
| Docker + Compose | solo para levantar el backend y el STT con su `docker-compose.yml` |
| Backend | `ws://…:8080/ws/translate`; credenciales en el `.env` del backend |
| STT | solo para dictar por voz; el backend lo consume |
| Internet en el dispositivo | Three.js se carga desde un CDN, aunque el avatar sea local |
| Permiso `RECORD_AUDIO` | la app lo pide al usar el micrófono; si se negó, se concede con `adb shell pm grant com.signall.app android.permission.RECORD_AUDIO` |

## Estructura

```
app/src/main/java/com/signall/app/   UI en Compose, estado de pantalla, WebSocket y puente con la escena
app/src/main/assets/web/             escena Three.js, capa de marcadores (humanize.js) y los dos avatares GLB
docs/                                arranque, modo en vivo, mediciones y manual de identidad visual
tools/                               scripts de empaquetado de avatares, mediciones y clientes de prueba
openspec/                            contexto del proyecto: especificaciones y bitácora de sesiones
```

## Cómo se verifica

```bash
./gradlew build          # compila; incluye ktlint y el lint de Android
./gradlew ktlintCheck    # solo el análisis estático de Kotlin
./gradlew installDebug   # compila e instala en el dispositivo conectado

adb logcat -s SignAllNet       # cada oración que manda el backend, con índice y total
adb logcat -s SignAllMetrics   # glosas, duración de la representación y percentiles
```

La app no tiene pruebas automatizadas. El arranque, la latencia y el comportamiento en el
dispositivo se miden a mano con lo que hay en `tools/`; el detalle está en
[`tools/README.md`](tools/README.md).

Las herramientas de desarrollo (consola de la escena y bloque AVANZADO de Ajustes) existen
solo si la compilación las pide, con `./gradlew installDebug -Psignall.devTools=true`. El
build `release` las ignora siempre.

## Licencia

Este proyecto se distribuye bajo la licencia que defina la Pontificia Universidad Javeriana (pendiente de confirmación; por ahora, todos los derechos reservados). Ver [LICENSE](LICENSE).

## Trabajo de grado

SignAll es un trabajo de grado de la Pontificia Universidad Javeriana (Colombia). Para
citarlo: el equipo del trabajo de grado (2026). *SignAll: español hablado a Lengua de Señas Colombiana con un
avatar 3D*.
