# Los 30 audios de los compañeros y la toma nueva de habla continua

**Fecha:** 2026-11-03, noche · **Rama:** `main`

## Qué llegó

Dos ZIP en la raíz del repo:

- `AudiosSignAllCompaneros.zip` (1 149 878 bytes, `9259f723…a33df`): **30 archivos**,
  `002.m4a` … `060.m4a`, todos con número par.
- `habla.zip` (787 846 bytes, `52e1ed7d…624f`): la toma nueva de `Habla Continua.m4a`.

El owner aclaró que **Sergio grabó los audios con ruido que le tocaban a Julián**. Eso resultó
ser más que un detalle: es la explicación de un hallazgo (abajo).

## Qué se hizo

- **Los 30 salieron de los ZIP por su número, no por su etiqueta.** Se transcribieron los 30
  con faster-whisper `small` en español y se comparó el texto contra la frase que el plan pide
  para cada ID. Los 30 coinciden.
- **Por qué eran 30 y no 40:** los IDs de H2 y H4 son los pares (`002`, `004`… `060`) y los de
  H3 los impares (`003`, `009`… `057`). Lo que llegó es H2 completo (20) + H4 completo (10);
  **falta Samuel (H3) con sus 10**.
- **Organización en el corpus** (`../data/audios/`): los 30 entraron a `AU-01/` con el nombre de
  su ID, se generó la copia WAV mono 16 kHz de cada uno y el ZIP se guardó en `origen/`.
- **AU-04 reemplazado** por la toma nueva, con su copia a 16 kHz. El ZIP quedó como
  `origen/habla-2026-11-03.zip`.
- **`MANIFIESTO.md` y `manifiesto.csv` reconstruidos** desde cero para los 69 audios, con el
  hablante y la condición de cada uno. AU-01 se presenta en diez bloques de seis (una frase por
  bloque) con los diez de H3 marcados como falta.

## Qué se midió

- **AU-04:** 96,1 s, las 20 frases en orden y una sola vez, **19 pausas internas de 1,5 s o
  más** (la más corta, 1,86 s) y 1,95 s de silencio al final. Las 20 frases cierran por
  silencio, que es lo que PR-RES-06 necesita. La toma anterior daba 17.
- **AU-01 de los compañeros:** los 30 se transcribieron completos. Los de silencio tienen entre
  0,1 y 1,4 s de silencio al final; los más justos son de H4, pero el texto salió entero en
  todos.
- **Los diez con ruido:** el ruido es del mismo tipo (energía en frecuencias bajas, sin tono)
  pero está **unos 6 dB más bajo** que el bloque con ruido del owner: −42,4 dB contra −36,4 dB
  de media en los tramos sin voz.

## La voz de los diez con ruido

El owner lo dijo al entregar el ZIP: **esos diez los grabó Sergio**, en lugar de Julián. La
medición no descubre nada nuevo, pero confirma de quién es la voz y deja la consecuencia para
el conteo de hablantes. Se comprobó por tres vías independientes y las tres apuntan a H4:

| Prueba | H2 en silencio | Los 10 con ruido | H4 en silencio |
|---|---|---|---|
| Tono (mediana por audio) | 86–107 Hz | 120–137 Hz | 110–131 Hz |
| Timbre: se parece más a… | — | H4 en **9 de 10** | — |
| Formato del archivo | mono, 70 kbps, hora 01:28 y 01:37 | **estéreo**, 135 kbps, los 10 a las 01:04:13 | mono, 63 kbps, firma de Grabaciones de voz |

El tono se midió con autocorrelación sobre tramas que el VAD marca como habla; el timbre, con
coeficientes cepstrales comparando cada audio con ruido contra los dos candidatos **de la misma
frase**. La diferencia no es de nivel ni de ruido: es otra voz.

Consecuencia: AU-01 queda con tres voces y no cuatro, y las dos voces con ruido son H1 y H4. En
el manifiesto esos diez figuran como «H2 · voz de H4», para que el ID del plan y la voz real se
lean juntos.

## El nivel de los diez con ruido: igualado

El owner pidió subirles el nivel. Medido: la diferencia con el bloque del owner es de **6,3 dB**
en todo el archivo, y la relación voz/ruido de los dos bloques es la misma (21,9 dB contra
21,8 dB). O sea que no era otro ruido ni otra distancia: era otra ganancia de grabación. Por eso
se sube el nivel entero y no se mezcla nada.

Se generó una **copia derivada** en `../data/audios/derivados/ruido-nivel/` (los `.m4a` para
escuchar y los WAV de 16 kHz que consume el servicio) con `ffmpeg -af volume=6.3dB` aplicado
sobre la copia de 16 kHz, más un `README.md` con la comparación audio por audio. Los picos
quedan entre −3,8 y −1,0 dBFS: no satura ninguno. Los dos bloques quedan con el mismo piso
(−37,2 dB) y el mismo techo (−15,3 dB). **Los originales no se tocaron**, y los diez se
volvieron a transcribir después de subirlos: dan exactamente el mismo texto.

Ojo con un detalle que costó una pasada: al convertir de estéreo a mono por el camino del
codificador, ffmpeg suma 3 dB respecto de la mezcla que hace al decodificar. La primera versión
quedó 9,3 dB arriba y con un pico en 0 dBFS. Por eso el nivel se aplica sobre la copia ya mono
de 16 kHz, que es la entrada real del servicio.

## Qué quedó abierto

- **Aprobación del nivel igualado:** si el owner aprueba la copia de `derivados/ruido-nivel/`,
  se reemplaza la del corpus y se rehace el manifiesto. Los originales se quedan igual.
- **Decisión sobre la voz:** los diez siguen siendo la voz de H4, así que AU-01 tiene tres
  voces. Se documenta así, o Julián regraba los diez.
- **Faltan los 10 de Samuel** (`AU-01-003`, `009`, `015`, `021`, `027`, `033`, `039`, `045`,
  `051`, `057`), los diez en silencio.
- **Falta la ficha de la grabación:** qué celular grabó cada persona, en qué cuarto se grabaron
  los de silencio, y cuál fue el ruido y a qué distancia.
- Detalle menor: `AU-02-004` y `AU-02-005` tienen una palabra distinta de la del plan («bien» y
  «las»). No afecta porque AU-02 mide el cierre y no la transcripción.

## Nota metodológica

El VAD de WebRTC **guarda estado entre tramas**: reutilizar la misma instancia para medir varios
archivos cambia el resultado en algunos bordes (en AU-04 aparecía una pausa partida en 2,22 +
0,63 s que con instancia nueva es una sola de 2,94 s). Las mediciones del manifiesto usan una
instancia nueva por audio. Si alguien reproduce las cifras, tiene que hacer lo mismo.

## Cómo se reproduce

```bash
# corpus
ls ../data/audios/AU-01/            # 50 de 60
ls ../data/audios/16k/              # 69 copias a 16 kHz
grep AU-04 ../data/audios/manifiesto.csv

# verificar un audio con el VAD del servicio (instancia nueva por archivo)
docker run --rm -v ../data/audios/16k:/a:ro --entrypoint python sign-ai-stt-stt:latest -c "
import wave, webrtcvad
v = webrtcvad.Vad(2)
w = wave.open('/a/AU-04-habla-continua.wav','rb')
raw = w.readframes(w.getnframes())
f = [v.is_speech(raw[i:i+960], 16000) for i in range(0, len(raw)-959, 960)]
print('tramas', len(f), 'con voz', sum(f))
"
```

No se tocó código de la app: solo documentos y el corpus, que vive fuera de los repos.
