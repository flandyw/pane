package app.pane.rdp

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Own one temporary rule, leaving the user's other DND modes and policy alone. */
internal class DesktopFocus(private val context: Context) {
    private val prefs = context.getSharedPreferences("desktop_focus", 0)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val condition = Uri.parse("condition://${context.packageName}/desktop")
    var enabled by mutableStateOf(prefs.getBoolean("enabled", false)); private set
    var active by mutableStateOf(false); private set
    private var foreground = false
    private var connected = false
    private var ruleId = prefs.getString("rule", null)
    val supported get() = Build.VERSION.SDK_INT >= 29
    val permitted get() = notifications.isNotificationPolicyAccessGranted

    // shortcut: crash recovery waits for next launch/manual DND change; add a condition provider if automatic crash recovery is needed.
    init { refresh() }
    fun updateEnabled(value: Boolean) {
        check(prefs.edit().putBoolean("enabled", value).commit())
        enabled = value
        refresh()
    }
    fun foreground(value: Boolean) { foreground = value; refresh() }
    fun connected(value: Boolean) { connected = value; refresh() }
    fun refresh() {
        if (Build.VERSION.SDK_INT < 29 || !permitted) { active = false; return }
        try {
            val wanted = quietDesktopActive(enabled, connected, foreground)
            val existing = ruleId?.let { notifications.getAutomaticZenRule(it) }
            if (wanted && existing == null) {
                val rule = AutomaticZenRule("Pane desktop", null, ComponentName(context, MainActivity::class.java),
                    condition, null, NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
                val id = checkNotNull(notifications.addAutomaticZenRule(rule))
                if (!prefs.edit().putString("rule", id).commit()) {
                    notifications.removeAutomaticZenRule(id)
                    error("Could not save the quiet desktop rule")
                }
                ruleId = id
            }
            ruleId?.let { id ->
                notifications.setAutomaticZenRuleState(id, Condition(condition, "Pane desktop connected",
                    if (wanted) Condition.STATE_TRUE else Condition.STATE_FALSE))
            }
            active = wanted && ruleId?.let { notifications.getAutomaticZenRule(it)?.isEnabled } == true
        } catch (_: Exception) { active = false }
    }
}

internal fun quietDesktopActive(enabled: Boolean, connected: Boolean, foreground: Boolean) = enabled && connected && foreground
