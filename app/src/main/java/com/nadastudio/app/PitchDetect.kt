package com.nadastudio.app

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

class VocalNote(val startSec: Double, val durSec: Double, val midi: Int)

class VocalAnalysis(
    val notes: List<VocalNote>,
    val keyRoot: Int,
    val keyScale: ScaleType,
    val fit: Double,
    val seconds: Double
)

/** Deteksi nada (metode YIN sederhana), pemotongan menjadi nada, dan deteksi kunci. */
object PitchDetect {
    private const val DEC = 4
    private const val SRD = CLIP_RATE / DEC
    private const val HOP = 256
    private const val W = 512
    private const val TAU_MIN = 14
    private const val TAU_MAX = 157

    fun analyze(clip: ShortArray): VocalAnalysis? {
        val secs = clip.size / CLIP_RATE.toDouble()
        val n = clip.size / DEC
        if (n < W + TAU_MAX + HOP) return null
        val x = FloatArray(n)
        for (i in 0 until n) {
            var s = 0
            for (k in 0 until DEC) s += clip[i * DEC + k]
            x[i] = s / (DEC * 32768f)
        }
        val frames = (n - W - TAU_MAX) / HOP
        if (frames <= 0) return null

        val rms = FloatArray(frames)
        var maxRms = 0f
        for (f in 0 until frames) {
            val st = f * HOP
            var e = 0f
            for (j in 0 until W) {
                val v = x[st + j]
                e += v * v
            }
            rms[f] = sqrt(e / W)
            if (rms[f] > maxRms) maxRms = rms[f]
        }
        val gate = maxOf(0.01f, maxRms * 0.08f)

        val midi = DoubleArray(frames) { Double.NaN }
        for (f in 0 until frames) {
            if (rms[f] < gate) continue
            val hz = yin(x, f * HOP)
            if (hz > 0) {
                val m = 69.0 + 12.0 * ln(hz / 440.0) / ln(2.0)
                if (m in 40.0..90.0) midi[f] = m
            }
        }

        val rounded = IntArray(frames) { -1 }
        for (f in 0 until frames) {
            if (midi[f].isNaN()) continue
            val vals = ArrayList<Double>()
            for (k in -2..2) {
                val g = f + k
                if (g in 0 until frames && !midi[g].isNaN()) vals.add(midi[g])
            }
            vals.sort()
            rounded[f] = vals[vals.size / 2].roundToInt()
        }

        val notes = ArrayList<VocalNote>()
        var curPitch = -1
        var curStart = 0
        var lastValid = 0
        fun close() {
            val startSec = curStart * HOP / SRD.toDouble()
            val dur = (lastValid - curStart + 1) * HOP / SRD.toDouble()
            if (dur >= 0.08) notes.add(VocalNote(startSec, dur, curPitch))
        }
        for (i in 0 until frames) {
            val p = rounded[i]
            if (p < 0) {
                if (curPitch >= 0 && i - lastValid > 3) {
                    close()
                    curPitch = -1
                }
                continue
            }
            if (curPitch == p) {
                lastValid = i
            } else {
                if (curPitch >= 0) close()
                curPitch = p
                curStart = i
                lastValid = i
            }
        }
        if (curPitch >= 0) close()

        if (notes.isEmpty()) return VocalAnalysis(emptyList(), 0, ScaleType.MAJOR, 0.0, secs)
        val (root, type, fit) = detectKey(notes)
        return VocalAnalysis(notes, root, type, fit, secs)
    }

    private fun yin(x: FloatArray, st: Int): Double {
        val d = FloatArray(TAU_MAX + 1)
        for (tau in 1..TAU_MAX) {
            var sum = 0f
            for (j in 0 until W) {
                val df = x[st + j] - x[st + j + tau]
                sum += df * df
            }
            d[tau] = sum
        }
        val dn = FloatArray(TAU_MAX + 1)
        dn[0] = 1f
        var running = 0f
        for (tau in 1..TAU_MAX) {
            running += d[tau]
            dn[tau] = if (running > 0f) d[tau] * tau / running else 1f
        }
        var t = TAU_MIN
        while (t < TAU_MAX) {
            if (dn[t] < 0.15f) {
                while (t + 1 < TAU_MAX && dn[t + 1] < dn[t]) t++
                val a = dn[t - 1]
                val b = dn[t]
                val c = dn[t + 1]
                val den = a + c - 2f * b
                val shift = if (abs(den) > 1e-6f) 0.5f * (a - c) / den else 0f
                return SRD / (t + shift).toDouble()
            }
            t++
        }
        return -1.0
    }

    private fun detectKey(notes: List<VocalNote>): Triple<Int, ScaleType, Double> {
        val w = DoubleArray(12)
        var total = 0.0
        for (n in notes) {
            w[n.midi % 12] += n.durSec
            total += n.durSec
        }
        if (total <= 0.0) return Triple(0, ScaleType.MAJOR, 0.0)
        val last = notes.last().midi % 12
        var bestRoot = 0
        var bestType = ScaleType.MAJOR
        var bestScore = -1.0
        var bestFit = 0.0
        for (root in 0 until 12) {
            for (type in listOf(ScaleType.MAJOR, ScaleType.MINOR)) {
                val set = scaleSet(root, type)
                var inW = 0.0
                for (pc in 0 until 12) if (pc in set) inW += w[pc]
                val fit = inW / total
                val score = fit + 0.25 * w[root] / total + 0.1 * w[(root + 7) % 12] / total +
                    (if (last == root) 0.15 else 0.0)
                if (score > bestScore) {
                    bestScore = score
                    bestRoot = root
                    bestType = type
                    bestFit = fit
                }
            }
        }
        return Triple(bestRoot, bestType, bestFit)
    }

    /** Ubah nada hasil deteksi menjadi (baris, step, panjang) untuk SEMUA bar lagu. */
    fun toRollMulti(a: VocalAnalysis, bpm: Int, scale: Set<Int>?): List<List<Triple<Int, Int, Int>>> {
        val bars = ceil(a.seconds / barSeconds(bpm)).toInt().coerceIn(1, 32)
        return List(bars) { toRoll(a, bpm, it, scale) }
    }

    /** Ubah nada hasil deteksi menjadi (baris, step, panjang) untuk satu bar Piano Roll. */
    fun toRoll(a: VocalAnalysis, bpm: Int, bar: Int, scale: Set<Int>?): List<Triple<Int, Int, Int>> {
        val stepSec = 60.0 / bpm / 4.0
        val barSec = stepSec * STEPS
        val t0 = bar * barSec
        val out = ArrayList<Triple<Int, Int, Int>>()
        for (n in a.notes) {
            if (n.startSec < t0 - stepSec / 2 || n.startSec >= t0 + barSec - stepSec / 2) continue
            var m = n.midi
            if (scale != null) m = snapToScale(m, scale)
            while (m < ROLL_LOW) m += 12
            while (m >= ROLL_LOW + ROLL_ROWS) m -= 12
            val step = ((n.startSec - t0) / stepSec).roundToInt().coerceIn(0, STEPS - 1)
            var len = (n.durSec / stepSec).roundToInt().coerceAtLeast(1)
            if (step + len > STEPS) len = STEPS - step
            out.add(Triple(m - ROLL_LOW, step, len))
        }
        out.sortBy { it.second }
        for (i in out.indices) {
            val a1 = out[i]
            for (j in i + 1 until out.size) {
                val b = out[j]
                if (b.first == a1.first && b.second < a1.second + a1.third) {
                    out[i] = Triple(a1.first, a1.second, maxOf(1, b.second - a1.second))
                    break
                }
            }
        }
        return out
    }
}
