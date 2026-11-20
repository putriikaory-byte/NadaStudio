package com.nadastudio.app

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

private val WHITE = intArrayOf(0, 2, 4, 5, 7, 9, 11)
private val BLACK_AFTER = setOf(0, 1, 3, 4, 5)

private fun noteAt(x: Float, y: Float, w: Float, h: Float, start: Int, octaves: Int): Int? {
    if (x < 0 || y < 0 || x >= w || y >= h) return null
    val count = 7 * octaves
    val kw = w / count
    if (y < h * 0.6f) {
        for (i in 0 until count - 1) {
            if (i % 7 in BLACK_AFTER && abs(x - (i + 1) * kw) < kw * 0.3f) {
                return start + 12 * (i / 7) + WHITE[i % 7] + 1
            }
        }
    }
    val i = (x / kw).toInt().coerceIn(0, count - 1)
    return start + 12 * (i / 7) + WHITE[i % 7]
}

@Composable
fun PianoKeyboard(
    modifier: Modifier,
    startNote: Int,
    octaves: Int,
    scale: Set<Int>,
    root: Int,
    lock: Boolean,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit
) {
    val down = remember { mutableStateMapOf<Int, Boolean>() }
    val scaleNow by rememberUpdatedState(scale)
    val lockNow by rememberUpdatedState(lock)
    val onOn by rememberUpdatedState(onNoteOn)
    val onOff by rememberUpdatedState(onNoteOff)
    val accent = MaterialTheme.colorScheme.primary

    Canvas(
        modifier.pointerInput(startNote, octaves) {
            val active = HashMap<PointerId, Int>()
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    for (ch in ev.changes) {
                        if (ch.pressed) {
                            var n = noteAt(
                                ch.position.x, ch.position.y,
                                size.width.toFloat(), size.height.toFloat(), startNote, octaves
                            )
                            if (n != null && lockNow) n = snapToScale(n, scaleNow)
                            val prev = active[ch.id]
                            if (n != prev) {
                                if (prev != null) { onOff(prev); down.remove(prev) }
                                if (n != null) { onOn(n); down[n] = true; active[ch.id] = n }
                                else active.remove(ch.id)
                            }
                        } else {
                            active.remove(ch.id)?.let { onOff(it); down.remove(it) }
                        }
                        ch.consume()
                    }
                }
            }
        }
    ) {
        val count = 7 * octaves
        val kw = size.width / count
        val bh = size.height * 0.6f
        val highlight = scale.size < 12

        for (i in 0 until count) {
            val n = startNote + 12 * (i / 7) + WHITE[i % 7]
            val pc = n % 12
            val c = when {
                down[n] == true -> accent
                highlight && pc == root -> Color(0xFFFFC999)
                highlight && pc in scale -> Color(0xFFCDEFFF)
                highlight && lock -> Color(0xFF8C8C99)
                else -> Color.White
            }
            drawRect(c, Offset(i * kw, 0f), Size(kw, size.height))
            drawRect(Color.Black, Offset(i * kw, 0f), Size(kw, size.height), style = Stroke(2f))
        }
        for (i in 0 until count - 1) {
            if (i % 7 !in BLACK_AFTER) continue
            val n = startNote + 12 * (i / 7) + WHITE[i % 7] + 1
            val pc = n % 12
            val c = when {
                down[n] == true -> accent
                highlight && pc == root -> Color(0xFFB36B2E)
                highlight && pc in scale -> Color(0xFF2D6A8F)
                else -> Color(0xFF202020)
            }
            drawRect(c, Offset((i + 1) * kw - kw * 0.3f, 0f), Size(kw * 0.6f, bh))
        }
    }
}
