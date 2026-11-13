package com.signall.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signall.app.ui.SignAllBranding

/**
 * The app icon as a tile: blue rounded square with the white hand (spec `BRD-1`).
 *
 * The rounding is in the PNG and not in a `clip()`: the asset *is* the icon, so it has
 * to look the same wherever it goes. The sizes in use are 40 dp (settings drawer) and
 * 68 dp (Acerca de), both well under the 288 px the asset ships.
 */
@Composable
fun SignAllBrandTile(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(SignAllBranding.Icon),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    }
}
