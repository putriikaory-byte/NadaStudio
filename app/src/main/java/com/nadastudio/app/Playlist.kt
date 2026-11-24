package com.nadastudio.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Playlist / timeline sederhana ala FL Studio: klip vokal (waveform) di atas
 * grid bar. Klip bisa digeser dengan drag atau tombol, selalu snap ke step
 * terdekat (1 step = 1/4 ketuk). Garis merah = posisi main saat ini.
 */
@Composable
fun PlaylistTimeline(
    clip: ShortArray?,
    version: Int,
    bars: Int,
    bpm: Int,
    startStep: Int,
    onStartStep: (Int) -> Unit,
    playingBar: Int,
    playingStep: Int,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pxPerStep = with(density) { 22.dp.toPx() }
    val totalSteps = bars.coerceAtLeast(1) * STEPS
    val totalWidthDp = with(density) { (totalSteps * pxPerStep).toDp() }
    val barWidthDp = with(density) { (STEPS * pxPerStep).toDp() }
    val cSteps = if (clip != null) clipSteps(clip.size, bpm) else 0
    val maxStart = (totalSteps - 1).coerceAtLeast(0)
    val scroll = rememberScrollState()
    var dragging by remember { mutableStateOf(false) }

    val peaks = remember(version, clip, bpm) {
        val out = FloatArray(cSteps)
        if (clip != null && clip.isNotEmpty() && cSteps > 0) {
            for (c in 0 until cSteps) {
                val i0 = (c.toLong() * clip.size / cSteps).toInt()
                val i1 = ((c + 1).toLong() * clip.size / cSteps).toInt().coerceAtMost(clip.size)
                val stp = max(1, (i1 - i0) / 64)
                var mx = 0
                var i = i0
                while (i < i1) {
                    val a = abs(clip[i].toInt())
                    if (a > mx) mx = a
                    i += stp
                }
                out[c] = mx / 32768f
            }
        }
        out
    }

    val startStepState = rememberUpdatedState(startStep)
    val onStartStepState = rememberUpdatedState(onStartStep)

    Column(modifier) {
        Text(
            "Playlist — klip vokal di atas grid bar",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF8A3D)
        )
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll, enabled = !dragging)) {
            Column(Modifier.width(totalWidthDp)) {
                Row(Modifier.fillMaxWidth().height(18.dp)) {
                    for (b in 0 until bars.coerceAtLeast(1)) {
                        Box(Modifier.width(barWidthDp).fillMaxHeight()) {
                            Text("Bar ${b + 1}", fontSize = 10.sp, color = Color(0xFF8A90A8))
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(72.dp)
                        .background(Color(0xFF14172A), RoundedCornerShape(4.dp))
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (s in 0..totalSteps) {
                            val x = s * pxPerStep
                            val isBar = s % STEPS == 0
                            drawLine(
                                color = if (isBar) Color(0xFF5A6070) else Color(0xFF2A2E40),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = if (isBar) 2f else 1f
                            )
                        }
                    }
                    if (clip != null && cSteps > 0) {
                        Box(
                            Modifier
                                .offset { IntOffset((startStep * pxPerStep).roundToInt(), 0) }
                                .width(with(density) { (cSteps * pxPerStep).toDp() })
                                .fillMaxHeight()
                                .background(Color(0xFF1F6F6A), RoundedCornerShape(4.dp))
                                .pointerInput(Unit) {
                                    var acc = 0f
                                    detectHorizontalDragGesture(
                                        onDragStart = { dragging = true },
                                        onDragEnd = { dragging = false },
                                        onDragCancel = { dragging = false },
                                        onHorizontalDrag = { _, dragAmount ->
                                            acc += dragAmount
                                            val dSteps = (acc / pxPerStep).roundToInt()
                                            if (dSteps != 0) {
                                                val cur = startStepState.value
                                                val next = (cur + dSteps).coerceIn(0, maxStart)
                                                if (next != cur) onStartStepState.value(next)
                                                acc -= dSteps * pxPerStep
                                            }
                                        }
                                    )
                                }
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                val cw = size.width / cSteps
                                for (c in 0 until cSteps) {
                                    val ph = peaks[c] * size.height
                                    drawRect(
                                        Color(0xFF29D3C6),
                                        Offset(c * cw, (size.height - ph) / 2f),
                                        Size(maxOf(cw - 1f, 1f), maxOf(ph, 1f))
                                    )
                                }
                            }
                        }
                    }
                    if (playing && playingBar >= 0) {
                        val px = (playingBar * STEPS + playingStep) * pxPerStep
                        Box(
                            Modifier.offset { IntOffset(px.roundToInt(), 0) }
                                .width(2.dp).fillMaxHeight()
                                .background(Color(0xFFFF4444))
                        )
                    }
                }
            }
        }
        Row {
            val curBar = startStep / STEPS + 1
            val curBeat = (startStep % STEPS) / 4 + 1
            Text(
                if (clip == null) "Belum ada klip vokal."
                else "Klip mulai Bar $curBar ketuk $curBeat · panjang $cSteps step · geser (snap 1 step):",
                fontSize = 11.sp
            )
        }
        Row {
            OutlinedButton(onClick = { onStartStep((startStep - STEPS).coerceAtLeast(0)) }) { Text("−1 bar") }
            OutlinedButton(onClick = { onStartStep((startStep - 4).coerceAtLeast(0)) }) { Text("−1 ketuk") }
            OutlinedButton(onClick = { onStartStep((startStep + 4).coerceAtMost(maxStart)) }) { Text("+1 ketuk") }
            OutlinedButton(onClick = { onStartStep((startStep + STEPS).coerceAtMost(maxStart)) }) { Text("+1 bar") }
        }
    }
}
