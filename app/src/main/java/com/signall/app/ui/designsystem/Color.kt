package com.signall.app.ui.designsystem

// CUSTOMIZED — originally Lumo-generated (`lumo --setup`), now overridden with
// the Vercel prototype palette (see `LightColors`/`DarkColors` below). The Lumo
// default names (Gray*, Blue*, Red*, Green*) are kept for the remaining tokens
// (error/success) but the semantic palette is the prototype's.
// Do NOT re-run `lumo --setup` (it would overwrite these changes).

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Black: Color = Color(0xFF000000)
val Gray900: Color = Color(0xFF282828)
val Gray800: Color = Color(0xFF4b4b4b)
val Gray700: Color = Color(0xFF5e5e5e)
val Gray600: Color = Color(0xFF727272)
val Gray500: Color = Color(0xFF868686)
val Gray400: Color = Color(0xFFC7C7C7)
val Gray300: Color = Color(0xFFDFDFDF)
val Gray200: Color = Color(0xFFE2E2E2)
val Gray100: Color = Color(0xFFF7F7F7)
val Gray50: Color = Color(0xFFFFFFFF)
val White: Color = Color(0xFFFFFFFF)

val Red900: Color = Color(0xFF520810)
val Red800: Color = Color(0xFF950f22)
val Red700: Color = Color(0xFFbb032a)
val Red600: Color = Color(0xFFde1135)
val Red500: Color = Color(0xFFf83446)
val Red400: Color = Color(0xFFfc7f79)
val Red300: Color = Color(0xFFffb2ab)
val Red200: Color = Color(0xFFffd2cd)
val Red100: Color = Color(0xFFffe1de)
val Red50: Color = Color(0xFFfff0ee)

val Blue900: Color = Color(0xFF276EF1)
val Blue800: Color = Color(0xFF3F7EF2)
val Blue700: Color = Color(0xFF578EF4)
val Blue600: Color = Color(0xFF6F9EF5)
val Blue500: Color = Color(0xFF87AEF7)
val Blue400: Color = Color(0xFF9FBFF8)
val Blue300: Color = Color(0xFFB7CEFA)
val Blue200: Color = Color(0xFFCFDEFB)
val Blue100: Color = Color(0xFFE7EEFD)
val Blue50: Color = Color(0xFFFFFFFF)

val Green950: Color = Color(0xFF0B4627)
val Green900: Color = Color(0xFF16643B)
val Green800: Color = Color(0xFF1A7544)
val Green700: Color = Color(0xFF178C4E)
val Green600: Color = Color(0xFF1DAF61)
val Green500: Color = Color(0xFF1FC16B)
val Green400: Color = Color(0xFF3EE089)
val Green300: Color = Color(0xFF84EBB4)
val Green200: Color = Color(0xFFC2F5DA)
val Green100: Color = Color(0xFFD0FBE9)
val Green50: Color = Color(0xFFE0FAEC)

@Immutable
data class Colors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val error: Color,
    val onError: Color,
    val success: Color,
    val onSuccess: Color,
    val disabled: Color,
    val onDisabled: Color,
    val surface: Color,
    val onSurface: Color,
    val background: Color,
    val onBackground: Color,
    val outline: Color,
    val transparent: Color = Color.Transparent,
    val white: Color = White,
    val black: Color = Black,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val scrim: Color,
    val elevation: Color,
    // Prototype-aligned tokens (PrototipoFrontend/app/globals.css):
    // `strong` is the high-contrast foreground used for filled CTAs, the live
    // dot and active states; `onStrong` is its readable content color.
    // `soft` is the muted surface used for icon buttons, glows and tiles.
    val strong: Color,
    val onStrong: Color,
    val soft: Color,
    // `live` marks "the app is capturing right now" (mic on, TR on). It is the one
    // warm color in the palette, so the brand blue stays reserved for the main
    // action and the controls instead of painting every active state.
    val live: Color,
    val onLive: Color,
) {
    // The prototype exposes `muted` separately from any Lumo token; map it to
    // the existing `textSecondary` so there is a single source of truth.
    val muted: Color get() = textSecondary
}

// Light still mirrors the Vercel prototype exactly (PrototipoFrontend/app/globals.css
// `:root`), which is the visual source of truth for it, so the Lumo defaults stay
// overridden here:
//   light  — background #f4f4f2, foreground #171717, muted #777773,
//            line #deded9, surface rgba(255,255,253,.92), soft #e8e8e3
// Exception (2026-10-16): the accent (`strong`/`primary`/`tertiary`) is the brand blue
// #1c4cfc, as in dark, so both modes read as the same brand (5.47:1 on the
// background, white on it 6.02:1). Everything else stays the prototype's.
internal val LightColors =
    Colors(
        primary = Color(0xFF1C4CFC),
        onPrimary = White,
        secondary = Color(0xFFDEDED9),
        onSecondary = Color(0xFF171717),
        tertiary = Color(0xFF1C4CFC),
        onTertiary = White,
        // Prototype `--surface: rgba(255,255,253,.92)` is translucent because it
        // sits over a `backdrop-filter: blur(14px)` panel. Compose has no cheap
        // region blur, so we use the opaque composite over `background`
        // (#f4f4f2) → #fefefc, which is what the prototype looks like with the
        // avatar clipped above the panel.
        surface = Color(0xFFFEFEFC),
        onSurface = Color(0xFF171717),
        error = Red600,
        onError = White,
        success = Green600,
        onSuccess = White,
        disabled = Color(0xFFE8E8E3),
        onDisabled = Color(0xFF777773),
        background = Color(0xFFF4F4F2),
        onBackground = Color(0xFF171717),
        outline = Color(0xFFDEDED9),
        transparent = Color.Transparent,
        white = White,
        black = Black,
        text = Color(0xFF171717),
        textSecondary = Color(0xFF777773),
        textDisabled = Color(0xFFB9B9B4),
        scrim = Black.copy(alpha = 0.28f),
        elevation = Black,
        strong = Color(0xFF1C4CFC),
        onStrong = White,
        soft = Color(0xFFE8E8E3),
        live = Color(0xFFF2B84B),
        onLive = Color(0xFF1C1503),
    )

// Dark: graphite surfaces, brand blue only on the main action, amber for "live".
// The 2026-10-16 rebrand first made every surface navy (#0c142c) and the team found
// the app "too blue". The blue now lives only in `strong`/`primary` (Traducir,
// switches, slider, cursor, splash bar); backgrounds are near-neutral graphite so
// the avatar and the content carry the screen.
//
//   token      value      vs background
//   background  #121316        —          graphite, barely cool so it sits with the navy logo ink
//   surface     #1a1b1f      1.08:1
//   soft        #25272c      1.24:1
//   outline     #33363d      1.54:1
//   text        #f5f4f0     16.88:1       warm white
//   muted       #a5a7ae      7.73:1
//   strong      #1c4cfc      3.09:1       white on it is 6.02:1, so `onStrong = white`
//   live        #f2b84b      10.38:1       dark `onLive` #1c1503 on it is 10.13:1
//
// The accent as a *thin mark* on a dark surface is only ~2.3-2.8:1, so icons and
// hairlines read better as `text` (that is why the character picker's checkmark is
// white and not `strong`).
internal val DarkColors =
    Colors(
        primary = Color(0xFF1C4CFC),
        onPrimary = White,
        secondary = Color(0xFF25272C),
        onSecondary = Color(0xFFF5F4F0),
        tertiary = Color(0xFF1C4CFC),
        onTertiary = White,
        surface = Color(0xFF1A1B1F),
        onSurface = Color(0xFFF5F4F0),
        error = Red400,
        onError = Black,
        success = Green700,
        onSuccess = Black,
        disabled = Color(0xFF1E2024),
        onDisabled = Color(0xFF8A8D95),
        background = Color(0xFF121316),
        onBackground = Color(0xFFF5F4F0),
        outline = Color(0xFF33363D),
        transparent = Color.Transparent,
        white = White,
        black = Black,
        text = Color(0xFFF5F4F0),
        textSecondary = Color(0xFFA5A7AE),
        textDisabled = Color(0xFF5E6169),
        scrim = Black.copy(alpha = 0.40f),
        elevation = Black,
        strong = Color(0xFF1C4CFC),
        onStrong = White,
        soft = Color(0xFF25272C),
        live = Color(0xFFF2B84B),
        onLive = Color(0xFF1C1503),
    )

val LocalColors = staticCompositionLocalOf { LightColors }
val LocalContentColor = compositionLocalOf { Color.Black }
val LocalContentAlpha = compositionLocalOf { 1f }

fun Colors.contentColorFor(backgroundColor: Color): Color {
    return when (backgroundColor) {
        primary -> onPrimary
        secondary -> onSecondary
        tertiary -> onTertiary
        surface -> onSurface
        error -> onError
        success -> onSuccess
        disabled -> onDisabled
        background -> onBackground
        strong -> onStrong
        else -> Color.Unspecified
    }
}
