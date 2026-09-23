package com.signai.webview.ui.designsystem.foundation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Swallows horizontal drags before the node underneath sees them, leaving tap
 * and vertical scroll intact.
 *
 * A `LazyColumn` consumes every drag once it decides to scroll, and it decides
 * on the dominant direction of the first touch slop — so sideways swipes over
 * the turns never reach the drawer behind, which is why closing the left
 * conversation drawer only worked from its header. This claims the gesture when
 * it commits horizontally and lets vertical scrolls through untouched
 * (spec `EDG-9`).
 */
fun Modifier.horizontalDragBlocked(): Modifier =
    pointerInput(Unit) {
        detectHorizontalDragGestures { change, _ -> change.consume() }
    }
