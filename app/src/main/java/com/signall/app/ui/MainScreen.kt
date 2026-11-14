package com.signall.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.signall.app.data.MicPermissionPrompt
import com.signall.app.ui.components.HistoryBottomSheet
import com.signall.app.ui.components.SignAllSplash
import com.signall.app.ui.designsystem.components.DrawerConfig
import com.signall.app.ui.designsystem.components.DrawerHandlers
import com.signall.app.ui.designsystem.components.DrawerOpenState
import com.signall.app.ui.designsystem.components.DrawerSide
import com.signall.app.ui.designsystem.components.SignAllEdgeDrawer
import com.signall.app.ui.designsystem.components.Surface
import com.signall.app.ui.designsystem.components.SurfaceStyle
import com.signall.app.ui.designsystem.foundation.LocalReduceMotion
import com.signall.app.ui.designsystem.foundation.SignAllMotion
import com.signall.app.ui.designsystem.foundation.rememberKeyboardDismissAction
import com.signall.app.ui.screens.about.AboutScreen
import com.signall.app.ui.screens.bottombar.BottomBarActions
import com.signall.app.ui.screens.bottombar.BottomBarAvailability
import com.signall.app.ui.screens.bottombar.BottomBarInput
import com.signall.app.ui.screens.bottombar.SignAllBottomBar
import com.signall.app.ui.screens.conversation.ConversationPanel
import com.signall.app.ui.screens.settings.SettingsActions
import com.signall.app.ui.screens.settings.SettingsSidebar
import com.signall.app.ui.screens.stage.AvatarStage
import com.signall.app.ui.screens.stage.StageState
import com.signall.app.ui.state.MainScreenState
import com.signall.app.ui.state.MicPermissionNotice
import com.signall.app.ui.state.MicUnavailableNotice
import com.signall.app.ui.theme.SignAllTheme
import com.signall.app.ui.webview.AvatarBridgeCallbacks
import com.signall.app.ui.webview.AvatarWebView
import kotlinx.coroutines.delay

private const val SplashTimeoutMs = 8_000L

// La salida del splash es un cruce: el splash se va y el contenido entra en el
// mismo tramo. Estaba a 500/400 ms y el cambio se sentía como un corte: el
// avatar aparecía de golpe. El owner pidió "el doble de lento" (2026-10-22),
// así que las dos mitades del cruce duran el doble y se solapan todo lo posible
// —el contenido arranca a entrar mientras el splash todavía se está yendo—.
private const val ContentFadeMs = 1_000
private const val SplashFadeMs = 800

/** ¿El permiso de micrófono ya está concedido? */
private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * El permiso del micrófono, encapsulado.
 *
 * La solicitud sale **al abrir** la app y una sola vez (`RF-UI-04`, SRS
 * `main.tex:837`); el toque del interruptor vuelve a preguntar si todavía no se
 * concedió. Las dos acciones de voz pasan por el mismo permiso, así que comparte
 * un solo camino y no dos copias de la comprobación.
 */
private class MicGate(
    val granted: Boolean,
    val onMic: () -> Unit,
    val onLive: () -> Unit,
)

@Composable
private fun rememberMicGate(state: MainScreenState): MicGate {
    val context = LocalContext.current
    val prompt = remember(context) { MicPermissionPrompt(context) }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // El permiso se lee al componer y se actualiza con la respuesta del
    // diálogo: `hasMicPermission()` no es estado de Compose y sin esto el aviso
    // del panel no se enteraba de que el usuario acababa de concederlo.
    var micGranted by remember { mutableStateOf(context.hasMicPermission()) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingMicAction
        pendingMicAction = null
        // Sin acción pendiente no hay nada que retomar: conceder el permiso deja
        // el micrófono disponible, no lo prende. Negarlo nunca rompe nada, solo
        // avisa —`RF-UI-04` exige el aviso y la app sigue con el texto—.
        micGranted = granted
        if (granted) action?.invoke() else state.onMicPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (!context.hasMicPermission()) {
            if (!prompt.alreadyAsked) {
                prompt.alreadyAsked = true
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                // El diálogo ya se pidió y el permiso sigue negado: el usuario
                // abre la app con el micrófono muerto y antes no había ninguna
                // explicación visible (`RF-UI-04`, PR-AND-01).
                state.onMicPermissionMissingAtLaunch()
            }
        }
    }

    fun withMicPermission(action: () -> Unit): () -> Unit = {
        if (context.hasMicPermission()) {
            action()
        } else {
            pendingMicAction = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    return MicGate(
        granted = micGranted,
        onMic = withMicPermission(state::toggleDictation),
        onLive = withMicPermission(state::toggleLive),
    )
}

/** Lo que la escena necesita saber para dibujarse. */
@Immutable
private data class SceneState(
    val contentAlpha: Float,
    val loading: Boolean,
    val bottomBarHeight: Dp,
    val conversationFraction: Float,
    val settingsFraction: Float,
    val showConversation: Boolean,
    val showSettings: Boolean,
)

/** Las acciones con las que la escena habla hacia arriba. */
private data class SceneCallbacks(
    val onLoadingChange: (Boolean) -> Unit,
    val onProgress: (Int) -> Unit,
    val onBottomBarHeightChange: (Dp) -> Unit,
    val onConversationFractionChange: (Float) -> Unit,
    val onSettingsFractionChange: (Float) -> Unit,
    val onShowAbout: () -> Unit,
    val onShowHistory: () -> Unit,
    val dismissKeyboard: () -> Unit,
    val micGate: MicGate,
)

@Composable
fun MainScreen(state: MainScreenState) {
    var showHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    // Open fraction of each drawer: the left one drives the fallback dim of the
    // right handle, the right one drives the dim of the left handle (`EDG-14`).
    var conversationFraction by remember { mutableFloatStateOf(0f) }
    var settingsFraction by remember { mutableFloatStateOf(0f) }
    // Tocar cualquier control fuera del campo deja el teclado cerrado. El panel
    // lo hace por su cuenta; acá alcanza para los tiradores de los drawers.
    val dismissKeyboard = rememberKeyboardDismissAction()
    val micGate = rememberMicGate(state)

    val showConversation = state.openDrawer == DrawerSide.Start
    val showSettings = state.openDrawer == DrawerSide.End

    // Blur the 3D canvas only while a drawer is open (perf, spec `EDG-14`). The
    // control panel is disabled separately, by `state.replayPending` (`CNV-9`).
    LaunchedEffect(showConversation, showSettings) {
        state.setBlur(showConversation || showSettings)
    }

    // Back closes the history sheet, then the open drawer, instead of leaving.
    BackHandler(enabled = showConversation || showSettings || showHistory) {
        when {
            showHistory -> showHistory = false
            showConversation || showSettings -> state.closeDrawer()
        }
    }

    // Safety net: never leave the app stuck on the splash if the WebView fails
    // to load (e.g. no internet for the Three.js CDN).
    //
    // Keyed on [loading] and not on `Unit`: Acerca de swaps the whole main subtree out
    // of composition, so returning recreates the WebView and turns `loading` back on
    // (see `onBack` below). With a `Unit` key that second wait never existed and a
    // failed reload would leave the splash up forever.
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        delay(SplashTimeoutMs)
        loading = false
    }

    // The STT's `SILENCE_TIMEOUT_MS`, for the scene's debug overlay (D-6).
    //
    // Keyed on `!loading` because it is the WebView that shows it, and the WebView
    // is destroyed and recreated when Acerca de is opened and closed: reading it
    // before the scene exists would leave the new one showing "?". Best effort —
    // `silenceTimeoutMs()` returns null when /health can't be read, and the overlay
    // says "?", which is the truth.
    LaunchedEffect(!loading) {
        if (loading) return@LaunchedEffect
        state.pushSilenceTimeout()
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (loading) 0f else 1f,
        animationSpec = SignAllMotion.motionTween(
            durationMillis = ContentFadeMs,
            easing = LinearEasing,
        ),
        label = "contentAlpha",
    )
    val splashExit: ExitTransition =
        if (LocalReduceMotion.current) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(durationMillis = SplashFadeMs))
        }

    val scene = SceneState(
        contentAlpha = contentAlpha,
        loading = loading,
        bottomBarHeight = bottomBarHeight,
        conversationFraction = conversationFraction,
        settingsFraction = settingsFraction,
        showConversation = showConversation,
        showSettings = showSettings,
    )
    val callbacks = SceneCallbacks(
        onLoadingChange = { loading = it },
        onProgress = { progress = it },
        onBottomBarHeightChange = { bottomBarHeight = it },
        onConversationFractionChange = { conversationFraction = it },
        onSettingsFractionChange = { settingsFraction = it },
        onShowAbout = { showAbout = true },
        onShowHistory = { showHistory = true },
        dismissKeyboard = dismissKeyboard,
        micGate = micGate,
    )

    Box(Modifier.fillMaxSize()) {
        if (showAbout) {
            // About is exclusive: Main and drawers are not shown (spec `NAV-8`).
            AboutScreen(
                onBack = {
                    showAbout = false
                    // Salir de Acerca de **destruye** el WebView, así que volver
                    // a la escena lo crea de cero y hay que volver a descargar y
                    // parsear el GLB del avatar (34,28 MB con Juan, 29,46 MB con
                    // Pauline): se veía el canvas vacío durante segundos (owner,
                    // 2026-10-16). Se vuelve a levantar el splash, que es lo que ya
                    // hay para el primer arranque y espera al `avatarReady` real.
                    loading = true
                },
            )
        } else {
            MainScene(state = state, scene = scene, callbacks = callbacks)
        }

        HistoryBottomSheet(
            visible = showHistory && !showAbout,
            history = state.historyList,
            onPick = { phrase ->
                showHistory = false
                state.setInput(phrase)
                state.play(phrase)
            },
            onDismiss = { showHistory = false },
        )

        AnimatedVisibility(
            visible = loading,
            enter = EnterTransition.None,
            exit = splashExit,
        ) {
            SignAllSplash(progress = progress)
        }
    }
}

/**
 * Escena, panel y los dos drawers.
 *
 * Compose order is the fix (spec `EDG-22`). The WebView is an embedded Android
 * View: it takes every touch inside its bounds and no Compose sibling can paint
 * over it, so the stage and the control bar must be the FIRST children and the
 * drawers must be composed after them, as siblings (not nested inside each
 * other).
 */
@Composable
private fun MainScene(
    state: MainScreenState,
    scene: SceneState,
    callbacks: SceneCallbacks,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scene.contentAlpha },
        style = SurfaceStyle(color = SignAllTheme.colors.background),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelWidth = minOf(maxWidth * 0.88f, 350.dp)
            Box(Modifier.fillMaxSize()) {
                SceneLayer(state = state, scene = scene, callbacks = callbacks)
                BottomBarLayer(state = state, scene = scene, callbacks = callbacks)
                ConversationDrawer(
                    state = state,
                    scene = scene,
                    callbacks = callbacks,
                    panelWidth = panelWidth,
                )
                SettingsDrawer(
                    state = state,
                    scene = scene,
                    callbacks = callbacks,
                    panelWidth = panelWidth,
                )
            }
        }
    }
}

/** El avatar y su `WebView`, montados dos frames después del primer dibujo. */
@Composable
private fun SceneLayer(
    state: MainScreenState,
    scene: SceneState,
    callbacks: SceneCallbacks,
) {
    // El WebView es la parte cara del arranque: crearlo y empezar a cargar la
    // página bloquea el hilo de UI mientras el splash todavía no se dibujó, así
    // que el usuario veía el canvas vacío antes de ver nada. Se lo crea **dos
    // frames después** del primer dibujo: el splash ya está en pantalla y el
    // trabajo pesado pasa por detrás (owner, 2026-10-22).
    var sceneMounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        sceneMounted = true
    }
    if (!sceneMounted) return

    AvatarStage(
        stage = StageState(
            status = state.statusLine,
            playback = state.stateForUi,
            doneVisible = state.doneVisible,
            bottomInset = scene.bottomBarHeight,
            currentGloss = state.currentGloss,
        ),
        // Repetir es un control más: cierra el teclado, y cierra de verdad.
        onRepeat = {
            callbacks.dismissKeyboard()
            state.replay()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        AvatarWebView(
            onReady = state::attachWebView,
            callbacks = AvatarBridgeCallbacks(
                onProgress = callbacks.onProgress,
                onPlaybackDone = state::onPlaybackDone,
                onSignStart = state::onSignStart,
                onSignFailed = state::onSignFailed,
                onAvatarLoaded = state::onAvatarLoaded,
                onAvatarFailed = state::onAvatarFailed,
                onSceneFailed = state::onSceneFailed,
                onLoaded = {
                    callbacks.onLoadingChange(false)
                    state.syncWebSettings()
                },
            ),
            modifier = Modifier.fillMaxSize(),
            interactive = !(scene.showConversation || scene.showSettings),
            avatarId = state.settings.character.id,
        )
    }
}

/** El panel de control, anclado abajo. */
@Composable
private fun BoxScope.BottomBarLayer(
    state: MainScreenState,
    scene: SceneState,
    callbacks: SceneCallbacks,
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged {
                callbacks.onBottomBarHeightChange(with(density) { it.height.toDp() })
            },
    ) {
        SignAllBottomBar(
            input = BottomBarInput(
                query = state.inputText,
                dictationActive = state.dictationActive,
                liveActive = state.liveActive,
                // Hay texto dictado esperando que el usuario confirme el envío: el
                // tercer estado de RF-UI-01.
                dictationReadyToSend = state.dictationReadyToSend,
                // El campo tiene la frase que la app ya mandó: se la puede leer, pero
                // no se la puede volver a mandar (`STA-20`).
                sentTextLocked = state.sentTextLocked,
            ),
            availability = BottomBarAvailability(
                // Dos cosas distintas pueden apagar el micrófono y el aviso tiene que
                // decir cuál: falta el permiso, o no hay servicio de voz pero el texto
                // sigue.
                micAvailable = state.backendConnected && state.sttAvailable,
                micNotice = micNotice(state, callbacks.micGate.granted),
                // Un cambio de avatar deja el modelo a medio construir: el panel
                // espera a `avatarLoaded`.
                enabled = !state.replayPending && !state.avatarLoading,
            ),
            playbackState = state.stateForUi,
            actions = BottomBarActions(
                onQueryChange = state::setInput,
                onSend = state::play,
                onMic = callbacks.micGate.onMic,
                onOpenHistory = callbacks.onShowHistory,
                onLive = callbacks.micGate.onLive,
            ),
        )
    }
}

/** Cuál de los dos motivos apaga el micrófono, o ninguno. */
private fun micNotice(state: MainScreenState, micGranted: Boolean): String? =
    when {
        !micGranted -> MicPermissionNotice
        !state.sttAvailable -> MicUnavailableNotice
        else -> null
    }

@Composable
private fun ConversationDrawer(
    state: MainScreenState,
    scene: SceneState,
    callbacks: SceneCallbacks,
    panelWidth: Dp,
) {
    SignAllEdgeDrawer(
        side = DrawerSide.Start,
        state = DrawerOpenState(
            isOpen = scene.showConversation,
            otherDrawerOpen = scene.showSettings,
            currentlyOpenFraction = scene.conversationFraction,
            otherOpenFraction = scene.settingsFraction,
        ),
        config = DrawerConfig(
            drawerWidth = panelWidth,
            edgeStripBottomExclusion = scene.bottomBarHeight,
            previewLabel = "Conversación",
        ),
        handlers = DrawerHandlers(
            onOpenChange = { open ->
                // Abrir un drawer también es tocar un control fuera del campo: el
                // teclado se cierra, y cierra de verdad.
                if (open) {
                    callbacks.dismissKeyboard()
                    state.openDrawer(DrawerSide.Start)
                } else {
                    state.closeDrawer()
                }
            },
            onFractionChange = callbacks.onConversationFractionChange,
            claimGesture = { state.claimDrawerGesture(DrawerSide.Start) },
            releaseGesture = { state.releaseDrawerGesture(DrawerSide.Start) },
        ),
        drawerContent = {
            ConversationPanel(
                turns = state.conversation,
                onRepeat = state::repeatTurnFromDrawer,
                onClose = state::closeDrawer,
            )
        },
    ) {
        // Empty: the drawer still needs the slot, but the scene is a sibling
        // below it now (spec `EDG-22`).
        Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun SettingsDrawer(
    state: MainScreenState,
    scene: SceneState,
    callbacks: SceneCallbacks,
    panelWidth: Dp,
) {
    SignAllEdgeDrawer(
        side = DrawerSide.End,
        state = DrawerOpenState(
            isOpen = scene.showSettings,
            otherDrawerOpen = scene.showConversation,
            currentlyOpenFraction = scene.settingsFraction,
            otherOpenFraction = scene.conversationFraction,
        ),
        config = DrawerConfig(
            drawerWidth = panelWidth,
            edgeStripBottomExclusion = scene.bottomBarHeight,
            previewLabel = "Ajustes",
        ),
        handlers = DrawerHandlers(
            onOpenChange = { open ->
                // Abrir un drawer también es tocar un control fuera del campo: el
                // teclado se cierra, y cierra de verdad.
                if (open) {
                    callbacks.dismissKeyboard()
                    state.openDrawer(DrawerSide.End)
                } else {
                    state.closeDrawer()
                }
            },
            onFractionChange = callbacks.onSettingsFractionChange,
            claimGesture = { state.claimDrawerGesture(DrawerSide.End) },
            releaseGesture = { state.releaseDrawerGesture(DrawerSide.End) },
        ),
        drawerContent = {
            SettingsSidebar(
                settings = state.settings,
                canSwitchAvatar = state.canSwitchAvatar,
                sign = state.sign,
                signs = state.signs,
                actions = SettingsActions(
                    onToggle = state::toggle,
                    onCharacter = state::setCharacter,
                    onSpeed = state::setSpeed,
                    onInterfaceSize = state::setInterfaceSize,
                    onPickSign = state::pickSign,
                    onClose = state::closeDrawer,
                    onOpenAbout = {
                        state.closeDrawer()
                        callbacks.onShowAbout()
                    },
                ),
            )
        },
    ) {
        Box(Modifier.fillMaxSize())
    }
}
