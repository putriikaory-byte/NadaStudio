package com.nadastudio.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

enum class Wave(val label: String) { SINE("Sine"), SQUARE("Square"), SAW("Saw"), TRIANGLE("Triangle") }

/** Synth polifonik sederhana + metronom. Langkah berikutnya: ganti dengan SoundFont. */
class SynthEngine {
    private val sampleRate = 44100

    private class Voice(val note: Int, val freq: Double) {
        var phase = 0.0
        var level = 0.0
        @Volatile var releasing = false
    }

    private val voices = CopyOnWriteArrayList<Voice>()
    @Volatile var wave = Wave.SINE
    @Volatile var volume = 0.8f
    @Volatile var bpm = 120
    @Volatile var metronomeOn = false
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        val frames = 256
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, frames * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track = t
        running = true
        t.play()
        thread = Thread({ renderLoop(t, frames) }, "synth").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        track?.stop()
        track?.release()
        track = null
        voices.clear()
    }

    fun noteOn(note: Int) {
        if (voices.any { it.note == note && !it.releasing }) return
        voices.add(Voice(note, 440.0 * 2.0.pow((note - 69) / 12.0)))
    }

    fun noteOff(note: Int) {
        voices.filter { it.note == note && !it.releasing }.forEach { it.releasing = true }
    }

    private fun renderLoop(t: AudioTrack, frames: Int) {
        val buf = ShortArray(frames)
        val attack = 1.0 / (0.005 * sampleRate)
        val release = 1.0 / (0.2 * sampleRate)
        val clickLen = (0.03 * sampleRate).toInt()
        var pos = 0L
        var clickPos = Int.MAX_VALUE
        var clickFreq = 1000.0
        while (running) {
            val w = wave
            val vol = volume
            val perBeat = (sampleRate * 60.0 / bpm).toLong().coerceAtLeast(1)
            for (i in 0 until frames) {
                var s = 0.0
                for (v in voices) {
                    v.level = if (v.releasing) v.level - release else minOf(1.0, v.level + attack)
                    if (v.level <= 0.0) { voices.remove(v); continue }
                    val p = v.phase
                    val raw = when (w) {
                        Wave.SINE -> sin(2 * PI * p)
                        Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
                        Wave.SAW -> 2 * p - 1
                        Wave.TRIANGLE -> 4 * abs(p - 0.5) - 1
                    }
                    s += raw * v.level * 0.25
                    v.phase += v.freq / sampleRate
                    if (v.phase >= 1.0) v.phase -= 1.0
                }
                if (metronomeOn) {
                    if (pos % perBeat == 0L) {
                        clickPos = 0
                        clickFreq = if ((pos / perBeat) % 4 == 0L) 1500.0 else 1000.0
                    }
                    if (clickPos < clickLen) {
                        val env = 1.0 - clickPos.toDouble() / clickLen
                        s += sin(2 * PI * clickFreq * clickPos / sampleRate) * env * 0.4
                        clickPos++
                    }
                }
                pos++
                buf[i] = (tanh(s * vol) * 32767 * 0.9).toInt().toShort()
            }
            t.write(buf, 0, frames)
        }
    }
}
