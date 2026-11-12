package com.maxixa.lemon.state

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

class AudioMonitor(private val sampleRate: Int = 44100) {
    private val tag = "Lemon"
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val queue = LinkedBlockingQueue<ByteArray>(8)

    fun start(): Boolean {
        if (running) return false
        val minBuf = try {
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        } catch (t: Throwable) {
            Log.e(tag, "AudioTrack.getMinBufferSize failed", t)
            return false
        }
        if (minBuf <= 0) {
            Log.e(tag, "AudioTrack.getMinBufferSize returned $minBuf")
            return false
        }
        val bufSize = minBuf * 2
        val t = try {
            AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        } catch (t: Throwable) {
            Log.e(tag, "AudioTrack construct failed", t)
            return false
        }
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(tag, "AudioTrack not initialized")
            try { t.release() } catch (_: Throwable) {}
            return false
        }
        try { t.play() } catch (t2: Throwable) {
            Log.e(tag, "AudioTrack.play failed", t2)
            try { t.release() } catch (_: Throwable) {}
            return false
        }
        track = t
        running = true
        thread = Thread {
            while (running) {
                val buf = try { queue.take() } catch (_: InterruptedException) { break }
                if (!running) break
                try { track?.write(buf, 0, buf.size) }
                catch (t3: Throwable) { Log.e(tag, "AudioTrack.write failed", t3) }
            }
        }.also { it.start() }
        Log.i(tag, "monitor started @ ${sampleRate}Hz, buf=$bufSize")
        return true
    }

    fun feed(buf: ByteArray, offset: Int, len: Int) {
        if (!running || len <= 0) return
        val copy = ByteArray(len)
        System.arraycopy(buf, offset, copy, 0, len)
        if (!queue.offer(copy)) {
            // Queue full — drop frame, recorder continues unaffected
        }
    }

    fun stop() {
        running = false
        try { thread?.interrupt() } catch (_: Throwable) {}
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
        try { track?.stop() } catch (_: Throwable) {}
        try { track?.release() } catch (_: Throwable) {}
        track = null
        queue.clear()
        Log.i(tag, "monitor stopped")
    }
}
