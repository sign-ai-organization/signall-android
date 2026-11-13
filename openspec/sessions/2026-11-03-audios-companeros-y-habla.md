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

## Los 10 de Samuel: el corpus queda completo

Llegaron en `AduisoSignAllConSamuel.zip` (1 876 468 bytes, `615d2d64…a4c642`), que traía los 40
de AU-01. Los 30 que ya estaban se compararon por sha256 con los del corpus: **idénticos, cero
diferencias**. O sea que lo único nuevo eran los diez de Samuel.

Venían como `Samuel 1.m4a` … `Samuel 10.m4a`, sin ID. Se transcribieron los diez y se asignaron
por contenido; el orden coincide con el de las frases del plan:

| Archivo | ID | Frase |
|---|---|---|
| `Samuel 1` | `AU-01-003` | Hola, buenos días, bienvenidos. |
| `Samuel 2` | `AU-01-009` | Buenas tardes. Hoy me siento más o menos. |
| `Samuel 3` | `AU-01-015` | ¿Tienes hambre? |
| `Samuel 4` | `AU-01-021` | Perdón. ¿Estás triste? |
| `Samuel 5` | `AU-01-027` | Hola, ¿cómo estás? ¿Cómo te llamas? |
| `Samuel 6` | `AU-01-033` | ¿Dónde vive tu familia? |
| `Samuel 7` | `AU-01-039` | Yo nunca como mucho. |
| `Samuel 8` | `AU-01-045` | Perdón. Mi hermano nunca está contento. |
| `Samuel 9` | `AU-01-051` | Por favor, come poco. |
| `Samuel 10` | `AU-01-057` | Con permiso. Déjame pasar, por favor. |

Medido: cuarto más silencioso de todo el corpus (**−71,5 dB de piso** de media), voz entre −21 y
−25 dB, picos entre −8,8 y −15,2 dBFS, 1,0–1,5 s de silencio al principio y 0,5–1,2 s al final.
Los diez archivos son estéreo a ~130 kbps, el mismo perfil que los de Sergio: conviene anotar si
los procesó la misma persona.

Una sola cosa por oír: en `AU-01-003` el transcriptor devolvió «bienvenido», en singular, donde
el plan dice «bienvenidos». Es una letra y el transcriptor suele comerse la ese final, pero si se
quiere descartar son los últimos dos segundos del audio.

El ZIP se borró del repo apenas se extrajo, como pidió el owner, y quedó copia en
`../data/audios/origen/`.

Con esto el corpus tiene **79 audios** (60 + 10 + 8 + 1) y `MANIFIESTO.md` se regeneró: AU-01
pasó de 50 a 60, con los diez bloques de seis completos.

## La ficha de la grabación

El owner informó cómo se grabaron los 79 audios. Lo que se sabe y lo que se midió:

| Dato | Valor | Comprobación |
|---|---|---|
| Celular de H1 | iPhone 14 Pro Max | los archivos traen la firma de Grabaciones de voz de iPhone |
| Celular a la boca | 10 cm (por confirmar) | con 1 cm el nivel habría sido mucho más alto y con soplidos |
| Fuente de ruido | a 10 cm; el «más cerca» (`AU-03-004`) a 5 cm | `AU-03-004` tiene ~8 dB más de ruido, lo que corresponde a la mitad de distancia |
| Ruido de H1 | video de ruido estudiantil desde un iPad | — |
| Ruido de H4 | televisión de fondo a unos 10 m, en su cuarto | — |
| Cuarto | cada uno en el suyo, en silencio | pisos medidos: H1 −57/−58, H2 −55 a −67, H3 −71, H4 −66 dB |

**Lo que esto cambia:** el bloque con ruido de Sergio no es el mismo ruido que el del owner. No
es un problema de nivel —eso ya se corrigió— sino de fuente y de distancia: iPad a 10 cm contra
televisión a 10 m. Con eso, los diez `-006` no se pueden promediar con el bloque de H1 como si
fueran una sola condición, y la copia con el nivel igualado no alcanza para igualarlos: arregla
el volumen, no el ruido ni la voz.

Queda por confirmar si la boca estaba a 10 cm (el owner dijo «unos 1 centímetros») y con qué
celular grabaron Samuel, Julián y Sergio. Los archivos muestran al menos tres formas distintas de
grabar: mono a 70 kbps (Julián), mono con Grabaciones de voz (Sergio en silencio) y estéreo a
~130 kbps exportado en lote (Samuel y los diez con ruido).

## El plan cambia: el ruido es de H1 y H4

El owner decidió no regrabar nada y **cambiar el plan**. Se editó
`../signall-docs/borradores/plan-pruebas/02-datos/AU-01-audios-transcripcion.md`:

- La condición de ruido pasa a ser de **H1 y H4** (era H1 y H2). En la tabla, los diez IDs
  `-006` cambiaron de hablante, de H2 a H4.
- Se agregó el criterio: el ruido no es el mismo en los dos casos —H1 con un video de ruido
  estudiantil desde un iPad a 10 cm, H4 con una televisión a unos 10 m— y los dos bloques se
  miden por separado, sin promediarse.
- Los 60 audios pasaron de «Por grabar» a «Grabado», y el estado del conjunto a «Grabados y
  verificados el 2026-11-03».

La edición quedó **sin commitear**: `sign-ai-docs` se commitea solo si el owner lo pide.

En este repo se alineó todo lo que decía «H2» para esos diez: `manifiesto.csv` y `MANIFIESTO.md`
(ahora **H4**, y la sección 1 cuenta la decisión en vez de plantearla como problema),
`AUDIOS-QUE-GRABAN-ELLOS.md` (Julián 10 en silencio, Sergio 20 contando los diez con ruido) y el
`README.md` de `derivados/ruido-nivel/` (aclara que la copia iguala el volumen, no el ruido ni la
voz).

Datos de la ficha que quedaron confirmados: la boca a **10 cm** del celular, la fuente de ruido a
10 cm en el caso de H1 y a 5 cm en `AU-03-004`; Sergio grabó todo con un **iPhone 17 Pro**. Siguen
faltando los celulares de Samuel y de Julián.

## La ficha, completa

Los celulares de los cuatro: **H1 iPhone 14 Pro Max · H2 iPhone 15 Pro · H3 iPhone 17 · H4
iPhone 17 Pro**. Con eso la ficha de grabación queda cerrada: celular a 10 cm de la boca, fuente
de ruido a 10 cm (y a 5 cm en `AU-03-004`), ruido de un video estudiantil desde un iPad en el
caso de H1 y una televisión a unos 10 m en el de H4, y cada uno en su cuarto en silencio. No
queda ningún dato pendiente de la parte de los audios.
