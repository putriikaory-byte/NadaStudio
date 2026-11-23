package com.nadastudio.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
private fun VFader(value: Float, onChange: (Float) -> Unit, modifier: Modifier, meter: Float) {
    val cb by rememberUpdatedState(onChange)
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                cb(1f - (down.position.y / size.height).coerceIn(0f, 1f))
                do {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull()
                    if (ch != null && ch.pressed) {
                        cb(1f - (ch.position.y / size.height).coerceIn(0f, 1f))
                        ch.consume()
                    }
                } while (ev.changes.any { it.pressed })
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val trackX = w * 0.3f
        val trackW = w * 0.3f
        drawRect(Color(0xFF14172A), Offset(trackX, 0f), Size(trackW, h))
        val fillH = h * value.coerceIn(0f, 1f)
        drawRect(Color(0xFFFF8A3D), Offset(trackX, h - fillH), Size(trackW, fillH))
        drawRect(
            Color.White,
            Offset(trackX - w * 0.12f, (h - fillH - 3f).coerceAtLeast(0f)),
            Size(trackW + w * 0.24f, 6f)
        )
        val m = meter.coerceIn(0f, 1f)
        val mh = h * m
        val mc = if (m > 0.9f) Color(0xFFE05555) else if (m > 0.7f) Color(0xFFE0C040) else Color(0xFF4FD070)
        drawRect(Color(0xFF14172A), Offset(w * 0.75f, 0f), Size(w * 0.14f, h))
        drawRect(mc, Offset(w * 0.75f, h - mh), Size(w * 0.14f, mh))
    }
}

@Composable
private fun HPan(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val cb by rememberUpdatedState(onChange)
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                var v = (down.position.x / size.width).coerceIn(0f, 1f) * 2f - 1f
                cb(if (abs(v) < 0.08f) 0f else v)
                do {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull()
                    if (ch != null && ch.pressed) {
                        v = (ch.position.x / size.width).coerceIn(0f, 1f) * 2f - 1f
                        cb(if (abs(v) < 0.08f) 0f else v)
                        ch.consume()
                    }
                } while (ev.changes.any { it.pressed })
            }
        }
    ) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF14172A), Offset(0f, h * 0.4f), Size(w, h * 0.2f))
        drawRect(Color(0xFF4A4F66), Offset(w / 2f - 1f, 0f), Size(2f, h))
        val x = (value.coerceIn(-1f, 1f) + 1f) / 2f * w
        drawRect(Color(0xFF29D3C6), Offset((x - 4f).coerceIn(0f, w - 8f), 0f), Size(8f, h))
    }
}

@Composable
private fun Tag(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp)
            .background(if (on) color else Color(0xFF3A3F4B), RoundedCornerShape(3.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, color = if (on) Color.Black else Color.White)
    }
}

@Composable
private fun MixStrip(
    name: String,
    vol: Float,
    pan: Float,
    mute: Boolean,
    solo: Boolean,
    meter: Float,
    onVol: (Float) -> Unit,
    onPan: (Float) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier.background(Color(0xFF252A3D), RoundedCornerShape(4.dp)).padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(name, fontSize = 10.sp, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Tag("M", mute, Color(0xFFE05555), onMute)
            Tag("S", solo, Color(0xFFE0C040), onSolo)
        }
        HPan(pan, onPan, Modifier.fillMaxWidth().height(14.dp))
        VFader(vol, onVol, Modifier.fillMaxWidth().weight(1f), meter)
    }
}

@Composable
private fun FxFader(name: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFF2E3350), RoundedCornerShape(4.dp)).padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(name, fontSize = 10.sp, color = Color(0xFFFFB300))
        VFader(value, onChange, Modifier.fillMaxWidth().weight(1f), 0f)
        Text("${(value * 100).toInt()}%", fontSize = 9.sp, color = Color.White)
    }
}

@Composable
fun MixerPanel(
    names: List<String>,
    vols: List<Float>,
    pans: List<Float>,
    mutes: List<Boolean>,
    solos: List<Boolean>,
    meters: List<Float>,
    onVol: (Int, Float) -> Unit,
    onPan: (Int, Float) -> Unit,
    onMute: (Int) -> Unit,
    onSolo: (Int) -> Unit,
    master: Float,
    onMaster: (Float) -> Unit,
    reverb: Float,
    onReverb: (Float) -> Unit,
    dly: Float,
    onDly: (Float) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in names.indices) {
            MixStrip(
                names[i], vols[i], pans[i], mutes[i], solos[i], meters[i],
                { onVol(i, it) }, { onPan(i, it) }, { onMute(i) }, { onSolo(i) },
                Modifier.width(62.dp).fillMaxHeight()
            )
        }
        FxFader("Reverb", reverb, onReverb, Modifier.width(62.dp).fillMaxHeight())
        FxFader("Delay", dly, onDly, Modifier.width(62.dp).fillMaxHeight())
        FxFader("Master", master, onMaster, Modifier.width(62.dp).fillMaxHeight())
    }
}
