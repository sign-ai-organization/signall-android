# Prueba del descubrimiento automático en el Redmi Note 9S

**Fecha:** 2026-11-14, noche.
**Dispositivo:** Redmi Note 9S (`curtana`, Android 11, API 30), `192.168.78.144/24`.
**PC:** `192.168.78.126/24`, misma red Wi-Fi de la casa.
**Backend:** `signall-backend` en Docker (`backend`, `stt`, `caddy`), catálogo con 75
glosas. `/health` responde `status: ok`, `catalog_loaded: true`.

La app se instaló **una sola vez** (`./gradlew installDebug`, APK `debug`). Después de
esa instalación no se recompiló ni se reinstaló nada: todos los cambios de red y de
preferencias se hicieron con el mismo APK.

## 1. Primer arranque, sin endpoint guardado: mDNS

Se borraron los datos de la app (`pm clear`) y se publicó el servicio con el contenedor
de `tools/mdns` (`_signall._tcp`, puerto 8080).

```text
11-14 21:24:51.899 I SignAllState: backend session starting
11-14 21:24:51.899 I SignAllNet: buscando backend: recordado=ninguno
11-14 21:24:51.930 I SignAllNet: buscando por mDNS: _signall._tcp.
11-14 21:24:52.304 I SignAllNet: encontrado: ws://192.168.78.126:8080/ws/translate (mDNS SignAll en diegohypr-2)
11-14 21:24:53.037 I SignAllState: backend para esta sesión: ws://192.168.78.126:8080/ws/translate
11-14 21:24:53.126 I SignAllNet: sesión abierta: ws://192.168.78.126:8080/ws/translate (SIN CIFRAR)
11-14 21:24:53.933 I SignAllNet: encontrado: sesión abierta contra ws://192.168.78.126:8080/ws/translate
```

**Resultado:** 374 ms desde el arranque hasta el anuncio resuelto, y 1,2 s hasta el
socket abierto. El endpoint quedó guardado en
`shared_prefs/signall.xml` → `backend_ws_url`.

## 2. Caída y vuelta de la red: endpoint recordado

Se apagó y encendió el Wi-Fi del teléfono (`svc wifi disable; sleep 6; svc wifi enable`).
La app siguió viva y, cuando la red volvió, reabrió sola la sesión:

```text
11-14 21:23:47.540 I SignAllNet: buscando backend: recordado=ws://192.168.78.126:8080/ws/translate
11-14 21:23:47.588 I SignAllNet: encontrado: ws://192.168.78.126:8080/ws/translate (recordado)
11-14 21:23:47.596 I SignAllState: backend para esta sesión: ws://192.168.78.126:8080/ws/translate
11-14 21:23:47.625 I SignAllNet: sesión abierta: ws://192.168.78.126:8080/ws/translate (SIN CIFRAR)
```

**Resultado:** 48 ms para validar el endpoint recordado y 85 ms hasta el socket. La app
volvió a *Inactivo* sin recompilar ni reinstalar.

## 3. Endpoint guardado que ya no existe: escaneo de subred

Para simular que el backend cambió de IP, se dejó el endpoint recordado apuntando a
`192.168.78.200` (una IP que no responde) y se abrió la app. No se recompiló nada. En ese
momento el publicador mDNS de prueba había terminado, así que el anuncio no estaba en la
red y el único camino que quedaba era el escaneo.

```text
11-14 21:24:08.453 I SignAllNet: buscando backend: recordado=ws://192.168.78.200:8080/ws/translate
11-14 21:24:08.874 I SignAllNet: intento fallido: el endpoint recordado ws://192.168.78.200:8080/ws/translate no responde; se vuelve a descubrir
11-14 21:24:08.901 I SignAllNet: buscando por mDNS: _signall._tcp.
11-14 21:24:11.398 I SignAllNet: sin resultados de mDNS en 2500 ms
11-14 21:24:11.444 I SignAllNet: buscando: 253 hosts de la subred local (http://…:8080/health)
11-14 21:24:13.311 I SignAllNet: encontrado: ws://192.168.78.126:8080/ws/translate (escaneo de subred)
11-14 21:24:13.313 I SignAllState: backend para esta sesión: ws://192.168.78.126:8080/ws/translate
11-14 21:24:13.397 I SignAllNet: sesión abierta: ws://192.168.78.126:8080/ws/translate (SIN CIFRAR)
11-14 21:24:15.512 I SignAllState: silencio del STT: 1500 ms
```

**Resultado:** el endpoint malo falló en 421 ms, mDNS agotó sus 2,5 s y el escaneo
encontró el backend en **1,87 s**. La preferencia se reescribió con la IP buena
(`run-as com.signall.app cat shared_prefs/signall.xml`).

## 4. Cambio de red con la app abierta: sin reiniciar

Este es el caso que el owner reportó ("me tocó reiniciar la aplicación"). No se reinstaló
nada: el mismo proceso (`PID 6208`) siguió vivo todo el tiempo.

A las **21:38:25** la app se abrió con el teléfono en la red de un hotspot (`172.20.10.3`,
backend en `172.20.10.2`) y resolvió por mDNS en **415 ms**:

```text
11-14 21:38:25.251 I SignAllNet: buscando backend: recordado=ninguno
11-14 21:38:25.280 I SignAllNet: buscando por mDNS: _signall._tcp.
11-14 21:38:25.666 I SignAllNet: encontrado: ws://172.20.10.2:8080/ws/translate (mDNS SignAll en diegohypr-2)
11-14 21:38:27.019 I SignAllNet: sesión abierta: ws://172.20.10.2:8080/ws/translate (SIN CIFRAR)
```

A las **21:38:39** se apagó el Wi-Fi del teléfono y el hotspot desapareció. El teléfono quedó
sin red durante ~80 minutos (sin datos móviles). Cuando volvió a la red de casa, a las
**23:02:42**, la app seguía abierta y se reconectó sola, con el endpoint guardado ya inválido
(`172.20.10.2` no existe en la red nueva), así que el escaneo de subred encontró la PC:

```text
11-14 23:02:42.040  6208  6290 I SignAllNet: encontrado: ws://192.168.78.126:8080/ws/translate (escaneo de subred)
11-14 23:02:42.137  6208  6208 I SignAllState: backend para esta sesión: ws://192.168.78.126:8080/ws/translate
11-14 23:02:42.168  6208  6310 I SignAllNet: sesión abierta: ws://192.168.78.126:8080/ws/translate (SIN CIFRAR)
```

**Resultado:** 128 ms desde el endpoint encontrado hasta el socket abierto, **sin reiniciar la
app y sin recompilar**. El proceso (`6208`) es el mismo que llevaba 84 minutos abierto. El log
no guarda el instante exacto en que el teléfono recuperó la red, así que el tiempo total desde
que volvió el Wi-Fi no se puede afirmar con estos datos.

### El aviso de red del sistema

La medición anterior demuestra la reconexión, pero el reintento periódico también la habría
alcanzado en ≤15 s. Para que la reacción sea inmediata —y no dependa del latido de 20 s— se
agregó `AndroidNetworkWatcher` (`ConnectivityManager.registerDefaultNetworkCallback`): corta el
socket en cuanto cambia la red y adelanta la espera del backoff. Además, `MainActivity.onStart`
reintenta si la pantalla vuelve al frente sin sesión.

Esos dos disparadores están cubiertos por pruebas unitarias (cambio de red que reabre la sesión
sin avanzar el reloj y cambio de red que cierra el socket viejo). La verificación física del
log `cambió la red: se reintenta el backend ahora` queda pendiente de una corrida con el
teléfono conectado y el backend arriba.

## Qué cubre y qué no

Cubierto en el dispositivo:

- mDNS con el tipo `_signall._tcp` y el contenedor de `tools/mdns`.
- Validación del endpoint recordado antes de confiar en él.
- Escaneo de la subred privada con `/health` y actualización del endpoint guardado.
- Reconexión después de una caída real de Wi-Fi, sin recompilar ni reinstalar.
- Reconexión sola tras un cambio de red de 84 minutos, con el endpoint guardado inválido.

Pendiente de una corrida aparte: **el teléfono como hotspot** (la app en el teléfono que
comparte la conexión y la PC como cliente). El código ya incluye las interfaces
`ap0`/`swlan0` para ese caso, pero la prueba física con la PC dentro del hotspot no se
corrió en esta sesión.

También queda pendiente la línea `cambió la red: se reintenta el backend ahora` en el log de
un teléfono real: es la parte nueva del aviso de red, y las pruebas unitarias cubren la
lógica pero no la entrega del callback de Android.
