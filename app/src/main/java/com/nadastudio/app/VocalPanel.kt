package com.nadastudio.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
private fun VRow(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content
)

@Composable
private fun Waveform(clip: ShortArray?, version: Int, modifier: Modifier) {
    val cols = 400
    val peaks = remember(version) {
        val out = FloatArray(cols)
        if (clip != null && clip.isNotEmpty()) {
            for (c in 0 until cols) {
                val i0 = (c.toLong() * clip.size / cols).toInt()
                val i1 = ((c + 1).toLong() * clip.size / cols).toInt().coerceAtMost(clip.size)
                val stp = maxOf(1, (i1 - i0) / 64)
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
    Canvas(modifier.background(Color(0xFF14172A), RoundedCornerShape(4.dp))) {
        val w = size.width
        val h = size.height
        val cw = w / cols
        for (c in 0 until cols) {
            val ph = peaks[c] * h
            drawRect(
                Color(0xFF29D3C6),
                Offset(c * cw, (h - ph) / 2f),
                Size(maxOf(cw, 1f), maxOf(ph, 1f))
            )
        }
    }
}

@Composable
fun ColumnScope.VocalPanel(
    clip: ShortArray?,
    version: Int,
    recording: Boolean,
    recSeconds: Int,
    monitor: Boolean,
    onMonitor: (Boolean) -> Unit,
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onUpload: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    loop: Boolean,
    onLoop: (Boolean) -> Unit,
    shift: Int,
    onShift: (Int) -> Unit,
    analysis: VocalAnalysis?,
    onAnalyze: () -> Unit,
    onApplyKey: () -> Unit,
    bars: Int,
    bar: Int,
    onBar: (Int) -> Unit,
    snap: Boolean,
    onSnap: (Boolean) -> Unit,
    onImport: () -> Unit
) {
    VRow {
        if (!recording) {
            Button(onClick = onRecord) { Text("⏺ Rekam") }
        } else {
            Button(
                onClick = onStopRecord,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE05555))
            ) { Text("⏹ Stop rekam · $recSeconds dtk") }
        }
        Switch(checked = monitor, onCheckedChange = onMonitor)
        Text("Rekam sambil lagu (pakai headset)")
        OutlinedButton(onClick = onUpload) { Text("📥 Upload suara") }
    }
    VRow {
        Button(onClick = onPlay, enabled = clip != null) { Text("▶ Putar") }
        OutlinedButton(onClick = onStop) { Text("■") }
        OutlinedButton(onClick = onClear, enabled = clip != null) { Text("🗑 Hapus") }
        Switch(checked = loop, onCheckedChange = onLoop)
        Text("Ulang tiap loop")
        Text("Geser")
        OutlinedButton(onClick = { onShift((shift - 10).coerceAtLeast(-500)) }) { Text("−") }
        Text("$shift ms", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onShift((shift + 10).coerceAtMost(500)) }) { Text("+") }
    }
    Waveform(clip, version, Modifier.fillMaxWidth().height(56.dp))
    Text(
        if (clip == null) "Belum ada suara. Rekam atau upload." else "Durasi ${(clip.size / 4410) / 10.0} detik",
        fontSize = 11.sp
    )
    VRow {
        Button(onClick = onAnalyze, enabled = clip != null) { Text("🎯 Deteksi nada dan kunci") }
    }
    if (analysis != null) {
        if (analysis.notes.isEmpty()) {
            Text(
                "Tidak ada nada terdeteksi. Coba rekaman yang lebih jelas dan tanpa musik latar.",
                fontSize = 11.sp,
                color = Color(0xFFE0C040)
            )
        } else {
            Text(
                "Terdeteksi ${analysis.notes.size} nada · kunci ${NOTE_NAMES[analysis.keyRoot]} " +
                    "${analysis.keyScale.label} (kecocokan ${(analysis.fit * 100).toInt()}%)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            VRow {
                Button(onClick = onApplyKey) { Text("✔ Pakai kunci ini") }
                Text("Bar")
                OutlinedButton(onClick = { onBar((bar - 1).coerceAtLeast(0)) }) { Text("−") }
                Text("${bar + 1}/$bars", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { onBar((bar + 1).coerceAtMost(bars - 1)) }) { Text("+") }
                Switch(checked = snap, onCheckedChange = onSnap)
                Text("Snap ke tangga nada")
                Button(onClick = onImport) { Text("🎹 Masukkan ke Piano Roll") }
            }
        }
    }
}
