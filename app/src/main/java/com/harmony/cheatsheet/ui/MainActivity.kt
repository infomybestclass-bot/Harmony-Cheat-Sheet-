package com.harmony.cheatsheet.ui

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { ScaleChartScreen(HarmonyDb.get(this)) } }
    }
    override fun onStop() { Playback.stop(); super.onStop() }
}

/* ---------------- playback + visual feedback ---------------- */
/** Plays a sound and tells the table which cell(s) are sounding right now. */
object Playback {
    var activeKeys by mutableStateOf(setOf<String>())   // sustained glow: the cell you pressed
    var stepKey by mutableStateOf<String?>(null)        // flashing: the cell sounding at this moment
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    fun play(keys: Set<String>, midi: List<Int>, mode: String, steps: List<String> = emptyList()) {
        if (midi.isEmpty()) return
        job?.cancel()
        Synth.play(midi, mode)
        activeKeys = keys; stepKey = null
        job = scope.launch {
            when (mode) {
                "scale" -> { midi.indices.forEach { i -> stepKey = steps.getOrNull(i % 7); delay(350) }; stepKey = null; delay(650) }
                "chord" -> delay(((midi.size * 0.3 + 2.0) * 1000).toLong())
                else -> delay(2000)
            }
            activeKeys = emptySet(); stepKey = null
        }
    }
    fun stop() { job?.cancel(); Synth.stop(); activeKeys = emptySet(); stepKey = null }
}

@Composable
private fun PlayFx(strong: Boolean) {
    val t = rememberInfiniteTransition(label = "fx")
    val a by t.animateFloat(0.10f, 0.55f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize().background(Color(0xFFFFEB3B).copy(alpha = if (strong) 0.6f else a))
        .border(2.5.dp, Color(0xFFFF6F00)))
}

/* ---------------- Excel look ---------------- */
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
    136.4f, 73.4f, 73.4f, 73.4f, 73.4f, 73.4f, 73.4f, 73.4f, 23.6f,
    136.4f, 73.4f, 73.4f, 73.4f, 73.4f, 73.4f, 78f, 73.4f, 23.6f, 168.3f)
private fun cw(a: Int, b: Int = a): Dp = (a..b).sumOf { WCOL[it].toDouble() }.toFloat().dp
private fun rh(pt: Float): Dp = (pt * 4f / 3f).dp
private val DW = WCOL.sum()          // landscape design width
private val PW = cw(0, 7)            // portrait design width (one table)

private val GOLD_CELLS = setOf("F# Major|6", "D# minor|1", "C# Major|2", "C# Major|6",
    "A# minor|1", "A# minor|4", "Gb Major|3", "Eb minor|5")

private data class Hi(val bg: Color, val fg: Color)
private fun highlight(chord: String, idx: Int, tonic: String, v: String): Hi? {
    val c = chord.trim()
    if (idx == 6 && c == tonic) return Hi(ORANGE, Color.White)
    if (v.isNotEmpty() && c == v) return Hi(PINK, Color.Black)
    if (idx == 4 && c == tonic) return Hi(RED, Color.White)
    if (idx == 3 && c == tonic) return Hi(BLUE, Color.White)
    if (idx == 0 && c == tonic) return Hi(GREEN, Color.White)
    if (idx == 2 && c == tonic) return Hi(MED, hx(0x7E0000))
    if (idx == 5 && c == tonic) return Hi(SUBM, Color.White)
    if (idx == 1 && c == tonic) return Hi(SUPER, Color.Black)
    return null
}

private val enharmonicRoot = mapOf("E#" to "F", "Fb" to "E", "Cb" to "B", "B#" to "C")
private fun rootOf(chord: String) = Regex("^[A-G][#b]?").find(chord)!!.value.let { enharmonicRoot[it] ?: it }

@Composable
private fun XCell(
    text: String, w: Dp, h: Dp, bg: Color = Color.White, fg: Color = Color.Black, pt: Float = 11f,
    bold: Boolean = false, weight: FontWeight? = null, family: FontFamily = Cal, border: Boolean = true,
    play: Boolean = false, fxKey: String? = null, onClick: (() -> Unit)? = null
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
        if (fxKey != null) {
            if (fxKey == Playback.stepKey) PlayFx(strong = true)
            else if (fxKey in Playback.activeKeys) PlayFx(strong = false)
        }
    }
}

/* ---------------- scale tables ---------------- */
@Composable
private fun ScaleBlock(c0: Int, title: String, bg: Color, rows: List<ScaleRow>, heads: List<String>, tonic: String, v: String) = Column {
    XCell(title, cw(c0, c0 + 7), rh(22.95f), bg, Color.White, 16f, family = Narrow, bold = true)
    Row {
        XCell("Scale Name", cw(c0), rh(19.95f), Color.White, NAVY, 12f, family = Narrow, bold = true, border = false)
        heads.forEachIndexed { i, hd -> XCell(hd, cw(c0 + 1 + i), rh(19.95f), pt = 14f, bold = true) }
    }
    rows.forEach { s ->
        Row {
            XCell(s.name, cw(c0), rh(18f), pt = 11f, weight = FontWeight.Black, play = true, fxKey = "S|${s.name}") {
                Playback.play(setOf("S|${s.name}"), Notes.scaleMidi(s.name), "scale", (0..6).map { "C|${s.name}|$it" }) }
            s.degrees.split("|").forEachIndexed { i, ch ->
                val hi = highlight(ch, i, tonic, v)
                val gold = hi == null && "${s.name}|$i" in GOLD_CELLS
                val key = "C|${s.name}|$i"
                XCell(ch, cw(c0 + 1 + i), rh(18f), bg = hi?.bg ?: if (gold) GOLD else Color.White, fg = hi?.fg ?: Color.Black,
                    pt = 11f, family = Slab, bold = hi != null, weight = if (hi == null) FontWeight.SemiBold else null, fxKey = key) {
                    Playback.play(setOf(key), Notes.romanChordMidi(ch), "block") }
            }
        }
    }
}

private val MAJ = listOf("I", "ii", "iii", "IV", "V", "vi", "vii°")
private val MIN = listOf("i", "ii°", "III", "iv", "V", "VI", "VII")
private val C_MAJ_SHARP = hx(0x767171); private val C_MAJ_FLAT = hx(0x3B3838)

/* ---------------- Index ---------------- */
@Composable
private fun TonicDropdown(tonic: String, chords: List<String>, onTonic: (String) -> Unit, w: Dp, h: Dp, pt: Float, border: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        XCell("$tonic  ▾", w, h, pt = pt, bold = true, border = border) { open = true }
        DropdownMenu(open, { open = false }) {
            chords.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onTonic(c); open = false }) } }
    }
}

@Composable
private fun IndexColumn(tonic: String, chords: List<String>, onTonic: (String) -> Unit, v: String, vv: String) = Column {
    val w = cw(18)
    XCell("Index", w, rh(22.95f), GREY, Color.Black, 14f, bold = true)
    XCell("Tonic Chord ", w, rh(19.95f), GREEN, Color.White, 12f, bold = true)
    TonicDropdown(tonic, chords, onTonic, w, rh(18f), 14f, false)
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

/** Portrait version of the Index: same cells and colors, laid out as a compact grid. */
@Composable
private fun IndexPortrait(tonic: String, chords: List<String>, onTonic: (String) -> Unit, v: String, vv: String) = Column {
    val h = rh(22f); val u = PW / 6
    XCell("Index", PW, rh(22.95f), GREY, Color.Black, 14f, bold = true)
    Row {
        XCell("Tonic Chord", u, h, GREEN, Color.White, 11f, bold = true)
        TonicDropdown(tonic, chords, onTonic, u, h, 14f, true)
        XCell("V", u, h, PINK, Color.Black, 12f, bold = true)
        XCell(v, u, h, pt = 14f, bold = true)
        XCell("V/V", u, h, hx(0x7E2455), Color.White, 12f, bold = true)
        XCell(vv, u, h, pt = 14f, bold = true)
    }
    val q = PW / 4
    Row {
        XCell("Sub Dominant Function", q, h, PLAYC, Color.White, 9f, bold = true)
        XCell("Dominant Function", q, h, hx(0x9E0000), Color.White, 10f, bold = true)
        XCell("Mediant", q, h, MED, hx(0x7E0000), 11f, bold = true)
        XCell("Sub Mediant", q, h, hx(0x295E20), Color.White, 11f, bold = true)
    }
    val t = PW / 3
    Row {
        XCell("Super Tonic", t, h, SUPER, Color.Black, 11f, bold = true)
        XCell("Leading Tone", t, h, ORANGE, Color.White, 11f, bold = true)
        XCell("Enharmonic Chords", t, h, GOLD, Color.Black, 11f, bold = true)
    }
}

/* ---------------- Chords Chart ---------------- */
private val CHORD_ROWS = listOf(
    listOf("Major" to "Major", "Major7 (Δ7)" to "Δ7 (Major7)", " Dominant 7 (7)" to "7 (Dominant 7)", "Italian +6" to "Italian +6"),
    listOf("Minor" to "Minor", "minor 7 (m7)" to "m7 (minor 7)", "minMaj7 (mΔ7)" to "mΔ7 (minMaj7)", "French +6" to "French +6"),
    listOf("Diminished" to "Diminished", "Half Diminished (ø7)" to "ø7 (Half Diminished)", "Diminished 7 (°7)" to "°7 (Diminished 7)", "German +6" to "German +6"),
    listOf("Augmented" to "Augmented", "Augmented Maj 7 (+M7)" to "Augmented Maj 7", "Augmented 7 (+7)" to "Augmented 7", "Neapolitan 6 (1st Inversion)" to "Neapolitan 6 (1st Inversion)"))

@Composable
private fun ChordLabel(t: String, w: Dp, h: Dp) = XCell(t, w, h, pt = if (t.length > 21) 8f else 10f, bold = true)

@Composable
private fun ChordValue(key: String, spell: Map<String, String>, w: Dp, h: Dp) {
    val notes = spell[key] ?: ""
    val fx = "H|$key"
    XCell(notes, w, h, pt = 12f, family = Slab, bold = true, play = true, fxKey = fx) {
        if (notes.isNotEmpty()) Playback.play(setOf(fx), Notes.chordMidi(notes), "chord") }
}

@Composable
private fun ChordsChartWide(spell: Map<String, String>) = Row {
    val h = rh(15.6f)
    Column {
        CHORD_ROWS.forEach { r ->
            Row {
                ChordLabel(r[0].first, cw(0), h); ChordValue(r[0].second, spell, cw(1, 2), h)
                ChordLabel(r[1].first, cw(3, 5), h); ChordValue(r[1].second, spell, cw(6, 7), h)
                Spacer(Modifier.width(cw(8)))
                ChordLabel(r[2].first, cw(9), h); ChordValue(r[2].second, spell, cw(10, 12), h)
                ChordLabel(r[3].first, cw(13, 14), h); ChordValue(r[3].second, spell, cw(15, 16), h)
                Spacer(Modifier.width(cw(17)))
            }
        }
    }
    XCell("Chords Chart", cw(18), h * 4, GREY, Color.Black, 11f, bold = true)
}

@Composable
private fun ChordsChartTall(spell: Map<String, String>) = Column {
    val h = rh(19f)
    XCell("Chords Chart", PW, rh(20f), GREY, Color.Black, 12f, bold = true)
    listOf(0 to 1, 2 to 3).forEach { (a, b) ->
        CHORD_ROWS.forEach { r ->
            Row {
                ChordLabel(r[a].first, cw(0), h); ChordValue(r[a].second, spell, cw(1, 2), h)
                ChordLabel(r[b].first, cw(3, 4), h); ChordValue(r[b].second, spell, cw(5, 7), h)
            }
        }
    }
}

/* ---------------- Relative scales ---------------- */
private data class RelItem(val label: String, val color: Color, val name: String?)

private fun relItems(r: Relative?) = listOf(
    RelItem("Dominant (V)", RED, r?.dominant), RelItem("Subdominant (IV)", BLUE, r?.subdominant),
    RelItem("Relative Key (Major/minor)", SLATE, r?.relativeKey), RelItem("Relative of Dominant", ORANGE, r?.relDominant),
    RelItem("Relative of Subdominant", SLATE, r?.relSubdominant), RelItem("Parallel", hx(0x3B3838), r?.parallel))

@Composable
private fun RelCell(it: RelItem, w: Dp) {
    val n = it.name ?: ""
    val fx = "R|${it.label}"
    XCell(it.label, w, rh(15f), Color.White, it.color, 11f, family = Narrow, bold = true, border = false)
}

@Composable
private fun RelValue(it: RelItem, w: Dp) {
    val n = it.name ?: ""
    val fx = "R|${it.label}"
    XCell(n, w, rh(29.4f), pt = 12f, weight = FontWeight.Black, border = false, play = true, fxKey = fx) {
        if (n.isNotEmpty()) Playback.play(setOf(fx, "S|$n"), Notes.scaleMidi(n), "scale") }
}

@Composable
private fun RelativeWide(r: Relative?) = Column {
    XCell("Relative Scales", cw(0, 18), rh(21f), Color.White, GREEN, 14f, weight = FontWeight.Black, border = false)
    val spans = listOf(0..2, 3..5, 6..9, 10..13, 14..16, 17..18)
    val items = relItems(r)
    Row { items.forEachIndexed { i, it -> RelCell(it, cw(spans[i].first, spans[i].last)) } }
    Row { items.forEachIndexed { i, it -> RelValue(it, cw(spans[i].first, spans[i].last)) } }
}

@Composable
private fun RelativeTall(r: Relative?) = Column {
    XCell("Relative Scales", PW, rh(21f), Color.White, GREEN, 14f, weight = FontWeight.Black, border = false)
    val items = relItems(r)
    items.chunked(3).forEach { chunk ->
        Row { chunk.forEach { RelCell(it, PW / 3) } }
        Row { chunk.forEach { RelValue(it, PW / 3) } }
    }
}

/* ---------------- sheets ---------------- */
@Composable
private fun SheetWide(tonic: String, chords: List<String>, onTonic: (String) -> Unit, scales: List<ScaleRow>,
                      spell: Map<String, String>, rel: Relative?, v: String, vv: String) = Column {
    XCell("Harmony Cheat Sheet ", cw(0, 18), rh(54.6f), Color.White, Color.Black, 22f, weight = FontWeight.Black, border = false)
    fun pick(kind: String, grp: String) = scales.filter { it.kind == kind && it.grp == grp }
    Row {
        Column {
            ScaleBlock(0, "Major Sharp Scales", C_MAJ_SHARP, pick("major", "sharp"), MAJ, tonic, v)
            Spacer(Modifier.height(rh(19.2f)))
            ScaleBlock(0, "Major Flat Scales", C_MAJ_FLAT, pick("major", "flat"), MAJ, tonic, v)
            Spacer(Modifier.height(rh(12.6f)))
        }
        Spacer(Modifier.width(cw(8)))
        Column {
            ScaleBlock(9, "Minor Sharp Scales", SUPER, pick("minor", "sharp"), MIN, tonic, v)
            Spacer(Modifier.height(rh(19.2f)))
            ScaleBlock(9, "Minor Flat Scales", SLATE, pick("minor", "flat"), MIN, tonic, v)
            Spacer(Modifier.height(rh(12.6f)))
        }
        Spacer(Modifier.width(cw(17)))
        IndexColumn(tonic, chords, onTonic, v, vv)
    }
    ChordsChartWide(spell)
    Spacer(Modifier.height(rh(10.2f)))
    RelativeWide(rel)
}

@Composable
private fun SheetTall(tonic: String, chords: List<String>, onTonic: (String) -> Unit, scales: List<ScaleRow>,
                      spell: Map<String, String>, rel: Relative?, v: String, vv: String) = Column {
    XCell("Harmony Cheat Sheet ", PW, rh(40f), Color.White, Color.Black, 20f, weight = FontWeight.Black, border = false)
    fun pick(kind: String, grp: String) = scales.filter { it.kind == kind && it.grp == grp }
    val gap = Modifier.height(rh(8f))
    IndexPortrait(tonic, chords, onTonic, v, vv); Spacer(gap)
    ScaleBlock(0, "Major Sharp Scales", C_MAJ_SHARP, pick("major", "sharp"), MAJ, tonic, v); Spacer(gap)
    ScaleBlock(0, "Major Flat Scales", C_MAJ_FLAT, pick("major", "flat"), MAJ, tonic, v); Spacer(gap)
    ScaleBlock(0, "Minor Sharp Scales", SUPER, pick("minor", "sharp"), MIN, tonic, v); Spacer(gap)
    ScaleBlock(0, "Minor Flat Scales", SLATE, pick("minor", "flat"), MIN, tonic, v); Spacer(gap)
    ChordsChartTall(spell); Spacer(gap)
    RelativeTall(rel)
    Spacer(Modifier.height(rh(20f)))
}

/* ---------------- screen: data + zoom/pan anywhere ---------------- */
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val landscape = maxWidth > maxHeight
        val dW = with(density) { (if (landscape) DW.dp else PW).toPx() }
        val fit = viewW / dW
        var dH by remember(landscape) { mutableFloatStateOf(1f) }
        var zoom by remember(landscape) { mutableFloatStateOf(1f) }
        var off by remember(landscape) { mutableStateOf(Offset.Zero) }
        fun clamp(o: Offset, s: Float) = Offset(
            o.x.coerceIn(minOf(0f, viewW - dW * s), 0f), o.y.coerceIn(minOf(0f, viewH - dH * s), 0f))

        // Gestures are watched in the Initial pass, so they work over cells, buttons and empty areas alike.
        // A tap passes through to the cell; dragging or pinching takes over (and cancels the tap).
        Box(Modifier.fillMaxSize().clipToBounds().pointerInput(fit, landscape) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var moving = false; var acc = Offset.Zero
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = ev.changes.count { it.pressed }
                    if (pressed >= 2 || moving) {
                        val z = ev.calculateZoom(); val pan = ev.calculatePan(); val c = ev.calculateCentroid()
                        if (c.isSpecified) {
                            val nz = (zoom * z).coerceIn(1f, 6f)
                            off = clamp(c - (c - off) * (nz / zoom) + pan, fit * nz); zoom = nz
                        }
                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        moving = true
                    } else {
                        acc += ev.calculatePan()
                        if (acc.getDistance() > viewConfiguration.touchSlop) moving = true
                    }
                } while (ev.changes.any { it.pressed })
            }
        }) {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = fit * zoom; scaleY = fit * zoom; translationX = off.x; translationY = off.y
            }.onSizeChanged { dH = it.height.toFloat() }) {
                if (landscape) SheetWide(tonic, chordList, { tonic = it }, scales, spell, rel, v, vv)
                else SheetTall(tonic, chordList, { tonic = it }, scales, spell, rel, v, vv)
            }
            if (zoom > 1.02f || off != Offset.Zero) {
                FilledTonalButton(onClick = { zoom = 1f; off = Offset.Zero },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Text("1×") }
            }
        }
    }
}

