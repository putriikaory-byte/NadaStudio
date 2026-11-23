package com.nadastudio.app

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavExport {
    /** Tulis audio 16-bit ke format WAV. Data stereo disusun selang-seling kiri, kanan. */
    fun write(out: OutputStream, samples: ShortArray, sampleRate: Int = 44100, channels: Int = 2) {
        val dataLen = samples.size * 2
        val head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        head.put("RIFF".toByteArray())
        head.putInt(36 + dataLen)
        head.put("WAVE".toByteArray())
        head.put("fmt ".toByteArray())
        head.putInt(16)
        head.putShort(1.toShort())
        head.putShort(channels.toShort())
        head.putInt(sampleRate)
        head.putInt(sampleRate * channels * 2)
        head.putShort((channels * 2).toShort())
        head.putShort(16.toShort())
        head.put("data".toByteArray())
        head.putInt(dataLen)
        out.write(head.array())

        val chunk = ByteBuffer.allocate(8192 * 2).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (i < samples.size) {
            chunk.clear()
            val n = minOf(8192, samples.size - i)
            for (k in 0 until n) chunk.putShort(samples[i + k])
            out.write(chunk.array(), 0, n * 2)
            i += n
        }
        out.flush()
    }
}
