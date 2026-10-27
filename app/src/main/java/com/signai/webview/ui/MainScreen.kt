package com.signai.webview.ui

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.signai.webview.data.MicPermissionPrompt
import com.signai.webview.ui.components.HistoryBottomSheet
import com.signai.webview.ui.components.SignAiSplash
import com.signai.webview.ui.designsystem.components.DrawerSide
import com.signai.webview.ui.designsystem.components.SignAiEdgeDrawer
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.LocalReduceMotion
import com.signai.webview.ui.designsystem.foundation.SignAiMotion
import com.signai.webview.ui.designsystem.foundation.rememberKeyboardDismissAction
import com.signai.webview.ui.screens.about.AboutScreen
import com.signai.webview.ui.screens.bottombar.SignAiBottomBar
import com.signai.webview.ui.screens.conversation.ConversationPanel
import com.signai.webview.ui.screens.settings.SettingsSidebar
import com.signai.webview.ui.screens.stage.AvatarStage
import com.signai.webview.ui.state.MainScreenState
import com.signai.webview.ui.theme.SignAiTheme
import com.signai.webview.ui.webview.AvatarWebView
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
    val density = LocalDensity.current
    // Tocar cualquier control fuera del campo deja el teclado cerrado. El panel
    // lo hace por su cuenta; acá alcanza para los tiradores de los drawers.
    val dismissKeyboard = rememberKeyboardDismissAction()

    // El mic es la única función que necesita permiso en tiempo de ejecución, y
    // la app lo pide **al abrir**, no en el primer toque del micrófono: el SRS
    // dice que la solicitud aparece la primera vez que se abre la aplicación
    // (`main.tex:837`), que es lo mismo que pedía `RF-UI-04`. Preguntarlo tarde
    // fazia que el diálogo llegara con el dedo del usuario ya encima del control.
    val context = LocalContext.current
    val micPrompt = remember(context) { MicPermissionPrompt(context) }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingMicAction
        pendingMicAction = null
        // Sin acción pendiente no hay nada que retomar: conceder el permiso deja
        // el micrófono disponible, no lo prende. Negarlo nunca rompe nada, solo
        // avisa —`RF-UI-04` exige el aviso y la app sigue con el texto—.
        if (granted) action?.invoke() else state.onMicPermissionDenied()
    }

    // `RF-UI-04` / SRS `main.tex:837`: la solicitud sale al abrir y sale **una
    // sola vez**, que es lo que guarda `MicPermissionPrompt`. Sin ese flag el
    // diálogo se repetiría en cada arranque mientras el permiso siga sin
    // concederse, y con el aviso de la negativa al lado sería insistente.
    LaunchedEffect(Unit) {
        if (!context.hasMicPermission() && !micPrompt.alreadyAsked) {
            micPrompt.alreadyAsked = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Red de seguridad del permiso: si al abrir la app no se concedió, el toque
    // del interruptor vuelve a preguntar. Both voice switches go through here:
    // the permission is the same one, and duplicating the check meant one of the
    // two would eventually be the one that forgot.
    fun withMicPermission(action: () -> Unit): () -> Unit = {
        if (context.hasMicPermission()) {
            action()
        } else {
            pendingMicAction = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val onMic: () -> Unit = withMicPermission(state::toggleDictation)
    val onLive: () -> Unit = withMicPermission(state::toggleLive)

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
        animationSpec = SignAiMotion.motionTween(
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

    Box(Modifier.fillMaxSize()) {
        if (showAbout) {
            // About is exclusive: Main and drawers are not shown (spec `NAV-8`).
            AboutScreen(
                onBack = {
                    showAbout = false
                    // Salir de Acerca de **destruye** el WebView (está dentro del
                    // `else` de este `if`), así que volver a la escena lo crea de cero
                    // y hay que volver a descargar y parsear el GLB del avatar (34,28 MB
                    // con Juan, 29,46 MB con Pauline): se veía el
                    // canvas vacío durante segundos (owner, 2026-10-16). Se vuelve a
                    // levantar el splash, que es lo que ya hay para el primer arranque y
                    // espera al `avatarReady` real, no a un tiempo fijo.
                    loading = true
                },
            )
        } else {
            // El WebView es la parte cara del arranque: crearlo y empezar a
            // cargar la página bloquea el hilo de UI mientras el splash todavía
            // no se dibujó, así que el usuario veía el canvas vacío antes de ver
            // nada. Se lo crea **dos frames después** del primer dibujo: el
            // splash ya está en pantalla y el trabajo pesado pasa por detrás
            // (owner, 2026-10-22).
            //
            // El estado vive dentro del `else` a propósito: volver de Acerca de
            // recrea la escena de cero, y ahí también conviene diferir.
            var sceneMounted by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                withFrameNanos { }
                sceneMounted = true
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha },
                color = SignAiTheme.colors.background,
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val panelWidth = minOf(maxWidth * 0.88f, 350.dp)

                    // Compose order is the fix (spec `EDG-22`). The WebView is
                    // an embedded Android View: it takes every touch inside its
                    // bounds and no Compose sibling can paint over it, so the
                    // stage and the control bar must be the FIRST children and
                    // the drawers must be composed after them, as siblings (not
                    // nested inside each other). Previously each drawer wrapped
                    // the next one (End → Start → stage), so the scene and the
                    // bar lived inside the drawers' content and stayed on top of
                    // the panels, while the backdrops never saw a tap.
                    Box(Modifier.fillMaxSize()) {
                        // Sin montar hasta el segundo frame: `AvatarStage` trae
                        // el `AndroidView` del WebView, que es lo que cuesta.
                        // Mientras tanto solo se ve el splash, que está encima.
                        if (sceneMounted) {
                            AvatarStage(
                                status = state.statusLine,
                                state = state.stateForUi,
                                doneVisible = state.doneVisible,
                                // Repetir es un control más: cierra el teclado, y cierra
                                // de verdad, como todos los demás.
                                onRepeat = {
                                    dismissKeyboard()
                                    state.replay()
                                },
                                bottomInset = bottomBarHeight,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                AvatarWebView(
                                    modifier = Modifier.fillMaxSize(),
                                    onReady = state::attachWebView,
                                    onProgress = { progress = it },
                                    onPlaybackDone = state::onPlaybackDone,
                                    onSignStart = state::onSignStart,
                                    onSignFailed = state::onSignFailed,
                                    onSceneFailed = state::onSceneFailed,
                                    onAvatarLoaded = state::onAvatarLoaded,
                                    onAvatarFailed = state::onAvatarFailed,
                                    onLoaded = {
                                        loading = false
                                        state.syncWebSettings()
                                    },
                                    interactive = !(showConversation || showSettings),
                                    avatarId = state.settings.character.id,
                                )
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .onSizeChanged {
                                    bottomBarHeight = with(density) { it.height.toDp() }
                                },
                        ) {
                            SignAiBottomBar(
                                query = state.inputText,
                                onQueryChange = state::setInput,
                                playbackState = state.stateForUi,
                                onSend = state::play,
                                onMic = onMic,
                                onOpenHistory = { showHistory = true },
                                onLive = onLive,
                                // Los dos son interruptores: quedan prendidos
                                // hasta que se los toca de nuevo, así que su
                                // estado visual y su habilitación vienen de acá y
                                // no de `playbackState`, que con el micrófono
                                // prendido pasa a `Signing` mientras representa.
                                dictationActive = state.dictationActive,
                                liveActive = state.liveActive,
                                // Hay texto dictado esperando que el usuario
                                // confirme el envío: el tercer estado de RF-UI-01.
                                dictationReadyToSend = state.dictationReadyToSend,
                                // El campo tiene la frase que la app ya mandó: se la
                                // puede leer, pero no se la puede volver a mandar
                                // (`STA-20`).
                                sentTextLocked = state.sentTextLocked,
                                micAvailable = state.backendConnected,
                                // Un cambio de avatar deja el modelo a medio
                                // construir: el panel espera a `avatarLoaded`.
                                enabled = !state.replayPending && !state.avatarLoading,
                            )
                        }

                        SignAiEdgeDrawer(
                            side = DrawerSide.Start,
                            isOpen = showConversation,
                            onOpenChange = { open ->
                                // Abrir un drawer también es tocar un control
                                // fuera del campo: el teclado se cierra, y
                                // cierra de verdad.
                                if (open) {
                                    dismissKeyboard()
                                    state.openDrawer(DrawerSide.Start)
                                } else {
                                    state.closeDrawer()
                                }
                            },
                            drawerWidth = panelWidth,
                            edgeStripBottomExclusion = bottomBarHeight,
                            previewLabel = "Conversación",
                            otherDrawerOpen = showSettings,
                            currentlyOpenFraction = conversationFraction,
                            otherOpenFraction = settingsFraction,
                            onFractionChange = { conversationFraction = it },
                            claimGesture = { state.claimDrawerGesture(DrawerSide.Start) },
                            releaseGesture = { state.releaseDrawerGesture(DrawerSide.Start) },
                            drawerContent = {
                                ConversationPanel(
                                    turns = state.conversation,
                                    onRepeat = state::repeatTurnFromDrawer,
                                    onClose = state::closeDrawer,
                                )
                            },
                        ) {
                            // Empty: the drawer still needs the slot, but the
                            // scene is a sibling below it now (spec `EDG-22`).
                            Box(Modifier.fillMaxSize())
                        }

                        SignAiEdgeDrawer(
                            side = DrawerSide.End,
                            isOpen = showSettings,
                            onOpenChange = { open ->
                                // Abrir un drawer también es tocar un control
                                // fuera del campo: el teclado se cierra, y
                                // cierra de verdad.
                                if (open) {
                                    dismissKeyboard()
                                    state.openDrawer(DrawerSide.End)
                                } else {
                                    state.closeDrawer()
                                }
                            },
                            drawerWidth = panelWidth,
                            edgeStripBottomExclusion = bottomBarHeight,
                            previewLabel = "Ajustes",
                            otherDrawerOpen = showConversation,
                            currentlyOpenFraction = settingsFraction,
                            otherOpenFraction = conversationFraction,
                            onFractionChange = { settingsFraction = it },
                            claimGesture = { state.claimDrawerGesture(DrawerSide.End) },
                            releaseGesture = { state.releaseDrawerGesture(DrawerSide.End) },
                            drawerContent = {
                                SettingsSidebar(
                                    settings = state.settings,
                                    onToggle = state::toggle,
                                    onCharacter = state::setCharacter,
                                    canSwitchAvatar = state.canSwitchAvatar,
                                    onSpeed = state::setSpeed,
                                    onInterfaceSize = state::setInterfaceSize,
                                    sign = state.sign,
                                    signs = state.signs,
                                    onPickSign = state::pickSign,
                                    onClose = state::closeDrawer,
                                    onOpenAbout = {
                                        state.closeDrawer()
                                        showAbout = true
                                    },
                                )
                            },
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
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
            SignAiSplash(progress = progress)
        }
    }
}
