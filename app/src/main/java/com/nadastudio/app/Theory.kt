package com.nadastudio.app

val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

enum class ScaleType(val label: String, val steps: List<Int>) {
    MAJOR("Mayor", listOf(0, 2, 4, 5, 7, 9, 11)),
    MINOR("Minor alami", listOf(0, 2, 3, 5, 7, 8, 10)),
    HARM_MINOR("Minor harmonik", listOf(0, 2, 3, 5, 7, 8, 11)),
    MEL_MINOR("Minor melodik", listOf(0, 2, 3, 5, 7, 9, 11)),
    HARM_MAJOR("Mayor harmonik", listOf(0, 2, 4, 5, 7, 8, 11)),
    DORIAN("Dorian", listOf(0, 2, 3, 5, 7, 9, 10)),
    PHRYGIAN("Phrygian", listOf(0, 1, 3, 5, 7, 8, 10)),
    LYDIAN("Lydian", listOf(0, 2, 4, 6, 7, 9, 11)),
    MIXOLYDIAN("Mixolydian", listOf(0, 2, 4, 5, 7, 9, 10)),
    LOCRIAN("Locrian", listOf(0, 1, 3, 5, 6, 8, 10)),
    PENT_MAJOR("Pentatonik Mayor", listOf(0, 2, 4, 7, 9)),
    PENT_MINOR("Pentatonik Minor", listOf(0, 3, 5, 7, 10)),
    BLUES("Blues", listOf(0, 3, 5, 6, 7, 10)),
    BLUES_MAJOR("Blues Mayor", listOf(0, 2, 3, 4, 7, 9)),
    WHOLE_TONE("Whole Tone", listOf(0, 2, 4, 6, 8, 10)),
    DIM_WH("Diminished (W-H)", listOf(0, 2, 3, 5, 6, 8, 9, 11)),
    DIM_HW("Diminished (H-W)", listOf(0, 1, 3, 4, 6, 7, 9, 10)),
    AUGMENTED("Augmented", listOf(0, 3, 4, 7, 8, 11)),
    DOUBLE_HARM("Double Harmonic (Arabic)", listOf(0, 1, 4, 5, 7, 8, 11)),
    HUNG_MINOR("Hungarian Minor", listOf(0, 2, 3, 6, 7, 8, 11)),
    HUNG_MAJOR("Hungarian Major", listOf(0, 3, 4, 6, 7, 9, 10)),
    NEAPOLITAN_MINOR("Neapolitan Minor", listOf(0, 1, 3, 5, 7, 8, 11)),
    NEAPOLITAN_MAJOR("Neapolitan Major", listOf(0, 1, 3, 5, 7, 9, 11)),
    PHRYG_DOM("Spanish (Phrygian Dominant)", listOf(0, 1, 4, 5, 7, 8, 10)),
    PERSIAN("Persian", listOf(0, 1, 4, 5, 6, 8, 11)),
    HIRAJOSHI("Hirajoshi", listOf(0, 2, 3, 7, 8)),
    IN_SEN("In Sen", listOf(0, 1, 5, 7, 10)),
    IWATO("Iwato", listOf(0, 1, 5, 6, 10)),
    YO("Yo", listOf(0, 2, 5, 7, 9)),
    PELOG("Pelog (perkiraan)", listOf(0, 1, 3, 7, 8)),
    SLENDRO("Slendro (perkiraan)", listOf(0, 2, 5, 7, 9)),
    LYDIAN_DOM("Lydian Dominant", listOf(0, 2, 4, 6, 7, 9, 10)),
    LYDIAN_AUG("Lydian Augmented", listOf(0, 2, 4, 6, 8, 9, 11)),
    ALTERED("Altered (Super Locrian)", listOf(0, 1, 3, 4, 6, 8, 10)),
    DORIAN_B2("Dorian b2", listOf(0, 1, 3, 5, 7, 9, 10)),
    PROMETHEUS("Prometheus", listOf(0, 2, 4, 6, 9, 10)),
    BEBOP_DOM("Bebop Dominant", listOf(0, 2, 4, 5, 7, 9, 10, 11)),
    BEBOP_MAJOR("Bebop Mayor", listOf(0, 2, 4, 5, 7, 8, 9, 11)),
    ENIGMATIC("Enigmatic", listOf(0, 1, 4, 6, 8, 10, 11)),
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
