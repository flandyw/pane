package app.pane.rdp

import java.net.URI

data class Host(val name: String, val url: String)

object Hosts {
    /** "mac.ts.net:6901" or "https://mac.ts.net" -> "wss://mac.ts.net:6901/ws". Null if unusable. */
    fun normalize(input: String): String? {
        var t = input.trim()
        if (!t.contains("://")) t = "wss://$t"
        val u = runCatching { URI(t) }.getOrNull() ?: return null
        // A password belongs in the Keystore, never in a stored, displayed URL.
        if (u.host.isNullOrEmpty() || u.rawUserInfo != null || u.rawFragment != null || u.port !in -1..65535 || u.port == 0) return null
        val scheme = when (u.scheme?.lowercase()) {
            "https", "wss" -> "wss"
            "http", "ws" -> "ws"
            else -> return null
        }
        val path = if (u.rawPath.isNullOrEmpty() || u.rawPath == "/") "/ws" else u.rawPath
        val port = if (u.port >= 0) ":${u.port}" else ""
        val query = u.rawQuery?.let { "?$it" } ?: ""
        return "$scheme://${u.host}$port$path$query"
    }

}
