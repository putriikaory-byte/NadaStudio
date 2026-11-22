package com.nadastudio.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private fun ChoiceDropdown(prefix: String, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(prefix + items[selected] + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onPick(i); open = false }
                )
            }
        }
    }
}

@Composable
private fun WaveDropdown(current: Wave, onPick: (Wave) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(current.label + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Wave.values().forEach {
                DropdownMenuItem(
                    text = { Text(it.label) },
                    onClick = { onPick(it); open = false }
                )
            }
        }
    }
}

@Composable
private fun FileMenu(
    bpm: Int,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onExportProject: () -> Unit,
    onImportProject: () -> Unit,
    onExportWav: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("📁 Berkas ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("💾 Simpan (di dalam aplikasi)") },
                onClick = { open = false; onSave() }
            )
            DropdownMenuItem(
                text = { Text("📂 Buka (dari dalam aplikasi)") },
                onClick = { open = false; onLoad() }
            )
            DropdownMenuItem(
                text = { Text("📤 Ekspor proyek ke HP…") },
                onClick = { open = false; onExportProject() }
            )
            DropdownMenuItem(
                text = { Text("📥 Impor proyek dari HP…") },
                onClick = { open = false; onImportProject() }
            )
            listOf(2, 4, 8, 16).forEach { n ->
                DropdownMenuItem(
                    text = { Text("🎧 Ekspor audio WAV · $n× (±${n * 240 / bpm} dtk)") },
                    onClick = { open = false; onExportWav(n) }
                )
            }
        }
    }
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
            val scope = rememberCoroutineScope()
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
            var track by remember { mutableIntStateOf(0) }
            var lenSel by remember { mutableIntStateOf(1) }
            var arrStyle by remember { mutableIntStateOf(0) }
            var autoFollow by remember { mutableStateOf(false) }
            var armed by remember { mutableStateOf(false) }
            var exportBusy by remember { mutableStateOf(false) }
            var pendingLoops by remember { mutableIntStateOf(4) }
            val rollOcts = remember { mutableStateListOf(2, 0, 1) }
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
            val rolls = remember {
                List(TRACKS) { t ->
                    mutableStateListOf<Int>().also { l ->
                        repeat(ROLL_SIZE) { l.add(synth.rollLen[t * ROLL_SIZE + it]) }
                    }
                }
            }
            val trackWaves = remember {
                mutableStateListOf<Wave>().also { l ->
                    repeat(TRACKS) { l.add(synth.trackWave[it]) }
                }
            }
            val trackMutes = remember {
                mutableStateListOf<Boolean>().also { l ->
                    repeat(TRACKS) { l.add(synth.trackMute[it]) }
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

            fun autoTrack() {
                val oct = rollOcts[track]
                val lst = rolls[track]
                val lo = oct * 12
                for (r in lo until minOf(lo + 12, ROLL_ROWS)) {
                    for (st in 0 until STEPS) {
                        lst[r * STEPS + st] = 0
                        synth.rollLen[track * ROLL_SIZE + r * STEPS + st] = 0
                    }
                }
                val notes = when (track) {
                    1 -> generateBass(root, scaleType, oct)
                    2 -> generateChords(root, scaleType, oct)
                    else -> generateMelody(root, scaleType, oct)
                }
                notes.forEach { (row, step, len) ->
                    lst[row * STEPS + step] = len
                    synth.rollLen[track * ROLL_SIZE + row * STEPS + step] = len
                }
            }

            fun autoArrange(setTempo: Boolean) {
                val d = generateDrums(arrStyle)
                for (i in grid.indices) { grid[i] = d[i]; synth.steps[i] = d[i] }
                seqNote = 60 + root
                if (setTempo) bpm = DRUM_BPM[arrStyle]
                rollOcts[0] = 2
                rollOcts[1] = 0
                rollOcts[2] = 1
                for (t in 0 until TRACKS) {
                    val lst = rolls[t]
                    for (i in lst.indices) { lst[i] = 0; synth.rollLen[t * ROLL_SIZE + i] = 0 }
                    val notes = when (t) {
                        1 -> generateBass(root, scaleType, rollOcts[1])
                        2 -> generateChords(root, scaleType, rollOcts[2])
                        else -> generateMelody(root, scaleType, rollOcts[0])
                    }
                    notes.forEach { (row, step, len) ->
                        lst[row * STEPS + step] = len
                        synth.rollLen[t * ROLL_SIZE + row * STEPS + step] = len
                    }
                }
                Toast.makeText(
                    ctx,
                    "Aransemen dibuat: ${NOTE_NAMES[root]} ${scaleType.label}, gaya ${DRUM_STYLES[arrStyle]}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            fun currentProject(): ProjectData = ProjectData(
                bpm, wave, root, scaleType, lock, seqNote,
                BooleanArray(grid.size) { grid[it] },
                BooleanArray(mutes.size) { mutes[it] },
                IntArray(TRACKS * ROLL_SIZE) { rolls[it / ROLL_SIZE][it % ROLL_SIZE] },
                List(TRACKS) { trackWaves[it] },
                BooleanArray(TRACKS) { trackMutes[it] }
            )

            fun applyProject(d: ProjectData) {
                autoFollow = false
                bpm = d.bpm
                wave = d.wave
                root = d.root
                scaleType = d.scale
                lock = d.lock
                seqNote = d.seqNote
                for (i in grid.indices) { grid[i] = d.steps[i]; synth.steps[i] = d.steps[i] }
                for (i in mutes.indices) { mutes[i] = d.mutes[i]; synth.chMute[i] = d.mutes[i] }
                for (t in 0 until TRACKS) {
                    for (i in 0 until ROLL_SIZE) {
                        val v = d.roll[t * ROLL_SIZE + i]
                        rolls[t][i] = v
                        synth.rollLen[t * ROLL_SIZE + i] = v
                    }
                    trackWaves[t] = d.trackWaves[t]
                    synth.trackWave[t] = d.trackWaves[t]
                    trackMutes[t] = d.trackMutes[t]
                    synth.trackMute[t] = d.trackMutes[t]
                }
            }

            fun saveProject() {
                val ok = ProjectStore.save(ctx, currentProject())
                Toast.makeText(ctx, if (ok) "Proyek disimpan" else "Gagal menyimpan", Toast.LENGTH_SHORT).show()
            }

            fun loadProject(quiet: Boolean) {
                val d = ProjectStore.load(ctx)
                if (d == null) {
                    if (!quiet) Toast.makeText(ctx, "Belum ada proyek tersimpan", Toast.LENGTH_SHORT).show()
                    return
                }
                applyProject(d)
                if (!quiet) Toast.makeText(ctx, "Proyek dibuka", Toast.LENGTH_SHORT).show()
            }

            val exportProjectL = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri: Uri? ->
                if (uri != null) {
                    val ok = try {
                        ctx.contentResolver.openOutputStream(uri)?.use {
                            it.write(ProjectStore.toText(currentProject()).toByteArray())
                        }
                        true
                    } catch (e: Throwable) {
                        false
                    }
                    Toast.makeText(
                        ctx,
                        if (ok) "Proyek diekspor ke HP" else "Gagal mengekspor proyek",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            val importProjectL = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    val text = try {
                        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } catch (e: Throwable) {
                        null
                    }
                    val d = if (text != null) ProjectStore.fromText(text) else null
                    if (d == null) {
                        Toast.makeText(ctx, "File proyek tidak valid", Toast.LENGTH_SHORT).show()
                    } else {
                        applyProject(d)
                        Toast.makeText(ctx, "Proyek diimpor", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val exportWavL = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("audio/wav")
            ) { uri: Uri? ->
                if (uri != null) {
                    val loops = pendingLoops
                    val sec = loops * 240 / bpm
                    scope.launch {
                        exportBusy = true
                        val ok = withContext(Dispatchers.Default) {
                            try {
                                synth.stop()
                                val samples = synth.renderOffline(loops)
                                ctx.contentResolver.openOutputStream(uri)?.use { WavExport.write(it, samples) }
                                true
                            } catch (e: Throwable) {
                                false
                            }
                        }
                        synth.start()
                        exportBusy = false
                        Toast.makeText(
                            ctx,
                            if (ok) "Audio disimpan (±$sec detik)" else "Gagal mengekspor audio",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (ProjectStore.exists(ctx)) loadProject(true)
            }

            LaunchedEffect(root, scaleType) {
                if (armed && autoFollow) autoArrange(false)
                armed = true
            }

            val transport: @Composable RowScope.() -> Unit = {
                Text("Nada Studio", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Button(onClick = { synth.startSeq() }) { Text("▶ Play") }
                OutlinedButton(onClick = { synth.stopSeq() }) { Text("■ Stop") }
                OutlinedButton(onClick = { bpm = (bpm - 1).coerceAtLeast(40) }) { Text("−") }
                Text("$bpm BPM", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { bpm = (bpm + 1).coerceAtMost(240) }) { Text("+") }
                Button(onClick = { tap() }) { Text("TAP") }
                ChoiceDropdown("Kunci ", NOTE_NAMES, root) { root = it }
                ChoiceDropdown("", ScaleType.values().map { it.label }, scaleType.ordinal) {
                    scaleType = ScaleType.values()[it]
                }
                ChoiceDropdown("Gaya ", DRUM_STYLES, arrStyle) { arrStyle = it }
                Button(onClick = { autoArrange(true) }) { Text("🎼 Auto aransemen") }
                Switch(checked = autoFollow, onCheckedChange = { autoFollow = it })
                Text("Ikuti kunci")
                Switch(checked = metro, onCheckedChange = { metro = it })
                Text("Metronom")
                FileMenu(
                    bpm = bpm,
                    onSave = { saveProject() },
                    onLoad = { loadProject(false) },
                    onExportProject = { exportProjectL.launch("aransemen.nada") },
                    onImportProject = { importProjectL.launch(arrayOf("*/*")) },
                    onExportWav = { n ->
                        pendingLoops = n
                        exportWavL.launch("aransemen.wav")
                    }
                )
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
                    Text("Track")
                    TRACK_NAMES.forEachIndexed { i, n ->
                        FilterChip(selected = track == i, onClick = { track = i }, label = { Text(n) })
                    }
                    Text("Instrumen")
                    WaveDropdown(trackWaves[track]) { w ->
                        trackWaves[track] = w
                        synth.trackWave[track] = w
                    }
                    Switch(
                        checked = !trackMutes[track],
                        onCheckedChange = { on ->
                            trackMutes[track] = !on
                            synth.trackMute[track] = !on
                        }
                    )
                    Text("Bunyi")
                }
                ScrollRow {
                    Text("Panjang")
                    listOf(1 to "1/16", 2 to "1/8", 4 to "1/4", 8 to "1/2").forEach { (v, label) ->
                        FilterChip(selected = lenSel == v, onClick = { lenSel = v }, label = { Text(label) })
                    }
                    Text("Oktaf")
                    OutlinedButton(onClick = { rollOcts[track] = (rollOcts[track] - 1).coerceAtLeast(0) }) { Text("−") }
                    Text("C${rollOcts[track] + 2}", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { rollOcts[track] = (rollOcts[track] + 1).coerceAtMost(4) }) { Text("+") }
                    val autoLabel = when (track) { 1 -> "bass"; 2 -> "akor"; else -> "melodi" }
                    Button(onClick = { autoTrack() }) { Text("🎲 Auto $autoLabel") }
                    OutlinedButton(onClick = {
                        val lst = rolls[track]
                        for (i in lst.indices) { lst[i] = 0; synth.rollLen[track * ROLL_SIZE + i] = 0 }
                    }) { Text("Hapus") }
                    KeyScaleControls(
                        root, { root = it },
                        scaleType, { scaleType = it },
                        lock, { lock = it }
                    )
                }
                PianoRoll(
                    synth = synth,
                    track = track,
                    roll = rolls[track],
                    octIndex = rollOcts[track],
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
                BrowserTitle("Suara keyboard")
                Wave.values().forEach { BrowserItem(it.label, wave == it) { wave = it } }
                BrowserTitle("Proyek")
                BrowserItem("💾 Simpan", false) { saveProject() }
                BrowserItem("📂 Buka", false) { loadProject(false) }
                BrowserItem("📤 Ekspor ke HP", false) { exportProjectL.launch("aransemen.nada") }
                BrowserItem("📥 Impor dari HP", false) { importProjectL.launch(arrayOf("*/*")) }
                BrowserItem("🎧 Ekspor WAV (8×)", false) {
                    pendingLoops = 8
                    exportWavL.launch("aransemen.wav")
                }
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

            if (exportBusy) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text("Mengekspor audio…") },
                    text = { Text("Mohon tunggu sebentar dan jangan tutup aplikasi.") }
                )
            }
        }
    }
}
