# La velocidad de las señas pasa a botones −/+ y AVANZADO deja de existir fuera de desarrollo

**Fecha:** 2026-11-03, tarde · **Rama:** `main`

## Qué llegó

El owner pidió que **cada fila de Ajustes se entienda de un vistazo**: que el control muestre
su valor, que el ícono diga qué es, que ninguna etiqueta se parta en dos líneas y que todo
pese lo mismo visualmente. En particular, la velocidad era una barra sin número: la pidió
como **dos botones** (menos/más), igual que «Tamaño de interfaz».

Después pidió dos cosas más, en mensajes separados:

1. Que el bloque **AVANZADO** (cámara libre, suelo, fondo, seña de prueba, seña de ejemplo)
   **no esté en la app que se entrega**: apagado, no borrado.
2. Que **quite todo lo que se había agregado para TalkBack** en esta misma sesión: no lo pidió,
   y no lo quiere en el código.

## Qué se hizo

Commit único `98fde03` (`feat(settings): velocidad con botones y AVANZADO solo en
compilaciones de desarrollo`).

1. **Velocidad con botones** (`ui/screens/settings/SettingsSidebar.kt`, `SpeedRow`): sale el
   `SignAiSlider` (mapeaba 10..100 a 0..1 y nunca mostraba el número) y entra el
   `SignAiStepper` que ya usaba «Tamaño de interfaz». Paso **10**, rango **10..100**, valor
   visible entre los dos botones. La escala guardada y el rango no cambian.
2. **Alineado a la grilla** (`ui/components/SignAiStepper.kt`, `steppedValue`): un valor viejo
   fuera de la grilla (en el teléfono del owner había quedado **52**) se ordena al primer
   toque — «−» a 50, «+» a 60 — en vez de dar 42/62 y no volver nunca a un múltiplo del paso.
3. **El cambio de velocidad no corta la seña en curso** (`assets/web/app.js`): `setSpeed`
   aplica al instante solo si la escena está quieta; si hay una seña saliendo, el valor se
   guarda y se aplica en `startClip` de la **siguiente**.
4. **Íconos con significado** (`ui/icons/SignAiIcons.kt`): Personaje = `Person` (antes
   `Sparkle`), Velocidad = `Speedometer` (antes `SpeakerHigh`), Negrita = `TextB`,
   Contraste = `CircleHalf`, Texto más grande = `TextHOne`, Reducir animaciones = `FilmStrip`,
   Cámara libre = `Cursor`, Suelo = `GridFour`, Fondo = `Tree`, Seña de prueba = `PlayCircle`,
   Seña de ejemplo = `Sparkle`. Antes AVANZADO repetía `Gear` en las cinco filas y
   ACCESIBILIDAD repetía `TextAa`.
5. **Etiquetas de una línea**: «Cámara libre (orbitar + zoom)» → «Cámara libre», «Seña de
   prueba si falta» → «Seña de prueba». «Seña de ejemplo» pasa de ser un botón suelto debajo
   de la sección a una fila más, con `stackBelow = 300.dp`.
6. **Selector de seña con el peso de Personaje** (`ui/components/SignPickerDropdown.kt`): era
   un `Button(PrimaryOutlined)` de ancho completo y azul —el elemento más pesado del panel
   para un ajuste de desarrollo—; ahora usa el mismo borde, chevron y tipografía que el
   selector de Personaje, con `SignAiSizes.signTriggerWidth = 150.dp` (nuevo token).
7. **AVANZADO solo en desarrollo**: `debug` deja de ser una preferencia. Se borra
   `Setting.Debug` (`domain/Settings.kt`), `SettingsRepository` ya no lee `KEY_DEBUG` y
   `save()` **borra la clave vieja**; `statusLine`, `showStatus`, el `setDebug` del WebView y
   la sección AVANZADO pasan a mirar `BuildConfig.DEV_TOOLS` (`ui/state/MainScreenState.kt`,
   `SettingsSidebar.kt`). El flag lo define `app/build.gradle.kts`: `-Psignai.devTools=true`
   o `SIGNAI_DEV_TOOLS=1`, y **por defecto está apagado incluso en `debug`**; en
   `release`/`demo` siempre es `false`. Con el flag apagado no se compone la sección, así que
   no hay forma de llegar a esos ajustes desde el teléfono, ni con la preferencia vieja
   guardada.

Después del commit, el teléfono quedó con el build **sin** el flag: `./gradlew installDebug`
a secas. La pantalla principal ya no muestra el texto verde de diagnóstico y Ajustes termina
en «Acerca de».

## Qué se decidió

- **Paso 10 y valor inicial 50**: lo eligió el owner. De 10 a 100 en nueve toques.
- **Nombres del flag (`signai.devTools` / `SIGNAI_DEV_TOOLS`) y apagado por defecto**: lo
  eligió el owner. Apagado por defecto para que una compilación normal —aunque sea `debug`—
  no entregue la pantalla de diagnóstico.
- **Íconos de la lista de arriba**: aprobados por el owner antes de aplicarlos.
- **Nada de TalkBack**: el owner pidió quitar lo que se había agregado en esta sesión para el
  lector de pantalla (el `stateDescription` con el valor en los botones del stepper, el
  «Valor actual: N» del número, el rótulo extra del selector de seña y los comentarios que los
  explicaban). Se revirtió todo eso: `SignAiSmallIconButton` vuelve a tener solo
  `contentDescription`, el número del stepper vuelve a limpiar su semántica y el selector no
  lleva descripción propia. **No volver a agregarlo sin que el owner lo pida.** La única
  semántica que queda es la que ya estaba en el repo antes de esta sesión.
- **El modo debug se apaga, no se borra**: pedido explícito del owner. El código del overlay y
  del bloque AVANZADO sigue en el repo, detrás de `DEV_TOOLS`.

## Qué se midió

Todo en el **Redmi Note 9S físico (MIUI, Android 11)**, build `debug`, 2026-11-03, sin backend
levantado (Ajustes no lo necesita).

- **Grilla**: con la velocidad guardada en **52** (valor que dejó el slider), el primer toque
  en «−» la deja en **50** y en «+» en **60**.
- **Persistencia**: el valor elegido sobrevive al cierre y reapertura de la app.
- **Cambio diferido**: con una seña en curso y la velocidad en 0,4, esa seña terminó en
  **5,82 s** sin cortarse; la siguiente ya salió a 0,8.
- **Anchos** (con `wm density`, el activity está fijo en retrato): **392 dp** de pantalla
  (panel de 345 dp) las filas quedan en una línea; **308 dp** (panel de 271 dp) se apilan las
  filas con control ancho, como ya estaba. «Seña de ejemplo» queda en una línea con el
  selector de 150 dp.
- **Tema claro y oscuro**: las dos vistas del panel, sin cambios de contraste.
- **Build de producción**: con `demo` y `-Psignai.devTools=true` no aparece la sección
  AVANZADO ni la consola de la escena (el flag solo enciende en `debug`).
- **Preferencia vieja**: con `debug=true` guardado en `SharedPreferences`, el build sin flag
  no muestra nada de depuración.
- `./gradlew build` limpio.

## Segunda parte (misma fecha): el spec y el SRS

El owner pidió cerrar los dos pendientes de arriba en la misma sesión, en ese orden: primero
poner al día el spec contra el código de `98fde03`, y después proponer en el SRS los dos
cambios de redacción que faltaban.

### Qué se hizo

1. **`openspec/specs/ui/spec.md`** (commit `e83c87e`):
   - `SET-4`: la velocidad son **dos botones − / +** con el valor visible al medio, paso 10 y
     rango 10–100; un valor viejo fuera de la grilla se alinea al primer toque y el cambio
     entra en la **seña siguiente** sin cortar la actual.
   - `SET-8`: AVANZADO deja de ser preferencia y pasa a existir solo con
     `BuildConfig.DEV_TOOLS` (`-Psignai.devTools=true` o `SIGNAI_DEV_TOOLS=1`, apagado por
     defecto); la clave vieja `debug` se ignora y se borra.
   - `SET-9`: cada fila con su ícono, etiquetas de una línea, y el apilado suma la fila de
     velocidad (116 dp) y la de seña de ejemplo (`stackBelow = 300.dp`).
   - `BRG-6`: `setSpeed` ya no se describe como «slider `SET-4`» y se documenta que no corta
     la seña en curso.
   - Tabla de íconos: se reescribió contra `SignAiIcons` (cada fila con su Phosphor real) y se
     dejó anotado que `Gear`, `SlidersHorizontal`, `SpeakerHigh`, `Clock` y `Play` quedaron
     definidos pero sin uso.
   - `ACC-1` no necesitó cambios: ya describía el stepper de botones.
   - `./gradlew build` limpio.
2. **SRS en Overleaf** (`main.tex`, proyecto `6a122a19bb9a5dd9d390ba3d`), con control de
   cambios (`track_changes_on_for_me: true`; las dos ediciones volvieron con `tracked: true`,
   versiones 639 → 640 → 641):
   - **Velocidad de señas** (tabla `tab:ajustes`, ~línea 874): «Control deslizante, de 10 a
     100…» → «Dos botones de menos y más, con el valor visible entre ellos, que ajustan la
     velocidad de reproducción de las señas de 10 a 100 en pasos de 10.»
   - **Avanzado** (misma tabla, ~línea 894): ahora dice que son opciones del equipo de
     desarrollo que **no forman parte de la interfaz que se entrega**, que se habilitan al
     compilar con una variable de entorno y no aparecen en la versión final; se quitó «modo de
     depuración» de la lista, porque esa opción ya no existe como fila.
   - El rango 10–100 no se tocó. Compila con **0 errores** (93 advertencias, sin comparar
     contra una línea base). No se aceptó ni rechazó ningún cambio ni se tocaron comentarios.

### Qué se decidió

- **Extender esta sesión y no abrir otra**: el skill `sesion-openspec` pide un archivo por
  sesión y, si ya hay uno del día, extenderlo.
- **Dos cambios y solo dos en el SRS**: el owner los acotó. Quedaron por fuera §3.2–3.6, el
  rótulo de la seña en curso y las expresiones faciales, que ya estaban anotados como
  pendientes en otras sesiones.

## Qué quedó pendiente

- **`demo` y `debug` comparten `applicationId`**, así que después de probar `demo` hay que
  volver a `./gradlew installDebug`.
