# Segunda propuesta: regreso directo, no mezcla con el regreso viejo

El owner rechazó la primera ventana: todavía veía pasar los brazos por el reposo
anterior antes de llegar al nuevo. Autorizó otra prueba sobre copias.

En `0000_afirmativo`, los canales de codo del GLB bajan hacia su extensión desde
aproximadamente 1,375 s; a 1,708 s están a unos 3,8° de su reposo aprobado en ambos
lados (calculado de sus cuaterniones). El brazo superior sigue a unos 38°/31,7°.
Esto orienta una ventana de prueba, **no certifica dónde termina el gesto LSC**.

La propuesta conserva la entrada experimental de v1 y las muestras centrales
hasta **1,375 s inclusive**. Después **sustituye** las curvas de seis rotaciones
por un regreso directo desde esa muestra al reposo aprobado, usando interpolación
esférica y peso quíntico. Llega a **1,9167 s** y mantiene ese reposo hasta el final.
No mezcla con las muestras del recorrido viejo, como sí hacía v1.

Son límites provisionales de esta seña de prueba, no parámetros para las otras 49.
La nueva ventana modifica parte del tramo que v1 conservaba; requiere aprobación
visual para asegurar que no se haya adelantado el regreso a costa del gesto.

`checks.json`: 24 canales verificados por avatar, cuatro variantes de `0000`:
cuaterniones normalizados, muestras centrales exactas, distancia angular al
reposo no creciente en el regreso y reposo exacto desde 1,9167 s. Las otras 196
animaciones, dedos, cara, geometría y demás canales permanecen intactos.

Visor en `http://127.0.0.1:8766/v2/`, con enlace a la primera propuesta, mientras
siga el servidor de prueba. Carga y render comprobados en Chromium headless en
ambos avatares. Capturas adjuntas: tiempo del clip 1,95 s (visor 2,65 s por su
espera inicial de 0,7 s), antes del desvanecimiento final.

La app instalada y sus assets no cambian; no hubo compilación ni instalación
nuevas. Pendientes aprobación del owner, revisión del punto inicial del regreso
y comprobación del encadenamiento entre señas. No hay validación lingüística.
