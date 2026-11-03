package com.harmony.cheatsheet.ui

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harmony.cheatsheet.audio.Notes
import com.harmony.cheatsheet.audio.Synth
import com.harmony.cheatsheet.data.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { ScaleChartScreen(HarmonyDb.get(this)) } }
    }
    override fun onStop() { Synth.stop(); super.onStop() }
}

/* ------------------------------------------------------------------ */
/*  Excel look: colors / fonts / sizes taken from the "Scale Chart" sheet */
/* ------------------------------------------------------------------ */
private fun hx(v: Long) = Color(0xFF000000 or v)
private val GREEN = hx(0x00B050); private val BLUE = hx(0x0070C0); private val RED = hx(0xC00000)
private val PINK = hx(0xDC84B4); private val ORANGE = hx(0xC55A11); private val MED = hx(0xA9D18E)
private val SUBM = hx(0x548235); private val SUPER = hx(0x8497B0); private val GOLD = hx(0xFFD966)
private val NAVY = hx(0x1F4E79); private val SLATE = hx(0x333F50); private val GREY = hx(0xAFABAB)
private val PLAYC = hx(0x2E75B6)

private val Cal = FontFamily.SansSerif
private val Narrow = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
private val Slab = FontFamily.Serif

// column widths B..T (Excel chars*7+5 px): index 0=B ... 18=T
private val WCOL = floatArrayOf(
    136.4f, 73.4f, 73.4f, 73.4f, 73.4f, 73.4f, 73.4f, 73.4f, 23.6f,      // B..J
    136.4f, 73.4f, 73.4f, 73.4f, 73.4f, 73.4f, 78f, 73.4f, 23.6f, 168.3f) // K..T
private fun cw(a: Int, b: Int = a): Dp = (a..b).sumOf { WCOL[it].toDouble() }.toFloat().dp
private fun rh(pt: Float): Dp = (pt * 4f / 3f).dp
private val DW = WCOL.sum()
private const val DH = 750f

// extra gold "Enharmonic Chords" cells that are filled by hand in the sheet
private val GOLD_CELLS = setOf("F# Major|6", "D# minor|1", "C# Major|2", "C# Major|6",
    "A# minor|1", "A# minor|4", "Gb Major|3", "Eb minor|5")

private data class Hi(val bg: Color, val fg: Color)

/** Re-creation of the sheet's conditional-formatting rules (by priority). */
private fun highlight(chord: String, idx: Int, tonic: String, v: String): Hi? {
    val c = chord.trim()
    if (idx == 6 && c == tonic) return Hi(ORANGE, Color.White)          // Leading tone
    if (v.isNotEmpty() && c == v) return Hi(PINK, Color.Black)          // V of the tonic
    if (idx == 4 && c == tonic) return Hi(RED, Color.White)             // Dominant
    if (idx == 3 && c == tonic) return Hi(BLUE, Color.White)            // Sub-dominant
    if (idx == 0 && c == tonic) return Hi(GREEN, Color.White)           // Tonic
    if (idx == 2 && c == tonic) return Hi(MED, hx(0x7E0000))            // Mediant
    if (idx == 5 && c == tonic) return Hi(SUBM, Color.White)            // Sub-mediant
    if (idx == 1 && c == tonic) return Hi(SUPER, Color.Black)           // Super-tonic
    return null
}

private val enharmonicRoot = mapOf("E#" to "F", "Fb" to "E", "Cb" to "B", "B#" to "C")
private fun rootOf(chord: String) = Regex("^[A-G][#b]?").find(chord)!!.value.let { enharmonicRoot[it] ?: it }

@Composable
private fun XCell(
    text: String, w: Dp, h: Dp, bg: Color = Color.White, fg: Color = Color.Black, pt: Float = 11f,
    bold: Boolean = false, weight: FontWeight? = null, family: FontFamily = Cal, border: Boolean = true,
    play: Boolean = false, onClick: (() -> Unit)? = null
) {
    var m = Modifier.size(w, h).background(bg)
    if (border) m = m.border(0.7.dp, Color.Black)
    if (onClick != null) m = m.clickable { onClick() }
    Box(m, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (play && text.isNotEmpty()) {
                Icon(Icons.Filled.PlayCircle, null, Modifier.size((pt * 1.35f).dp), tint = if (bg == Color.White) PLAYC else fg)
                Spacer(Modifier.width(3.dp))
            }
            Text(text, color = fg, fontSize = (pt * 4f / 3f).sp, textAlign = TextAlign.Center,
                fontWeight = weight ?: if (bold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = family, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun ScaleHeader(title: String, bg: Color, heads: List<String>, c0: Int) {
    XCell(title, cw(c0, c0 + 7), rh(22.95f), bg, Color.White, 16f, family = Narrow, bold = true)
    Row {
        XCell("Scale Name", cw(c0), rh(19.95f), Color.White, NAVY, 12f, family = Narrow, bold = true, border = false)
        heads.forEachIndexed { i, hd -> XCell(hd, cw(c0 + 1 + i), rh(19.95f), pt = 14f, bold = true) }
    }
}

@Composable
private fun ScaleRowView(s: ScaleRow, tonic: String, v: String, c0: Int) = Row {
    XCell(s.name, cw(c0), rh(18f), pt = 11f, weight = FontWeight.Black, play = true) {
        Synth.play(Notes.scaleMidi(s.name), "scale") }
    s.degrees.split("|").forEachIndexed { i, ch ->
        val hi = highlight(ch, i, tonic, v)
        val gold = hi == null && "${s.name}|$i" in GOLD_CELLS
        XCell(ch, cw(c0 + 1 + i), rh(18f), bg = hi?.bg ?: if (gold) GOLD else Color.White, fg = hi?.fg ?: Color.Black,
            pt = 11f, family = Slab, bold = hi != null, weight = if (hi == null) FontWeight.SemiBold else null) {
            Synth.play(Notes.romanChordMidi(ch), "block") }
    }
}

@Composable
private fun ScaleColumn(c0: Int, t1: String, bg1: Color, sharp: List<ScaleRow>, t2: String, bg2: Color, flat: List<ScaleRow>,
                        heads: List<String>, tonic: String, v: String) = Column {
    ScaleHeader(t1, bg1, heads, c0)
    sharp.forEach { ScaleRowView(it, tonic, v, c0) }
    Spacer(Modifier.height(rh(19.2f)))
    ScaleHeader(t2, bg2, heads, c0)
    flat.forEach { ScaleRowView(it, tonic, v, c0) }
    Spacer(Modifier.height(rh(12.6f)))
}

@Composable
private fun IndexColumn(tonic: String, chords: List<String>, onTonic: (String) -> Unit, v: String, vv: String) = Column {
    val w = cw(18)
    var open by remember { mutableStateOf(false) }
    XCell("Index", w, rh(22.95f), GREY, Color.Black, 14f, bold = true)
    XCell("Tonic Chord ", w, rh(19.95f), GREEN, Color.White, 12f, bold = true)
    Box {
        XCell("$tonic  ▾", w, rh(18f), pt = 14f, bold = true, border = false) { open = true }
        DropdownMenu(open, { open = false }) {
            chords.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onTonic(c); open = false }) } }
    }
    XCell("Sub Dominant Function", w, rh(18f), PLAYC, Color.White, 12f, bold = true)
    Spacer(Modifier.height(rh(18f)))
    XCell("Dominant Function", w, rh(18f), hx(0x9E0000), Color.White, 12f, bold = true)
    Spacer(Modifier.height(rh(18f)))
    XCell("V", w, rh(18f), PINK, Color.Black, 12f, bold = true)
    XCell(v, w, rh(18f), pt = 14f, bold = true, border = false)
    XCell("Enharmonic Chords", w, rh(18f), GOLD, Color.Black, 11f, bold = true)
    XCell("V/V", w, rh(19.2f), hx(0x7E2455), Color.White, 12f, bold = true)
    XCell(vv, w, rh(22.95f), pt = 14f, bold = true, border = false)
    XCell("Mediant", w, rh(19.95f), MED, hx(0x7E0000), 12f, bold = true)
    Spacer(Modifier.height(rh(18f)))
    XCell("Sub Mediant", w, rh(18f), hx(0x295E20), Color.White, 11f, bold = true)
    Spacer(Modifier.height(rh(18f)))
    XCell("Super Tonic", w, rh(18f), SUPER, Color.Black, 12f, bold = true)
    Spacer(Modifier.height(rh(18f)))
    XCell("Leading Tone", w, rh(18f), ORANGE, Color.White, 12f, bold = true)
}

private val CHORD_ROWS = listOf(
    listOf("Major" to "Major", "Major7 (Δ7)" to "Δ7 (Major7)", " Dominant 7 (7)" to "7 (Dominant 7)", "Italian +6" to "Italian +6"),
    listOf("Minor" to "Minor", "minor 7 (m7)" to "m7 (minor 7)", "minMaj7 (mΔ7)" to "mΔ7 (minMaj7)", "French +6" to "French +6"),
    listOf("Diminished" to "Diminished", "Half Diminished (ø7)" to "ø7 (Half Diminished)", "Diminished 7 (°7)" to "°7 (Diminished 7)", "German +6" to "German +6"),
    listOf("Augmented" to "Augmented", "Augmented Maj 7 (+M7)" to "Augmented Maj 7", "Augmented 7 (+7)" to "Augmented 7", "Neapolitan 6 (1st Inversion)" to "Neapolitan 6 (1st Inversion)"))

@Composable
private fun ChordsChart(spell: Map<String, String>) = Row {
    val h = rh(15.6f)
    @Composable fun lab(t: String, w: Dp) = XCell(t, w, h, pt = if (t.length > 21) 8f else 10f, bold = true)
    @Composable fun valc(key: String, w: Dp) {
        val notes = spell[key] ?: ""
        XCell(notes, w, h, pt = 12f, family = Slab, bold = true, play = true) { if (notes.isNotEmpty()) Synth.play(Notes.chordMidi(notes), "chord") }
    }
    Column {
        CHORD_ROWS.forEach { r ->
            Row {
                lab(r[0].first, cw(0)); valc(r[0].second, cw(1, 2))
                lab(r[1].first, cw(3, 5)); valc(r[1].second, cw(6, 7))
                Spacer(Modifier.width(cw(8)))
                lab(r[2].first, cw(9)); valc(r[2].second, cw(10, 12))
                lab(r[3].first, cw(13, 14)); valc(r[3].second, cw(15, 16))
                Spacer(Modifier.width(cw(17)))
            }
        }
    }
    XCell("Chords Chart", cw(18), h * 4, GREY, Color.Black, 11f, bold = true)
}

@Composable
private fun RelativeScales(r: Relative?) = Column {
    XCell("Relative Scales", cw(0, 18), rh(21f), Color.White, GREEN, 14f, weight = FontWeight.Black, border = false)
    val groups = listOf(
        Triple("Dominant (V)", 0..2, RED) to r?.dominant, Triple("Subdominant (IV)", 3..5, BLUE) to r?.subdominant,
        Triple("Relative Key (Major/minor)", 6..9, SLATE) to r?.relativeKey, Triple("Relative of Dominant", 10..13, ORANGE) to r?.relDominant,
        Triple("Relative of Subdominant", 14..16, SLATE) to r?.relSubdominant, Triple("Parallel", 17..18, hx(0x3B3838)) to r?.parallel)
    Row { groups.forEach { (g, _) -> XCell(g.first, cw(g.second.first, g.second.last), rh(15f), Color.White, g.third, 11f, family = Narrow, bold = true, border = false) } }
    Row { groups.forEach { (g, name) -> val n = name ?: ""
        XCell(n, cw(g.second.first, g.second.last), rh(29.4f), pt = 12f, weight = FontWeight.Black, border = false, play = true) {
            if (n.isNotEmpty()) Synth.play(Notes.scaleMidi(n), "scale") } } }
}

@Composable
private fun Sheet(tonic: String, chords: List<String>, onTonic: (String) -> Unit, scales: List<ScaleRow>,
                  spell: Map<String, String>, rel: Relative?, v: String, vv: String) = Column {
    XCell("Harmony Cheat Sheet ", cw(0, 18), rh(54.6f), Color.White, Color.Black, 22f, weight = FontWeight.Black, border = false)
    val majHeads = listOf("I", "ii", "iii", "IV", "V", "vi", "vii°")
    val minHeads = listOf("i", "ii°", "III", "iv", "V", "VI", "VII")
    fun pick(kind: String, grp: String) = scales.filter { it.kind == kind && it.grp == grp }
    Row {
        ScaleColumn(0, "Major Sharp Scales", hx(0x767171), pick("major", "sharp"), "Major Flat Scales", hx(0x3B3838), pick("major", "flat"), majHeads, tonic, v)
        Spacer(Modifier.width(cw(8)))
        ScaleColumn(9, "Minor Sharp Scales", SUPER, pick("minor", "sharp"), "Minor Flat Scales", SLATE, pick("minor", "flat"), minHeads, tonic, v)
        Spacer(Modifier.width(cw(17)))
        IndexColumn(tonic, chords, onTonic, v, vv)
    }
    ChordsChart(spell)
    Spacer(Modifier.height(rh(10.2f)))
    RelativeScales(rel)
}

@Composable
fun ScaleChartScreen(db: HarmonyDb) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dao = db.dao()
    var tonic by remember { mutableStateOf("G") }
    var chordList by remember { mutableStateOf(emptyList<String>()) }
    var scales by remember { mutableStateOf(emptyList<ScaleRow>()) }
    var spell by remember { mutableStateOf(emptyMap<String, String>()) }
    var rel by remember { mutableStateOf<Relative?>(null) }
    var v by remember { mutableStateOf("") }
    var vv by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { db.seedIfEmpty(ctx); chordList = dao.chordList().map { it.name }; scales = dao.scales() }
    LaunchedEffect(tonic, chordList) {
        val r = rootOf(tonic)
        spell = dao.spellings(r).associate { it.type to it.notes }
        rel = dao.relative(tonic) ?: dao.relative(r)
        v = ((dao.dominantOf(tonic) ?: dao.dominantOf(r)) ?: "").trim()
        vv = (dao.dominantOf(v) ?: "").trim()
    }

    // The whole sheet is drawn at its design size and scaled to fit the screen width.
    // Pinch to zoom, drag to pan, double-tap to reset.
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val viewW = with(LocalDensity.current) { maxWidth.toPx() }
        val viewH = with(LocalDensity.current) { maxHeight.toPx() }
        val dW = with(LocalDensity.current) { DW.dp.toPx() }
        val dH = with(LocalDensity.current) { DH.dp.toPx() }
        val fit = viewW / dW
        var zoom by remember { mutableFloatStateOf(1f) }
        var off by remember { mutableStateOf(Offset.Zero) }
        fun clamp(o: Offset, s: Float) = Offset(
            o.x.coerceIn(minOf(0f, viewW - dW * s), 0f), o.y.coerceIn(minOf(0f, viewH - dH * s), 0f))
        Box(Modifier.fillMaxSize().clipToBounds()
            .pointerInput(fit) { detectTransformGestures { c, pan, z, _ ->
                val nz = (zoom * z).coerceIn(1f, 5f)
                off = clamp(c - (c - off) * (nz / zoom) + pan, fit * nz); zoom = nz } }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = 1f; off = Offset.Zero }) }) {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = fit * zoom; scaleY = fit * zoom; translationX = off.x; translationY = off.y }) {
                Sheet(tonic, chordList, { tonic = it }, scales, spell, rel, v, vv)
            }
        }
    }
}
