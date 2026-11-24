package com.nadastudio.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
val MIX_NAMES = listOf("Kick", "Snare", "Clap", "Hat", "Open Hat", "Synth", "Melodi", "Bass", "Akor", "Live", "Vokal")
const val STEPS = 16
const val SYNTH_CH = 5
const val ROLL_ROWS = 60
const val ROLL_LOW = 36
const val TRACKS = 3
const val ROLL_SIZE = ROLL_ROWS * STEPS
const val MIX_CH = 11
const val LIVE_STRIP = 9
const val VOCAL_STRIP = 10

/** Synth polifonik + drum + step sequencer + piano roll + mixer stereo + jalur vokal. */
class SynthEngine {
    private val sampleRate = 44100
    private val atkRate = DoubleArray(Wave.values().size) { 1.0 / (Wave.values()[it].atk * sampleRate) }
    private val relRate = DoubleArray(Wave.values().size) { 1.0 / (Wave.values()[it].rel * sampleRate) }

    private class Voice(
        val note: Int,
        val freq: Double,
        val wave: Wave,
        val gain: Float,
        val owner: Int,
        val strip: Int
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

    private class Comb(size: Int) {
        private val buf = DoubleArray(size)
        private var idx = 0
        private var store = 0.0
        fun process(x: Double): Double {
            val y = buf[idx]
            store = y * 0.8 + store * 0.2
            buf[idx] = x + store * 0.84
            idx++
            if (idx >= buf.size) idx = 0
            return y
        }
        fun clear() { buf.fill(0.0); idx = 0; store = 0.0 }
    }

    private class Allpass(size: Int) {
        private val buf = DoubleArray(size)
        private var idx = 0
        fun process(x: Double): Double {
            val b = buf[idx]
            val y = -x + b
            buf[idx] = x + b * 0.5
            idx++
            if (idx >= buf.size) idx = 0
            return y
        }
        fun clear() { buf.fill(0.0); idx = 0 }
    }

    private class Reverb(spread: Int) {
        private val combs = arrayOf(
            Comb(1116 + spread), Comb(1188 + spread), Comb(1277 + spread), Comb(1356 + spread)
        )
        private val ap1 = Allpass(556 + spread)
        private val ap2 = Allpass(441 + spread)
        fun process(x: Double): Double {
            val inp = x * 0.015
            var s = 0.0
            for (c in combs) s += c.process(inp)
            return ap2.process(ap1.process(s))
        }
        fun clear() {
            for (c in combs) c.clear()
            ap1.clear()
            ap2.clear()
        }
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

    val mixVol = FloatArray(MIX_CH) { 0.8f }
    val mixPan = FloatArray(MIX_CH)
    val mixSolo = BooleanArray(MIX_CH)
    val peak = FloatArray(MIX_CH)
    private val gainL = DoubleArray(MIX_CH)
    private val gainR = DoubleArray(MIX_CH)
    private val blockPeak = DoubleArray(MIX_CH)
    private val revL = Reverb(0)
    private val revR = Reverb(23)
    private val delayBufL = DoubleArray(sampleRate * 2)
    private val delayBufR = DoubleArray(sampleRate * 2)
    private var delayW = 0

    @Volatile var wave = Wave.SINE
    @Volatile var volume = 0.8f
    @Volatile var bpm = 120
    @Volatile var metronomeOn = false
    @Volatile var seqNote = 60
    @Volatile var playing = false
    @Volatile var currentStep = 0
    @Volatile var exporting = false
    @Volatile var liveMute = false
    @Volatile var vocalMute = false
    @Volatile var reverbMix = 0.15f
    @Volatile var delayMix = 0f
    @Volatile var vocal: ShortArray? = null
    @Volatile var vocalActive = false
    @Volatile var vocalSuppress = false
    @Volatile var vocalLoop = false
    @Volatile var vocalShiftMs = 0
    @Volatile private var clipResetReq = false
    @Volatile private var resetReq = false
    @Volatile private var running = false
    @Volatile private var pos = 0L
    @Volatile private var gateNote = -1
    @Volatile private var gateOwner = -1
    @Volatile private var offAt = 0L
    @Volatile private var rollActive = false
    private var clipPos = 0L
    private var seqCounter = 0L
    private var seqStep = 0
    private var clickPos = Int.MAX_VALUE
    private var clickFreq = 1000.0
    private var seed = 12345
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    init {
        for (s in listOf(0, 4, 8, 12)) steps[0 * STEPS + s] = true
        for (s in listOf(4, 12)) steps[1 * STEPS + s] = true
        for (s in 0 until STEPS step 2) steps[3 * STEPS + s] = true
    }

    fun start() {
        if (running || exporting) return
        val frames = 256
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, frames * 8))
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
        if (exporting) return
        running = false
        playing = false
        vocalActive = false
        thread?.join(500)
        track?.stop()
        track?.release()
        track = null
        voices.clear()
        hits.clear()
        for (k in 0 until TRACKS * ROLL_ROWS) rollLeft[k] = 0
        rollActive = false
    }

    fun startSeq() {
        resetReq = true
        clipResetReq = true
        vocalActive = true
        playing = true
    }

    fun stopSeq() {
        playing = false
        vocalActive = false
    }

    /** Putar suara vokal saja, tanpa aransemen. */
    fun playClip() {
        clipResetReq = true
        vocalActive = true
    }

    fun stopClip() { vocalActive = false }

    private fun stripOf(owner: Int): Int = when {
        owner in 0 until TRACKS -> CHANNEL_NAMES.size + owner
        owner == SYNTH_CH -> SYNTH_CH
        else -> LIVE_STRIP
    }

    fun noteOn(note: Int, w: Wave = wave, gain: Float = 1f, owner: Int = -1) {
        if (voices.any { it.note == note && it.owner == owner && !it.releasing }) return
        voices.add(Voice(note, 440.0 * 2.0.pow((note - 69) / 12.0), w, gain, owner, stripOf(owner)))
    }

    fun noteOff(note: Int, owner: Int = -1) {
        voices.filter { it.note == note && it.owner == owner && !it.releasing }
            .forEach { it.releasing = true }
    }

    /** Bunyikan satu nada singkat; t = nomor track (untuk memakai instrumennya). */
    fun previewNote(n: Int, t: Int = -1) {
        if (gateNote >= 0) noteOff(gateNote, gateOwner)
        noteOn(n, if (t >= 0) trackWave[t] else wave)
        gateNote = n
        gateOwner = -1
        offAt = pos + sampleRate / 2
    }

    /** Coba suara satu channel. */
    fun preview(ch: Int) {
        if (ch == SYNTH_CH) previewNote(seqNote) else hits.add(Hit(ch))
    }

    /**
     * Render aransemen ke audio stereo tanpa lewat speaker. Panggil setelah stop().
     * loops = berapa kali pola 16 step diulang; ditambah ekor suara 2 detik.
     * Hasil: sampel selang-seling kiri, kanan.
     */
    fun renderOffline(loops: Int): ShortArray {
        val savedMetro = metronomeOn
        exporting = true
        try {
            voices.clear()
            hits.clear()
            for (k in 0 until TRACKS * ROLL_ROWS) rollLeft[k] = 0
            rollActive = false
            gateNote = -1
            clickPos = Int.MAX_VALUE
            revL.clear()
            revR.clear()
            delayBufL.fill(0.0)
            delayBufR.fill(0.0)
            metronomeOn = false
            val perBeat = (sampleRate * 60.0 / bpm).toLong().coerceAtLeast(4)
            val stepLen = perBeat / 4
            val musicFrames = (stepLen * STEPS * loops).toInt()
            val clipFrames = (vocal?.size ?: 0) + maxOf(0, vocalShiftMs * sampleRate / 1000)
            val total = maxOf(musicFrames, clipFrames) + sampleRate * 2
            val out = ShortArray(total * 2)
            val buf = ShortArray(256 * 2)
            resetReq = true
            clipResetReq = true
            vocalActive = vocal != null
            playing = true
            var done = 0
            while (done < total) {
                if (done >= musicFrames) playing = false
                val n = minOf(256, total - done)
                renderFrames(buf, n)
                System.arraycopy(buf, 0, out, done * 2, n * 2)
                done += n
            }
            playing = false
            vocalActive = false
            voices.clear()
            hits.clear()
            return out
        } finally {
            metronomeOn = savedMetro
            exporting = false
        }
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

    /** Hitung penguatan kiri-kanan tiap jalur dari volume, pan, mute, dan solo. */
    private fun updateGains() {
        var anySolo = false
        for (i in 0 until MIX_CH) if (mixSolo[i]) anySolo = true
        for (i in 0 until MIX_CH) {
            val muted = when {
                i < CHANNEL_NAMES.size -> chMute[i]
                i < CHANNEL_NAMES.size + TRACKS -> trackMute[i - CHANNEL_NAMES.size]
                i == LIVE_STRIP -> liveMute
                else -> vocalMute
            }
            val on = !muted && (!anySolo || mixSolo[i])
            val g = if (on) mixVol[i] * 1.25 else 0.0
            val a = (mixPan[i].toDouble().coerceIn(-1.0, 1.0) + 1.0) * PI / 4.0
            gainL[i] = g * cos(a) * 1.4142
            gainR[i] = g * sin(a) * 1.4142
        }
    }

    private fun renderLoop(t: AudioTrack, frames: Int) {
        val buf = ShortArray(frames * 2)
        while (running) {
            renderFrames(buf, frames)
            t.write(buf, 0, frames * 2)
        }
    }

    private fun renderFrames(buf: ShortArray, frames: Int) {
        val vol = volume
        val perBeat = (sampleRate * 60.0 / bpm).toLong().coerceAtLeast(4)
        val stepLen = perBeat / 4
        val clickLen = (0.03 * sampleRate).toInt()
        val revAmt = reverbMix.toDouble()
        val dlyAmt = delayMix.toDouble()
        val delayLen = (perBeat / 2).toInt().coerceIn(1, delayBufL.size - 1)
        updateGains()
        for (i in 0 until frames) {
            if (resetReq) { resetReq = false; seqCounter = 0; seqStep = 0 }
            if (clipResetReq) {
                clipResetReq = false
                clipPos = -(vocalShiftMs.toLong() * sampleRate / 1000L)
            }
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
                    currentStep = seqStep
                    for (ch in CHANNEL_NAMES.indices) {
                        if (steps[ch * STEPS + seqStep] && !chMute[ch]) {
                            if (ch == SYNTH_CH) {
                                if (gateNote >= 0) noteOff(gateNote, gateOwner)
                                noteOn(seqNote, wave, 1f, SYNTH_CH)
                                gateNote = seqNote
                                gateOwner = SYNTH_CH
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
                            val l = rollLen[tr * ROLL_SIZE + r * STEPS + seqStep]
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
                if (seqCounter >= stepLen) {
                    seqCounter = 0
                    seqStep = (seqStep + 1) % STEPS
                    if (seqStep == 0 && vocalLoop && vocalActive) clipResetReq = true
                }
            }
            if (gateNote >= 0 && pos >= offAt) { noteOff(gateNote, gateOwner); gateNote = -1 }

            var l = 0.0
            var r = 0.0
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
                val x = raw * v.level * dec * 0.25 * v.gain
                val si = v.strip
                l += x * gainL[si]
                r += x * gainR[si]
                val ax = abs(x) * maxOf(gainL[si], gainR[si])
                if (ax > blockPeak[si]) blockPeak[si] = ax

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
                val x = d * 0.9
                val si = h.type
                l += x * gainL[si]
                r += x * gainR[si]
                val ax = abs(x) * maxOf(gainL[si], gainR[si])
                if (ax > blockPeak[si]) blockPeak[si] = ax
                h.t++
                if (h.t > sampleRate * 0.7) hits.remove(h)
            }

            val vc = vocal
            if (vocalActive && vc != null && !vocalSuppress) {
                if (clipPos >= 0 && clipPos < vc.size) {
                    val x = vc[clipPos.toInt()] / 32768.0
                    l += x * gainL[VOCAL_STRIP]
                    r += x * gainR[VOCAL_STRIP]
                    val ax = abs(x) * maxOf(gainL[VOCAL_STRIP], gainR[VOCAL_STRIP])
                    if (ax > blockPeak[VOCAL_STRIP]) blockPeak[VOCAL_STRIP] = ax
                }
                clipPos++
                if (clipPos >= vc.size && !(vocalLoop && playing)) vocalActive = false
            }

            if (revAmt > 0.001) {
                val inp = (l + r) * 0.5
                l += revL.process(inp) * revAmt * 6.0
                r += revR.process(inp) * revAmt * 6.0
            }
            if (dlyAmt > 0.001) {
                val rp = (delayW - delayLen + delayBufL.size) % delayBufL.size
                val dl = delayBufL[rp]
                val dr = delayBufR[rp]
                delayBufL[delayW] = l + dr * 0.4
                delayBufR[delayW] = r + dl * 0.4
                l += dl * dlyAmt
                r += dr * dlyAmt
                delayW = (delayW + 1) % delayBufL.size
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
                    val c = sin(2 * PI * clickFreq * clickPos / sampleRate) * env * 0.4
                    l += c
                    r += c
                    clickPos++
                }
            }
            pos++
            buf[i * 2] = (tanh(l * vol) * 32767 * 0.9).toInt().toShort()
            buf[i * 2 + 1] = (tanh(r * vol) * 32767 * 0.9).toInt().toShort()
        }
        for (k in 0 until MIX_CH) {
            peak[k] = maxOf(blockPeak[k].toFloat(), peak[k] * 0.85f)
            blockPeak[k] = 0.0
        }
    }
}
