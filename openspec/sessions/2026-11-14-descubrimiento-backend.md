# Descubrimiento automático del backend (sin IP horneada)

**Fecha:** 2026-11-14 · **Rama:** `feat/descubrimiento-backend` (no se commitea hasta que
el owner lo pida).

## Qué llegó

La app dependía de `BuildConfig.BACKEND_WS_URL`, una IP horneada al compilar. Cuando el
DHCP cambiaba la dirección había que recompilar e instalar, y en un teléfono físico el
default `10.0.2.2` solo existe en el emulador. El pedido: descubrir el backend solo, **sin**
un campo de URL editable ni una pantalla de configuración manual, y sin escanear rangos
públicos.

## Qué se hizo

**Orden del descubrimiento** (`data/BackendDiscovery.kt`):

1. El último endpoint exitoso, en `SharedPreferences` (`signall` → `backend_ws_url`), se
   **prueba**; si no responde se vuelve a descubrir.
2. mDNS con `NsdManager` y el tipo propio `_signall._tcp.` (`data/MdnsDiscovery.kt`), con
   `MulticastLock` y ventana de 2,5 s.
3. Escaneo de la subred local solo si mDNS no encontró nada (`data/LocalNetwork.kt` +
   `data/NetworkCandidates.kt`): IPv4 RFC 1918, recortada a `/24`, sin IP propia, red ni
   broadcast, con deduplicación y concurrencia de 32.
4. `BuildConfig.BACKEND_WS_URL` como **último recurso**.

**Validación**: `data/BackendHealthProbe.kt` hace `GET /health` con 400 ms de espera y
`data/SignAllHealth.kt` exige `status == "ok"`, `catalog_loaded == true` y los campos
propios (`cache_loaded`, `catalog_glosses`). Un `200` de otro servicio no pasa.

**Integración**: `SignAllWebSocketClient`, `SignAssetRepository` y `BackendHealth` reciben
un proveedor de URL/origen, así que la reconexión usa el endpoint que acaba de encontrar el
descubrimiento. `MainScreenState.connect()` resuelve antes de abrir el socket y
`onConnected()` vuelve a guardar. `MainScreen` cancela todo en `onDispose`
(`MainScreenState.dispose()`), lo que corta NSD, el escaneo y las llamadas HTTP en vuelo.

**Reconexión sin reiniciar** (pedido del owner después de la primera prueba): el bucle ya
reintentaba sin límite, pero el socket muerto se detectaba recién con el latido de 20 s y la
espera creciente no se enteraba de que había red nueva. Se agregó
`data/NetworkWatcher.kt` (`AndroidNetworkWatcher` con
`ConnectivityManager.registerDefaultNetworkCallback`): un cambio de red cierra el socket y
adelanta el reintento. Además `MainActivity.onStart` llama a
`MainScreenState.retryIfDisconnected()`, para cuando la app vuelve al frente sin sesión.

**Seguridad y compatibilidad**: el esquema lo decide el build (`debug` → `http/8080`,
`demo`/`release` → `https/443`); nunca se propone texto claro en un build cifrado. Se
agregaron `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` y `CHANGE_WIFI_MULTICAST_STATE`.

**Documentación**: `docs/DESCUBRIMIENTO-BACKEND.md` (tipo mDNS, Avahi, contenedor, Caddy,
hotspot del Redmi, diagnóstico) y ajustes en `docs/ARRANQUE.md` y `AGENTS.md`.

## Qué se midió

- `./gradlew testDebugUnitTest`: **256 pruebas, todas en verde** (35 nuevas: candidatos y
  deduplicación, parseo de `/health`, probe HTTP real contra `MockWebServer`, orden del
  descubrimiento y los dos disparadores de reconexión).
- `./gradlew ktlintCheck`: en verde.
- `./gradlew build`: en verde (incluye lint de Android, `assembleDebug` y `assembleDemo`).
- `./gradlew assembleDebug`: APK generado en `app/build/outputs/apk/debug/`.

En el Redmi Note 9S: mDNS al primer arranque (374–415 ms), endpoint recordado tras caída de
Wi-Fi (48 ms), escaneo de subred cuando el recordado no existe (1,87 s) y reconexión sola tras
84 minutos sin red, con el proceso original (sin reiniciar). Evidencia en
`docs/mediciones/2026-11-14-descubrimiento-backend.md`.

## Qué quedó pendiente

- **Prueba del Redmi como hotspot** (la app en el teléfono que comparte y la PC como
  cliente): no se pudo ejecutar. El teléfono se probó como **cliente** de un hotspot
  (`172.20.10.x`) y ahí funcionó mDNS y el escaneo, pero la interfaz `ap0`/`swlan0` sigue sin
  confirmación física.
- **Línea `cambió la red: se reintenta el backend ahora`** en un teléfono real: la lógica está
  cubierta por pruebas unitarias, pero el callback de Android no se vio en logcat (el corte de
  Wi-Fi dejó al teléfono sin red antes de poder leerlo).
- **`signall-docs/cambios/`**: queda la entrada de esta sesión en el repositorio de
  documentación (no se commitea desde acá).
- **Android 17**: a partir de targetSdk 37 el acceso a la red local pasa a
  `ACCESS_LOCAL_NETWORK`; hoy (target 36) no aplica y no se declara.

## Qué se decidió

- **Reemplaza la decisión 3 de `signall-backend/docs/plan-wifi.md`** (campo de URL en
  AVANZADO). La decisión es del owner en este pedido: descubrimiento automático, sin campo
  manual.
- **Tipo mDNS `_signall._tcp`** en vez de `_http._tcp`: evita probar impresoras y routers y
  hace explícito el nombre del producto. El puerto anunciado es el de entrada real (8080
  con Docker, 443 con Caddy).
- **El endpoint recordado se prueba, no se confía**: una IP reasignada por DHCP puede
  apuntar a otra máquina.
