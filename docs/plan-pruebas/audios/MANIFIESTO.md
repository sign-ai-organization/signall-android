# Corpus de audios

Los **79 audios** que pide la sección 8 del plan de pruebas, **todos grabados**:
**60 de AU-01**, los **10 de AU-02**, los **8 de AU-03** y el **1 de AU-04**.

| Entrega | Archivo | Contenido |
|---|---|---|
| 2026-11-03 | `origen/AudiosTesisJD.zip` | Los 39 de H1 (`01`…`38` y `Habla Continua`) |
| 2026-11-03 | `origen/new-2026-11-03.zip` | Las tomas nuevas de `35` y `36` (AU-02-007 y AU-02-008) |
| 2026-11-03 | `origen/AudiosSignAllCompaneros.zip` | Los 30 de los compañeros (`002`…`060`, solo los pares) |
| 2026-11-03 | `origen/habla-2026-11-03.zip` | La toma nueva del audio de habla continua |
| 2026-11-03 | `origen/AduisoSignAllConSamuel.zip` | Los 40 de AU-01: los 30 anteriores y los 10 de Samuel (`Samuel 1`…`Samuel 10`) |

Los hashes de los cinco ZIP están al final.

## Cómo está organizado

```
audios/
  AU-01/   60 audios: 10 en silencio y 10 con ruido de H1, 10 + 10 de H2, 10 de H3 y 10 de H4
  AU-02/   10 audios de cierre de frase
  AU-03/    8 audios sin voz
  AU-04/    1 audio de habla continua (adentro van AU-04-001 a AU-04-020)
  16k/      los mismos audios en WAV mono 16 kHz (copia derivada, para el STT)
  origen/   los cinco ZIP tal como llegaron
  manifiesto.csv   esta misma tabla, legible por máquina
```

Los `.m4a` son los originales y no se recodificaron. La copia de `16k/` existe porque los
originales vienen en 48 kHz y el servicio trabaja a 16 kHz mono.

## Cómo se verificó

Ningún archivo se asignó por su nombre: se transcribieron todos con faster-whisper `small` en
español y se comparó el texto contra el que el plan pide para cada ID. Después, para cada uno:

- **Duración** y **silencio inicial y final** (nada por encima de −40 dB).
- **La racha que acumula el VAD del servicio** (WebRTC, agresividad 2, tramas de 30 ms), que
  es la que dispara el cierre: el servicio cierra cuando junta 1 500 ms seguidos sin habla.
  Se mide con una instancia nueva del VAD por audio, porque el VAD guarda estado entre tramas
  y reutilizarlo cambia el resultado en algunos bordes.
- **Transcripción** contra el texto del plan. Una diferencia puede ser del audio o del
  transcriptor, así que queda marcada para oírla, no como error.

## AU-01 · Transcripción (60 audios)

Cada frase la leen cuatro personas en silencio (H1 a H4) y dos de ellas otra vez con ruido
(H1 y H2). Cada bloque de seis IDs es una frase, siempre en el mismo orden:
`-001` H1 silencio · `-002` H2 silencio · `-003` H3 silencio · `-004` H4 silencio ·
`-005` H1 ruido · `-006` H4 ruido.

El `-006` lo grabó H4 y no H2: así se decidió el 2026-11-03 y así quedó en el plan.
Ojo con el ruido: el de H1 es un iPad a 10 cm y el de H4 una televisión a 10 m, así que los
dos bloques con ruido se miden por separado y no se promedian.

Los diez de H3 son los `-003`, `-009`, …, `-057`.

### 1. Hola, buenos días, bienvenidos.

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-001` | H1 | silencio | `01.m4a` | 4,46 s | 1,00 s | Hola, buenos días, bienvenidos. | — |
| `AU-01-002` | H2 | silencio | `002.m4a` | 3,99 s | 0,90 s | Hola, buenos días, bienvenidos. | — |
| `AU-01-003` | H3 | silencio | `Samuel 1.m4a` | 4,42 s | 0,70 s | Hola, buenos días, bienvenido. | «bienvenidos» por «bienvenido» |
| `AU-01-004` | H4 | silencio | `004.m4a` | 3,78 s | 0,50 s | Hola, buenos días, bienvenidos. | — |
| `AU-01-005` | H1 | ruido | `13.m4a` | 5,78 s | — el ruido llena el archivo | Hola, buenos días, bienvenidos. | — |
| `AU-01-006` | H4 | ruido | `006.m4a` | 3,14 s | — el ruido llena el archivo | Hola, buenos días, bienvenidos. | — |

### 2. Buenas tardes. Hoy me siento más o menos.

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-007` | H1 | silencio | `02.m4a` | 5,21 s | 1,40 s | Buenas tardes. Hoy me siento más o menos. | — |
| `AU-01-008` | H2 | silencio | `008.m4a` | 4,42 s | 1,10 s | Buenas tardes. Hoy me siento más o menos. | — |
| `AU-01-009` | H3 | silencio | `Samuel 2.m4a` | 5,01 s | 0,60 s | Buenas tardes, hoy me siento más o menos. | — |
| `AU-01-010` | H4 | silencio | `010.m4a` | 3,56 s | 0,20 s | Buenas tardes, hoy me siento más o menos. | — |
| `AU-01-011` | H1 | ruido | `14.m4a` | 5,36 s | — el ruido llena el archivo | Buenas tardes. Hoy me siento más o menos. | — |
| `AU-01-012` | H4 | ruido | `012.m4a` | 3,56 s | — el ruido llena el archivo | Buenas tardes, hoy me siento más o menos. | — |

### 3. ¿Tienes hambre?

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-013` | H1 | silencio | `03.m4a` | 2,69 s | 0,90 s | ¿Tienes hambre? | — |
| `AU-01-014` | H2 | silencio | `014.m4a` | 2,62 s | 0,90 s | Tienes hambre. | — |
| `AU-01-015` | H3 | silencio | `Samuel 3.m4a` | 3,73 s | 1,20 s | ¿Tienes hambre? | — |
| `AU-01-016` | H4 | silencio | `016.m4a` | 2,07 s | 0,10 s | ¿Tienes hambre? | — |
| `AU-01-017` | H1 | ruido | `15.m4a` | 2,60 s | — el ruido llena el archivo | ¿Tienes hambre? | — |
| `AU-01-018` | H4 | ruido | `018.m4a` | 2,37 s | — el ruido llena el archivo | tienes hambre | — |

### 4. Perdón. ¿Estás triste?

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-019` | H1 | silencio | `04.m4a` | 3,99 s | 1,00 s | Perdón, ¿estás triste? | — |
| `AU-01-020` | H2 | silencio | `020.m4a` | 3,39 s | 0,80 s | Perdón, ¿estás triste? | — |
| `AU-01-021` | H3 | silencio | `Samuel 4.m4a` | 4,42 s | 0,90 s | Perdón, ¿estás triste? | — |
| `AU-01-022` | H4 | silencio | `022.m4a` | 2,92 s | 0,30 s | Perdón, estás triste? | — |
| `AU-01-023` | H1 | ruido | `16.m4a` | 3,39 s | — el ruido llena el archivo | Perdón, ¿estás triste? | — |
| `AU-01-024` | H4 | ruido | `024.m4a` | 3,05 s | — el ruido llena el archivo | Perdón, ¿estás triste? | — |

### 5. Hola, ¿cómo estás? ¿Cómo te llamas?

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-025` | H1 | silencio | `05.m4a` | 4,01 s | 0,30 s | Hola, ¿cómo estás? ¿Cómo te llamas? | — |
| `AU-01-026` | H2 | silencio | `026.m4a` | 3,65 s | 0,60 s | Hola, ¿cómo estás? ¿Cómo te llamas? | — |
| `AU-01-027` | H3 | silencio | `Samuel 5.m4a` | 4,84 s | 0,90 s | Hola, ¿cómo estás? ¿Cómo te llamas? | — |
| `AU-01-028` | H4 | silencio | `028.m4a` | 3,80 s | 0,40 s | Hola, ¿cómo estás? ¿Cómo te llamas? | — |
| `AU-01-029` | H1 | ruido | `17.m4a` | 4,46 s | — el ruido llena el archivo | Hola, ¿cómo estás? ¿Cómo te llamas? | — |
| `AU-01-030` | H4 | ruido | `030.m4a` | 3,56 s | — el ruido llena el archivo | Hola, ¿cómo estás? ¿Cómo te llamas? | — |

### 6. ¿Dónde vive tu familia?

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-031` | H1 | silencio | `06.m4a` | 3,60 s | 1,00 s | ¿Dónde vive tu familia? | — |
| `AU-01-032` | H2 | silencio | `032.m4a` | 3,22 s | 0,80 s | ¡Dónde hay tu familia! | «vive» por «hay» |
| `AU-01-033` | H3 | silencio | `Samuel 6.m4a` | 3,48 s | 0,70 s | ¿Dónde vive tu familia? | — |
| `AU-01-034` | H4 | silencio | `034.m4a` | 2,60 s | 0,10 s | donde vive tu familia | — |
| `AU-01-035` | H1 | ruido | `18.m4a` | 3,35 s | — el ruido llena el archivo | ¿Dónde vive tu familia? | — |
| `AU-01-036` | H4 | ruido | `036.m4a` | 2,45 s | — el ruido llena el archivo | donde vive tu familia | — |

### 7. Yo nunca como mucho.

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-037` | H1 | silencio | `07.m4a` | 3,65 s | 1,20 s | Yo nunca como mucho. | — |
| `AU-01-038` | H2 | silencio | `038.m4a` | 2,54 s | 0,70 s | Yo nunca como a mucho. | sobra «a» |
| `AU-01-039` | H3 | silencio | `Samuel 7.m4a` | 3,05 s | 0,50 s | Yo nunca como mucho. | — |
| `AU-01-040` | H4 | silencio | `040.m4a` | 2,26 s | 0,20 s | Yo nunca como mucho | — |
| `AU-01-041` | H1 | ruido | `19.m4a` | 3,80 s | — el ruido llena el archivo | Yo nunca como mucho | — |
| `AU-01-042` | H4 | ruido | `042.m4a` | 2,62 s | — el ruido llena el archivo | Yo nunca como mucho | — |

### 8. Perdón. Mi hermano nunca está contento.

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-043` | H1 | silencio | `08.m4a` | 5,12 s | 1,00 s | Perdón, mi hermano nunca está contento. | — |
| `AU-01-044` | H2 | silencio | `044.m4a` | 3,56 s | 0,70 s | Perdón, mi hermano nunca está contento. | — |
| `AU-01-045` | H3 | silencio | `Samuel 8.m4a` | 4,67 s | 0,70 s | Perdón, mi hermano nunca está contento. | — |
| `AU-01-046` | H4 | silencio | `046.m4a` | 3,35 s | 0,30 s | Perdón mi hermano nunca está contento | — |
| `AU-01-047` | H1 | ruido | `20.m4a` | 4,74 s | — el ruido llena el archivo | Perdón, mi hermano nunca está contento. | — |
| `AU-01-048` | H4 | ruido | `048.m4a` | 3,22 s | — el ruido llena el archivo | Perdón, mi hermano nunca está contento. | — |

### 9. Por favor, come poco.

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-049` | H1 | silencio | `09.m4a` | 3,65 s | 1,00 s | Por favor, come poco. | — |
| `AU-01-050` | H2 | silencio | `050.m4a` | 2,96 s | 0,60 s | Por favor, comen poco. | «come» por «comen» |
| `AU-01-051` | H3 | silencio | `Samuel 9.m4a` | 3,73 s | 0,70 s | por favor, come poco | — |
| `AU-01-052` | H4 | silencio | `052.m4a` | 2,90 s | 0,50 s | Por favor, come poco. | — |
| `AU-01-053` | H1 | ruido | `21.m4a` | 3,39 s | — el ruido llena el archivo | Por favor, come poco. | — |
| `AU-01-054` | H4 | ruido | `054.m4a` | 2,62 s | — el ruido llena el archivo | Por favor, come poco. | — |

### 10. Con permiso. Déjame pasar, por favor.

| ID | Hablante | Condición | Archivo | Duración | Silencio final | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-01-055` | H1 | silencio | `10.m4a` | 5,06 s | 0,80 s | Con permiso, déjame pasar, por favor. | — |
| `AU-01-056` | H2 | silencio | `056.m4a` | 3,90 s | 0,90 s | Con permiso, déjame pasar por favor. | — |
| `AU-01-057` | H3 | silencio | `Samuel 10.m4a` | 4,59 s | 0,60 s | Con permiso, déjame pasar por favor. | — |
| `AU-01-058` | H4 | silencio | `058.m4a` | 3,39 s | 0,20 s | Con permiso, déjame pasar por favor. | — |
| `AU-01-059` | H1 | ruido | `22.m4a` | 4,74 s | — el ruido llena el archivo | Con permiso, déjame pasar, por favor. | — |
| `AU-01-060` | H4 | ruido | `060.m4a` | 3,56 s | — el ruido llena el archivo | Con permiso, déjame pasar por favor. | — |

## AU-02 · Cierre de frase (10 audios)

Cada audio está hecho para cerrar de una sola forma. La columna «Qué se va a ver» es la
lectura del audio con el VAD del servicio, no una suposición.

| ID | Archivo | Duración | Cierre que pide el plan | Qué se va a ver | Texto del plan | Transcripción | Diferencias |
|---|---|---|---|---|---|---|---|
| `AU-02-001` | `29.m4a` | 6,32 s | silencio de 1,5 s | 3,39 s de silencio al final de la frase | Buenos días, ¿cómo estás? | Buenos días. ¿Cómo estás? | — |
| `AU-02-002` | `30.m4a` | 6,06 s | silencio de 1,5 s | 2,58 s de silencio al final de la frase | Hoy me siento muy bien. | Hoy me siento muy bien. | — |
| `AU-02-003` | `31.m4a` | 6,53 s | silencio de 1,5 s | 2,55 s de silencio al final de la frase | ¿Dónde vive tu familia? | ¿Dónde vive tu familia? | — |
| `AU-02-004` | `32.m4a` | 18,20 s | límite de 8 s, sin pausa | ninguna pausa interna y 4,62 s de silencio al final | Mi abuelo vive con mi tío en una casa grande y todos los días se levantan temprano para trabajar, desayunan con la familia, hablan de lo que van a hacer y después salen juntos hasta la tarde. | Mi abuelo vive con mi tío en una casa grande y todos los días se levantan bien temprano para trabajar. Desayunan con la familia, hablan de lo que van a hacer y después salen juntos hasta la tarde. | sobra «bien» |
| `AU-02-005` | `33.m4a` | 17,47 s | límite de 8 s, sin pausa | ninguna pausa interna y 3,42 s de silencio al final | Los niños y las niñas llegan a la casa después de estudiar, comen con gusto lo que preparó la familia, cuentan lo que aprendieron durante el día y luego ayudan a recoger la mesa antes de descansar un rato. | Los niños y niñas llegan a la casa después de estudiar, comen con gusto lo que preparó la familia, cuentan lo que aprendieron durante el día y luego ayudan a recoger la mesa antes de descansar un rato. | falta «las» |
| `AU-02-006` | `34.m4a` | 17,02 s | límite de 8 s, sin pausa | ninguna pausa interna y 3,63 s de silencio al final | Cuando mi hermano está triste yo me siento a su lado, le pregunto qué le pasa, lo escucho con calma y le recuerdo que toda la familia lo quiere mucho y que siempre estamos juntos para ayudarlo. | Cuando mi hermano está triste, yo me siento a su lado. Le pregunto qué le pasa. Lo escucho con calma y le recuerdo que toda la familia lo quiere mucho y que siempre estamos juntos para ayudarlo. | — |
| `AU-02-007` | `35.m4a` | 15,83 s | límite de 8 s, en una pausa | corta a los 8 s en una pausa de 0,48 s (a los 6,21 s); 3,69 s de silencio al final | Mi tío trabaja mucho durante la semana, [1 s] pero los domingos descansa con la familia, [1 s] cocina para todos y después nos cuenta historias de cuando era niño y vivía lejos de la ciudad. | Mi tío trabaja mucho durante la semana, pero los domingos descansa con la familia, cocina para todos y después nos cuenta historias de cuando era niño y vivía lejos de la ciudad. | — |
| `AU-02-008` | `36.m4a` | 18,07 s | límite de 8 s, en una pausa | corta a los 8 s en una pausa de 0,87 s (a los 6,48 s); 4,59 s de silencio al final | Buenas tardes a todos, [1 s] quiero decirles que hoy me siento más o menos, [1 s] porque dormí poco y tengo mucha hambre, así que voy a comer algo antes de seguir con el trabajo de la tarde. | Buenas tardes a todos. Si quiero decirles que hoy me siento más o menos, porque dormí poco y tengo mucha hambre, así que voy a comer algo antes de seguir con el trabajo de la tarde. | sobra «si» |
| `AU-02-009` | `37.m4a` | 3,67 s | mensaje de fin | sin silencio al final, como pide el plan | Gracias a todos, hasta luego. | Gracias a todos, hasta luego. | — |
| `AU-02-010` | `38.m4a` | 6,38 s | mensaje de fin | sin silencio al final, como pide el plan | Perdón, necesito hablar con la persona encargada de atender a las familias. | Perdón, necesito hablar con la persona encargada de atender a las familias. | — |

Los diez cierran por donde el plan dice: tres por el silencio de 1,5 s, cinco por el límite
de 8 s y dos por el mensaje de fin.

## AU-03 · Sin voz (8 audios)

No hay voz en ningún momento. La columna «Transcripción inventada» es lo que el transcriptor
puso sobre el ruido —lo importante es que el servicio no lo mande como texto— y «Eventos»
cuenta los tramos de sonido por encima de −40 dB.

| ID | Archivo | Qué se produce | Duración | Eventos | Transcripción inventada |
|---|---|---|---|---|---|
| `AU-03-001` | `11.m4a` | silencio, 20 s | 22,04 s | 1 | y |
| `AU-03-002` | `12.m4a` | silencio, 20 s | 20,54 s | 0 | Música |
| `AU-03-003` | `23.m4a` | ruido, 20 s | 20,35 s | 1 | . |
| `AU-03-004` | `24.m4a` | ruido, 20 s, más cerca | 20,37 s | 1 | ... ... ¡Suscríbete! |
| `AU-03-005` | `25.m4a` | 4 tosidos | 21,76 s | 4 | ... ... ... ... ... |
| `AU-03-006` | `26.m4a` | 4 carraspeos | 21,33 s | 4 | un un un un un |
| `AU-03-007` | `27.m4a` | 10 golpes | 21,27 s | 10 | Corre los nombres de los nombres y los nombres de los nombres que se han utilizado. |
| `AU-03-008` | `28.m4a` | teclado sin parar | 21,31 s | 6 | ¡Suscríbete! |

El número de eventos no se compara entre audios de distinto tipo: un teclado no suena como
cuatro tosidos. Lo que este conjunto mide es que **no haya voz**, y en los ocho no la hay.

## AU-04 · Habla continua (1 audio)

`AU-04-habla-continua.m4a` · 96,08 s · una sola toma · las 20 frases en el orden del plan.
Los tiempos de cada frase salen de la transcripción con marcas de tiempo. La columna «Pausa»
no sale del transcriptor: es lo que mide el VAD del servicio entre el final de una frase y el
comienzo de la siguiente, que es la cifra que decide si la frase se cierra.

| # | Frase | Texto | Comienza | Termina | Pausa siguiente |
|---|---|---|---|---|---|
| 1 | FR-01-001 | Hola, buenos días, bienvenidos. | 0,86 s | 3,96 s | 2,52 s |
| 2 | FR-01-007 | ¿Tienes hambre? | 6,20 s | 7,34 s | 2,94 s |
| 3 | FR-01-019 | Yo nunca como mucho. | 10,02 s | 11,70 s | 2,73 s |
| 4 | FR-01-025 | Por favor, come poco. | 14,28 s | 16,22 s | 2,49 s |
| 5 | FR-01-003 | Mi abuelo y mi tío trabajan en familia. | 18,76 s | 21,52 s | 2,73 s |
| 6 | FR-01-016 | ¿Por qué estás triste? | 24,04 s | 25,50 s | 2,67 s |
| 7 | FR-01-020 | Mi abuelo nunca trabaja. | 27,68 s | 31,52 s | 2,31 s |
| 8 | FR-01-027 | Niños, trabajen todos. | 32,38 s | 34,52 s | 2,55 s |
| 9 | FR-01-014 | ¿Dónde vive tu familia? | 37,34 s | 38,52 s | 2,61 s |
| 10 | FR-01-008 | ¿Estás bien? | 41,38 s | 42,08 s | 2,94 s |
| 11 | FR-01-004 | Estoy contento porque mi hermano está feliz. | 45,30 s | 48,08 s | 2,76 s |
| 12 | FR-01-021 | Mi tío nunca está aburrido. | 50,64 s | 53,26 s | 1,86 s |
| 13 | FR-01-029 | Hola, familia, siéntanse bienvenidos. | 55,36 s | 58,98 s | 3,03 s |
| 14 | FR-01-017 | ¿Cuándo comen ustedes? | 61,64 s | 63,32 s | 2,79 s |
| 15 | FR-01-022 | Ustedes nunca se sienten mal. | 66,52 s | 68,54 s | 2,76 s |
| 16 | FR-01-009 | ¿Ustedes trabajan en familia? | 71,06 s | 73,08 s | 2,85 s |
| 17 | FR-01-012 | ¿Tu hermano está aburrido? | 75,88 s | 77,56 s | 2,73 s |
| 18 | FR-01-002 | Adiós a todos. Buenas noches. | 79,94 s | 83,02 s | 2,10 s |
| 19 | FR-01-011 | Perdón. ¿Estás triste? | 85,54 s | 87,82 s | 2,76 s |
| 20 | FR-01-026 | Con permiso. Déjame pasar, por favor. | 90,74 s | 93,88 s | — |

Esta es la toma nueva. En la anterior las pausas no alcanzaban: con el VAD del servicio daba
**17 rachas de 1,5 s o más** en vez de 19, porque dos pausas quedaban cortadas por la
respiración y una medía 1,2 s. En esta hay **19 pausas internas de 1,5 s o más** (la más
corta mide 1,86 s) y 1,95 s de silencio al final, así que las 20 frases cierran por silencio.
La toma dura 96,1 s en vez de los 73 aproximados del plan porque las pausas quedaron de 2,5 a
3 s en lugar de 2; el plan dice que los tiempos aproximados se reemplazan por los reales.

## Cómo se grabó

Lo que el owner informó el 2026-11-03, contrastado con lo que dicen los archivos:

| Dato | Qué se sabe |
|---|---|
| **Celular** | H1, **iPhone 14 Pro Max** · H2, **iPhone 15 Pro** · H3, **iPhone 17** · H4, **iPhone 17 Pro**. |
| **Distancia a la boca** | **10 cm**. |
| **Distancia a la fuente de ruido** | H1: **10 cm**, y el audio «más cerca» (`AU-03-004`) a **5 cm**, la mitad. H4: **unos 10 m**, porque la fuente era la televisión de su cuarto. |
| **Ruido** | H1: un **video de ruido estudiantil reproducido desde un iPad** a 10 cm del celular. H4: **una televisión de fondo a unos 10 m**, en su cuarto. |
| **Cuarto** | Cada persona grabó **en su propio cuarto, en silencio**. El piso medido va de −55 dB (H2) a −71 dB (H3): son cuartos distintos, todos silenciosos. |
| **Ruido de fondo medido** | H1 −57 a −58 dB · H2 −55 a −67 dB · H3 −71 dB · H4 −66 dB. |

## Lo que hay que revisar

### 1. Los diez con ruido: voz de H4 y otro ruido

El owner decidió el 2026-11-03 **cambiar el plan en vez de regrabar**. AU-01 queda con H1 y H4
en la condición de ruido, y esos diez se documentan como son. Cambian tres cosas respecto de
lo que el plan pedía al principio:

**La voz.** Es de Sergio (H4), no de Julián. Se comprobó por tres vías:

- **Tono:** los diez de H2 en silencio están entre 86 y 107 Hz; los diez con ruido, entre 120 y
  137 Hz; los de H4, entre 110 y 131 Hz.
- **Timbre:** con coeficientes cepstrales sobre el mismo texto, el audio con ruido se parece más
  al de H4 que al de H2 en **9 de los 10 casos**.
- **Archivo:** los diez con ruido son estéreo a 135 kbps y todos tienen la misma hora de creación
  (01:04:13–01:04:14), o sea que salieron de un mismo procesado; los de H2 en silencio son mono a
  70 kbps.

**El ruido.** No es el mismo: el del owner es un **video de ruido estudiantil desde un iPad a
10 cm** del celular, y el de Sergio es **una televisión a unos 10 m**, en su cuarto. Los dos son
de frecuencias bajas y sin tono, por eso se parecen en el espectro, pero no son la misma fuente ni
la misma distancia.

**El nivel.** Los diez de Sergio venían **6,3 dB más bajos** que el bloque del owner, con la
misma relación voz/ruido (21,9 dB contra 21,8 dB): era la ganancia de la grabación. Está
corregido en `derivados/ruido-nivel/`, sin tocar los originales:

| Bloque | Piso | Techo | Relación voz/ruido |
|---|---|---|---|
| Los 10 con ruido de H1 | −37,2 dB | −15,3 dB | 21,8 dB |
| Los 10 con ruido de H4, originales | −43,5 dB | −21,6 dB | 21,9 dB |
| Los 10 con ruido de H4, subidos +6,3 dB | −37,2 dB | −15,3 dB | 21,9 dB |

Subir el nivel iguala el volumen, no la voz ni el ruido.

### 2. Un detalle menor en dos audios del owner

- `AU-01-025` termina con 0,30 s de silencio, el borde más justo de los 50 (los demás están
  entre 0,5 y 1,4 s). El texto se transcribió completo, así que sirve.
- `AU-03-001` dura 22,02 s y tiene un golpe suave en el segundo 21, compatible con levantar el
  celular de la mesa. Los primeros 20 s son silencio de habitación.

### 3. Tres palabras que conviene oír

- `AU-02-004`: el transcriptor oyó «se levantan **bien** temprano» donde el plan dice «se
  levantan temprano».
- `AU-02-005`: el transcriptor oyó «los niños y niñas» donde el plan dice «los niños y las
  niñas».
- `AU-01-003` (Samuel): el transcriptor oyó «bienvenido», en singular, donde el plan dice
  «bienvenidos». Es una sola letra y el transcriptor suele comerse la ese final; si se quiere
  descartar, es oír los últimos dos segundos del audio.

Puede ser el transcriptor —las dos son confusiones típicas— o una palabra de más o de menos
al leer. Estos dos audios no se comparan contra ese texto (AU-02 mide el cierre, no la
transcripción), así que no afecta a la prueba; queda anotado por si se citan como ejemplo.

## Lo que falta

- **La ficha está completa**: los cuatro celulares, las distancias y el ruido.

## Los diez con ruido, con el nivel igualado

Los diez `-006` venían **6,3 dB más bajos** que el bloque con ruido del owner, con la misma
relación voz/ruido (21,9 dB contra 21,8 dB). O sea: misma ganancia de grabación distinta, no
otro ruido. Hay una copia derivada con el nivel subido en `derivados/ruido-nivel/`, con la
comparación audio por audio en su `README.md`. **Los originales no se tocaron.**

| Bloque | Piso | Techo | Relación voz/ruido |
|---|---|---|---|
| Los 10 con ruido del owner | −37,2 dB | −15,3 dB | 21,8 dB |
| Los 10 de Sergio, originales | −43,5 dB | −21,6 dB | 21,9 dB |
| Los 10 de Sergio, subidos +6,3 dB | −37,2 dB | −15,3 dB | 21,9 dB |

Subir el nivel no cambia de quién es la voz: esos diez siguen siendo la voz de H4, y la
transcripción sigue dando el mismo texto que antes.

## Los cinco ZIP

| Archivo | Bytes | sha256 |
|---|---|---|
| `AudiosTesisJD.zip` | 3 677 328 | `3bc96a0238c8343ec6ec693504c8ce1440905610b4d300ab9374c2a34df218cd` |
| `new-2026-11-03.zip` | 278 382 | `8608ed019e03f5fa2cdf3b97f316c09b5a2a2f9dd60c9ab8e2ec7a268ac939e1` |
| `AudiosSignAllCompaneros.zip` | 1 149 878 | `9259f7230306770596c44b90ac81ddf6a25a6f0f8e002e0887563412544a33df` |
| `habla-2026-11-03.zip` | 787 846 | `52e1ed7d1d72c7175b8c195d679196c53be150fcc8679caf049c2e2075c7624f` |
| `AduisoSignAllConSamuel.zip` | 1 876 468 | `615d2d64fb4afef7bc51599bec91278a4093ea48b950d96807cac79901a4c642` |

## Dónde vive esta carpeta

Hasta el 2026-11-05 el corpus estaba **fuera de los repositorios**, en `data/audios/` junto
a este árbol de trabajo. Se versionó porque las evidencias del plan citan los audios por ID
(`AU-01-014`, `AU-03-005`…) y sin los archivos la prueba no se puede repetir: la carpeta se
copió completa a `sign-ai-android/docs/plan-pruebas/audios/`, con la misma estructura y los
mismos nombres.

No es un movimiento del original: `data/audios/` sigue existiendo en la máquina, y las
bitácoras viejas lo citan así. Las dos copias son idénticas; los `sha256` de abajo lo
verifican. Los originales no se tocan; lo que consume el STT es `16k/`.
