package com.nadastudio.app

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Matematika murni untuk Playlist/timeline ala FL Studio dan count-in.
 * Tanpa Android/UI supaya bisa diuji di luar HP.
 */

/** Snap posisi klip (dalam step, boleh pecahan dari hasil geser) ke step terdekat. */
fun snapStep(rawSteps: Float): Int = rawSteps.roundToInt().coerceAtLeast(0)

/** Berapa step (1 step = 1/16 nada = 1/4 ketuk) panjang sebuah klip pada [bpm]. */
fun clipSteps(clipSamples: Int, bpm: Int, sampleRate: Int = CLIP_RATE): Int {
    if (clipSamples <= 0) return 1
    val stepSec = 60.0 / bpm.coerceIn(40, 240) / 4.0
    return ceil(clipSamples / sampleRate.toDouble() / stepSec).toInt().coerceAtLeast(1)
}

/** Posisi awal klip dalam detik, dari posisi step-nya pada [bpm]. */
fun clipStartSec(startStep: Int, bpm: Int): Double =
    startStep.coerceAtLeast(0) * 60.0 / bpm.coerceIn(40, 240) / 4.0

/** Total durasi count-in 1 bar (4 ketuk) dalam milidetik. */
fun countInTotalMs(bpm: Int): Long = (4 * 60000.0 / bpm.coerceIn(40, 240)).toLong()

/**
 * Angka hitungan yang sedang tampil selama count-in: 4, 3, 2, 1, lalu 0
 * (0 = count-in selesai, rekaman boleh mulai). [elapsedMs] = waktu sejak
 * count-in dimulai.
 */
fun countInBeatAt(elapsedMs: Long, bpm: Int): Int {
    if (elapsedMs < 0) return 4
    val beatMs = 60000.0 / bpm.coerceIn(40, 240)
    val beatIndex = (elapsedMs / beatMs).toInt()
    if (beatIndex >= 4) return 0
    return 4 - beatIndex
}
