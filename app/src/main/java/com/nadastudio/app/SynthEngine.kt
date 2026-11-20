package com.nadastudio.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

enum class Wave(val label: String) {
    SINE("Sine"), SQUARE("Square"), SAW("Saw"), TRIANGLE("Triangle"),
    ORGAN("Organ"), PIANO("Piano"), PLUCK("Pluck"), BELL("Bell")
}

val CHANNEL_NAMES = listOf("Kick", "Snare", "Clap", "Hat", "Open Hat", "Synth")
const val STEPS = 16
const val SYNTH_CH = 5

/** Synth polifonik + drum + step sequencer. */
class SynthEngine {
    private val sampleRate = 44100

    private class Voice(val note: Int, val freq: Double) {
        var phase = 0.0
        var mphase = 0.0
        var age = 0L
        var level = 0.0
        @Volatile var releasing = false
    }

    private class Hit(val type: Int) {
        var t = 0
        var phase = 0.0
        var last = 0.0
    }

    private val voices = CopyOnWriteArrayList<Voice>()
    private val hits = CopyOnWriteArrayList<Hit>()

    val steps = BooleanArray(CHANNEL_NAMES.size * STEPS)
    val chMute = BooleanArray(CHANNEL_NAMES.size)

    @Volatile var wave = Wave.SINE
    @Volatile var volume = 0.8f
    @Volatile var bpm = 120
    @Volatile var metronomeOn = false
    @Volatile var seqNote = 60
    @Volatile var playing = false
    @Volatile var currentStep = 0
    @Volatile private var resetReq = false
    @Volatile private var running = false
    @Volatile private var pos = 0L
    @Volatile private var gateNote = -1
    @Volatile private var offAt = 0L
    private var seed = 12345
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    init {
        for (s in listOf(0, 4, 8, 12)) steps[0 * STEPS + s] = true
        for (s in listOf(4, 12)) steps[1 * STEPS + s] = true
        for (s in 0 until STEPS step 2) steps[3 * STEPS + s] = true
    }

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
        playing = false
        thread?.join(500)
        track?.stop()
        track?.release()
        track = null
        voices.clear()
        hits.clear()
    }

    fun startSeq() { resetReq = true; playing = true }
    fun stopSeq() { playing = false }

    fun noteOn(note: Int) {
        if (voices.any { it.note == note && !it.releasing }) return
        voices.add(Voice(note, 440.0 * 2.0.pow((note - 69) / 12.0)))
    }

    fun noteOff(note: Int) {
        voices.filter { it.note == note && !it.releasing }.forEach { it.releasing = true }
    }

    /** Coba suara satu channel. */
    fun preview(ch: Int) {
        if (ch == SYNTH_CH) {
            if (gateNote >= 0) noteOff(gateNote)
            noteOn(seqNote)
            gateNote = seqNote
            offAt = pos + sampleRate / 4
        } else {
            hits.add(Hit(ch))
        }
    }

    private fun nextNoise(): Double {
        seed = seed * 1664525 + 1013904223
        return (seed ushr 8) / 8388608.0 - 1.0
    }

    private fun renderLoop(t: AudioTrack, frames: Int) {
        val buf = ShortArray(frames)
        val attack = 1.0 / (0.005 * sampleRate)
        val release = 1.0 / (0.2 * sampleRate)
        val clickLen = (0.03 * sampleRate).toInt()
        var clickPos = Int.MAX_VALUE
        var clickFreq = 1000.0
        var seqCounter = 0L
        var step = 0
        while (running) {
            val w = wave
            val vol = volume
            val perBeat = (sampleRate * 60.0 / bpm).toLong().coerceAtLeast(4)
            val stepLen = perBeat / 4
            for (i in 0 until frames) {
                if (resetReq) { resetReq = false; seqCounter = 0; step = 0 }
                var stepTrig = false
                if (playing) {
                    if (seqCounter == 0L) {
                        stepTrig = true
                        currentStep = step
                        for (ch in CHANNEL_NAMES.indices) {
                            if (steps[ch * STEPS + step] && !chMute[ch]) {
                                if (ch == SYNTH_CH) {
                                    if (gateNote >= 0) noteOff(gateNote)
                                    noteOn(seqNote)
                                    gateNote = seqNote
                                    offAt = pos + stepLen * 9 / 10
                                } else {
                                    hits.add(Hit(ch))
                                }
                            }
                        }
                    }
                    seqCounter++
                    if (seqCounter >= stepLen) { seqCounter = 0; step = (step + 1) % STEPS }
                }
                if (gateNote >= 0 && pos >= offAt) { noteOff(gateNote); gateNote = -1 }

                var s = 0.0
                for (v in voices) {
                    v.level = if (v.releasing) v.level - release else minOf(1.0, v.level + attack)
                    if (v.level <= 0.0) { voices.remove(v); continue }
                    val p = v.phase
                    var dec = 1.0
                    val raw = when (w) {
                        Wave.SINE -> sin(2 * PI * p)
                        Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
                        Wave.SAW -> 2 * p - 1
                        Wave.TRIANGLE -> 4 * abs(p - 0.5) - 1
                        Wave.ORGAN -> (sin(2 * PI * p) + 0.5 * sin(4 * PI * p) + 0.25 * sin(6 * PI * p)) * 0.6
                        Wave.PIANO -> {
                            dec = exp(-v.age / (0.5 * sampleRate))
                            (sin(2 * PI * p) + 0.5 * sin(4 * PI * p) + 0.25 * sin(6 * PI * p) + 0.12 * sin(8 * PI * p)) * 0.6
                        }
                        Wave.PLUCK -> {
                            dec = exp(-v.age / (0.15 * sampleRate))
                            2 * p - 1
                        }
                        Wave.BELL -> {
                            dec = exp(-v.age / (0.7 * sampleRate))
                            sin(2 * PI * p + 2.5 * dec * sin(2 * PI * v.mphase))
                        }
                    }
                    s += raw * v.level * dec * 0.25
                    v.phase += v.freq / sampleRate
                    if (v.phase >= 1.0) v.phase -= 1.0
                    v.mphase += v.freq * 3.5 / sampleRate
                    if (v.mphase >= 1.0) v.mphase -= 1.0
                    v.age++
                }

                for (h in hits) {
                    val tt = h.t / sampleRate.toDouble()
                    val n = nextNoise()
                    val d: Double = when (h.type) {
                        0 -> {
                            val f = 45 + 110 * exp(-tt / 0.03)
                            h.phase += f / sampleRate
                            sin(2 * PI * h.phase) * exp(-tt / 0.14)
                        }
                        1 -> n * exp(-tt / 0.07) * 0.6 + sin(2 * PI * 190 * tt) * exp(-tt / 0.04) * 0.5
                        2 -> n * exp(-tt / 0.06) * 0.7 * (if (tt < 0.03) 0.6 + 0.4 * sin(2 * PI * 130 * tt) else 1.0)
                        3 -> {
                            val hp = n - h.last
                            h.last = n
                            hp * exp(-tt / 0.03) * 0.5
                        }
                        else -> {
                            val hp = n - h.last
                            h.last = n
                            hp * exp(-tt / 0.18) * 0.5
                        }
                    }
                    s += d * 0.9
                    h.t++
                    if (h.t > sampleRate * 0.7) hits.remove(h)
                }

                if (metronomeOn) {
                    val beat = if (playing) stepTrig && currentStep % 4 == 0 else pos % perBeat == 0L
                    if (beat) {
                        clickPos = 0
                        clickFreq = if (playing) {
                            if (currentStep == 0) 1500.0 else 1000.0
                        } else {
                            if ((pos / perBeat) % 4 == 0L) 1500.0 else 1000.0
                        }
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
