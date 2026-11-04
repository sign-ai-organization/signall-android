# Los 39 audios de H1: organizados y verificados

**Fecha:** 2026-11-03, noche · **Rama:** `main`

## Qué llegó

El owner grabó sus **39 audios** de los cuatro conjuntos de la sección 8 del plan de pruebas
(H1: 20 de AU-01, los 10 de AU-02, los 8 de AU-03 y el de AU-04) y los pasó en
`AudiosTesisJD.zip`, en la raíz del repo, con los archivos nombrados `01.m4a` … `38.m4a` más
`Habla Continua.m4a`. No había ninguna relación escrita entre el número de archivo y el ID
del plan.

## Qué se hizo

- **Identificación por contenido, no por número.** Se transcribieron los 39 con
  faster-whisper `small` en español, corriendo dentro de la imagen `sign-ai-stt-stt:latest`
  que ya estaba en Docker, y se comparó palabra por palabra contra el texto que el plan pide
  para cada ID. Los 39 coinciden con la frase que les toca y los cuatro conjuntos quedaron
  completos: 20 + 10 + 8 + 1.
- **Organización fuera de los repos**, en `../data/audios/` (junto a `../data/LSC50.pdf`), un
  archivo por ID y agrupado por conjunto: `AU-01/`, `AU-02/`, `AU-03/`, `AU-04/`. El `.m4a`
  original no se recodificó.
- **Copia derivada a 16 kHz** en `16k/`: los originales son 48 kHz AAC de iPhone y el
  servicio trabaja a 16 kHz mono, así que sin esa copia cada prueba tiene que convertir antes
  de mandar.
- **El ZIP se guarda intacto** en `origen/` (sha256 `3bc96a02…f218cd`), para poder rehacer la
  copia sin depender del teléfono.
- **`MANIFIESTO.md` y `manifiesto.csv`** con, por audio: duración, sha256, tramos de sonido
  por encima de −40 dB, silencio inicial y final, eventos, el texto del plan, la
  transcripción y las diferencias. Para AU-04, además, el tiempo de cada una de las 20
  frases y la pausa hasta la siguiente.

## Qué se midió y qué salió

- **AU-01:** los 20 se transcribieron completos y en el orden del plan. En los diez con ruido
  el silencio final no existe porque el ruido llena el archivo, y así se grabó. El ruido de
  los diez y el de `AU-03-003` cae en el mismo rango de piso (−44 a −36,5 dB contra −37,2 dB),
  y `AU-03-004` está unos 8 dB arriba, como pide «más cerca de la fuente»: la comparación
  entre los dos conjuntos es válida.
- **AU-02:** ocho de los diez van a cerrar por donde el plan dice, comprobado con el mismo VAD
  del servicio (WebRTC, agresividad 2, tramas de 30 ms) y la regla de cierre de 1 500 ms del
  código de `sign-ai-stt`.
- **AU-03:** en los ocho no hay voz. Lo que el transcriptor devolvió sobre el ruido («Música»,
  «¡Suscríbete!») es alucinación del modelo, no audio.
- **AU-04:** las 20 frases están, en orden y una sola vez. Las pausas no están completas: con
  el VAD salen **17 rachas de 1,5 s o más, no 19**, porque dos pausas quedan cortadas por la
  respiración y una mide 1,2 s.

## Qué quedó abierto

- **`AU-02-007` y `AU-02-008` hay que regrabarlos.** El primero tiene la pausa interna en
  1,50 s, así que el servicio cierra por silencio a los 8,9 s en vez de por el límite de 8 s,
  que es lo que la prueba mide; los dos tienen 0,75 s y 1,17 s de silencio al final en vez de
  los 3 s que pide el plan, así que la segunda mitad espera al mensaje de fin.
- **Dos palabras por oír:** en `AU-02-004` el transcriptor oyó «se levantan **bien**
  temprano» y en `AU-02-005` «los niños y niñas». Puede ser el transcriptor o una palabra de
  más o de menos al leer, y esos dos textos son la referencia contra la que se mide.
- **Falta la ficha de la grabación:** cuál fue el ruido y a qué distancia, con qué celular y
  en qué cuarto. Sin el ruido y la distancia, los diez de Julián con ruido no se pueden
  grabar igual.
- **Faltan los 40 de Julián, Samuel y Sergio.** Se organizan con la misma regla cuando
  lleguen.
- No se tocó código de la app, así que no hubo nada nuevo que probar en el dispositivo.

## Cómo se reproduce

```bash
# el corpus
ls ../data/audios/AU-0*/            # un archivo por ID
ls ../data/audios/16k/              # la copia que consume el STT

# volver a medir un audio con el VAD del servicio
docker run --rm -v ../data/audios/16k:/audios16k:ro --entrypoint python \
  sign-ai-stt-stt:latest -c "
import wave, webrtcvad
v = webrtcvad.Vad(2)
w = wave.open('/audios16k/AU-02-007.wav','rb')
pcm = w.readframes(w.getnframes())
print(sum(v.is_speech(pcm[i:i+1920], 16000) for i in range(0, len(pcm)-1920, 1920)))
"
```
