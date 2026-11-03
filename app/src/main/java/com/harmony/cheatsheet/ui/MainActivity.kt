package com.harmony.cheatsheet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harmony.cheatsheet.audio.Notes
import com.harmony.cheatsheet.audio.Synth
import com.harmony.cheatsheet.data.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ScaleChartScreen(HarmonyDb.get(this)) } }
    }
    override fun onStop() { Synth.stop(); super.onStop() }
}

private val enharmonicRoot = mapOf("E#" to "F", "Fb" to "E", "Cb" to "B", "B#" to "C")
private fun rootOf(chord: String) = Regex("^[A-G][#b]?").find(chord)!!.value.let { enharmonicRoot[it] ?: it }
private val groups = listOf(            // same grouping/order as rows 23-26 of the sheet
    listOf("Major", "Δ7 (Major7)", "7 (Dominant 7)", "Italian +6"),
    listOf("Minor", "m7 (minor 7)", "mΔ7 (minMaj7)", "French +6"),
    listOf("Diminished", "ø7 (Half Diminished)", "°7 (Diminished 7)", "German +6"),
    listOf("Augmented", "Augmented Maj 7", "Augmented 7", "Neapolitan 6 (1st Inversion)"))

@Composable
fun ScaleChartScreen(db: HarmonyDb) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dao = db.dao()
    var tonic by remember { mutableStateOf("G") }
    var chordList by remember { mutableStateOf(emptyList<String>()) }
    var scales by remember { mutableStateOf(emptyList<ScaleRow>()) }
    var spell by remember { mutableStateOf(emptyList<ChordSpelling>()) }
    var rel by remember { mutableStateOf<Relative?>(null) }
    var dom by remember { mutableStateOf("") }
    var domOfDom by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { db.seedIfEmpty(ctx); chordList = dao.chordList().map { it.name }; scales = dao.scales() }
    LaunchedEffect(tonic, chordList) {
        val r = rootOf(tonic)
        spell = dao.spellings(r)
        rel = dao.relative(r)
        dom = (dao.dominantOf(tonic) ?: dao.dominantOf(r)) ?: ""
        domOfDom = dao.dominantOf(dom) ?: ""
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Harmony Cheat Sheet", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        // ---- Index panel: Tonic selector (data-validation list of the sheet) ----
        Card { Column(Modifier.padding(12.dp)) {
            Text("Index", fontWeight = FontWeight.Bold)
            var open by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick = { open = true }) { Text("Tonic Chord:  $tonic") }
                DropdownMenu(open, { open = false }) { chordList.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { tonic = c; open = false }) } } }
            // labels and values replicate T4:T21 of the sheet (several of them are simply "=T4" there)
            listOf("Sub Dominant Function" to tonic, "Dominant Function" to tonic, "V" to dom,
                "Enharmonic Chords" to "", "V/V" to domOfDom, "Mediant" to tonic, "Sub Mediant" to tonic,
                "Super Tonic" to tonic, "Leading Tone" to tonic).forEach { (k, v) ->
                Row { Text(k, Modifier.weight(1f)); Text(v, fontWeight = FontWeight.SemiBold) } }
        } }

        // ---- Four scale tables ----
        ScaleTable("Major Sharp Scales", scales.filter { it.kind == "major" && it.grp == "sharp" }, listOf("I","ii","iii","IV","V","vi","vii°"), tonic)
        ScaleTable("Minor Sharp Scales", scales.filter { it.kind == "minor" && it.grp == "sharp" }, listOf("i","ii°","III","iv","V","VI","VII"), tonic)
        ScaleTable("Major Flat Scales", scales.filter { it.kind == "major" && it.grp == "flat" }, listOf("I","ii","iii","IV","V","vi","vii°"), tonic)
        ScaleTable("Minor Flat Scales", scales.filter { it.kind == "minor" && it.grp == "flat" }, listOf("i","ii°","III","iv","V","VI","VII"), tonic)

        // ---- Chords Chart (rows 23-26): play icon in front of every chord ----
        Text("Chords Chart – ${rootOf(tonic)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        val byType = spell.associateBy { it.type }
        val typeNames = mapOf("Major" to "Major", "Minor" to "Minor", "Diminished" to "Diminished", "Augmented" to "Augmented")
        groups.forEach { row -> Card { Column(Modifier.padding(8.dp)) {
            row.forEach { type -> byType[type]?.let { ChordRow(type, it.notes) } } } } }

        // ---- Relative scales (row 29-30) ----
        rel?.let { r -> Text("Relative Scales", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Card { Column(Modifier.padding(8.dp)) {
                listOf("Dominant (V)" to r.dominant, "Subdominant (IV)" to r.subdominant,
                    "Relative Key (Major/minor)" to r.relativeKey, "Relative of Dominant" to r.relDominant,
                    "Relative of Subdominant" to r.relSubdominant, "Parallel" to r.parallel).forEach { (k, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayButton { Synth.play(Notes.scaleMidi(v), "scale") }
                        Column { Text(k, fontSize = 12.sp); Text(v, fontWeight = FontWeight.SemiBold) } } } } } }
    }
}

@Composable private fun PlayButton(onClick: () -> Unit) =
    IconButton(onClick) { Icon(Icons.Filled.PlayCircle, contentDescription = "Play") }

@Composable private fun ChordRow(type: String, notes: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    PlayButton { Synth.play(Notes.chordMidi(notes), "chord") }
    Column { Text(type, fontSize = 12.sp); Text(notes, fontWeight = FontWeight.SemiBold) }
}

@Composable private fun ScaleTable(title: String, rows: List<ScaleRow>, heads: List<String>, tonic: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row { Spacer(Modifier.width(48.dp)); Cell("Scale Name", 90, true); heads.forEach { Cell(it, 56, true) } }
        rows.forEach { s ->
            val hl = s.name.substringBefore(' ') == Regex("^[A-G][#b]?").find(tonic)?.value
            Row(Modifier.background(if (hl) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                verticalAlignment = Alignment.CenterVertically) {
                PlayButton { Synth.play(Notes.scaleMidi(s.name), "scale") }
                Cell(s.name, 90, true)
                s.degrees.split("|").forEach { c ->
                    Box(Modifier.clickable { Synth.play(Notes.romanChordMidi(c), "block") }) { Cell(c, 56, false) } }
            } }
    }
}

@Composable private fun Cell(t: String, w: Int, bold: Boolean) =
    Text(t, Modifier.width(w.dp).padding(4.dp), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
