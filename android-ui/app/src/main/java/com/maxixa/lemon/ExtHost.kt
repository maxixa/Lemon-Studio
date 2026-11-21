package com.maxixa.lemon

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object ExtHost {
    private const val TAG = "ExtHost"
    init { try { System.loadLibrary("lemon_engine_ffi") } catch (_: Throwable) {} }

    @JvmStatic private external fun nativeExtList(): String
    @JvmStatic private external fun nativeExtRun(id: String, kind: String, input: String): String
    @JvmStatic private external fun nativeExtRunSong(id: String, input: String): String

    data class Param(val id: String, val name: String, val min: Float, val max: Float, val default: Float, val unit: String)
    data class Info(
        val id: String, val name: String, val description: String, val version: String,
        val kind: String, val params: List<Param>, val needs: Int, val capabilities: Int,
        val primaryAction: String,
    )
    data class Note(
        val startTick: Int, val lengthTicks: Int, val pitch: Int,
        val velocity: Int, val channel: Int, val flags: Int = 0,
    )

    fun list(): List<Info> = try {
        val raw = nativeExtList()
        if (raw.isNullOrBlank()) emptyList()
        else { val a = JSONArray(raw); (0 until a.length()).map { parseInfo(a.getJSONObject(it)) } }
    } catch (t: Throwable) { Log.e(TAG, "list() failed", t); emptyList() }

    fun runNoteEditor(id: String, notes: List<Note>, params: Map<String, Float>): List<Note>? = try {
        val input = JSONObject().apply {
            put("notes", JSONArray().apply {
                notes.forEach { n -> put(JSONObject().apply {
                    put("startTick", n.startTick); put("lengthTicks", n.lengthTicks)
                    put("pitch", n.pitch); put("velocity", n.velocity)
                    put("channel", n.channel); put("flags", n.flags)
                }) }
            })
            put("params", JSONObject().apply { params.forEach { (k, v) -> put(k, v) } })
        }
        val out = JSONObject(nativeExtRun(id, "editor", input.toString()))
        if (!out.optBoolean("ok", false)) {
            Log.e(TAG, "runNoteEditor($id) failed: ${out.optString("error", "unknown")}")
            return null
        }
        val arr = out.optJSONArray("notes") ?: return null
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Note(o.getInt("startTick"), o.getInt("lengthTicks"), o.getInt("pitch"),
                 o.getInt("velocity"), o.optInt("channel", 0), o.optInt("flags", 0))
        }
    } catch (t: Throwable) { Log.e(TAG, "runNoteEditor($id) threw", t); null }

    /** Run a song generator. Returns the raw JSON sketch string, or null on error.
     *  Kotlin-side application to engine is a separate follow-up. */
    fun runSongGenerator(id: String, params: Map<String, Float>): String? = try {
        val input = JSONObject().apply {
            put("params", JSONObject().apply {
                params.forEach { (k, v) -> put(k, v) }
            })
        }
        val out = JSONObject(nativeExtRunSong(id, input.toString()))
        if (!out.optBoolean("ok", false)) {
            Log.e(TAG, "runSongGenerator($id) failed: ${out.optString("error", "unknown")}")
            null
        } else {
            out.optJSONObject("sketch")?.toString()
        }
    } catch (t: Throwable) {
        Log.e(TAG, "runSongGenerator($id) threw", t); null
    }

    private fun parseInfo(o: JSONObject): Info {
        val pa = o.optJSONArray("params") ?: JSONArray()
        val ui = o.optJSONObject("ui") ?: JSONObject()
        return Info(
            id = o.getString("id"), name = o.getString("name"),
            description = o.optString("description", ""), version = o.optString("version", "0.1.0"),
            kind = o.getString("kind"),
            params = (0 until pa.length()).map { i ->
                val p = pa.getJSONObject(i)
                Param(p.getString("id"), p.getString("name"),
                      p.getDouble("min").toFloat(), p.getDouble("max").toFloat(),
                      p.getDouble("default").toFloat(), p.optString("unit", ""))
            },
            needs = o.optInt("needs", 0), capabilities = o.optInt("capabilities", 0),
            primaryAction = ui.optString("primaryAction", "Apply"),
        )
    }
}
