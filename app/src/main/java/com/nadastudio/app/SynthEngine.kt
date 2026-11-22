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

/** atk/rel = waktu naik dan turun volume (detik), vib = ada getaran halus. */
enum class Wave(
    val label: String,
    val atk: Double = 0.005,
    val rel: Double = 0.2,
    val vib: Boolean = false
) {
    SINE("Sine"), SQUARE("Square"), SAW("Saw"), TRIANGLE("Triangle"),
    PIANO("Piano"),
    EPIANO("Piano listrik", rel = 0.3),
    ORGAN("Organ"),
    GUITAR("Gitar akustik", rel = 0.25),
    DISTGTR("Gitar distorsi"),
    HARP("Harpa", rel = 0.5),
    PLUCK("Pluck"),
    FLUTE("Seruling", atk = 0.06, rel = 0.15, vib = true),
    SULING("Suling bambu", atk = 0.05, rel = 0.15, vib = true),
    CLARINET("Klarinet", atk = 0.03),
    OBOE("Oboe", atk = 0.04, vib = true),
    TRUMPET("Terompet", atk = 0.03),
    SAX("Saksofon", atk = 0.04, vib = true),
    VIOLIN("Biola", atk = 0.08, rel = 0.25, vib = true),
    CELLO("Cello", atk = 0.1, rel = 0.3, vib = true),
    STRINGS("Strings", atk = 0.15, rel = 0.4),
    BELL("Bell"),
    MARIMBA("Marimba", atk = 0.001, rel = 0.1),
    XYLOPHONE("Xilofon", atk = 0.001, rel = 0.08),
    GAMELAN("Gamelan", atk = 0.001, rel = 0.3),
    BASS("Bass"),
    BASS808("Bass 808", rel = 0.25),
    LEAD("Lead"),
    PAD("Pad", atk = 0.4, rel = 0.6),
    CHOIR("Paduan suara", atk = 0.25, rel = 0.4, vib = true)
}

val CHANNEL_NAMES = listOf("Kick", "Snare", "Clap", "Hat", "Open Hat", "Synth")
val TRACK_NAMES = listOf("Melodi", "Bass", "Akor")
const val STEPS = 16
const val SYNTH_CH = 5
const val ROLL_ROWS = 60
const val ROLL_LOW = 36
const val TRACKS = 3
const val ROLL_SIZE = ROLL_ROWS * STEPS

/** Synth polifonik + drum + step sequencer + piano roll multi-track. */
class SynthEngine {
    private val sampleRate = 44100
    private val atkRate = DoubleArray(Wave.values().size) { 1.0 / (Wave.values()[it].atk * sampleRate) }
    private val relRate = DoubleArray(Wave.values().size) { 1.0 / (Wave.values()[it].rel * sampleRate) }

    private class Voice(
        val note: Int,
        val freq: Double,
        val wave: Wave,
        val gain: Float,
        val owner: Int
    ) {
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
    val rollLen = IntArray(TRACKS * ROLL_SIZE)
    val trackWave = arrayOf(Wave.PIANO, Wave.BASS, Wave.STRINGS)
    val trackMute = BooleanArray(TRACKS)
    val trackGain = FloatArray(TRACKS) { 0.8f }
    private val rollLeft = IntArray(TRACKS * ROLL_ROWS)
    private val rollNote = IntArray(TRACKS * ROLL_ROWS)

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
    @Volatile private var rollActive = false
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
        for (k in 0 until TRACKS * ROLL_ROWS) rollLeft[k] = 0
        rollActive = false
    }

    fun startSeq() { resetReq = true; playing = true }
    fun stopSeq() { playing = false }

    fun noteOn(note: Int, w: Wave = wave, gain: Float = 1f, owner: Int = -1) {
        if (voices.any { it.note == note && it.owner == owner && !it.releasing }) return
        voices.add(Voice(note, 440.0 * 2.0.pow((note - 69) / 12.0), w, gain, owner))
    }

    fun noteOff(note: Int, owner: Int = -1) {
        voices.filter { it.note == note && it.owner == owner && !it.releasing }
            .forEach { it.releasing = true }
    }

    /** Bunyikan satu nada singkat; t = nomor track (untuk memakai instrumennya). */
    fun previewNote(n: Int, t: Int = -1) {
        if (gateNote >= 0) noteOff(gateNote)
        noteOn(n, if (t >= 0) trackWave[t] else wave)
        gateNote = n
        offAt = pos + sampleRate / 2
    }

    /** Coba suara satu channel. */
    fun preview(ch: Int) {
        if (ch == SYNTH_CH) previewNote(seqNote) else hits.add(Hit(ch))
    }

    private fun nextNoise(): Double {
        seed = seed * 1664525 + 1013904223
        return (seed ushr 8) / 8388608.0 - 1.0
    }

    /** Jumlah gelombang sinus harmonik 1 sampai 6 dengan bobot masing-masing. */
    private fun additive(
        p: Double, a1: Double, a2: Double, a3: Double, a4: Double, a5: Double, a6: Double
    ): Double =
        a1 * sin(2 * PI * p) + a2 * sin(4 * PI * p) + a3 * sin(6 * PI * p) +
            a4 * sin(8 * PI * p) + a5 * sin(10 * PI * p) + a6 * sin(12 * PI * p)

    private fun renderLoop(t: AudioTrack, frames: Int) {
        val buf = ShortArray(frames)
        val clickLen = (0.03 * sampleRate).toInt()
        var clickPos = Int.MAX_VALUE
        var clickFreq = 1000.0
        var seqCounter = 0L
        var step = 0
        while (running) {
            val vol = volume
            val perBeat = (sampleRate * 60.0 / bpm).toLong().coerceAtLeast(4)
            val stepLen = perBeat / 4
            for (i in 0 until frames) {
                if (resetReq) { resetReq = false; seqCounter = 0; step = 0 }
                if (!playing && rollActive) {
                    for (k in 0 until TRACKS * ROLL_ROWS) {
                        if (rollLeft[k] > 0) { noteOff(rollNote[k], k / ROLL_ROWS); rollLeft[k] = 0 }
                    }
                    rollActive = false
                }
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
                        for (tr in 0 until TRACKS) {
                            for (r in 0 until ROLL_ROWS) {
                                val k = tr * ROLL_ROWS + r
                                if (rollLeft[k] > 0) {
                                    rollLeft[k]--
                                    if (rollLeft[k] == 0) noteOff(rollNote[k], tr)
                                }
                            }
                        }
                        for (tr in 0 until TRACKS) {
                            for (r in 0 until ROLL_ROWS) {
                                val l = rollLen[tr * ROLL_SIZE + r * STEPS + step]
                                if (l > 0 && !trackMute[tr]) {
                                    val k = tr * ROLL_ROWS + r
                                    if (rollLeft[k] > 0) noteOff(rollNote[k], tr)
                                    rollNote[k] = ROLL_LOW + r
                                    noteOn(rollNote[k], trackWave[tr], trackGain[tr], tr)
                                    rollLeft[k] = l
                                    rollActive = true
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
                    val wv = v.wave
                    val oi = wv.ordinal
                    v.level = if (v.releasing) v.level - relRate[oi] else minOf(1.0, v.level + atkRate[oi])
                    if (v.level <= 0.0) { voices.remove(v); continue }
                    val p = v.phase
                    val m = v.mphase
                    val ts = v.age / sampleRate.toDouble()
                    var dec = 1.0
                    val raw = when (wv) {
                        Wave.SINE -> sin(2 * PI * p)
                        Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
                        Wave.SAW -> 2 * p - 1
                        Wave.TRIANGLE -> 4 * abs(p - 0.5) - 1
                        Wave.PIANO -> {
                            dec = exp(-ts / 0.5)
                            additive(p, 1.0, 0.5, 0.25, 0.12, 0.0, 0.0) * 0.6
                        }
                        Wave.EPIANO -> {
                            dec = exp(-ts / 1.2)
                            sin(2 * PI * p + 1.6 * exp(-ts / 0.25) * sin(2 * PI * m))
                        }
                        Wave.ORGAN -> additive(p, 1.0, 0.5, 0.25, 0.0, 0.0, 0.0) * 0.6
                        Wave.GUITAR -> {
                            dec = exp(-ts / 0.45)
                            additive(p, 1.0, 0.6, 0.4, 0.25, 0.0, 0.0) * 0.5
                        }
                        Wave.DISTGTR -> {
                            dec = exp(-ts / 1.5)
                            tanh(3.0 * additive(p, 1.0, 0.5, 0.35, 0.0, 0.0, 0.0)) * 0.6
                        }
                        Wave.HARP -> {
                            dec = exp(-ts / 1.0)
                            additive(p, 1.0, 0.4, 0.2, 0.1, 0.0, 0.0) * 0.6
                        }
                        Wave.PLUCK -> {
                            dec = exp(-ts / 0.15)
                            2 * p - 1
                        }
                        Wave.FLUTE -> additive(p, 1.0, 0.22, 0.07, 0.02, 0.0, 0.0) * 0.6 + nextNoise() * 0.025
                        Wave.SULING -> additive(p, 1.0, 0.12, 0.04, 0.0, 0.0, 0.0) * 0.6 + nextNoise() * 0.05
                        Wave.CLARINET -> additive(p, 1.0, 0.0, 0.55, 0.0, 0.3, 0.0) * 0.5
                        Wave.OBOE -> additive(p, 0.6, 1.0, 0.9, 0.5, 0.3, 0.15) * 0.3
                        Wave.TRUMPET -> additive(p, 1.0, 0.8, 0.65, 0.5, 0.35, 0.2) * 0.25
                        Wave.SAX -> additive(p, 1.0, 0.6, 0.45, 0.3, 0.2, 0.1) * 0.35 + nextNoise() * 0.02
                        Wave.VIOLIN -> ((2 * p - 1) * 0.6 + (2 * m - 1) * 0.4) * 0.5
                        Wave.CELLO -> additive(p, 1.0, 0.7, 0.5, 0.35, 0.2, 0.1) * 0.3
                        Wave.STRINGS -> ((2 * p - 1) + (2 * m - 1)) * 0.35
                        Wave.BELL -> {
                            dec = exp(-ts / 0.7)
                            sin(2 * PI * p + 2.5 * dec * sin(2 * PI * m))
                        }
                        Wave.MARIMBA -> {
                            dec = exp(-ts / 0.35)
                            (sin(2 * PI * p) + 0.25 * sin(8 * PI * p)) * 0.9
                        }
                        Wave.XYLOPHONE -> {
                            dec = exp(-ts / 0.18)
                            (sin(2 * PI * p) + 0.5 * sin(6 * PI * p) + 0.25 * sin(12 * PI * p)) * 0.7
                        }
                        Wave.GAMELAN -> {
                            dec = exp(-ts / 0.9)
                            (sin(2 * PI * p) + 0.5 * sin(2 * PI * m) + 0.2 * sin(4 * PI * p)) * 0.7
                        }
                        Wave.BASS -> sin(2 * PI * p) * 0.8 + (2 * p - 1) * 0.2
                        Wave.BASS808 -> {
                            dec = exp(-ts / 0.8)
                            sin(2 * PI * p)
                        }
                        Wave.LEAD -> (if (p < 0.5) 1.0 else -1.0) * 0.35 + (2 * p - 1) * 0.35
                        Wave.PAD -> (sin(2 * PI * p) + 0.6 * sin(2 * PI * m) + 0.3 * sin(4 * PI * p)) * 0.4
                        Wave.CHOIR -> additive(p, 0.6, 0.5, 1.0, 0.8, 0.4, 0.2) * 0.22
                    }
                    s += raw * v.level * dec * 0.25 * v.gain

                    var pf = 1.0
                    if (wv.vib) pf = 1.0 + 0.0045 * sin(2 * PI * 5.5 * ts) * minOf(1.0, ts / 0.35)
                    if (wv == Wave.BASS808) pf = 1.0 + 1.2 * exp(-ts / 0.03)
                    v.phase += v.freq * pf / sampleRate
                    if (v.phase >= 1.0) v.phase -= 1.0
                    val ratio = when (wv) {
                        Wave.BELL -> 3.5
                        Wave.EPIANO -> 1.0
                        Wave.GAMELAN -> 2.76
                        else -> 1.006
                    }
                    v.mphase += v.freq * ratio / sampleRate
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
