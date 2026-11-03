# Semana 13 — orden de trabajo

Esta es la sesión maestra: **ordena y registra, no implementa**. Cada tarjeta se trabaja en
su propia sesión, y acá vuelve solo el resultado.

Reglas de la semana:

- Se hace **una tarjeta por sesión**, en el orden de la tabla.
- No se abre la siguiente hasta que la anterior esté cerrada: compilada, instalada si hay
  device conectado, y contada acá abajo.
- Si una tarjeta se traba por una decisión del owner, se para y se anota. No se reordena
  sola ni se salta a otra.

## Tabla

| Orden | Tarjeta | Qué se hace | Dónde | Costo | Estado |
|---|---|---|---|---|---|
| 1 | 8a · Datos de prueba | Grabar AU-01–AU-04, definir CT-02 y conseguir las personas para la validación en LSC. Va primero porque es lo que más tarda en volver y no depende de nada del código. | `../sign-ai-docs/borradores/plan-pruebas/02-datos/` | Tarda semanas | Pendiente |
| 2 | 2 · Rebote de los paneles | Quitar el rebote al abrir y al soltar el arrastre de los dos drawers. | `ui/designsystem/components/EdgeDrawer.kt` | Bajo | Pendiente |
| 3 | 3 · Velocidad con número | Mostrar el valor real 10–100 y pasar de slider sin números a pasos o presets. Ya lo pide `RF-AVT-08`. | `ui/screens/settings/SettingsSidebar.kt` | Bajo | Pendiente |
| 4 | 4 · AVANZADO en release | Decidir si el bloque AVANZADO se oculta en release y alinear el SRS con esa decisión (contraste A11). | `SettingsSidebar.kt` + SRS | Bajo, pero es decisión | Pendiente (decisión del owner) |
| 5 | 5 · Conversación | Pasar la lista «TÚ / TRADUCCIÓN» a burbujas de chat. | `ui/screens/conversation/ConversationPanel.kt` | Medio | Pendiente |
| 6 | 1 · Requerimientos de Julián | Bajar el artifact al SRS §3.2–3.6 y cerrar los 24 TODO del consolidado v0.16. | artifact + `../sign-ai-docs` + Overleaf | Medio | Pendiente (falta el texto del artifact) |
| 7 | 7 · Instrumentación | Llenar §7.3 del plan de pruebas: qué se mide, en qué repo y con qué tag o endpoint. | `../sign-ai-docs` | Medio | Pendiente |
| 8 | 6 · Marcadores faciales | Medir si las variantes `_alegria`/`_tristeza`/`_sorpresa`/`_pregunta`/`_negacion` mueven la cara en `juan.glb` y `pauline.glb`, decidir el mapeo A/B/C y hacer el trabajo en Cascadeur. | `../signai-avatar` + app | Alto | Pendiente (medición + decisión) |
| 9 | 8b · Correr las pruebas | Ejecutar las 74 pruebas del plan y la validación con personas. Depende del orden 1 y del orden 7. | `../sign-ai-docs` | Alto | Pendiente |

## Bitácora

Una entrada por sesión que vuelve, con fecha, tarjeta, qué quedó hecho y qué quedó abierto.
Sin prosa: renglones.

| Fecha | Tarjeta | Resultado | Quedó abierto |
|---|---|---|---|
| | | | |
