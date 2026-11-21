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
    val roll: IntArray
)

object ProjectStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "proyek1.nada")

    fun exists(ctx: Context): Boolean = file(ctx).exists()

    fun save(ctx: Context, d: ProjectData): Boolean = try {
        val sb = StringBuilder()
        sb.append("v=1\n")
        sb.append("bpm=").append(d.bpm).append('\n')
        sb.append("wave=").append(d.wave.name).append('\n')
        sb.append("root=").append(d.root).append('\n')
        sb.append("scale=").append(d.scale.name).append('\n')
        sb.append("lock=").append(if (d.lock) 1 else 0).append('\n')
        sb.append("seqNote=").append(d.seqNote).append('\n')
        sb.append("steps=").append(d.steps.joinToString("") { if (it) "1" else "0" }).append('\n')
        sb.append("mutes=").append(d.mutes.joinToString("") { if (it) "1" else "0" }).append('\n')
        sb.append("roll=").append(d.roll.joinToString(",")).append('\n')
        file(ctx).writeText(sb.toString())
        true
    } catch (e: Exception) {
        false
    }

    fun load(ctx: Context): ProjectData? = try {
        val f = file(ctx)
        if (!f.exists()) {
            null
        } else {
            val m = f.readLines()
                .filter { it.contains("=") }
                .associate { it.substringBefore("=") to it.substringAfter("=") }
            ProjectData(
                bpm = (m["bpm"]?.toInt() ?: 120).coerceIn(40, 240),
                wave = Wave.valueOf(m["wave"] ?: "SINE"),
                root = (m["root"]?.toInt() ?: 0).coerceIn(0, 11),
                scale = ScaleType.valueOf(m["scale"] ?: "MAJOR"),
                lock = m["lock"] == "1",
                seqNote = (m["seqNote"]?.toInt() ?: 60).coerceIn(24, 96),
                steps = BooleanArray(CHANNEL_NAMES.size * STEPS) { m["steps"]?.getOrNull(it) == '1' },
                mutes = BooleanArray(CHANNEL_NAMES.size) { m["mutes"]?.getOrNull(it) == '1' },
                roll = IntArray(ROLL_ROWS * STEPS).also { arr ->
                    val parts = (m["roll"] ?: "").split(",")
                    for (i in arr.indices) {
                        arr[i] = parts.getOrNull(i)?.toIntOrNull()?.coerceIn(0, STEPS) ?: 0
                    }
                }
            )
        }
    } catch (e: Exception) {
        null
    }
}
