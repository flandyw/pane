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
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class Release(val code: Long, val name: String, val url: String, val sha256: String, val size: Long) {
    companion object {
        const val ORIGIN = "https://pane.flandolf.me/releases/"
        fun parse(json: String, current: Long): Release? {
            val feed = JSONObject(json)
            val code = feed.getLong("version_code")
            if (code <= current) return null
            val assets = feed.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name").endsWith(".apk") }
            val release = Release(code, feed.getString("version_name"), apk.getString("browser_download_url"),
                apk.getString("digest").removePrefix("sha256:"), apk.getLong("size"))
            val uri = URI(release.url)
            require(uri.scheme == "https" && uri.host == "pane.flandolf.me" && uri.port in listOf(-1, 443) &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.startsWith("/releases/") && !uri.rawPath.contains('%') && !uri.rawPath.contains(".."))
            require(release.sha256.matches(Regex("[0-9a-f]{64}")))
            require(release.size in 1..200_000_000)
            return release
        }
    }
}

enum class UpdateStage { IDLE, CHECKING, DOWNLOADING, VERIFYING, READY, INSTALLING, CONFIRMATION, ERROR }
data class UpdateState(val stage: UpdateStage = UpdateStage.IDLE, val version: String = "", val bytes: Long = 0,
    val total: Long = 0, val detail: String = "")

/** Downloads from our release service and requests silent self-installation while idle. */
object Updater {
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean()
    var state by mutableStateOf(UpdateState()); private set
    var celebratedVersion by mutableStateOf<String?>(null); private set
    @Volatile var foreground = false
    @Volatile var sessionActive = false
    @Volatile private var release: Release? = null
    private var confirmation: Intent? = null
    private var launchChecked = false
    private fun apk(context: Context) = File(context.cacheDir, "update.apk")

    fun dismissCelebration() { celebratedVersion = null }
    fun check(context: Context) {
        val app = context.applicationContext
        if (!launchChecked) {
            launchChecked = true
            val prefs = app.getSharedPreferences("updates", 0)
            val previous = prefs.getInt("seen_version", 0)
            @Suppress("DEPRECATION") val info = app.packageManager.getPackageInfo(app.packageName, 0)
            if (shouldCelebrate(previous, BuildConfig.VERSION_CODE, info.lastUpdateTime > info.firstInstallTime))
                celebratedVersion = BuildConfig.VERSION_NAME
            prefs.edit().putInt("seen_version", BuildConfig.VERSION_CODE).apply()
        }
        if (release != null || !busy.compareAndSet(false, true)) return
        state = UpdateState(UpdateStage.CHECKING)
        worker.execute {
            try {
                val connection = get(Release.ORIGIN + "latest.json")
                val json = try { connection.inputStream.use { it.reader().readText() } } finally { connection.disconnect() }
                val next = Release.parse(json, BuildConfig.VERSION_CODE.toLong())
                if (next == null) { apk(app).delete(); state = UpdateState(); return@execute }
                state = UpdateState(UpdateStage.VERIFYING, next.name)
                if (sha256(apk(app)) != next.sha256) download(app, next)
                verify(app, apk(app), next)
                release = next
                state = UpdateState(UpdateStage.READY, next.name)
            } catch (e: Exception) {
                Log.w("PaneUpdater", "Update check failed", e)
                state = UpdateState(UpdateStage.ERROR, detail = "Couldn't download the update. Check your connection and try again.")
            } finally {
                busy.set(false)
                if (!foreground && !sessionActive) install(app, background = true)
            }
        }
    }
    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000
        instanceFollowRedirects = false
        if (responseCode != 200) { disconnect(); error("Unexpected HTTP response") }
    }
    private fun sha256(file: File): String = if (!file.exists()) "" else file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(input, digest).use { stream -> val buffer = ByteArray(64 * 1024); while (stream.read(buffer) >= 0) Unit }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun verify(context: Context, file: File, next: Release) {
        check(file.length() == next.size)
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
        check(info?.packageName == context.packageName && PackageInfoCompat.getLongVersionCode(info) == next.code)
        @Suppress("DEPRECATION") val current = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION") check(!current.signatures.isNullOrEmpty() && info.signatures?.toSet() == current.signatures?.toSet())
    }
    private fun download(context: Context, next: Release) {
        val part = File(context.cacheDir, "update.apk.part")
        val connection = get(next.url)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            DigestInputStream(connection.inputStream, digest).use { input ->
                part.outputStream().use { out -> copyUpdate(input, out, next.size) { count ->
                    state = UpdateState(UpdateStage.DOWNLOADING, next.name, count, next.size)
                } }
            }
            state = UpdateState(UpdateStage.VERIFYING, next.name, next.size, next.size)
            check(digest.digest().joinToString("") { "%02x".format(it) } == next.sha256)
            verify(context, part, next)
            check(part.renameTo(apk(context)))
        } finally { connection.disconnect(); part.delete() }
    }

    fun install(context: Context, background: Boolean) {
        val app = context.applicationContext
        if (state.stage == UpdateStage.INSTALLING || (background && state.stage == UpdateStage.CONFIRMATION)) return
        if (release == null || sessionActive || (background && (foreground || Build.VERSION.SDK_INT < 31)) || !busy.compareAndSet(false, true)) return
        if (!app.packageManager.canRequestPackageInstalls()) { busy.set(false); return }
        if (!background && confirmation != null) {
            try { app.startActivity(confirmation!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); state = state.copy(stage = UpdateStage.INSTALLING) }
            catch (_: Exception) { state = state.copy(stage = UpdateStage.ERROR, detail = "Couldn't open Android's installer. Try again.") }
            finally { busy.set(false) }
            return
        }
        worker.execute {
            val installer = app.packageManager.packageInstaller
            var id = -1
            try {
                if (sessionActive || (background && foreground)) return@execute
                val next = release ?: return@execute
                check(sha256(apk(app)) == next.sha256)
                verify(app, apk(app), next)
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                installer.mySessions.filter { it.appPackageName == app.packageName }.forEach { installer.abandonSession(it.sessionId) }
                id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("pane.apk", 0, apk(app).length()).use { out ->
                        apk(app).inputStream().use { it.copyTo(out) }; session.fsync(out)
                    }
                    if (sessionActive || (background && foreground)) { session.abandon(); state = UpdateState(UpdateStage.READY, next.name); return@execute }
                    state = UpdateState(UpdateStage.INSTALLING, next.name)
                    val result = Intent(app, UpdateReceiver::class.java).putExtra("interactive", !background).putExtra("session", id)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(app, id, result, flags).intentSender)
                }
            } catch (e: Exception) {
                if (id >= 0) runCatching { installer.abandonSession(id) }
                Log.w("PaneUpdater", "Update install failed", e)
                state = state.copy(stage = UpdateStage.ERROR, detail = "Couldn't install the update. Try again.")
            } finally { busy.set(false) }
        }
    }
    internal fun result(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                state = state.copy(stage = UpdateStage.CONFIRMATION)
                if (foreground && !sessionActive && intent.getBooleanExtra("interactive", false) && confirmation != null)
                    runCatching { context.startActivity(confirmation!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> { confirmation = null; release = null; state = UpdateState() }
            else -> {
                confirmation = null
                state = state.copy(stage = UpdateStage.ERROR, detail = if (status == PackageInstaller.STATUS_FAILURE_ABORTED)
                    "Update paused. Your current version is still installed." else "Android couldn't install the update. Try again.")
            }
        }
    }
}
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Updater.result(context, intent) }
}

internal fun shouldCelebrate(previous: Int, current: Int, updatedInstall: Boolean) =
    previous in 1 until current || (previous == 0 && updatedInstall)

/** A server must never be able to write beyond its advertised download size. */
internal fun copyUpdate(input: InputStream, out: OutputStream, expected: Long, progress: (Long) -> Unit) {
    require(expected > 0)
    val buffer = ByteArray(64 * 1024)
    var count = 0L
    var percent = -1L
    progress(0)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        check(count + read <= expected) { "Update exceeds published size" }
        out.write(buffer, 0, read); count += read
        val next = count * 100 / expected
        if (next != percent) { progress(count); percent = next }
    }
    check(count == expected) { "Incomplete update" }
}
