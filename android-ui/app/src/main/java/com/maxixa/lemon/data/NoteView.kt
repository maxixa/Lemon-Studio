package com.maxixa.lemon.data

import androidx.compose.runtime.Immutable

/** A note as the piano roll holds it locally. */
@Immutable
data class NoteView(
    val id: Long,
    val position: Int,       // ticks
    val length: Int,         // ticks
    val channel: Int,
    val key: Int,            // 60 = C5
    val velocity: Int,
    val pan: Int = 64,          // 0..=128; 64 = center
    val finePitch: Int = 120,   // 0..=255; 120 = center
    val release: Int = 0,
    val modX: Int = 0,
    val modY: Int = 0,
    val flags: Int = 0x4000,    // 0x4000 always; 0x0008 = slide
    val group: Int = 0,         // low nibble = color; 0x20 = muted
    val unk14: Int = 0,         // preserved as-read
    val repeat: Int = 0,        // 0..=14; 0 = off
    val selected: Boolean = false,
)
