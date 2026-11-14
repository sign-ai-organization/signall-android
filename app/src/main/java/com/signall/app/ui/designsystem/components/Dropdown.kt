package com.signall.app.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.signall.app.ui.designsystem.foundation.SignAllShapes
import com.signall.app.ui.designsystem.foundation.SignAllSizes
import com.signall.app.ui.designsystem.foundation.SignAllSpacing
import com.signall.app.ui.designsystem.foundation.LocalReduceMotion
import com.signall.app.ui.theme.SignAllTheme

// CUSTOMIZED — no re-generate with `lumo --add Dropdown` (Lumo doesn't have one).
// Replaces material3.DropdownMenu with foundation Popup + manual outside-click
// handling (focusable=true triggers onDismissRequest on outside click).
//
// **El menu se posiciona adentro de la ventana, no con `alignment`/`offset` del Popup.**
// El `Popup` de Compose abre su propia ventana, y en Android 11 esa ventana ignora
// el gravity y el x/y de `LayoutParams`: la deja centrada en la pantalla. Medido en
// el Redmi Note 9S (Android 11, build del 16/10/2026): con el trigger en
// `IntRect.fromLTRB(445, 522, 1019, 638)`, el menu aparecía en x≈12, y≈1158; poner
// `offset = (0, 1800)` lo movió 50 px, o sea no loHonoró. Por eso la ventana se estira
// a pantalla completa (`fillMaxSize`) y el menu se posiciona con un `offset` normal de
// Compose, medido contra el origen real de la ventana.
//
// Usage:
//   SignAllDropdown(
//       expanded = ...,
//       onDismissRequest = { ... },
//       trigger = { MyTrigger(onClick = { ... }) },
//   ) {
//       SignAllDropdownMenuItem("Foo") { ... }
//   }

@Composable
fun SignAllDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = SignAllSizes.dropdownMaxHeight,
    /** Ancho del menu. Por defecto, el mismo que el trigger. */
    menuWidth: Dp? = null,
    trigger: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var triggerBounds by remember { mutableStateOf(IntRect.Zero) }
    // Alto del menu una vez medido: sin esto no se puede saber si cabe abajo del
    // trigger o si hay que abrirlo hacia arriba, que es lo que pasa con el desplegable
    // de señas, que está al final de un panel con scroll.
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val r = coordinates.boundsInWindow()
                triggerBounds = IntRect(
                    left = r.left.toInt(),
                    top = r.top.toInt(),
                    right = r.right.toInt(),
                    bottom = r.bottom.toInt(),
                )
            },
        ) {
            trigger()
        }
        if (expanded) {
            Popup(
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                val view = LocalView.current
                val visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter =
                        if (LocalReduceMotion.current) {
                            EnterTransition.None
                        } else {
                            fadeIn(animationSpec = tween(160)) +
                                scaleIn(
                                    initialScale = 0.98f,
                                    animationSpec = tween(160),
                                )
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                val origin = IntArray(2)
                                view.rootView.getLocationOnScreen(origin)
                                val below = triggerBounds.bottom + menuSize.height
                                val y =
                                    if (below <= view.rootView.height) {
                                        triggerBounds.bottom
                                    } else {
                                        (triggerBounds.top - menuSize.height).coerceAtLeast(0)
                                    }
                                IntOffset(triggerBounds.left - origin[0], y - origin[1])
                            },
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Surface(
                            style =
                                SurfaceStyle(
                                    color = SignAllTheme.colors.surface,
                                    border = BorderStroke(1.dp, SignAllTheme.colors.outline),
                                    shape = SignAllShapes.cornerControl,
                                ),
                            modifier = Modifier
                                .width(menuWidth ?: with(density) { triggerBounds.width.toDp() })
                                .heightIn(max = maxHeight)
                                .onGloballyPositioned { menuSize = it.size },
                        ) {
                            // Con scroll, no sin: el desplegable de señas tiene 32
                            // opciones y sin esto las últimas no se alcanzan nunca
                            // (la Column recortaba en `maxHeight` y no había forma de
                            // bajar).
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(6.dp),
                                content = content,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SignAllDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SignAllTheme.colors.text,
    maxLines: Int = Int.MAX_VALUE,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SignAllSizes.dropdownMinHeight)
            .clickable { onClick() }
            .padding(horizontal = SignAllSpacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        SignAllText(text, color = color, maxLines = maxLines)
    }
}
