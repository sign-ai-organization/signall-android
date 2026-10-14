package com.signai.webview.ui.screens.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextField
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextFieldDefaults
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.state.PlaybackState
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Control panel (spec `CTL-*`), en dos filas:
 *
 * 1. `[ input de texto | 🎤 micrófono | 🕐 historial ]`
 * 2. `[ ➤ Traducir | ◢◢ TR ]`
 *
 * Los dos de la derecha son cosas distintas y no se confunden:
 *
 * - **Micrófono (fila 1)** — dictado: se toca, se habla, se toca de nuevo. El
 *   texto se va escribiendo en el input y el muñeco no se mueve. Traducirlo es
 *   un paso aparte, el botón Traducir.
 * - **TR (fila 2)** — "tiempo real": el interruptor continuo, el que firma
 *   mientras se habla sin esperar a que termine la frase.
 *
 * Los dos son interruptores, ninguno se mantiene apretado, y **no pueden estar
 * prendidos a la vez**: comparten una sola captura de micrófono y un solo socket.
 * Por eso el micrófono queda deshabilitado mientras TR está prendido, pero TR
 * nunca se deshabilita: si la única forma de cortar el modo continuo estuviera
 * bloqueada durante una seña, no habría forma de cortarlo.
 *
 * El input usa `weight(1f)` y los dos botones son de ancho fijo, así que el
 * micrófono le saca ancho al campo sin tocar ningún tamaño.
 */
@Composable
fun SignAiBottomBar(
    query: String,
    onQueryChange: (String) -> Unit,
    playbackState: PlaybackState,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onOpenHistory: () -> Unit,
    onRealtime: () -> Unit,
    dictationActive: Boolean = false,
    realtimeActive: Boolean = false,
    micAvailable: Boolean = true,
    enabled: Boolean = true,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    // `Signing` **no** bloquea el panel. En tiempo real la cola se llena más
    // rápido de lo que el avatar la consume —en una prueba de 30 s eran 38
    // fragmentos, casi 40 s de señas por delante—, y bloquear todo el panel
    // mientras el muñeco finishes dejaba al usuario mirando un formulario muerto
    // sin ninguna señal de por qué. Lo que realmente no se puede tocar es lo que
    // está **en vuelo**: una traducción pedida y todavía sin firmar.
    //
    // Mandar otra frase con el avatar firmando la anterior no pisa la animación:
    // la escena reemplaza la cola, que es lo mismo que pasa con cualquier frase
    // nueva.
    val busy =
        playbackState == PlaybackState.Translating ||
            playbackState == PlaybackState.Processing
    val controlsEnabled = enabled && !busy

    val micActive = dictationActive || realtimeActive

    val sendEnabled =
        controlsEnabled &&
            query.isNotBlank() &&
            // Con el micrófono prendido no: texto y voz van por el mismo socket y el
            // backend no puede saber de quién es cada fragmento. Intercalados, el
            // `index` de los fragmentos de voz deja de corresponderse con el orden
            // en que llegaron y el avatar firmaría la frase al revés. Son dos
            // formas de mandar, no dos a la vez.
            !micActive

    // El estado visual de los dos interruptores viene de su propio flag y no de
    // `playbackState`: con el micrófono prendido el muñeco firma al mismo tiempo,
    // así que el estado de la reproducción pasa a `Signing` y el botón se vería
    // apagado justo cuando está captando.
    //
    // Y el micrófono se habilita con una regla sola: nunca se deshabilita
    // mientras esté prendido, porque si la única forma de pararlo quedara
    // bloqueada durante una seña no habría forma de pararlo. Esa regla es la que
    // lo dejaba tocable mientras el resto del panel estaba deshabilitado, y no
    // es un descuido: es el botón de parar.
    val micEnabled = enabled && micAvailable && !realtimeActive
    val realtimeEnabled = enabled && micAvailable


    val send: () -> Unit = {
        val text = query.trim()
        if (text.isNotEmpty() && sendEnabled) {
            keyboard?.hide()
            onSend(text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(SignAiTheme.colors.surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 1dp top border (prototype `border-top: 1px solid var(--line)`).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SignAiTheme.colors.outline),
        )
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    // Con el mic prendido el campo se vuelve pantalla, no
                    // teclado: sigue mostrando lo que se está transcribiendo en
                    // vivo (`inputText` lo va llenando el handler de
                    // `Transcript`), pero no acepta escritura ni abre el teclado.
                    // Deliberadamente `readOnly` y no `enabled`: un campo
                    // deshabilitado se atenúa y el texto se vuelve difícil de
                    // leer, y acá el texto es justo lo que el usuario quiere ver.
                    enabled = controlsEnabled || micActive,
                    readOnly = micActive,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { send() }),
                    placeholder = {
                        SignAiText(
                            text = when {
                                dictationActive -> "Dictando..."
                                realtimeActive -> "Escuchando..."
                                else -> "Escribe una frase..."
                            },
                            color = SignAiTheme.colors.muted,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(SignAiSizes.inputHeight),
                    shape = SignAiShapes.cornerControl,
                    colors = OutlinedTextFieldDefaults.colors().copy(
                        focusedTextColor = SignAiTheme.colors.text,
                        unfocusedTextColor = SignAiTheme.colors.text,
                        cursorColor = SignAiTheme.colors.strong,
                        focusedOutlineColor = SignAiTheme.colors.strong,
                        unfocusedOutlineColor = SignAiTheme.colors.outline,
                        focusedContainerColor = SignAiTheme.colors.background,
                        unfocusedContainerColor = SignAiTheme.colors.background,
                    ),
                )
                ControlIconButton(
                    icon = SignAiIcons.Mic,
                    contentDescription = "Entrada por voz",
                    onClick = onMic,
                    active = dictationActive,
                    enabled = micEnabled,
                    toggle = true,
                    activeDescription = "Dictando. Tocar para dejar de dictar.",
                    inactiveDescription =
                        if (realtimeActive) {
                            "Apagado. El modo tiempo real está usando el micrófono."
                        } else {
                            "Apagado. Tocar para dictar."
                        },
                )
                ControlIconButton(
                    icon = SignAiIcons.History,
                    contentDescription = "Historial reciente",
                    onClick = onOpenHistory,
                    // Con el micrófono prendido el panel se ocupa solo de
                    // escuchar: texto, Traducir e Historial quedan fuera. La
                    // conversación en curso ya se ve en el escenario, así que
                    // nada se pierde.
                    enabled = controlsEnabled && !micActive,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = send,
                    enabled = sendEnabled,
                    shape = SignAiShapes.cornerControl,
                    color = SignAiTheme.colors.strong,
                    modifier = Modifier
                        .weight(1f)
                        .height(SignAiSizes.actionButtonHeight)
                        .alpha(if (sendEnabled) 1f else 0.35f)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Traducir"
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(SignAiSizes.actionButtonHeight),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SignAiIcon(
                            imageVector = SignAiIcons.Send,
                            contentDescription = null,
                            size = 18.dp,
                            tint = SignAiTheme.colors.onStrong,
                        )
                        Box(Modifier.size(9.dp))
                        SignAiText(
                            text = "Traducir",
                            style = SignAiTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                            color = SignAiTheme.colors.onStrong,
                        )
                    }
                }

                // TR = "tiempo real". Mitad y mitad con Traducir: los dos pesan 1f, así
                // que el padding y el `spacedBy(9.dp)` de la fila los reparten
                // solos y ningún ancho se calcula a mano.
                //
                // Nunca se deshabilita, ni siquiera prendido: es el único botón
                // que puede apagar el modo continuo, así que bloquearlo sería
                // dejar al usuario sin salida.
                Surface(
                    onClick = onRealtime,
                    enabled = realtimeEnabled,
                    shape = SignAiShapes.cornerControl,
                    color =
                        if (realtimeActive) SignAiTheme.colors.strong
                        else SignAiTheme.colors.soft,
                    modifier = Modifier
                        .weight(1f)
                        .height(SignAiSizes.actionButtonHeight)
                        .semantics {
                            role = Role.Switch
                            contentDescription = "Modo tiempo real"
                            stateDescription =
                                if (realtimeActive) {
                                    "Encendido. Firmando mientras hablás."
                                } else {
                                    "Apagado. Tocar para firmar en tiempo real."
                                }
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SignAiSizes.actionButtonHeight),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SignAiIcon(
                            imageVector = SignAiIcons.Waveform,
                            contentDescription = null,
                            size = 18.dp,
                            tint =
                                if (realtimeActive) SignAiTheme.colors.onStrong
                                else SignAiTheme.colors.text,
                        )
                        Box(Modifier.size(9.dp))
                        SignAiText(
                            text = "TR",
                            style =
                                SignAiTheme.typography.body1
                                    .copy(fontWeight = FontWeight.Bold),
                            color =
                                if (realtimeActive) SignAiTheme.colors.onStrong
                                else SignAiTheme.colors.text,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Botón cuadrado de la fila de arriba: micrófono e historial.
 *
 * Mismo `actionButtonSize` y mismo anillo activo para los dos, así las filas
 * miden igual. Lo que hace cada uno va por su propio callback.
 */
@Composable
private fun ControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    toggle: Boolean = false,
    activeDescription: String = "",
    inactiveDescription: String = "",
) {
    val shape = SignAiShapes.cornerControl
    val container = if (active) SignAiTheme.colors.strong else SignAiTheme.colors.soft
    val tint = if (active) SignAiTheme.colors.onStrong else SignAiTheme.colors.text
    val ringColor = SignAiTheme.colors.soft
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        modifier = Modifier
            .size(SignAiSizes.actionButtonSize)
            .alpha(if (enabled) 1f else 0.35f)
            .then(
                if (active) {
                    // Prototype `.mic-button.active` has
                    // `box-shadow: 0 0 0 5px var(--soft)`: a 5dp soft ring that
                    // does not affect layout. Drawn behind the surface.
                    Modifier.drawBehind {
                        val ring = 5.dp.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(-ring, -ring),
                            size = Size(size.width + ring * 2, size.height + ring * 2),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .semantics {
                // El micrófono es un interruptor (queda prendido hasta que se
                // toca de nuevo), no un botón. `stateDescription` es lo que
                // lee TalkBack, y deja el `contentDescription` libre para los
                // selectors de las pruebas, que no cambian al prenderlo.
                role = if (toggle) Role.Switch else Role.Button
                this.contentDescription = contentDescription
                if (toggle) {
                    this.stateDescription =
                        if (active) activeDescription else inactiveDescription
                }
            },
    ) {
        Box(
            modifier = Modifier.size(SignAiSizes.actionButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            SignAiIcon(
                imageVector = icon,
                contentDescription = null,
                size = 20.dp,
                tint = tint,
            )
        }
    }
}
