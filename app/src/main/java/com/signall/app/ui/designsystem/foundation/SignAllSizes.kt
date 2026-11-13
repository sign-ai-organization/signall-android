package com.signall.app.ui.designsystem.foundation

import androidx.compose.ui.unit.dp

// SignAll size tokens. Centralizes the dp values used across screens/components
// so they can be tuned in one place.
//
// Prototype-aligned values (PrototipoFrontend/app/globals.css) are added as new
// tokens; the older ones are kept untouched so the current UI keeps parity.
object SignAllSizes {
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

    /**
     * Ancho del selector de Seña, más angosto que el de Personaje.
     *
     * Sus opciones son «Seña 0»..«Seña 49», no un nombre completo, y con 167 dp
     * la etiqueta «Seña de ejemplo» se partía en dos líneas en el panel de 345 dp
     * (medido en el Redmi, 2026-11-03). 150 dp le dejan el ancho que necesita.
     */
    val signTriggerWidth = 150.dp

    /**
     * Ancho máximo de la burbuja de la frase en el panel de Conversación, como
     * fracción del ancho del panel (350 dp − 44 dp de padding = 306 dp, así que
     * 0,88 deja ~269 dp de texto y ~37 dp de aire del lado contrario).
     *
     * Es una fracción y no un dp fijo porque el panel mide `min(88 % de la
     * pantalla, 350 dp)`: en un teléfono angosto 269 dp no cabrían. Las frases
     * cortas no se estiran hasta el máximo — la burbuja mide lo que mide su
     * texto y solo se topa con este techo.
     */
    const val conversationBubbleMaxWidthFraction = 0.88f

    val settingRowMinHeight = 50.dp

    /**
     * Ancho de fila por debajo del cual una fila de Ajustes **se apila**: la
     * etiqueta arriba y el control abajo a la derecha (owner, 2026-11-03).
     *
     * El panel mide `min(88 % del ancho, 350 dp)`, así que en una pantalla de 320 dp
     * quedan ~237 dp de fila: el selector de Personaje (167 dp) más el ícono y el
     * espacio dejaban ~30 dp para la etiqueta y "Personaje" se partía en tres líneas.
     * 270 dp es el ancho en el que la etiqueta todavía conserva ~70 dp con el control
     * más ancho, o sea pantallas de ~360 dp para arriba quedan como estaban.
     */
    val settingRowStackBelow = 270.dp
    val historyItemMinHeight = 55.dp
    val switchWidth = 43.dp
    val switchHeight = 25.dp
}
