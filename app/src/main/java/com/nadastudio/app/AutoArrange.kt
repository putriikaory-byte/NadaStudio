package com.nadastudio.app

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random

/** Satu bar = 4 ketuk. */
fun barSeconds(bpm: Int): Double = 240.0 / bpm.coerceIn(40, 240)

private fun stepsOf(type: ScaleType): List<Int> =
    if (type == ScaleType.CHROMATIC) ScaleType.MAJOR.steps else type.steps

/** Nada dasar akor (pitch class) untuk [degree] dalam tangga nada [scale] dari [root]. */
fun chordRootPcFor(root: Int, degree: Int, scale: ScaleType): Int {
    val s = stepsOf(scale)
    return (root + s[degree % s.size]) % 12
}

/** Nama akor, mis. "C", "Am", "G". Ditentukan dari interval terts dan kwint triad. */
fun chordNameFor(root: Int, degree: Int, scale: ScaleType): String {
    val s = stepsOf(scale)
    val n = s.size
    val pc = chordRootPcFor(root, degree, scale)
    val third = (s[(degree + 2) % n] - s[degree % n] + 12) % 12
    val fifth = (s[(degree + 4) % n] - s[degree % n] + 12) % 12
    return NOTE_NAMES[pc] + when {
        fifth == 6 -> "dim"
        third == 3 -> "m"
        else -> ""
    }
}

enum class ArrSection(val label: String) {
    INTRO("Intro"),
    VERSE("Verse"),
    CHORUS("Chorus")
}

class BarInfo(
    val index: Int,
    val section: ArrSection,
    val degree: Int,
    val chordRootPc: Int,
    val chordName: String,
    val vocalDensity: Double,
    val hasVocal: Boolean
)

/**
 * Aransemen multi-bar hasil "Aransemen dari Vokal".
 * drums[b] = pola Channel Rack 1 bar (6 channel x 16 step).
 * rolls[b] = isi Piano Roll 1 bar (3 track x 60 baris x 16 step, format sama seperti SynthEngine.rollLen).
 * Track 0 (Melodi) berisi nada vokal yang terdeteksi; hanya di bar tanpa vokal diisi
 * counter-melody dari generator genre.
 */
class VocalArrangement(
    val bars: Int,
    val bpm: Int,
    val root: Int,
    val scale: ScaleType,
    val genreName: String,
    val barInfos: List<BarInfo>,
    val drums: List<BooleanArray>,
    val rolls: List<IntArray>
) {
    fun drumOn(bar: Int, ch: Int, step: Int): Boolean =
        drums.getOrNull(bar)?.getOrNull(ch * STEPS + step) ?: false

    fun rollLenAt(bar: Int, track: Int, row: Int, step: Int): Int =
        rolls.getOrNull(bar)?.getOrNull(track * ROLL_SIZE + row * STEPS + step) ?: 0

    fun chordProgressionText(): String =
        barInfos.joinToString(" – ") { it.chordName }

    fun summaryText(): String {
        val sb = StringBuilder()
        sb.append("$genreName · ${NOTE_NAMES[root]} ${scale.label} · $bpm BPM · $bars bar\n")
        sb.append("Progresi: ").append(chordProgressionText()).append("\n")
        val parts = ArrayList<String>()
        var i = 0
        while (i < barInfos.size) {
            var j = i
            while (j + 1 < barInfos.size && barInfos[j + 1].section == barInfos[i].section) j++
            val label = barInfos[i].section.label
            parts.add(if (i == j) "$label bar ${i + 1}" else "$label bar ${i + 1}–${j + 1}")
            i = j + 1
        }
        sb.append("Bagian: ").append(parts.joinToString(" · "))
        return sb.toString()
    }
}

/**
 * Perkiraan tempo dari onset nada vokal.
 * Interval antar onset (median) biasanya ~1 ketuk, kadang ~1/2 atau ~2 ketuk,
 * jadi diuji tiga kandidat dan dipilih yang paling dekat dengan BPM genre.
 * Kembali ke [fallbackBpm] bila nada terlalu sedikit atau intervalnya tidak beraturan.
 */
fun estimateTempo(notes: List<VocalNote>, fallbackBpm: Int): Int {
    val fb = fallbackBpm.coerceIn(60, 160)
    if (notes.size < 6) return fb
    val intervals = notes.zipWithNext { a, b -> b.startSec - a.startSec }
        .filter { it in 0.15..1.2 }
        // Bulatkan ke 10 ms supaya noise floating-point tidak menggagalkan uji sebaran.
        .map { round(it * 100.0) / 100.0 }
        .sorted()
    if (intervals.size < 5) return fb
    val q1 = intervals[intervals.size / 4]
    val q3 = intervals[(intervals.size * 3) / 4]
    if (q3 - q1 > 0.6) return fb
    val median = intervals[intervals.size / 2]
    if (median <= 0.0) return fb
    val candidates = listOf(60.0 / median, 120.0 / median, 30.0 / median)
        .filter { it in 60.0..160.0 }
    if (candidates.isEmpty()) return fb
    return candidates.minByOrNull { abs(it - fb) }!!.roundToInt().coerceIn(60, 160)
}

/**
 * Pilih derajat akor terbaik untuk satu bar: skor tiap kandidat dari genre.prog
 * berdasarkan pitch class vokal di bar itu, dibobot durasi. Nada akor +2,
 * nada tangga nada lain +0,5, nada di luar tangga nada -1 per detik.
 * Bar tanpa vokal memakai urutan progresi genre.
 */
fun chordPerBar(
    notes: List<VocalNote>,
    barIndex: Int,
    root: Int,
    scale: ScaleType,
    genre: Genre
): Int {
    val fallback = genre.prog[barIndex % genre.prog.size]
    if (notes.isEmpty()) return fallback
    val steps = stepsOf(scale)
    val set = scaleSet(root, scale)
    var best = fallback
    var bestScore = Double.NEGATIVE_INFINITY
    for (d in genre.prog.distinct()) {
        val tones = setOf(0, 2, 4).map { (root + steps[(d + it) % steps.size]) % 12 }.toSet()
        var score = 0.0
        for (n in notes) {
            val pc = n.midi % 12
            score += n.durSec * when {
                pc in tones -> 2.0
                pc in set -> 0.5
                else -> -1.0
            }
        }
        if (d == fallback) score += 0.05
        if (score > bestScore) {
            bestScore = score
            best = d
        }
    }
    return best
}

private fun rowOf(pc: Int, octIndex: Int): Int {
    val lo = octIndex * 12
    for (r in lo until minOf(lo + 12, ROLL_ROWS)) {
        if ((ROLL_LOW + r) % 12 == pc) return r
    }
    return lo
}

/** Pola drum genre dengan variasi deterministik per bar/section (seed tetap, hasil bisa diulang). */
fun drumsForBar(genre: Genre, section: ArrSection, bar: Int): BooleanArray {
    val rnd = Random(1234567L + genre.name.hashCode().toLong() * 31L + bar * 7919L + section.ordinal * 131L)
    val out = BooleanArray(CHANNEL_NAMES.size * STEPS)
    val pats = listOf(genre.kick, genre.snare, genre.clap, genre.hat, genre.open, genre.synth)
    for (ch in pats.indices) {
        for (s in 0 until STEPS) {
            val c = pats[ch].getOrNull(s) ?: '.'
            if (c == 'x' || (c == 'o' && rnd.nextInt(100) < 60)) out[ch * STEPS + s] = true
        }
    }
    when (section) {
        ArrSection.INTRO -> {
            // Sepi: kick hanya ketuk 1 dan 3, tanpa snare/clap/synth, hat hanya tiap ketuk.
            for (s in 0 until STEPS) {
                if (s != 0 && s != 8) out[0 * STEPS + s] = false
                if (s % 4 != 0) out[3 * STEPS + s] = false
                out[1 * STEPS + s] = false
                out[2 * STEPS + s] = false
                out[4 * STEPS + s] = false
                out[5 * STEPS + s] = false
            }
        }
        ArrSection.CHORUS -> {
            // Lebih padat: hat tiap 1/8, open hat di akhir bar, kick tambahan.
            for (s in 0 until STEPS step 2) out[3 * STEPS + s] = true
            out[4 * STEPS + 14] = true
            out[0 * STEPS + 10] = true
        }
        ArrSection.VERSE -> Unit
    }
    return out
}

/** Akor satu bar (satu akor penuh) mengikuti gaya akor genre. Hasil: (baris, step, panjang). */
fun chordsForBar(
    root: Int,
    degree: Int,
    scale: ScaleType,
    octIndex: Int,
    genre: Genre
): List<Triple<Int, Int, Int>> {
    val steps = stepsOf(scale)
    val rows = intArrayOf(0, 2, 4).map { k ->
        rowOf((root + steps[(degree + k) % steps.size]) % 12, octIndex)
    }
    val out = ArrayList<Triple<Int, Int, Int>>()
    when (genre.chordStyle) {
        0 -> rows.forEach { out.add(Triple(it, 0, STEPS)) }
        1 -> for (s in listOf(2, 6, 10, 14)) rows.forEach { out.add(Triple(it, s, 1)) }
        2 -> for (s in listOf(0, 3, 4, 7, 8, 11, 12, 15)) rows.forEach { out.add(Triple(it, s, 1)) }
        else -> {
            val order = intArrayOf(0, 1, 2, 1)
            for (i in 0 until STEPS) out.add(Triple(rows[order[i % 4]], i, 1))
        }
    }
    return out
}

/** Bass satu bar mengikuti akar akor bar itu, mengikuti gaya bass genre. */
fun bassForBar(
    root: Int,
    degree: Int,
    scale: ScaleType,
    octIndex: Int,
    genre: Genre
): List<Triple<Int, Int, Int>> {
    val steps = stepsOf(scale)
    val chordRoot = (root + steps[degree % steps.size]) % 12
    val r0 = rowOf(chordRoot, octIndex)
    val r5 = rowOf((chordRoot + 7) % 12, octIndex)
    val out = ArrayList<Triple<Int, Int, Int>>()
    when (genre.bassStyle) {
        0 -> {
            out.add(Triple(r0, 0, 8))
            out.add(Triple(r0, 8, 8))
        }
        1 -> {
            out.add(Triple(r0, 0, 2))
            out.add(Triple(r5, 4, 2))
            out.add(Triple(r0, 8, 2))
            out.add(Triple(r5, 12, 2))
        }
        2 -> {
            out.add(Triple(r0, 0, 1))
            out.add(Triple(r0, 2, 1))
            out.add(Triple(r5, 3, 1))
            out.add(Triple(r0, 7, 1))
            out.add(Triple(r0, 10, 1))
            out.add(Triple(r5, 14, 1))
        }
        else -> for (s in listOf(0, 2, 4, 6, 8, 10, 12, 14)) out.add(Triple(r0, s, 1))
    }
    return out
}

/**
 * Bangun aransemen multi-bar dari hasil analisis vokal. Lokal dan offline.
 * bars = ceil(durasi vokal / durasi 1 bar), dibatasi 32 bar.
 * Vokal tetap melodi utama; counter-melody hanya di bar tanpa vokal.
 */
fun buildArrangement(analysis: VocalAnalysis, genre: Genre): VocalArrangement {
    val bpm = estimateTempo(analysis.notes, genre.bpm)
    val secPerBar = barSeconds(bpm)
    val lastEnd = analysis.notes.maxOfOrNull { it.startSec + it.durSec } ?: 0.0
    val totalSec = max(analysis.seconds, lastEnd)
    val bars = ceil(totalSec / secPerBar).toInt().coerceIn(1, 32)
    val root = analysis.keyRoot
    val scale = analysis.keyScale
    val scaleSetLocal = scaleSet(root, scale)
    val vocalByBar = PitchDetect.toRollMulti(analysis, bpm, scaleSetLocal)

    val densities = DoubleArray(bars) { b ->
        val t0 = b * secPerBar
        val t1 = t0 + secPerBar
        var d = 0.0
        for (n in analysis.notes) {
            val s = max(n.startSec, t0)
            val e = min(n.startSec + n.durSec, t1)
            if (e > s) d += e - s
        }
        // Bulatkan ke 3 desimal supaya noise floating-point (1 ULP) tidak
        // membuat bar yang kepadatannya sama tiba-tiba dianggap Chorus.
        (round(d / secPerBar * 1000.0) / 1000.0).coerceIn(0.0, 1.0)
    }
    val maxDensity = densities.maxOrNull() ?: 0.0
    val medianDensity = densities.sorted().getOrNull(densities.size / 2) ?: 0.0
    val firstVocalBar = densities.indexOfFirst { it > 0.0 }.takeIf { it >= 0 } ?: bars

    val barInfos = ArrayList<BarInfo>(bars)
    val drums = ArrayList<BooleanArray>(bars)
    val rolls = ArrayList<IntArray>(bars)
    for (b in 0 until bars) {
        val t0 = b * secPerBar
        val t1 = t0 + secPerBar
        val notesInBar = analysis.notes.filter { it.startSec >= t0 && it.startSec < t1 }
        val degree = chordPerBar(notesInBar, b, root, scale, genre)
        val chordPc = chordRootPcFor(root, degree, scale)
        val hasVocal = vocalByBar.getOrNull(b)?.isNotEmpty() == true
        val section = when {
            b < firstVocalBar -> ArrSection.INTRO
            // Chorus = bar terpadat; harus di atas median supaya lagu yang
            // kepadatannya merata tidak seluruhnya jadi Chorus.
            maxDensity > 0.15 && densities[b] >= 0.8 * maxDensity && densities[b] > medianDensity -> ArrSection.CHORUS
            else -> ArrSection.VERSE
        }
        barInfos.add(
            BarInfo(
                index = b,
                section = section,
                degree = degree,
                chordRootPc = chordPc,
                chordName = chordNameFor(root, degree, scale),
                vocalDensity = densities[b],
                hasVocal = hasVocal
            )
        )
        drums.add(drumsForBar(genre, section, b))
        val roll = IntArray(TRACKS * ROLL_SIZE)
        val melodyNotes = if (hasVocal) {
            vocalByBar[b]
        } else {
            // Bar tanpa vokal saja yang diisi counter-melody dari generator genre.
            generateMelodyFor(chordPc, scale, 2, genre)
        }
        for ((row, step, len) in melodyNotes) {
            roll[0 * ROLL_SIZE + row * STEPS + step] = len.coerceIn(1, STEPS - step)
        }
        for ((row, step, len) in bassForBar(root, degree, scale, 0, genre)) {
            roll[1 * ROLL_SIZE + row * STEPS + step] = len.coerceIn(1, STEPS - step)
        }
        for ((row, step, len) in chordsForBar(root, degree, scale, 1, genre)) {
            roll[2 * ROLL_SIZE + row * STEPS + step] = len.coerceIn(1, STEPS - step)
        }
        rolls.add(roll)
    }
    return VocalArrangement(
        bars = bars,
        bpm = bpm,
        root = root,
        scale = scale,
        genreName = genre.name,
        barInfos = barInfos,
        drums = drums,
        rolls = rolls
    )
}
