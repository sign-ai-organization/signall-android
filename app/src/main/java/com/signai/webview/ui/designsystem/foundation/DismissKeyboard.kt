package com.signai.webview.ui.designsystem.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Cierra el teclado **y le saca el foco al campo**.
 *
 * Solo pedirle al IME que se esconda no alcanza, y por eso el teclado volvía a
 * aparecer un segundo después de tocar un botón: el `TextField` seguía teniendo
 * el foco, así que la siguiente recomposición lo volvía a mostrar. Hay que
 * quitarle el foco primero —con `force = true`, porque un campo `readOnly` (el
 * micrófono prendido, o el texto que la voz ya mandó) ignora el cierre normal—
 * y recién después esconder el teclado.
 *
 * Va en `foundation` y no en la pantalla porque la regla es de toda la app:
 * **tocar un control cierra el teclado, no manda la frase.** El texto se queda
 * en el campo; lo que se cancela es el teclado y el estado de edición.
 */
@Composable
fun rememberKeyboardDismissAction(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return remember(keyboard, focusManager) {
        {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}
