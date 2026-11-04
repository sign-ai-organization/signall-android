# Audios que graban los compañeros (H2, H3 y H4)

Lista de los **40 audios de AU-01** que no graba el owner. Con los 39 de
[`AUDIOS-QUE-GRABO.md`](AUDIOS-QUE-GRABO.md) se completan los 79 del plan de pruebas.

Fuente: `../sign-ai-docs/borradores/plan-pruebas/02-datos/AU-01-audios-transcripcion.md`
(v0.1). Las diez frases son las de FR-01-A; cada una la leen cuatro personas en silencio y
dos de ellas otra vez con ruido de fondo.

## Estado: los 40 recibidos

Los 40 están grabados, organizados y verificados en el corpus (`../../data/audios/`):

| Persona | Audios | Estado |
|---|---|---|
| **Julián** (H2) | 20 | Recibidos los 20: los 10 en silencio y los 10 con ruido |
| **Samuel** (H3) | 10 | Recibidos los 10 en silencio |
| **Sergio** (H4) | 10 | Recibidos los 10 en silencio |

Los de Julián y Sergio llegaron nombrados con el número del ID (`002.m4a` … `060.m4a`), solo los
pares. Los de Samuel llegaron como `Samuel 1.m4a` … `Samuel 10.m4a`, sin ID. Ninguno se asignó
por el nombre: se transcribieron los 40 y se comparó contra la frase que le toca a cada uno, y
los 40 coinciden. En el caso de Samuel, el orden `1`…`10` coincide con el de las diez frases y
por eso quedó `1`→`AU-01-003`, `2`→`009`, `3`→`015`, `4`→`021`, `5`→`027`, `6`→`033`,
`7`→`039`, `8`→`045`, `9`→`051` y `10`→`057`.

**Los diez con ruido son la voz de Sergio.** Así lo dijo el owner al entregarlos —Sergio grabó
el bloque que iba a ser de Julián— y la medición lo confirma: el tono de esos diez (120–137 Hz)
no coincide con el de los diez de Julián en silencio (86–107 Hz) y sí con el de Sergio
(110–131 Hz); el timbre se parece más a los audios de Sergio en 9 de los 10 casos; y los
archivos son estéreo a 135 kbps con la misma hora de creación en los diez, mientras los otros
son mono a 63–70 kbps. Además, esos diez venían 6,3 dB más bajos que el bloque con ruido del
owner, con la misma relación voz/ruido: era la ganancia de la grabación, no otro ruido. Ya hay
una copia con el nivel igualado, sin tocar los originales, en
`../../data/audios/derivados/ruido-nivel/`.

La consecuencia es de conteo: AU-01 queda con tres voces (H1, H2 y H4) y las dos voces con
ruido son H1 y H4. El detalle está en
[`MANIFIESTO.md`](../../data/audios/MANIFIESTO.md), sección «Lo que hay que revisar».

La ficha de la grabación sigue pendiente: **qué celular grabó cada persona** y **en qué cuarto
se grabaron los de silencio**. Los diez de Samuel y los de Sergio vienen del mismo tipo de
archivo (estéreo a ~130 kbps), así que conviene anotar si los procesó la misma persona.

## Cómo se graba (vale para los 40)

- Se lee la frase **tal como está escrita**, sin agregar ni quitar palabras. No se lee el
  código del audio en voz alta.
- **Una toma por audio**, aunque salga mal: se borra y se repite completa. No se corta ni se
  pega audio.
- Un archivo por audio, con el **ID como nombre** (por ejemplo `AU-01-002`). Cualquier
  formato sirve; no se convierte ni se recomprime después.
- **Las 30 en silencio, en el mismo cuarto cerrado y sin ruido de fondo** (nada de
  ventilador, nevera, calle ni música). Si se graban en sitios distintos, los audios dejan de
  ser comparables entre sí.
- Lo ideal es el **mismo celular** para los 40: cambia el micrófono y cambia el resultado.
  Si no se puede, hay que anotar qué teléfono grabó cuáles.
- Antes de hablar, esperar un segundo; al terminar, dejar un segundo de silencio. Un segundo a
  cada lado evita que se corte la primera o la última palabra.
- Hablar normal, sin exagerar ni susurrar, a la distancia de siempre.
- Los datos son inventados: no hay nombres ni datos personales reales.

## Las 10 con ruido: van con el owner

Los 10 audios con ruido de H2 **no se pueden grabar por separado**. El plan exige el **mismo
ruido, al mismo nivel y a la misma distancia** que las grabaciones con ruido del owner y que
AU-03-003 y AU-03-004, porque lo que se compara después es la transcripción con y sin ruido.
Si H2 graba con otro ruido, esos diez audios no sirven para la prueba.

Por eso: las **30 en silencio** se pueden repartir y grabar cuando sea; los **10 con ruido de
H2** se graban en la misma sesión que el bloque de ruido del owner.

## Los 40 audios

En el orden del archivo fuente. «Silencio» es cuarto cerrado sin ruido de fondo; «Ruido» es el
mismo ruido y la misma distancia del bloque del owner.

| # | ID | Frase | Hablante | Condición |
|---|---|---|---|---|
| 1 | AU-01-002 | Hola, buenos días, bienvenidos. | H2 | Silencio |
| 2 | AU-01-003 | Hola, buenos días, bienvenidos. | H3 | Silencio |
| 3 | AU-01-004 | Hola, buenos días, bienvenidos. | H4 | Silencio |
| 4 | AU-01-006 | Hola, buenos días, bienvenidos. | H2 | Ruido |
| 5 | AU-01-008 | Buenas tardes. Hoy me siento más o menos. | H2 | Silencio |
| 6 | AU-01-009 | Buenas tardes. Hoy me siento más o menos. | H3 | Silencio |
| 7 | AU-01-010 | Buenas tardes. Hoy me siento más o menos. | H4 | Silencio |
| 8 | AU-01-012 | Buenas tardes. Hoy me siento más o menos. | H2 | Ruido |
| 9 | AU-01-014 | ¿Tienes hambre? | H2 | Silencio |
| 10 | AU-01-015 | ¿Tienes hambre? | H3 | Silencio |
| 11 | AU-01-016 | ¿Tienes hambre? | H4 | Silencio |
| 12 | AU-01-018 | ¿Tienes hambre? | H2 | Ruido |
| 13 | AU-01-020 | Perdón. ¿Estás triste? | H2 | Silencio |
| 14 | AU-01-021 | Perdón. ¿Estás triste? | H3 | Silencio |
| 15 | AU-01-022 | Perdón. ¿Estás triste? | H4 | Silencio |
| 16 | AU-01-024 | Perdón. ¿Estás triste? | H2 | Ruido |
| 17 | AU-01-026 | Hola, ¿cómo estás? ¿Cómo te llamas? | H2 | Silencio |
| 18 | AU-01-027 | Hola, ¿cómo estás? ¿Cómo te llamas? | H3 | Silencio |
| 19 | AU-01-028 | Hola, ¿cómo estás? ¿Cómo te llamas? | H4 | Silencio |
| 20 | AU-01-030 | Hola, ¿cómo estás? ¿Cómo te llamas? | H2 | Ruido |
| 21 | AU-01-032 | ¿Dónde vive tu familia? | H2 | Silencio |
| 22 | AU-01-033 | ¿Dónde vive tu familia? | H3 | Silencio |
| 23 | AU-01-034 | ¿Dónde vive tu familia? | H4 | Silencio |
| 24 | AU-01-036 | ¿Dónde vive tu familia? | H2 | Ruido |
| 25 | AU-01-038 | Yo nunca como mucho. | H2 | Silencio |
| 26 | AU-01-039 | Yo nunca como mucho. | H3 | Silencio |
| 27 | AU-01-040 | Yo nunca como mucho. | H4 | Silencio |
| 28 | AU-01-042 | Yo nunca como mucho. | H2 | Ruido |
| 29 | AU-01-044 | Perdón. Mi hermano nunca está contento. | H2 | Silencio |
| 30 | AU-01-045 | Perdón. Mi hermano nunca está contento. | H3 | Silencio |
| 31 | AU-01-046 | Perdón. Mi hermano nunca está contento. | H4 | Silencio |
| 32 | AU-01-048 | Perdón. Mi hermano nunca está contento. | H2 | Ruido |
| 33 | AU-01-050 | Por favor, come poco. | H2 | Silencio |
| 34 | AU-01-051 | Por favor, come poco. | H3 | Silencio |
| 35 | AU-01-052 | Por favor, come poco. | H4 | Silencio |
| 36 | AU-01-054 | Por favor, come poco. | H2 | Ruido |
| 37 | AU-01-056 | Con permiso. Déjame pasar, por favor. | H2 | Silencio |
| 38 | AU-01-057 | Con permiso. Déjame pasar, por favor. | H3 | Silencio |
| 39 | AU-01-058 | Con permiso. Déjame pasar, por favor. | H4 | Silencio |
| 40 | AU-01-060 | Con permiso. Déjame pasar, por favor. | H2 | Ruido |

## Para mandar por separado

**Samuel** (H3) graba, en silencio y en el mismo cuarto: `AU-01-003`, `009`, `015`, `021`,
`027`, `033`, `039`, `045`, `051`, `057`.

**Sergio** (H4) graba, en silencio y en el mismo cuarto: `AU-01-004`, `010`, `016`, `022`,
`028`, `034`, `040`, `046`, `052`, `058`.

**Julián** (H2) graba en silencio: `AU-01-002`, `008`, `014`, `020`, `026`, `032`, `038`, `044`,
`050`, `056`. Y con ruido, en la sesión del owner: `AU-01-006`, `012`, `018`, `024`, `030`,
`036`, `042`, `048`, `054`, `060`.

## Lo que hay que anotar al recibirlos

- Qué teléfono grabó cada persona.
- En qué cuarto se grabaron los de silencio, y si alguien grabó en otro sitio.
- Qué ruido se usó en los diez de H2 y a qué distancia estaba el celular.

Sin eso, los resultados de la prueba de transcripción no se pueden defender.
