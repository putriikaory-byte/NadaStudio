package com.nadastudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BLACK_PC = setOf(1, 3, 6, 8, 10)

private val C_ROOT = Color(0xFF9B3A6E)
private val C_THIRD = Color(0xFF2A7F59)
private val C_FIFTH = Color(0xFF2F66B0)
private val C_SCALE = Color(0xFF4A5068)
private val C_OUT = Color(0xFF30343F)
private val C_NOTE = Color(0xFFFFB300)
private val C_NOTE_TAIL = Color(0xFFC98A00)

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 9.sp, color = Color(0xFFD0D0DC))
    }
}

@Composable
fun PianoRoll(
    synth: SynthEngine,
    roll: SnapshotStateList<Int>,
    octIndex: Int,
    lenSel: Int,
    scaleType: ScaleType,
    scale: Set<Int>,
    root: Int,
    lock: Boolean,
    modifier: Modifier
) {
    var current by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            current = if (synth.playing) synth.currentStep else -1
            delay(20)
        }
    }
    val highlight = scale.size < 12
    val shape = RoundedCornerShape(2.dp)

    fun pick(vararg intervals: Int): Int {
        for (i in intervals) {
            if ((root + i) % 12 in scale) return (root + i) % 12
        }
        return -1
    }
    val third = pick(4, 3)
    val fifth = pick(7, 6, 8)

    fun roleColor(pc: Int): Color = when {
        !highlight -> C_SCALE
        pc !in scale -> C_OUT
        pc == root -> C_ROOT
        pc == third -> C_THIRD
        pc == fifth -> C_FIFTH
        else -> C_SCALE
    }

    fun coverStart(row: Int, s: Int): Int {
        var st = -1
        for (j in 0..s) {
            val l = roll[row * STEPS + j]
            if (l > 0 && j + l > s) st = j
        }
        return st
    }

    fun toggle(row0: Int, s: Int) {
        val st0 = coverStart(row0, s)
        if (st0 >= 0) {
            roll[row0 * STEPS + st0] = 0
            synth.rollLen[row0 * STEPS + st0] = 0
            return
        }
        var row = row0
        if (lock) {
            val m = snapToScale(ROLL_LOW + row0, scale) - ROLL_LOW
            if (m in 0 until ROLL_ROWS) row = m
        }
        if (coverStart(row, s) >= 0) return
        var l = lenSel
        for (j in s + 1 until s + l) {
            if (j >= STEPS || roll[row * STEPS + j] > 0) { l = j - s; break }
        }
        roll[row * STEPS + s] = l
        synth.rollLen[row * STEPS + s] = l
        synth.previewNote(ROLL_LOW + row)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (highlight) {
                LegendItem(C_ROOT, "Nada dasar (rumah)")
                LegendItem(C_THIRD, "Nada 3 (manis)")
                LegendItem(C_FIFTH, "Nada 5 (kuat)")
                LegendItem(C_SCALE, "Nada penghubung")
                LegendItem(C_OUT, "Di luar kunci")
            } else {
                LegendItem(C_SCALE, "Kromatik: semua nada bebas")
            }
            LegendItem(C_NOTE, "Nadamu")
        }
        for (k in 0 until 12) {
            val row = octIndex * 12 + (11 - k)
            val midi = ROLL_LOW + row
            val pc = midi % 12
            val isBlack = pc in BLACK_PC
            val degree = if (highlight) scaleType.steps.indexOfFirst { (root + it) % 12 == pc } else -1
            val label = NOTE_NAMES[pc] + (midi / 12 - 1) + (if (degree >= 0) "  ${degree + 1}" else "")
            val strong = highlight && (pc == root || pc == third || pc == fifth)
            val role = roleColor(pc)
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Box(
                    Modifier.width(58.dp).fillMaxHeight()
                        .background(if (isBlack) Color(0xFF222222) else Color(0xFFE8E8EE))
                        .clickable { synth.previewNote(midi) },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        label,
                        fontSize = 9.sp,
                        fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                        color = if (isBlack) Color.White else Color.Black,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Box(Modifier.width(4.dp).fillMaxHeight().background(role))
                for (s in 0 until STEPS) {
                    val isStart = roll[row * STEPS + s] > 0
                    val covered = coverStart(row, s) >= 0
                    var base = role
                    if ((s / 4) % 2 == 1) base = lerp(base, Color.Black, 0.15f)
                    var c = when {
                        isStart -> C_NOTE
                        covered -> C_NOTE_TAIL
                        else -> base
                    }
                    if (s == current) c = lerp(c, Color.White, 0.25f)
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(c, shape)
                            .clickable { toggle(row, s) }
                    )
                }
            }
        }
    }
}
