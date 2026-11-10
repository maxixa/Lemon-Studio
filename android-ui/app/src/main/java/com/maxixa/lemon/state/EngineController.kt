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
    internal val tag = "Lemon"

    internal var handle: Long = 0L

    /** Last-loaded project basename (no extension), or "" for unsaved. Drives per-project scoping. */
    var currentProjectName by mutableStateOf("")
        private set

    internal fun setProjectName(name: String) {
        currentProjectName = name.substringAfterLast('/').substringBeforeLast('.')
    }

    var isPlaying by mutableStateOf(false);       internal set
    /** Exposed for PianoRollBridge (JNI handle access). */

    val handleRef: Long get() = handle

    var isRecording by mutableStateOf(false);     internal set

    var isLooping by mutableStateOf(false);       internal set

    var metronomeOn by mutableStateOf(false); internal set

    var canUndo by mutableStateOf(false); internal set

    var canRedo by mutableStateOf(false); internal set

    var revision by mutableStateOf(0L); internal set

    var metronomeSound by mutableStateOf(1); internal set

    var metronomeVolumeDb by mutableStateOf(0); internal set

    var countInBars by mutableStateOf(0); internal set

    var currentPattern by mutableStateOf(1); internal set

    var loopRangeStart by mutableStateOf(-1L); internal set

    var loopRangeEnd by mutableStateOf(-1L); internal set

    var projectLoaded by mutableStateOf(false);   internal set

    var positionFrames by mutableStateOf(0L);     internal set

    var sampleRate by mutableStateOf(48_000);     internal set

    var tracks by mutableStateOf<List<String>>(emptyList()); internal set

    var trackPeaks by mutableStateOf<List<FloatArray>>(emptyList()); internal set

    var trackColors by mutableStateOf<List<Int>>(emptyList()); internal set

    var masterPeak by mutableStateOf(0f); internal set

    var bpm by mutableStateOf(130f); internal set

    var timeSigNum by mutableStateOf(4); internal set

    var timeSigDen by mutableStateOf(4); internal set

    var currentArrangement by mutableStateOf(-1); internal set

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
        setProjectName(path)
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
        currentProjectName = "demo"
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

    fun previewPpq(): Int = 96

    fun deleteChannel(ch: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineDeleteChannel(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "deleteChannel threw", t); false }
        if (ok) refreshAfterLoad()
        Log.i(tag, "deleteChannel($ch) -> $ok")
        return ok
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

    fun setChannelStartOffset(ch: Int, offset: Double): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelStartOffset(handle, ch, offset) }
            catch (t: Throwable) { Log.e(tag, "setStartOffset threw", t); false }
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

    fun renderStemFd(track: Int, fd: Int, songMode: Boolean, rate: Int, bits: Int = 32): Long {
        if (handle == 0L) return 0L
        return try { LemonEngineBridge.engineRenderStem(handle, track, fd, songMode, rate, bits) }
            catch (t: Throwable) { Log.e(tag, "renderStemFd threw", t); 0L }
    }

    fun setChannelPitch(ch: Int, cents: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelPitch(handle, ch, cents) }
            catch (t: Throwable) { Log.e(tag, "setPitch threw", t); false }
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

    fun setChannelUseLoop(ch: Int, on: Boolean): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelUseLoop(handle, ch, on) }
            catch (t: Throwable) { Log.e(tag, "setUseLoop threw", t); false }
    }

    fun setChannelStretchMode(ch: Int, mode: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelStretchMode(handle, ch, mode) }
            catch (t: Throwable) { Log.e(tag, "setStretchMode threw", t); false }
    }

    fun setChannelStretchMultiplier(ch: Int, mul: Double): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelStretchMultiplier(handle, ch, mul) }
            catch (t: Throwable) { Log.e(tag, "setStretchMul threw", t); false }
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

    fun setChannelFxTrim(ch: Int, start: Double, length: Double): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelFxTrim(handle, ch, start, length) }
            catch (t: Throwable) { Log.e(tag, "setFxTrim threw", t); false }
    }

    fun setChannelFxNormalize(ch: Int, on: Boolean): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineSetChannelFxNormalize(handle, ch, on) }
            catch (t: Throwable) { Log.e(tag, "setFxNorm threw", t); false }
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

    fun addSampleChannel(path: String, positionTicks: Int): Int {
        if (handle == 0L) return -1
        val idx = try { LemonEngineBridge.engineAddSampleChannel(handle, path, positionTicks) }
            catch (t: Throwable) { Log.e(tag, "addSampleChannel threw", t); -1 }
        if (idx >= 0) refreshAfterLoad()
        Log.i(tag, "addSampleChannel($path, $positionTicks) -> $idx")
        return idx
    }

    fun applyLooping(on: Boolean) {
        if (handle == 0L) return
        val ok = try { LemonEngineBridge.engineSetLooping(handle, on) }
            catch (t: Throwable) { Log.e(tag, "setLooping threw", t); false }
        if (ok) isLooping = on
        Log.i(tag, "applyLooping($on) -> $ok")
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
