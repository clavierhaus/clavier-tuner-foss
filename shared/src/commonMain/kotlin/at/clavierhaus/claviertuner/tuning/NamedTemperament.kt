package at.clavierhaus.claviertuner.tuning

import kotlin.math.ln

/**
 * The temperament a tuning is laid out in: equal, or one of the historical temperaments that carry a name. Its own type
 * beside [Temperament], the strategy interface of the first commit, which [EqualTemperament] fills and TuningController
 * keeps (22 Nov 2026: the first version of this file replaced that interface and broke the build) (Peter,
 * 22 Nov 2026, 22:27: "it's all math and you can look up all those non-equal temperaments that have a name put on
 * them").
 *
 * It measures nothing. Each is its definition, the way its author gave it: which fifths of the circle are narrowed and by
 * what share of which comma, the rest pure; the last fifth of the circle takes what is left, so the circle closes (the
 * wolf of meantone, G#-Eb). From that the code works out every note's place; no table of cents is typed in. The
 * definitions, read 22 Nov 2026:
 * - Werckmeister III: C-G, G-D, D-A and B-F# by 1/4 Pythagorean comma (en.wikipedia.org/wiki/Werckmeister_temperament).
 * - Kirnberger III: C-G, G-D, D-A, A-E by 1/4 syntonic comma, F#-C# by the schisma (en.wikipedia.org/wiki/
 *   Kirnberger_temperament, its table: F#-C# 700.001 c).
 * - Vallotti: F-C, C-G, G-D, D-A, A-E, E-B by 1/6 Pythagorean comma (en.wikipedia.org/wiki/Vallotti_temperament).
 * - Young II: the same six from C on, C-G to B-F# (the same page).
 * - Kellner: C-G, G-D, D-A, A-E and B-F# by 1/5 Pythagorean comma (tuning.ableton.com, "12 WT (Kellner)").
 * - Quarter-comma meantone: every fifth by 1/4 syntonic comma, the wolf on G#-Eb (en.wikipedia.org/wiki/Meantone_temperament).
 *
 * A note's offset is how far it stands from equal temperament in cents, A at 0, so A4 stays the reference and the
 * offsets are laid over the stretch the way an edition's offset is (TuningSession.offset): every octave of a note carries
 * the same offset.
 */
enum class NamedTemperament(private val narrowed: Map<String, Double>, private val wolfAfter: String? = null) {
    EQUAL(emptyMap()),
    WERCKMEISTER_III(listOf("C", "G", "D", "B").associateWith { Commas.PC / 4 }),
    KIRNBERGER_III(listOf("C", "G", "D", "A").associateWith { Commas.SC / 4 } + ("F#" to Commas.SCHISMA)),
    VALLOTTI(listOf("F", "C", "G", "D", "A", "E").associateWith { Commas.PC / 6 }),
    YOUNG_II(listOf("C", "G", "D", "A", "E", "B").associateWith { Commas.PC / 6 }),
    KELLNER(listOf("C", "G", "D", "A", "B").associateWith { Commas.PC / 5 }),
    MEANTONE_QUARTER(Commas.CIRCLE.filter { it != "G#" }.associateWith { Commas.SC / 4 }, wolfAfter = "G#");

    /**
     * The offsets from equal temperament, cents, by pitch class from C (0) to B (11), A at 0. The fifths start on C and
     * go round the circle; the fifth from [wolfAfter] (else from F, the last) takes what closes it.
     */
    val offsets: DoubleArray by lazy {
        if (narrowed.isEmpty()) return@lazy DoubleArray(12)
        val last = wolfAfter ?: Commas.CIRCLE.last()
        val placed = DoubleArray(12)
        var at = 0.0                        // cents above C, in the octave
        var sumOthers = 0.0
        for (name in Commas.CIRCLE) {
            if (name == last) continue
            sumOthers += Commas.PURE_FIFTH - (narrowed[name] ?: 0.0)
        }
        for (name in Commas.CIRCLE) {
            placed[Commas.PITCH_CLASS.getValue(name)] = at
            val fifth = if (name == last) 7 * 1200.0 - sumOthers else Commas.PURE_FIFTH - (narrowed[name] ?: 0.0)
            at = (at + fifth) % 1200.0
        }
        val fromEqual = DoubleArray(12) { pc -> placed[pc] - 100.0 * pc }
        val a = fromEqual[9]
        DoubleArray(12) { fromEqual[it] - a }
    }

    /** [midi]'s offset from equal temperament, cents, A at 0. */
    fun offsetCents(midi: Int): Double = offsets[((midi % 12) + 12) % 12]

    companion object {
        /** The circle of fifths from C, each the lower note of the fifth that follows it. */
        val CIRCLE: List<String> get() = Commas.CIRCLE
    }
}

/**
 * The commas and the circle, apart from the enum: an enum's entries are built before its companion object, so constants
 * kept there would still be zero while the entries read them.
 */
private object Commas {
    val CIRCLE = listOf("C", "G", "D", "A", "E", "B", "F#", "C#", "G#", "D#", "A#", "F")
    val PITCH_CLASS = mapOf("C" to 0, "C#" to 1, "D" to 2, "D#" to 3, "E" to 4, "F" to 5, "F#" to 6,
        "G" to 7, "G#" to 8, "A" to 9, "A#" to 10, "B" to 11)
    private fun cents(ratio: Double) = 1200.0 * ln(ratio) / ln(2.0)
    val PURE_FIFTH = cents(1.5)
    /** The Pythagorean comma: twelve pure fifths over seven octaves. */
    val PC = 12 * PURE_FIFTH - 7 * 1200.0
    /** The syntonic comma, 81/80. */
    val SC = cents(81.0 / 80.0)
    /** The schisma, the Pythagorean comma less the syntonic. */
    val SCHISMA = PC - SC
}
