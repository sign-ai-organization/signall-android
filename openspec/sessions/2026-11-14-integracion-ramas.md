# Integración de las ramas de descubrimiento y de CPU/GPU, y prueba en el Redmi

**Fecha:** 2026-11-14 · **Ramas integradas:** `feat/descubrimiento-backend` en este repo,
`feat/declarative-cpu-gpu` en `signall-backend` y `signall-stt`. Las tres a `main` con merge
(no había fast-forward: `main` ya tenía el retiro del CI).

## Qué llegó

Otra sesión, en otra computadora, dejó tres ramas sin mergear y un commit sin traer en
`signall-docs`. El owner pidió integrarlas y probarlas en el celular.

- **`signall-android`** — descubrimiento automático del backend (endpoint recordado → mDNS
  `_signall._tcp` → escaneo de la subred local → `BuildConfig` como último recurso) y
  reconexión sin reiniciar (aviso de cambio de red + `onStart`).
- **`signall-backend` + `signall-stt`** — la CPU pasa a ser el caso base (`small`/`cpu`/`int8`)
  y la GPU es opt-in con `docker-compose.gpu.yml`; el `Dockerfile` del backend instala torch
  desde el índice de CPU.
- **`signall-docs`** — la entrada de `cambios/` de esa sesión, que estaba pusheada pero no
  traída al clon.

Los merges fueron limpios; el único archivo tocado por los dos lados (`CLAUDE.md` y `README.md`
del backend) se auto-fusionó sin conflicto.

## Qué se arregló

**`NetworkWatcher` descartaba el aviso de red perdida.** El estado se guardaba en una
`AtomicReference` y `onLost` usaba `compareAndSet`, que compara **identidad de objeto**. Android
entrega una instancia distinta de `Network` para la misma red (mismo `netId`, otro objeto), así
que el `compareAndSet` fallaba y la app no cortaba el socket al quedarse sin red. Medido en el
Redmi el 2026-11-14: al apagar el Wi-Fi **no** aparecía `aviso de red: perdida` y la reconexión
dependía de que OkHttp notara la caída. Ahora el estado vive detrás de un candado y se compara
con `equals`; re-medido: `perdida 158` → `cambió la red: se reintenta el backend ahora` en el
mismo milisegundo, y al volver el Wi-Fi `disponible 159` → `encontrado` en 58 ms.

## Qué se midió (Redmi Note 9S, Android 11, `adb` por Wi-Fi)

Backend en la PC de la tesis (`192.168.78.102:8080`, Docker con red de host) y mDNS anunciado
con `avahi-publish-service` durante la prueba.

| Caso | Resultado |
|---|---|
| Arranque en frío sin endpoint recordado, con mDNS | `encontrado ... (mDNS)` en **56 ms**; sesión abierta a 1,1 s |
| Arranque en frío sin mDNS | **253 hosts** escaneados, `encontrado ... (escaneo de subred)` en **1,75 s** |
| Endpoint recordado válido | probado y adoptado en **35–79 ms** |
| Backend detenido y levantado | la app sola volvió a conectar **sin reiniciar**, 18 s después (incluye el arranque del contenedor) |
| Wi-Fi apagado 14 s y prendido | `perdida 158` → reintento inmediato; al volver, `encontrado` en **58 ms** y sesión en **134 ms**, **mismo pid** |
| Frase de punta a punta | `hola dias` → 1 glosa (`HOLA`), frase 1386 ms, oración→avatar 136 ms, envío→primera seña 1517 ms, representado 2652 ms |

Con eso queda cerrado el pendiente de la sesión anterior: la línea
`cambió la red: se reintenta el backend ahora` **sí** se ve en logcat de un teléfono real.

`./gradlew build` en verde (incluye ktlint y lint) y **256 pruebas**. `tools/calidad/verificar.sh`
en verde en `signall-backend` y `signall-stt`.

## Qué se verificó del stack sin GPU

- Imagen del backend construida con el índice de CPU: **10,1 GB → 2,58 GB**
  (`torch 2.15.0+cpu`, `sentence-transformers` carga bien).
- Perfil de CPU del STT levantado aparte (`small`/`cpu`/`int8`): modelo cargado y precalentado
  en **1176 ms**, `/health` con `device: cpu`.
- `docker compose config` de los dos perfiles en los dos repos: sin reserva NVIDIA en el caso
  base y con `driver: nvidia` al agregar el override.

## Qué quedó pendiente

- **El Redmi como hotspot** sigue sin probarse (la app en el teléfono que comparte y la PC como
  cliente): el código contempla `ap0`/`swlan0` pero no se ejecutó. Igual que en la sesión
  anterior.
- **El firewall muerde y no es obvio.** En esta PC `ufw` solo tenía el 443 abierto para la
  subred vieja `192.168.80.0/24`: el teléfono hacía ping a la PC pero el **8080 daba timeout**, y
  la app solo decía `sin resultados` en `SignAllNet`. Se resolvió con
  `sudo ufw allow from 192.168.78.0/24 to any port 8080 proto tcp`. Está documentado en
  `docs/DESCUBRIMIENTO-BACKEND.md` §2.4; ojo, los **puertos publicados por Docker no los filtra
  `ufw`** y los del backend en red de host sí.
- **Decisiones para el owner**, medidas pero no aplicadas:
  - El override de GPU del backend recompila su imagen con torch de PyPI (10,1 GB) y el backend
    **no usa CUDA**: sus embeddings corren en CPU. Quitarlo ahorraría 7,5 GB también en la PC
    con GPU.
  - El perfil de CPU del STT todavía instala **2,27 GB** de `nvidia-*` (`cublas` 856 MB,
    `cudnn` 1184 MB, `nvrtc` 227 MB) fijados en `requirements.txt`. Moverlos a un archivo de
    requisitos de GPU dejaría la imagen de CPU mucho más chica, pero cambios en ese archivo hay
    que probarlos también con GPU.
- El anuncio mDNS de la prueba se hizo con `avahi-publish-service` como usuario normal; el
  archivo en `/etc/avahi/services/` (el que documenta el repo) necesita `sudo` y no se instaló.
