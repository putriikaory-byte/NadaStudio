package com.nadastudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val VW = 1760f
private const val VH = 900f

private class PanelRect(val x: Float, val y: Float, val w: Float, val h: Float)

private val R_BROWSER = PanelRect(0f, 70f, 220f, 830f)
private val R_RACK = PanelRect(226f, 70f, 640f, 250f)
private val R_MIXER = PanelRect(872f, 70f, 888f, 250f)
private val R_ROLL = PanelRect(226f, 326f, 800f, 390f)
private val R_AUTO = PanelRect(1032f, 326f, 728f, 390f)
private val R_KEYS = PanelRect(226f, 722f, 1534f, 178f)

/** Status zoom dan geser layar kerja. zoom = 0 berarti pas layar. */
class WorkspaceState {
    var zoom by mutableFloatStateOf(0f)
    var pan by mutableStateOf(Offset.Zero)
    fun fit() { zoom = 0f; pan = Offset.Zero }
}

@Composable
private fun PanelFrame(
    title: String,
    r: PanelRect,
    onZoom: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier.offset(r.x.dp, r.y.dp).size(r.w.dp, r.h.dp)
            .background(Color(0xFF1E2130), shape)
            .border(1.dp, Color(0xFF4A4F66), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().height(22.dp).background(Color(0xFF3A3F4B)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title, fontSize = 11.sp, color = Color.White,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            Text(
                "⤢", fontSize = 13.sp, color = Color(0xFFFF8A3D),
                modifier = Modifier.clickable { onZoom() }.padding(horizontal = 10.dp)
            )
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(4.dp), content = content)
    }
}

@Composable
fun PcWorkspace(
    ws: WorkspaceState,
    modifier: Modifier,
    toolbar: @Composable RowScope.() -> Unit,
    browser: @Composable ColumnScope.() -> Unit,
    rack: @Composable ColumnScope.() -> Unit,
    roll: @Composable ColumnScope.() -> Unit,
    keys: @Composable ColumnScope.() -> Unit,
    auto: @Composable ColumnScope.() -> Unit,
    mixer: @Composable ColumnScope.() -> Unit
) {
    val base = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val d = base.density
        val fit = minOf(wPx / (VW * d), hPx / (VH * d)).coerceAtLeast(0.1f)
        val z = if (ws.zoom <= 0f) fit else ws.zoom.coerceIn(fit, 4f)
        val pan = if (ws.zoom <= 0f) Offset.Zero else ws.pan

        fun clampPan(o: Offset, zz: Float): Offset {
            val minX = minOf(0f, wPx - VW * d * zz)
            val minY = minOf(0f, hPx - VH * d * zz)
            return Offset(o.x.coerceIn(minX, 0f), o.y.coerceIn(minY, 0f))
        }

        fun zoomTo(r: PanelRect) {
            val zz = minOf(wPx / (r.w * d), hPx / (r.h * d)).coerceIn(fit, 4f)
            ws.zoom = zz
            ws.pan = clampPan(Offset(-r.x * d * zz, -r.y * d * zz), zz)
        }

        Box(
            Modifier.fillMaxSize().pointerInput(fit, wPx, hPx) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val cur = if (ws.zoom <= 0f) fit else ws.zoom
                    val nz = (cur * zoomChange).coerceIn(fit, 4f)
                    val ratio = nz / cur
                    val curPan = if (ws.zoom <= 0f) Offset.Zero else ws.pan
                    ws.zoom = nz
                    ws.pan = clampPan((curPan - centroid) * ratio + centroid + panChange, nz)
                }
            }
        ) {
            CompositionLocalProvider(LocalDensity provides Density(d * z, base.fontScale)) {
                Box(
                    Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset { IntOffset(pan.x.roundToInt(), pan.y.roundToInt()) }
                        .size(VW.dp, VH.dp)
                        .background(Color(0xFF14142B))
                ) {
                    Row(
                        Modifier.size(VW.dp, 64.dp)
                            .background(Color(0xFF2A2E3F), RoundedCornerShape(4.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = toolbar
                    )
                    PanelFrame("Browser", R_BROWSER, { zoomTo(R_BROWSER) }) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            browser()
                        }
                    }
                    PanelFrame("Channel Rack", R_RACK, { zoomTo(R_RACK) }) { rack() }
                    PanelFrame("Mixer", R_MIXER, { zoomTo(R_MIXER) }) { mixer() }
                    PanelFrame("Piano Roll", R_ROLL, { zoomTo(R_ROLL) }) { roll() }
                    PanelFrame("Auto Lagu", R_AUTO, { zoomTo(R_AUTO) }) { auto() }
                    PanelFrame("Keyboard", R_KEYS, { zoomTo(R_KEYS) }) { keys() }
                }
            }
        }
    }
}
