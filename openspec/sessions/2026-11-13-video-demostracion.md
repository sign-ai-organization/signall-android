# Video de demostración de la app (HyperFrames)

**Fecha:** 2026-11-13 · **Rama:** `FableAvatar` · **Sin commit** (lo decide el owner)

## Qué llegó

El owner pidió instalar [HyperFrames](https://github.com/heygen-com/hyperframes) y hacer un
video de ~30 s (±5) que **muestre** las funcionalidades de la app, sin venderla, con los
colores del modo oscuro de la app como base. Tres decisiones suyas al empezar: el proyecto
vive en un repo hermano (`../signall-video`), se rinden los dos formatos (horizontal y
vertical), y el material es la app real grabada en el Redmi.

## Qué se hizo

1. **HyperFrames instalado**: `npx hyperframes init sign-ai-video` (CLI 0.9.41, Node 26,
   FFmpeg 9). Las skills quedaron en `~/.config/opencode/skills/`.
2. **Proyecto `../signall-video`** (hermano, como el resto del sistema):
   `index.html` (horizontal 1920×1080), `compositions/s1..s7-*.html` (una escena por beat),
   `assets/scene.css` (tokens, tipografía, layout de los dos formatos), `assets/clips/`
   (cortes), `assets/audio/bed.wav` (música generada con `tools/make_bed.py`).
3. **Proyecto `../signall-video-vertical`**: generado por `tools/build_formats.py` a partir
   de `index.html`, con `assets/` y `compositions/` enlazados al proyecto horizontal. Es un
   segundo proyecto porque HyperFrames solo admite un HTML raíz con
   `data-composition-id` por proyecto, y no deja que un `data-composition-src` salga de la
   carpeta.
4. **Material real**: backend + STT levantados (`docker compose up -d` en
   `../signall-backend`, puertos 8080 y 8000), app instalada con
   `-Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` y `adb reverse tcp:8080`.
   Seis grabaciones de pantalla (`assets/raw/rec1..rec7.mp4`) y siete cortes.
5. **Guion** (opción A, elegida por el owner): una frase —«Hola, buenos días, ¿cómo
   estás?»— de punta a punta; después conversación, ajustes y el cambio de personaje.
   Detalle en `../signall-video/SPEC.md`.
6. **Renders**: `../signall-video/renders/signall-horizontal.mp4` (1920×1080, 5,8 MB) y
   `../signall-video-vertical/renders/signall-vertical.mp4` (1080×1920, 6,1 MB), los dos de
   30,9 s a 30 fps.

## Qué se decidió

- **Sin voz, solo música** (owner). El video no demuestra cómo suena el dictado; a cambio
  se agrega un **muñeco** de línea que representa a la persona que habla, dibujado con la
  paleta de la app y sin pretender ser UI.
- **Nada inventado dentro del teléfono.** Los beats son la app real; el copy y los chips de
  glosas viven fuera del marco.
- **Paleta y tipografía de la app**: `Color.kt` (`DarkColors`) y Liberation Sans, la misma
  de `app/src/main/res/font/`. Logo: `logo_signall_on_dark.png`.
- **Música sintetizada localmente** con numpy en lugar de una pista con licencia, para no
  meter material de terceros en la tesis.

## Qué se midió

- **Duración 30,90 s** y 927 fotogramas a 30 fps (render de entrega).
- **−22,3 LUFS integrados**, pico −9,1 dBFS en el render horizontal (`ffmpeg ebur128`).
- **Transcripción real**: el STT devolvió la frase completa (31 caracteres) reproduciendo
  `conversacion_1.wav` por los parlantes de la laptop; `adb logcat -s SignAiState` da
  `dictado cerrado: 31 caracteres` y el historial de la app la conserva.
- **Glosas reales** de esa frase: HOLA · BUENOS DÍAS · BIENVENIDO · CÓMO ESTÁS, con
  `signStart` en 1,23 s / 3,27 s / 5,68 s / 7,92 s desde el toque en Traducir.
- `npx hyperframes check`: cero errores, cero avisos en el horizontal; en el vertical queda
  un aviso de layout en `#s3-chips` que es artefacto del auditor (mide con un lienzo de
  1920×1080), no del render.

## Segunda iteración (misma fecha)

El owner revisó el video y pidió cambios de fondo: cambiar la música por
`/home/diegocachy/Descargas/MusicaFondoAnimYT.mp3`, agregar una introducción corta, mostrar
más funcionalidades (escribir, dictar y modo de tiempo real), juntar «Traducir» con la
representación, juntar Ajustes con el personaje y dejar el cierre con una sola línea:
«Prototipo de Tesis de Proyecto de Grado», centrada y abajo.

**Guion nuevo, 40,4 s**, en `SPEC.md`: intro → escribir → hablar → tiempo real →
conversación → ajustes y personaje → pie.

**La introducción no es una pregunta.** La primera versión de esta iteración abría con
«¿Alguna vez no pudiste comunicarte con alguien que usa lengua de señas?». El owner la
rechazó: pone a quien mira en el lugar de una persona oyente, deja afuera a las personas
sordas y suena a aviso. La versión final describe lo que hace la app, sin apelar a nadie:
«Español hablado o escrito, representado en Lengua de Señas Colombiana», con «Un avatar 3D
lo representa seña por seña».

Grabaciones nuevas en el Redmi (wifi, `adb connect 192.168.78.144:5555`):

- `raw/rec8_escribir.mp4`: el campo se enfoca con los IME **deshabilitados** (así no aparece
  el teclado) y el texto se inyecta con `adb shell input text` en tramos. El tramo de
  escritura va a 1,75x en el corte.
- `raw/rec9_tiempo_real.mp4`: **TR** encendido y los fixtures `hola`, `buenos_dias` y
  `como_estas` separados por ~2 s de silencio, para que el STT cerrara cada frase. Hubo que
  subir el volumen del altavoz de la laptop a 100 %: al 70 % el micrófono del teléfono no
  detectaba voz.
- `raw/rec10_ajustes.mp4`: drawer Ajustes y cambio de Juan a Pauline, con salto al momento
  en que Pauline ya está en escena.

Los cortes quedaron en `tools/cut_clips.py` (inicio, duración y velocidad por tramo), y se
borraron las escenas viejas (`s1-voice`, `s2-translate`, `s3-sign`, `s6-avatar`,
`s7-endcard`). El muñeco de la primera versión también se fue: la intro es tipográfica.

**Música**: `MusicaFondoAnimYT.mp3` recortada de 0 a 40,4 s, con fundido de entrada (1,2 s)
y de salida (2,4 s), a −18,1 LUFS integrados (`ffmpeg ebur128`). El archivo del proyecto es
`assets/audio/musica.mp3`.

**Trampa del vertical que costó tiempo**: en el proyecto 1080×1920 la raíz de cada escena
sigue declarada 1920×1080 (viene del archivo de la escena, no del host). Las escenas con
coordenadas explícitas no lo notan, pero el pie, que se centraba con `left:0; right:0` y
`bottom`, se centraba en 1920 y caía a `bottom:150` sobre 1080. Se ancló con `top:1737px;
width:1080px` en la media query del vertical. Comprobado con el auditor (`bbox` del texto en
x 224–856, y 1742–1773) y con un `snapshot`.

**Medido**: 40,4 s y 1212 fotogramas a 30 fps; `hyperframes check` con **cero errores y cero
avisos en los dos formatos**; glosas del modo en vivo (HOLA, BUENOS DÍAS, CÓMO ESTÁS) visibles
en el clip a 4,57 s / 8,00 s / 11,63 s. La frase escrita va sin tildes porque `adb input text`
solo acepta ASCII; el teclado no aparece porque los IME están deshabilitados.

**Entregables**: `renders/signall-horizontal.mp4` (1920×1080, 6,1 MB) y
`../signall-video-vertical/renders/signall-vertical.mp4` (1080×1920, 7,6 MB), los dos de
40,4 s a 30 fps y con audio AAC; el render mide **−18,1 LUFS** integrados.

**Teléfono**: se devolvió al estado por defecto (Juan, velocidad 50, interfaz 50) y se
volvieron a habilitar los dos IME que se apagaron para grabar la escritura sin teclado. El
historial de la app conserva las frases de las tomas; no se borró porque es dato de la app.

**Pendiente**: commit (ninguno de los dos proyectos está en git) y una toma con voz humana si
se quiere oír el dictado.

## Qué quedó pendiente

- **Commit**: ninguno de los dos proyectos de video está en git; el owner decide si
  `../signall-video` (y el vertical) se versionan aparte.
- **Voz**: si se quiere mostrar el dictado con audio, hace falta una toma con voz humana
  (no se puede inyectar audio al micrófono del Redmi sin root). El guion ya tiene el lugar.
- **Música con licencia**: hoy es un pad generado; si se prefiere una pista real, cambiar
  `assets/audio/bed.wav` y re-renderizar.
- **El teléfono quedó con la app en el estado del video** (personaje Pauline, velocidad 70).
  Si se quiere de vuelta el default, Ajustes → Personaje → Juan y velocidad 50.
