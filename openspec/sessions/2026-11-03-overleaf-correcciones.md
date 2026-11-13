# Correcciones de Overleaf que no dependían de una decisión abierta

**Fecha:** 2026-11-03, madrugada · **Rama:** `main`

## Qué llegó

El 2026-11-02 se revisaron los siete documentos de Overleaf contra el código y se dejó una
tabla de cambios aprobada, salvo la decisión del **rótulo de la seña en curso** (`RF-UI-11`
con ficha completa, o solo descripción). El owner pidió hacer **solo lo que ya se podía
hacer** sin esa decisión ni las demás que quedaron abiertas, y entregar dos tablas: lo
hecho y lo pendiente.

## Qué se hizo

Tres proyectos de Overleaf, todas las ediciones como **sugerencias con control de cambios**
(`tracked: true`), y los tres compilan limpio después (0 errores).

**SRS** (`6a122a19bb9a5dd9d390ba3d`, 3 cambios): la única aparición de `SignAI` (línea 1708,
§Suposiciones y Dependencias) pasa a `SignAll`; se quita el `TODO` vencido de `RF-UI-06`
(línea 2054), porque la app **ya** impide cambiar de modo mientras graba
(`SignAiBottomBar.kt:141-142`: `micEnabled` cae si `liveActive` y `liveEnabled` cae si
`dictationActive`).

**SDD** (`6a85c9a632974414503d9b82`, 26 cambios): las 9 apariciones de `SignAI` pasan a
`SignAll` (41, 42, 112, 177, 180, 189, 292, 450, 461); «los 50 archivos GLB» pasa a **«los
dos archivos GLB —uno por avatar—», cada uno con las animaciones de las 50 señas** (línea
202), que es lo que hay desde el 2026-10-26 (`models/juan.glb`, `models/pauline.glb`, 300
clips cada uno); se borra la nota interna cruda de la línea 217 («Pues ya dijimos q queda
así…»); y se borran los cuatro restos de la plantilla: el bloque de instrucciones y el
`[PENDIENTE DE COMPLETAR]` de la portada, el de la Lista de Tablas y el de Referencias. Las
definiciones `\instrucciones` y `\pendiente` se quedan: quedaron sin uso y no molestan.

**Plan de pruebas** (`6add1e0e35efb959c10cd0fb`, 46 cambios): `PR-AND-04` deja de decir
«tres ajustes: la velocidad del avatar, la cámara y el tema». Ahora prueba **los ajustes con
requisito** (velocidad `RF-AVT-08` y tema `RF-UI-10`), remite a `PR-AVT-07` (avatar) y
`PR-AND-06` (accesibilidad), y aclara que los controles de depuración del panel Avanzado,
entre ellos la cámara, no tienen requisito, que es lo que la propia ficha ya decía en
Trazabilidad. `PR-VAL-03` deja de pedir que se retire y se agregue «la animación»: la
animación va **dentro del archivo del avatar** y no depende del catálogo, así que la prueba
mueve solo la fila del catálogo y se deja dicho que una seña no empaquetada exige regenerar
el GLB. Se ajustaron, por lo mismo, el conjunto `CT-01` en §Datos de Prueba y los campos
Datos de entrada, Procedimiento y Evidencia de la ficha.

**Repo:** `docs/SISTEMA.md` pasa a `cambios/requerimientos.md` y la tabla de proyectos de
Overleaf se completa (faltaban `plan-pruebas` y `SPMP`; la cuenta tiene **7 proyectos
activos y 5 en la papelera**, medido con `list_projects`). La ruta vieja `modificaciones/`
se corrigió en `AGENTS.md`, en los skills `overleaf-lectura` y `tres-fuentes-de-verdad`, en
`agents/contraste.md` y en `docs/historia/CONTRASTE-APPvsDOCUMENTOS.md`; en
`docs/PLAN-AVATARES-Y-MARCADORES.md` se dejó, porque cita un commit y es historia. En
`../signall-docs/cambios/requerimientos.md` se marcó `RF-UI-06` como hecho (sección 2,
pendiente 6.2 y choque 6.3).

## Qué se decidió

- **No se tocó el rótulo**: ni `RF-UI-11` ni la descripción en §2.1.2 ni la fila
  *Reproduciendo*, porque la decisión sigue abierta y es del owner.
- **No se tocaron** las expresiones faciales (líneas 268–269, 743 y 1700–1702 del SRS), el
  identificador de turno (2387–2389), la portada ni el historial del SRS, y las secciones
  3.2–3.6 siguen vacías: quedan para otra sesión desde
  `../signall-docs/borradores/requerimientos/SignAI_Requerimientos_Consolidados_v0.16.md`.
- **`PR-VAL-03` se corrige, no se borra**: lo que se prueba (el catálogo se cambia sin tocar
  código del backend, `RNF-MNT-01`) sigue siendo cierto; lo que estaba mal era el paso de
  retirar la animación, que ya no existe como archivo por seña.

## Qué se midió

- Compilación de los tres proyectos después de editar: **0 errores** en los tres
  (`built_cleanly: true`). El plan de pruebas reporta 111 advertencias de LaTeX, que ya
  estaban.
- Cambios registrados: SRS 3, SDD 26, plan 46 (el conteo es por operación de texto, no por
  edición lógica; todos con marca de tiempo del 2026-11-03 y autor `signai.tesis@gmail.com`).

## Qué quedó pendiente

- **Owner:** decidir si el rótulo entra como `RF-UI-11` (ficha completa, trazabilidad y
  prueba) o solo como descripción en §2.1.2 y en la fila *Reproduciendo*.
- **Owner:** aceptar o rechazar las sugerencias en el panel de revisión de cada documento.
- **Otra sesión:** completar §3.2–3.6 del SRS con los 56 requerimientos de la v0.16.
- **Otra sesión:** investigar el identificador de turno (SRS 2387–2389 contra
  `SignAiWebSocketClient.sendText`, que no lo manda).
- **Otra sesión:** expresiones faciales (el SRS las afirma y la app solo reproduce el clip
  base) y las secciones del SRS que las mencionan.
- **`../signall-docs`:** el cambio de `cambios/requerimientos.md` quedó **sin commitear**,
  como corresponde a un repo hermano; lo manda el owner.
- **`../signall-docs/cambios/requerimientos.md`:** `RF-CAT-01` sigue pidiendo acordar el
  formato de la clave de animación, que en el código ya es `signs/<id>.glb`
  (`SignsCatalog.kt`); falta que el owner lo dé por acordado.
