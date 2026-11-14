package com.signall.app.ui.screens.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.signall.app.ui.designsystem.components.SignAllIcon
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.components.Surface
import com.signall.app.ui.designsystem.components.SurfaceStyle
import com.signall.app.ui.designsystem.components.textfield.OutlinedTextField
import com.signall.app.ui.designsystem.components.textfield.OutlinedTextFieldDefaults
import com.signall.app.ui.designsystem.components.textfield.TextFieldDecorations
import com.signall.app.ui.designsystem.components.textfield.TextFieldInput
import com.signall.app.ui.designsystem.components.textfield.TextFieldOptions
import com.signall.app.ui.designsystem.components.textfield.TextFieldVisuals
import com.signall.app.ui.designsystem.foundation.SignAllShapes
import com.signall.app.ui.designsystem.foundation.SignAllSizes
import com.signall.app.ui.designsystem.foundation.rememberKeyboardDismissAction
import com.signall.app.ui.icons.SignAllIcons
import com.signall.app.ui.state.PlaybackState
import com.signall.app.ui.theme.SignAllTheme

/**
 * Control panel (spec `CTL-*`), en dos filas:
 *
 * 1. `[ input de texto | 🎤 micrófono | 🕐 historial ]`
 * 2. `[ ➤ Traducir | ◢◢ TR ]`
 *
 * Los dos de la derecha son cosas distintas y no se confunden:
 *
 * - **Micrófono (fila 1)** — dictado: se toca, se habla, y la frase se cierra sola
 *   cuando el STT oye la pausa (o cuando se toca de nuevo). Lo que se escribió
 *   queda a la vista **editable** y mandarlo es un paso aparte: Traducir. Es el
 *   paso de confirmación de RF-STT-03. Tocar el micrófono con texto esperando lo
 *   descarta y vuelve a dictar de cero.
 * - **TR (fila 2)** — "en vivo": el interruptor continuo. El micrófono queda
 *   prendido entre frases y cada frase sale sola al cerrarse, sin que nadie toque
 *   nada. Es el **único** modo que manda solo. Ya no representa a mitad de frase: la
 *   unidad es la oración.
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

/** Lo que el panel muestra: el texto y en qué modo está el micrófono. */
@Immutable
data class BottomBarInput(
    val query: String,
    val dictationActive: Boolean = false,
    val liveActive: Boolean = false,
    val dictationReadyToSend: Boolean = false,
    val sentTextLocked: Boolean = false,
)

/** De qué dispone la app para mandar voz, y si el panel entero está habilitado. */
@Immutable
data class BottomBarAvailability(
    val micAvailable: Boolean = true,
    val micNotice: String? = null,
    val enabled: Boolean = true,
)

/** Hacia dónde habla el panel. */
class BottomBarActions(
    val onQueryChange: (String) -> Unit,
    val onSend: (String) -> Unit,
    val onMic: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onLive: () -> Unit,
)

@Composable
fun SignAllBottomBar(
    input: BottomBarInput,
    availability: BottomBarAvailability,
    playbackState: PlaybackState,
    actions: BottomBarActions,
) {
    val dismissKeyboard = rememberKeyboardDismissAction()
    val controls = controlsFor(input, availability, playbackState)

    // Mandar es una cosa y cerrar el teclado es otra: el botón **siempre** cierra el
    // teclado, y solo manda si además la frase se puede mandar. Si el `onDone`
    // dependiera de `sendEnabled`, con el micrófono prendido —donde el campo es
    // de solo lectura y no hay nada que mandar— el botón del teclado no
    // cerraba nada y el usuario se quedaba sin ninguna salida.
    val send: () -> Unit = {
        val text = input.query.trim()
        dismissKeyboard()
        if (text.isNotEmpty() && controls.sendEnabled) actions.onSend(text)
    }
    // Cada control del panel cierra el teclado antes de hacer lo suyo. Con solo
    // `keyboard?.hide()` el teclado se iba un segundo y volvía, porque el campo
    // seguía con el foco; `dismissKeyboard()` se lo saca primero.
    val mic: () -> Unit = { dismissKeyboard(); actions.onMic() }
    val live: () -> Unit = { dismissKeyboard(); actions.onLive() }
    val openHistory: () -> Unit = { dismissKeyboard(); actions.onOpenHistory() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(SignAllTheme.colors.surface)
            // **Un** inset de fondo, el mayor entre la barra de navegación y el
            // teclado —eso es exactamente `safeDrawing`—, no la suma de los dos.
            // Con `.navigationBarsPadding().imePadding()` se sumaban: el teclado se
            // dibuja **encima** de la barra de navegación, así que su alto ya la
            // incluye, y el panel quedaba un cacho más arriba del teclado.
            //
            // El padding va **después** del `background` a propósito: así el color
            // del panel sigue llegando hasta el borde de abajo de la pantalla y la
            // píldora de navegación queda sobre el panel y no sobre el fondo.
            //
            // El hueco grande de antes (~200 dp) no era solo esto: el manifest tenía
            // `windowSoftInputMode` en `<application>`, donde el sistema lo ignora y
            // queda `adjustPan`, así que además el sistema paneaba la ventana
            // (owner, 2026-10-22). Las dos cosas se arreglan juntas.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        // 1dp top border (prototype `border-top: 1px solid var(--line)`).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SignAllTheme.colors.outline),
        )
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MicNotice(availability.micNotice)
            InputRow(
                input = input,
                controls = controls,
                onQueryChange = actions.onQueryChange,
                send = send,
                mic = mic,
                openHistory = openHistory,
            )
            ActionRow(
                input = input,
                controls = controls,
                send = send,
                live = live,
            )
        }
    }
}

/** Aviso del micrófono (RF-UI-04, PR-AND-01), pegado al botón que lo explica. */
@Composable
private fun MicNotice(notice: String?) {
    if (notice == null) return
    // Antes solo existía como texto de depuración —`showStatus` no escribe nada
    // sin `DEV_TOOLS`—, así que quien negaba el permiso no veía ninguna
    // explicación. Va acá y no en el indicador del escenario: la tabla de estados
    // del SRS solo permite ahí el nombre del estado y «Seña no disponible».
    SignAllText(
        text = notice,
        color = SignAllTheme.colors.text,
        style = SignAllTheme.typography.caption,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Fila 1: el campo de texto, el micrófono y el historial. */
@Composable
private fun InputRow(
    input: BottomBarInput,
    controls: Controls,
    onQueryChange: (String) -> Unit,
    send: () -> Unit,
    mic: () -> Unit,
    openHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input.query,
            onValueChange = onQueryChange,
            // El campo es de solo lectura cuando el micrófono está prendido: sigue
            // mostrando lo que se está transcribiendo en vivo (`inputText` lo va
            // llenando el handler de `Transcript`), pero no acepta escritura ni abre
            // el teclado. `readOnly` y no `enabled`: un campo deshabilitado se
            // atenúa y el texto se vuelve difícil de leer, y acá el texto es justo
            // lo que el usuario quiere ver.
            //
            // Y lo mismo con la frase que la voz acaba de mandar: está para que se
            // lea, no para que se mande. Se va sola, así que dejarla editable 1,5 s
            // solo serviría para que el teclado salte sin motivo.
            options = TextFieldOptions(
                enabled = controls.fieldEnabled,
                readOnly = controls.fieldReadOnly,
                singleLine = true,
            ),
            input = TextFieldInput(
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { send() }),
            ),
            decorations = TextFieldDecorations(
                placeholder = {
                    SignAllText(
                        text = placeholderFor(input),
                        color = SignAllTheme.colors.muted,
                    )
                },
            ),
            modifier = Modifier
                .weight(1f)
                .height(SignAllSizes.inputHeight),
            visuals = inputFieldVisuals(),
        )
        ControlIconButton(
            spec = ControlButtonSpec(
                icon = SignAllIcons.Mic,
                contentDescription = "Entrada por voz",
                toggle = true,
                activeDescription = "Dictando. Tocar para dejar de dictar.",
                inactiveDescription = micInactiveDescription(input),
            ),
            active = input.dictationActive,
            enabled = controls.micEnabled,
            onClick = mic,
        )
        ControlIconButton(
            spec = ControlButtonSpec(
                icon = SignAllIcons.History,
                contentDescription = "Historial reciente",
            ),
            // Con el micrófono prendido el panel se ocupa solo de escuchar: texto,
            // Traducir e Historial quedan fuera. La conversación en curso ya se ve
            // en el escenario, así que nada se pierde.
            active = false,
            enabled = controls.controlsEnabled && !controls.micActive,
            onClick = openHistory,
        )
    }
}

/** Fila 2: Traducir y el interruptor del modo en vivo. */
@Composable
private fun ActionRow(
    input: BottomBarInput,
    controls: Controls,
    send: () -> Unit,
    live: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = send,
            enabled = controls.sendEnabled,
            style =
                SurfaceStyle(
                    shape = SignAllShapes.cornerControl,
                    color = SignAllTheme.colors.strong,
                ),
            modifier = Modifier
                .weight(1f)
                .height(SignAllSizes.actionButtonHeight)
                .alpha(if (controls.sendEnabled) 1f else 0.35f)
                .semantics {
                    role = Role.Button
                    contentDescription = "Traducir"
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(SignAllSizes.actionButtonHeight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignAllIcon(
                    imageVector = SignAllIcons.Send,
                    contentDescription = null,
                    size = 18.dp,
                    tint = SignAllTheme.colors.onStrong,
                )
                Box(Modifier.size(9.dp))
                SignAllText(
                    text = "Traducir",
                    style = SignAllTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                    color = SignAllTheme.colors.onStrong,
                )
            }
        }

        // TR = "en vivo". Mitad y mitad con Traducir: los dos pesan 1f, así que el
        // padding y el `spacedBy(9.dp)` de la fila los reparten solos y ningún
        // ancho se calcula a mano.
        //
        // Nunca se deshabilita **prendido**: es el único botón que puede apagar el
        // modo continuo, así que bloquearlo sería dejar al usuario sin salida.
        //
        // Igual que en el micrófono, el apagado se ve en el icono y en la etiqueta:
        // atenuar la superficie entera —`soft` sobre `surface`, los dos casi
        // blancos— no se veía.
        val contentColor = liveContentColor(input.liveActive, controls.liveEnabled)
        Surface(
            onClick = live,
            enabled = controls.liveEnabled,
            style =
                SurfaceStyle(
                    shape = SignAllShapes.cornerControl,
                    color = if (input.liveActive) SignAllTheme.colors.live else SignAllTheme.colors.soft,
                ),
            modifier = Modifier
                .weight(1f)
                .height(SignAllSizes.actionButtonHeight)
                .semantics {
                    role = Role.Switch
                    contentDescription = "Modo en vivo"
                    stateDescription = liveStateDescription(input.liveActive, controls.liveEnabled)
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SignAllSizes.actionButtonHeight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignAllIcon(
                    imageVector = SignAllIcons.Waveform,
                    contentDescription = null,
                    size = 18.dp,
                    tint = contentColor,
                )
                Box(Modifier.size(9.dp))
                SignAllText(
                    text = "TR",
                    style =
                        SignAllTheme.typography.body1
                            .copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                )
            }
        }
    }
}

/** Qué se puede tocar en este instante, y por qué. */
private data class Controls(
    val controlsEnabled: Boolean,
    val micActive: Boolean,
    val sendEnabled: Boolean,
    val micEnabled: Boolean,
    val liveEnabled: Boolean,
    val fieldEnabled: Boolean,
    val fieldReadOnly: Boolean,
)

private fun controlsFor(
    input: BottomBarInput,
    availability: BottomBarAvailability,
    playbackState: PlaybackState,
): Controls {
    // `Reproduciendo` **no** bloquea el panel. Bloquear todo el panel mientras el
    // muñeco representa dejaba al usuario mirando un formulario muerto sin ninguna
    // señal de por qué. Lo que realmente no se puede tocar es lo que está **en
    // vuelo**: una traducción pedida y todavía sin representar.
    //
    // Mandar otra frase con el avatar representando la anterior no pisa la
    // animación: la escena reemplaza la cola, que es lo mismo que pasa con
    // cualquier frase nueva.
    //
    // Un solo estado para las dos esperas: el SRS tiene *Procesando* y no distingue
    // esperar la transcripción de esperar las glosas (`main.tex:726-773`), así que
    // acá tampoco hay dos.
    val busy = playbackState == PlaybackState.Procesando
    val controlsEnabled = availability.enabled && !busy
    val micActive = input.dictationActive || input.liveActive

    // Con el micrófono prendido no se manda: texto y voz van por el mismo socket,
    // así que intercalados el `index` de las oraciones deja de corresponder al
    // orden en que llegaron y el avatar representaría la frase al revés. Son dos
    // formas de mandar, no dos a la vez.
    //
    // Tampoco si el campo tiene la frase que la app acaba de mandar —un Traducir a
    // mano o el Repetir—: el botón ofrecería reenviar lo que ya se representó.
    val sendEnabled =
        controlsEnabled && input.query.isNotBlank() && !micActive && !input.sentTextLocked

    // El estado visual de los dos interruptores viene de su propio flag y no de
    // `playbackState`: con el micrófono prendido el muñeco representa al mismo
    // tiempo, así que el estado de la reproducción pasa a `Signing` y el botón se
    // vería apagado justo cuando está captando.
    //
    // Y el micrófono se habilita con una regla sola: nunca se deshabilita mientras
    // esté prendido, porque si la única forma de pararlo quedara bloqueada durante
    // una seña no habría forma de pararlo. La misma regla para los dos, y **en los
    // dos sentidos**: comparten el `AudioRecord` y el socket, así que el que está
    // prendido deja al otro deshabilitado.
    val micEnabled = availability.enabled && availability.micAvailable && !input.liveActive
    val liveEnabled = availability.enabled && availability.micAvailable && !input.dictationActive

    return Controls(
        controlsEnabled = controlsEnabled,
        micActive = micActive,
        sendEnabled = sendEnabled,
        micEnabled = micEnabled,
        liveEnabled = liveEnabled,
        fieldEnabled = controlsEnabled || micActive || input.sentTextLocked,
        fieldReadOnly = micActive || input.sentTextLocked,
    )
}

/** Lo que dice el campo cuando está vacío, según el modo. */
private fun placeholderFor(input: BottomBarInput): String =
    when {
        input.dictationActive -> "Dictando..."
        input.liveActive -> "Escuchando..."
        else -> "Escribe una frase..."
    }

/**
 * El micrófono **no** cambia de icono cuando hay texto esperando.
 *
 * RF-UI-01 v0.2 pedía tres estados visuales del botón y que el tercero mandara la
 * frase. La parte del ícono se probó en el dispositivo y se descartó: con un icono
 * distinto el botón deja de leerse como "micrófono". La señal persistente es el
 * **estado**: hay texto en el campo y la etiqueta del escenario dice "Texto
 * listo" (`main.tex:746`). Se decidió con el owner el 2026-10-17 hacer A1 así.
 */
private fun micInactiveDescription(input: BottomBarInput): String =
    when {
        input.dictationReadyToSend ->
            "Apagado. Hay texto esperando: tocar lo borra y vuelve a dictar."
        input.liveActive ->
            "Apagado. El modo en vivo está usando el micrófono."
        else ->
            "Apagado. Tocar para dictar."
    }

@Composable
private fun liveContentColor(active: Boolean, enabled: Boolean): Color =
    when {
        active -> SignAllTheme.colors.onLive
        !enabled -> SignAllTheme.colors.muted
        else -> SignAllTheme.colors.text
    }

private fun liveStateDescription(active: Boolean, enabled: Boolean): String =
    when {
        active -> "Encendido. Cada frase se representa sola al terminar."
        !enabled -> "Apagado. El micrófono está dictando."
        else -> "Apagado. Tocar para representar cada frase al terminar."
    }

/** Lo que un botón cuadrado de la fila de arriba necesita saber de sí mismo. */
@Immutable
private data class ControlButtonSpec(
    val icon: ImageVector,
    val contentDescription: String,
    val toggle: Boolean = false,
    val activeDescription: String = "",
    val inactiveDescription: String = "",
)

/**
 * Botón cuadrado de la fila de arriba: micrófono e historial.
 *
 * Mismo `actionButtonSize` y mismo anillo activo para los dos, así las filas miden
 * igual. Lo que hace cada uno va por su propio callback.
 */
@Composable
private fun ControlIconButton(
    spec: ControlButtonSpec,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = SignAllShapes.cornerControl
    val container = if (active) SignAllTheme.colors.live else SignAllTheme.colors.soft
    // El apagado se marca apagando el **icono**, no la superficie entera.
    //
    // El relleno de estos dos botones es `soft` sobre una barra `surface`: al
    // bajarle el alfa a 0.35 a todo, el fondo queda a `#F8F8F5` sobre `#FEFEFC`,
    // o sea un gris que a simple vista es el mismo color, y el botón seguía
    // pareciendo habilitado siendo que no lo estaba. Atenuar solo el contenido
    // deja el bloque gris bien visible y el icono se ve apagado.
    val tint = when {
        active -> SignAllTheme.colors.onLive
        !enabled -> SignAllTheme.colors.muted
        else -> SignAllTheme.colors.text
    }
    val ringColor = SignAllTheme.colors.soft
    Surface(
        onClick = onClick,
        enabled = enabled,
        style =
            SurfaceStyle(
                shape = shape,
                color = container,
            ),
        modifier = Modifier
            .size(SignAllSizes.actionButtonSize)
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
                role = if (spec.toggle) Role.Switch else Role.Button
                this.contentDescription = spec.contentDescription
                if (spec.toggle) {
                    this.stateDescription =
                        if (active) spec.activeDescription else spec.inactiveDescription
                }
            },
    ) {
        Box(
            modifier = Modifier.size(SignAllSizes.actionButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            SignAllIcon(
                imageVector = spec.icon,
                contentDescription = null,
                size = 20.dp,
                tint = tint,
            )
        }
    }
}

/**
 * El aspecto del campo de texto: la forma del control y los colores del panel, en
 * lugar del `surface` que trae el componente por defecto.
 */
@Composable
private fun inputFieldVisuals(): TextFieldVisuals {
    val base = OutlinedTextFieldDefaults.visuals()
    val baseColors = base.colors
    val inputText =
        baseColors.text.input.copy(
            focused = SignAllTheme.colors.text,
            unfocused = SignAllTheme.colors.text,
        )
    val background =
        baseColors.container.background.copy(
            focused = SignAllTheme.colors.background,
            unfocused = SignAllTheme.colors.background,
        )
    val border =
        baseColors.container.border.copy(
            focused = SignAllTheme.colors.strong,
            unfocused = SignAllTheme.colors.outline,
        )

    return base.copy(
        shape = SignAllShapes.cornerControl,
        colors =
            baseColors.copy(
                container = baseColors.container.copy(background = background, border = border),
                text = baseColors.text.copy(input = inputText),
                cursor = baseColors.cursor.copy(color = SignAllTheme.colors.strong),
            ),
    )
}
