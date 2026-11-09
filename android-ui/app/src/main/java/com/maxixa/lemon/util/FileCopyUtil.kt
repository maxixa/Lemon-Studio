package com.maxixa.lemon.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

object FileCopyUtil {
    private const val TAG = "Lemon"

    /** Copy a SAF URI to `<appExternal>/samples/`. Returns absolute path or null. */
    fun copyUriToAppStorage(ctx: Context, uri: Uri): String? {
        return try {
            val dir = ctx.getExternalFilesDir(null)?.resolve("samples") ?: return null
            dir.mkdirs()

            val ext = mimeToExt(ctx.contentResolver.getType(uri))
            val filename = "sample_${System.currentTimeMillis()}$ext"
            val dest = File(dir, filename)

            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return null

            Log.i(TAG, "copied to ${dest.absolutePath} (${dest.length()} bytes)")
            dest.absolutePath
        } catch (t: Throwable) {
            Log.e(TAG, "copyUriToAppStorage failed", t)
            null
        }
    }

    private fun mimeToExt(mime: String?): String = when {
        mime == null -> ".wav"
        mime.contains("mpeg", ignoreCase = true) -> ".mp3"
        mime.contains("flac", ignoreCase = true) -> ".flac"
        mime.contains("ogg", ignoreCase = true) -> ".ogg"
        mime.contains("m4a", ignoreCase = true) -> ".m4a"
        mime.contains("aac", ignoreCase = true) -> ".m4a"
        mime.contains("x-wav", ignoreCase = true) -> ".wav"
        mime.contains("wav", ignoreCase = true) -> ".wav"
        else -> ".wav"
    }
}
