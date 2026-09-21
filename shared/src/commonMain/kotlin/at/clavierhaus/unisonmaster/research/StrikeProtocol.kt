package at.clavierhaus.unisonmaster.research

import at.clavierhaus.unisonmaster.tuning.Notes

/*
 * The wobble study: raw recordings of single strings, struck three times
 * each, so the pitch modulation can be measured offline and its repeatability
 * tested. Research tooling; hidden from the interface once the study is done.
 */

/**
 * One recording to make: [strike] of three on [string] of [midi]. A
 * [variant] names a special case of the Reference study (its code goes into
 * the file name) and [note] is the instruction for it, shown on the screen.
 */
data class Take(val midi: Int, val string: StringPos, val strike: Int, val variant: String = "", val note: String = "", val part: String = "") {
    val id: String get() = "${Notes.name(midi)}-${string.code}-s$strike" + (if (variant.isEmpty()) "" else "-$variant")
    /** What to do for this take: the variant's own instruction, or the string position's. */
    val instruction: String get() = note.ifEmpty { string.instruction }
}

enum class StringPos(val code: String, val label: String, val instruction: String) {
    CENTRE("C", "centre string", "Mute the left and right strings; only the centre string sounds."),
    LEFT("L", "left string", "Mute every other string of this note; only the left string sounds."),
    RIGHT("R", "right string", "Mute every other string of this note; only the right string sounds."),
    ALL("U", "all strings", "Nothing muted: the whole unison sounds, as it is."),
}

/** What is being recorded. */
enum class Study(val label: String, val code: String) {
    /** Single strings, three strikes, for the pitch-modulation study. */
    WOBBLE("Wobble", "wobble"),
    /** Every key of the instrument, unmuted, once: the corpus the key detector is tested against. */
    COMPASS("Compass", "compass"),
    /**
     * The reference: the whole instrument, every key one string and every
     * key as it is, and the corner cases at six notes — the recordings the
     * measurement layer is built and judged on while the piano is out of
     * reach (docs/REFERENCE-RECORDING.md).
     */
    REFERENCE("Reference", "reference");

    companion object {
        fun ofCode(code: String?): Study = entries.firstOrNull { it.code == code } ?: WOBBLE
    }
}

/** The pianos of the study; [code] goes into file names. */
enum class Piano(val label: String, val code: String) {
    STEINWAY_D("Steinway D", "D"),
    BOESENDORFER("Bösendorfer", "Boesendorfer");

    companion object {
        fun ofCode(code: String?): Piano = entries.firstOrNull { it.code == code } ?: STEINWAY_D
    }
}

object StrikeProtocol {
    const val STRIKES = 3
    const val SAMPLE_RATE = 48_000
    const val DIRECTORY = "Recordings/ClavierTuner"

    private const val C1 = 24
    private const val C2 = 36
    private const val C3 = 48
    private const val C4 = 60
    private const val C6 = 84

    /** The lowest key of the instrument: the Bösendorfer 225 goes down to F0. */
    fun lowestMidi(piano: Piano): Int = when (piano) { Piano.BOESENDORFER -> 17; else -> 21 }

    fun takes(piano: Piano, firstPlainMidi: Int = 40, study: Study = Study.WOBBLE): List<Take> = when (study) {
        Study.WOBBLE -> wobbleTakes(piano, firstPlainMidi)
        Study.COMPASS -> (lowestMidi(piano)..108).map { Take(it, StringPos.ALL, 1) }
        Study.REFERENCE -> referenceTakes(piano, firstPlainMidi)
    }

    /** Recording length per take: the compass wants only the strike and a few seconds of tone. */
    fun seconds(piano: Piano, midi: Int, study: Study): Int = when (study) {
        Study.COMPASS -> if (midi < 48) 5 else 4
        Study.REFERENCE -> if (midi < C2) 8 else if (midi < C4) 6 else 4
        Study.WOBBLE -> seconds(piano, midi)
    }

    /** The six notes of the Reference study's corner cases: the lowest key, both sides of the break, and A2, A4, C6, C7. */
    fun cornerNotes(piano: Piano, firstPlainMidi: Int): List<Int> =
        listOf(lowestMidi(piano), firstPlainMidi - 1, firstPlainMidi, 45, 69, 84, 96).distinct().sorted()

    /**
     * The Reference study, in the order it is recorded:
     *  A. every key, one string (centre of a trichord, left of a bichord, the
     *     monochord as it is), one strike — the inharmonicity of the
     *     instrument and the single-string reading;
     *  B. every key as it is, nothing muted, one strike — the coupled unison
     *     the FOSS tuner hears;
     *  C. at the corner notes: three strikes on one string, soft, medium
     *     and hard; the unison with one string set 3 cents sharp, then that
     *     string alone; the note struck while the semitone above still
     *     rings; the octave below it struck first and held; and every string
     *     of the unison alone, for the beats that are the string's own.
     * File names carry the variant: "Boesendorfer_A4-U-s1-det3_unproc_…".
     */
    fun referenceTakes(piano: Piano, firstPlainMidi: Int = 40): List<Take> {
        val keys = lowestMidi(piano)..108
        val list = ArrayList<Take>()
        for (m in keys) list += Take(m, if (m < firstPlainMidi) StringPos.LEFT else StringPos.CENTRE, 1, part = PART_A)
        for (m in keys) list += Take(m, StringPos.ALL, 1, part = PART_B)
        for (m in cornerNotes(piano, firstPlainMidi)) {
            val one = if (m < firstPlainMidi) StringPos.LEFT else StringPos.CENTRE
            list += Take(m, one, 1, "soft", "${one.instruction} Strike piano: as softly as the note still speaks.", PART_C)
            list += Take(m, one, 2, "medium", "${one.instruction} Strike mezzo-forte, the usual touch.", PART_C)
            list += Take(m, one, 3, "hard", "${one.instruction} Strike forte, as hard as you would in a pitch raise.", PART_C)
            list += Take(
                m, StringPos.ALL, 1, "det3",
                "With another tuner (PianoMeter), set the RIGHT string of this note 3 cents sharp of its partners. Nothing muted: the detuned unison sounds. Type the tuner's reading below.",
                PART_C,
            )
            list += Take(m, StringPos.RIGHT, 1, "det3", "The same right string, still 3 cents sharp, alone: mute the others. Type the reading below. Afterwards tune it back.", PART_C)
            list += Take(
                m, one, 1, "ring",
                "Strike the semitone ABOVE this note (nothing muted) and, while it rings, one second later this note's ${one.label}. Hold both.",
                PART_C,
            )
            if (m - 12 >= keys.first) list += Take(
                m, one, 1, "octave",
                "Strike the octave BELOW this note (its ${one.label}, others muted), hold it, and one second later this note's ${one.label}: the octave as the ear hears it.",
                PART_C,
            )
            // every string of the unison alone: which partials of which string
            // beat by themselves — a false beat is the string's, not the unison's
            if (m >= firstPlainMidi) {
                for (pos in listOf(StringPos.LEFT, StringPos.CENTRE, StringPos.RIGHT)) list += Take(m, pos, 1, "alone", "${pos.instruction} One strike, mezzo-forte: this string by itself, for its own beats.", PART_C)
            } else if (m >= BICHORD_FROM) {
                for (pos in listOf(StringPos.LEFT, StringPos.RIGHT)) list += Take(m, pos, 1, "alone", "${pos.instruction} One strike, mezzo-forte: this string by itself, for its own beats.", PART_C)
            }
        }
        return list
    }

    /** Below this the 225's bass is monochord; from here to the plain-wire floor, bichords. Set per instrument later. */
    const val BICHORD_FROM = 28   // E1

    const val PART_A = "A · one string"
    const val PART_B = "B · as it is"
    const val PART_C = "C · corner cases"

    /** What to do before the first take of a part: the mutes. */
    fun partSetup(part: String): String = when (part) {
        PART_A -> "Mutes in: a felt strip through every section so that only the centre string of each trichord sounds; a wedge on the right string of every bichord. They stay in for the whole part."
        PART_B -> "Mutes out, all of them. The piano as it is."
        PART_C -> "Each take says what to mute and what to detune. Have the second tuner app ready."
        else -> ""
    }

    /** True for a take whose instruction asks for a reading from the second tuner. */
    fun wantsReading(take: Take): Boolean = take.variant == "det3"

    val referenceSetup: List<String> = listOf(
        "Place the phone on the music desk, bottom edge (microphone) towards the strings, and leave it there for the whole session; the same place for every take.",
        "Lid fully open, room quiet, no pedal. Level: a mezzo-forte strike should reach at least a third of full scale on the meter, never red.",
        "Part A, one string per key: run a felt strip through each section so only the centre string of a trichord sounds; on the bichords mute the right string. Part B: take the mutes out. Part C says per take what to mute.",
        "Tap Record; when \"Strike now\" appears, play the key and hold it until the recording ends. If a strike goes wrong, tap Redo.",
        "Corner cases (Part C) need a second tuner app to set 3 cents: type its reading into the field under the take; every take saved is listed in Documents/ClavierTuner/<piano>_manifest.csv.",
    )

    val compassSetup: List<String> = listOf(
        "Place the phone on the music desk, bottom edge (microphone) towards the strings. Leave it there for the whole session.",
        "Lid fully open, room quiet, no pedal. Nothing muted.",
        "Every key from the lowest to C8, once. Tap Record; when \"Strike now\" appears, play the key mezzo-forte and hold it until the recording ends.",
        "The piano as it is: unisons need not be clean. If a strike goes wrong, tap Redo.",
    )

    /** The core set across the compass, then the top-stringing comparison (left and right strings). */
    fun wobbleTakes(piano: Piano, firstPlainMidi: Int = 40): List<Take> {
        val notes = when (piano) {
            Piano.BOESENDORFER -> listOf(C1, C2, firstPlainMidi, C3, 57, C4, 69, 72, 81, 84, 93, 96)
            else -> listOf(33, 38, firstPlainMidi, 45, C3, 57, C4, 69, 72, 81, 84, 93, 96)
        }.distinct().sorted()
        val list = ArrayList<Take>()
        for (m in notes) {
            // wound strings below the first plain unison: the left string; plain trichords: the centre string
            val s = if (m < firstPlainMidi) StringPos.LEFT else StringPos.CENTRE
            for (k in 1..STRIKES) list += Take(m, s, k)
        }
        for (m in listOf(69, 84, 96)) for (s in listOf(StringPos.LEFT, StringPos.RIGHT)) {
            for (k in 1..STRIKES) list += Take(m, s, k)
        }
        return list
    }

    /** Recording length per note, as the operator set it at the instrument. */
    fun seconds(piano: Piano, midi: Int): Int = when (piano) {
        Piano.STEINWAY_D -> if (midi >= C6) 5 else 20
        Piano.BOESENDORFER -> when {
            midi < C2 -> 10
            midi < C3 -> 12
            midi < C4 -> 10
            midi == C4 -> 8
            else -> 5
        }
    }

    /** "D_A4-C-s2_unproc_20260916-153012.wav" */
    fun fileName(piano: Piano, take: Take, unprocessed: Boolean, stamp: String): String =
        "${piano.code}_${take.id}_${if (unprocessed) "unproc" else "mic"}_$stamp.wav"

    val setup: List<String> = listOf(
        "Place the phone on the music desk, bottom edge (microphone) towards the strings. Leave it there for the whole session.",
        "Lid fully open, room quiet, no pedal.",
        "Mute every string of the note except the one named on the right.",
        "Tap Record. When \"Strike now\" appears, play the key mezzo-forte and hold it down until the recording ends.",
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
