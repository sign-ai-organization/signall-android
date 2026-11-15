# Descubrimiento automático del backend

**Fecha:** 2026-11-14.
**Alcance:** `signall-android` (descubrimiento en la app) y la PC que corre
`signall-backend` (anuncio por mDNS).
**Requisito:** la app encuentra el backend sin una IP horneada, sin un campo de
URL editable y sin pantalla de configuración manual.

Este documento reemplaza la decisión 3 de
[`signall-backend/docs/plan-wifi.md`](../../signall-backend/docs/plan-wifi.md)
("la dirección se cambia en AVANZADO"): el owner pidió que la dirección se
descubra sola. No hay ningún campo de servidor que llenar.

---

## 1. Qué hace la app

Al abrir la app, y otra vez cada vez que se pierde la conexión, se intenta en
este orden:

| Paso | Qué hace | Cuánto espera |
|---|---|---|
| 1 | Prueba el **último endpoint exitoso** guardado en preferencias (`signall` → `backend_ws_url`) | 400 ms |
| 2 | Busca por **mDNS** el servicio `_signall._tcp` | 2,5 s |
| 3 | Escanea la **subred local** con `GET /health` corto y concurrencia limitada | lo que tarde la red |
| 4 | Usa `BuildConfig.BACKEND_WS_URL` como **último recurso** | — |

El primer paso que contesta con el `/health` propio de SignAll gana, se guarda y
es el que se usa para abrir el WebSocket. Si nada contesta, se usa el valor
compilado, que sigue sirviendo para el emulador (`10.0.2.2`) y para
`adb reverse` (`127.0.0.1`).

### Qué dispara un intento de reconexión

La app **no necesita reiniciarse** para volver a encontrar el backend. El bucle
de sesión reintenta para siempre, con espera creciente de 1 a 5 s
(`ReconnectBaseMs`, `MaxReconnectWaitMs`) y sin límite de intentos: el SRS lo
pide así (`main.tex:761-772`).

Además de ese turno, hay dos atajos que saltean la espera:

| Disparador | Qué hace |
|---|---|
| **Cambio de red** (`ConnectivityManager.registerDefaultNetworkCallback`) | Cierra el socket, que quedó atado a una red que ya no existe, y reintenta en el acto. Sin esto había que esperar hasta 20 s al latido (`HeartbeatIntervalSec`) para que OkHttp notara la caída. |
| **Volver a la pantalla** (`MainActivity.onStart`) | Si no hay sesión abierta, la busca ya: el proceso pudo haber quedado congelado en segundo plano esperando el turno del backoff. |

Con eso, el caso "cambié de red y la app siguió en *Desconectado* hasta que la
reinicié" queda cubierto por tres caminos: el aviso del sistema, el regreso a
la pantalla y el reintento periódico. En el log se ve como
`cambió la red: se reintenta el backend ahora` seguido de `buscando backend: …`.

### Por qué el tipo es `_signall._tcp`

`_signall` es el nombre del producto y `_tcp` el transporte: HTTP y WebSocket
van sobre TCP. No se usa `_http._tcp` porque ese tipo lo anuncian impresoras,
routers y cámaras, y el descubrimiento tendría que probar servicios ajenos. El
punto final (`_signall._tcp.`) es obligatorio para `NsdManager`.

### Qué se acepta como "el backend de SignAll"

Un `200 OK` no basta. La app exige el JSON de `/health` con:

```json
{
  "status": "ok",
  "cache_loaded": true,
  "catalog_loaded": true,
  "catalog_glosses": 50
}
```

`status` tiene que ser `"ok"` y `catalog_loaded` tiene que ser `true`: un
backend degradado contesta pero no puede traducir, así que aceptarlo sería
encontrar algo que no sirve. `cache_rows` es opcional (se lee como `0`).

### Qué se escanea y qué no

- Solo IPv4 **privada RFC 1918**: `10/8`, `172.16/12`, `192.168/16`.
- Nunca un rango público.
- Se recorta a `/24` alrededor de la IP del teléfono: una `/16` son 65.534
  hosts y el escaneo dejaría de ser corto.
- Se excluyen la propia IP, la dirección de red y la de broadcast.
- Se deduplican las interfaces: la misma red reportada por `LinkProperties` y
  por `NetworkInterface` se prueba una sola vez.
- Se excluyen las interfaces celulares (`rmnet*`, `ccmni*`, …) y las de túnel.
  Un CGNAT con `10.x` también es "privado", pero no es la LAN del backend.
- En el **hotspot del propio teléfono** se agrega la interfaz del punto de
  acceso (`ap0`, `swlan0`, …), porque la red activa es la de datos móviles y la
  subred que interesa es la del hotspot.

El esquema lo decide el build type:

| Build | Pista | Escaneo | mDNS |
|---|---|---|---|
| `debug` | `ws://` | `http://<ip>:8080/health` | puerto anunciado; sin `tls` → `ws://` |
| `demo` / `release` | `wss://` | `https://<ip>:443/health` | puerto anunciado; `tls=1` o 443 → `wss://` |

---

## 2. Anunciar con Avahi en Linux

### 2.1 Instalar y comprobar

```bash
sudo apt install avahi-daemon avahi-utils
systemctl status avahi-daemon
```

### 2.2 El archivo de servicio

El repositorio trae el archivo listo en
[`tools/mdns/signall.service`](../tools/mdns/signall.service). Se copia y se recarga:

```bash
sudo cp tools/mdns/signall.service /etc/avahi/services/signall.service
sudo systemctl restart avahi-daemon
```

El contenido es el siguiente. El puerto es el **de entrada real** de la PC:

```xml
<?xml version="1.0" standalone='no'?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name replace-wildcards="yes">SignAll en %h</name>
  <service>
    <type>_signall._tcp</type>

    <!-- Backend en Docker: el puerto publicado en la PC. -->
    <port>8080</port>

    <txt-record>path=/ws/translate</txt-record>
    <txt-record>health=/health</txt-record>
    <txt-record>scheme=ws</txt-record>
  </service>
</service-group>
```

Verificar:

```bash
avahi-browse -rt _signall._tcp
```

`avahi-browse` tiene que listar el servicio con la IP y el puerto. Si el nombre
sale con `%h`, es el hostname de la PC; la app no depende del nombre.

### 2.3 Con Caddy en el 443

Si el backend se publica por Caddy, el anuncio apunta a Caddy, no al 8080. Es
el puerto al que la app debe conectarse. El archivo está en
[`tools/mdns/signall-caddy.service`](../tools/mdns/signall-caddy.service) y se
copia con el mismo nombre que el otro (los dos no pueden convivir):

```xml
<service>
  <type>_signall._tcp</type>
  <port>443</port>
  <txt-record>path=/ws/translate</txt-record>
  <txt-record>health=/health</txt-record>
  <txt-record>scheme=wss</txt-record>
  <txt-record>tls=1</txt-record>
</service>
```

El certificado de Caddy tiene que cubrir la IP o el nombre que resuelve el
teléfono. El build `demo` confía en la CA de laboratorio instalada por el
usuario; el build `release` solo confía en las CA del sistema, así que para
producción hace falta un certificado de una CA pública y un dominio, no una IP
con CA local.

### 2.4 Cortafuegos

El mDNS es **UDP 5353** y el backend es **TCP** en el puerto que se anuncia:

```bash
# mDNS, solo desde la subred local
sudo ufw allow from 192.168.43.0/24 to any port 5353 proto udp comment 'SignAll mDNS'

# Backend en Docker
sudo ufw allow from 192.168.43.0/24 to any port 8080 proto tcp comment 'SignAll backend'

# ...o detrás de Caddy
sudo ufw allow from 192.168.43.0/24 to any port 443 proto tcp comment 'SignAll Caddy'
```

Ajustar `192.168.43.0/24` a la subred real (en el hotspot del Redmi suele ser
`192.168.43.0/24`; en MIUI también puede ser `192.168.232.0/24`). Se comprueba
con `ip -4 addr` en la PC.

> Docker inserta sus reglas de `iptables` antes que `ufw`: un puerto publicado
> con `-p 8080:8080` puede quedar visible aunque `ufw` esté en `DROP`. Para que
> el escaneo no dependa de eso, se recomienda publicar el 8080 solo en la
> interfaz de la LAN o confiar en las reglas de Docker.

---

## 3. Anunciar con un contenedor pequeño

Cuando no se quiere instalar Avahi en la PC, o cuando el backend ya vive en
Docker, se levanta un contenedor que solo anuncia. Todo está en
[`tools/mdns/`](../tools/mdns/):

```bash
cd tools/mdns
docker compose up -d --build
docker compose logs -f      # Avahi dice si el anuncio quedó publicado
```

`signall-mdns/Dockerfile`:

```dockerfile
FROM alpine:3.20
RUN apk add --no-cache avahi avahi-tools dbus
COPY signall.service /etc/avahi/services/signall.service
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
```

`signall-mdns/entrypoint.sh`:

```sh
#!/bin/sh
set -e
mkdir -p /run/dbus
dbus-daemon --system --fork
exec avahi-daemon --no-chroot --debug
```

`signall-mdns/docker-compose.yml`:

```yaml
services:
  mdns:
    build: .
    container_name: signall-mdns
    network_mode: host   # imprescindible en Linux: el multicast tiene que salir
    restart: unless-stopped
```

Con `network_mode: host`, el contenedor ve la interfaz de la PC y su multicast
llega al hotspot o al Wi-Fi. En Docker Desktop (macOS/Windows) el modo host no
funciona para multicast: ahí se instala Avahi en el sistema o se usa una VM
Linux.

`signall.service` es el mismo XML de §2.2. El puerto que se anuncia es el de la
PC, no el interno del contenedor.

---

## 4. Prueba en el Redmi por hotspot

El objetivo es comprobar que, **sin recompilar ni reinstalar**, la app se
conecta después de cambiar de red.

1. En el Redmi: activar el hotspot y anotar el nombre y la contraseña.
2. En la PC: conectarse a ese hotspot y comprobar la IP:
   ```bash
   ip -4 addr show | grep 'inet '
   ```
3. Levantar el backend y el anuncio:
   ```bash
   cd signall-backend
   docker compose up -d
   # y el servicio de Avahi o el contenedor de §3
   ```
4. Comprobar desde la PC que el health responde y que el anuncio existe:
   ```bash
   curl -s http://127.0.0.1:8080/health
   avahi-browse -rt _signall._tcp
   ```
5. Instalar la app (`./gradlew installDebug`) y abrirla. Con el teléfono
   conectado por USB para leer el log:
   ```bash
   adb logcat -s SignAllNet
   ```
   Se espera, en orden:
   ```
   buscando backend: recordado=ninguno
   buscando por mDNS: _signall._tcp.
   encontrado: ws://192.168.43.100:8080/ws/translate (mDNS ...)
   sesión abierta: ws://192.168.43.100:8080/ws/translate (SIN CIFRAR)
   ```
   Si mDNS no aparece, el log muestra `sin resultados de mDNS en 2500 ms` y
   después `buscando: 253 hosts de la subred local`; el resultado esperado es
   `encontrado: ... (escaneo de subred)`.

6. **Cambiar de red sin recompilar**: apagar y volver a prender el hotspot (o
   cambiar el teléfono de Wi-Fi). La PC recibe otra IP. La app detecta la
   caída, vuelve a descubrir y abre el socket contra la IP nueva. En el log se
   ve `intento fallido: el endpoint recordado ... no responde` y luego
   `encontrado`.

Si se cambia la red pero la PC conserva la misma IP, el paso 1 (endpoint
recordado) gana y no hay que esperar el escaneo: es el caso normal.

---

## 5. Diagnóstico rápido

| Síntoma en `adb logcat -s SignAllNet` | Causa probable | Qué revisar |
|---|---|---|
| `sin resultados de mDNS` y el escaneo no encuentra nada | El anuncio no sale de la PC, o el cortafuegos bloquea el puerto | `avahi-browse -rt _signall._tcp`, `ufw status`, `curl http://<ip-pc>:8080/health` |
| `intento fallido: mDNS ... no es el backend de SignAll` | El servicio anuncia otra cosa, o el backend arrancó sin catálogo (`status: degraded`) | `curl .../health` y el log del backend |
| El escaneo toca pocos hosts | La IP del teléfono es celular o la red no es privada | `ip -4 addr` en el teléfono; con hotspot se agrega `ap0`/`swlan0` |
| Se conecta y se corta al cambiar de red | El backend no está en la subred nueva, o el DHCP dio otra IP | Repetir §4; el descubrimiento corre en cada reconexión |
| `Sin conexión con el servidor` en un teléfono físico | No hay backend en la subred y se usó el fallback `10.0.2.2` | Es el comportamiento esperado sin backend; el log dice `sin resultados` |

Las pruebas de la lógica (candidatos, deduplicación, parseo de `/health` y orden
del descubrimiento) corren en la JVM:

```bash
./gradlew testDebugUnitTest --tests "com.signall.app.data.*DiscoveryTest" \
    --tests "com.signall.app.data.NetworkCandidatesTest" \
    --tests "com.signall.app.data.SignAllHealthTest"
```
