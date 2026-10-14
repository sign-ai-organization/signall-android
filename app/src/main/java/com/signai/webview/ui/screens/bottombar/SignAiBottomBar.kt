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
import com.signai.webview.ui.designsystem.foundation.rememberKeyboardDismissAction
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
 * - **Micrófono (fila 1)** — dictado: se toca, se habla, se toca de nuevo y el
 *   texto se manda solo. Lo que se escribió queda a la vista, pero Traducir no
 *   queda disponible para firmarlo otra vez.
 * - **TR (fila 2)** — "tiempo real": el interruptor continuo, el que firma
 *   mientras se habla sin esperar a que termine la frase.
 *
 * Los dos son interruptores, ninguno se mantiene apretado, y **no pueden estar
 * prendidos a la vez**: comparten una sola captura de micrófono y un solo socket.
 * Por eso cada uno deshabilita al otro, en los dos sentidos, y ninguno se
 * deshabilita mientras está prendido: es el botón de parar, y si la única forma
 * de cortar el modo continuo quedara bloqueada durante una seña no habría forma
 * de cortarla.
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
    voiceTextLocked: Boolean = false,
    micAvailable: Boolean = true,
    enabled: Boolean = true,
) {
    val dismissKeyboard = rememberKeyboardDismissAction()

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
            !micActive &&
            // Tampoco si el campo tiene la frase que la voz acaba de mandar: el
            // botón ofrecería reenviar lo que ya se firmó, y el avatar firmaría
            // la frase dos veces seguidas por un solo toque.
            !voiceTextLocked

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
    //
    // La misma regla para los dos, y **en los dos sentidos**: comparten el
    // `AudioRecord` y el socket, así que no pueden estar prendidos a la vez y el
    // que está prendido tiene que dejar al otro deshabilitado. Antes solo pasaba
    // en un sentido —el micrófono se apagaba con TR prendido, pero TR seguía
    // habilitado con el micrófono prendido—, así que un estado que el código sí
    // representa y el backend rechaza aparecía en pantalla como disponible.
    val micEnabled = enabled && micAvailable && !realtimeActive
    val realtimeEnabled = enabled && micAvailable && !dictationActive


    // Mandar es una cosa y cerrar el teclado es otra: el botón **siempre** cierra el
    // teclado, y solo manda si además la frase se puede mandar. Si el `onDone`
    // dependiera de `sendEnabled`, con el micrófono prendido —donde el campo es
    // de solo lectura y no hay nada que mandar— el botón del teclado no
    // cerraba nada y el usuario se quedaba sin ninguna salida.
    val send: () -> Unit = {
        val text = query.trim()
        dismissKeyboard()
        if (text.isNotEmpty() && sendEnabled) onSend(text)
    }

    // Cada control del panel cierra el teclado antes de hacer lo suyo. Con solo
    // `keyboard?.hide()` el teclado se iba un segundo y volvía, porque el campo
    // seguía con el foco; `dismissKeyboard()` se lo saca primero.
    val mic: () -> Unit = { dismissKeyboard(); onMic() }
    val realtime: () -> Unit = { dismissKeyboard(); onRealtime() }
    val openHistory: () -> Unit = { dismissKeyboard(); onOpenHistory() }

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
                    //
                    // Y lo mismo con la frase que la voz acaba de mandar: está
                    // para que se lea, no para que se mande. Se va sola, así que
                    // dejarla editable 1,5 s solo serviría para que el teclado
                    // salte sin motivo. Cuando se borra, `sentVoiceText` vuelve
                    // a `null` en la misma asignación, y con ella el campo y
                    // Traducir quedan utilizables en el mismo fotograma.
                    enabled = controlsEnabled || micActive || voiceTextLocked,
                    readOnly = micActive || voiceTextLocked,
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
                    onClick = mic,
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
                    onClick = openHistory,
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
                // Nunca se deshabilita **prendido**: es el único botón que puede
                // apagar el modo continuo, así que bloquearlo sería dejar al
                // usuario sin salida. Apagado sí se deshabilita, y solo cuando
                // está tomando el micrófono el otro interruptor.
                //
                // Igual que en el micrófono, el apagado se ve en el icono y en la
                // etiqueta: atenuar la superficie entera —`soft` sobre `surface`,
                // los dos casi blancos— no se veía.
                val trContentColor = when {
                    realtimeActive -> SignAiTheme.colors.onStrong
                    !realtimeEnabled -> SignAiTheme.colors.muted
                    else -> SignAiTheme.colors.text
                }
                Surface(
                    onClick = realtime,
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
                                when {
                                    realtimeActive -> "Encendido. Firmando mientras hablás."
                                    !realtimeEnabled ->
                                        "Apagado. El micrófono está dictando."
                                    else -> "Apagado. Tocar para firmar en tiempo real."
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
                            tint = trContentColor,
                        )
                        Box(Modifier.size(9.dp))
                        SignAiText(
                            text = "TR",
                            style =
                                SignAiTheme.typography.body1
                                    .copy(fontWeight = FontWeight.Bold),
                            color = trContentColor,
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
    // El apagado se marca apagando el **icono**, no la superficie entera.
    //
    // El relleno de estos dos botones es `soft` sobre una barra `surface`: al
    // bajarle el alfa a 0.35 a todo, el fondo queda a `#F8F8F5` sobre `#FEFEFC`,
    // o sea un gris que a simple vista es el mismo color, y el botón seguía
    // pareciendo habilitado siendo que no lo estaba. Attenuar solo el contenido
    // deja el bloque gris bien visible y el icono se ve apagado.
    val tint = when {
        active -> SignAiTheme.colors.onStrong
        !enabled -> SignAiTheme.colors.muted
        else -> SignAiTheme.colors.text
    }
    val ringColor = SignAiTheme.colors.soft
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        modifier = Modifier
            .size(SignAiSizes.actionButtonSize)
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
