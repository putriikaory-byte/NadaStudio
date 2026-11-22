package com.nadastudio.app

import kotlin.math.abs
import kotlin.random.Random

val DRUM_STYLES = listOf("Pop", "Rock", "Hip-hop", "Dance", "Santai")
val DRUM_BPM = listOf(110, 120, 90, 124, 80)

/** Nada kuat tangga nada: nada ke-1, ke-3, dan ke-5. */
fun chordTones(root: Int, type: ScaleType): Set<Int> {
    if (type == ScaleType.CHROMATIC) return setOf(root, (root + 4) % 12, (root + 7) % 12)
    val s = type.steps
    return if (s.size >= 5) setOf(s[0], s[2], s[4]).map { (root + it) % 12 }.toSet() else setOf(root)
}

/** Pola Channel Rack: 6 channel x 16 step (Kick, Snare, Clap, Hat, Open Hat, Synth). */
fun generateDrums(style: Int): BooleanArray {
    val g = BooleanArray(CHANNEL_NAMES.size * STEPS)
    val rnd = Random(System.nanoTime())
    fun on(ch: Int, vararg st: Int) {
        for (s in st) g[ch * STEPS + s] = true
    }
    when (style) {
        0 -> {
            on(0, 0, 8)
            if (rnd.nextBoolean()) on(0, 10) else on(0, 6)
            on(1, 4, 12)
            for (s in 0 until STEPS step 2) on(3, s)
            on(4, 14)
        }
        1 -> {
            on(0, 0, 8)
            if (rnd.nextBoolean()) on(0, 10)
            on(1, 4, 12)
            for (s in 0 until STEPS step 2) on(3, s)
            on(4, 6)
        }
        2 -> {
            on(0, 0, 7, 10)
            on(1, 4, 12)
            on(2, 12)
            for (s in listOf(0, 2, 3, 4, 6, 8, 10, 11, 12, 14)) {
                if (rnd.nextInt(100) < 85) on(3, s)
            }
            on(4, 15)
        }
        3 -> {
            on(0, 0, 4, 8, 12)
            on(2, 4, 12)
            on(3, 2, 10)
            on(4, 6, 14)
        }
        else -> {
            on(0, 0, 10)
            on(1, 4, 12)
            for (s in 0 until STEPS step 2) {
                if (rnd.nextInt(100) < 70) on(3, s)
            }
        }
    }
    val syn = if (style == 2) listOf(3, 7, 11, 15) else listOf(2, 6, 10, 14)
    for (s in syn) {
        if (rnd.nextInt(100) < 75) on(SYNTH_CH, s)
    }
    return g
}

/** Melodi berbentuk frasa: A, jawaban A, B, lalu A dan berakhir di nada dasar. */
fun generateMelody(root: Int, type: ScaleType, octIndex: Int): List<Triple<Int, Int, Int>> {
    val set = scaleSet(root, type)
    val lo = octIndex * 12
    val rows = (lo until minOf(lo + 12, ROLL_ROWS)).filter { (ROLL_LOW + it) % 12 in set }
    if (rows.isEmpty()) return emptyList()

    val rnd = Random(System.nanoTime())
    val rhythms = listOf(
        listOf(0 to 2, 2 to 2),
        listOf(0 to 1, 1 to 1, 2 to 2),
        listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
        listOf(0 to 3, 3 to 1),
        listOf(0 to 2, 2 to 1, 3 to 1)
    )
    var rootIdx = rows.indexOfFirst { (ROLL_LOW + it) % 12 == root }
    if (rootIdx < 0) rootIdx = rows.size / 2

    fun clampIdx(i: Int): Int = i.coerceIn(0, rows.size - 1)

    fun makePitches(n: Int, startIdx: Int): List<Int> {
        val out = ArrayList<Int>()
        var idx = clampIdx(startIdx)
        val moves = listOf(-2, -1, -1, 0, 1, 1, 2)
        for (k in 0 until n) {
            out.add(idx)
            idx = clampIdx(idx + moves[rnd.nextInt(moves.size)])
        }
        return out
    }

    val rhA = rhythms[rnd.nextInt(rhythms.size)]
    val rhB = rhythms[rnd.nextInt(rhythms.size)]
    val pa = makePitches(rhA.size, rootIdx + rnd.nextInt(3))
    val pb = makePitches(rhB.size, rootIdx + rnd.nextInt(5) - 1)

    val out = mutableListOf<Triple<Int, Int, Int>>()
    fun addPhrase(g: Int, rh: List<Pair<Int, Int>>, pitches: List<Int>, shift: Int) {
        rh.forEachIndexed { i, (off, len) ->
            out.add(Triple(rows[clampIdx(pitches[i] + shift)], g * 4 + off, len))
        }
    }
    addPhrase(0, rhA, pa, 0)
    addPhrase(1, rhA, pa, if (rnd.nextBoolean()) 1 else -1)
    addPhrase(2, rhB, pb, 0)
    addPhrase(3, rhA, pa, 0)

    val last = out.last()
    val rootRows = rows.filter { (ROLL_LOW + it) % 12 == root }
    if (rootRows.isNotEmpty()) {
        val target = rootRows.minByOrNull { abs(it - last.first) } ?: last.first
        out[out.size - 1] = Triple(target, last.second, last.third)
    }
    return out
}

private fun rowOf(pc: Int, lo: Int): Int {
    for (r in lo until minOf(lo + 12, ROLL_ROWS)) {
        if ((ROLL_LOW + r) % 12 == pc) return r
    }
    return lo
}

private fun scaleSteps(type: ScaleType): List<Int> =
    if (type == ScaleType.CHROMATIC) ScaleType.MAJOR.steps else type.steps

/** Bass otomatis: mengikuti urutan akor I - V - vi - IV dalam tangga nada. */
fun generateBass(root: Int, type: ScaleType, octIndex: Int): List<Triple<Int, Int, Int>> {
    val s = scaleSteps(type)
    val lo = octIndex * 12
    val rnd = Random(System.nanoTime())
    val degrees = listOf(0, 4, 5, 3)
    val patterns = listOf(
        listOf(0 to 2, 2 to 2),
        listOf(0 to 4),
        listOf(0 to 1, 1 to 1, 2 to 2),
        listOf(0 to 2, 3 to 1)
    )
    val out = mutableListOf<Triple<Int, Int, Int>>()
    for (g in 0 until 4) {
        val chordRoot = (root + s[degrees[g] % s.size]) % 12
        val fifth = (chordRoot + 7) % 12
        val pat = patterns[rnd.nextInt(patterns.size)]
        pat.forEachIndexed { i, (off, len) ->
            val pc = if (i > 0 && rnd.nextInt(100) < 30) fifth else chordRoot
            out.add(Triple(rowOf(pc, lo), g * 4 + off, len))
        }
    }
    return out
}

/** Akor otomatis: tiga nada (1-3-5) per ketukan, urutan I - V - vi - IV. */
fun generateChords(root: Int, type: ScaleType, octIndex: Int): List<Triple<Int, Int, Int>> {
    val s = scaleSteps(type)
    val lo = octIndex * 12
    val degrees = listOf(0, 4, 5, 3)
    val out = mutableListOf<Triple<Int, Int, Int>>()
    for (g in 0 until 4) {
        for (k in intArrayOf(0, 2, 4)) {
            val pc = (root + s[(degrees[g] + k) % s.size]) % 12
            out.add(Triple(rowOf(pc, lo), g * 4, 4))
        }
    }
    return out
}
