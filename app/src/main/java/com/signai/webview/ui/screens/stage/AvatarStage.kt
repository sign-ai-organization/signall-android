package com.signai.webview.ui.screens.stage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.SignAiMotion
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSpacing
import com.signai.webview.ui.designsystem.foundation.pressScale
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.state.PlaybackState
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Avatar stage (spec `STG-*`): the 3D WebView is the protagonist, overlaid with
 * the status hint (top-left), the current-sign badge (top-center) and the
 * Repetir button (bottom-center, just above the control bar, only when the last
 * playback is `done`). [bottomInset] keeps the button clear of the overlaid
 * control bar.
 *
 * The WebView itself is passed as [content] so this stays a presentation
 * component and never touches the bridge directly.
 */

/** Space between the Repetir button and the top edge of the control bar. */
private val RepeatGap = 32.dp

/**
 * Distancia entre la barra de estado y el rótulo de la seña en curso.
 *
 * 36 dp deja la píldora justo debajo del indicador de estado —que ocupa la
 * esquina superior izquierda— y encima de la cabeza del avatar: con el encuadre
 * por defecto (`STG-9`) la cabeza arranca alrededor del 13 % del alto, y el
 * rótulo termina antes. Es una distancia fija y no un porcentaje porque la
 * píldora mide lo mismo en cualquier pantalla; lo que tiene que quedar libre
 * es su alto, no su centro.
 */
private val GlossTopGap = 36.dp

/**
 * El indicador creció un 25 % (owner, 2026-10-17): era la señal de estado de la
 * app y a 7 dp con 12 sp no se leía de reojo, que es como se mira mientras el
 * avatar representa.
 */
private const val StageHintScale = 1.25f
private val StageHintDotSize = 7.dp * StageHintScale

/**
 * El color del punto del indicador, por estado.
 *
 * **Tres colores para siete estados**, no uno por estado: un semáforo, que es lo
 * que el owner pidió ("coherencia, no un montón de cambios"). Los tres son tokens
 * que ya existen en `ui/designsystem/Color.kt` —no se inventó ningún color— y se
 * reparten por lo que la app está haciendo, no por el nombre del estado:
 *
 * - **Azul** (`strong`, el de marca): el camino normal. Inactivo, Texto listo,
 *   Reproduciendo.
 * - **Ámbar** (`live`): hay que esperar. Grabando y Procesando.
 * - **Rojo** (`error`): algo falló o no hay a quién preguntar. Error y
 *   Desconectado.
 *
 * El ámbar era de `live`/`onLive`, que por manual significa "escuchando ahora"
 * (`docs/manual-identidad-visual.md`). Aquí se ensancha a las dos esperas, que es
 * el mismo sentido —algo está pasando y toca esperar— y **no** se usa para nada
 * que no esté pasando en ese momento, que es lo que el manual protege.
 *
 * La fila *Error* de la tabla del SRS dice "el indicador de estados muestra la
 * señal de error": el punto rojo es esa señal. Y en la fila *Desconectado* dice
 * "el indicador de estados lo indica": el rojo también, porque la conexión caída
 * es un fallo aunque la app se esté reconectando sola.
 */
@Composable
private fun PlaybackState.dotColor(): Color = when (this) {
    PlaybackState.Grabando, PlaybackState.Procesando -> SignAiTheme.colors.live
    PlaybackState.Error, PlaybackState.Desconectado -> SignAiTheme.colors.error
    PlaybackState.Inactivo,
    PlaybackState.TextoListo,
    PlaybackState.Reproduciendo,
    -> SignAiTheme.colors.strong
}

@Composable
fun AvatarStage(
    status: String,
    state: PlaybackState,
    doneVisible: Boolean,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    currentGloss: String? = null,
    content: @Composable () -> Unit,
) {
    // `AnimatedVisibility` sigue componiendo su contenido mientras se va, y para
    // entonces `currentGloss` ya es `null`: se guarda el último texto para que la
    // salida muestre la seña que terminó y no una píldora vacía.
    var lastGloss by remember { mutableStateOf(currentGloss) }
    LaunchedEffect(currentGloss) {
        if (currentGloss != null) lastGloss = currentGloss
    }

    // Un solo spec para todas las transiciones del rótulo. Se calcula acá porque
    // `motionTween` es `@Composable` —lee "reducir animaciones"— y las lambdas de
    // `transitionSpec` no lo son.
    val glossFade = SignAiMotion.motionTween<Float>(SignAiMotion.Fast)

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        content()

        // stage-hint: live dot + current playback status.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 25.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SignAiSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(StageHintDotSize)
                    .background(state.dotColor(), CircleShape),
            )
            SignAiText(
                text = status,
                // +25 % sobre `body3` (12/16), que es lo que pidió el owner el
                // 2026-10-17: el indicador es la única señal de estado de la app
                // y a 12 sp costaba de leer. Se escalan también `lineHeight` y
                // `letterSpacing` para que la línea no se corte.
                style = SignAiTheme.typography.body3.copy(
                    fontSize = 12.sp * StageHintScale,
                    lineHeight = 16.sp * StageHintScale,
                    letterSpacing = 0.15.sp * StageHintScale,
                ),
                color = SignAiTheme.colors.muted,
            )
        }

        // Rótulo de la seña en curso: arriba al centro, en la banda libre que
        // queda sobre la cabeza del avatar. Muestra **una** seña, la que la
        // escena acaba de arrancar, no la frase entera. Es la única superficie
        // que dice qué seña se está representando: el indicador de la izquierda
        // ya no la repite (owner, 2026-11-05).
        //
        // Sin `clickable` ni `pointerInput` a propósito: un nodo sin entrada no
        // participa del hit-test, así que un toque en esta zona sigue llegando
        // al WebView del avatar.
        AnimatedVisibility(
            visible = currentGloss != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = GlossTopGap),
            enter = fadeIn(glossFade) +
                scaleIn(
                    animationSpec = glossFade,
                    initialScale = 0.96f,
                ),
            exit = fadeOut(glossFade) +
                scaleOut(
                    animationSpec = glossFade,
                    targetScale = 0.96f,
                ),
        ) {
            Box(
                modifier = Modifier
                    .clip(SignAiShapes.cornerPill)
                    .background(SignAiTheme.colors.surface)
                    .border(1.dp, SignAiTheme.colors.outline, SignAiShapes.cornerPill)
                    .padding(
                        horizontal = SignAiSpacing.mdLg,
                        vertical = SignAiSpacing.sm,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Seña en curso: ${lastGloss.orEmpty()}"
                    },
            ) {
                // La píldora es una sola: lo que cambia es el texto de adentro,
                // con un fundido corto y el ancho acomodándose en el mismo
                // movimiento. Dos píldoras superpuestas durante el cruce se
                // verían como un parpadeo de dos anchos distintos.
                AnimatedContent(
                    targetState = lastGloss.orEmpty(),
                    transitionSpec = {
                        (
                            fadeIn(glossFade) togetherWith
                                fadeOut(glossFade)
                        )
                            .using(SizeTransform(clip = true))
                    },
                    label = "glosa-en-curso",
                ) { text ->
                    SignAiText(
                        text = text,
                        style = SignAiTheme.typography.h4.copy(textAlign = TextAlign.Center),
                        color = SignAiTheme.colors.text,
                        maxLines = 2,
                    )
                }
            }
        }

        // Repetir: only after a completed playback, centered a little above the
        // overlaid control bar. The gap is [bottomInset] (the measured bar
        // height) plus [RepeatGap]: 64 dp and the owner asked for "half", so 32.
        // Half, not nothing — glued to the bar it would read as part of it, and
        // the bar's own top edge is where the input's focus ring draws.
        AnimatedVisibility(
            visible = doneVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = RepeatGap + bottomInset),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = onRepeat,
                shape = SignAiShapes.cornerPill,
                color = SignAiTheme.colors.surface,
                border = BorderStroke(1.dp, SignAiTheme.colors.outline),
                interactionSource = interaction,
                modifier = Modifier
                    .pressScale(interaction)
                    .semantics { contentDescription = "Repetir reproducción" },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SignAiIcon(
                        imageVector = SignAiIcons.Repeat,
                        contentDescription = null,
                        size = 22.dp,
                        tint = SignAiTheme.colors.text,
                    )
                    SignAiText(
                        text = "Repetir",
                        style = SignAiTheme.typography.body2,
                        color = SignAiTheme.colors.text,
                    )
                }
            }
        }
    }
}
