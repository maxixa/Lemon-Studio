package com.maxixa.lemon.state

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

data class RecentProject(
    val uri: String,
    val name: String,
    val timestamp: Long,
)

class RecentProjectsStore(private val ctx: Context) {
    private val tag = "Lemon"
    private val prefs = ctx.getSharedPreferences("lemon-recent", Context.MODE_PRIVATE)
    private val KEY_RECENT = "recent"
    private val KEY_LAST_SAVE = "last_save_uri"
    private val KEY_LAST_PROJECT = "last_project_uri"
    private val MAX_RECENT = 5

    /** Load recent list from prefs. */
    fun load(): List<RecentProject> {
        val raw = prefs.getString(KEY_RECENT, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size != 3) return@mapNotNull null
            RecentProject(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
        }
    }

    /** Persist a newly loaded/saved project as most recent. */
    fun push(uri: Uri, name: String) {
        val existing = load().filter { it.uri != uri.toString() }
        val entry = RecentProject(uri.toString(), name, System.currentTimeMillis())
        val updated = (listOf(entry) + existing).take(MAX_RECENT)
        val serialized = updated.joinToString("\n") { "${it.uri}|${it.name}|${it.timestamp}" }
        prefs.edit().putString(KEY_RECENT, serialized).apply()
        persistPermission(uri)
    }

    /** Remove a stale entry (e.g. permission lost). */
    fun remove(uri: String) {
        val updated = load().filter { it.uri != uri }
        val serialized = updated.joinToString("\n") { "${it.uri}|${it.name}|${it.timestamp}" }
        prefs.edit().putString(KEY_RECENT, serialized).apply()
    }

    var lastSaveUri: String
        get() = prefs.getString(KEY_LAST_SAVE, "") ?: ""
        set(v) { prefs.edit().putString(KEY_LAST_SAVE, v).apply() }

    var lastProjectUri: String
        get() = prefs.getString(KEY_LAST_PROJECT, "") ?: ""
        set(v) { prefs.edit().putString(KEY_LAST_PROJECT, v).apply() }

    private fun persistPermission(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            Log.i(tag, "persisted permission for $uri")
        } catch (t: SecurityException) {
            Log.w(tag, "persist permission failed: $uri — ${t.message}")
        } catch (t: Throwable) {
            Log.w(tag, "persist permission threw: ${t.message}")
        }
    }
}
