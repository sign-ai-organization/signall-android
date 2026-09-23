package com.signai.webview.ui.designsystem.foundation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween as coreTween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

// SignAI motion tokens. Centralizes durations and easings so animations stay
// consistent across the app and can be disabled in one place when the user
// reduces motion (ACC-5 / ACC-6, ANI-7).
//
// Durations mirror the prototype (PrototipoFrontend/app/globals.css):
//   - fast   160 ms → dropdown-in
//   - short  180 ms → press feedback, switch
//   - medium 250 ms → rise del sheet, slide del drawer, cambio de tema
//   - slow   500 ms → fade de contenido
object SignAiMotion {
    const val Fast = 160
    const val Short = 180
    const val Medium = 250
    const val Slow = 500

    // Prototype animations use `ease-out`; LinearOutSlowInEasing is Compose's
    // closest match. FastOutSlowInEasing is used for symmetric in/out motion.
    val EaseOut: Easing = LinearOutSlowInEasing
    val EaseInOut: Easing = FastOutSlowInEasing

    /**
     * Tween that collapses to zero duration when reduce-motion is active.
     * Use this instead of calling `tween(...)` directly so ACC-5/ANI-7 hold.
     */
    @Composable
    fun <T> motionTween(
        durationMillis: Int = Medium,
        easing: Easing = EaseOut,
    ) = coreTween<T>(
        durationMillis = if (LocalReduceMotion.current) 0 else durationMillis,
        easing = easing,
    )
}

/**
 * When true, animations should be reduced to the minimum (theme transitions
 * collapse to instant, loops stop). Fed from the user setting and, eventually,
 * the system's reduced-motion preference.
 */
val LocalReduceMotion = compositionLocalOf { false }
