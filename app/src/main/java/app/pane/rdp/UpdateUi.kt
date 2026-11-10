package app.pane.rdp

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.random.Random

@Composable
internal fun UpdateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val update = Updater.state
    val celebration = Updater.celebratedVersion
    if (update.stage == UpdateStage.IDLE && celebration == null) return
    val active = update.stage in listOf(UpdateStage.CHECKING, UpdateStage.VERIFYING, UpdateStage.INSTALLING)
    val title = when (update.stage) {
        UpdateStage.CHECKING -> "Checking for updates"
        UpdateStage.DOWNLOADING -> "A little upgrade is on its way"
        UpdateStage.VERIFYING -> "Making sure everything checks out"
        UpdateStage.READY -> "Pane ${update.version} is ready"
        UpdateStage.INSTALLING -> "Installing Pane ${update.version}"
        UpdateStage.CONFIRMATION -> "One last step from Android"
        UpdateStage.ERROR -> "Let's try that update again"
        UpdateStage.IDLE -> "You're updated. Make yourself at home."
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (active) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(when (update.stage) {
                        UpdateStage.DOWNLOADING -> "Pane ${update.version} · ${String.format(Locale.getDefault(), "%.1f / %.1f MB", update.bytes / 1_000_000.0, update.total / 1_000_000.0)}"
                        UpdateStage.READY -> "Installs quietly when you leave Pane. Your desktop won't be interrupted."
                        UpdateStage.CONFIRMATION -> "Android needs your confirmation to finish."
                        UpdateStage.ERROR -> update.detail
                        UpdateStage.IDLE -> "Pane $celebration · You're on the latest installed build."
                        UpdateStage.INSTALLING -> "Pane will close as Android replaces it."
                        else -> "Secure updates from pane.flandolf.me"
                    }, style = MaterialTheme.typography.bodySmall)
                }
                if (celebration != null && update.stage == UpdateStage.IDLE)
                    TextButton(onClick = Updater::dismissCelebration) { Text("Got it") }
            }
            if (update.stage == UpdateStage.DOWNLOADING && update.total > 0) {
                LinearProgressIndicator(progress = { (update.bytes.toFloat() / update.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${update.bytes * 100 / update.total}%", style = MaterialTheme.typography.labelMedium)
            }
            if (update.stage in listOf(UpdateStage.READY, UpdateStage.CONFIRMATION) || update.stage == UpdateStage.ERROR && update.version.isNotEmpty()) {
                val allowed = context.packageManager.canRequestPackageInstalls()
                FilledTonalButton(onClick = {
                    if (!allowed) context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    else Updater.install(context, background = false)
                }) { Text(if (!allowed) "Enable quiet updates" else if (update.stage == UpdateStage.CONFIRMATION) "Finish update" else "Update now") }
                if (!allowed) Text("Allow Pane under Install unknown apps once. Android may still ask for confirmation.", style = MaterialTheme.typography.bodySmall)
            } else if (update.stage == UpdateStage.ERROR) {
                TextButton(onClick = { Updater.check(context) }) { Text("Try again") }
            }
        }
    }
}

/** A short decorative burst; Compose's animation clock respects Android's reduced-motion setting. */
@Composable
internal fun UpdateConfetti(modifier: Modifier = Modifier) {
    val version = Updater.celebratedVersion ?: return
    val progress = remember(version) { Animatable(0f) }
    val pieces = remember(version) { val random = Random(42); List(60) { FloatArray(5) { random.nextFloat() } } }
    val colors = listOf(Color(0xFF5652C9), Color(0xFFD4EA98), Color(0xFFFFCB68), Color(0xFFE7A2CD))
    LaunchedEffect(version) { progress.animateTo(1f, tween(2600, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier.semantics { hideFromAccessibility() }) {
        val t = progress.value
        pieces.forEachIndexed { index, piece ->
            val x = piece[0] * size.width + (piece[1] - 0.5f) * 200.dp.toPx() * t
            val y = -100.dp.toPx() * piece[2] + size.height * (0.3f * t + t * t) * (0.8f + piece[3])
            rotate(piece[4] * 360 + t * 540, Offset(x, y)) {
                drawRect(colors[index % colors.size], Offset(x, y), Size(8.dp.toPx(), 4.dp.toPx()), alpha = (1f - t).coerceIn(0f, 1f))
            }
        }
    }
}
