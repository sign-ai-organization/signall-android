package com.signai.webview.ui

/**
 * Single source of truth for the app wordmark (spec `APP-6` / `BRD-1`).
 *
 * Branding is intentionally open: the prototype is called "Seña" but the app
 * still uses "SignAI". Change [Name] here only and every logo tile, eyebrow and
 * title follows — do not scatter literals across the UI.
 */
object SignAiBranding {
    const val Name = "SignAI"

    val LogoLetter: String = Name.take(1)
    val Eyebrow: String = Name.uppercase()
}
