package app.pane.rdp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class Release(val code: Long, val name: String, val url: String, val sha256: String, val size: Long) {
    companion object {
        const val ORIGIN = "https://pane.flandolf.me/releases/"
        /** Returns a newer, well-formed release from the server's latest.json, or null. */
        fun parse(json: String, current: Long): Release? {
            val feed = JSONObject(json)
            val code = feed.getLong("version_code")
            if (code <= current) return null
            val assets = feed.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name").endsWith(".apk") }
            val release = Release(code, feed.getString("version_name"), apk.getString("browser_download_url"),
                apk.getString("digest").removePrefix("sha256:"), apk.getLong("size"))
            // The feed is untrusted input: stay on our HTTPS origin and cap the download.
            require(release.url.startsWith(ORIGIN) && !release.url.contains("..")) { "Unexpected update URL" }
            require(release.sha256.matches(Regex("[0-9a-f]{64}"))) { "Unexpected update digest" }
            require(release.size in 1..200_000_000) { "Unexpected update size" }
            return release
        }
    }
}

/** Downloads verified updates in the background and installs them without a prompt where Android allows. */
object Updater {
    private const val TAG = "PaneUpdater"
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean()
    /** Version name of a downloaded, verified update waiting to install. */
    var ready by mutableStateOf<String?>(null); private set
    var status by mutableStateOf(""); internal set
    @Volatile var foreground = false

    private fun apk(context: Context) = File(context.cacheDir, "update.apk")

    fun check(context: Context) {
        if (ready != null || !busy.compareAndSet(false, true)) return
        val app = context.applicationContext
        worker.execute {
            try {
                val json = get(Release.ORIGIN + "latest.json").inputStream.use { it.reader().readText() }
                val release = Release.parse(json, BuildConfig.VERSION_CODE.toLong())
                if (release == null) { apk(app).delete(); return@execute }
                if (sha256(apk(app)) != release.sha256) download(app, release)
                ready = release.name
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000
        check(responseCode == 200) { "HTTP $responseCode" }
    }

    private fun sha256(file: File) = if (!file.exists()) "" else file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        DigestInputStream(input, digest).use { while (it.read(buffer) >= 0) Unit }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun download(context: Context, release: Release) {
        val file = apk(context)
        val part = File(context.cacheDir, "update.apk.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = DigestInputStream(get(release.url).inputStream, digest).use { input ->
            part.outputStream().use { out -> input.copyTo(out) }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        try {
            check(bytes == release.size && hash == release.sha256) { "Download did not match the published checksum" }
            val info = context.packageManager.getPackageArchiveInfo(part.path, 0)
            check(info?.packageName == context.packageName && PackageInfoCompat.getLongVersionCode(info) == release.code) { "Download is not the expected Pane build" }
            check(part.renameTo(file)) { "Could not store the update" }
        } finally { part.delete() }
    }

    /** Installs the ready update. In the background this only proceeds if Android needs no confirmation. */
    fun install(context: Context, background: Boolean) {
        val app = context.applicationContext
        if (ready == null || !busy.compareAndSet(false, true)) return
        // Silent self-updates need "Install unknown apps" allowed for Pane once.
        if (background && !app.packageManager.canRequestPackageInstalls()) { busy.set(false); return }
        worker.execute {
            try {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("pane.apk", 0, apk(app).length()).use { out ->
                        apk(app).inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val result = Intent(app, UpdateReceiver::class.java)
                    // Mutable so the installer can add its status extras; the intent is explicit.
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(app, id, result, flags).intentSender)
                }
                status = "Installing Pane $ready…"
            } catch (e: Exception) {
                Log.w(TAG, "Update install failed", e)
                status = "Update failed: ${e.message}"
                busy.set(false)
            }
        }
    }

    internal fun finished(message: String) { status = message; busy.set(false) }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android wants confirmation. Show it only while Pane is open; otherwise wait for the user to tap Update.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (Updater.foreground && confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Updater.finished("")
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.finished("Updated.")
            else -> Updater.finished(if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) == PackageInstaller.STATUS_FAILURE_ABORTED) ""
                else "Update failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()}")
        }
    }
}
