@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.pane.rdp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.saveable.rememberSaveable
import android.widget.Toast
import android.content.ActivityNotFoundException
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

private val Ink = Color(0xFF20212A)
private val Purple = Color(0xFF5652C9)
private val Paper = Color(0xFFF7F6FC)
private val Lime = Color(0xFFD4EA98)
private val Desk = Color(0xFF14151C)
private val Bar = Color(0xFF252631)
private val OnBar = Color(0xFFEFEDF7)

class MainActivity : ComponentActivity() {
    private val session: VoxlSession by viewModels()
    override fun onStart() { super.onStart(); Updater.foreground = true; Updater.check(this) }
    var desktopPinned by mutableStateOf(false); private set
    private var requestedPin = false
    fun pinDesktop() {
        try { startLockTask(); requestedPin = true }
        catch (_: IllegalStateException) { Toast.makeText(this, "Android couldn't pin this desktop.", Toast.LENGTH_LONG).show() }
    }
    fun unpinDesktop() {
        if (requestedPin || desktopPinned) {
            try { stopLockTask() } catch (_: IllegalStateException) { }
            catch (_: SecurityException) { Toast.makeText(this, "Use Android's unpin gesture to leave screen pinning.", Toast.LENGTH_LONG).show() }
            requestedPin = false
        }
        desktopPinned = getSystemService(android.app.ActivityManager::class.java).lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    }
    internal fun hideSystemBars() {
        if (session.phase != Phase.IDLE) WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            desktopPinned = getSystemService(android.app.ActivityManager::class.java).lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        }
        session.focus.foreground(hasFocus && !isFinishing)
    }
    override fun onResume() {
        super.onResume()
        desktopPinned = getSystemService(android.app.ActivityManager::class.java).lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        session.focus.foreground(true)
        hideSystemBars()
        session.view?.post { session.view?.restartInput() }
    }
    override fun onPause() {
        session.focus.foreground(false)
        session.view?.releaseInput()
        session.keyboard.releaseAll()
        super.onPause()
    }
    override fun onStop() {
        super.onStop()
        Updater.foreground = false
        // Leaving Pane while no desktop is open is the quiet moment for a silent self-update.
        if (session.phase == Phase.IDLE && !isChangingConfigurations) Updater.install(this, background = true)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            SideEffect {
                val insets = WindowCompat.getInsetsController(window,window.decorView)
                val lightBars = !dark && session.phase == Phase.IDLE
                insets.isAppearanceLightStatusBars = lightBars
                insets.isAppearanceLightNavigationBars = lightBars
            }
            MaterialExpressiveTheme(colorScheme = if (dark) darkColorScheme(
                primary = Color(0xFFC5BFFF), onPrimary = Color(0xFF29236E),
                primaryContainer = Color(0xFF403B8E), onPrimaryContainer = Color(0xFFE5E1FF),
                secondaryContainer = Color(0xFF394922), onSecondaryContainer = Lime,
                background = Desk, onBackground = OnBar,
                surface = Desk, onSurface = OnBar, surfaceContainer = Bar
            ) else lightColorScheme(
                primary = Purple, onPrimary = Color.White,
                primaryContainer = Color(0xFFE5E1FF), onPrimaryContainer = Color(0xFF28236F),
                secondaryContainer = Lime, onSecondaryContainer = Color(0xFF283713),
                background = Paper, onBackground = Ink,
                surface = Paper, onSurface = Ink, surfaceContainer = Color(0xFFEDEBF5)
            )) {
                Surface(Modifier.fillMaxSize()) {
                    if (session.phase == Phase.IDLE) Box(Modifier.fillMaxSize()) {
                        Home(session)
                        UpdateConfetti(Modifier.matchParentSize())
                    } else MaterialTheme(colorScheme = darkColorScheme(
                        primary = Color(0xFFC5BFFF), secondaryContainer = Color(0xFF394922), onSecondaryContainer = Lime,
                        surface = Desk, onSurface = OnBar, surfaceContainer = Bar
                    )) { Remote(session) }
                }
            }
        }
    }
}

private fun openLink(context: Context, intent: Intent) {
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
        Toast.makeText(context,"No app is available to open this link.",Toast.LENGTH_LONG).show()
    }
}

@Composable private fun Home(session: VoxlSession) {
    var password by remember(session.profile.host) {
        mutableStateOf(session.savedPassword())
    }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val hardKeyboard = hasHardwareKeyboard(LocalConfiguration.current.keyboard,LocalConfiguration.current.hardKeyboardHidden)
    LaunchedEffect(hardKeyboard) {
        if (hardKeyboard && session.profile.error() == null) passwordFocus.requestFocus()
    }
    val connect = {
        focus.clearFocus()
        session.connect(password)
        if (session.phase != Phase.IDLE) password = ""
    }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val compact = maxWidth < 600.dp
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 32.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary,shape = RoundedCornerShape(16.dp)) {
                    Text("▥",Modifier.padding(horizontal = 13.dp,vertical = 7.dp).semantics { hideFromAccessibility() },color = MaterialTheme.colorScheme.onPrimary,fontSize = 25.sp)
                }
                Text("Pane",Modifier.weight(1f).padding(start = 12.dp),style = MaterialTheme.typography.titleLarge,fontWeight = FontWeight.Bold)
                TextButton(onClick = { openLink(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://pane.flandolf.me/releases/"))) }) { Text("Builds ↗") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= 840.dp && LocalDensity.current.fontScale <= 1.3f) Row(Modifier.fillMaxSize(),horizontalArrangement = Arrangement.spacedBy(32.dp),verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(0.9f).verticalScroll(rememberScrollState())) { Introduction(false) }
                    Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                        UpdateCard(Modifier.padding(bottom = 12.dp))
                        ConnectCard(session,password,{password=it},passwordFocus,advanced,{advanced=!advanced},connect)
                    }
                } else Column(Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).align(Alignment.TopCenter),verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Introduction(true)
                    UpdateCard()
                    ConnectCard(session,password,{password=it},passwordFocus,advanced,{advanced=!advanced},connect)
                    Text("PRIVATE CONNECTION · VOXL",Modifier.padding(bottom = 20.dp),style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun Introduction(compact: Boolean) {
    Text("YOUR DESKTOP, WITH YOU",color = MaterialTheme.colorScheme.primary,style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
    Text("A little closer to your desktop.",Modifier.semantics { heading() },style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    Text("Connect to your Voxl server, then open your workspace.",style = MaterialTheme.typography.bodyLarge,color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!compact) {
        Spacer(Modifier.height(28.dp))
        Surface(shape = RoundedCornerShape(28.dp),color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(24.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tablet → Tailscale → Desktop",style = MaterialTheme.typography.titleMedium)
                Text("More room. Less overhead.",style = MaterialTheme.typography.titleLarge)
                Text("Choose a smaller desktop for a lighter session. Your apps stay running when you disconnect.",color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun SettingRow(title: String, detail: String, checked: Boolean, onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked,role = Role.Switch,onValueChange = onChange).padding(vertical = 8.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title,style = MaterialTheme.typography.titleMedium)
            Text(detail,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked,onCheckedChange = null)
    }
}

@Composable private fun ConnectCard(session: VoxlSession,password: String,onPassword: (String)->Unit,passwordFocus: FocusRequester,advanced: Boolean,onAdvanced: ()->Unit,connect: ()->Unit) {
    var showPassword by remember(session.profile.host) { mutableStateOf(false) }
    var hostTouched by remember { mutableStateOf(false) }
    val profile = session.profile
    val hostError = profile.hostError().takeIf { hostTouched }
    val valid = profile.error() == null
    Surface(shape = RoundedCornerShape(28.dp),color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your workspace",Modifier.semantics { heading() },style = MaterialTheme.typography.headlineSmall,fontWeight = FontWeight.SemiBold)
            Text(if (profile.host.isBlank()) "Add your server to get started" else "${profile.host}",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant,maxLines = 2,overflow = TextOverflow.Ellipsis)
            OutlinedTextField(value = profile.host,onValueChange = {hostTouched=true; session.profile=profile.copy(host=it.trim())},label = {Text("Voxl server address")},placeholder = {Text("wss://server.ts.net:6901/ws")},supportingText = {Text(hostError ?: "Voxl password authentication · H.264 video")},isError = hostError != null,singleLine = true,modifier = Modifier.fillMaxWidth(),shape = RoundedCornerShape(16.dp),keyboardOptions = KeyboardOptions(autoCorrectEnabled = false,keyboardType = KeyboardType.Uri,imeAction = ImeAction.Next))
            OutlinedTextField(value = password,onValueChange = onPassword,label = {Text("Password")},singleLine = true,modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),shape = RoundedCornerShape(16.dp),visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon = { TextButton(onClick = {showPassword=!showPassword},modifier = Modifier.semantics { contentDescription = if (showPassword) "Hide password" else "Show password" }) { Text(if (showPassword) "Hide" else "Show") } },keyboardOptions = KeyboardOptions(autoCorrectEnabled = false,keyboardType = KeyboardType.Password,imeAction = ImeAction.Go),keyboardActions = KeyboardActions(onGo={if (valid && password.isNotEmpty()) connect()}))
            SettingRow("Remember password","Encrypted on this tablet. Turn off to delete it.",session.rememberPassword,session::updateRememberPassword)
            Text("Desktop quality",Modifier.semantics { heading() },style = MaterialTheme.typography.titleMedium)
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 340.dp || fontScale > 1.3f) Column(Modifier.selectableGroup()) {
                    Quality.entries.forEach { quality ->
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=profile.quality==quality,role=Role.RadioButton,onClick={session.profile=profile.copy(quality=quality)}),verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(selected=profile.quality==quality,onClick=null)
                            Column(Modifier.weight(1f).padding(start=8.dp,top=8.dp,bottom=8.dp)) {
                                Text(quality.label,style=MaterialTheme.typography.titleSmall)
                                Text(quality.detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Quality.entries.forEachIndexed { index,quality ->
                        SegmentedButton(selected = profile.quality == quality,onClick = {session.profile=profile.copy(quality=quality)},shape = SegmentedButtonDefaults.itemShape(index,Quality.entries.size)) { Text(quality.label) }
                    }
                }
            }
            Text("${session.size} pixels · ${profile.quality.detail}",Modifier.semantics { liveRegion = LiveRegionMode.Polite },style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAdvanced,modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (advanced) "Expanded" else "Collapsed" }) { Text(if(advanced) "Hide server verification" else "Server verification") }
            if (advanced) {
                HorizontalDivider()
                Text("Verified server",Modifier.semantics { heading() },style = MaterialTheme.typography.titleMedium)
                Text("WSS verifies the server's certificate and hostname using Android's trusted authorities. WS is unencrypted: use it only over Tailscale or another trusted private network.",style = MaterialTheme.typography.bodySmall)
                Text("Voxl · WebSocket · H.264 · Server resizing requires --enable-resize",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (session.message.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.errorContainer,shape = RoundedCornerShape(16.dp)) {
                SelectionContainer { Text(session.message,Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onErrorContainer) }
            }
            Button(onClick = connect,enabled = password.isNotEmpty() && valid,modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),shapes = ButtonDefaults.shapes()) {
                Text("Open desktop",style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward,contentDescription = null)
            }
            Text(if (!valid) "Complete the server details to connect." else if (password.isEmpty()) "Enter your password to connect." else "Ready to connect to Voxl.",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val RemoteShortcuts = listOf("Esc" to 0x1b,"Tab" to 0x09,"↑" to 0x26,"↓" to 0x28,"←" to 0x25,"→" to 0x27,"Home" to 0x24,"End" to 0x23,"PgUp" to 0x21,"PgDn" to 0x22)

@android.annotation.SuppressLint("WrongConstant") // The cutout value restored below came directly from Android.
@Composable private fun Remote(session: VoxlSession) {
    val context = LocalContext.current
    val activity = context as MainActivity
    var clipboard by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf(false) }
    val keyboard = session.keyboard
    val imeVisible = WindowInsets.isImeVisible
    var redraws by remember { mutableIntStateOf(0) }
    val ready = session.phase == Phase.CONNECTED
    // The OnePlus keyboard cover and Bluetooth keyboards both report as a hard keyboard.
    val hardKeyboard = hasHardwareKeyboard(LocalConfiguration.current.keyboard,LocalConfiguration.current.hardKeyboardHidden)
    LaunchedEffect(help, ready) {
        if (help && ready) {
            session.paints.set(0)
            while(true) { delay(1000); redraws=session.paints.getAndSet(0) }
        }
    }
    val accessibility = remember(context) { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
    var touchExploration by remember { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(accessibility) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { touchExploration=it }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    SideEffect { session.inputSuspended = clipboard || help || confirmExit || pinPrompt }
    LaunchedEffect(ready, touchExploration, clipboard, help, confirmExit, pinPrompt) {
        if (ready && !touchExploration && !clipboard && !help && !confirmExit && !pinPrompt) { delay(2000); session.toolbar=false }
    }
    LaunchedEffect(clipboard, help, confirmExit, pinPrompt) {
        if (clipboard || help || confirmExit || pinPrompt) {
            session.view?.releaseInput()
            keyboard.releaseAll()
        }
    }
    LaunchedEffect(session.toolbar) { if (!session.toolbar) session.view?.requestFocus() }
    DisposableEffect(activity) {
        val window = activity.window
        val oldCutout = if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else 0
        val oldContrast = if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else false
        window.attributes = window.attributes.apply {
            if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode =
                if (android.os.Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Local controls use media volume; focused desktop input forwards volume keys to the host.
        activity.volumeControlStream = AudioManager.STREAM_MUSIC
        val insets = WindowCompat.getInsetsController(activity.window,activity.window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity.unpinDesktop()
            window.attributes = window.attributes.apply { if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = oldCutout }
            if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = oldContrast
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
            insets.show(WindowInsetsCompat.Type.systemBars())
            session.inputSuspended=false
            keyboard.releaseAll()
        }
    }
    LaunchedEffect(hardKeyboard, ready, imeVisible) { keyboard.releaseAll() }
    LaunchedEffect(ready, imeVisible) { if (ready && !imeVisible) activity.hideSystemBars() }
    LaunchedEffect(hardKeyboard, ready) { if (hardKeyboard && ready) session.view?.hideKeyboard() }
    BackHandler(enabled = !imeVisible && !clipboard && !help && !confirmExit && !pinPrompt) { if (session.toolbar) confirmExit=true else session.toolbar=true }
    Column(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        val key=event.nativeKeyEvent
        ready && !clipboard && !help && !confirmExit && !pinPrompt && keyboard.toolbarShortcut(key.action,key.keyCode,key.deviceId,key.metaState,key.repeatCount)
    }.background(Desk).imePadding()) {
        // The desktop owns the whole screen; controls float over it and hide while you work.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory={DesktopView(it,session)},update={ it.setImmersed(ready && !touchExploration && !session.toolbar && !imeVisible && !clipboard && !help && !confirmExit && !pinPrompt) },modifier=Modifier.fillMaxSize())
            if (!ready) Surface(Modifier.align(Alignment.Center),shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.widthIn(max=400.dp).verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    LoadingIndicator()
                    Text(if(session.phase==Phase.DISCONNECTING) "Closing session…" else "Opening your desktop…",Modifier.semantics { liveRegion=LiveRegionMode.Polite },style=MaterialTheme.typography.titleMedium)
                    Text("${session.profile.host}\n${session.size} · Voxl",style=MaterialTheme.typography.bodySmall)
                    Text("Keep Tailscale connected. Connection setup may take a few seconds.",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={session.disconnect()},enabled=session.phase!=Phase.DISCONNECTING) {Text("Cancel")}
                }
            }
            androidx.compose.animation.AnimatedVisibility(session.toolbar,Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal=8.dp,vertical=12.dp),enter=slideInVertically{-it}+fadeIn(),exit=slideOutVertically{-it}+fadeOut()) {
                Surface(shape=RoundedCornerShape(50),color=Bar,contentColor=OnBar,shadowElevation=8.dp) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(start=16.dp,end=6.dp).heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=RoundedCornerShape(50),color=if(ready) Lime else Color(0xFF8C8AA0)) { Box(Modifier.size(8.dp)) }
                        Column(Modifier.widthIn(max=200.dp).padding(start=12.dp,end=12.dp)) {
                            Text(session.profile.host,style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("${if (ready) "Connected" else if (session.phase == Phase.DISCONNECTING) "Disconnecting" else "Connecting"} · ${session.size}",style=MaterialTheme.typography.labelSmall,color=OnBar.copy(alpha=0.6f),maxLines=1)
                        }
                        VerticalDivider(Modifier.height(28.dp).padding(horizontal=4.dp),color=OnBar.copy(alpha=0.15f))
                        TextButton(enabled=ready,onClick={session.view?.releaseInput();session.toggleTrackpad()}) { Text(if(session.trackpad) "Touch: trackpad" else "Touch: direct",color=if(ready) Lime else OnBar.copy(alpha=0.38f)) }
                        TextButton(enabled=ready,onClick={
                            if (imeVisible) session.view?.hideKeyboard() else { session.toolbar=false;session.view?.keyboard() }
                        }) { Text(if (imeVisible) "Hide keyboard" else "Keyboard",color=if(ready) OnBar else OnBar.copy(alpha=0.38f)) }
                        TextButton(enabled=ready,onClick={clipboard=true}) { Text("Clipboard",color=if(ready) OnBar else OnBar.copy(alpha=0.38f)) }
                        TextButton(enabled=ready,onClick={session.view?.resetZoom()}) { Text("Fit",color=if(ready) OnBar else OnBar.copy(alpha=0.38f)) }
                        TextButton(enabled=ready,onClick={
                            try {
                                if (!session.focus.supported) Toast.makeText(context,"Quiet desktop needs Android 10 or newer.",Toast.LENGTH_LONG).show()
                                else if (!session.focus.permitted) {
                                    Toast.makeText(context,"Allow Pane in Do Not Disturb access, then turn on Quiet desktop.",Toast.LENGTH_LONG).show()
                                    openLink(context,Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                } else session.focus.updateEnabled(!session.focus.enabled)
                            } catch (_: Exception) { Toast.makeText(context,"Couldn't change Quiet desktop. Check Android's Do Not Disturb settings.",Toast.LENGTH_LONG).show() }
                        }) { Text(if(session.focus.active) "Quiet: on" else if(session.focus.enabled) "Quiet: paused" else "Quiet desktop",color=OnBar) }
                        TextButton(enabled=ready,onClick={if(activity.desktopPinned) activity.unpinDesktop() else pinPrompt=true}) { Text(if(activity.desktopPinned) "Unpin desktop" else "Pin desktop",color=OnBar) }
                        IconButton(onClick={help=true}) { Icon(Icons.Default.Info,"Controls and session info") }
                        IconButton(onClick={confirmExit=true}) { Icon(Icons.Default.Close,"Disconnect") }
                        FilledTonalIconButton(enabled=ready,onClick={session.toolbar=false}) { Icon(Icons.Default.KeyboardArrowUp,"Hide toolbar") }
                    }
                }
            }
            // A small handle keeps the toolbar reachable by touch without covering much of the remote panel.
            if (!session.toolbar) Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.displayCutout).size(96.dp,48.dp).semantics { contentDescription="Session toolbar" }.clickable(role=Role.Button,onClickLabel="Show toolbar"){session.toolbar=true},contentAlignment=Alignment.Center) {
                Surface(shape=RoundedCornerShape(50),color=OnBar.copy(alpha=0.45f)) { Box(Modifier.size(56.dp,5.dp)) }
            }
        }
        // Keep modifiers available whenever the user chooses the on-screen keyboard.
        if (ready && imeVisible) Surface(color=Bar,contentColor=OnBar) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                FilterChip(selected=keyboard.ctrl,onClick={keyboard.toggleCtrl();session.view?.requestFocus()},label={Text("Ctrl")},modifier=Modifier.heightIn(min=48.dp),enabled=ready)
                FilterChip(selected=keyboard.alt,onClick={keyboard.toggleAlt();session.view?.requestFocus()},label={Text("Alt")},modifier=Modifier.heightIn(min=48.dp),enabled=ready)
                RemoteShortcuts.forEach { (label,vk) ->
                    FilledTonalButton(enabled=ready,onClick={keyboard.tap(vk);session.view?.requestFocus()},contentPadding=PaddingValues(horizontal=16.dp),modifier=Modifier.heightIn(min=48.dp).semantics { contentDescription = when (vk) { 0x26 -> "Arrow up"; 0x28 -> "Arrow down"; 0x25 -> "Arrow left"; 0x27 -> "Arrow right"; 0x21 -> "Page up"; 0x22 -> "Page down"; 0x1b -> "Escape"; else -> label } }) {Text(label)}
                }
            }
        }
    }
    if(pinPrompt) AlertDialog(onDismissRequest={pinPrompt=false},title={Text("Pin this desktop?")},text={Text("Android will ask to keep Pane on screen. Home and app switching are restricted while pinned. Use Android's unpin gesture, or open this toolbar with Ctrl+Alt+Home and choose Unpin desktop. Disconnect also unpins Pane.")},confirmButton={TextButton(onClick={pinPrompt=false;activity.pinDesktop()}){Text("Pin desktop")}},dismissButton={TextButton(onClick={pinPrompt=false}){Text("Cancel")}})
    if(confirmExit) AlertDialog(onDismissRequest={confirmExit=false;session.view?.requestFocus()},title={Text("Leave your desktop?")},text={Text("Your apps keep running on the server. Reconnect when you’re ready.")},confirmButton={TextButton(onClick={confirmExit=false;session.disconnect()}){Text("Disconnect")}},dismissButton={TextButton(onClick={confirmExit=false;session.view?.requestFocus()}){Text("Stay")}})
    if(help) AlertDialog(onDismissRequest={help=false;session.view?.requestFocus()},title={Text("A little help")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Pane ${BuildConfig.VERSION_NAME} · Voxl",style=MaterialTheme.typography.bodySmall)
        Text("Keyboard: typing follows your Android layout. Ctrl/Alt shortcuts go to the desktop. Ctrl+Alt+Home opens or hides this toolbar. Volume/media keys go to the host while the desktop has focus. Back opens Pane's controls. Android-reserved shortcuts still belong to the system.")
        Text("Trackpad: click or tap to click, right click and middle click are supported. Two fingers scroll in both directions; supported touchpad pinches zoom. Pointer capture keeps the mouse inside the desktop while the toolbar is hidden. Ctrl+Alt+Home or Back releases it to show controls.")
        Text("Touchscreen: tap to click, hold to right click, double tap and hold to drag. Two fingers scroll; pinch to zoom. In trackpad mode the screen moves the pointer like a laptop.")
        Text("${session.size} · H.264 · Voxl\n$redraws redraws in the last second",style=MaterialTheme.typography.bodySmall)
        Text("Quiet desktop is optional and uses your Android priority Do Not Disturb policy, including its call and alarm exceptions. It stops when Pane loses focus or disconnects. If Android ends Pane unexpectedly, reopen Pane or disable its desktop mode in Android Do Not Disturb. Pin desktop adds Android's screen-pinning protection.",style=MaterialTheme.typography.bodySmall)
        Text("Keyboard requests the Android keyboard with or without a cover. Back hides it first. Uncommitted composition is local; clipboard transfer is best for large passages.",style=MaterialTheme.typography.bodySmall)
        Text("Redraws depend on screen activity; this is not a network latency measurement.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={help=false;session.view?.requestFocus()}){Text("Got it")}})
    val clipboardDismiss = { clipboard=false; session.view?.requestFocus(); Unit }
    if(clipboard) AlertDialog(onDismissRequest=clipboardDismiss,title={Text("Text clipboard")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Transfer text only when you ask. Images and files stay on their device.")
        Text(if (session.remoteClipboard.isEmpty()) "No text received from the desktop yet." else "Desktop text is available to copy.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=ready,onClick={
        val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip=cm.primaryClip
        val text=if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        if (text == null) Toast.makeText(context,"Copy some text on this tablet first.",Toast.LENGTH_SHORT).show()
        else {
            session.sendClipboard(text)
            Toast.makeText(context,if (text.length > 1_048_576) "Sent the first 1 MB of text." else "Text sent to desktop.",Toast.LENGTH_SHORT).show()
            clipboardDismiss()
        }
    }){Text("Send to desktop")}},dismissButton={Column {
        TextButton(enabled=ready && session.remoteClipboard.isNotEmpty(),onClick={
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Desktop",session.remoteClipboard))
            Toast.makeText(context,"Desktop text copied.",Toast.LENGTH_SHORT).show()
            clipboardDismiss()
        }){Text("Copy from desktop")}
        TextButton(onClick=clipboardDismiss){Text("Cancel")}
    }})
}
