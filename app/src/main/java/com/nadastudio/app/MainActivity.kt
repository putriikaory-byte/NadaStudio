package com.nadastudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val synth = SynthEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NadaApp(synth) }
    }

    override fun onResume() { super.onResume(); synth.start() }
    override fun onPause() { synth.stop(); super.onPause() }
}

@Composable
private fun ScrollRow(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content
)

private fun noteLabel(n: Int): String = NOTE_NAMES[n % 12] + (n / 12 - 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NadaApp(synth: SynthEngine) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF8A3D),
            secondary = Color(0xFF29D3C6),
            background = Color(0xFF14142B),
            surface = Color(0xFF1F1F3D)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var bpm by remember { mutableIntStateOf(120) }
            var metro by remember { mutableStateOf(false) }
            var wave by remember { mutableStateOf(Wave.SINE) }
            var root by remember { mutableIntStateOf(0) }
            var scaleType by remember { mutableStateOf(ScaleType.MAJOR) }
            var lock by remember { mutableStateOf(false) }
            var octave by remember { mutableIntStateOf(3) }
            var volume by remember { mutableFloatStateOf(0.8f) }
            var tab by remember { mutableIntStateOf(0) }
            var seqNote by remember { mutableIntStateOf(synth.seqNote) }
            var rollOct by remember { mutableIntStateOf(2) }
            var lenSel by remember { mutableIntStateOf(1) }
            val taps = remember { mutableListOf<Long>() }
            val scale = remember(root, scaleType) { scaleSet(root, scaleType) }
            val grid = remember {
                mutableStateListOf<Boolean>().also { l ->
                    repeat(CHANNEL_NAMES.size * STEPS) { l.add(synth.steps[it]) }
                }
            }
            val mutes = remember {
                mutableStateListOf<Boolean>().also { l ->
                    repeat(CHANNEL_NAMES.size) { l.add(synth.chMute[it]) }
                }
            }
            val roll = remember {
                mutableStateListOf<Int>().also { l ->
                    repeat(ROLL_ROWS * STEPS) { l.add(synth.rollLen[it]) }
                }
            }

            SideEffect {
                synth.bpm = bpm; synth.metronomeOn = metro
                synth.wave = wave; synth.volume = volume
                synth.seqNote = seqNote
            }

            fun tap() {
                val now = System.currentTimeMillis()
                if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                taps.add(now)
                if (taps.size > 5) taps.removeAt(0)
                if (taps.size >= 2) {
                    val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
                    bpm = (60000 / avg).roundToInt().coerceIn(40, 240)
                }
            }

            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ScrollRow {
                    Text("Nada Studio", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { synth.startSeq() }) { Text("▶ Play") }
                    OutlinedButton(onClick = { synth.stopSeq() }) { Text("■ Stop") }
                    OutlinedButton(onClick = { bpm = (bpm - 1).coerceAtLeast(40) }) { Text("−") }
                    Text("$bpm BPM", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { bpm = (bpm + 1).coerceAtMost(240) }) { Text("+") }
                    Button(onClick = { tap() }) { Text("TAP") }
                    Switch(checked = metro, onCheckedChange = { metro = it })
                    Text("Metronom")
                }
                ScrollRow {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Channel Rack") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Piano Roll") })
                    FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Keyboard") })
                    Text("Suara")
                    Wave.values().forEach {
                        FilterChip(selected = wave == it, onClick = { wave = it }, label = { Text(it.label) })
                    }
                }
                when (tab) {
                    0 -> {
                        ScrollRow {
                            Text("Nada channel Synth")
                            OutlinedButton(onClick = { seqNote = (seqNote - 1).coerceAtLeast(24) }) { Text("−") }
                            Text(noteLabel(seqNote), fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { seqNote = (seqNote + 1).coerceAtMost(96) }) { Text("+") }
                        }
                        ChannelRack(synth, grid, mutes, Modifier.fillMaxWidth().weight(1f))
                    }
                    1 -> {
                        ScrollRow {
                            Text("Panjang")
                            listOf(1 to "1/16", 2 to "1/8", 4 to "1/4", 8 to "1/2").forEach { (v, label) ->
                                FilterChip(selected = lenSel == v, onClick = { lenSel = v }, label = { Text(label) })
                            }
                            Text("Oktaf")
                            OutlinedButton(onClick = { rollOct = (rollOct - 1).coerceAtLeast(0) }) { Text("−") }
                            Text("C${rollOct + 2}", fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { rollOct = (rollOct + 1).coerceAtMost(4) }) { Text("+") }
                            OutlinedButton(onClick = {
                                for (i in roll.indices) { roll[i] = 0; synth.rollLen[i] = 0 }
                            }) { Text("Hapus") }
                            Text(
                                "Kunci: ${NOTE_NAMES[root]} ${scaleType.label}" + if (lock) " (terkunci)" else ""
                            )
                        }
                        PianoRoll(
                            synth = synth,
                            roll = roll,
                            octIndex = rollOct,
                            lenSel = lenSel,
                            scale = scale,
                            root = root,
                            lock = lock,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                    else -> {
                        ScrollRow {
                            Text("Oktaf")
                            OutlinedButton(onClick = { octave = (octave - 1).coerceAtLeast(1) }) { Text("−") }
                            Text("C$octave")
                            OutlinedButton(onClick = { octave = (octave + 1).coerceAtMost(5) }) { Text("+") }
                            Text("Volume")
                            Slider(value = volume, onValueChange = { volume = it }, modifier = Modifier.width(120.dp))
                        }
                        ScrollRow {
                            Text("Kunci")
                            NOTE_NAMES.forEachIndexed { i, name ->
                                FilterChip(selected = root == i, onClick = { root = i }, label = { Text(name) })
                            }
                            Text("Tangga nada")
                            ScaleType.values().forEach {
                                FilterChip(selected = scaleType == it, onClick = { scaleType = it }, label = { Text(it.label) })
                            }
                            Switch(checked = lock, onCheckedChange = { lock = it })
                            Text("Kunci nada")
                        }
                        PianoKeyboard(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            startNote = 12 * (octave + 1),
                            octaves = 3,
                            scale = scale,
                            root = root,
                            lock = lock,
                            onNoteOn = { synth.noteOn(it) },
                            onNoteOff = { synth.noteOff(it) }
                        )
                    }
                }
            }
        }
    }
}
