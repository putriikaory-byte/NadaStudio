package com.nadastudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ChannelRack(
    synth: SynthEngine,
    grid: SnapshotStateList<Boolean>,
    mutes: SnapshotStateList<Boolean>,
    modifier: Modifier
) {
    var current by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            current = if (synth.playing) synth.currentStep else -1
            delay(20)
        }
    }
    val shape = RoundedCornerShape(3.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CHANNEL_NAMES.forEachIndexed { ch, name ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape)
                        .background(if (mutes[ch]) Color(0xFF444444) else Color(0xFF7CFF4F))
                        .clickable { mutes[ch] = !mutes[ch]; synth.chMute[ch] = mutes[ch] }
                )
                Box(
                    Modifier.width(80.dp).fillMaxHeight().clip(shape)
                        .background(Color(0xFF3A3F4B))
                        .clickable { synth.preview(ch) },
                    contentAlignment = Alignment.Center
                ) { Text(name, color = Color.White, fontSize = 12.sp) }
                for (s in 0 until STEPS) {
                    val idx = ch * STEPS + s
                    val on = grid[idx]
                    val base = if ((s / 4) % 2 == 0) Color(0xFF5A6070) else Color(0xFF454A58)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(shape)
                            .background(if (on) Color(0xFFFF7A2F) else base)
                            .then(if (s == current) Modifier.border(2.dp, Color.White, shape) else Modifier)
                            .clickable { grid[idx] = !on; synth.steps[idx] = !on }
                    )
                }
            }
        }
    }
}
