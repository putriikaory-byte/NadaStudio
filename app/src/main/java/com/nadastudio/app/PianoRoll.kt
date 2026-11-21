package com.nadastudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BLACK_PC = setOf(1, 3, 6, 8, 10)

@Composable
fun PianoRoll(
    synth: SynthEngine,
    roll: SnapshotStateList<Int>,
    octIndex: Int,
    lenSel: Int,
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
        for (k in 0 until 12) {
            val row = octIndex * 12 + (11 - k)
            val midi = ROLL_LOW + row
            val pc = midi % 12
            val isBlack = pc in BLACK_PC
            val inScale = !highlight || pc in scale
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Box(
                    Modifier.width(44.dp).fillMaxHeight()
                        .background(if (isBlack) Color(0xFF222222) else Color(0xFFE8E8EE))
                        .clickable { synth.previewNote(midi) },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        NOTE_NAMES[pc] + (midi / 12 - 1),
                        fontSize = 9.sp,
                        color = if (isBlack) Color.White else Color.Black,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                for (s in 0 until STEPS) {
                    val isStart = roll[row * STEPS + s] > 0
                    val covered = coverStart(row, s) >= 0
                    var base = when {
                        !inScale -> Color(0xFF30343F)
                        highlight && pc == root -> Color(0xFF5B4A45)
                        else -> Color(0xFF4A5068)
                    }
                    if ((s / 4) % 2 == 1) base = lerp(base, Color.Black, 0.15f)
                    var c = when {
                        isStart -> Color(0xFFFF7A2F)
                        covered -> Color(0xFFCC5E22)
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
