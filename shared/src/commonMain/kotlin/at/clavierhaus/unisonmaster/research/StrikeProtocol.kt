package at.clavierhaus.unisonmaster.research

import at.clavierhaus.unisonmaster.tuning.Notes

/*
 * The wobble study: raw recordings of single strings, struck three times
 * each, so the pitch modulation can be measured offline and its repeatability
 * tested. Research tooling; hidden from the interface once the study is done.
 */

/** One recording to make: [strike] of three on [string] of [midi]. */
data class Take(val midi: Int, val string: StringPos, val strike: Int) {
    val id: String get() = "${Notes.name(midi)}-${string.code}-s$strike"
}

enum class StringPos(val code: String, val label: String, val instruction: String) {
    CENTRE("C", "centre string", "Mute the left and right strings; only the centre string sounds."),
    LEFT("L", "left string", "Mute every other string of this note; only the left string sounds."),
    RIGHT("R", "right string", "Mute every other string of this note; only the right string sounds."),
}

object StrikeProtocol {
    const val STRIKES = 3
    const val SECONDS = 20
    const val SAMPLE_RATE = 48_000
    const val DIRECTORY = "Recordings/ClavierTuner"

    /** The core set across the compass, then the top-stringing comparison (left and right strings). */
    fun takes(firstPlainMidi: Int = 40): List<Take> {
        val core = listOf(33, 38, firstPlainMidi, 45, 48, 57, 60, 69, 72, 81, 84, 93, 96).distinct().sorted()
        val list = ArrayList<Take>()
        for (m in core) {
            // wound bichords below the first plain unison: the left string; plain trichords: the centre string
            val s = if (m < firstPlainMidi) StringPos.LEFT else StringPos.CENTRE
            for (k in 1..STRIKES) list += Take(m, s, k)
        }
        for (m in listOf(69, 84, 96)) for (s in listOf(StringPos.LEFT, StringPos.RIGHT)) {
            for (k in 1..STRIKES) list += Take(m, s, k)
        }
        return list
    }

    /** "D_A4-C-s2_unproc_20260916-153012.wav" */
    fun fileName(piano: String, take: Take, unprocessed: Boolean, stamp: String): String {
        val safe = piano.filter { it.isLetterOrDigit() }.ifEmpty { "piano" }
        return "${safe}_${take.id}_${if (unprocessed) "unproc" else "mic"}_$stamp.wav"
    }

    val setup: List<String> = listOf(
        "Place the phone on the music desk, bottom edge (microphone) towards the strings. Leave it there for the whole session.",
        "Lid fully open, room quiet, no pedal.",
        "Mute every string of the note except the one named on the right.",
        "Tap Record. When \"Strike now\" appears, play the key mezzo-forte and hold it down until the recording ends (${SECONDS} s).",
        "Same touch for all three strikes. If a strike goes wrong, tap Redo.",
    )
}

/** 16-bit PCM mono WAV. */
object Wav {
    fun pcm16(samples: FloatArray, sampleRate: Int): ByteArray {
        val data = samples.size * 2
        val out = ByteArray(44 + data)
        fun str(at: Int, s: String) = s.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
        fun u32(at: Int, v: Int) { for (i in 0..3) out[at + i] = (v ushr (8 * i)).toByte() }
        fun u16(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v ushr 8).toByte() }
        str(0, "RIFF"); u32(4, 36 + data); str(8, "WAVE")
        str(12, "fmt "); u32(16, 16); u16(20, 1); u16(22, 1); u32(24, sampleRate); u32(28, sampleRate * 2); u16(32, 2); u16(34, 16)
        str(36, "data"); u32(40, data)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).let { if (it >= 0) it + 0.5f else it - 0.5f }.toInt()
            u16(44 + 2 * i, v)
        }
        return out
    }
}
