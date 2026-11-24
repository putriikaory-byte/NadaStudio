package com.nadastudio.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Penyelarasan vokal ke grid ketukan + time-stretch tanpa mengubah pitch.
 * Logika murni (tanpa Android/UI) supaya bisa diuji di luar HP.
 *
 * Masalah yang dibetulkan: vokal yang direkam bebas tidak pernah mulai tepat
 * di ketukan 1 dan tidak mengikuti tempo proyek, jadi dari bar pertama saja
 * sudah tidak nyambung dengan musik. Alurnya:
 *   1. [alignVocalToBeat1] — potong leading silence supaya onset nada pertama
 *      jatuh tepat di beat 1 bar 1.
 *   2. [processVocalForArrangement] — perkirakan tempo vokal, lalu stretch
 *      vokal ke tempo proyek (WSOLA, pitch tetap). Kalau rasionya ekstrem,
 *      tempo proyek justru mengikuti tempo vokal.
 */

private const val TS_WINDOW = 1024
private const val TS_HOP = 256
private const val TS_CORR_LEN = 256
private const val TS_TOL = 128

/** Batas rasio stretch yang masih wajar; di luar itu lebih baik ikut tempo vokal. */
const val STRETCH_MIN_RATE = 0.7
const val STRETCH_MAX_RATE = 1.4

/**
 * Time-stretch WSOLA (Waveform Similarity Overlap-Add): mengubah durasi tanpa
 * mengubah pitch. [rate] = panjang hasil / panjang asal (>1 = lebih lambat
 * dan panjang, <1 = lebih cepat dan pendek). Di luar [0.5, 2.0] dijepit.
 * Masukan yang terlalu pendek atau rate ~1 dikembalikan sebagai salinan.
 */
fun timeStretch(input: ShortArray, rate: Double, sampleRate: Int = CLIP_RATE): ShortArray {
    val r = rate.coerceIn(0.5, 2.0)
    if (input.size < TS_WINDOW * 2 || abs(r - 1.0) < 0.02) return input.copyOf()
    val outLen = (input.size * r).toInt().coerceAtLeast(TS_WINDOW)
    val out = FloatArray(outLen + TS_WINDOW)
    val winSum = FloatArray(outLen + TS_WINDOW)
    val win = FloatArray(TS_WINDOW) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (TS_WINDOW - 1))).toFloat()
    }
    val inF = FloatArray(input.size) { input[it] / 32768f }

    fun grainAt(pos: Int): Float =
        if (pos in 0 until inF.size) inF[pos] else 0f

    /**
     * Cari geseran terbaik (±[TS_TOL]) supaya grain baru nyambung dengan ekor hasil.
     * Dua pengaman supaya pemetaan waktu tidak meleset:
     * 1. Kandidat diurutkan dari |delta| terkecil dan hanya skor yang benar-benar
     *    lebih besar yang menang — puncak korelasi yang hampir sama kuat tidak
     *    boleh memilih geseran jauh (nada periodik punya banyak puncak sama kuat).
     * 2. Skor diberi penalti sebanding |delta|, dan bila skor terbaik terlalu
     *    rendah (hening / tidak periodik) kembalikan 0 alias tanpa geser.
     */
    fun bestOffset(nominal: Int, synPos: Int): Int {
        if (synPos <= 0) return 0
        val candidates = ArrayList<Int>()
        candidates.add(0)
        var d = 4
        while (d <= TS_TOL) {
            candidates.add(-d)
            candidates.add(d)
            d += 4
        }
        fun scoreAt(delta: Int, step: Int = 4): Double {
            val s = nominal + delta
            var xy = 0.0
            var xx = 0.0
            var yy = 0.0
            var k = 0
            while (k < TS_CORR_LEN) {
                val x = grainAt(s + k).toDouble()
                val y = if (synPos + k < out.size) out[synPos + k].toDouble() else 0.0
                xy += x * y
                xx += x * x
                yy += y * y
                k += step
            }
            val raw = if (xx > 1e-9 && yy > 1e-9) xy / sqrt(xx * yy) else 0.0
            return raw * (1.0 - 0.3 * abs(delta).toDouble() / TS_TOL)
        }
        var best = 0
        var bestScore = -1.0
        for (delta in candidates) {
            val score = scoreAt(delta)
            if (score > bestScore) {
                bestScore = score
                best = delta
            }
        }
        if (bestScore < 0.25) return 0
        // Perhalus ±3 sampel di sekitar best (urut |off| menaik, resolusi penuh).
        var fine = best
        var fineScore = scoreAt(best, 1)
        for (off in listOf(-1, 1, -2, 2, -3, 3)) {
            val dd = best + off
            if (dd < -TS_TOL || dd > TS_TOL) continue
            val score = scoreAt(dd, 1)
            if (score > fineScore) {
                fineScore = score
                fine = dd
            }
        }
        return fine
    }

    var synPos = 0
    var g = 0
    while (synPos < outLen) {
        // Jangkar posisi analisis ke lintasan ideal (g * Hs / rate), BUKAN
        // dirangkai dari grain sebelumnya: koreksi fase per grain (±128 sampel)
        // jadi tidak menumpuk menjadi drift waktu di sepanjang lagu.
        val nominal = (g * TS_HOP / r).roundToInt()
        if (nominal >= inF.size && g > 0) break
        val delta = if (g == 0) 0 else bestOffset(nominal, synPos)
        val start = (nominal + delta).coerceIn(0, max(0, inF.size - 1))
        for (i in 0 until TS_WINDOW) {
            val o = synPos + i
            if (o >= out.size) break
            val w = win[i]
            out[o] += grainAt(start + i) * w
            winSum[o] += w
        }
        synPos += TS_HOP
        g++
    }
    val result = ShortArray(outLen)
    for (i in 0 until outLen) {
        val w = winSum[i]
        val v = if (w > 0.05f) out[i] / w else 0f
        result[i] = (v * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }
    return result
}

/**
 * Deteksi awal bunyi pertama (onset energi) dalam detik: jendela RMS 10 ms,
 * gate 8% dari RMS maksimum (mirip gate di PitchDetect). Butuh 2 jendela
 * berurutan di atas gate supaya satu klik kecil tidak dianggap onset.
 * Mengembalikan 0.0 bila tidak ada bunyi yang jelas.
 */
fun firstSoundOnsetSec(clip: ShortArray, sampleRate: Int = CLIP_RATE): Double {
    if (clip.size < sampleRate / 50) return 0.0
    val win = sampleRate / 100 // 10 ms
    val frames = clip.size / win
    if (frames < 3) return 0.0
    val rms = FloatArray(frames)
    var maxRms = 0f
    for (f in 0 until frames) {
        var e = 0.0
        val base = f * win
        for (j in 0 until win) {
            val v = clip[base + j] / 32768.0
            e += v * v
        }
        rms[f] = sqrt(e / win).toFloat()
        if (rms[f] > maxRms) maxRms = rms[f]
    }
    if (maxRms <= 0.001f) return 0.0
    val gate = max(0.01f, maxRms * 0.08f)
    for (f in 0 until frames - 1) {
        if (rms[f] > gate && rms[f + 1] > gate) {
            return f * win / sampleRate.toDouble()
        }
    }
    return 0.0
}

class AlignedVocal(
    val clip: ShortArray,
    /** Berapa detik leading silence yang dipotong (0 bila sudah pas). */
    val onsetSec: Double,
    val notes: List<VocalNote>,
    val seconds: Double
)

/**
 * Sejajarkan vokal ke ketukan 1 bar 1: potong leading silence sampai onset
 * nada pertama. Onset diambil dari nada pertama hasil deteksi pitch (paling
 * musikal); kalau tidak ada nada terdeteksi, pakai onset energi.
 * Nada-nada digeser dengan offset yang sama, jadi analisis tetap cocok
 * dengan klip hasil potongan. Ujung potongan diberi fade-in 5 ms supaya
 * tidak ada klik.
 */
fun alignVocalToBeat1(
    clip: ShortArray,
    analysis: VocalAnalysis?,
    sampleRate: Int = CLIP_RATE
): AlignedVocal {
    val notes = analysis?.notes ?: emptyList()
    val onsetSec = notes.minOfOrNull { it.startSec } ?: firstSoundOnsetSec(clip, sampleRate)
    if (clip.isEmpty() || onsetSec <= 0.01) {
        return AlignedVocal(clip, 0.0, notes, clip.size / sampleRate.toDouble())
    }
    val startSample = (onsetSec * sampleRate).roundToInt().coerceIn(0, clip.size - 1)
    val trimmed = clip.copyOfRange(startSample, clip.size)
    // Fade-in 5 ms di titik potong.
    val fade = (sampleRate * 0.005).toInt().coerceAtMost(trimmed.size)
    for (i in 0 until fade) {
        trimmed[i] = (trimmed[i] * (i + 1) / fade).toShort()
    }
    val shifted = notes.mapNotNull { n ->
        val s = n.startSec - onsetSec
        val e = s + n.durSec
        if (e <= 0.0) null else VocalNote(max(0.0, s), n.durSec - max(0.0, -s), n.midi)
    }
    return AlignedVocal(trimmed, onsetSec, shifted, trimmed.size / sampleRate.toDouble())
}

class ProcessedVocal(
    /** Klip vokal yang sudah disejajarkan ke beat 1 dan di-stretch ke tempo proyek. */
    val clip: ShortArray,
    /** Analisis nada yang waktunya sudah disesuaikan dengan [clip]. */
    val analysis: VocalAnalysis,
    val detectedBpm: Int,
    /** BPM yang akhirnya dipakai proyek (target, atau tempo vokal bila stretch ekstrem). */
    val usedBpm: Int,
    /** Rasio stretch yang dipakai, atau null bila tidak di-stretch. */
    val stretchRate: Double?,
    val stretched: Boolean,
    val onsetTrimSec: Double,
    /** Penjelasan singkat untuk ditampilkan di ringkasan. */
    val note: String
)

/**
 * Alur lengkap sebelum "Aransemen dari Vokal": sejajarkan onset ke beat 1,
 * perkirakan tempo vokal, lalu stretch ke [targetBpm] (tempo proyek saat ini).
 * [targetBpm] = BPM proyek yang dipilih pengguna; [genreBpm] = fallback bila
 * tempo tidak bisa diperkirakan. Rasio stretch = detected / target; di luar
 * [STRETCH_MIN_RATE, STRETCH_MAX_RATE] vokal TIDAK di-stretch — tempo proyek
 * justru mengikuti tempo vokal supaya tidak terdengar rusak.
 */
fun processVocalForArrangement(
    clip: ShortArray,
    analysis: VocalAnalysis?,
    targetBpm: Int,
    genreBpm: Int,
    sampleRate: Int = CLIP_RATE
): ProcessedVocal {
    val aligned = alignVocalToBeat1(clip, analysis, sampleRate)
    val detected = estimateTempo(aligned.notes, genreBpm)
    val target = targetBpm.coerceIn(60, 160)
    val rate = detected.toDouble() / target
    if (rate < STRETCH_MIN_RATE || rate > STRETCH_MAX_RATE) {
        val a = VocalAnalysis(aligned.notes, analysis?.keyRoot ?: 0, analysis?.keyScale ?: ScaleType.MAJOR, analysis?.fit ?: 0.0, aligned.seconds)
        return ProcessedVocal(
            clip = aligned.clip,
            analysis = a,
            detectedBpm = detected,
            usedBpm = detected,
            stretchRate = null,
            stretched = false,
            onsetTrimSec = aligned.onsetSec,
            note = "Tempo vokal $detected BPM dipakai (stretch ${"%.2f".format(rate)}x terlalu ekstrem, jadi BPM proyek mengikuti vokal)."
        )
    }
    if (abs(rate - 1.0) < 0.03) {
        val a = VocalAnalysis(aligned.notes, analysis?.keyRoot ?: 0, analysis?.keyScale ?: ScaleType.MAJOR, analysis?.fit ?: 0.0, aligned.seconds)
        return ProcessedVocal(
            clip = aligned.clip,
            analysis = a,
            detectedBpm = detected,
            usedBpm = target,
            stretchRate = null,
            stretched = false,
            onsetTrimSec = aligned.onsetSec,
            note = "Tempo vokal $detected BPM sudah ≈ $target BPM, vokal tidak perlu di-stretch."
        )
    }
    val stretchedClip = timeStretch(aligned.clip, rate, sampleRate)
    val scaledNotes = aligned.notes.map { n ->
        VocalNote(n.startSec * rate, n.durSec * rate, n.midi)
    }
    val a = VocalAnalysis(
        scaledNotes,
        analysis?.keyRoot ?: 0,
        analysis?.keyScale ?: ScaleType.MAJOR,
        analysis?.fit ?: 0.0,
        stretchedClip.size / sampleRate.toDouble()
    )
    return ProcessedVocal(
        clip = stretchedClip,
        analysis = a,
        detectedBpm = detected,
        usedBpm = target,
        stretchRate = rate,
        stretched = true,
        onsetTrimSec = aligned.onsetSec,
        note = "Vokal di-stretch ${"%.2f".format(rate)}x dari $detected BPM ke $target BPM (pitch tetap), onset pertama dipotong ${"%.2f".format(aligned.onsetSec)} dtk supaya jatuh di ketukan 1."
    )
}
