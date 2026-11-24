package com.nadastudio.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

const val CLIP_RATE = 44100
const val CLIP_MAX_SEC = 120

/** Merekam suara dari mikrofon (mono, 44100 Hz, 16-bit). */
class VoiceRecorder {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val lock = Any()
    private var data = ShortArray(CLIP_RATE * 10)
    private var len = 0

    val seconds: Int
        get() = synchronized(lock) { len / CLIP_RATE }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return false
        val minBuf = AudioRecord.getMinBufferSize(
            CLIP_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, CLIP_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 8192) * 2
            )
        } catch (e: Throwable) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        synchronized(lock) { len = 0 }
        rec = r
        running = true
        r.startRecording()
        thread = Thread({
            val buf = ShortArray(2048)
            val maxLen = CLIP_RATE * CLIP_MAX_SEC
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) {
                    synchronized(lock) {
                        val take = minOf(n, maxLen - len)
                        if (take > 0) {
                            if (len + take > data.size) data = data.copyOf(maxOf(data.size * 2, len + take))
                            System.arraycopy(buf, 0, data, len, take)
                            len += take
                        }
                        if (len >= maxLen) running = false
                    }
                } else if (n < 0) {
                    running = false
                }
            }
        }, "rekam").also { it.start() }
        return true
    }

    fun stop(): ShortArray? {
        val r = rec ?: return null
        running = false
        thread?.join(800)
        try { r.stop() } catch (e: Throwable) { }
        r.release()
        rec = null
        thread = null
        val out = synchronized(lock) { data.copyOf(len) }
        return normalize(out)
    }

    private fun normalize(x: ShortArray): ShortArray {
        var peak = 0
        for (s in x) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
        }
        if (peak < 200) return x
        val g = minOf(6.0, 0.9 * 32767.0 / peak)
        if (g <= 1.05) return x
        return ShortArray(x.size) { (x[it] * g).toInt().coerceIn(-32768, 32767).toShort() }
    }
}

/** Membaca file audio (WAV, MP3, M4A, OGG, dan lainnya) menjadi mono 44100 Hz. */
object AudioDecoder {
    fun decode(ctx: Context, uri: Uri): ShortArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) { track = i; fmt = f; break }
            }
            if (track < 0 || fmt == null) return null
            ex.selectTrack(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            val info = MediaCodec.BufferInfo()
            var pcm = ShortArray(CLIP_RATE * 10)
            var n = 0
            var inputDone = false
            var outputDone = false
            val t0 = System.currentTimeMillis()
            while (!outputDone) {
                if (System.currentTimeMillis() - t0 > 90000) break
                if (!inputDone) {
                    val ii = c.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val ib = c.getInputBuffer(ii)
                        val size = if (ib != null) ex.readSampleData(ib, 0) else -1
                        if (size < 0) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 10000)
                if (oi >= 0) {
                    val ob = c.getOutputBuffer(oi)
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val cnt = sb.remaining()
                        if (n + cnt > pcm.size) pcm = pcm.copyOf(maxOf(pcm.size * 2, n + cnt))
                        sb.get(pcm, n, cnt)
                        n += cnt
                    }
                    c.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    if (n / maxOf(ch, 1) >= rate * CLIP_MAX_SEC) outputDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            return toMono44(pcm, n, rate, maxOf(ch, 1))
        } catch (e: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (e: Throwable) { }
            try { codec?.release() } catch (e: Throwable) { }
            ex.release()
        }
    }

    private fun toMono44(pcm: ShortArray, n: Int, rate: Int, ch: Int): ShortArray? {
        val frames = n / ch
        if (frames <= 0 || rate <= 0) return null
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var s = 0
            for (c in 0 until ch) s += pcm[i * ch + c]
            mono[i] = s.toFloat() / ch
        }
        val outLen = if (rate == CLIP_RATE) frames else (frames.toLong() * CLIP_RATE / rate).toInt()
        val out = ShortArray(minOf(outLen, CLIP_RATE * CLIP_MAX_SEC))
        if (rate == CLIP_RATE) {
            for (i in out.indices) out[i] = mono[i].toInt().toShort()
        } else {
            val ratio = rate.toDouble() / CLIP_RATE
            for (i in out.indices) {
                val p = i * ratio
                val i0 = p.toInt()
                val i1 = minOf(i0 + 1, frames - 1)
                val fr = (p - i0).toFloat()
                out[i] = (mono[i0] + (mono[i1] - mono[i0]) * fr).toInt().toShort()
            }
        }
        return out
    }
}

/** Menyimpan suara vokal di penyimpanan aplikasi supaya tidak hilang saat aplikasi ditutup. */
object VocalStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "vokal.raw")

    fun save(ctx: Context, c: ShortArray?) {
        try {
            val f = file(ctx)
            if (c == null) {
                f.delete()
                return
            }
            val bb = ByteBuffer.allocate(c.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            bb.asShortBuffer().put(c)
            f.writeBytes(bb.array())
        } catch (e: Throwable) { }
    }

    fun load(ctx: Context): ShortArray? = try {
        val f = file(ctx)
        if (!f.exists() || f.length() < 4) {
            null
        } else {
            val bytes = f.readBytes()
            val out = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
            out
        }
    } catch (e: Throwable) {
        null
    }
}
