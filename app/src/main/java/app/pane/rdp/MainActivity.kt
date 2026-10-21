@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pane.rdp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

private val Ink = Color(0xFF20212A)
private val Purple = Color(0xFF5652C9)
private val Paper = Color(0xFFF7F6FC)
private val Lime = Color(0xFFD4EA98)
private val Desk = Color(0xFF14151C)
private val Bar = Color(0xFF252631)
private val OnBar = Color(0xFFEFEDF7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialExpressiveTheme(colorScheme = lightColorScheme(
                primary = Purple, onPrimary = Color.White,
                primaryContainer = Color(0xFFE5E1FF), onPrimaryContainer = Color(0xFF28236F),
                secondaryContainer = Lime, onSecondaryContainer = Color(0xFF283713),
                background = Paper, onBackground = Ink,
                surface = Paper, onSurface = Ink, surfaceContainer = Color(0xFFEDEBF5)
            )) {
                val session: RdpSession = viewModel()
                Surface(Modifier.fillMaxSize()) {
                    if (session.phase == Phase.IDLE) Home(session) else Remote(session)
                }
            }
        }
    }
}

@Composable private fun Home(session: RdpSession) {
    var password by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val passwordFocus = remember { FocusRequester() }
    // With the keyboard cover attached, land in the password field: type it and press Enter.
    val hardKeyboard = LocalConfiguration.current.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    LaunchedEffect(hardKeyboard) { if (hardKeyboard) runCatching { passwordFocus.requestFocus() } }
    val connect = {
        session.connect(password)
        if (session.phase != Phase.IDLE) password = ""
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp),verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Purple,shape = RoundedCornerShape(16.dp)) {
                Text("▥",Modifier.padding(horizontal = 13.dp,vertical = 7.dp),color = Color.White,fontSize = 25.sp)
            }
            Text("Pane",Modifier.padding(start = 12.dp),style = MaterialTheme.typography.titleLarge,fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pane.flandolf.me/releases/"))) }) { Text("Builds ↗") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 760.dp
            if (wide) Row(Modifier.fillMaxSize(),horizontalArrangement = Arrangement.spacedBy(40.dp),verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.9f).verticalScroll(rememberScrollState())) { Introduction() }
                Column(Modifier.weight(1.1f).widthIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                    ConnectCard(session,password,{password=it},passwordFocus,advanced,{advanced=!advanced},connect)
                }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Introduction()
                ConnectCard(session,password,{password=it},passwordFocus,advanced,{advanced=!advanced},connect)
            }
        }
        Text("PRIVATE CONNECTION  ·  NATIVE RDP",Modifier.padding(vertical = 18.dp),style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing = 1.8.sp)
    }
}

@Composable private fun Introduction() {
    Surface(shape = RoundedCornerShape(50),color = Lime) {
        Text("YOUR DESKTOP, WITH YOU",Modifier.padding(horizontal = 16.dp,vertical = 9.dp),style = MaterialTheme.typography.labelMedium,letterSpacing = 1.sp)
    }
    Spacer(Modifier.height(24.dp))
    Text("A little closer\nto your desktop.",style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp,lineHeight = 56.sp,letterSpacing = (-2).sp),fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(18.dp))
    Text("Your Linux workspace. A quiet interface.\nRoom to get something done.",style = MaterialTheme.typography.bodyLarge,color = MaterialTheme.colorScheme.onSurfaceVariant,lineHeight = 26.sp)
    Spacer(Modifier.height(32.dp))
    Surface(shape = RoundedCornerShape(28.dp),color = Color(0xFFEAE7F5)) {
        Column(Modifier.padding(24.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50),color = Purple) { Box(Modifier.size(10.dp)) }
                Text("  Tablet  →  Tailscale  →  XFCE",style = MaterialTheme.typography.titleSmall)
            }
            Text("Built for your ARM VPS",style = MaterialTheme.typography.titleLarge,fontWeight = FontWeight.SemiBold)
            Text("Modest desktop sizes and fewer effects keep more of those four cores free for your apps.",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable private fun ConnectCard(session: RdpSession,password: String,onPassword: (String)->Unit,passwordFocus: FocusRequester,advanced: Boolean,onAdvanced: ()->Unit,connect: ()->Unit) {
    Surface(shape = RoundedCornerShape(32.dp),color = Color.White) {
        Column(Modifier.padding(24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your workspace",style = MaterialTheme.typography.headlineSmall,fontWeight = FontWeight.SemiBold)
                    Text("armvps · Ubuntu / XFCE",style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(50),color = Color(0xFFF0EEF8)) { Text("RDP",Modifier.padding(12.dp),style = MaterialTheme.typography.labelLarge) }
            }
            OutlinedTextField(value = session.profile.host,onValueChange = {session.profile=session.profile.copy(host=it.trim())},label = {Text("Tailscale address")},supportingText = {Text("Keep Tailscale connected on your tablet.")},singleLine = true,modifier = Modifier.fillMaxWidth(),shape = RoundedCornerShape(16.dp),keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri,imeAction = ImeAction.Next))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = session.profile.username,onValueChange = {session.profile=session.profile.copy(username=it)},label = {Text("Username")},singleLine = true,modifier = Modifier.weight(1f),shape = RoundedCornerShape(16.dp),keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(value = password,onValueChange = onPassword,label = {Text("Password")},singleLine = true,modifier = Modifier.weight(1f).focusRequester(passwordFocus),shape = RoundedCornerShape(16.dp),visualTransformation = PasswordVisualTransformation(),keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,imeAction = ImeAction.Go),keyboardActions = KeyboardActions(onGo={connect()}))
            }
            Text("Password stays in this session. Never saved.",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Make room. Keep it light.",style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Quality.entries.forEachIndexed { index,quality ->
                    SegmentedButton(selected = session.profile.quality == quality,onClick = {session.profile=session.profile.copy(quality=quality)},shape = SegmentedButtonDefaults.itemShape(index,Quality.entries.size)) { Text(quality.label) }
                }
            }
            Text("${session.size}  ·  ${session.profile.quality.detail}",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (session.message.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.errorContainer,shape = RoundedCornerShape(16.dp)) {
                Text(session.message,Modifier.padding(16.dp),style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onErrorContainer)
            }
            session.profile.error()?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
            Button(onClick = connect,enabled = password.isNotEmpty() && session.profile.error()==null,modifier = Modifier.fillMaxWidth().height(64.dp),shapes = ButtonDefaults.shapes()) {
                Text("Open desktop",style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward,contentDescription = null)
            }
            TextButton(onClick = onAdvanced,modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if(advanced) "Hide connection details" else "Connection details") }
            if (advanced) {
                HorizontalDivider()
                Text("Verified server",style = MaterialTheme.typography.titleMedium)
                Text("TLS certificate pinned to this VPS. A changed certificate blocks the connection.",style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = session.profile.fingerprint,onValueChange = {session.profile=session.profile.copy(fingerprint=it)},label = {Text("SHA-256 certificate fingerprint")},textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),modifier = Modifier.fillMaxWidth(),shape = RoundedCornerShape(16.dp))
                Text("Port 3389 · TLS · 24-bit colour\nAudio and video codecs off · Text clipboard on demand",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun Remote(session: RdpSession) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var clipboard by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var redraws by remember { mutableIntStateOf(0) }
    val ready = session.phase == Phase.CONNECTED
    // The OnePlus keyboard cover and Bluetooth keyboards both report as a hard keyboard.
    val hardKeyboard = LocalConfiguration.current.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    LaunchedEffect(session) { while(true) { delay(1000); redraws=session.paints.getAndSet(0) } }
    LaunchedEffect(ready) { if (ready) { delay(2500); session.toolbar=false } }
    LaunchedEffect(session.toolbar) { if (!session.toolbar) session.view?.requestFocus() }
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insets = WindowCompat.getInsetsController(activity.window,activity.window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insets.show(WindowInsetsCompat.Type.systemBars())
            session.key(0xa2,false); session.key(0xa4,false)
        }
    }
    BackHandler { if (session.toolbar) confirmExit=true else session.toolbar=true }
    Column(Modifier.fillMaxSize().background(Desk).windowInsetsPadding(WindowInsets.displayCutout).imePadding()) {
        // The desktop owns the whole screen; controls float over it and hide while you work.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory={DesktopView(it,session)},modifier=Modifier.fillMaxSize())
            if (!ready) Surface(Modifier.align(Alignment.Center),shape=RoundedCornerShape(32.dp),color=Color(0xFFE5E1FF)) {
                Column(Modifier.padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    LoadingIndicator()
                    Text(if(session.phase==Phase.DISCONNECTING) "Closing session…" else "Opening your desktop…",style=MaterialTheme.typography.titleMedium)
                    Text("${session.size} · TLS",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={session.disconnect()},enabled=session.phase!=Phase.DISCONNECTING) {Text("Cancel")}
                }
            }
            androidx.compose.animation.AnimatedVisibility(session.toolbar,Modifier.align(Alignment.TopCenter).padding(top=12.dp),enter=slideInVertically{-it}+fadeIn(),exit=slideOutVertically{-it}+fadeOut()) {
                Surface(shape=RoundedCornerShape(50),color=Bar,contentColor=OnBar,shadowElevation=8.dp) {
                    Row(Modifier.padding(start=20.dp,end=6.dp).height(56.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=RoundedCornerShape(50),color=if(ready) Lime else Color(0xFF8C8AA0)) { Box(Modifier.size(8.dp)) }
                        Column(Modifier.padding(start=12.dp,end=12.dp)) {
                            Text(session.profile.host,style=MaterialTheme.typography.labelLarge,maxLines=1)
                            Text("${session.size}  ·  Ctrl+Alt+Home",style=MaterialTheme.typography.labelSmall,color=OnBar.copy(alpha=0.6f),maxLines=1)
                        }
                        VerticalDivider(Modifier.height(28.dp).padding(horizontal=4.dp),color=OnBar.copy(alpha=0.15f))
                        TextButton(enabled=ready,onClick={session.toggleTrackpad()}) { Text(if(session.trackpad) "Touch: trackpad" else "Touch: direct",color=Lime) }
                        if (!hardKeyboard) TextButton(enabled=ready,onClick={session.toolbar=false;session.view?.keyboard()}) { Text("Keyboard",color=OnBar) }
                        TextButton(enabled=ready,onClick={clipboard=true}) { Text("Clipboard",color=OnBar) }
                        TextButton(enabled=ready,onClick={session.view?.resetZoom()}) { Text("Fit",color=OnBar) }
                        IconButton(onClick={help=true}) { Icon(Icons.Default.Info,"Controls and session info") }
                        IconButton(onClick={confirmExit=true}) { Icon(Icons.Default.Close,"Disconnect") }
                        FilledTonalIconButton(enabled=ready,onClick={session.toolbar=false}) { Icon(Icons.Default.KeyboardArrowUp,"Hide toolbar") }
                    }
                }
            }
            // A small handle keeps the toolbar reachable by touch without covering much of the remote panel.
            if (!session.toolbar) Box(Modifier.align(Alignment.TopCenter).size(96.dp,20.dp).clickable(onClickLabel="Show toolbar"){session.toolbar=true},contentAlignment=Alignment.Center) {
                Surface(shape=RoundedCornerShape(50),color=OnBar.copy(alpha=0.45f)) { Box(Modifier.size(56.dp,5.dp)) }
            }
        }
        // On-screen modifiers only matter without a physical keyboard.
        if (!hardKeyboard) Surface(color=Bar) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                FilterChip(selected=ctrl,onClick={ctrl=!ctrl;session.key(0xa2,ctrl);session.view?.requestFocus()},label={Text("Ctrl")},enabled=ready)
                FilterChip(selected=alt,onClick={alt=!alt;session.key(0xa4,alt);session.view?.requestFocus()},label={Text("Alt")},enabled=ready)
                listOf("Esc" to 0x1b,"Tab" to 0x09,"↑" to 0x26,"↓" to 0x28,"←" to 0x25,"→" to 0x27,"Home" to 0x24,"End" to 0x23,"PgUp" to 0x21,"PgDn" to 0x22).forEach { (label,vk) ->
                    FilledTonalButton(enabled=ready,onClick={session.key(vk,true);session.key(vk,false);session.view?.requestFocus()},contentPadding=PaddingValues(horizontal=16.dp),modifier=Modifier.height(40.dp)) {Text(label)}
                }
            }
        }
    }
    if(confirmExit) AlertDialog(onDismissRequest={confirmExit=false},title={Text("Leave your desktop?")},text={Text("Your apps keep running on the VPS. Reconnect when you’re ready.")},confirmButton={TextButton(onClick={confirmExit=false;session.disconnect()}){Text("Disconnect")}},dismissButton={TextButton(onClick={confirmExit=false}){Text("Stay")}})
    if(help) AlertDialog(onDismissRequest={help=false},title={Text("A little help")},text={Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Keyboard cover: every key goes to the desktop. Ctrl+Alt+Home shows or hides this toolbar. Android keeps a few system shortcuts, such as Meta combinations.")
        Text("Trackpad: click or tap to click, two-finger click for right click, two fingers scroll in both directions.")
        Text("Touchscreen: tap to click, hold to right click, double tap and hold to drag. Two fingers scroll; pinch to zoom. In trackpad mode the screen moves the pointer like a laptop.")
        Text("${session.size} · 24-bit · TLS\n$redraws redraws in the last second",style=MaterialTheme.typography.bodySmall)
        Text("Redraws depend on screen activity; this is not a network latency measurement.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={help=false}){Text("Got it")}})
    if(clipboard) AlertDialog(onDismissRequest={clipboard=false},title={Text("Clipboard")},text={Text("Clipboard access happens only when you ask. Images and files aren’t transferred.")},confirmButton={TextButton(onClick={
        val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text=cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        session.sendClipboard(text); clipboard=false;session.view?.requestFocus()
    }){Text("Send to desktop")}},dismissButton={TextButton(enabled=session.remoteClipboard.isNotEmpty(),onClick={
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Desktop",session.remoteClipboard))
        clipboard=false
    }){Text("Copy from desktop")}})
}
