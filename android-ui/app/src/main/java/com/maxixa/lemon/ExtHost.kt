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
    @JvmStatic private external fun nativeExtRunGen(id: String, input: String): String

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

    /** Generate + apply in one call. Returns Summary or null on failure. */
    fun generateAndApply(handle: Long, generatorId: String, params: Map<String, Float>)
        : com.maxixa.lemon.ext.SongApplier.Summary? {
        val sketchJson = runSongGenerator(generatorId, params) ?: return null
        return com.maxixa.lemon.ext.SongApplier.apply(handle, sketchJson)
    }

    data class TargetRange(val trackIdx: Int, val patternIdx: Int, val startTick: Int, val lengthTicks: Int)
    data class ReferenceTrack(
        val trackIdx: Int,
        val patternIdx: Int,
        val trackName: String,
        val notes: List<Note>,
        val instrumentCategory: String? = null,
    )
    data class SecondaryResult(
        val kind: String,               // "existing" | "new" | "unknown"
        val idx: Int?,                  // populated when kind == "existing"
        val name: String?,              // populated when kind == "new"
        val instrumentCategory: String?,// populated when kind == "new"
        val patternIdx: Int,
        val notes: List<Note>,
    )
    data class NoteGenResult(
        val primary: List<Note>,
        val secondary: List<SecondaryResult>,
    )

    /** Run a NoteGenerator with a reference track. Returns null on failure. */
    fun runNoteGenerator(
        id: String,
        target: TargetRange,
        references: List<ReferenceTrack>,
        params: Map<String, Float>,
        scale: Pair<Int, String>? = null,   // (root 0-11, mode name)
        seed: Long = 42L,
        ppq: Int = 96,
        tempo: Double = 120.0,
    ): NoteGenResult? = try {
        val input = JSONObject().apply {
            put("ppq", ppq)
            put("tempo", tempo)
            put("seed", seed)
            put("patternLen", target.lengthTicks)
            put("target", JSONObject().apply {
                put("trackIdx", target.trackIdx)
                put("patternIdx", target.patternIdx)
                put("startTick", target.startTick)
                put("lengthTicks", target.lengthTicks)
            })
            put("references", JSONArray().apply {
                references.forEach { r ->
                    put(JSONObject().apply {
                        put("trackIdx", r.trackIdx)
                        put("patternIdx", r.patternIdx)
                        put("trackName", r.trackName)
                        r.instrumentCategory?.let { put("instrumentCategory", it) }
                        put("notes", JSONArray().apply {
                            r.notes.forEach { n ->
                                put(JSONObject().apply {
                                    put("startTick", n.startTick)
                                    put("lengthTicks", n.lengthTicks)
                                    put("pitch", n.pitch)
                                    put("velocity", n.velocity)
                                    put("channel", n.channel)
                                    put("flags", n.flags)
                                })
                            }
                        })
                    })
                }
            })
            put("heldNotes", JSONArray())
            scale?.let {
                put("scale", JSONObject().apply {
                    put("root", it.first)
                    put("mode", it.second)
                })
            }
            put("params", JSONObject().apply {
                params.forEach { (k, v) -> put(k, v) }
            })
        }

        val out = JSONObject(nativeExtRunGen(id, input.toString()))
        if (!out.optBoolean("ok", false)) {
            Log.e(TAG, "runNoteGenerator($id) failed: ${out.optString("error", "unknown")}")
            null
        } else {
            out.optJSONObject("output")?.let { output ->
                val primary = parseNoteArray(output.optJSONArray("primary") ?: JSONArray())
                val secondaryArr = output.optJSONArray("secondary") ?: JSONArray()
                val secondary = (0 until secondaryArr.length()).map { i ->
                    val obj = secondaryArr.getJSONObject(i)
                    val tgt = obj.optJSONObject("target") ?: JSONObject()
                    SecondaryResult(
                        kind = tgt.optString("kind", "unknown"),
                        idx = if (tgt.has("idx")) tgt.getInt("idx") else null,
                        name = tgt.optString("name", "").ifEmpty { null },
                        instrumentCategory = tgt.optJSONObject("instrument")?.optString("category"),
                        patternIdx = obj.optInt("patternIdx", 0),
                        notes = parseNoteArray(obj.optJSONArray("notes") ?: JSONArray()),
                    )
                }
                NoteGenResult(primary, secondary)
            }
        }
    } catch (t: Throwable) {
        Log.e(TAG, "runNoteGenerator($id) threw", t); null
    }

    private fun parseNoteArray(arr: JSONArray): List<Note> = (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        Note(
            startTick = o.getInt("startTick"),
            lengthTicks = o.getInt("lengthTicks"),
            pitch = o.getInt("pitch"),
            velocity = o.getInt("velocity"),
            channel = o.optInt("channel", 0),
            flags = o.optInt("flags", 0),
        )
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
