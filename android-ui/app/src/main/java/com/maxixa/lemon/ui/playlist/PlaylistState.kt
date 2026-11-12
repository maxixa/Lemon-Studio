package com.maxixa.lemon.ui.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.maxixa.lemon.state.SettingsStore
import com.maxixa.lemon.state.*

enum class PlaylistTool { Pencil, Select }

class PlaylistState(private val store: SettingsStore? = null) {
    var trackCount by mutableStateOf(8)
    var patternCount by mutableStateOf(0)
    var defaultPattern by mutableStateOf(1)
    var ppq by mutableStateOf(96)
    var scrollX by mutableStateOf(0f)
    var scrollY by mutableStateOf(0)
    var songMode by mutableStateOf(true)
    var tool by mutableStateOf(PlaylistTool.Pencil)
    var selectionRect by mutableStateOf<Rect?>(null)
    val clips = mutableStateListOf<ClipView>()
    val markers = mutableStateListOf<MarkerView>()
    var currentArrangement by mutableStateOf(0)
    var barNum by mutableStateOf(0)
    var barDen by mutableStateOf(4)
    var selectedMarkerId by mutableStateOf<Long?>(null)
    var showArrangementPicker by mutableStateOf(false)

    internal var nextId = 1L

    fun clear() {
        clips.clear(); markers.clear()
        nextId = 1L; selectionRect = null
    }

    fun loadClips(list: List<ClipView>) {
        clear()
        for (c in list) clips.add(c.copy(id = nextId++))
    }

    /** Effective numerator — 4/4 fallback when wire or engine carries 0. */
    fun effectiveBarNum(): Int = if (barNum > 0) barNum else 4
    fun effectiveBarDen(): Int = if (barDen > 0) barDen else 4

    /** Ticks per bar for the active meter. */
    fun barTicks(): Int =
        (ppq * effectiveBarNum() * 4 / effectiveBarDen()).coerceAtLeast(1)

    /** Quarter-note beats per bar (for grid emphasis). */
    fun barBeats(): Int =
        (effectiveBarNum() * 4 / effectiveBarDen()).coerceAtLeast(1)


    /** Grid snap mode: 0=off, 1=bar, 2=1/2 bar, 3=1/4 bar. */
    var snapMode by mutableStateOf(0)

    /** Snap raw ticks to the current grid. Off → unchanged. */
    fun snapTicks(raw: Int): Int {
        if (snapMode == 0) return raw
        val bar = barTicks().coerceAtLeast(1)
        val step = when (snapMode) {
            1 -> bar
            2 -> (bar / 2).coerceAtLeast(1)
            3 -> (bar / 4).coerceAtLeast(1)
            else -> return raw
        }
        return ((raw + step / 2) / step) * step
    }

    fun addClip(track: Int, position: Int, length: Int, pattern: Int): Int {
        clips.add(ClipView(nextId++, track, snapTicks(position).coerceAtLeast(0), length, pattern))
        return clips.size - 1
    }

    /** Insert a pre-populated clip (copy/paste). Assigns a fresh id. */
    fun addClipRaw(clip: ClipView): Int {
        clips.add(clip.copy(id = nextId++))
        return clips.size - 1
    }


    fun deleteSelected() { clips.removeAll { it.selected } }

    /** Add a channel-source clip (audio / automation). `channelIdx` is 0-based. */
    fun addChannelClip(track: Int, position: Int, length: Int, channelIdx: Int): Int {
        clips.add(ClipView(
            id = nextId++,
            track = track.coerceIn(0, (trackCount - 1).coerceAtLeast(0)),
            position = snapTicks(position).coerceAtLeast(0),
            length = length.coerceAtLeast(1),
            pattern = channelIdx,
            sourceKind = 1,
        ))
        return clips.size - 1
    }

    fun addMarker(position: Int, name: String): Int {
        markers.add(MarkerView(
            id = nextId++,
            position = position.coerceAtLeast(0),
            kind = 0,
            name = name,
            numerator = 4,
            denominator = 4,
            scale = 0,
        ))
        return markers.size - 1
    }

    /** Returns (start, end) loop ticks: this marker → next marker (or +4 bars if none). */

    fun loopRangeFromMarker(markerId: Long): Pair<Int, Int>? {

        val marker = markers.firstOrNull { it.id == markerId } ?: return null

        val start = marker.position

        val nextPos = markers

            .asSequence()

            .map { it.position }

            .filter { it > start }

            .minOrNull()

        val end = nextPos ?: (start + 4 * barTicks())

        return start to end

    }


    fun deleteMarker(id: Long): Boolean {
        val before = markers.size
        markers.removeAll { it.id == id }
        return markers.size < before
    }

    fun pxPerTick(w: Float): Float =
        (w - 90f) / (barsVisible.coerceAtLeast(0.5f) * barTicks().toFloat()).coerceAtLeast(1f)

    /** Auto-follow playhead while playing. */
    var autoFollow by mutableStateOf(true)
    /** How many bars fit in the visible viewport. */
    private var _barsVisible by mutableStateOf(store?.barsVisible ?: 4f)
    var barsVisible: Float
        get() = _barsVisible
        set(v) {
            _barsVisible = v
            store?.barsVisible = v
        }
    private var _trimMode by mutableStateOf(store?.trimMode ?: false)
    var trimMode: Boolean
        get() = _trimMode
        set(v) {
            _trimMode = v
            store?.trimMode = v
        }
    /** Clip ID currently being fade-dragged; null when no fade drag active. */
    var fadeDragClipId by mutableStateOf<Long?>(null)
    /** True when dragging the fade-IN handle; false for fade-out. */
    var fadeDragIsIn by mutableStateOf(true)
    /** Clip ID currently being gain-dragged; null when idle. */
    var gainDragClipId by mutableStateOf<Long?>(null)
    var automationEditorChannelIdx by mutableStateOf<Int?>(null)
    var automationClipChannels by mutableStateOf<Set<Int>>(emptySet())
    var bpm by mutableStateOf(120f)
    var audioClipDurations by mutableStateOf<Map<Int, Float>>(emptyMap())
    /** Loop range in ticks; -1 = no loop. Mirrors engine loop range. */
    var loopStart by mutableStateOf(-1)
    var loopEnd by mutableStateOf(-1)

    /** Scroll viewport so playheadTicks sits within 15%..85% of width. */
    fun followPlayhead(playheadTicks: Int, w: Float) {
        if (!autoFollow) return
        val visibleTicks = barsVisible * barTicks().toFloat()
        val lead = scrollX + visibleTicks * 0.15f
        val trail = scrollX + visibleTicks * 0.85f
        val ph = playheadTicks.toFloat()
        if (ph < lead || ph > trail) {
            scrollX = (ph - visibleTicks * 0.15f).coerceAtLeast(0f)
        }
    }


    fun tickAtX(x: Float, w: Float): Int {
        val px = pxPerTick(w); if (px <= 0f) return 0
        return (scrollX + (x - 90f) / px).toInt().coerceAtLeast(0)
    }

    fun trackAtY(y: Float, h: Float): Int =
        (scrollY + y / 36f).toInt().coerceIn(0, trackCount - 1)
}

/** A time marker as the playlist holds it locally. */
data class MarkerView(
    val id: Long,
    val position: Int,       // ticks
    val kind: Int,           // 0 = plain; preserved from wire
    val name: String,        // ≤ 32 UTF-8 bytes on the wire
    val numerator: Int,      // time-signature numerator, 4 by default
    val denominator: Int,    // steps per beat, 4 by default
    val scale: Int,          // key marker pitch mask, 0 by default
)

/**
 * One clip on the playlist.
 *
 * `pattern` is the pattern number for pattern-source clips (0 = none),
 * or the channel index for `sourceKind = 1` (audio/automation clips
 * from FLP imports). The canvas uses it for color only.
 *
 * `windowLo`/`windowHi` are the raw wire `window` values (f32 lossy
 * of f64). Zero on both means no window. Preserved through round-trip
 * even though no UI exposes them yet — prevents data loss on FLP
 * import → edit → save.
 *
 * `fadeInMs`/`fadeOutMs` (with tension) are audio-clip manual fades.
 * Zero = no manual fade (auto-crossfade may still apply at playback
 * from clip overlap). Preserved through round-trip even though no UI
 * exposes them yet.
 */
data class ClipView(
    val id: Long,
    val track: Int,
    val position: Int,
    val length: Int,
    val pattern: Int,
    val muted: Boolean = false,
    val selected: Boolean = false,
    val sourceKind: Int = 0,     // 0 = pattern, 1 = channel (audio/automation)
    val windowLo: Float = 0f,
    val windowHi: Float = 0f,
    val fadeInMs: Float = 0f,
    val fadeInTension: Float = 0f,
    val fadeOutMs: Float = 0f,
    val fadeOutTension: Float = 0f,
    val gain: Float = 1f,
)
