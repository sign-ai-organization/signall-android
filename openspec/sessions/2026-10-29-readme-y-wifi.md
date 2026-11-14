# El README que faltaba, y qué tan listo está el WiFi

**Fecha:** 2026-10-29, mañana · **Rama:** `main`

## Qué llegó

El owner preguntó dos cosas: qué tan adaptado estaba el `README.md` para arrancar **todo**
—incluidos los repos hermanos en la misma carpeta padre— con los comandos de WiFi para
CachyOS/Arch y Windows; y si la PC ya estaba preparada **permanentemente, en cualquier
red**, para trabajar por WiFi.

No había `README.md` en la raíz del repo. Lo más cercano era `AGENTS.md` (reglas),
`docs/SISTEMA.md` (mapa de los hermanos) y `tools/README.md` (tooling), y ninguno de los
tres explica el orden de arranque. En los hermanos sí hay README, pero el del backend
enseña el `venv` y no dice `docker compose up`, que es como se levanta de verdad.

## Qué se hizo

| Archivo | Qué |
|---|---|
| `README.md` | **Nuevo**, 325 líneas: prerrequisitos y versiones reales (Gradle 8.11.1, AGP 8.9.3, Kotlin 2.3.21, SDK 36, JVM 17), orden de arranque, las cuatro formas de apuntar la app al backend, ADB y descarga del APK por red en Linux y Windows, el cortafuegos con sus dos caminos, verificación, la demo cifrada y la tabla de errores ya pisados |

Se verificó el camino completo por WiFi antes de escribirlo, no después. Se dejó apuntando
a `plan-wifi.md` con el estado de sus fases, en vez de describir un camino que el plan
todavía no terminó.

Commits: `9f076b8` (`docs(readme): agregar la puesta en marcha del sistema y el camino
por WiFi`), pusheado a `main`. En los hermanos **no se commiteó nada**.

## Qué se midió

Todo el 2026-10-29, en la máquina de la tesis, Redmi Note 9S (Android 11) contra el PC,
los dos en `Marmolejo Villalba_5G`:

| Qué | Valor | Cómo |
|---|---|---|
| Arranque del STT | ~10 s hasta `/health` | `docker compose up -d` desde `../signall-backend` |
| Arranque del backend | ~52 s hasta `/health` | ídem, con modelo `medium` en GPU y catálogo de Neon |
| ADB por WiFi | `adb tcpip 5555` + `adb connect 192.168.80.19:5555` | la entrada USB y la de red conviven; mDNS lo anuncia como `adb-1ed6699f` |
| Instalación por red | 26 s con `ANDROID_SERIAL`, 10 s sin cable | `installDebug -Psignai.backendWsUrl=ws://192.168.80.49:8080/ws/translate` |
| Sesión | `SignAiNet: sesión abierta: ws://192.168.80.49:8080/ws/translate` | `adb logcat` por WiFi |
| Frase "Hola como estas" | 2 glosas, envío→primera seña **205 ms**, 2 señas en **3 956 ms** | una sola frase, no un promedio |
| Puerto 15672 de RabbitMQ desde el teléfono | `200` | prueba de que Docker se salta `ufw` |
| Puerto 8080 desde el teléfono | `200`, **sin regla de `ufw`** | ídem |
| Puerto 443 (Caddy) | `404` con `-k`; handshake rechazado sin `-k` | `curl` del teléfono; el certificado no está en el almacén del sistema |
| `https://192.168.80.49/apk/` | `200`, y el APK de 40,8 MB también | `curl -k` desde el teléfono |

## Qué se decidió

Ninguna decisión es del owner todavía. Se dejó escrito, y hay una diferencia que **no se
resolvió por cuenta propia**:

- `plan-wifi.md` §4, decisión 1 (2026-10-27) cierra que **el 8080 no se abre para la LAN**.
  Hoy el backend corre en la red del compose con `8080:8080` publicado, y Docker inserta
  sus reglas antes que `ufw`: el puerto queda abierto para cualquier dispositivo de la red
  **sin ninguna regla**. Medido con 15672 y con 8080.
- El plan midió el 2026-10-27 que el 443 y el 8080 daban **timeout** desde el teléfono.
  Eso corresponde a cuando el backend corría en red de host (`docker-compose.host.yml`),
  donde el tráfico sí pasa por el `INPUT` de `ufw`. Las dos mediciones son correctas con
  su configuración; lo que cambió es el compose del backend.

## Qué quedó pendiente

1. **Decisión del owner sobre el 8080.** O se acepta que Docker lo publique (y se corrige
   `plan-wifi.md`), o se cierra con reglas en `DOCKER-USER` / volviendo a red de host.
   Es en `../signall-backend`.
2. **`plan-wifi.md` fase 3** (dirección editable en AVANZADO): es lo que hace que cambiar
   de red no obligue a recompilar. Sin eso, "en cualquier red" no se cumple.
3. **Windows**: el procedimiento del README está marcado como *esperado*, no verificado.
   No hay una máquina con Windows para probarlo.
4. **`adb tcpip 5555` no es permanente**: se apaga al reiniciar el teléfono y hace falta el
   cable una vez. La depuración inalámbrica del teléfono sí está prendida
   (`adb_wifi_enabled=1`).
5. **`restart=no`** en `backend` y `stt` del compose: después de reiniciar el PC no vuelve
   nada solo. `caddy` sí tiene `unless-stopped`.
6. La latencia por WiFi **no es comparable** con la del cable: el plan ya midió hasta
   227 ms de RTT en esta red.
