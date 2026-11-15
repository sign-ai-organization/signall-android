# SignAll — app Android

App Android del proyecto SignAll: español hablado a Lengua de Señas Colombiana (LSC),
representada por un avatar 3D. Captura el audio y las frases escritas, las manda al backend y
representa las señas que el backend devuelve.

Trabajo de grado de Ingeniería de Sistemas, Pontificia Universidad Javeriana. Corpus fijo de 50
señas (LSC50) para el caso de uso "Registro Básico de Datos".

Este archivo son las reglas del repositorio. Lo demás se carga cuando la tarea lo pide, al final
está el índice.

## El sistema

- signall-android: esta app.
- signall-backend: FastAPI, WebSocket, glosas, catálogo y caché semántico. Puerto 8080.
- signall-stt: faster-whisper y VAD, audio a texto. Puerto 8000.
- signall-docs: los documentos de la tesis.
- signall-avatar (rama dev): producción de las animaciones.

Los repos hermanos son fuentes de información, no cajas negras. Antes de cambiar un frame, una
glosa o un nombre de métrica se lee el otro lado: el contrato son frames y ningún compilador
avisa cuando se rompe. Un cambio que cruza el contrato no está terminado hasta que el otro lado
lo refleja; si queda pendiente, se dice.

signall-docs es el repositorio de los documentos y el que manda para lo escrito.

Los comandos suponen los clones uno al lado del otro (../signall-backend).

## Cómo se escribe

Se le habla al owner en español neutro, sin voseo y sin jerga sin explicar: la idea primero y el
detalle después.

- El término de la tesis es representar: el avatar representa las señas, no "firma" ni "signa".
  Para mandar una frase se dice enviar. En inglés se queda signing.
- Los números van con su origen. Una cifra de fixture no es una cifra de persona.
- No se inventa terminología: si una palabra no está en signall-docs, es del owner hasta que la
  avale.
- Una decisión, una pregunta: se muestran las opciones con lo que se gana y se pierde, y se para.
- Se separa lo medido de lo supuesto.

## Documentación

Cada cambio no trivial se documenta en signall-docs/cambios: qué cambió, por qué y qué quedó
pendiente. Si toca un documento formal (srs, sdd, memoria, plan-pruebas, administracion, vfp) se
edita el .tex y se compila con make; un documento que no compila no es un cambio terminado.

Acá la sesión además deja openspec/sessions/YYYY-MM-DD-slug.md. Si el dato viene de otro
repositorio se dice de cuál y de qué commit. Las evidencias de pruebas van a
signall-docs/evidencias y se agregan, no se corrigen.

## Las dos fuentes de verdad

1. Este repositorio: el código.
2. signall-docs: los documentos formales y los borradores.

Antes de decidir se leen los documentos. Al implementar se comprueba que el código esté alineado.
Si no lo está, no se sigue por cuenta propia: se determina cuál de los dos está mal y se pregunta
al owner.

Si dos documentos se contradicen entre ellos no hay ganador automático: se citan las dos versiones
y se pregunta.

## El contrato con el backend

Un solo WebSocket, de la app al backend, en :8080/ws/translate. La app nunca habla con el STT ni
con Neon.

- Solo se traduce lo que el cliente manda como text. La voz vuelve como transcript y no se
  traduce; un eco de la frase escrita ya quemó el proceso una vez.
- segment es una oración, con index y total reales desde el primero. No existen stream,
  provisional ni source_text.
- El final siempre llega, incluso vacío: un final sin texto es "no se escuchó nada", no un error.
- Los nombres de métrica son los mismos en los dos lados (rt_* en el backend, SignAllMetrics en el
  dispositivo).

Para cambiar un frame o una métrica se usa el skill contrato-websocket.

## El servidor que consume la app

El default es el entorno del plan de pruebas: Whisper medium en GPU (cuda/float16), con la tarjeta
reservada. Nada detecta el hardware.

    docker compose -f docker-compose.yml -f docker-compose.host.yml up -d

Para una PC sin GPU se agrega -f docker-compose.cpu.yml: fija small/cpu/int8, quita la reserva y
compila el STT sin librerías CUDA. Cambiar de perfil necesita --build la primera vez. Se corre
desde ../signall-backend.

La app no se configura: descubre el backend sola (endpoint recordado, mDNS, escaneo de la subred
y por último la dirección compilada).

## Build y verificación

    ./gradlew build
    ./gradlew ktlintCheck
    ./gradlew installDebug

Después de cada cambio hay que compilar e instalar en el dispositivo, para que el owner lo pruebe
de inmediato. Nunca se cierra un trabajo sin instalar. Si no hay dispositivo, se dice.

Para tocar la app en el teléfono se usa el MCP mobile-mcp, no coordenadas adivinadas: primero
mobile_list_elements_on_screen, después se toca por ref y se verifica con una lectura nueva. Si el
MCP no está, el fallback es tools/dev.sh. El detalle está en el skill controlar-app-movil.

Trampas:

- La dirección compilada por defecto es el loopback del emulador; en un teléfono real normalmente
  no hay que configurar nada porque la app descubre el backend. Para USB se usa adb reverse.
- El build demo es el del cifrado: hereda release, usa wss:// y comparte applicationId con debug,
  así que después hay que reinstalar debug. Necesita la CA del backend.
- La consola de la escena y el bloque AVANZADO solo existen si la compilación los pide con
  -Psignall.devTools=true o SIGNALL_DEV_TOOLS=1. Están apagados por defecto.
- RECORD_AUDIO es permiso de runtime. Sin él, el micrófono falla y parece un error del STT.
- Tocar un control cierra el teclado, no manda la frase.

Pruebas: ./gradlew test y ./gradlew jacocoTestReport. No hay CI. Todo se verifica con
tools/calidad/verificar.sh, y SonarQube es local con tools/calidad/sonar.sh.

## Observabilidad

- Backend :8080/metrics: rt_first_sign_ms, rt_phrase_total_ms, rt_sentence_delay_ms y contadores.
- STT :8000/metrics: inferencia, espera de GPU, lag de audio, drops.
- adb logcat -s SignAllMetrics: oraciones, glosas, duración de la representación, p50 y p95.
- adb logcat -s SignAllNet: cada segment con index y total.
- adb logcat -s SignAllWeb: consola JS, solo con DEV_TOOLS.

La métrica se agrega antes que la feature. El número que decide la experiencia es audio a primera
seña: p50 4366 ms, de los cuales el STT se lleva 3724 ms. Con el flujo por oraciones,
rt_first_sign_ms da p50 632 a 675 ms con el LLM en frío y 0,5 ms con caché caliente. Cómo
repetirlo, en tools/TIEMPO-REAL.md.

## Git

Commit y push directo a main, sin PR y sin pedir confirmación. Los repos hermanos no se commitean
sin que el owner lo pida. Todo va a main; develop no existe. signall-avatar se queda en dev y ahí
no se toca.

## UI

- Lumo UI (lumoui.com) para componentes nuevos, no el Lumo de Vaadin. Se personaliza en el design
  system de SignAll.
- Material 3 se migra, no se agrega: hoy hay cero imports.
- Iconos Phosphor para lo nuevo.
- Los tokens viven en ui/designsystem/foundation y se usan en vez de .dp sueltos.
- Paleta en ui/designsystem/Color.kt. El default es oscuro grafito. El azul de marca vive solo en
  la acción principal y los controles. El ámbar live/onLive es lo único que significa "escuchando
  ahora", y nunca es la única señal.
- Sobre oscuro, iconos y líneas finas usan colors.text, no strong.
- Look minimalista, con el avatar como protagonista. Nada que degrade el WebView.
- Las teclas se tocan una vez y quedan prendidas; ninguna se mantiene apretada.

## El puente Kotlin y JS

El WebView carga por WebViewAssetLoader. JS avisa a Kotlin por window.SignAllBridge: onPlaybackDone
al terminar la cola, avatarReady cuando todo está listo, progress con el avance real para el splash
y signStart por cada seña.

avatarReady no es "llegó el archivo": se manda después de indexar los 200 clips, resolver la seña
de arranque, compilar shaders y pintar un frame. Es lo que esconde el splash, así que si se manda
antes se ve el canvas vacío.

El WebView no se crea en el primer frame: se monta dos withFrameNanos después para que el splash ya
esté en pantalla.

Los comandos están en domain/SignAllCommands.kt. Se mantiene la compatibilidad hacia atrás y el
guard ?. Un método nuevo es aditivo; borrar o renombrar rompe en silencio.

## Gotchas

- Todo el estado de Compose vive en ui/state/MainScreenState.kt. No hay ViewModel, ni DI, ni
  navegación.
- Los drawers son un solo componente (EdgeDrawer, side Start/End) y solo uno se abre a la vez. Su
  layering es frágil: antes de tocarlo se lee el comentario ORO del archivo.
- MIUI force-dark tiene que seguir apagado o el texto de los drawers se ve blanco.
- windowSoftInputMode va en la activity, no en la aplicación. El inset de abajo es uno solo:
  safeDrawing bottom, nunca navigationBarsPadding e imePadding sumados.
- Con navegación por gestos, unos 20 dp de cada borde son del sistema y MIUI ignora la exclusión;
  por eso las zonas táctiles de la pestaña son más grandes que el dibujo.
- El avatar 3D es invisible para la accesibilidad: el canvas no publica nada. Para el estado de la
  escena se usa SignAllWeb o el overlay de diagnóstico, los dos solo con DEV_TOOLS.
- El micrófono, la cola y la latencia están en docs/MODO-EN-VIVO.md.

## Assets web

- index.html carga Three.js desde un CDN: el teléfono necesita internet aunque los datos sean
  locales.
- models/juan.glb y models/pauline.glb traen el personaje y las 50 señas como clips, 200 por
  archivo; la escena indexa solo el avatar elegido y resuelve signs/id.glb por nombre, sin red.
- Nunca se edita un glb a mano: se regeneran con tools/pack_avatars.py. avatars.json es el
  inventario reproducible.
- humanize.js y nmm.json son la capa de cabeza, mirada, parpadeo y respiración. El orden restore,
  tickPlayback y apply no se cambia: sin restore la capa se acumula.
- La normalización de escala y offset va en un pivote padre, nunca en la raíz de la escena.
- assets/web/signs ya no existe, pero la ruta signs/id.glb sigue siendo el identificador. No se
  limpia del código.
- setPixelRatio está capado en 1.75 a propósito. No se saca el no-store de los assets ni se fuerza
  DoubleSide en las mallas.
- El arranque tarda 1,8 a 3,4 segundos según caché de página.

## Neon

La app no habla con Neon. La organización del proyecto es org-orange-surf-39007829 (sign-ai); la
otra organización de la cuenta es personal y no se toca. El catálogo se carga una sola vez al
arrancar el backend, así que después de un seed hay que reiniciarlo.

## OpenSpec

openspec/specs es la fuente de verdad del comportamiento y openspec/sessions la bitácora. Si un
spec contradice al código, gana el código y la diferencia se reporta.

## Qué leer según la tarea

- Frame, métrica, catálogo o algo que cruce repos: skill contrato-websocket.
- Código y documentos en desacuerdo: skill fuentes-de-verdad.
- Documentos de la tesis: skill documentos-tesis.
- El sistema, el STT o una decisión de tesis: docs/SISTEMA.md.
- Tocar el teléfono o medir: skill controlar-app-movil, con el MCP mobile-mcp.
- Micrófono, cola o latencia: docs/MODO-EN-VIVO.md y tools/TIEMPO-REAL.md.
- Conexión con el backend, mDNS o "no conecta": docs/DESCUBRIMIENTO-BACKEND.md.
- Compose, Lumo o accesibilidad: skill compose-best-practices y ui/designsystem/Color.kt.
- Colores y marca: docs/manual-identidad-visual.md.
- Cerrar una sesión: skill sesion-openspec.

Agentes: @pipeline para el contrato, @ui para Compose, @contraste para comparar código y
documentos (solo lectura).
