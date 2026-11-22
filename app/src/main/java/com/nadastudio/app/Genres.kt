package com.nadastudio.app

import kotlin.math.abs
import kotlin.random.Random

/**
 * Satu genre. Pola drum ditulis 16 karakter (1 karakter = 1 step):
 * x = selalu bunyi, o = kadang bunyi (60%), . = diam.
 * chordStyle: 0 tahan, 1 offbeat, 2 stab, 3 arpeggio.
 * bassStyle: 0 akar tahan, 1 akar-kwint, 2 syncopasi, 3 denyut.
 * melodyDensity: 0 jarang, 1 sedang, 2 rapat.
 */
class Genre(
    val name: String,
    val bpm: Int,
    val scale: ScaleType,
    val prog: List<Int>,
    val kick: String,
    val snare: String,
    val clap: String,
    val hat: String,
    val open: String,
    val synth: String,
    val chordStyle: Int,
    val bassStyle: Int,
    val melodyDensity: Int,
    val melodyWave: Wave,
    val bassWave: Wave,
    val chordWave: Wave
)

private fun pat(vararg g: String): String = g.joinToString("")

private val NONE = pat("....", "....", "....", "....")
private val FOUR = pat("x...", "x...", "x...", "x...")
private val BACK = pat("....", "x...", "....", "x...")
private val EIGHTHS = pat("x.x.", "x.x.", "x.x.", "x.x.")
private val OFFBEAT = pat("..x.", "..x.", "..x.", "..x.")

val GENRES: List<Genre> = listOf(
    Genre(
        name = "Pop", bpm = 110, scale = ScaleType.MAJOR, prog = listOf(0, 4, 5, 3),
        kick = pat("x...", "....", "x.o.", "...."), snare = BACK, clap = NONE,
        hat = EIGHTHS, open = pat("....", "....", "....", "..o."), synth = OFFBEAT,
        chordStyle = 0, bassStyle = 1, melodyDensity = 1,
        melodyWave = Wave.PIANO, bassWave = Wave.BASS, chordWave = Wave.STRINGS
    ),
    Genre(
        name = "Rock", bpm = 125, scale = ScaleType.MAJOR, prog = listOf(0, 3, 4, 0),
        kick = pat("x...", "....", "x.o.", "...."), snare = BACK, clap = NONE,
        hat = EIGHTHS, open = pat("....", "..o.", "....", "...."), synth = NONE,
        chordStyle = 2, bassStyle = 1, melodyDensity = 1,
        melodyWave = Wave.LEAD, bassWave = Wave.BASS, chordWave = Wave.DISTGTR
    ),
    Genre(
        name = "Hip-hop", bpm = 90, scale = ScaleType.MINOR, prog = listOf(0, 5, 2, 6),
        kick = pat("x...", "..o.", "..x.", "...."), snare = BACK, clap = NONE,
        hat = pat("x.xo", "x.xo", "x.xo", "x.xo"), open = NONE, synth = pat("....", "..x.", "....", "..x."),
        chordStyle = 1, bassStyle = 2, melodyDensity = 0,
        melodyWave = Wave.EPIANO, bassWave = Wave.BASS808, chordWave = Wave.PAD
    ),
    Genre(
        name = "Trap", bpm = 140, scale = ScaleType.MINOR, prog = listOf(0, 5, 2, 6),
        kick = pat("x...", "..o.", "..x.", ".o.."), snare = pat("....", "....", "x...", "...."),
        clap = pat("....", "....", "x...", "...."), hat = pat("x.x.", "xxx.", "x.x.", "xxxx"),
        open = pat("....", "....", "....", "..x."), synth = NONE,
        chordStyle = 0, bassStyle = 0, melodyDensity = 1,
        melodyWave = Wave.BELL, bassWave = Wave.BASS808, chordWave = Wave.PAD
    ),
    Genre(
        name = "House", bpm = 124, scale = ScaleType.MINOR, prog = listOf(0, 5, 2, 6),
        kick = FOUR, snare = NONE, clap = BACK, hat = EIGHTHS, open = OFFBEAT, synth = OFFBEAT,
        chordStyle = 1, bassStyle = 3, melodyDensity = 1,
        melodyWave = Wave.LEAD, bassWave = Wave.BASS, chordWave = Wave.PIANO
    ),
    Genre(
        name = "Lo-fi", bpm = 80, scale = ScaleType.DORIAN, prog = listOf(1, 4, 0, 0),
        kick = pat("x...", "..o.", ".x..", "...."), snare = BACK, clap = NONE,
        hat = pat("x.o.", "x.o.", "x.o.", "x.o."), open = NONE, synth = NONE,
        chordStyle = 0, bassStyle = 0, melodyDensity = 0,
        melodyWave = Wave.EPIANO, bassWave = Wave.BASS, chordWave = Wave.PAD
    ),
    Genre(
        name = "R&B", bpm = 90, scale = ScaleType.MINOR, prog = listOf(0, 5, 2, 6),
        kick = pat("x...", "..o.", "..x.", "o..."), snare = BACK, clap = NONE,
        hat = EIGHTHS, open = NONE, synth = NONE,
        chordStyle = 0, bassStyle = 1, melodyDensity = 0,
        melodyWave = Wave.CHOIR, bassWave = Wave.BASS, chordWave = Wave.EPIANO
    ),
    Genre(
        name = "Ballad", bpm = 70, scale = ScaleType.MAJOR, prog = listOf(0, 4, 5, 3),
        kick = pat("x...", "....", "x...", "...."), snare = pat("....", "....", "x...", "...."),
        clap = NONE, hat = NONE, open = NONE, synth = NONE,
        chordStyle = 3, bassStyle = 0, melodyDensity = 0,
        melodyWave = Wave.VIOLIN, bassWave = Wave.BASS, chordWave = Wave.PIANO
    ),
    Genre(
        name = "Disco", bpm = 118, scale = ScaleType.MAJOR, prog = listOf(0, 5, 3, 4),
        kick = FOUR, snare = BACK, clap = NONE, hat = EIGHTHS, open = OFFBEAT, synth = OFFBEAT,
        chordStyle = 1, bassStyle = 3, melodyDensity = 1,
        melodyWave = Wave.TRUMPET, bassWave = Wave.BASS, chordWave = Wave.EPIANO
    ),
    Genre(
        name = "Reggaeton (perkiraan)", bpm = 95, scale = ScaleType.MINOR, prog = listOf(0, 6, 5, 6),
        kick = FOUR, snare = pat("...x", "..x.", "...x", "..x."), clap = NONE,
        hat = EIGHTHS, open = NONE, synth = NONE,
        chordStyle = 2, bassStyle = 1, melodyDensity = 1,
        melodyWave = Wave.LEAD, bassWave = Wave.BASS808, chordWave = Wave.PAD
    ),
    Genre(
        name = "Reggae", bpm = 75, scale = ScaleType.MAJOR, prog = listOf(0, 3, 4, 0),
        kick = pat("....", "....", "x...", "...."), snare = pat("....", "....", "x...", "...."),
        clap = NONE, hat = EIGHTHS, open = NONE, synth = OFFBEAT,
        chordStyle = 1, bassStyle = 2, melodyDensity = 0,
        melodyWave = Wave.ORGAN, bassWave = Wave.BASS, chordWave = Wave.GUITAR
    ),
    Genre(
        name = "Funk", bpm = 100, scale = ScaleType.DORIAN, prog = listOf(0, 3, 0, 3),
        kick = pat("x..o", "..x.", "..o.", "x..."), snare = pat("....", "x..o", "....", "x..o"),
        clap = NONE, hat = pat("xoxx", "xoxx", "xoxx", "xoxx"), open = pat("....", "..o.", "....", "..o."),
        synth = pat("..x.", ".x..", "x..x", "..x."),
        chordStyle = 2, bassStyle = 2, melodyDensity = 2,
        melodyWave = Wave.SAX, bassWave = Wave.BASS, chordWave = Wave.GUITAR
    ),
    Genre(
        name = "Jazz", bpm = 125, scale = ScaleType.DORIAN, prog = listOf(1, 4, 0, 0),
        kick = pat("x...", "....", "o...", "...."), snare = pat("....", "o...", "....", "o..."),
        clap = NONE, hat = pat("x..x", ".x..", "x..x", ".x.."), open = NONE, synth = NONE,
        chordStyle = 2, bassStyle = 1, melodyDensity = 1,
        melodyWave = Wave.SAX, bassWave = Wave.BASS, chordWave = Wave.PIANO
    ),
    Genre(
        name = "Blues", bpm = 90, scale = ScaleType.MIXOLYDIAN, prog = listOf(0, 3, 0, 4),
        kick = pat("x...", "....", "x...", "...."), snare = BACK, clap = NONE,
        hat = EIGHTHS, open = NONE, synth = NONE,
        chordStyle = 0, bassStyle = 1, melodyDensity = 1,
        melodyWave = Wave.GUITAR, bassWave = Wave.BASS, chordWave = Wave.ORGAN
    ),
    Genre(
        name = "Metal", bpm = 160, scale = ScaleType.PHRYGIAN, prog = listOf(0, 1, 0, 5),
        kick = EIGHTHS, snare = BACK, clap = NONE, hat = EIGHTHS, open = NONE, synth = NONE,
        chordStyle = 2, bassStyle = 3, melodyDensity = 2,
        melodyWave = Wave.DISTGTR, bassWave = Wave.BASS, chordWave = Wave.DISTGTR
    ),
    Genre(
        name = "Country", bpm = 110, scale = ScaleType.MAJOR, prog = listOf(0, 3, 4, 0),
        kick = pat("x...", "....", "x...", "...."), snare = BACK, clap = NONE,
        hat = NONE, open = NONE, synth = NONE,
        chordStyle = 2, bassStyle = 1, melodyDensity = 1,
        melodyWave = Wave.VIOLIN, bassWave = Wave.BASS, chordWave = Wave.GUITAR
    ),
    Genre(
        name = "Chill / Ambient", bpm = 70, scale = ScaleType.LYDIAN, prog = listOf(0, 4, 5, 3),
        kick = pat("x...", "....", "....", "...."), snare = NONE, clap = NONE,
        hat = NONE, open = NONE, synth = NONE,
        chordStyle = 0, bassStyle = 0, melodyDensity = 0,
        melodyWave = Wave.BELL, bassWave = Wave.BASS, chordWave = Wave.PAD
    ),
    Genre(
        name = "Bossa Nova / Latin (perkiraan)", bpm = 120, scale = ScaleType.MAJOR, prog = listOf(1, 4, 0, 0),
        kick = pat("x..x", "..x.", "..x.", ".x.."), snare = pat("x..x", "..x.", ".x..", "x..."),
        clap = NONE, hat = EIGHTHS, open = NONE, synth = NONE,
        chordStyle = 2, bassStyle = 1, melodyDensity = 1,
        melodyWave = Wave.FLUTE, bassWave = Wave.BASS, chordWave = Wave.GUITAR
    ),
    Genre(
        name = "Dangdut (perkiraan)", bpm = 115, scale = ScaleType.HARM_MINOR, prog = listOf(0, 3, 4, 0),
        kick = pat("x...", "....", "x.x.", "...."), snare = pat("..x.", "....", ".x..", "...."),
        clap = pat("....", "x...", "....", "x.x."), hat = EIGHTHS, open = NONE, synth = NONE,
        chordStyle = 1, bassStyle = 2, melodyDensity = 2,
        melodyWave = Wave.SULING, bassWave = Wave.BASS, chordWave = Wave.GUITAR
    ),
    Genre(
        name = "Koplo (perkiraan)", bpm = 135, scale = ScaleType.HARM_MINOR, prog = listOf(0, 3, 4, 0),
        kick = pat("x..x", "..x.", "x..x", "..x."), snare = NONE,
        clap = pat("..x.", "..x.", "..x.", "x.x."), hat = EIGHTHS,
        open = pat("....", "....", "....", "..x."), synth = OFFBEAT,
        chordStyle = 1, bassStyle = 2, melodyDensity = 2,
        melodyWave = Wave.SULING, bassWave = Wave.BASS, chordWave = Wave.GUITAR
    ),
    Genre(
        name = "Keroncong (perkiraan)", bpm = 95, scale = ScaleType.MAJOR, prog = listOf(0, 3, 4, 0),
        kick = NONE, snare = NONE, clap = NONE, hat = NONE, open = NONE, synth = NONE,
        chordStyle = 1, bassStyle = 1, melodyDensity = 1,
        melodyWave = Wave.VIOLIN, bassWave = Wave.PLUCK, chordWave = Wave.GUITAR
    )
)

private fun gRowOf(pc: Int, lo: Int): Int {
    for (r in lo until minOf(lo + 12, ROLL_ROWS)) {
        if ((ROLL_LOW + r) % 12 == pc) return r
    }
    return lo
}

private fun gSteps(type: ScaleType): List<Int> =
    if (type == ScaleType.CHROMATIC) ScaleType.MAJOR.steps else type.steps

/** Pola Channel Rack sesuai genre: 6 channel x 16 step. */
fun generateDrumsFor(g: Genre): BooleanArray {
    val out = BooleanArray(CHANNEL_NAMES.size * STEPS)
    val rnd = Random(System.nanoTime())
    val pats = listOf(g.kick, g.snare, g.clap, g.hat, g.open, g.synth)
    for (ch in pats.indices) {
        for (s in 0 until STEPS) {
            val c = pats[ch].getOrNull(s) ?: '.'
            if (c == 'x' || (c == 'o' && rnd.nextInt(100) < 60)) out[ch * STEPS + s] = true
        }
    }
    return out
}

/** Akor sesuai urutan dan gaya genre. Hasil: (baris, step, panjang). */
fun generateChordsFor(root: Int, type: ScaleType, octIndex: Int, g: Genre): List<Triple<Int, Int, Int>> {
    val s = gSteps(type)
    val lo = octIndex * 12
    val out = mutableListOf<Triple<Int, Int, Int>>()
    for (grp in 0 until 4) {
        val d = g.prog[grp % g.prog.size]
        val rows = intArrayOf(0, 2, 4).map { k -> gRowOf((root + s[(d + k) % s.size]) % 12, lo) }
        when (g.chordStyle) {
            0 -> rows.forEach { out.add(Triple(it, grp * 4, 4)) }
            1 -> rows.forEach { out.add(Triple(it, grp * 4 + 2, 1)) }
            2 -> {
                rows.forEach { out.add(Triple(it, grp * 4, 1)) }
                rows.forEach { out.add(Triple(it, grp * 4 + 3, 1)) }
            }
            else -> {
                val order = intArrayOf(0, 1, 2, 1)
                for (i in 0 until 4) out.add(Triple(rows[order[i]], grp * 4 + i, 1))
            }
        }
    }
    return out
}

/** Bass sesuai urutan akor dan gaya genre. */
fun generateBassFor(root: Int, type: ScaleType, octIndex: Int, g: Genre): List<Triple<Int, Int, Int>> {
    val s = gSteps(type)
    val lo = octIndex * 12
    val rnd = Random(System.nanoTime())
    val out = mutableListOf<Triple<Int, Int, Int>>()
    for (grp in 0 until 4) {
        val d = g.prog[grp % g.prog.size]
        val cr = (root + s[d % s.size]) % 12
        val r0 = gRowOf(cr, lo)
        val r5 = gRowOf((cr + 7) % 12, lo)
        when (g.bassStyle) {
            0 -> out.add(Triple(r0, grp * 4, 4))
            1 -> {
                out.add(Triple(r0, grp * 4, 2))
                out.add(Triple(if (rnd.nextInt(100) < 40) r5 else r0, grp * 4 + 2, 2))
            }
            2 -> {
                out.add(Triple(r0, grp * 4, 1))
                out.add(Triple(r0, grp * 4 + 2, 1))
                out.add(Triple(if (rnd.nextBoolean()) r5 else r0, grp * 4 + 3, 1))
            }
            else -> {
                out.add(Triple(r0, grp * 4, 1))
                out.add(Triple(r0, grp * 4 + 2, 1))
            }
        }
    }
    return out
}

/** Melodi berbentuk frasa (A, jawaban A, B, A) dengan kerapatan sesuai genre. */
fun generateMelodyFor(root: Int, type: ScaleType, octIndex: Int, g: Genre): List<Triple<Int, Int, Int>> {
    val set = scaleSet(root, type)
    val lo = octIndex * 12
    val rows = (lo until minOf(lo + 12, ROLL_ROWS)).filter { (ROLL_LOW + it) % 12 in set }
    if (rows.isEmpty()) return emptyList()

    val rnd = Random(System.nanoTime())
    val rhythms: List<List<Pair<Int, Int>>> = when (g.melodyDensity) {
        0 -> listOf(
            listOf(0 to 4),
            listOf(0 to 2, 2 to 2),
            listOf(0 to 3, 3 to 1)
        )
        2 -> listOf(
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
            listOf(0 to 1, 1 to 1, 2 to 2),
            listOf(0 to 2, 2 to 1, 3 to 1),
            listOf(0 to 1, 1 to 2, 3 to 1)
        )
        else -> listOf(
            listOf(0 to 2, 2 to 2),
            listOf(0 to 1, 1 to 1, 2 to 2),
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
            listOf(0 to 3, 3 to 1),
            listOf(0 to 2, 2 to 1, 3 to 1)
        )
    }
    var rootIdx = rows.indexOfFirst { (ROLL_LOW + it) % 12 == root }
    if (rootIdx < 0) rootIdx = rows.size / 2

    fun clampIdx(i: Int): Int = i.coerceIn(0, rows.size - 1)

    fun makePitches(n: Int, startIdx: Int): List<Int> {
        val res = ArrayList<Int>()
        var idx = clampIdx(startIdx)
        val moves = listOf(-2, -1, -1, 0, 1, 1, 2)
        for (k in 0 until n) {
            res.add(idx)
            idx = clampIdx(idx + moves[rnd.nextInt(moves.size)])
        }
        return res
    }

    val rhA = rhythms[rnd.nextInt(rhythms.size)]
    val rhB = rhythms[rnd.nextInt(rhythms.size)]
    val pa = makePitches(rhA.size, rootIdx + rnd.nextInt(3))
    val pb = makePitches(rhB.size, rootIdx + rnd.nextInt(5) - 1)

    val out = mutableListOf<Triple<Int, Int, Int>>()
    fun addPhrase(grp: Int, rh: List<Pair<Int, Int>>, pitches: List<Int>, shift: Int) {
        rh.forEachIndexed { i, (off, len) ->
            out.add(Triple(rows[clampIdx(pitches[i] + shift)], grp * 4 + off, len))
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
