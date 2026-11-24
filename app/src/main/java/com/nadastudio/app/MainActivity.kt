package com.nadastudio.app

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
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
            val recorder = remember { VoiceRecorder() }
            var pcMode by remember { mutableStateOf(true) }
            var bpm by remember { mutableIntStateOf(120) }
            var metro by remember { mutableStateOf(false) }
            var wave by remember { mutableStateOf(Wave.SINE) }
            var root by remember { mutableIntStateOf(0) }
            var scaleType by remember { mutableStateOf(ScaleType.MAJOR) }
            var lock by remember { mutableStateOf(false) }
            var octave by remember { mutableIntStateOf(3) }
            var volume by remember { mutableFloatStateOf(0.8f) }
            var reverb by remember { mutableFloatStateOf(synth.reverbMix) }
            var delayFx by remember { mutableFloatStateOf(synth.delayMix) }
            var liveMute by remember { mutableStateOf(false) }
            var vocalMute by remember { mutableStateOf(false) }
            var tab by remember { mutableIntStateOf(0) }
            var seqNote by remember { mutableIntStateOf(synth.seqNote) }
            var track by remember { mutableIntStateOf(0) }
            var lenSel by remember { mutableIntStateOf(1) }
            var genreIdx by remember { mutableIntStateOf(0) }
            var followGenreScale by remember { mutableStateOf(true) }
            var genreInstr by remember { mutableStateOf(true) }
            var incDrums by remember { mutableStateOf(true) }
            var incBass by remember { mutableStateOf(true) }
            var incChords by remember { mutableStateOf(true) }
            var incMelody by remember { mutableStateOf(true) }
            var autoFollow by remember { mutableStateOf(false) }
            var armed by remember { mutableStateOf(false) }
            var skipEffect by remember { mutableStateOf(false) }
            var busyMsg by remember { mutableStateOf<String?>(null) }
            var pendingLoops by remember { mutableIntStateOf(4) }
            var autoSub by remember { mutableIntStateOf(0) }
            var clipVersion by remember { mutableIntStateOf(0) }
            var recording by remember { mutableStateOf(false) }
            var recSeconds by remember { mutableIntStateOf(0) }
            var monitorRec by remember { mutableStateOf(true) }
            var vShift by remember { mutableIntStateOf(0) }
            var vLoop by remember { mutableStateOf(false) }
            var analysis by remember { mutableStateOf<VocalAnalysis?>(null) }
            var importBar by remember { mutableIntStateOf(0) }
            var snapImport by remember { mutableStateOf(true) }
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
            val mixVols = remember {
                mutableStateListOf<Float>().also { l ->
                    repeat(MIX_CH) { l.add(synth.mixVol[it]) }
                }
            }
            val mixPans = remember {
                mutableStateListOf<Float>().also { l ->
                    repeat(MIX_CH) { l.add(synth.mixPan[it]) }
                }
            }
            val mixSolos = remember {
                mutableStateListOf<Boolean>().also { l ->
                    repeat(MIX_CH) { l.add(false) }
                }
            }
            val meters = remember {
                mutableStateListOf<Float>().also { l ->
                    repeat(MIX_CH) { l.add(0f) }
                }
            }

            SideEffect {
                synth.bpm = bpm; synth.metronomeOn = metro
                synth.wave = wave; synth.volume = volume
                synth.seqNote = seqNote
                synth.reverbMix = reverb; synth.delayMix = delayFx
                synth.vocalShiftMs = vShift; synth.vocalLoop = vLoop
            }

            fun toast(msg: String) {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
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

            fun fillDrums(g: Genre) {
                val d = generateDrumsFor(g)
                for (i in grid.indices) { grid[i] = d[i]; synth.steps[i] = d[i] }
                seqNote = 60 + root
            }

            fun fillTrack(t: Int, g: Genre, st: ScaleType, clearAll: Boolean) {
                val oct = rollOcts[t]
                val lst = rolls[t]
                if (clearAll) {
                    for (i in lst.indices) { lst[i] = 0; synth.rollLen[t * ROLL_SIZE + i] = 0 }
                } else {
                    val lo = oct * 12
                    for (r in lo until minOf(lo + 12, ROLL_ROWS)) {
                        for (s in 0 until STEPS) {
                            lst[r * STEPS + s] = 0
                            synth.rollLen[t * ROLL_SIZE + r * STEPS + s] = 0
                        }
                    }
                }
                val notes = when (t) {
                    1 -> generateBassFor(root, st, oct, g)
                    2 -> generateChordsFor(root, st, oct, g)
                    else -> generateMelodyFor(root, st, oct, g)
                }
                notes.forEach { (row, step, len) ->
                    lst[row * STEPS + step] = len
                    synth.rollLen[t * ROLL_SIZE + row * STEPS + step] = len
                }
            }

            fun autoTrack() {
                fillTrack(track, GENRES[genreIdx], scaleType, false)
            }

            fun autoArrange(setTempo: Boolean) {
                val g = GENRES[genreIdx]
                var st = scaleType
                if (followGenreScale && scaleType != g.scale) {
                    st = g.scale
                    skipEffect = true
                    scaleType = st
                }
                if (incDrums) fillDrums(g)
                if (setTempo) bpm = g.bpm
                if (genreInstr) {
                    val w = listOf(g.melodyWave, g.bassWave, g.chordWave)
                    for (t in 0 until TRACKS) {
                        trackWaves[t] = w[t]
                        synth.trackWave[t] = w[t]
                    }
                }
                rollOcts[0] = 2
                rollOcts[1] = 0
                rollOcts[2] = 1
                val inc = listOf(incMelody, incBass, incChords)
                for (t in 0 until TRACKS) {
                    if (inc[t]) fillTrack(t, g, st, true)
                }
                toast("Lagu dibuat: ${g.name}, ${NOTE_NAMES[root]} ${st.label}, ${if (setTempo) g.bpm else bpm} BPM")
            }

            fun currentProject(): ProjectData = ProjectData(
                bpm, wave, root, scaleType, lock, seqNote,
                BooleanArray(grid.size) { grid[it] },
                BooleanArray(mutes.size) { mutes[it] },
                IntArray(TRACKS * ROLL_SIZE) { rolls[it / ROLL_SIZE][it % ROLL_SIZE] },
                List(TRACKS) { trackWaves[it] },
                BooleanArray(TRACKS) { trackMutes[it] },
                FloatArray(MIX_CH) { mixVols[it] },
                FloatArray(MIX_CH) { mixPans[it] },
                reverb, delayFx, volume, vShift, vLoop
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
                for (i in 0 until MIX_CH) {
                    mixVols[i] = d.mixVol[i]
                    synth.mixVol[i] = d.mixVol[i]
                    mixPans[i] = d.mixPan[i]
                    synth.mixPan[i] = d.mixPan[i]
                }
                reverb = d.reverb
                delayFx = d.delay
                volume = d.master
                vShift = d.vShift
                vLoop = d.vLoop
            }

            fun saveProject() {
                val ok = ProjectStore.save(ctx, currentProject())
                toast(if (ok) "Proyek disimpan" else "Gagal menyimpan")
            }

            fun loadProject(quiet: Boolean) {
                val d = ProjectStore.load(ctx)
                if (d == null) {
                    if (!quiet) toast("Belum ada proyek tersimpan")
                    return
                }
                applyProject(d)
                if (!quiet) toast("Proyek dibuka")
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
                    toast(if (ok) "Proyek diekspor ke HP" else "Gagal mengekspor proyek")
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
                        toast("File proyek tidak valid")
                    } else {
                        applyProject(d)
                        toast("Proyek diimpor")
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
                        busyMsg = "Mengekspor audio…"
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
                        busyMsg = null
                        Toast.makeText(
                            ctx,
                            if (ok) "Audio disimpan (±$sec detik)" else "Gagal mengekspor audio",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            fun setClip(c: ShortArray?) {
                synth.vocal = c
                clipVersion++
                analysis = null
                importBar = 0
                scope.launch(Dispatchers.IO) { VocalStore.save(ctx, c) }
            }

            fun startRecording() {
                if (!recorder.start()) {
                    toast("Mikrofon tidak bisa dibuka")
                    return
                }
                recording = true
                if (monitorRec) {
                    synth.vocalSuppress = true
                    synth.startSeq()
                }
            }

            fun stopRecording() {
                val data = recorder.stop()
                recording = false
                synth.stopSeq()
                synth.vocalSuppress = false
                if (data != null && data.size > CLIP_RATE / 2) {
                    setClip(data)
                    if (monitorRec) vShift = -120
                    toast("Rekaman disimpan (${data.size / CLIP_RATE} detik)")
                } else {
                    toast("Rekaman terlalu pendek")
                }
            }

            val micPermL = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { ok: Boolean ->
                if (ok) startRecording() else toast("Izin mikrofon ditolak")
            }

            fun onRecordTap() {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    startRecording()
                } else {
                    micPermL.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            val uploadL = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        busyMsg = "Membaca file audio…"
                        val c = withContext(Dispatchers.Default) {
                            try {
                                AudioDecoder.decode(ctx, uri)
                            } catch (e: Throwable) {
                                null
                            }
                        }
                        busyMsg = null
                        if (c == null || c.size < CLIP_RATE / 2) {
                            toast("File audio tidak bisa dibaca")
                        } else {
                            setClip(c)
                            toast("Suara dimuat (${c.size / CLIP_RATE} detik)")
                        }
                    }
                }
            }

            fun analyzeClip() {
                val c = synth.vocal ?: return
                scope.launch {
                    busyMsg = "Menganalisis nada vokal…"
                    val a = withContext(Dispatchers.Default) {
                        try {
                            PitchDetect.analyze(c)
                        } catch (e: Throwable) {
                            null
                        }
                    }
                    busyMsg = null
                    analysis = a
                    importBar = 0
                    if (a == null || a.notes.isEmpty()) {
                        toast("Tidak ada nada terdeteksi. Coba rekaman yang lebih jelas.")
                    }
                }
            }

            fun applyKey() {
                val a = analysis ?: return
                root = a.keyRoot
                scaleType = a.keyScale
                followGenreScale = false
                toast("Kunci diatur: ${NOTE_NAMES[a.keyRoot]} ${a.keyScale.label}")
            }

            fun importToRoll() {
                val a = analysis ?: return
                val notes = PitchDetect.toRoll(a, bpm, importBar, if (snapImport) scale else null)
                if (notes.isEmpty()) {
                    toast("Tidak ada nada di bar ini")
                    return
                }
                val lst = rolls[0]
                for (i in lst.indices) { lst[i] = 0; synth.rollLen[i] = 0 }
                notes.forEach { (row, step, len) ->
                    lst[row * STEPS + step] = len
                    synth.rollLen[row * STEPS + step] = len
                }
                val sorted = notes.map { it.first }.sorted()
                rollOcts[0] = (sorted[sorted.size / 2] / 12).coerceIn(0, 4)
                track = 0
                toast("${notes.size} nada dimasukkan ke track Melodi")
            }

            LaunchedEffect(Unit) {
                if (ProjectStore.exists(ctx)) loadProject(true)
            }

            LaunchedEffect(Unit) {
                val c = withContext(Dispatchers.IO) { VocalStore.load(ctx) }
                if (c != null) {
                    synth.vocal = c
                    clipVersion++
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    for (i in 0 until MIX_CH) meters[i] = synth.peak[i]
                    delay(60)
                }
            }

            LaunchedEffect(recording) {
                while (recording) {
                    recSeconds = recorder.seconds
                    if (recSeconds >= CLIP_MAX_SEC) {
                        stopRecording()
                        break
                    }
                    delay(300)
                }
            }

            LaunchedEffect(root, scaleType) {
                if (skipEffect) {
                    skipEffect = false
                } else if (armed && autoFollow) {
                    autoArrange(false)
                }
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
                    followGenreScale = false
                }
                ChoiceDropdown("Genre ", GENRES.map { it.name }, genreIdx) { genreIdx = it }
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

            val autoContent: @Composable ColumnScope.() -> Unit = {
                val g = GENRES[genreIdx]
                ScrollRow {
                    Text("Genre")
                    ChoiceDropdown("", GENRES.map { it.name }, genreIdx) { genreIdx = it }
                    Text("Kunci")
                    ChoiceDropdown("", NOTE_NAMES, root) { root = it }
                    Text("Tangga nada")
                    ChoiceDropdown("", ScaleType.values().map { it.label }, scaleType.ordinal) {
                        scaleType = ScaleType.values()[it]
                        followGenreScale = false
                    }
                    Switch(checked = followGenreScale, onCheckedChange = { followGenreScale = it })
                    Text("Ikut genre")
                }
                ScrollRow {
                    Text("Elemen")
                    Switch(checked = incDrums, onCheckedChange = { incDrums = it })
                    Text("Drum")
                    Switch(checked = incBass, onCheckedChange = { incBass = it })
                    Text("Bass")
                    Switch(checked = incChords, onCheckedChange = { incChords = it })
                    Text("Akor")
                    Switch(checked = incMelody, onCheckedChange = { incMelody = it })
                    Text("Melodi")
                    Switch(checked = genreInstr, onCheckedChange = { genreInstr = it })
                    Text("Instrumen ikut genre")
                }
                ScrollRow {
                    Button(onClick = { autoArrange(true) }) { Text("🎼 Buat lagu") }
                    OutlinedButton(onClick = { fillDrums(g) }) { Text("🥁 Ulang drum") }
                    OutlinedButton(onClick = { fillTrack(1, g, scaleType, false) }) { Text("🎸 Ulang bass") }
                    OutlinedButton(onClick = { fillTrack(2, g, scaleType, false) }) { Text("🎹 Ulang akor") }
                    OutlinedButton(onClick = { fillTrack(0, g, scaleType, false) }) { Text("🎵 Ulang melodi") }
                    Switch(checked = autoFollow, onCheckedChange = { autoFollow = it })
                    Text("Ikuti kunci")
                }
                Text(
                    "${g.name} · ${NOTE_NAMES[root]} ${scaleType.label} · $bpm BPM",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "Melodi: ${trackWaves[0].label} · Bass: ${trackWaves[1].label} · Akor: ${trackWaves[2].label}",
                    fontSize = 12.sp
                )
                Text(
                    "🎤 Vokal: buka sub-tab Vokal di atas untuk rekam atau upload suara",
                    fontSize = 11.sp,
                    color = Color(0xFF8A90A8)
                )
            }

            val autoPanelContent: @Composable ColumnScope.() -> Unit = {
                ScrollRow {
                    FilterChip(selected = autoSub == 0, onClick = { autoSub = 0 }, label = { Text("Auto Lagu") })
                    FilterChip(selected = autoSub == 1, onClick = { autoSub = 1 }, label = { Text("🎤 Vokal") })
                }
                if (autoSub == 0) {
                    autoContent()
                } else {
                    val bars = analysis?.let {
                        ceil(it.seconds / (240.0 / bpm)).toInt().coerceAtLeast(1)
                    } ?: 1
                    VocalPanel(
                        clip = synth.vocal,
                        version = clipVersion,
                        recording = recording,
                        recSeconds = recSeconds,
                        monitor = monitorRec,
                        onMonitor = { monitorRec = it },
                        onRecord = { onRecordTap() },
                        onStopRecord = { stopRecording() },
                        onUpload = { uploadL.launch(arrayOf("audio/*", "*/*")) },
                        onPlay = { synth.playClip() },
                        onStop = { synth.stopClip() },
                        onClear = {
                            synth.stopClip()
                            setClip(null)
                        },
                        loop = vLoop,
                        onLoop = { vLoop = it },
                        shift = vShift,
                        onShift = { vShift = it },
                        analysis = analysis,
                        onAnalyze = { analyzeClip() },
                        onApplyKey = { applyKey() },
                        bars = bars,
                        bar = importBar,
                        onBar = { importBar = it },
                        snap = snapImport,
                        onSnap = { snapImport = it },
                        onImport = { importToRoll() }
                    )
                }
            }

            val mixerContent: @Composable ColumnScope.() -> Unit = {
                val mutedList = List(MIX_CH) { i ->
                    when {
                        i < CHANNEL_NAMES.size -> mutes[i]
                        i < CHANNEL_NAMES.size + TRACKS -> trackMutes[i - CHANNEL_NAMES.size]
                        i == LIVE_STRIP -> liveMute
                        else -> vocalMute
                    }
                }
                MixerPanel(
                    names = MIX_NAMES,
                    vols = mixVols,
                    pans = mixPans,
                    mutes = mutedList,
                    solos = mixSolos,
                    meters = meters,
                    onVol = { i, v -> mixVols[i] = v; synth.mixVol[i] = v },
                    onPan = { i, p -> mixPans[i] = p; synth.mixPan[i] = p },
                    onMute = { i ->
                        if (i < CHANNEL_NAMES.size) {
                            mutes[i] = !mutes[i]
                            synth.chMute[i] = mutes[i]
                        } else if (i < CHANNEL_NAMES.size + TRACKS) {
                            val t = i - CHANNEL_NAMES.size
                            trackMutes[t] = !trackMutes[t]
                            synth.trackMute[t] = trackMutes[t]
                        } else if (i == LIVE_STRIP) {
                            liveMute = !liveMute
                            synth.liveMute = liveMute
                        } else {
                            vocalMute = !vocalMute
                            synth.vocalMute = vocalMute
                        }
                    },
                    onSolo = { i -> mixSolos[i] = !mixSolos[i]; synth.mixSolo[i] = mixSolos[i] },
                    master = volume,
                    onMaster = { volume = it },
                    reverb = reverb,
                    onReverb = { reverb = it },
                    dly = delayFx,
                    onDly = { delayFx = it },
                    modifier = Modifier.fillMaxWidth().weight(1f)
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
                        keys = keysContent,
                        auto = autoPanelContent,
                        mixer = mixerContent
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
                        FilterChip(selected = tab == 3, onClick = { tab = 3 }, label = { Text("Auto Lagu / Vokal") })
                        FilterChip(selected = tab == 4, onClick = { tab = 4 }, label = { Text("Mixer") })
                        Text("Suara")
                        Wave.values().forEach {
                            FilterChip(selected = wave == it, onClick = { wave = it }, label = { Text(it.label) })
                        }
                    }
                    when (tab) {
                        0 -> rackContent()
                        1 -> rollContent()
                        2 -> keyboardContent()
                        3 -> autoPanelContent()
                        else -> mixerContent()
                    }
                }
            }

            val busy = busyMsg
            if (busy != null) {
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    title = { Text(busy) },
                    text = { Text("Mohon tunggu sebentar dan jangan tutup aplikasi.") }
                )
            }
        }
    }
}
