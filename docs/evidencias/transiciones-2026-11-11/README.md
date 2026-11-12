# Prueba de extremos de animación — 2026-11-11

El owner detectó que los clips todavía pasan por el reposo anterior y autorizó
una prueba sobre copias, conservando el movimiento central. No autorizó integrar
esta propuesta ni extenderla a todas las señas.

## Prueba preparada

- Copias de los assets aprobados de Juan y Pauline, hashes en `verification.json`.
- Solo seña `0000`, sus cuatro variantes faciales. Por variante, solo las seis
  rotaciones `upper_arm`, `forearm`, `hand`, de ambos lados.
- Primer y último 20% de cada canal: interpolación esférica hacia el reposo
  aprobado, con peso quíntico que se anula al entrar al tramo central.
  **Es una ventana experimental, no un límite lingüístico identificado.**
- El 60% central conserva exactamente sus muestras. No se cambiaron tiempos,
  duración, dedos, cara, traslaciones, escalas, nodos, geometría ni matrices de bind.
  Las otras 196 animaciones por avatar conservan sus referencias originales.
- Se anexaron nuevos outputs al binario, sin sobrescribir accessors compartidos.
  El bloque binario anterior permanece íntegro como prefijo.
- Fuentes del hermano intactas; procedencia histórica `../signai-avatar`, commit
  `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, según el inventario del empaquetado.

## Reproducir

Copiar `prepare.py` e `index.html` a una carpeta de trabajo y ejecutar el script
desde la raíz del repo. Genera cuatro GLB al lado del script, sin tocar la app.
Servir esa carpeta con `python3 -m http.server`, abrir `index.html`.
La prueba actual está en `/tmp/opencode/transition-test/`, servida en
`http://127.0.0.1:8766/` mientras siga activo el servidor.

El visor reproduce una seña, espera en reposo y repite, con una mezcla de 100 ms
de entrada/salida. No usa el controlador de la app ni comprueba el encadenamiento
entre distintas señas. Requiere conectividad a jsDelivr para Three.js.

Se verificó carga y render de ambos pares en Chromium headless; capturas adjuntas.
**No se da por validada visualmente la transición ni el significado en LSC.**
Pendiente: revisión del owner, ajuste de ventanas si afecta el gesto, prueba de
pares de señas y eventual autorización de integración.

No se cambió la app instalada: no hubo compilación ni instalación nuevas en esta
prueba aislada. La comprobación anterior de regreso a reposo solo comprobaba el
estado final, no la naturalidad del movimiento.
