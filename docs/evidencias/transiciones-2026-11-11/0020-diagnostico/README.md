# 0020: diagnóstico separado del movimiento corporal brusco

**2026-11-11. Estado:** inspección, no corrección. El owner aprobó entradas 03
pero describió un movimiento «como si le diera un escalofrio» después del gesto
de `0020` y pidió mirarlo aparte. Se pausan nuevas propuestas para localizarlo.

## Evidencia

`audit.py` compara asset original con `entries-03/despues.glb`, sin editar ninguno:
**ocho clips** (cuatro variantes × dos avatares). Todos los canales fuera de las
seis rotaciones modificadas de brazos/antebrazos/manos son idénticos al original,
incluidos torso, hombros, piernas y dedos. Hashes y eventos en `audit.json`.

Ejemplos de `0020_afirmativo` de Juan, calculados de cuaterniones normalizados:

- Entre **1,9583 y 2,000 s**, una articulación del anular izquierdo cambia
  **86,44°**; el índice izquierdo también presenta cambios grandes. Esos canales
  no fueron editados por nuestros ajustes. En el original la muñeca izquierda
  cambia unos 50,07° en ese intervalo (esa muñeca sí se suavizó en el regreso).
- Entre **2,1667 y 2,2083 s**, muslo izquierdo **16,59°**, derecho **14,22°**,
  hombro izquierdo **11,63°**, derecho **10,73°**; traslación de `spine` cambia
  aproximadamente **0,00871 unidades locales**. Canales originales conservados.
  No se convierte esa traslación en centímetros físicos sin verificar la escala.
- Ambos intervalos duran aproximadamente **42 ms**. Las capturas de los frames
  52/53 muestran un cambio de postura corporal muy rápido, también en la versión
  revisada. El owner confirmó después **«Si, es ese el que noto»**, en respuesta
  a la pregunta sobre el tirón corporal alrededor de 2,2 s. Momento identificado;
  no se interpreta como aprobación de una corrección todavía no preparada.

No se atribuye causa de exportación, validez/invalidez lingüística ni necesidad
de filtrar todos los canales. Un cambio grande de dedos podría ser significativo:
no se suaviza automáticamente. El diagnóstico distingue lo que existe en el
original de las seis rotaciones editadas y de la mezcla final del visor.

## Visor de inspección

**http://127.0.0.1:8766/0020-inspect/**.
Izquierda original, derecha versión de entradas 03. Ambos sin arreglo nuevo.
Velocidad normal/mitad/cuarto, pausa y desplazamiento manual de tiempo.
Por defecto muestra **solo el clip grabado, sin mezcla al reposo**: los tiempos
indicados corresponden al clip y mantiene la última muestra al llegar al final.
Al desmarcar esa opción se aplica v3: durante el acomodo el reloj/slider siguen
siendo tiempo local de reproducción, no el tiempo del clip muestreado.

`browser-checks.json`: 14 selecciones/tiempos verificados en Chromium headless,
ambos avatares y cuatro variantes; comprobadas pausa, lectura temporal y modo
sin mezcla. Capturas afirmativas en 47/24, 48/24, 52/24 y 53/24 s. Son evidencia
de poses, no una medición de rendimiento ni validación lingüística.

## Reproducir

Generar primero `entries-03` como carpeta hermana; ejecutar `audit.py` desde raíz.
Para el visor, poner/enlazar `juan-antes.glb` y `pauline-antes.glb` a los assets
originales de app, y `*-despues.glb` a los de `entries-03`, sin sobrescribirlos;
servir esta carpeta por HTTP. Three.js necesita jsDelivr.
Fuentes originales de producción intactas: `../signai-avatar`, commit
`fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según procedencia del empaquetado.

Momento confirmado y copia autorizada después por el owner («Si»; «sigue»).
Propuesta localizada preparada en `../0020-cuerpo/`, visor `/0020-smooth/`,
pendiente de aprobación visual. Entradas/curvas locales de brazos aprobadas
no se cambian; la suavización corporal afecta su trayectoria global en el tramo.
App y
GLB intactos; no hubo build/instalación por esta inspección externa.
