# Reposo aprobado de Juan y Pauline — 2026-11-11

El owner aprobó el giro intermedio de las manos, primero sobre copias y después
autorizó integrar ambos avatares. `comparacion-punto-medio.png` muestra las tres
opciones de Juan (centro aprobado); `pauline-antes-despues.png`, la adaptación.

## Verificación en dispositivo físico

- Redmi Note 9S, Android 11, build `debug`, herramientas de desarrollo apagadas.
- `./gradlew build -Psignai.backendWsUrl=ws://127.0.0.1:8080/ws/translate` pasó,
  incluido ktlint; después `installDebug` instaló el APK en el teléfono.
- Backend apagado: la etiqueta «Desconectado» de las capturas es esperada.
  **No es una prueba del WebSocket, voz ni traducción.**
- Reproducción local desde Chrome DevTools Protocol del WebView, sin cambios
  en el código JS: `setQueue` + `play`, con `signs/0000_0000_0000.glb` y tipos
  `AFFIRMATIVE`, `YES_NO_QUESTION`, `NEGATIVE`, `IMPERATIVE`, en cada avatar.
- Ocho reproducciones de esa seña de prueba: se observó `state.playing = true`
  y después `false`; las seis rotaciones regresaron al reposo aprobado
  (tolerancia 1e-5 en componentes del cuaternión, equivalencia q/−q).
  Inspección mediante breakpoint temporal en `queueState`; retirado al terminar.
  No se midieron rendimiento ni tiempos con este procedimiento, que pausa JS.
- `device-smoke.json` guarda los resultados. Las dos capturas `tras-reproducir`
  muestran el reposo posterior. No sustituye la revisión visual de las 50 señas.

## Integridad de los assets

El empaquetado regenerado coincide byte a byte con las copias aprobadas.
Frente a los assets anteriores: binario y 200 animaciones idénticos por avatar;
solo seis rotaciones de nodos difieren. Los originales de `../signai-avatar`,
commit `fc6f372c8412010d85925fe342fdcf0c073fbdf1`, no se modificaron.
Los hashes originales y las rotaciones aprobadas están en
`tools/approved_rest_pose.json`; hashes nuevos en `models/avatars.json`.

El empaquetador rechaza fuentes con otro hash y clips sin canales de rotación
para los huesos ajustados. Se comprobaron ambas guardas y la idempotencia en
los dos avatares. La advertencia histórica de bind de Pauline se mantiene;
no se cambió geometría ni matrices de bind para intentar corregirla.
