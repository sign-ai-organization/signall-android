package com.signai.webview.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.ArrowCounterClockwise
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.CaretDown
import com.phosphor.icons.regular.CaretRight
import com.phosphor.icons.regular.Check
import com.phosphor.icons.regular.CircleHalf
import com.phosphor.icons.regular.Clock
import com.phosphor.icons.regular.ClockCounterClockwise
import com.phosphor.icons.regular.Cursor
import com.phosphor.icons.regular.FilmStrip
import com.phosphor.icons.regular.Gear
import com.phosphor.icons.regular.GridFour
import com.phosphor.icons.regular.Info
import com.phosphor.icons.regular.Microphone
import com.phosphor.icons.regular.Minus
import com.phosphor.icons.regular.Moon
import com.phosphor.icons.regular.PaperPlaneTilt
import com.phosphor.icons.regular.Person
import com.phosphor.icons.regular.Play
import com.phosphor.icons.regular.PlayCircle
import com.phosphor.icons.regular.Plus
import com.phosphor.icons.regular.SlidersHorizontal
import com.phosphor.icons.regular.Sparkle
import com.phosphor.icons.regular.SpeakerHigh
import com.phosphor.icons.regular.Speedometer
import com.phosphor.icons.regular.Sun
import com.phosphor.icons.regular.TextAa
import com.phosphor.icons.regular.TextB
import com.phosphor.icons.regular.TextHOne
import com.phosphor.icons.regular.Tree
import com.phosphor.icons.regular.Waveform
import com.phosphor.icons.regular.X

// Phosphor Regular icon set used by SignAI, named after the prototype's lucide
// icons (PrototipoFrontend/app/page.tsx). All verified present in
// phosphoricons-regular-android:1.0.5.
object SignAiIcons {
    // Existing (kept for backwards compatibility with call sites).
    val Mic: ImageVector = PhIcons.Regular.Microphone
    val Send: ImageVector = PhIcons.Regular.PaperPlaneTilt
    val Gear: ImageVector = PhIcons.Regular.Gear
    val History: ImageVector = PhIcons.Regular.ClockCounterClockwise

    val ArrowLeft: ImageVector = PhIcons.Regular.ArrowLeft
    val Check: ImageVector = PhIcons.Regular.Check
    val CaretDown: ImageVector = PhIcons.Regular.CaretDown
    val CaretRight: ImageVector = PhIcons.Regular.CaretRight
    val Clock: ImageVector = PhIcons.Regular.Clock
    val ClockCounterClockwise: ImageVector = PhIcons.Regular.ClockCounterClockwise
    val CircleHalf: ImageVector = PhIcons.Regular.CircleHalf

    // Filas de Ajustes (owner, 2026-11-03): cada icono dice qué hace la fila.
    // Antes AVANZADO repetía `Gear` en las cinco y ACCESIBILIDAD repetía `TextAa`.
    val Person: ImageVector = PhIcons.Regular.Person
    val Speedometer: ImageVector = PhIcons.Regular.Speedometer
    val TextB: ImageVector = PhIcons.Regular.TextB
    val TextHOne: ImageVector = PhIcons.Regular.TextHOne
    val FilmStrip: ImageVector = PhIcons.Regular.FilmStrip
    val Cursor: ImageVector = PhIcons.Regular.Cursor
    val GridFour: ImageVector = PhIcons.Regular.GridFour
    val Tree: ImageVector = PhIcons.Regular.Tree
    val PlayCircle: ImageVector = PhIcons.Regular.PlayCircle
    val Info: ImageVector = PhIcons.Regular.Info
    // El TR de tiempo real. Onda y no micrófono, a propósito: los dos botones
    // de la barra toman voz, y dos micrófonos en la misma barra eran
    // indistinguibles. La onda dice "esto está sonando ahora" — que es
    // justamente lo que pasa en tiempo real — y el micrófono queda como
    // ancla de "hablá acá" para el dictado.
    val Waveform: ImageVector = PhIcons.Regular.Waveform
    val Minus: ImageVector = PhIcons.Regular.Minus
    val Microphone: ImageVector = PhIcons.Regular.Microphone
    val Moon: ImageVector = PhIcons.Regular.Moon
    val Play: ImageVector = PhIcons.Regular.Play
    val Plus: ImageVector = PhIcons.Regular.Plus
    val Repeat: ImageVector = PhIcons.Regular.ArrowCounterClockwise
    val PaperPlaneTilt: ImageVector = PhIcons.Regular.PaperPlaneTilt
    val SlidersHorizontal: ImageVector = PhIcons.Regular.SlidersHorizontal
    val Sparkle: ImageVector = PhIcons.Regular.Sparkle
    val Sun: ImageVector = PhIcons.Regular.Sun
    val TextAa: ImageVector = PhIcons.Regular.TextAa
    val SpeakerHigh: ImageVector = PhIcons.Regular.SpeakerHigh
    val X: ImageVector = PhIcons.Regular.X
}
