package com.signai.webview.ui

import androidx.annotation.DrawableRes
import com.signai.webview.R

/**
 * Single source of truth for the app's brand (spec `APP-6` / `BRD-1`).
 *
 * The app is **SignALL** since the 2026-10-16 rebrand (it used to be "SignAI"; the
 * prototype still calls it "Seña"). Change [Name] here only — do not scatter
 * literals across the UI.
 *
 * The two rasters are generated, not hand-made: `tools/generar-marca.py` reads the
 * masters in `docs/marca/` (`icono-signall.png`, `logo-signall.png`) and writes the
 * drawables plus the launcher icon. Run it again if the masters change.
 */
object SignAiBranding {
    const val Name = "SignALL"

    val Eyebrow: String = Name.uppercase()

    /**
     * The icon tile: blue rounded square, white hand. Used in the settings drawer and
     * in Acerca de. It carries its own blue, so it looks the same in both themes —
     * which is the point of an icon.
     */
    @DrawableRes
    val Icon: Int = R.drawable.ic_signall

    /**
     * The wordmark. It comes in two variants because the "Sign" half is navy in the
     * master, and navy on the dark graphite background (#121316) is invisible: the dark
     * variant has that half in white. [darkTheme] has to be the *app's* theme, not the
     * system's — read it from `LocalIsDarkTheme`.
     */
    @DrawableRes
    fun logo(darkTheme: Boolean): Int =
        if (darkTheme) R.drawable.logo_signall_on_dark else R.drawable.logo_signall_on_light
}