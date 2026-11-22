package com.nadastudio.app

import android.content.Context
import java.io.File

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
    val trackMutes: BooleanArray
)

object ProjectStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "proyek1.nada")

    fun exists(ctx: Context): Boolean = file(ctx).exists()

    fun toText(d: ProjectData): String {
        val sb = StringBuilder()
        sb.append("v=2\n")
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
        return sb.toString()
    }

    fun fromText(text: String): ProjectData? = try {
        val m = text.lines()
            .filter { it.contains("=") }
            .associate { it.substringBefore("=") to it.substringAfter("=").trim() }
        if (!m.containsKey("bpm")) {
            null
        } else {
            val defaults = listOf(Wave.PIANO, Wave.BASS, Wave.STRINGS)
            ProjectData(
                bpm = (m["bpm"]?.toInt() ?: 120).coerceIn(40, 240),
                wave = Wave.valueOf(m["wave"] ?: "SINE"),
                root = (m["root"]?.toInt() ?: 0).coerceIn(0, 11),
                scale = ScaleType.valueOf(m["scale"] ?: "MAJOR"),
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
                trackMutes = BooleanArray(TRACKS) { m["tmutes"]?.getOrNull(it) == '1' }
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
