package com.maxixa.lemon.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.maxixa.lemon.data.NoteView

enum class Tool { Pencil, Eraser, Select, Pan }

/**
 * Piano roll core state.
 *
 * Selection / clipboard / bulk-edit operations live in
 * PianoRollSelection.kt as extension functions.
 */
class PianoRollState(private val store: SettingsStore? = null) {
    var tool by mutableStateOf(Tool.Pencil)
    var ppq by mutableStateOf(96)
    var barNum by mutableStateOf(4)
    var barDen by mutableStateOf(4)
    var patternLength by mutableStateOf(384)
    val notes = mutableStateListOf<NoteView>()

    var hBeats by mutableStateOf(4f)
    var vRows by mutableStateOf(24)
    var snapDenominator by mutableStateOf(16)
    var targetChannel by mutableStateOf(0)
    var scrollX by mutableStateOf(0f)
    var scrollY by mutableStateOf(0)
    var autoFollow by mutableStateOf(true)

    var contextMenuIndex by mutableStateOf(-1)
        private set

    internal var nextId = 1L
    internal var clipboard: List<NoteView> = emptyList()
    var selectionRect by mutableStateOf<Rect?>(null)
    private var _showGhosts by mutableStateOf(store?.showGhosts ?: false)
    var showGhosts: Boolean
        get() = _showGhosts
        set(v) {
            _showGhosts = v
            store?.showGhosts = v
        }
    private var _scaleLockEnabled by mutableStateOf(store?.scaleLockEnabled ?: false)
    var scaleLockEnabled: Boolean
        get() = _scaleLockEnabled
        set(v) {
            _scaleLockEnabled = v
            store?.scaleLockEnabled = v
        }
    private var _scaleRoot by mutableStateOf(store?.scaleRoot ?: 0)
    var scaleRoot: Int
        get() = _scaleRoot
        set(v) {
            _scaleRoot = v
            store?.scaleRoot = v
        }
    private var _scaleType by mutableStateOf(store?.scaleType ?: 0)
    var scaleType: Int
        get() = _scaleType
        set(v) {
            _scaleType = v
            store?.scaleType = v
        }

    /** Snap `key` to nearest scale note if scale lock is on; pass-through otherwise. */
    fun snapKey(key: Int): Int {
        val clamped = key.coerceIn(0, 127)
        if (!scaleLockEnabled) return clamped
        val preset = SCALE_PRESETS.getOrNull(scaleType) ?: return clamped
        val keys = buildScaleKeys(scaleRoot, preset.intervals)
        return nearestScaleKey(clamped, keys)
    }


    val snapTicks: Int
        get() = if (snapDenominator <= 0) 0
                else (ppq * 4 / snapDenominator).coerceAtLeast(1)

    val defaultNoteLength: Int
        get() = snapTicks.takeIf { it > 0 } ?: (ppq / 4).coerceAtLeast(1)

    val minNoteLength: Int
        get() = snapTicks.takeIf { it > 0 } ?: (ppq / 8).coerceAtLeast(1)

    val selectedCount: Int get() = notes.count { it.selected }
    val hasClipboard: Boolean get() = clipboard.isNotEmpty()

    fun clear() { notes.clear(); nextId = 1L; selectionRect = null }
    /** Quarter-note beats per bar for grid emphasis. */
    fun barBeats(): Int = (barNum * 4 / barDen).coerceAtLeast(1)


    fun add(pos: Int, len: Int, key: Int, ch: Int): Int {
        notes.add(NoteView(nextId++, pos, len, ch, key, 100))
        return notes.size - 1
    }

    /** Insert a fully-populated NoteView. Reuses add() for id gen. */
    fun addFull(note: NoteView): Int {
        val idx = add(note.position, note.length, note.key, note.channel)
        notes[idx] = note.copy(id = notes[idx].id)
        return idx
    }


    fun findAt(pos: Int, key: Int, tol: Int): Int =
        notes.indexOfFirst {
            it.key == key && pos >= it.position && pos <= it.position + it.length.coerceAtLeast(tol)
        }

    fun snap(raw: Float): Int {
        val t = snapTicks
        if (t <= 0) return raw.toInt().coerceAtLeast(0)
        return (((raw + t / 2.0f) / t).toInt() * t).coerceAtLeast(0)
    }

    fun followPlayhead(playheadTicks: Int) {
        if (!autoFollow) return
        val visibleTicks = hBeats * ppq
        val lead = scrollX + visibleTicks * 0.15f
        val trail = scrollX + visibleTicks * 0.85f
        val ph = playheadTicks.toFloat()
        if (ph < lead || ph > trail) {
            scrollX = (ph - visibleTicks * 0.15f).coerceAtLeast(0f)
        }
    }

    fun openContextMenu(idx: Int) {
        if (idx in notes.indices) contextMenuIndex = idx
    }
    fun closeContextMenu() { contextMenuIndex = -1 }

    fun updateNoteAt(idx: Int, velocity: Int, length: Int): Boolean {
        if (idx !in notes.indices) return false
        val n = notes[idx]
        notes[idx] = n.copy(
            velocity = velocity.coerceIn(1, 127),
            length = length.coerceAtLeast(1),
        )
        return true
    }

    fun updateNoteFull(idx: Int, updated: NoteView): Boolean {
        if (idx !in notes.indices) return false
        val old = notes[idx]
        notes[idx] = updated.copy(id = old.id, selected = old.selected)
        return true
    }

    fun deleteNoteAt(idx: Int): Boolean {
        if (idx !in notes.indices) return false
        notes.removeAt(idx)
        contextMenuIndex = -1
        return true
    }

    fun duplicateNoteAt(idx: Int, barTicks: Int): Boolean {
        if (idx !in notes.indices) return false
        val original = notes[idx]
        val copy = original.copy(
            id = nextId++,
            position = original.position + barTicks,
            selected = false,
        )
        notes.add(copy)
        contextMenuIndex = notes.size - 1
        return true
    }
}
