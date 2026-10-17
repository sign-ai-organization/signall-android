# Los tokens de SignAI

## Color

**La fuente única es `ui/designsystem/Color.kt`**, en el bloque `// CUSTOMIZED`. No hay
otra. Un color nuevo se agrega ahí y se consume desde los dos modos.

Se llega al color con `colors` de `SignAiTheme` (que delega en Lumo y expone `Colors`).
**`SignAiColors` ya no existe.** Y `MaterialTheme.colorScheme` **no** se usa: no hay
Material 3 en el proyecto.

```kotlin
// correcto
Text(color = colors.text)
Surface(color = colors.surface, shape = SignAiShapes.card)

// mal
Text(color = Color(0xFFF5F4F0))
Text(color = MaterialTheme.colorScheme.onSurface)
```

### Dark = grafito

Es el **default** (`Settings.darkTheme = true`).

| Rol | Valor |
|---|---|
| `background` | `#121316` |
| `surface` | `#1A1B1F` |
| `soft` | `#25272C` |
| `outline` | `#33363D` |
| `text` | `#F5F4F0` |
| `muted` | `#A5A7AE` |
| `strong` / `primary` | `#1C4CFC` |
| `live` / `onLive` | `#F2B84B` / `#1C1503` |

### Light

| Rol | Valor |
|---|---|
| `background` | `#F4F4F2` |
| `text` | `#171717` |
| `surface` | `#FEFEFC` |
| `outline` | `#DEDED9` |
| `muted` | `#777773` |
| `soft` | `#E8E8E3` |
| `strong` / `primary` | `#1C4CFC` |

`surface` en claro es el compuesto opaco del `rgba(255,255,253,.92)` del prototipo: Compose
no tiene blur de región barato, así que se opaquiza.

### Contraste: lo que hay que saber de memoria

La tabla de ratios está en `Color.kt`. Lo que importa:

- `muted` da **7,73:1** sobre el fondo. Se puede usar para texto.
- `strong` da **3,09:1** sobre `background` pero **2,86:1** sobre `surface**: por debajo
  de 3:1. **Por eso los iconos y las líneas finas sobre oscuro usan `colors.text`, no
  `colors.strong`** — y por eso el check del selector de personaje es blanco.
- `outline` (1,54:1) y `soft` (1,24:1) **no** son texto: son bordes y superficies.

### Reglas de marca

- **El azul `#1C4CFC` vive solo en la acción principal y los controles.** Si aparece en
  otro lado, es un error.
- **`live` = ámbar = "escuchando ahora"**, y nada más. Nunca es decoración, y nunca es la
  única señal: el control cambia de etiqueta o de `stateDescription`.
- El canvas 3D sigue la paleta (`app.js`/`index.html`: fondo `#121316`, grilla
  `#25272c`), y el cambio de tema llega al JS por el comando `setTheme`.

## Foundation

`ui/designsystem/foundation/` tiene el resto:

| Token | Para qué |
|---|---|
| `SignAiSpacing` | Espaciado. Nada de `.dp` sueltos. |
| `SignAiSizes` | Tamaños de componentes e íconos. |
| `SignAiShapes` | Radios. |
| `SignAiMotion` | Duraciones, easings y `LocalReduceMotion`. |
| `pressScale` | La escala del feedback táctil. |

## Tipografía

`ui/designsystem/Typography.kt`. La familia es **Liberation Sans**, incluida en
`res/font/` porque Android no trae Arial ni Helvetica y Liberation es métricamente
compatible con Arial. Así el layout y el peso coinciden con el prototipo.

`SignAiTheme` aplica, sobre la base: `fontScale` y `uiScale` (este último escala
`LocalDensity`), `bold`, `highContrast` (que sobreescribe `outline`) y `reduceMotion`.

## Modo claro y MIUI

`android:forceDarkAllowed=false` en `res/values/themes.xml` **tiene que seguir ahí**. Con
force-dark prendido, MIUI invierte el contenido dibujado dentro de hardware layers: el
texto dentro de `verticalScroll` y de los drawers se ve blanco mientras las cajas sólidas
quedan bien.

Quirk conocido: en MIUI la apariencia de los iconos de la barra de estado no sigue al
tema claro (`isAppearanceLightStatusBars` y el flag legacy se ignoran).