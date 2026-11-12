# Tercera prueba: desaceleración y acomodo final del cuerpo

El owner valoró v2 como «muchísimo mejor». Después pidió suavizar el último
cambio del movimiento al reposo estático, especialmente el acomodo del torso.
No pidió integrar ni extender a todas las señas.

Esta variante cambia **solo el visor**. Ambos lados cargan exactamente el GLB
`despues` de v2: izquierda reproduce v2; derecha desacelera el último tramo y
reduce progresivamente el peso de la animación hacia el reposo de los nodos.
No se modificaron archivos GLB, el empaquetador, la app ni fuentes hermanas.

- V2 usaba en el visor una salida lineal de 100 ms después del final del clip.
  La app tiene su propia salida de 100 ms calendarizada antes del final; no son
  una reproducción idéntica del controlador ni una prueba del dispositivo.
- V3 inicia el acomodo 250 ms antes del final del clip y lo reparte en 700 ms.
  Este parámetro experimental alarga el final visible respecto a v2 en 350 ms.
- Tiempo del clip mediante Hermite cúbica: velocidad inicial 1, final 0 y avance
  monótono. Peso de la animación mediante curva quíntica: 1 al empezar, 0 al
  terminar, derivadas nulas de la mezcla en ambos extremos.
- No cambia el regreso de brazos aprobado en v2 (1,375–1,9167 s). El ajuste nuevo
  empieza a 2,2083 s del clip de 2,4583 s y acaba a 2,9083 s de reproducción local.
  Afecta también torso, cabeza, dedos y cara por la mezcla del clip completo.
  Los pesos y tiempos anteriores a ese tramo siguen iguales a v2.

Verificados en Chromium headless los dos avatares y 1001 muestras de la función
temporal: tiempo sin retrocesos, peso decreciente en [0,1], continuidad en el
inicio y peso final cero. `checks.json` conserva los resultados; las capturas
son durante el acomodo, no evidencia de fluidez del movimiento.

Visor: `http://127.0.0.1:8766/v3/`, mientras siga el servidor. Para reproducir
fuera de esa carpeta, mantener v3 junto a v2 y generar los GLB de v2 primero.
Pendiente aprobación visual del ritmo final por el owner. Sin mediciones de
rendimiento, prueba lingüística, build ni instalación nuevos (prueba externa).
