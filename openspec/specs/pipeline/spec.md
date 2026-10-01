# Pipeline SignAI (WebView) — flujo de datos

## Flujo real

```
data/LANDMARKS/ CSVs ──► sign.json ──► WebView ──► avatar animado
(BODY 33, HANDS 21×2,     (horneado,     (app.js +
 FACE 468)                 1 seña)        avatar.glb)
      ▲                        ▲                ▲
      │                        │                │ Kotlin vía
 videos propios          script de horneado   window.SignAI
 (futuro)                (⚠️ pendiente          .play()
                          documentar cuál)
```

No hay servidor: la app es 100% local salvo el CDN de Three.js.
Flask (`../app/`), Filament, Unity y Flutter están muertos — no documentar
flujos hacia ellos.

## `sign.json` — formato real (verificado del archivo)

```json
{
  "stem": "0000_0000_0000",
  "fps": 24,
  "total_frames": 59,
  "frames": [
    {
      "hands": [
        { "hand": "Left",  "landmarks": [{"x":0.59,"y":0.88,"z":0.0}, ...] },
        { "hand": "Right", "landmarks": [...] }
      ],
      "body": [ {"x":0.45,"y":0.32,"z":-0.01}, ... ],
      "face": { "13": {"x":0.5,"y":0.6,"z":0.01}, "14": {...}, ... }
    }
  ]
}
```

- `stem`: ID `SSSS_VVVV_RRRR` de la seña horneada (actual: `0000_0000_0000`)
- `hands`: hasta 2 elementos, 21 landmarks MediaPipe Hands cada uno
- `body`: 33 landmarks MediaPipe Pose, indexados directamente (body[11] = hombro izq)
- `face`: dict con keys = string del índice FaceMesh, solo 16 landmarks
  (`1, 13, 14, 55, 70, 78, 107, 145, 159, 168, 285, 300, 308, 336, 374, 386`)
- Cara ya sincronizada a 24fps en el horneado (ver `specs/avatar`)

## Agregar una seña

1. Producir los CSVs en `data/LANDMARKS/` con el formato exacto LSC50
   (ver "Captura de señas propias" abajo).
2. Re-hornear `sign.json` (⚠️ script pendiente de documentar).
3. Extender el `listOf("0000_0000_0000")` hardcodeado en `MainActivity.kt`.

## Captura de señas propias — requisitos

Para que videos propios sean compatibles con el pipeline:

1. **Modelos**: MediaPipe Pose (33 lm) + Hands (21 lm) + FaceMesh (468 lm),
   mismos que usó LSC50.
2. **Output CSV**: exactamente el mismo formato de columnas que LSC50:
   header `landmark_0_x, landmark_0_y, landmark_0_z, landmark_1_x, ...`
3. **Estructura de carpetas**:
   ```
   data/LANDMARKS/BODY_LANDMARKS/<id>.csv
   data/LANDMARKS/HANDS_LANDMARKS/LEFT_HAND_LANDMARKS/<id>.csv
   data/LANDMARKS/HANDS_LANDMARKS/RIGHT_HAND_LANDMARKS/<id>.csv
   data/LANDMARKS/FACE_LANDMARKS/<id>.csv
   ```
4. **ID**: mismo formato `SSSS_VVVV_RRRR`; señas propias en rango nuevo (0050+).

## Integraciones futuras (carpetas hermanas en `..`)

- `sign-ai-backend`: **SÍ**. Plan en `openspec/changes/integracion-backend/tasks.md`.
  Hoy el flujo de arriba sigue siendo 100% local. Cuando la integración aterrice, este
  diagrama se reemplaza por: `micrófono → WS /ws/translate → glosas validadas →
  assets descargados a cacheDir/signs → app.js reproduce la cola`. Decisiones 2026-09-30:
  híbrido (frames de landmarks ahora, clips GLB después) y texto primero.
- `sign-ai-gloss-engine`: **NO**, fuera de alcance.

## Constantes de animación en `app.js` (valores reales)

| Variable         | Valor | Descripción                              |
|------------------|-------|------------------------------------------|
| `smoothAlpha`    | 0.12  | Fracción de slerp por frame (1.0 = snap) |
| `FACE_ALPHA`     | 1.0   | Alpha para huesos faciales (siempre snap)|
| `Z_SCALE`        | 0.40  | Factor empírico para profundidad del brazo|
| `FACE_REF_BROW`  | 0.47  | Altura de ceja neutra normalizada        |
| `FACE_BROW_SCALE`| 6.0   | Amplificación del movimiento de ceja     |
| `FACE_JAW_MAX`   | 1.2   | Apertura máxima de mandíbula, en rad     |
