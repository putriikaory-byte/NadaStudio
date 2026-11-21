package com.nadastudio.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.KeyScaleControls(
    root: Int, onRoot: (Int) -> Unit,
    scaleType: ScaleType, onScale: (ScaleType) -> Unit,
    lock: Boolean, onLock: (Boolean) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val all = ScaleType.values()
    Text("Kunci")
    NOTE_NAMES.forEachIndexed { i, name ->
        FilterChip(selected = root == i, onClick = { onRoot(i) }, label = { Text(name) })
    }
    Text("Tangga nada")
    OutlinedButton(onClick = { onScale(all[(scaleType.ordinal + all.size - 1) % all.size]) }) { Text("‹") }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(scaleType.label + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            all.forEach {
                DropdownMenuItem(
                    text = { Text(it.label) },
                    onClick = { onScale(it); open = false }
                )
            }
        }
    }
    OutlinedButton(onClick = { onScale(all[(scaleType.ordinal + 1) % all.size]) }) { Text("›") }
    Switch(checked = lock, onCheckedChange = onLock)
    Text("Kunci nada")
}

@Composable
private fun BrowserTitle(text: String) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A3D),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun BrowserItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.sp, color = Color.White,
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) Color(0xFF3A3F4B) else Color.Transparent, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 5.dp)
    )
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xDDFF8A3D))
    ) { Text(text) }
}

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
            val ctx = LocalContext.current
            val ws = remember { WorkspaceState() }
            var pcMode by remember { mutableStateOf(true) }
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

            fun autoMelody() {
                val lo = rollOct * 12
                for (r in lo until minOf(lo + 12, ROLL_ROWS)) {
                    for (st in 0 until STEPS) {
                        roll[r * STEPS + st] = 0
                        synth.rollLen[r * STEPS + st] = 0
                    }
                }
                generateMelody(root, scaleType, rollOct).forEach { (row, step, len) ->
                    roll[row * STEPS + step] = len
                    synth.rollLen[row * STEPS + step] = len
                }
            }

            fun saveProject() {
                val ok = ProjectStore.save(
                    ctx,
                    ProjectData(
                        bpm, wave, root, scaleType, lock, seqNote,
                        BooleanArray(grid.size) { grid[it] },
                        BooleanArray(mutes.size) { mutes[it] },
                        IntArray(roll.size) { roll[it] }
                    )
                )
                Toast.makeText(ctx, if (ok) "Proyek disimpan" else "Gagal menyimpan", Toast.LENGTH_SHORT).show()
            }

            fun loadProject(quiet: Boolean) {
                val d = ProjectStore.load(ctx)
                if (d == null) {
                    if (!quiet) Toast.makeText(ctx, "Belum ada proyek tersimpan", Toast.LENGTH_SHORT).show()
                    return
                }
                bpm = d.bpm
                wave = d.wave
                root = d.root
                scaleType = d.scale
                lock = d.lock
                seqNote = d.seqNote
                for (i in grid.indices) { grid[i] = d.steps[i]; synth.steps[i] = d.steps[i] }
                for (i in mutes.indices) { mutes[i] = d.mutes[i]; synth.chMute[i] = d.mutes[i] }
                for (i in roll.indices) { roll[i] = d.roll[i]; synth.rollLen[i] = d.roll[i] }
                if (!quiet) Toast.makeText(ctx, "Proyek dibuka", Toast.LENGTH_SHORT).show()
            }

            LaunchedEffect(Unit) {
                if (ProjectStore.exists(ctx)) loadProject(true)
            }

            val transport: @Composable RowScope.() -> Unit = {
                Text("Nada Studio", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Button(onClick = { synth.startSeq() }) { Text("▶ Play") }
                OutlinedButton(onClick = { synth.stopSeq() }) { Text("■ Stop") }
                OutlinedButton(onClick = { bpm = (bpm - 1).coerceAtLeast(40) }) { Text("−") }
                Text("$bpm BPM", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { bpm = (bpm + 1).coerceAtMost(240) }) { Text("+") }
                Button(onClick = { tap() }) { Text("TAP") }
                Switch(checked = metro, onCheckedChange = { metro = it })
                Text("Metronom")
                OutlinedButton(onClick = { saveProject() }) { Text("💾 Simpan") }
                OutlinedButton(onClick = { loadProject(false) }) { Text("📂 Buka") }
                if (!pcMode) {
                    OutlinedButton(onClick = { pcMode = true }) { Text("🖥 Mode PC") }
                }
            }

            val rackContent: @Composable ColumnScope.() -> Unit = {
                ScrollRow {
                    Text("Nada channel Synth")
                    OutlinedButton(onClick = { seqNote = (seqNote - 1).coerceAtLeast(24) }) { Text("−") }
                    Text(noteLabel(seqNote), fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { seqNote = (seqNote + 1).coerceAtMost(96) }) { Text("+") }
                }
                ChannelRack(synth, grid, mutes, Modifier.fillMaxWidth().weight(1f))
            }

            val rollContent: @Composable ColumnScope.() -> Unit = {
                ScrollRow {
                    Text("Panjang")
                    listOf(1 to "1/16", 2 to "1/8", 4 to "1/4", 8 to "1/2").forEach { (v, label) ->
                        FilterChip(selected = lenSel == v, onClick = { lenSel = v }, label = { Text(label) })
                    }
                    Text("Oktaf")
                    OutlinedButton(onClick = { rollOct = (rollOct - 1).coerceAtLeast(0) }) { Text("−") }
                    Text("C${rollOct + 2}", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { rollOct = (rollOct + 1).coerceAtMost(4) }) { Text("+") }
                    Button(onClick = { autoMelody() }) { Text("🎲 Auto melodi") }
                    OutlinedButton(onClick = {
                        for (i in roll.indices) { roll[i] = 0; synth.rollLen[i] = 0 }
                    }) { Text("Hapus") }
                    KeyScaleControls(
                        root, { root = it },
                        scaleType, { scaleType = it },
                        lock, { lock = it }
                    )
                }
                PianoRoll(
                    synth = synth,
                    roll = roll,
                    octIndex = rollOct,
                    lenSel = lenSel,
                    scaleType = scaleType,
                    scale = scale,
                    root = root,
                    lock = lock,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            val keyboardContent: @Composable ColumnScope.() -> Unit = {
                ScrollRow {
                    Text("Oktaf")
                    OutlinedButton(onClick = { octave = (octave - 1).coerceAtLeast(1) }) { Text("−") }
                    Text("C$octave")
                    OutlinedButton(onClick = { octave = (octave + 1).coerceAtMost(5) }) { Text("+") }
                    Text("Volume")
                    Slider(value = volume, onValueChange = { volume = it }, modifier = Modifier.width(120.dp))
                }
                ScrollRow {
                    KeyScaleControls(
                        root, { root = it },
                        scaleType, { scaleType = it },
                        lock, { lock = it }
                    )
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

            val keysContent: @Composable ColumnScope.() -> Unit = {
                ScrollRow {
                    Text("Oktaf")
                    OutlinedButton(onClick = { octave = (octave - 1).coerceAtLeast(1) }) { Text("−") }
                    Text("C$octave")
                    OutlinedButton(onClick = { octave = (octave + 1).coerceAtMost(5) }) { Text("+") }
                    Text("Volume")
                    Slider(value = volume, onValueChange = { volume = it }, modifier = Modifier.width(160.dp))
                }
                PianoKeyboard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    startNote = 12 * (octave + 1),
                    octaves = 4,
                    scale = scale,
                    root = root,
                    lock = lock,
                    onNoteOn = { synth.noteOn(it) },
                    onNoteOff = { synth.noteOff(it) }
                )
            }

            val browserContent: @Composable ColumnScope.() -> Unit = {
                BrowserTitle("Suara")
                Wave.values().forEach { BrowserItem(it.label, wave == it) { wave = it } }
                BrowserTitle("Proyek")
                BrowserItem("💾 Simpan", false) { saveProject() }
                BrowserItem("📂 Buka", false) { loadProject(false) }
            }

            if (pcMode) {
                Box(Modifier.fillMaxSize()) {
                    PcWorkspace(
                        ws = ws,
                        modifier = Modifier.fillMaxSize(),
                        toolbar = transport,
                        browser = browserContent,
                        rack = rackContent,
                        roll = rollContent,
                        keys = keysContent
                    )
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmallButton("⛶") { ws.fit() }
                        SmallButton("📱") { pcMode = false }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScrollRow { transport() }
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
                        0 -> rackContent()
                        1 -> rollContent()
                        else -> keyboardContent()
                    }
                }
            }
        }
    }
}
