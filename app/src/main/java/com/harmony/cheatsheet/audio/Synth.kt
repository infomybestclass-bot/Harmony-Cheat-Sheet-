package com.harmony.cheatsheet.audio

import android.media.*
import kotlin.math.*

object Notes {
    private val base = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** "E♭" "F*" (double sharp) "B♭♭" "C#" "Abb" -> pitch class 0..11 */
    fun pitchClass(tok: String): Int {
        val t = tok.trim(); var pc = base[t[0].uppercaseChar()] ?: error("bad note $tok")
        for (ch in t.drop(1)) pc += when (ch) { '♯', '#' -> 1; '♭', 'b' -> -1; '*', 'x' -> 2; else -> 0 }
        return ((pc % 12) + 12) % 12
    }

    /** Chord spelling "C–E♭–G" or "Ab – C – F#" -> ascending MIDI notes starting near middle C. */
    fun chordMidi(spelling: String): List<Int> {
        val toks = spelling.split('–', '-').map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Int>()
        for (t in toks) {
            val pc = pitchClass(t)
            var m = if (out.isEmpty()) 60 + pc else { var x = out.last() - out.last() % 12 + pc; while (x <= out.last()) x += 12; x }
            out += m
        }
        return out
    }

    /** "G Major" / "E minor" / "Bb Major" -> 8 ascending MIDI notes (natural minor for minor). */
    fun scaleMidi(name: String): List<Int> {
        val (root, kind) = name.trim().split(" ")
        val steps = if (kind.lowercase() == "major") listOf(0,2,4,5,7,9,11,12) else listOf(0,2,3,5,7,8,10,12)
        val r = 60 + pitchClass(root); return steps.map { r + it }
    }

    /** "Dm" "B°" "F#" "Bb" -> triad MIDI notes. */
    fun romanChordMidi(sym: String): List<Int> {
        val m = Regex("^([A-G][#b]?)(.*)$").matchEntire(sym.trim()) ?: return emptyList()
        val r = 60 + pitchClass(m.groupValues[1])
        return when (m.groupValues[2]) { "m" -> listOf(0,3,7); "°" -> listOf(0,3,6); else -> listOf(0,4,7) }.map { r + it }
    }
}

object Synth {
    private const val SR = 44100
    private var track: AudioTrack? = null

    private fun freq(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)

    private fun tone(buf: FloatArray, startSec: Double, durSec: Double, midi: Int, gain: Float) {
        val f = freq(midi); val s0 = (startSec * SR).toInt(); val n = (durSec * SR).toInt()
        val harm = floatArrayOf(1f, .5f, .3f, .15f, .08f)
        for (i in 0 until n) { val idx = s0 + i; if (idx >= buf.size) break
            val t = i / SR.toDouble(); var v = 0.0
            for (h in harm.indices) v += harm[h] * sin(2 * PI * f * (h + 1) * t) * exp(-t * (2.0 + h * 1.5))
            val attack = min(1.0, i / (0.005 * SR)); val rel = min(1.0, (n - i) / (0.05 * SR))
            buf[idx] += (v * attack * rel * gain).toFloat() }
    }

    /** mode: "chord" = quick arpeggio then block chord, "scale" = ascending line, "block" = just the chord */
    fun play(midi: List<Int>, mode: String) {
        if (midi.isEmpty()) return
        stop()
        val total = when (mode) { "scale" -> midi.size * 0.35 + 1.0; "chord" -> midi.size * 0.3 + 2.2; else -> 2.2 }
        val buf = FloatArray((total * SR).toInt())
        val g = 0.3f / sqrt(midi.size.toFloat())
        when (mode) {
            "scale" -> midi.forEachIndexed { i, m -> tone(buf, i * 0.35, 0.9, m, 0.35f) }
            "chord" -> { midi.forEachIndexed { i, m -> tone(buf, i * 0.3, 0.8, m, 0.35f) }
                val t0 = midi.size * 0.3 + 0.1; midi.forEach { tone(buf, t0, 1.8, it, g) } }
            else -> midi.forEach { tone(buf, 0.0, 2.0, it, g) }
        }
        val pcm = ShortArray(buf.size) { (buf[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        t.write(pcm, 0, pcm.size); t.play(); track = t
    }
    fun stop() { track?.run { runCatching { stop() }; release() }; track = null }
}
