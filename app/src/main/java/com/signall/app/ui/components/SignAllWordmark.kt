package com.signall.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signall.app.ui.SignAllBranding
import com.signall.app.ui.designsystem.LocalIsDarkTheme

/**
 * The SignALL wordmark (spec `BRD-2`), the splash's hero.
 *
 * The width comes from the painter's own intrinsic ratio instead of a constant: the
 * asset is 3.59:1 today and a hard-coded number would silently stretch if the master
 * changed. The variant comes from [LocalIsDarkTheme] because the "Sign" half is navy
 * in the master and would vanish on the dark background.
 */
@Composable
fun SignAllWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    contentDescription: String? = SignAllBranding.Name,
) {
    val painter = painterResource(SignAllBranding.logo(LocalIsDarkTheme.current))
    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(width = height * ratio, height = height),
    )
}
