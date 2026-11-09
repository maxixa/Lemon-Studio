package com.maxixa.lemon.ui.playlist

/**
 * Which picker the dialog shows for a target.
 *   - Channel: pick a channel (Volume / Pan / Cutoff / Resonance / Mod X / Mod Y)
 *   - MixerTrack: pick a mixer track (Mixer Volume)
 *   - Global: no picker at all (Tempo)
 */
internal enum class TargetKind { Channel, MixerTrack, Global }

/**
 * Automation target types exposed by the Add dialog.
 *
 * Channel targets encode as `(idx shl 16) or paramId`, decoding to
 * `Target::ChannelVolume / ChannelPan / ChannelLevel(ch, paramId)` where
 * paramId matches `os_model::target::CHANNEL_*`:
 *   CHANNEL_VOLUME=0x00, PAN=0x01, CUTOFF=0x02, RESONANCE=0x03,
 *   MOD_X=0x13, MOD_Y=0x14
 *
 * Tempo is a global target: full u32 = `target::TEMPO = 0x4000_0005`.
 *
 * Mixer Volume uses mixer-place encoding: `((0x7000 + track*64) shl 16) or 0x1FC0`
 * decoding to `Target::MixerVolume(track)`. `0x1FC0 = 0x1F00 | MIXER_VOLUME (0xC0)`.
 */
internal enum class AutoTargetType(
    val label: String,
    val paramId: Int,
    val bipolar: Boolean,
    val kind: TargetKind = TargetKind.Channel,
) {
    Volume("Volume", 0x0000, false),
    Pan("Pan", 0x0001, true),
    Cutoff("Cutoff", 0x0002, false),
    Resonance("Resonance", 0x0003, false),
    ModX("Mod X", 0x0013, true),
    ModY("Mod Y", 0x0014, true),
    Tempo("Tempo", 0x40000005, false, TargetKind.Global),
    MixerVolume("Mixer Vol", 0xC0, false, TargetKind.MixerTrack),
}

/** Encode (target, idx) → u32 to hand to `engineAddAutomationChannel`. */
internal fun encodeTarget(type: AutoTargetType, idx: Int): Int = when (type.kind) {
    TargetKind.Channel -> (idx shl 16) or type.paramId
    TargetKind.Global -> type.paramId
    TargetKind.MixerTrack -> (((0x7000 + idx * 64) shl 16) or 0x1FC0)
}

/** Human-readable value display. `value` is 0..100. */
internal fun displayAutomationValue(type: AutoTargetType, value: Int): String {
    if (!type.bipolar) return "$value%"
    return when {
        value == 50 -> if (type == AutoTargetType.Pan) "C" else "0"
        value < 50 -> if (type == AutoTargetType.Pan) "L${(50 - value) * 2}" else "-${(50 - value) * 2}"
        else -> if (type == AutoTargetType.Pan) "R${(value - 50) * 2}" else "+${(value - 50) * 2}"
    }
}

/** Label above the slider. */
internal fun targetValueLabel(type: AutoTargetType): String = when (type) {
    AutoTargetType.Volume -> "Initial volume"
    AutoTargetType.Pan -> "Initial pan"
    AutoTargetType.Cutoff -> "Initial cutoff"
    AutoTargetType.Resonance -> "Initial resonance"
    AutoTargetType.ModX -> "Initial Mod X"
    AutoTargetType.ModY -> "Initial Mod Y"
    AutoTargetType.Tempo -> "Initial tempo (0-100% of engine range)"
    AutoTargetType.MixerVolume -> "Initial track volume"
}

/** Label above the picker (or "" when no picker). */
internal fun pickerLabel(type: AutoTargetType): String = when (type.kind) {
    TargetKind.Channel -> "Channel"
    TargetKind.MixerTrack -> "Mixer track"
    TargetKind.Global -> ""
}
