# 0028: propuesta aislada para el salto inicial

**2026-11-11. Estado:** corrección del salto aprobada visualmente. El owner
autorizó «Si. Go.» y después respondió «Bien, excelente. Muy muy muy buena».
Señaló aparte que aún pasa por el reposo viejo al inicio; esa segunda corrección
se prepara en `../0028-entrada/`, sin alterar esta evidencia aprobada.

## Diagnóstico y alcance

Partimos del `despues.glb` de grupo 07 aprobado: los regresos se conservan.
La inspección más detallada de `0028_afirmativo` de Juan encontró diferencias
entre primera/segunda muestra también en brazos superiores (45,329° L,
40,578° R), manos (30,041° L, 36,256° R), dedos, pies y cabeza. No era solo codos.
No se atribuye una causa de exportación concreta ni se valida significado LSC.

Se sustituye **solo la primera muestra TRS que tiene segunda muestra a 2/24 s**
por el valor de esa segunda muestra. Se conservan los tiempos, duración,
interpolaciones y todas las muestras posteriores, incluido el regreso aprobado.
Canales constantes y canales compactados con solo extremos del clip se dejan
intactos: no tienen el salto entre 1/24 y 2/24 s. No se edita el peso de la cara.
La primera postura queda sostenida hasta 2/24 s en vez de hacer el salto previo.

Se aplica a las cuatro variantes de `0028` en ambos avatares. La comparación
muestra el mismo final suave v3 **en ambos lados**, para aislar el cambio inicial.
Izquierda: grupo 07 aprobado con el salto; derecha: corrección inicial propuesta.

## Integridad comprobada

`prepare.py` incluye comprobaciones que fallan ante estructura/interpolación o
tiempos inesperados. `verification.json` conserva hashes y detalle de cambios:

- Juan: 232 primeras muestras TRS cambiadas entre las cuatro variantes.
- Pauline: 384 primeras muestras TRS cambiadas entre las cuatro variantes.
- Todas las muestras posteriores son exactas al grupo 07 aprobado.
- Tiempos, nodos, pesos de cara y las otras 196 animaciones son idénticos.
- El binario anterior se conserva como prefijo; se anexan outputs nuevos para
  no sobrescribir datos compartidos. Fuentes de grupo 07 intactas.
- `browser-checks.json`: ocho selecciones/render en Chromium headless (cuatro
  variantes × dos avatares). Capturas a 0,05 s del clip, 0,75 s del visor.
  No certifican fluidez ni validez lingüística.

## Reproducir/revisar

Visor temporal: **http://127.0.0.1:8766/0028-start/**.
Para reproducir fuera, generar primero grupo 07; ubicar su carpeta como hermana
`group-07/` con los `despues.glb`. Ejecutar `prepare.py` desde raíz del repo y
servir la carpeta del visor por HTTP. Three.js requiere jsDelivr.

El script parte de assets empaquetados de grupo 07, cuyo origen documentado es
`../signai-avatar`, commit `fc6f372c8412010d85925fe342fdcf0c073fbdf1`.
Originales hermanos y app intactos. No hubo build/instalación por esta prueba
externa. Corrección del salto aprobada; pendiente revisar la entrada desde el
reposo nuevo y posterior integración, sin cambiar aprobaciones de los regresos.
