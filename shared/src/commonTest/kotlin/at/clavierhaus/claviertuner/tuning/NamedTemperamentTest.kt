package at.clavierhaus.claviertuner.tuning

import at.clavierhaus.claviertuner.settings.TunerSettings
import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The named temperaments (22 Nov 2026): worked out from their definitions, they give the places their sources print,
 * A stays the reference, equal temperament changes nothing, and they are laid over the stretch with every octave of a
 * note at the same offset.
 */
class NamedTemperamentTest {
    private val a4 = 443.0
    private fun b(m: Int) = 0.9e-4 * 2.0.pow((m - 48) / 12.0) + 2.5e-4 * exp(-0.09 * (m - 21))

    /** The note's place above C, cents, from the offsets (A at 0) and equal temperament. */
    private fun aboveC(t: NamedTemperament, pc: Int) = 100.0 * pc + t.offsets[pc] - t.offsets[0]

    @Test
    fun werckmeisterIIIStandsWhereItsSourcePrintsIt() {
        // en.wikipedia.org/wiki/Werckmeister_temperament, read 22 Nov 2026: C 0, C# 90.225, D 192.180 ... B 1092.180
        val printed = doubleArrayOf(0.0, 90.225, 192.180, 294.135, 390.225, 498.045, 588.270, 696.090, 792.180, 888.270, 996.090, 1092.180)
        for (pc in 0..11) assertEquals(printed[pc], aboveC(NamedTemperament.WERCKMEISTER_III, pc), 0.001, "pitch class $pc")
    }

    @Test
    fun kirnbergerIIIHasAPureThirdCEAndItsSchismaOnFSharpCSharp() {
        val t = NamedTemperament.KIRNBERGER_III
        assertEquals(1200.0 * kotlin.math.ln(5.0 / 4.0) / kotlin.math.ln(2.0), aboveC(t, 4), 0.001, "C-E pure")
        // the article's table: F#-C# 700.001 c
        assertEquals(700.001, aboveC(t, 1) + 1200.0 - aboveC(t, 6), 0.002, "F#-C#")
    }

    @Test
    fun meantoneHasItsWolfOnGSharpEFlat() {
        val t = NamedTemperament.MEANTONE_QUARTER
        val wolf = aboveC(t, 3) + 1200.0 - aboveC(t, 8)
        assertEquals(737.637, wolf, 0.01, "G#-Eb")
    }

    @Test
    fun aIsZeroInEveryTemperamentAndEqualIsZeroEverywhere() {
        for (t in NamedTemperament.entries) assertEquals(0.0, t.offsetCents(69), 1e-9, "$t")
        for (pc in 0..11) assertEquals(0.0, NamedTemperament.EQUAL.offsets[pc], 1e-12)
    }

    private fun sampled(settings: TunerSettings): TuningSession {
        val s = TuningSession(a4, settings)
        for (m in listOf(69, 21, 28, 36, 41, 45, 50, 57, 64, 76, 84)) s.record(measuredString(m, TuningSession.targetF1(m, a4), b(m)))
        s.finishSampling()
        return s
    }

    @Test
    fun aTemperamentMovesEveryKeyByItsNoteNamesOffsetOverTheSameStretch() {
        val equal = sampled(TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = 43))
        val werck = sampled(TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = 43, temperament = NamedTemperament.WERCKMEISTER_III))
        for (m in 21..108) {
            val off = NamedTemperament.WERCKMEISTER_III.offsetCents(m)
            assertEquals(off, TuningSession.centsOff(werck.listening(m).targetHz, equal.listening(m).targetHz), 1e-6, "midi $m")
            assertEquals(equal.curveTargetF1(m), werck.curveTargetF1(m), 1e-9, "the stretch itself, midi $m")
        }
    }
}
