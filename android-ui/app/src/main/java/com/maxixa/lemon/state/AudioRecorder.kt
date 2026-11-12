package com.maxixa.lemon.state

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records mono 16-bit PCM from the microphone into a WAV file.
 * Simple: start() spawns a thread that fills the file, stop()
 * joins the thread and rewrites the header with the final size.
 */
class AudioRecorder(private val sampleRate: Int = 44100) {
    private val tag = "Lemon"
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var recording = false

    fun start(outputFile: File, monitor: AudioMonitor? = null): Boolean {
        if (recording) return false

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            Log.e(tag, "AudioRecord.getMinBufferSize failed: $minBuf")
            return false
        }
        val bufSize = minBuf * 4

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
            )
        } catch (t: Throwable) {
            Log.e(tag, "AudioRecord construct failed", t)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(tag, "AudioRecord not initialized")
            rec.release()
            return false
        }
        record = rec
        recording = true

        thread = Thread {
            outputFile.parentFile?.mkdirs()
            val raf = RandomAccessFile(outputFile, "rw")
            try {
                raf.setLength(0)
                raf.write(ByteArray(44))     // placeholder header
                val buf = ByteArray(bufSize)
                var totalBytes = 0L
                rec.startRecording()
                while (recording) {
                    val n = rec.read(buf, 0, bufSize)
                    if (n > 0) {
                        raf.write(buf, 0, n)
                        monitor?.feed(buf, 0, n)
                        totalBytes += n
                    }
                }
                rec.stop()
                raf.seek(0)
                raf.write(makeWavHeader(totalBytes))
                Log.i(tag, "recording saved: ${outputFile.absolutePath} ($totalBytes bytes)")
            } catch (t: Throwable) {
                Log.e(tag, "recording thread failed", t)
            } finally {
                try { raf.close() } catch (_: Throwable) {}
                try { rec.release() } catch (_: Throwable) {}
                record = null
            }
        }.also { it.start() }

        return true
    }

    fun stop() {
        recording = false
        try { thread?.join(3000) } catch (_: InterruptedException) {}
        thread = null
    }

    private fun makeWavHeader(dataLen: Long): ByteArray {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt((36 + dataLen).toInt())
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)                       // fmt chunk size
        buf.putShort(1)                      // PCM
        buf.putShort(1)                      // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)           // byte rate = rate * 2 (mono 16-bit)
        buf.putShort(2)                      // block align
        buf.putShort(16)                     // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataLen.toInt())
        return buf.array()
    }
}
