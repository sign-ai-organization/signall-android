# Grupo 03: 0008–0012

**2026-11-11. Estado:** cinco propuestas aprobadas visualmente, integración pendiente. Grupo anterior
`0003`–`0007` aprobado por el owner; no se modifican esas propuestas.

| Seña | Inicio de regreso | Llegada al reposo | Revisión | Integración |
|---|---:|---:|---|---|
| `0008` | 33/24 = 1,375 s | 47/24 = 1,9583 s | Aprobada | Pendiente |
| `0009` | 32/24 = 1,3333 s | 45/24 = 1,875 s | Aprobada | Pendiente |
| `0010` | 33/24 = 1,375 s | 46/24 = 1,9167 s | Aprobada | Pendiente |
| `0011` | 32/24 = 1,3333 s | 45/24 = 1,875 s | Aprobada | Pendiente |
| `0012` | 29/24 = 1,2083 s | 43/24 = 1,7917 s | Aprobada | Pendiente |

El owner respondió **«hermoso todo aprobado, siguientes»** el 2026-11-11.
Las cinco propuestas quedan aprobadas sin correcciones. No especificó revisión
individual por avatar/variante ni se atribuye validación lingüística formal.

Los límites son hipótesis para revisión, no fronteras lingüísticas comprobadas.
Se inspeccionaron las curvas de los antebrazos de Juan, variante afirmativa:
`0008` y `0010` mantienen flexión hasta la zona de 1,292 s y bajan después;
`0009` presenta una última elevación del codo izquierdo hasta esa zona, que se
conserva antes del ancla; `0011` mantiene la postura hasta 1,292 s; `0012` tiene
una elevación final hacia 1,167 s y se propone regresar después. Es necesario
revisar que no se elimina un contacto ni un componente significativo del gesto.

La entrada de `0012` ya está flexionada y se conserva; **todas las entradas y las
muestras hasta el ancla son exactas**. Solo cambian seis rotaciones de brazos,
antebrazos y manos por clip, mediante regreso directo con slerp y peso quíntico.
No se cambian dedos, cara, geometría, nodos, traslaciones, escalas ni tiempos.
Son copias separadas de los assets de app. Las otras 180 animaciones por avatar
no cambian.

El visor compara actual/propuesta y aplica a la derecha el acomodo final v3 de
700 ms para el cuerpo completo. No se encadenan señas. Este acomodo es del visor,
no una edición del GLB ni una integración en el controlador de la aplicación.

## Reproducción y verificaciones

Visor temporal: **http://127.0.0.1:8766/group-03/**.
Para reproducir fuera: copiar scripts/HTML juntos, ejecutar `prepare.py` y
`check.py` desde la raíz del repo y servir la carpeta por HTTP. El enlace al
grupo anterior necesita esa carpeta hermana. Three.js requiere jsDelivr.

- `verification.json`: hashes de entrada y ventanas, ambos avatares.
- `checks.json`: 120 canales por avatar; entradas y muestras protegidas exactas,
  normalización, regreso angular monótono y reposo exacto en el extremo.
- El cálculo angular del test normaliza ambos cuaterniones float32 antes de
  aplicar acos: sin ello, q contra sí mismo puede dar un ángulo ficticio. Se
  corrigió la comprobación, no se relajó la tolerancia ni se cambiaron los assets.
- `browser-checks.json`: 40 selecciones/render en Chromium headless (cinco señas,
  cuatro variantes, dos avatares). No certifica fluidez ni significado LSC.
- Capturas afirmativas a tiempo local 1,75 s (visor 2,45 s), por seña/avatar.
- Fuentes originales intactas de `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`; procedencia de los assets según sus
  inventarios e hashes de entrada del informe.

No se modificó la app ni se compiló/instaló por esta prueba externa. Pendientes
integración posterior de las cinco aprobadas y prueba en el controlador real.
