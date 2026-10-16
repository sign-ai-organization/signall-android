package com.signai.webview.ui.designsystem.foundation

import androidx.compose.ui.unit.dp

// SignAI size tokens. Centralizes the dp values used across screens/components
// so they can be tuned in one place.
//
// Prototype-aligned values (PrototipoFrontend/app/globals.css) are added as new
// tokens; the older ones are kept untouched so the current UI keeps parity.
object SignAiSizes {
    val buttonHeight = 56.dp
    val fieldHeight = 44.dp
    val drawerWidth = 300.dp
    val dropdownMaxHeight = 280.dp
    val dropdownMinHeight = 48.dp

    // Control panel (prototype: input 50, action buttons 50)
    val inputHeight = 50.dp
    val actionButtonSize = 50.dp
    val actionButtonHeight = 50.dp

    // Side panels / bottom sheet (prototype: width min(88%, 350px))
    val panelMaxWidth = 350.dp
    const val panelWidthFraction = 0.88f

    // Misc prototype sizes
    val iconButtonSmall = 36.dp
    val repeatButtonSize = 46.dp
    val logoTileSize = 68.dp
    val settingsLogoSize = 40.dp
    val miniAvatarSize = 42.dp

    /**
     * Tamaño del dropdown de Personaje, en vez de "lo que sobra de la fila".
     *
     * Antes el ancho lo medía el espacio que dejaba la etiqueta de `SettingRow`, así
     * que el control cambiaba de ancho según el texto de al lado ("Personaje" contra
     * "Cámara libre (orbitar + zoom)"). El owner lo ajustó dos veces: primero −5%
     * (208 dp medidos → 196), después −15% más (196 → 167 dp de ancho y 42 → 36 dp de
     * alto, que además lo deja a la altura de los botones del stepper de ACCESIBILIDAD).
     */
    val characterTriggerWidth = 167.dp
    val characterTriggerHeight = 36.dp

    val settingRowMinHeight = 50.dp
    val historyItemMinHeight = 55.dp
    val switchWidth = 43.dp
    val switchHeight = 25.dp
}
