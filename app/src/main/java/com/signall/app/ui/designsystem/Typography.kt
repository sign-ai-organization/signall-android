package com.signall.app.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.signall.app.R

// The prototype renders with `Arial, Helvetica, sans-serif` (globals.css body).
// Android ships neither, so we bundle Liberation Sans, which is metrically
// compatible with Arial (same advance widths and glyph shapes), to keep the
// layout and text weight identical to the prototype.
private val SignAllFontFamily =
    FontFamily(
        Font(R.font.liberation_sans_regular, FontWeight.Normal),
        Font(R.font.liberation_sans_bold, FontWeight.Bold),
    )

@Composable
fun fontFamily() = SignAllFontFamily

data class Typography(
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val body1: TextStyle,
    val body2: TextStyle,
    val body3: TextStyle,
    val label1: TextStyle,
    val label2: TextStyle,
    val label3: TextStyle,
    val button: TextStyle,
    val input: TextStyle,
    // Prototype-aligned styles (PrototipoFrontend/app/globals.css).
    val eyebrow: TextStyle,
    val caption: TextStyle,
    val quote: TextStyle,
    val gloss: TextStyle,
    val label: TextStyle,
)

private val defaultTypography =
    Typography(
        h1 =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        h2 =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                letterSpacing = (-1.2).sp,
            ),
        h3 =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        h4 =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        body1 =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        body2 =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.15.sp,
            ),
        body3 =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.15.sp,
            ),
        label1 =
            TextStyle(
                fontWeight = FontWeight.W500,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        label2 =
            TextStyle(
                fontWeight = FontWeight.W500,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        label3 =
            TextStyle(
                fontWeight = FontWeight.W500,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.5.sp,
            ),
        button =
            TextStyle(
                fontWeight = FontWeight.W500,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 1.sp,
            ),
        input =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        // .eyebrow — 10px / 700 / .18em, muted color at call site.
        eyebrow =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.8.sp,
            ),
        // .stage-caption — 10px / .12em, uppercase at call site.
        caption =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.2.sp,
            ),
        // .quote — 20px / 1.25 / -.03em
        quote =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                letterSpacing = (-0.6).sp,
            ),
        // .gloss — 15px / 700 / .08em
        gloss =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = 1.2.sp,
            ),
        // .label — 10px / 700 / .16em, muted color at call site.
        label =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.6.sp,
            ),
    )

@Composable
fun provideTypography(
    scale: Float = 1f,
    bold: Boolean = false,
): Typography {
    val fontFamily = fontFamily()
    val base = defaultTypography.transform(scale = scale, bold = bold)

    return base.copy(
        h1 = base.h1.copy(fontFamily = fontFamily),
        h2 = base.h2.copy(fontFamily = fontFamily),
        h3 = base.h3.copy(fontFamily = fontFamily),
        h4 = base.h4.copy(fontFamily = fontFamily),
        body1 = base.body1.copy(fontFamily = fontFamily),
        body2 = base.body2.copy(fontFamily = fontFamily),
        body3 = base.body3.copy(fontFamily = fontFamily),
        label1 = base.label1.copy(fontFamily = fontFamily),
        label2 = base.label2.copy(fontFamily = fontFamily),
        label3 = base.label3.copy(fontFamily = fontFamily),
        button = base.button.copy(fontFamily = fontFamily),
        input = base.input.copy(fontFamily = fontFamily),
        eyebrow = base.eyebrow.copy(fontFamily = fontFamily),
        caption = base.caption.copy(fontFamily = fontFamily),
        quote = base.quote.copy(fontFamily = fontFamily),
        gloss = base.gloss.copy(fontFamily = fontFamily),
        label = base.label.copy(fontFamily = fontFamily),
    )
}

/** Accessibility transform: `large` scales sizes, `bold` bumps weights (`ACC-2`/`ACC-4`). */
private fun Typography.transform(scale: Float, bold: Boolean): Typography {
    fun TextStyle.applyAccessibility(): TextStyle {
        var style = this
        if (scale != 1f) {
            style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
        }
        if (bold) {
            style = style.copy(fontWeight = style.fontWeight.boldened())
        }
        return style
    }
    return copy(
        h1 = h1.applyAccessibility(),
        h2 = h2.applyAccessibility(),
        h3 = h3.applyAccessibility(),
        h4 = h4.applyAccessibility(),
        body1 = body1.applyAccessibility(),
        body2 = body2.applyAccessibility(),
        body3 = body3.applyAccessibility(),
        label1 = label1.applyAccessibility(),
        label2 = label2.applyAccessibility(),
        label3 = label3.applyAccessibility(),
        button = button.applyAccessibility(),
        input = input.applyAccessibility(),
        eyebrow = eyebrow.applyAccessibility(),
        caption = caption.applyAccessibility(),
        quote = quote.applyAccessibility(),
        gloss = gloss.applyAccessibility(),
        label = label.applyAccessibility(),
    )
}

private fun FontWeight?.boldened(): FontWeight =
    when {
        this == null || weight >= 700 -> FontWeight.ExtraBold
        weight >= 600 -> FontWeight.Bold
        weight >= 500 -> FontWeight.SemiBold
        else -> FontWeight.SemiBold
    }


val LocalTypography = staticCompositionLocalOf { defaultTypography }
val LocalTextStyle = compositionLocalOf(structuralEqualityPolicy()) { TextStyle.Default }
