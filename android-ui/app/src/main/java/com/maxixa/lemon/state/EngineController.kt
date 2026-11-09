package com.maxixa.lemon.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxixa.lemon.LemonEngineBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class EffectParamMeta(
    val id: Int,
    val name: String,
    val unit: String,
    val group: String,
    val min: Double,
    val max: Double,
    val default: Double,
    val steps: Int,
)


class EngineController {
    private val tag = "Lemon"
    private var handle: Long = 0L

    var isPlaying by mutableStateOf(false);       private set
    /** Exposed for PianoRollBridge (JNI handle access). */
    val handleRef: Long get() = handle
    var isRecording by mutableStateOf(false);     private set
    var isLooping by mutableStateOf(false);       private set
    var metronomeOn by mutableStateOf(false); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var revision by mutableStateOf(0L); private set
    var metronomeSound by mutableStateOf(1); private set
    var metronomeVolumeDb by mutableStateOf(0); private set
    var countInBars by mutableStateOf(0); private set
    var currentPattern by mutableStateOf(1); private set
    var loopRangeStart by mutableStateOf(-1L); private set
    var loopRangeEnd by mutableStateOf(-1L); private set
    var projectLoaded by mutableStateOf(false);   private set
    var positionFrames by mutableStateOf(0L);     private set
    var sampleRate by mutableStateOf(48_000);     private set
    var tracks by mutableStateOf<List<String>>(emptyList()); private set
    var trackPeaks by mutableStateOf<List<FloatArray>>(emptyList()); private set
    var trackColors by mutableStateOf<List<Int>>(emptyList()); private set
    var masterPeak by mutableStateOf(0f); private set
    var bpm by mutableStateOf(130f); private set
    var timeSigNum by mutableStateOf(4); private set
    var timeSigDen by mutableStateOf(4); private set
    var currentArrangement by mutableStateOf(-1); private set

    init {
        handle = try {
            LemonEngineBridge.engineNew().also { Log.i(tag, "engineNew -> $it") }
        } catch (t: Throwable) {
            Log.e(tag, "engineNew threw", t); 0L
        }
    }

    /** Load a project from a filesystem path (e.g. /sdcard/.../test.flp). */
    fun loadProject(path: String): Boolean {
        if (handle == 0L) return false
        if (isPlaying) stop()
        val ok = try { LemonEngineBridge.engineLoadProject(handle, path) }
            catch (t: Throwable) { Log.e(tag, "loadProject threw", t); false }
        projectLoaded = ok
        if (ok) refreshAfterLoad()
        Log.i(tag, "loadProject($path) -> $ok")
        return ok
    }

    /** Load a project from an open file descriptor (SAF picker). */
    fun loadProjectFd(fd: Int): Boolean {
        if (handle == 0L) return false
        if (isPlaying) stop()
        val ok = try { LemonEngineBridge.engineLoadProjectFd(handle, fd) }
            catch (t: Throwable) { Log.e(tag, "loadProjectFd threw", t); false }
        projectLoaded = ok
        if (ok) refreshAfterLoad()
        Log.i(tag, "loadProjectFd(fd=$fd) -> $ok")
        return ok
    }

    fun loadDemo(): Boolean {
        if (handle == 0L) return false
        if (isPlaying) stop()
        val ok = try { LemonEngineBridge.engineLoadDemo(handle) }
            catch (t: Throwable) { Log.e(tag, "loadDemo threw", t); false }
        projectLoaded = ok
        if (ok) refreshAfterLoad()
        Log.i(tag, "loadDemo -> $ok")
        return ok
    }

    private fun refreshAfterLoad() {
        refreshPattern()
        sampleRate = try { LemonEngineBridge.engineSampleRate(handle) } catch (_: Throwable) { 48_000 }
        bpm = try { LemonEngineBridge.engineTempo(handle).toFloat() } catch (_: Throwable) { 130f }
        val packed = try { LemonEngineBridge.engineGetTimeSignature(handle) } catch (_: Throwable) { 0 }
        if (packed == 0) { timeSigNum = 4; timeSigDen = 4 }
        else { timeSigNum = (packed shr 16) and 0xFFFF; timeSigDen = packed and 0xFFFF }
        currentArrangement = try { LemonEngineBridge.engineArrangementCurrent(handle) } catch (_: Throwable) { -1 }
        val n = try { LemonEngineBridge.engineTrackCount(handle) } catch (_: Throwable) { 0 }
        tracks = (0 until n).map { i ->
            try { LemonEngineBridge.engineTrackName(handle, i) }
            catch (_: Throwable) { "Track ${i + 1}" }
        }
        positionFrames = 0L
        Log.i(tag, "loaded: sampleRate=$sampleRate tracks=${tracks.size}")
    }

    fun tickPosition() {
        if (handle == 0L) return
        positionFrames = try { LemonEngineBridge.enginePosition(handle) }
            catch (_: Throwable) { 0L }
    }

    fun setFactory(path: String): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetFactory(handle, path) }
            catch (t: Throwable) { Log.e(tag, "setFactory threw", t); false }
        Log.i(tag, "setFactory($path) -> $ok")
        return ok
    }

    fun factory(): String = try {
        if (handle == 0L) "" else LemonEngineBridge.engineFactory(handle)
    } catch (t: Throwable) { Log.e(tag, "factory threw", t); "" }

    fun setTempo(v: Float): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetTempo(handle, v.toDouble()) }
            catch (t: Throwable) { Log.e(tag, "setTempo threw", t); false }
        if (ok) bpm = v
        Log.i(tag, "setTempo($v) -> $ok")
        return ok
    }

    fun setTimeSignature(num: Int, den: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetTimeSignature(handle, num, den) }
            catch (t: Throwable) { Log.e(tag, "setTimeSignature threw", t); false }
        if (ok) {
            timeSigNum = if (num > 0) num else 4
            timeSigDen = if (den > 0) den else 4
        }
        Log.i(tag, "setTimeSignature($num/$den) -> $ok")
        return ok
    }

    fun setArrangement(num: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetArrangement(handle, num) }
            catch (t: Throwable) { Log.e(tag, "setArrangement threw", t); false }
        if (ok) currentArrangement = num
        Log.i(tag, "setArrangement($num) -> $ok")
        return ok
    }

    fun addArrangement(): Int {
        if (handle == 0L) return -1
        val num = try { LemonEngineBridge.engineAddArrangement(handle) }
            catch (t: Throwable) { Log.e(tag, "addArrangement threw", t); -1 }
        if (num > 0) currentArrangement = num
        Log.i(tag, "addArrangement -> $num")
        return num
    }

    fun renameArrangement(num: Int, name: String): Boolean {
        if (handle == 0L) return false
        val ok = try {
            LemonEngineBridge.engineRenameArrangement(handle, num, name.toByteArray(Charsets.UTF_8))
        } catch (t: Throwable) { Log.e(tag, "renameArrangement threw", t); false }
        Log.i(tag, "renameArrangement($num, \"$name\") -> $ok")
        return ok
    }

    fun deleteArrangement(num: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineDeleteArrangement(handle, num) }
            catch (t: Throwable) { Log.e(tag, "deleteArrangement threw", t); false }
        if (ok) {
            currentArrangement = try { LemonEngineBridge.engineArrangementCurrent(handle) }
                catch (_: Throwable) { -1 }
        }
        Log.i(tag, "deleteArrangement($num) -> $ok")
        return ok
    }

    fun undo(): Boolean {
        revision++
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineUndo(handle) }
            catch (t: Throwable) { Log.e(tag, "undo threw", t); false }
        refreshAfterLoad()
                if (ok) refreshAfterUndoRedo()
        Log.i(tag, "undo -> $ok")
        return ok
    }

    fun redo(): Boolean {
        revision++
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineRedo(handle) }
            catch (t: Throwable) { Log.e(tag, "redo threw", t); false }
        refreshAfterLoad()
                if (ok) refreshAfterUndoRedo()
        Log.i(tag, "redo -> $ok")
        return ok
    }

    private fun refreshAfterUndoRedo() {
        try {
            canUndo = LemonEngineBridge.engineCanUndo(handle)
            canRedo = LemonEngineBridge.engineCanRedo(handle)
            bpm = LemonEngineBridge.engineTempo(handle).toFloat()
            sampleRate = LemonEngineBridge.engineSampleRate(handle)
            val n = LemonEngineBridge.engineTrackCount(handle)
            tracks = (0 until n).map { i ->
                try { LemonEngineBridge.engineTrackName(handle, i) } catch (_: Throwable) { "Track ${i+1}" }
            }
        } catch (t: Throwable) { Log.e(tag, "refreshAfterUndoRedo threw", t) }
    }

    fun refreshUndoState() {
        if (handle == 0L) return
        try {
            canUndo = LemonEngineBridge.engineCanUndo(handle)
            canRedo = LemonEngineBridge.engineCanRedo(handle)
        } catch (_: Throwable) {}
    }

    fun setMetronome(on: Boolean): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetMetronome(handle, on) }
            catch (t: Throwable) { Log.e(tag, "setMetronome threw", t); false }
        if (ok) metronomeOn = on
        return ok
    }

    /** Default PPQ used for bar math in dialogs (96 as the engine's demo). */
    fun previewPpq(): Int = 96

    fun deleteChannel(ch: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineDeleteChannel(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "deleteChannel threw", t); false }
        if (ok) refreshAfterLoad()
        Log.i(tag, "deleteChannel($ch) -> $ok")
        return ok
    }

    fun channelSample(ch: Int): String {
        if (handle == 0L) return ""
        return try { LemonEngineBridge.engineChannelSample(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "channelSample threw", t); "" }
    }

    fun setChannelSample(ch: Int, path: String): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetChannelSample(handle, ch, path) }
            catch (t: Throwable) { Log.e(tag, "setChannelSample threw", t); false }
        if (ok) refreshAfterLoad()
        Log.i(tag, "setChannelSample($ch) -> $ok")
        return ok
    }

    fun clearChannelSample(ch: Int): Boolean = setChannelSample(ch, "")

    fun reorderChannel(from: Int, to: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineReorderChannel(handle, from, to) }
            catch (t: Throwable) { Log.e(tag, "reorderChannel threw", t); false }
        if (ok) refreshAfterLoad()
        Log.i(tag, "reorderChannel($from->$to) -> $ok")
        return ok
    }

    fun duplicateChannel(idx: Int): Int {
        if (handle == 0L) return -1
        val newIdx = try { LemonEngineBridge.engineDuplicateChannel(handle, idx) }
            catch (t: Throwable) { Log.e(tag, "duplicateChannel threw", t); -1 }
        if (newIdx >= 0) refreshAfterLoad()
        Log.i(tag, "duplicateChannel($idx) -> $newIdx")
        return newIdx
    }

    fun setBuiltinInstrument(ch: Int, name: String): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetBuiltinInstrument(handle, ch, name) }
            catch (t: Throwable) { Log.e(tag, "setBuiltinInstrument threw", t); false }
        if (ok) refreshAfterLoad()
        Log.i(tag, "setBuiltinInstrument($ch, $name) -> $ok")
        return ok
    }

    fun channelInstrument(ch: Int): String {
        if (handle == 0L) return ""
        return try { LemonEngineBridge.engineChannelInstrument(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "channelInstrument threw", t); "" }
    }

    fun instrumentCatalog(): List<Pair<String, String>> {
        return try {
            LemonEngineBridge.engineInstrumentCatalog().split('\n')
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
        } catch (t: Throwable) { Log.e(tag, "catalog threw", t); emptyList() }
    }

    fun mixerAddEffect(track: Int, slot: Int, name: String): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineMixerAddEffect(handle, track, slot, name) }
            catch (t: Throwable) { Log.e(tag, "mixerAddEffect threw", t); false }
        Log.i(tag, "mixerAddEffect(t=$track, s=$slot, $name) -> $ok")
        return ok
    }

    fun mixerRemoveEffect(track: Int, slot: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineMixerRemoveEffect(handle, track, slot) }
            catch (t: Throwable) { Log.e(tag, "mixerRemoveEffect threw", t); false }
    }

    fun mixerSlotEffect(track: Int, slot: Int): String {
        if (handle == 0L) return ""
        return try { LemonEngineBridge.engineMixerSlotEffect(handle, track, slot) }
            catch (t: Throwable) { Log.e(tag, "mixerSlotEffect threw", t); "" }
    }

    fun mixerGetSends(track: Int): Map<Int, Float> {
        if (handle == 0L) return emptyMap()
        val bytes = try { LemonEngineBridge.engineMixerGetSendsBytes(handle, track) }
            catch (t: Throwable) { Log.e(tag, "mixerGetSends threw", t); ByteArray(0) }
        if (bytes.size < 10) return emptyMap()
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = mutableMapOf<Int, Float>()
        while (buf.remaining() >= 10) {
            val dest = buf.short.toInt() and 0xFFFF
            val lv = buf.double.toFloat()
            out[dest] = lv
        }
        return out
    }

    fun mixerSetSendLevel(track: Int, dest: Int, level: Double): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineMixerSetSendLevel(handle, track, dest, level) }
            catch (t: Throwable) { Log.e(tag, "mixerSetSendLevel threw", t); false }
        Log.i(tag, "mixerSetSendLevel($track -> $dest, $level) -> $ok")
        return ok
    }

    fun effectCatalog(): List<Pair<String, String>> {
        return try {
            LemonEngineBridge.engineEffectCatalog().split('\n')
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
        } catch (t: Throwable) { Log.e(tag, "effectCatalog threw", t); emptyList() }
    }

    fun setChannelColor(ch: Int, r: Int, g: Int, b: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetChannelColor(handle, ch, r, g, b) }
            catch (t: Throwable) { Log.e(tag, "setChannelColor threw", t); false }
        if (ok) refreshTrackColors()
        return ok
    }

    fun clearChannelColor(ch: Int): Boolean = setChannelColor(ch, -1, -1, -1)

    fun refreshTrackColors() {
        if (handle == 0L) return
        try {
            val n = LemonEngineBridge.engineTrackCount(handle)
            trackColors = (0 until n).map { i ->
                try { LemonEngineBridge.engineChannelColor(handle, i) } catch (_: Throwable) { -1 }
            }
        } catch (t: Throwable) { Log.e(tag, "refreshTrackColors threw", t) }
    }

    fun mixerBypassEffect(track: Int, slot: Int, on: Boolean): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineMixerBypassEffect(handle, track, slot, on) }
            catch (t: Throwable) { Log.e(tag, "mixerBypassEffect threw", t); false }
    }

    fun mixerIsEffectBypassed(track: Int, slot: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineMixerEffectBypassed(handle, track, slot) }
            catch (_: Throwable) { false }
    }

    fun effectParamCount(track: Int, slot: Int): Int {
        if (handle == 0L) return 0
        return try { LemonEngineBridge.engineEffectParamCount(handle, track, slot) }
            catch (t: Throwable) { Log.e(tag, "effectParamCount threw", t); 0 }
    }

    fun effectParamMeta(track: Int, slot: Int, index: Int): EffectParamMeta? {
        if (handle == 0L) return null
        return try {
            val parts = LemonEngineBridge.engineEffectParamMeta(handle, track, slot, index).split('\t')
            if (parts.size < 8) return null
            EffectParamMeta(
                id = parts[0].toIntOrNull() ?: return null,
                name = parts[1],
                unit = parts[2],
                group = parts[3],
                min = parts[4].toDoubleOrNull() ?: 0.0,
                max = parts[5].toDoubleOrNull() ?: 1.0,
                default = parts[6].toDoubleOrNull() ?: 0.0,
                steps = parts[7].toIntOrNull() ?: 0,
            )
        } catch (t: Throwable) { Log.e(tag, "effectParamMeta threw", t); null }
    }

    fun effectParamValue(track: Int, slot: Int, id: Int): Double {
        if (handle == 0L) return 0.0
        return try { LemonEngineBridge.engineEffectParamValue(handle, track, slot, id) }
            catch (_: Throwable) { 0.0 }
    }

    fun effectParamText(track: Int, slot: Int, id: Int, value: Double): String {
        if (handle == 0L) return ""
        return try { LemonEngineBridge.engineEffectParamText(handle, track, slot, id, value) }
            catch (_: Throwable) { "" }
    }

    fun effectSetParam(track: Int, slot: Int, id: Int, value: Double): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineEffectSetParam(handle, track, slot, id, value) }
            catch (t: Throwable) { Log.e(tag, "setParam threw", t); false }
    }

    fun setChannelStartOffset(ch: Int, offset: Double): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelStartOffset(handle, ch, offset) }
            catch (t: Throwable) { Log.e(tag, "setStartOffset threw", t); false }
    }

    fun channelStartOffset(ch: Int): Double {
        if (handle == 0L) return 0.0
        return try { LemonEngineBridge.engineChannelStartOffset(handle, ch) }
            catch (_: Throwable) { 0.0 }
    }

    fun mixerSwapEffects(track: Int, a: Int, b: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineMixerSwapEffects(handle, track, a, b) }
            catch (t: Throwable) { Log.e(tag, "swapEffects threw", t); false }
    }

    fun refreshPeak() {
        if (handle == 0L) return
        try { masterPeak = LemonEngineBridge.engineTakeMasterPeak(handle) } catch (_: Throwable) { }
    }

    fun clearPeak() {
        if (handle == 0L) return
        masterPeak = 0f
        try { LemonEngineBridge.engineClearMasterPeak(handle) } catch (_: Throwable) { }
    }

    fun renderStemFd(track: Int, fd: Int, songMode: Boolean, rate: Int): Long {
        if (handle == 0L) return 0L
        return try { LemonEngineBridge.engineRenderStem(handle, track, fd, songMode, rate) }
            catch (t: Throwable) { Log.e(tag, "renderStemFd threw", t); 0L }
    }

    fun channelPitch(ch: Int): Int {
        if (handle == 0L) return 0
        return try { LemonEngineBridge.engineChannelPitch(handle, ch) }
            catch (_: Throwable) { 0 }
    }

    fun setChannelPitch(ch: Int, cents: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelPitch(handle, ch, cents) }
            catch (t: Throwable) { Log.e(tag, "setPitch threw", t); false }
    }

    fun channelLoopStart(ch: Int): Long {
        if (handle == 0L) return -1L
        return try { LemonEngineBridge.engineChannelLoopStart(handle, ch) }
            catch (_: Throwable) { -1L }
    }

    fun channelLoopEnd(ch: Int): Long {
        if (handle == 0L) return -1L
        return try { LemonEngineBridge.engineChannelLoopEnd(handle, ch) }
            catch (_: Throwable) { -1L }
    }

    fun setChannelLoopPoints(ch: Int, start: Long, end: Long): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelLoopPoints(handle, ch, start, end) }
            catch (t: Throwable) { Log.e(tag, "setLoopPoints threw", t); false }
    }

    fun clearChannelLoopPoints(ch: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineClearChannelLoopPoints(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "clearLoop threw", t); false }
    }

    fun channelUseLoop(ch: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineChannelUseLoop(handle, ch) }
            catch (_: Throwable) { false }
    }

    fun setChannelUseLoop(ch: Int, on: Boolean): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelUseLoop(handle, ch, on) }
            catch (t: Throwable) { Log.e(tag, "setUseLoop threw", t); false }
    }

    fun channelPeaks(ch: Int): FloatArray {
        if (handle == 0L) return FloatArray(0)
        val bytes = try { LemonEngineBridge.engineChannelPeaks(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "channelPeaks threw", t); ByteArray(0) }
        if (bytes.size < 4) return FloatArray(0)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = buf.float
        return out
    }

    fun channelSampleFrames(ch: Int): Long {
        if (handle == 0L) return 0L
        return try { LemonEngineBridge.engineChannelSampleFrames(handle, ch) }
            catch (_: Throwable) { 0L }
    }

    fun channelStretchMode(ch: Int): Int {
        if (handle == 0L) return 0
        return try { LemonEngineBridge.engineChannelStretchMode(handle, ch) }
            catch (_: Throwable) { 0 }
    }
    fun setChannelStretchMode(ch: Int, mode: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelStretchMode(handle, ch, mode) }
            catch (t: Throwable) { Log.e(tag, "setStretchMode threw", t); false }
    }
    fun channelStretchMultiplier(ch: Int): Double {
        if (handle == 0L) return 1.0
        return try { LemonEngineBridge.engineChannelStretchMultiplier(handle, ch) }
            catch (_: Throwable) { 1.0 }
    }
    fun setChannelStretchMultiplier(ch: Int, mul: Double): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelStretchMultiplier(handle, ch, mul) }
            catch (t: Throwable) { Log.e(tag, "setStretchMul threw", t); false }
    }
    fun channelStretchPitch(ch: Int): Int {
        if (handle == 0L) return 0
        return try { LemonEngineBridge.engineChannelStretchPitch(handle, ch) }
            catch (_: Throwable) { 0 }
    }
    fun setChannelStretchPitch(ch: Int, cents: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelStretchPitch(handle, ch, cents) }
            catch (t: Throwable) { Log.e(tag, "setStretchPitch threw", t); false }
    }

    // SampleFx flag constants (mirror os_model::SampleFx)
    val FX_REVERSE = 2
    val FX_CLIP = 4
    val FX_SWAP = 256

    fun setChannelFxFlag(ch: Int, flag: Int, on: Boolean): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelFxFlag(handle, ch, flag, on) }
            catch (t: Throwable) { Log.e(tag, "setFxFlag threw", t); false }
    }
    fun channelFxFlag(ch: Int, flag: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineChannelFxFlag(handle, ch, flag) }
            catch (_: Throwable) { false }
    }
    fun setChannelFxTrim(ch: Int, start: Double, length: Double): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelFxTrim(handle, ch, start, length) }
            catch (t: Throwable) { Log.e(tag, "setFxTrim threw", t); false }
    }
    fun channelFxTrimStart(ch: Int): Double {
        if (handle == 0L) return 0.0
        return try { LemonEngineBridge.engineChannelFxTrimStart(handle, ch) }
            catch (_: Throwable) { 0.0 }
    }
    fun channelFxTrimLength(ch: Int): Double {
        if (handle == 0L) return 1.0
        return try { LemonEngineBridge.engineChannelFxTrimLength(handle, ch) }
            catch (_: Throwable) { 1.0 }
    }
    fun setChannelFxNormalize(ch: Int, on: Boolean): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelFxNormalize(handle, ch, on) }
            catch (t: Throwable) { Log.e(tag, "setFxNorm threw", t); false }
    }
    fun channelFxNormalize(ch: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineChannelFxNormalize(handle, ch) }
            catch (_: Throwable) { false }
    }
    fun clearSampleCache() {
        try { LemonEngineBridge.engineClearSampleCache() } catch (_: Throwable) {}
    }

    fun addSamplerChannel(): Int {
        if (handle == 0L) return -1
        val idx = try { LemonEngineBridge.engineAddSamplerChannel(handle) }
            catch (t: Throwable) { Log.e(tag, "addSamplerChannel threw", t); -1 }
        if (idx >= 0) refreshAfterLoad()
        Log.i(tag, "addSamplerChannel -> $idx")
        return idx
    }

    fun addAudioClipChannel(path: String): Int {
        if (handle == 0L) return -1
        val idx = try { LemonEngineBridge.engineAddAudioClipChannel(handle, path) }
            catch (t: Throwable) { Log.e(tag, "addAudioClipChannel threw", t); -1 }
        if (idx >= 0) refreshAfterLoad()
        Log.i(tag, "addAudioClipChannel -> $idx")
        return idx
    }

    fun addAutomationChannel(target: Int, value: Double = 0.5): Int {
        if (handle == 0L) return -1
        val idx = try { LemonEngineBridge.engineAddAutomationChannel(handle, target, value) }
            catch (t: Throwable) { Log.e(tag, "addAutomationChannel threw", t); -1 }
        if (idx >= 0) refreshAfterLoad()
        Log.i(tag, "addAutomationChannel(target=$target) -> $idx")
        return idx
    }

    fun automationPoints(ch: Int): List<Pair<Double, Double>> {
        if (handle == 0L) return emptyList()
        val bytes = try { LemonEngineBridge.engineAutomationPoints(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "automationPoints threw", t); ByteArray(0) }
        if (bytes.isEmpty()) return emptyList()
        val n = bytes.size / 16
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = ArrayList<Pair<Double, Double>>(n)
        for (i in 0 until n) {
            val beat = buf.double
            val value = buf.double
            out.add(beat to value)
        }
        return out
    }

    fun setAutomationPoints(ch: Int, points: List<Pair<Double, Double>>): Boolean {
        if (handle == 0L) return false
        val buf = ByteBuffer.allocate(points.size * 16).order(ByteOrder.LITTLE_ENDIAN)
        for ((beat, value) in points) {
            buf.putDouble(beat)
            buf.putDouble(value)
        }
        val ok = try { LemonEngineBridge.engineSetAutomationPoints(handle, ch, buf.array()) }
            catch (t: Throwable) { Log.e(tag, "setAutomationPoints threw", t); false }
        Log.i(tag, "setAutomationPoints(ch=$ch, n=${points.size}) -> $ok")
        return ok
    }

    data class AutomationLfo(
        val switch: Int = 0,
        val amount: Int = 128,
        val speed: Int = 45819,
        val mode: Int = 0,
    ) {
        val isOn: Boolean get() = switch != 0
    }

    fun getAutomationLfo(ch: Int): AutomationLfo? {
        if (handle == 0L) return null
        val bytes = try { LemonEngineBridge.engineGetAutomationLfo(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "getAutomationLfo threw", t); ByteArray(0) }
        if (bytes.size < 16) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return AutomationLfo(buf.int, buf.int, buf.int, buf.int)
    }

    fun setAutomationFull(ch: Int, points: List<Pair<Double, Double>>, lfo: AutomationLfo): Boolean {
        if (handle == 0L) return false
        val buf = ByteBuffer.allocate(points.size * 16).order(ByteOrder.LITTLE_ENDIAN)
        for ((beat, value) in points) { buf.putDouble(beat); buf.putDouble(value) }
        val ok = try {
            LemonEngineBridge.engineSetAutomationFull(handle, ch, buf.array(),
                lfo.switch, lfo.amount, lfo.speed, lfo.mode)
        } catch (t: Throwable) { Log.e(tag, "setAutomationFull threw", t); false }
        Log.i(tag, "setAutomationFull(ch=$ch, pts=${points.size}) -> $ok")
        return ok
    }

    fun renameChannel(ch: Int, name: String): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineRenameChannel(handle, ch, name) }
            catch (t: Throwable) { Log.e(tag, "renameChannel threw", t); false }
        if (ok) refreshAfterLoad()
        Log.i(tag, "renameChannel($ch, \"$name\") -> $ok")
        return ok
    }

    fun saveProject(fd: Int): Long {
        if (handle == 0L) return 0L
        val bytes = try { LemonEngineBridge.engineSaveProject(handle, fd) }
            catch (t: Throwable) { Log.e(tag, "saveProject threw", t); 0L }
        Log.i(tag, "saveProject(fd=$fd) -> $bytes bytes")
        return bytes
    }

    fun addPattern(): Int {
        if (handle == 0L) return -1
        val num = try { LemonEngineBridge.engineAddPattern(handle) }
            catch (t: Throwable) { Log.e(tag, "addPattern threw", t); -1 }
        if (num > 0) { currentPattern = num; refreshAfterLoad() }
        Log.i(tag, "addPattern -> $num")
        return num
    }

    fun duplicatePattern(srcNum: Int): Int {
        if (handle == 0L) return -1
        val num = try { LemonEngineBridge.engineDuplicatePattern(handle, srcNum) }
            catch (t: Throwable) { Log.e(tag, "duplicatePattern threw", t); -1 }
        if (num > 0) { currentPattern = num; refreshAfterLoad() }
        Log.i(tag, "duplicatePattern($srcNum) -> $num")
        return num
    }

    fun renamePattern(num: Int, name: String): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineRenamePattern(handle, num, name) }
            catch (t: Throwable) { Log.e(tag, "renamePattern threw", t); false }
        Log.i(tag, "renamePattern($num, \"$name\") -> $ok")
        return ok
    }

    fun setPattern(num: Int): Boolean {
        if (handle == 0L || num <= 0) return false
        val ok = try { LemonEngineBridge.engineSetPattern(handle, num) }
            catch (t: Throwable) { Log.e(tag, "setPattern threw", t); false }
        if (ok) {
            currentPattern = num
            refreshAfterLoad()
        }
        Log.i(tag, "setPattern($num) -> $ok")
        return ok
    }

    /** Refresh current pattern number from engine (after loads). */
    fun refreshPattern() {
        if (handle == 0L) return
        try { currentPattern = LemonEngineBridge.enginePatternCurrent(handle) }
        catch (t: Throwable) { Log.e(tag, "refreshPattern threw", t) }
    }

    fun setCountIn(bars: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetCountIn(handle, bars) }
            catch (t: Throwable) { Log.e(tag, "setCountIn threw", t); false }
        if (ok) countInBars = bars
        return ok
    }

    fun setLoopRange(startTick: Long, endTick: Long): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetLoopRange(handle, startTick, endTick) }
            catch (t: Throwable) { Log.e(tag, "setLoopRange threw", t); false }
        if (ok) {
            loopRangeStart = startTick
            loopRangeEnd = endTick
        }
        Log.i(tag, "setLoopRange($startTick,$endTick) -> $ok")
        return ok
    }

    fun clearLoopRange() = setLoopRange(-1L, -1L)

    fun refreshTransportExtras() {
        if (handle == 0L) return
        try {
            countInBars = LemonEngineBridge.engineCountIn(handle)
            loopRangeStart = LemonEngineBridge.engineLoopRangeStart(handle)
            loopRangeEnd = LemonEngineBridge.engineLoopRangeEnd(handle)
        } catch (t: Throwable) { Log.e(tag, "refreshTransportExtras threw", t) }
    }

    fun toggleMetronome() = setMetronome(!metronomeOn)

    fun setMetronomeSound(index: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetMetronomeSound(handle, index) }
            catch (t: Throwable) { Log.e(tag, "setMetronomeSound threw", t); false }
        if (ok) metronomeSound = index
        return ok
    }

    fun setMetronomeVolume(db: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetMetronomeVolume(handle, db) }
            catch (t: Throwable) { Log.e(tag, "setMetronomeVolume threw", t); false }
        if (ok) metronomeVolumeDb = db.coerceIn(-24, 12)
        return ok
    }

    /** Called once on load to sync with engine state. */
    fun refreshMetronomeSettings() {
        if (handle == 0L) return
        try {
            metronomeSound = LemonEngineBridge.engineMetronomeSound(handle)
            metronomeVolumeDb = LemonEngineBridge.engineMetronomeVolume(handle)
        } catch (t: Throwable) { Log.e(tag, "refreshMetronome threw", t) }
    }


    fun addSampleChannel(path: String, positionTicks: Int): Int {
        if (handle == 0L) return -1
        val idx = try { LemonEngineBridge.engineAddSampleChannel(handle, path, positionTicks) }
            catch (t: Throwable) { Log.e(tag, "addSampleChannel threw", t); -1 }
        if (idx >= 0) refreshAfterLoad()
        Log.i(tag, "addSampleChannel($path, $positionTicks) -> $idx")
        return idx
    }

    fun togglePlay() {
        if (handle == 0L) { Log.e(tag, "togglePlay: no handle"); return }
        if (isPlaying) {
            LemonEngineBridge.engineStopAudio(handle)
            isPlaying = false
        } else {
            val ok = try { LemonEngineBridge.engineStartAudio(handle) }
                catch (t: Throwable) { Log.e(tag, "engineStartAudio threw", t); false }
            isPlaying = ok
            Log.i(tag, "startAudio -> $ok")
        }
    }

    fun stop() {
        if (handle == 0L) return
        LemonEngineBridge.engineStopAudio(handle)
        isPlaying = false
        isRecording = false
        positionFrames = 0L
    }

    fun applyLooping(on: Boolean) {
        if (handle == 0L) return
        val ok = try { LemonEngineBridge.engineSetLooping(handle, on) }
            catch (t: Throwable) { Log.e(tag, "setLooping threw", t); false }
        if (ok) isLooping = on
        Log.i(tag, "applyLooping($on) -> $ok")
    }

    fun toggleRecord() { isRecording = !isRecording }

    fun seek(frames: Long) {
        if (handle == 0L) return
        try { LemonEngineBridge.engineSeek(handle, frames) }
        catch (t: Throwable) { Log.e(tag, "seek threw", t) }
    }

    fun rewind() = seek(0L)

    fun forward() = seek(positionFrames + sampleRate.toLong())

    fun dispose() {
        if (handle != 0L) {
            LemonEngineBridge.engineStopAudio(handle)
            LemonEngineBridge.engineDispose(handle)
            handle = 0L
        }
    }
}
