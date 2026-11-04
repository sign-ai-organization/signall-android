# Audios que graba el owner (H1)

Lista de los **39 audios** que le tocan a H1 en los cuatro conjuntos de la sección 8 del
plan de pruebas. Los otros 40 de AU-01 son de H2, H3 y H4 y están en
[`AUDIOS-QUE-GRABAN-ELLOS.md`](AUDIOS-QUE-GRABAN-ELLOS.md), listos para mandarles.

Fuente: `../sign-ai-docs/borradores/plan-pruebas/02-datos/AU-0*.md` (v0.1).

## Estado: grabados y organizados

Los 39 **están grabados y organizados**, el 2026-11-03. Quedaron fuera de los repos, en
`../../data/audios/`, con un archivo por ID y una copia a 16 kHz para el STT:

```text
data/audios/
  AU-01/  20 audios   AU-02/  10   AU-03/   8   AU-04/  1
  16k/    los 39 en WAV mono 16 kHz
  origen/AudiosTesisJD.zip          el ZIP como llegó
  MANIFIESTO.md  manifiesto.csv     qué es cada archivo, qué mide y qué se verificó
```

Cada archivo se identificó **por su contenido**, no por el número: se transcribieron los 39 y
se comparó el texto contra el que pide el plan para cada código. Los 39 coinciden y los
cuatro conjuntos quedaron completos. Ese trabajo está en `MANIFIESTO.md`, con la duración, el
silencio final y la transcripción de cada uno.

**Dos se regrabaron y ya están verificados**, el mismo 2026-11-03, con la segunda entrega
(`new.zip`):

- `AU-02-007` (`35.m4a`): tenía la pausa interna en 1,50 s y cerraba por silencio a los 8,9 s
  en vez de por el límite de 8 s. La toma nueva tiene 3,7 s de silencio al final y corta a
  los 8 s dentro de una pausa.
- `AU-02-008` (`36.m4a`): le faltaba el silencio al final (1,17 s). La toma nueva tiene 4,6 s
  y corta a los 8 s dentro de una pausa.

Los diez de AU-02 cierran ahora como el plan pide.

**Falta decidir el audio de habla continua** (`Habla Continua.m4a`, AU-04). Las 20 frases
están, en orden y una sola vez, pero con el VAD del servicio salen **17 pausas de 1,5 s o
más, no 19**: dos quedan cortadas por la respiración y una mide 1,2 s. PR-RES-06 mide
«frases enviadas y traducidas», así que de ese audio saldrían 17 o 18 frases en vez de 20 y
la prueba leería una pérdida que en realidad es del audio. Se regraba en una toma de ~83 s
con las pausas más largas (3 s) y respirando al final de cada pausa, pegado a la frase
siguiente.

Lo que hay que anotar para que el corpus se pueda repetir: **cuál fue el ruido** y a qué
distancia, **con qué celular** se grabaron los 39 y **en qué cuarto** se grabaron los de
silencio. Sin el ruido y la distancia, los diez de Julián con ruido no se pueden grabar igual.

Están ordenados **por lugar y condición**, no por código, para no mover el celular ni la
fuente de ruido entre audios:

| Bloque | Audios | Dónde |
|---|---|---|
| A · Silencio | 12 | Habitación cerrada, sin ruido de fondo |
| B · Ruido | 12 | Con el mismo ruido y a la misma distancia |
| C · Sin voz | 4 | Tos, carraspeo, golpes y teclado |
| D · Cierre de frase | 10 | Silencio controlado, con pausas |
| E · Habla continua | 1 | Una sola toma de ~73 s |

## A · Silencio

Habitación cerrada, sin ruido de fondo. Los dos últimos son de AU-03, pero van acá porque
el plan pide el mismo lugar que las grabaciones en silencio de AU-01.

| # | ID | Qué se dice o se hace | Condición |
|---|---|---|---|
| 1 | AU-01-001 | Hola, buenos días, bienvenidos. | Silencio |
| 2 | AU-01-007 | Buenas tardes. Hoy me siento más o menos. | Silencio |
| 3 | AU-01-013 | ¿Tienes hambre? | Silencio |
| 4 | AU-01-019 | Perdón. ¿Estás triste? | Silencio |
| 5 | AU-01-025 | Hola, ¿cómo estás? ¿Cómo te llamas? | Silencio |
| 6 | AU-01-031 | ¿Dónde vive tu familia? | Silencio |
| 7 | AU-01-037 | Yo nunca como mucho. | Silencio |
| 8 | AU-01-043 | Perdón. Mi hermano nunca está contento. | Silencio |
| 9 | AU-01-049 | Por favor, come poco. | Silencio |
| 10 | AU-01-055 | Con permiso. Déjame pasar, por favor. | Silencio |
| 11 | AU-03-001 | No se dice nada. 20 s | Celular apoyado en una mesa |
| 12 | AU-03-002 | No se dice nada. 20 s | En el mismo lugar que los anteriores, con el celular en la mano |

## B · Ruido

El **mismo ruido** en los doce, al mismo nivel y con el celular a la misma distancia, salvo
el último, que va más cerca. Si el ruido cambia, los resultados de AU-01 y AU-03 dejan de
ser comparables.

| # | ID | Qué se dice o se hace | Condición |
|---|---|---|---|
| 13 | AU-01-005 | Hola, buenos días, bienvenidos. | Ruido moderado |
| 14 | AU-01-011 | Buenas tardes. Hoy me siento más o menos. | Ruido moderado |
| 15 | AU-01-017 | ¿Tienes hambre? | Ruido moderado |
| 16 | AU-01-023 | Perdón. ¿Estás triste? | Ruido moderado |
| 17 | AU-01-029 | Hola, ¿cómo estás? ¿Cómo te llamas? | Ruido moderado |
| 18 | AU-01-035 | ¿Dónde vive tu familia? | Ruido moderado |
| 19 | AU-01-041 | Yo nunca como mucho. | Ruido moderado |
| 20 | AU-01-047 | Perdón. Mi hermano nunca está contento. | Ruido moderado |
| 21 | AU-01-053 | Por favor, come poco. | Ruido moderado |
| 22 | AU-01-059 | Con permiso. Déjame pasar, por favor. | Ruido moderado |
| 23 | AU-03-003 | No se dice nada. 20 s de ruido | Mismo ruido y misma distancia que los anteriores |
| 24 | AU-03-004 | No se dice nada. 20 s de ruido | Mismo ruido, celular más cerca de la fuente |

## C · Sin voz

20 segundos cada uno y sin hablar en ningún momento.

| # | ID | Qué se hace | Condición |
|---|---|---|---|
| 25 | AU-03-005 | 4 tosidos, uno cada 5 s | 20 s |
| 26 | AU-03-006 | 4 carraspeos, uno cada 5 s | 20 s |
| 27 | AU-03-007 | 10 golpes con la mano sobre una mesa, uno cada 2 s | 20 s |
| 28 | AU-03-008 | Teclear sin parar en el teclado de un computador | 20 s |

## D · Cierre de frase

Cada audio termina con **3 segundos de silencio**, salvo los dos últimos, que terminan sin
silencio porque llevan mensaje de fin. Las pausas dentro del texto duran 1 s: son más
cortas que el silencio que cierra la frase, para que no se cierre antes.

| # | ID | Qué se dice | Pausas | Final | Duración |
|---|---|---|---|---|---|
| 29 | AU-02-001 | Buenos días, ¿cómo estás? | Ninguna | 3 s de silencio | ~4 s |
| 30 | AU-02-002 | Hoy me siento muy bien. | Ninguna | 3 s de silencio | ~5 s |
| 31 | AU-02-003 | ¿Dónde vive tu familia? | Ninguna | 3 s de silencio | ~4 s |
| 32 | AU-02-004 | Mi abuelo vive con mi tío en una casa grande y todos los días se levantan temprano para trabajar, desayunan con la familia, hablan de lo que van a hacer y después salen juntos hasta la tarde. | Ninguna | 3 s de silencio | ~16 s |
| 33 | AU-02-005 | Los niños y las niñas llegan a la casa después de estudiar, comen con gusto lo que preparó la familia, cuentan lo que aprendieron durante el día y luego ayudan a recoger la mesa antes de descansar un rato. | Ninguna | 3 s de silencio | ~17 s |
| 34 | AU-02-006 | Cuando mi hermano está triste yo me siento a su lado, le pregunto qué le pasa, lo escucho con calma y le recuerdo que toda la familia lo quiere mucho y que siempre estamos juntos para ayudarlo. | Ninguna | 3 s de silencio | ~16 s |
| 35 | AU-02-007 | Mi tío trabaja mucho durante la semana, [1 s] pero los domingos descansa con la familia, [1 s] cocina para todos y después nos cuenta historias de cuando era niño y vivía lejos de la ciudad. | 2 de 1 s | 3 s de silencio | ~16 s |
| 36 | AU-02-008 | Buenas tardes a todos, [1 s] quiero decirles que hoy me siento más o menos, [1 s] porque dormí poco y tengo mucha hambre, así que voy a comer algo antes de seguir con el trabajo de la tarde. | 2 de 1 s | 3 s de silencio | ~18 s |
| 37 | AU-02-009 | Gracias a todos, hasta luego. | Ninguna | Ninguno | ~2 s |
| 38 | AU-02-010 | Perdón, necesito hablar con la persona encargada de atender a las familias. | Ninguna | Ninguno | ~4 s |

## E · Habla continua

Una sola toma de unos 73 segundos, de corrido, con **2 segundos de pausa** entre frase y
frase. En los tres últimos, las dos oraciones van separadas por 0,5 s.

Audio **39**, en este orden. Son veinte frases dentro de una sola toma: no se graban por
separado.

| Orden | ID | Qué se dice |
|---|---|---|
| 1 | AU-04-001 | Hola, buenos días, bienvenidos. |
| 2 | AU-04-002 | ¿Tienes hambre? |
| 3 | AU-04-003 | Yo nunca como mucho. |
| 4 | AU-04-004 | Por favor, come poco. |
| 5 | AU-04-005 | Mi abuelo y mi tío trabajan en familia. |
| 6 | AU-04-006 | ¿Por qué estás triste? |
| 7 | AU-04-007 | Mi abuelo nunca trabaja. |
| 8 | AU-04-008 | Niños, trabajen todos. |
| 9 | AU-04-009 | ¿Dónde vive tu familia? |
| 10 | AU-04-010 | ¿Estás bien? |
| 11 | AU-04-011 | Estoy contento porque mi hermano está feliz. |
| 12 | AU-04-012 | Mi tío nunca está aburrido. |
| 13 | AU-04-013 | Hola, familia, siéntanse bienvenidos. |
| 14 | AU-04-014 | ¿Cuándo comen ustedes? |
| 15 | AU-04-015 | Ustedes nunca se sienten mal. |
| 16 | AU-04-016 | ¿Ustedes trabajan en familia? |
| 17 | AU-04-017 | ¿Tu hermano está aburrido? |
| 18 | AU-04-018 | Adiós a todos. [0,5 s] Buenas noches. |
| 19 | AU-04-019 | Perdón. [0,5 s] ¿Estás triste? |
| 20 | AU-04-020 | Con permiso. [0,5 s] Déjame pasar, por favor. |

## Lo que el plan no fija

Tres cosas quedan a criterio de quien graba, y conviene anotar qué se eligió:

- Cuánto silencio dejar al principio y al final de los audios de AU-01. El plan no lo dice;
  un segundo a cada lado evita que se corte la primera o la última palabra.
- El formato y el nombre de los archivos. El plan solo fija el ID de cada audio.
- Qué ruido se usa. Tiene que ser uno solo, el mismo en AU-01 y en AU-03, y hay que
  escribirlo en la evidencia de la prueba.
