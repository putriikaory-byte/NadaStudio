package com.nadastudio.app

import android.content.Context
import java.io.File
import kotlin.math.roundToInt

class ProjectData(
    val bpm: Int,
    val wave: Wave,
    val root: Int,
    val scale: ScaleType,
    val lock: Boolean,
    val seqNote: Int,
    val steps: BooleanArray,
    val mutes: BooleanArray,
    val roll: IntArray,
    val trackWaves: List<Wave>,
    val trackMutes: BooleanArray,
    val mixVol: FloatArray,
    val mixPan: FloatArray,
    val reverb: Float,
    val delay: Float,
    val master: Float,
    val vShift: Int,
    val vLoop: Boolean,
    /** Aransemen multi-bar dari vokal; null = proyek 1 bar lama (format v=3). */
    val arrangement: VocalArrangement? = null
)

object ProjectStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "proyek1.nada")

    fun exists(ctx: Context): Boolean = file(ctx).exists()

    fun toText(d: ProjectData): String {
        val sb = StringBuilder()
        val arr = d.arrangement
        sb.append(if (arr != null) "v=4\n" else "v=3\n")
        sb.append("bpm=").append(d.bpm).append('\n')
        sb.append("wave=").append(d.wave.name).append('\n')
        sb.append("root=").append(d.root).append('\n')
        sb.append("scale=").append(d.scale.name).append('\n')
        sb.append("lock=").append(if (d.lock) 1 else 0).append('\n')
        sb.append("seqNote=").append(d.seqNote).append('\n')
        sb.append("steps=").append(d.steps.joinToString("") { if (it) "1" else "0" }).append('\n')
        sb.append("mutes=").append(d.mutes.joinToString("") { if (it) "1" else "0" }).append('\n')
        sb.append("roll=").append(d.roll.joinToString(",")).append('\n')
        sb.append("twaves=").append(d.trackWaves.joinToString(",") { it.name }).append('\n')
        sb.append("tmutes=").append(d.trackMutes.joinToString("") { if (it) "1" else "0" }).append('\n')
        sb.append("mixvol=").append(d.mixVol.joinToString(",")).append('\n')
        sb.append("mixpan=").append(d.mixPan.joinToString(",")).append('\n')
        sb.append("reverb=").append(d.reverb).append('\n')
        sb.append("delay=").append(d.delay).append('\n')
        sb.append("master=").append(d.master).append('\n')
        sb.append("vshift=").append(d.vShift).append('\n')
        sb.append("vloop=").append(if (d.vLoop) 1 else 0).append('\n')
        if (arr != null) {
            sb.append("bars=").append(arr.bars).append('\n')
            sb.append("arrGenre=").append(arr.genreName).append('\n')
            sb.append("arrDegrees=").append(arr.barInfos.joinToString(",") { it.degree.toString() }).append('\n')
            sb.append("arrRoots=").append(arr.barInfos.joinToString(",") { it.chordRootPc.toString() }).append('\n')
            sb.append("arrSections=").append(arr.barInfos.joinToString(",") { it.section.name }).append('\n')
            sb.append("arrVocal=").append(arr.barInfos.joinToString(",") { if (it.hasVocal) "1" else "0" }).append('\n')
            sb.append("arrDensity=").append(arr.barInfos.joinToString(",") { ((it.vocalDensity * 100).roundToInt() / 100.0).toString() }).append('\n')
            sb.append("arrDrums=").append(arr.drums.joinToString(";") { b -> b.joinToString("") { if (it) "1" else "0" } }).append('\n')
            sb.append("arrRoll=").append(arr.rolls.joinToString(";") { encodeSparse(it) }).append('\n')
        }
        return sb.toString()
    }

    /** Hanya entri bukan nol, "indeks:nilai", supaya file proyek multi-bar tetap kecil. */
    private fun encodeSparse(a: IntArray): String {
        val sb = StringBuilder()
        for (i in a.indices) {
            if (a[i] != 0) {
                if (sb.isNotEmpty()) sb.append(',')
                sb.append(i).append(':').append(a[i])
            }
        }
        return sb.toString()
    }

    private fun decodeSparse(text: String, size: Int): IntArray {
        val out = IntArray(size)
        if (text.isBlank()) return out
        for (part in text.split(",")) {
            val kv = part.split(":")
            if (kv.size != 2) continue
            val i = kv[0].toIntOrNull() ?: continue
            val v = kv[1].toIntOrNull() ?: continue
            if (i in 0 until size) out[i] = v.coerceIn(0, STEPS)
        }
        return out
    }

    private fun parseArrangement(m: Map<String, String>, root: Int, scale: ScaleType): VocalArrangement? {
        return try {
        val bars = (m["bars"]?.toIntOrNull() ?: return null).coerceIn(1, 32)
        val degrees = (m["arrDegrees"] ?: return null).split(",").map { it.toInt() }
        val roots = (m["arrRoots"] ?: return null).split(",").map { it.toInt() }
        val sections = (m["arrSections"] ?: return null).split(",").map { ArrSection.valueOf(it) }
        val hasVocal = (m["arrVocal"] ?: "").split(",").map { it == "1" }
        val densities = (m["arrDensity"] ?: "").split(",").mapNotNull { it.toDoubleOrNull() }
        val drumsText = (m["arrDrums"] ?: return null).split(";")
        val rollText = (m["arrRoll"] ?: return null).split(";")
        if (degrees.size < bars || roots.size < bars || sections.size < bars ||
            drumsText.size < bars || rollText.size < bars
        ) return null
        val barInfos = List(bars) { b ->
            BarInfo(
                index = b,
                section = sections[b],
                degree = degrees[b],
                chordRootPc = roots[b],
                chordName = chordNameFor(root, degrees[b], scale),
                vocalDensity = densities.getOrNull(b) ?: if (hasVocal.getOrNull(b) == true) 1.0 else 0.0,
                hasVocal = hasVocal.getOrNull(b) ?: false
            )
        }
        val drums = List(bars) { b ->
            BooleanArray(CHANNEL_NAMES.size * STEPS) { i -> drumsText[b].getOrNull(i) == '1' }
        }
        val rolls = List(bars) { b -> decodeSparse(rollText[b], TRACKS * ROLL_SIZE) }
        VocalArrangement(
            bars = bars,
            bpm = (m["bpm"]?.toInt() ?: 120).coerceIn(40, 240),
            root = root,
            scale = scale,
            genreName = m["arrGenre"] ?: "Pop",
            barInfos = barInfos,
            drums = drums,
            rolls = rolls
        )
        } catch (e: Exception) {
            null
        }
    }

    fun fromText(text: String): ProjectData? = try {
        val m = text.lines()
            .filter { it.contains("=") }
            .associate { it.substringBefore("=") to it.substringAfter("=").trim() }
        if (!m.containsKey("bpm")) {
            null
        } else {
            val defaults = listOf(Wave.PIANO, Wave.BASS, Wave.STRINGS)
            val parsedRoot = (m["root"]?.toInt() ?: 0).coerceIn(0, 11)
            val parsedScale = ScaleType.valueOf(m["scale"] ?: "MAJOR")
            ProjectData(
                bpm = (m["bpm"]?.toInt() ?: 120).coerceIn(40, 240),
                wave = Wave.valueOf(m["wave"] ?: "SINE"),
                root = parsedRoot,
                scale = parsedScale,
                lock = m["lock"] == "1",
                seqNote = (m["seqNote"]?.toInt() ?: 60).coerceIn(24, 96),
                steps = BooleanArray(CHANNEL_NAMES.size * STEPS) { m["steps"]?.getOrNull(it) == '1' },
                mutes = BooleanArray(CHANNEL_NAMES.size) { m["mutes"]?.getOrNull(it) == '1' },
                roll = IntArray(TRACKS * ROLL_SIZE).also { arr ->
                    val parts = (m["roll"] ?: "").split(",")
                    for (i in arr.indices) {
                        arr[i] = parts.getOrNull(i)?.toIntOrNull()?.coerceIn(0, STEPS) ?: 0
                    }
                },
                trackWaves = List(TRACKS) { i ->
                    val name = m["twaves"]?.split(",")?.getOrNull(i)
                    Wave.values().firstOrNull { it.name == name } ?: defaults[i]
                },
                trackMutes = BooleanArray(TRACKS) { m["tmutes"]?.getOrNull(it) == '1' },
                mixVol = FloatArray(MIX_CH) { i ->
                    m["mixvol"]?.split(",")?.getOrNull(i)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.8f
                },
                mixPan = FloatArray(MIX_CH) { i ->
                    m["mixpan"]?.split(",")?.getOrNull(i)?.toFloatOrNull()?.coerceIn(-1f, 1f) ?: 0f
                },
                reverb = (m["reverb"]?.toFloatOrNull() ?: 0.15f).coerceIn(0f, 1f),
                delay = (m["delay"]?.toFloatOrNull() ?: 0f).coerceIn(0f, 1f),
                master = (m["master"]?.toFloatOrNull() ?: 0.8f).coerceIn(0f, 1f),
                vShift = (m["vshift"]?.toIntOrNull() ?: 0).coerceIn(-500, 500),
                vLoop = m["vloop"] == "1",
                // File lama (v=3) tidak punya baris-baris arr*: arrangement = null.
                arrangement = parseArrangement(m, parsedRoot, parsedScale)
            )
        }
    } catch (e: Exception) {
        null
    }

    fun save(ctx: Context, d: ProjectData): Boolean = try {
        file(ctx).writeText(toText(d))
        true
    } catch (e: Exception) {
        false
    }

    fun load(ctx: Context): ProjectData? = try {
        val f = file(ctx)
        if (!f.exists()) null else fromText(f.readText())
    } catch (e: Exception) {
        null
    }
}
