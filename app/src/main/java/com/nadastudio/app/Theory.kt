package com.nadastudio.app

val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

enum class ScaleType(val label: String, val steps: List<Int>) {
    MAJOR("Mayor", listOf(0, 2, 4, 5, 7, 9, 11)),
    MINOR("Minor", listOf(0, 2, 3, 5, 7, 8, 10)),
    PENT_MAJOR("Pentatonik Mayor", listOf(0, 2, 4, 7, 9)),
    PENT_MINOR("Pentatonik Minor", listOf(0, 3, 5, 7, 10)),
    BLUES("Blues", listOf(0, 3, 5, 6, 7, 10)),
    DORIAN("Dorian", listOf(0, 2, 3, 5, 7, 9, 10)),
    CHROMATIC("Kromatik", (0..11).toList())
}

fun scaleSet(root: Int, type: ScaleType): Set<Int> = type.steps.map { (root + it) % 12 }.toSet()

/** Geser nada ke nada terdekat yang masuk tangga nada (fitur kunci nada). */
fun snapToScale(note: Int, scale: Set<Int>): Int {
    if (note % 12 in scale) return note
    for (d in 1..6) {
        if (((note - d) % 12 + 12) % 12 in scale) return note - d
        if ((note + d) % 12 in scale) return note + d
    }
    return note
}
