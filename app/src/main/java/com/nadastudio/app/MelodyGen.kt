package com.nadastudio.app

import kotlin.math.abs
import kotlin.random.Random

/** Nada kuat tangga nada: nada ke-1, ke-3, dan ke-5. */
fun chordTones(root: Int, type: ScaleType): Set<Int> {
    if (type == ScaleType.CHROMATIC) return setOf(root, (root + 4) % 12, (root + 7) % 12)
    val s = type.steps
    return if (s.size >= 5) setOf(s[0], s[2], s[4]).map { (root + it) % 12 }.toSet() else setOf(root)
}

/** Hasil: daftar (baris, step, panjang) untuk oktaf yang sedang tampil. */
fun generateMelody(root: Int, type: ScaleType, octIndex: Int): List<Triple<Int, Int, Int>> {
    val set = scaleSet(root, type)
    val tones = chordTones(root, type)
    val lo = octIndex * 12
    val rows = (lo until minOf(lo + 12, ROLL_ROWS)).filter { (ROLL_LOW + it) % 12 in set }
    if (rows.isEmpty()) return emptyList()

    val rnd = Random(System.nanoTime())
    var idx = rows.indexOfFirst { (ROLL_LOW + it) % 12 == root }
    if (idx < 0) idx = rows.size / 2
    val lens = listOf(1, 2, 2, 2, 4, 4)
    val out = mutableListOf<Triple<Int, Int, Int>>()
    var step = 0
    while (step < STEPS) {
        if (step % 4 != 0 && rnd.nextInt(100) < 15) { step++; continue }
        var len = lens[rnd.nextInt(lens.size)]
        if (step + len > STEPS) len = STEPS - step
        val strong = step % 4 == 0
        val weights = rows.indices.map { i ->
            val d = abs(i - idx)
            if (d > 3) {
                0
            } else {
                var w = when (d) { 0 -> 1; 1 -> 5; 2 -> 3; else -> 1 }
                if (strong && (ROLL_LOW + rows[i]) % 12 in tones) w += 4
                w
            }
        }
        val total = weights.sum()
        var pick = rnd.nextInt(total)
        var chosen = idx
        for (i in weights.indices) {
            pick -= weights[i]
            if (pick < 0) { chosen = i; break }
        }
        idx = chosen
        out.add(Triple(rows[idx], step, len))
        step += len
    }

    if (out.isNotEmpty()) {
        val last = out.last()
        val rootRows = rows.filter { (ROLL_LOW + it) % 12 == root }
        if (rootRows.isNotEmpty()) {
            val target = rootRows.minByOrNull { abs(it - last.first) } ?: last.first
            out[out.size - 1] = Triple(target, last.second, last.third)
        }
    }
    return out
}
