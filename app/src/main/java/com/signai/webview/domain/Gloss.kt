package com.signai.webview.domain

/**
 * Spanish → LSC gloss (spec `GLS-*`). Pure, hardcoded approximation ported from
 * the prototype's `glossFor`. This is an honest placeholder: the real gloss
 * engine is out of scope.
 */
object Gloss {
    fun glossFor(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("universidad") -> "YO UNIVERSIDAD IR QUERER"
            lower.contains("gracias") -> "GRACIAS MUCHO"
            lower.contains("ayuda") -> "YO AYUDA NECESITAR"
            lower.contains("cómo") -> "TÚ CÓMO ESTAR"
            else ->
                text.uppercase()
                    .replace(Regex("[¿?¡!]"), "")
                    .ifBlank { "ESPERANDO MENSAJE" }
        }
    }
}
