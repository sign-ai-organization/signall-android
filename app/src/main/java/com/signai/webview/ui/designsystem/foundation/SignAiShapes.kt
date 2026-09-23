package com.signai.webview.ui.designsystem.foundation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// SignAI shape tokens. Use these instead of inline RoundedCornerShape(...).
//
// Prototype radii (PrototipoFrontend/app/globals.css): 24 (panels/--radius),
// 28 (bottom sheet), 16 (inputs/buttons/history items), 13 (logo tiles /
// character trigger), 12 (small icon buttons). The older Lumo tokens are kept
// untouched for parity with the current UI.
object SignAiShapes {
    val cornerSmall = RoundedCornerShape(6)
    val cornerMedium = RoundedCornerShape(8)
    val cornerLarge = RoundedCornerShape(12)

    val cornerIcon = RoundedCornerShape(12)
    val cornerTile = RoundedCornerShape(13)
    val cornerControl = RoundedCornerShape(16)
    val cornerPanel = RoundedCornerShape(24)
    val cornerSheet = RoundedCornerShape(28)
    val cornerPill = RoundedCornerShape(percent = 50)
    val cornerEdgeTabLeft = RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 0.dp)
    val cornerEdgeTabRight = RoundedCornerShape(topStart = 18.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 18.dp)
}
