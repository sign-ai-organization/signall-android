# Grupo 02: cinco propuestas, 0003–0007

**2026-11-11. Estado:** aprobado visualmente por el owner, pendiente de integración.
El owner pidió grupos de **cinco señas nuevas**. Grupo 01 (`0000`–`0002`) aprobado;
este grupo no cambia esas propuestas ni equivale a integrarlas en la app.

| Seña | Inicio propuesto (frame/24) | Llegada al reposo (frame/24) | Revisión | Integración |
|---|---:|---:|---|---|
| `0003` | 35/24 = 1,4583 s | 48/24 = 2,000 s | Aprobada | Pendiente |
| `0004` | 38/24 = 1,5833 s | 48/24 = 2,000 s | Aprobada | Pendiente |
| `0005` | 32/24 = 1,3333 s | 45/24 = 1,875 s | Aprobada | Pendiente |
| `0006` | 35/24 = 1,4583 s | 47/24 = 1,9583 s | Aprobada | Pendiente |
| `0007` | 28/24 = 1,1667 s | 42/24 = 1,750 s | Aprobada | Pendiente |

El owner respondió **«hermoso todo aprobado, siguientes»** el 2026-11-11.
Se registran las cinco propuestas sin correcciones. No especificó un desglose
de revisión por avatar/variante y no se atribuye validación lingüística formal.

Ventanas **provisionales**, elegidas por separado tras inspeccionar las curvas
de brazos/antebrazos de los GLB actuales, no por un porcentaje común:

- `0003`: los codos siguen flexionados hasta aproximadamente 1,417 s; después bajan.
- `0004`: el codo derecho mantiene flexión hasta aproximadamente 1,542 s;
  se propone un inicio posterior al de `0003`.
- `0005`: ambos codos empiezan a bajar después de la zona de 1,292 s.
- `0006`: los codos mantienen su flexión hasta la zona de 1,417 s y después bajan.
- `0007`: el regreso aparece antes; no se le aplica el inicio tardío de `0004`.

Esto orienta la prueba, **no demuestra dónde termina el significado de la seña**.
La revisión debe comprobar que los gestos se conservan y que el regreso no se
adelanta ni elimina un contacto. La aprobación posterior consta arriba.

## Qué cambia y qué se conserva

Copias de Juan y Pauline; cuatro variantes por seña. Solo se sustituyen las seis
rotaciones de `upper_arm`, `forearm`, `hand`, ambos lados, después del inicio
indicado. Interpolación esférica desde la muestra ancla al reposo aprobado, con
peso quíntico; se mantiene ese reposo al llegar al extremo. No se mezcla con el
recorrido viejo. **Todas las entradas y las muestras previas al regreso son
exactas al original**. Dedos, cara, otros canales, geometría y nodos no se editan.

El visor añade a la derecha el acomodo final v3 de 700 ms para el cuerpo completo;
no modifica los datos GLB. Puede solaparse con el final de la curva de regreso:
también debe revisarse. Izquierda muestra el asset actual de la app, no la
propuesta aprobada del grupo anterior. Las señas se reproducen individualmente,
no se ha probado su encadenamiento.

## Evidencia técnica

- `prepare.py` genera las copias desde la raíz del repo, al lado del script.
- `check.py` verifica **120 canales por avatar**, 24 por seña: normalización,
  muestras previas exactas, regreso angular monótono y reposo exacto en el extremo.
- Las otras **180 animaciones** por avatar están intactas. No se cambian tiempos,
  nodos ni el binario anterior; se anexan outputs para evitar accessors compartidos.
- `verification.json`: ventanas y hashes de entrada. `checks.json`: comprobaciones.
- `browser-checks.json`: **40 selecciones con render** en Chromium headless,
  cinco señas × cuatro variantes × dos avatares. No es validación lingüística.
- Capturas afirmativas por avatar/seña a tiempo local 1,75 s (visor 2,45 s).
  Las imágenes no prueban por sí solas la continuidad del movimiento.
- Procedencia: assets de la app con reposo aprobado; fuentes originales intactas
  de `../signai-avatar`, commit `fc6f372c8412010d85925fe342fdcf0c073fbdf1`.

Visor temporal: **http://127.0.0.1:8766/group-02/**. Para reproducir aparte, copiar
scripts y HTML juntos, ejecutar `prepare.py` y `check.py` desde la raíz del repo y
servir la carpeta por HTTP. El enlace al grupo anterior requiere esa carpeta
hermana. Three.js se descarga de jsDelivr.

Sin cambios en la app instalada ni build/instalación nuevos por esta prueba
externa. Aprobaciones registradas; pendiente integración posterior con pruebas de
cola, velocidad, variantes y movimiento reducido en el dispositivo.
