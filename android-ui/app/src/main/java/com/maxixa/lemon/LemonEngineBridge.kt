package com.maxixa.lemon

import android.content.Context

object LemonEngineBridge {
    external fun initContext(ctx: Context)
    external fun engineNew(): Long
    external fun engineLoadProject(handle: Long, path: String): Boolean
    external fun engineLoadProjectFd(handle: Long, fd: Int): Boolean
    external fun engineLoadDemo(handle: Long): Boolean
    external fun engineStartAudio(handle: Long): Boolean
    external fun engineStopAudio(handle: Long)
    external fun engineDispose(handle: Long)
    external fun engineVersion(): String
    external fun enginePosition(handle: Long): Long
    external fun engineTrackCount(handle: Long): Int
    external fun engineTrackName(handle: Long, index: Int): String
    external fun engineSampleRate(handle: Long): Int
    external fun engineSetLooping(handle: Long, on: Boolean): Boolean
    external fun engineSeek(handle: Long, frames: Long): Boolean
    external fun engineNotesBytes(handle: Long): ByteArray
    external fun engineCommitNotes(handle: Long, bytes: ByteArray): Boolean
    external fun engineNotesDebug(handle: Long): String
    external fun engineStepMaskBytes(handle: Long, channel: Int): ByteArray
    external fun enginePatternLengthBars(handle: Long): Int
    external fun engineSetPatternLengthBars(handle: Long, bars: Int): Boolean
    external fun engineToggleStep(handle: Long, channel: Int, step: Int): Boolean
    external fun engineArrangementBytes(handle: Long): ByteArray
    external fun engineCommitArrangement(handle: Long, bytes: ByteArray): Boolean
    external fun engineCommitArrangementSilent(handle: Long, bytes: ByteArray): Boolean
    external fun engineSetArrangement(handle: Long, num: Int): Boolean
    external fun engineArrangementCurrent(handle: Long): Int
    external fun engineArrangementCount(handle: Long): Int
    external fun engineArrangementNumber(handle: Long, index: Int): Int
    external fun engineArrangementName(handle: Long, index: Int): String
    external fun engineAddArrangement(handle: Long): Int
    external fun engineRenameArrangement(handle: Long, num: Int, nameBytes: ByteArray): Boolean
    external fun engineDeleteArrangement(handle: Long, num: Int): Boolean
    external fun engineSetPlayMode(handle: Long, song: Boolean): Boolean
    external fun engineIsSongMode(handle: Long): Boolean
    external fun enginePatternCount(handle: Long): Int
    external fun enginePatternName(handle: Long, index: Int): String
    external fun engineMixerBytes(handle: Long): ByteArray
    external fun engineMixerTrackCount(handle: Long): Int
    external fun engineMixerSetVolume(handle: Long, track: Int, v: Double): Boolean
    external fun engineMixerSetPan(handle: Long, track: Int, v: Double): Boolean
    external fun engineMixerGetSendsBytes(handle: Long, track: Int): ByteArray
    external fun engineMixerSetSendLevel(handle: Long, track: Int, dest: Int, level: Double): Boolean
    external fun engineExportWav(handle: Long, fd: Int, songMode: Boolean, rate: Int): Long
    external fun engineTempo(handle: Long): Double
    external fun engineSetTempo(handle: Long, bpm: Double): Boolean
    external fun engineChannelEnabled(handle: Long, channel: Int): Boolean
    external fun engineChannelMuted(handle: Long, channel: Int): Boolean
    external fun engineChannelSoloed(handle: Long, channel: Int): Boolean
    external fun engineSetChannelMuted(handle: Long, channel: Int, on: Boolean): Boolean
    external fun engineSetChannelSoloed(handle: Long, channel: Int, on: Boolean): Boolean
    external fun engineFactory(handle: Long): String
    external fun engineSetFactory(handle: Long, path: String): Boolean
    external fun engineExportFlac(handle: Long, fd: Int, songMode: Boolean, rate: Int): Long
    external fun engineSetMetronome(handle: Long, on: Boolean): Boolean
    external fun engineMetronomeEnabled(handle: Long): Boolean
    external fun engineMetronomeSound(handle: Long): Int
    external fun engineMetronomeVolume(handle: Long): Int
    external fun engineSetMetronomeSound(handle: Long, index: Int): Boolean
    external fun engineSetMetronomeVolume(handle: Long, db: Int): Boolean
    external fun engineExportMp3(handle: Long, fd: Int, songMode: Boolean, rate: Int, kbps: Int): Long
    external fun engineUndo(handle: Long): Boolean
    external fun engineRedo(handle: Long): Boolean
    external fun engineCanUndo(handle: Long): Boolean
    external fun engineCanRedo(handle: Long): Boolean
    external fun engineAddSampleChannel(handle: Long, path: String, positionTicks: Int): Int
    external fun engineChannelPeaks(handle: Long, channel: Int): ByteArray
    external fun engineSetCountIn(handle: Long, bars: Int): Boolean
    external fun engineCountIn(handle: Long): Int
    external fun engineSetLoopRange(handle: Long, start: Long, end: Long): Boolean
    external fun engineLoopRangeStart(handle: Long): Long
    external fun engineLoopRangeEnd(handle: Long): Long
    external fun engineExportMidi(handle: Long, fd: Int, songMode: Boolean): Long
    external fun enginePatternCurrent(handle: Long): Int
    external fun engineSetPattern(handle: Long, num: Int): Boolean
    external fun engineSetPatternLength(handle: Long, length: Int): Boolean
    external fun engineProjectSwing(handle: Long): Int
    external fun engineSetProjectSwing(handle: Long, swing: Int): Boolean
    external fun engineGetTimeSignature(handle: Long): Int
    external fun engineSetTimeSignature(handle: Long, num: Int, den: Int): Boolean
    external fun engineRotateSteps(handle: Long, channel: Int, delta: Int): Boolean
    external fun engineApplyStepMask(handle: Long, channel: Int, mask: ByteArray): Boolean
    external fun engineAddPattern(handle: Long): Int
    external fun engineDuplicatePattern(handle: Long, srcNum: Int): Int
    external fun engineRenamePattern(handle: Long, num: Int, name: String): Boolean
    external fun engineAddSamplerChannel(handle: Long): Int
    external fun engineAddAudioClipChannel(handle: Long, path: String): Int
    external fun engineAddAutomationChannel(handle: Long, target: Int, value: Double): Int
    external fun engineAutomationPoints(handle: Long, channel: Int): ByteArray
    external fun engineSetAutomationPoints(handle: Long, channel: Int, points: ByteArray): Boolean
    external fun engineGetAutomationLfo(handle: Long, channel: Int): ByteArray
    external fun engineSetAutomationFull(handle: Long, channel: Int, points: ByteArray, lfoSwitch: Int, lfoAmount: Int, lfoSpeed: Int, lfoMode: Int): Boolean
    external fun engineRenameChannel(handle: Long, channel: Int, name: String): Boolean
    external fun engineDeleteChannel(handle: Long, channel: Int): Boolean
    external fun engineChannelSample(handle: Long, channel: Int): String
    external fun engineSetChannelSample(handle: Long, channel: Int, path: String): Boolean
    external fun engineSaveProject(handle: Long, fd: Int): Long
    external fun engineReorderChannel(handle: Long, from: Int, to: Int): Boolean
    external fun engineDuplicateChannel(handle: Long, channel: Int): Int
    external fun engineSetBuiltinInstrument(handle: Long, channel: Int, name: String): Boolean
    external fun engineChannelInstrument(handle: Long, channel: Int): String
    external fun engineInstrumentCatalog(): String
    external fun engineMixerAddEffect(handle: Long, track: Int, slot: Int, name: String): Boolean
    external fun engineMixerRemoveEffect(handle: Long, track: Int, slot: Int): Boolean
    external fun engineMixerSlotEffect(handle: Long, track: Int, slot: Int): String
    external fun engineEffectCatalog(): String
    external fun engineSetChannelColor(handle: Long, channel: Int, r: Int, g: Int, b: Int): Boolean
    external fun engineChannelColor(handle: Long, channel: Int): Int
    external fun engineMixerBypassEffect(handle: Long, track: Int, slot: Int, on: Boolean): Boolean
    external fun engineMixerEffectBypassed(handle: Long, track: Int, slot: Int): Boolean
    external fun engineEffectParamCount(handle: Long, track: Int, slot: Int): Int
    external fun engineEffectParamMeta(handle: Long, track: Int, slot: Int, index: Int): String
    external fun engineEffectParamValue(handle: Long, track: Int, slot: Int, id: Int): Double
    external fun engineEffectParamText(handle: Long, track: Int, slot: Int, id: Int, value: Double): String
    external fun engineEffectSetParam(handle: Long, track: Int, slot: Int, id: Int, value: Double): Boolean
    external fun engineSetChannelStartOffset(handle: Long, channel: Int, offset: Double): Boolean
    external fun engineChannelStartOffset(handle: Long, channel: Int): Double
    external fun engineMixerSwapEffects(handle: Long, track: Int, a: Int, b: Int): Boolean
    external fun engineTakeMasterPeak(handle: Long): Float
    external fun engineClearMasterPeak(handle: Long)
    external fun engineRenderStem(handle: Long, track: Int, fd: Int, songMode: Boolean, rate: Int): Long
    external fun engineChannelPitch(handle: Long, channel: Int): Int
    external fun engineSetChannelPitch(handle: Long, channel: Int, cents: Int): Boolean
    external fun engineChannelLoopStart(handle: Long, channel: Int): Long
    external fun engineChannelLoopEnd(handle: Long, channel: Int): Long
    external fun engineSetChannelLoopPoints(handle: Long, channel: Int, start: Long, end: Long): Boolean
    external fun engineClearChannelLoopPoints(handle: Long, channel: Int): Boolean
    external fun engineChannelUseLoop(handle: Long, channel: Int): Boolean
    external fun engineSetChannelUseLoop(handle: Long, channel: Int, on: Boolean): Boolean
    external fun engineChannelSampleFrames(handle: Long, channel: Int): Long
    external fun engineChannelStretchMode(handle: Long, channel: Int): Int
    external fun engineSetChannelStretchMode(handle: Long, channel: Int, mode: Int): Boolean
    external fun engineChannelStretchMultiplier(handle: Long, channel: Int): Double
    external fun engineSetChannelStretchMultiplier(handle: Long, channel: Int, multiplier: Double): Boolean
    external fun engineChannelStretchPitch(handle: Long, channel: Int): Int
    external fun engineSetChannelStretchPitch(handle: Long, channel: Int, cents: Int): Boolean
    external fun engineSetChannelFxFlag(handle: Long, channel: Int, flag: Int, on: Boolean): Boolean
    external fun engineChannelFxFlag(handle: Long, channel: Int, flag: Int): Boolean
    external fun engineSetChannelFxTrim(handle: Long, channel: Int, start: Double, length: Double): Boolean
    external fun engineChannelFxTrimStart(handle: Long, channel: Int): Double
    external fun engineChannelFxTrimLength(handle: Long, channel: Int): Double
    external fun engineSetChannelFxNormalize(handle: Long, channel: Int, on: Boolean): Boolean
    external fun engineChannelFxNormalize(handle: Long, channel: Int): Boolean
    external fun engineClearSampleCache()
}
