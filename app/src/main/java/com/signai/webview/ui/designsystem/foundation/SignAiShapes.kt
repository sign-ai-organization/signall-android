package com.signai.webview.ui.designsystem.foundation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// SignAI shape tokens. Use these instead of inline RoundedCornerShape(...).
//
// Prototype radii (PrototipoFrontend/app/globals.css): 28 (bottom sheet),
// 24 (panels/--radius), 23 (logo tiles), 16 (inputs/buttons/history items),
// 15 (repeat icon button), 14 (mini avatar), 13 (character trigger / settings
// logo), 12 (small icon buttons), 11 (dropdown menu items).
object SignAiShapes {
    val cornerSmall = RoundedCornerShape(6)
    val cornerMedium = RoundedCornerShape(8)
    val cornerLarge = RoundedCornerShape(12)

    val cornerMenu = RoundedCornerShape(11)
    val cornerIcon = RoundedCornerShape(12)
    val cornerTile = RoundedCornerShape(13)
    val cornerMiniAvatar = RoundedCornerShape(14)
    val cornerRepeat = RoundedCornerShape(15)
    val cornerControl = RoundedCornerShape(16)
    val cornerLogoTile = RoundedCornerShape(23)
    val cornerPanel = RoundedCornerShape(24)
    val cornerSheet = RoundedCornerShape(28)
    val cornerPill = RoundedCornerShape(percent = 50)
    // Conversation bubbles (panel de Conversación): 18 dp like the edge tab, with
    // the corner that points at the speaker squared down to 6 dp, so the two
    // sides of a turn read as a chat. `Start` is the gloss (left), `End` is the
    // phrase (right).
    val cornerBubbleStart = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
    val cornerBubbleEnd = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    val cornerEdgeTabLeft = RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 0.dp)
    val cornerEdgeTabRight = RoundedCornerShape(topStart = 18.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 18.dp)
    // Prototype `.side-left { border-radius: 0 27px 27px 0 }` and
    // `.side-right { border-radius: 27px 0 0 27px }` (inner edge rounded).
    val cornerDrawerLeft = RoundedCornerShape(topStart = 0.dp, topEnd = 27.dp, bottomEnd = 27.dp, bottomStart = 0.dp)
    val cornerDrawerRight = RoundedCornerShape(topStart = 27.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 27.dp)
}
