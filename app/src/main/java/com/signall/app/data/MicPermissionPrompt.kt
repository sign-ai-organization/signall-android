package com.signall.app.data

import android.content.Context

/**
 * Si a **esta** instalación ya se le preguntó al usuario por el micrófono.
 *
 * La pregunta sale al abrir la app y sale una sola vez (`RF-UI-04`, SRS
 * `main.tex:837`), así que hace falta acordarse de que ya se preguntó. Y para
 * acordarse no sirve preguntarle al sistema: `shouldShowRequestPermissionRationale()`
 * devuelve `false` tanto si nunca se preguntó como si ya no se va a preguntar
 * nunca, que son dos cosas distintas. En MIUI —el teléfono donde se prueba— una
 * sola negativa deja el permiso en `USER_FIXED`: el diálogo no vuelve a aparecer
 * y el sistema responde `false` en el acto, así que con esa señal la app nunca
 * más volvía a avisar que el micrófono quedó bloqueado, que es justo lo que
 * `RF-UI-04` pide (`MainScreen.kt`, aviso de `onMicPermissionDenied`).
 *
 * Vive en el mismo archivo de preferencias que los ajustes y el historial,
 * con otra clave: no es un ajuste que el usuario pueda cambiar.
 */
class MicPermissionPrompt(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** `true` en cuanto se lanza la solicitud, granted o no. */
    var alreadyAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ASKED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "signall"
        const val KEY_ASKED = "mic_prompted"
    }
}
