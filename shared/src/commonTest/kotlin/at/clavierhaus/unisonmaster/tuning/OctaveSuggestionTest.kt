package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.settings.OctaveType
import kotlin.math.log2
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bass's octave type suggested from the sampling (Peter, 10 Nov 2026): the type that leaves the smallest hardest beat
 * between two sampled keys an octave apart, every partial within [OctaveSuggestion.LEVEL_DB] of its own string's
 * loudest counting.
 */
class OctaveSuggestionTest {
    /** A string sampled: partials 1..12 where a stiff string puts them, each at the level [level] gives it. */
    private fun string(midi: Int, b: Double, level: (Int) -> Double): NoteMeasurement {
        val f1 = TuningSession.targetF1(midi, 440.0)
        val partials = (1..12).map { k -> MeasuredPartial(k, 1200 * log2(sqrt((1 + b * k * k) / (1 + b))), level(k), 0.0) }
        return NoteMeasurement(midi, f1, b, 0.0, partials)
    }

    @Test
    fun loudHighPartialsInAStiffBassCallFor10to5() {
        // a short upright's bass: B high, partials up to 12 within a few dB of the loudest
        val samples = listOf(string(33, 3.4e-4) { -6.0 }, string(45, 1.6e-4) { -6.0 }, string(57, 2.4e-4) { -6.0 })
        val s = assertNotNull(OctaveSuggestion.bass(samples, temperamentLowMidi = 57))
        assertEquals(OctaveType.O10_5, s.type, "hardest beats ${s.hardestHz}")
        assertTrue(s.hardestHz.getValue(OctaveType.O10_5) < s.hardestHz.getValue(OctaveType.O6_3))
        assertEquals(2, s.octaves, "A1-A2 and A2-A3")
    }

    @Test
    fun whereOnlyTheLowPartialsAreLoudTheMiddleTypeLeavesLessAndIsSuggested() {
        // above partial 6 everything stands 30 dB down: only 2:1, 4:2 and 6:3 count, and 10:5 would set them beating
        val samples = listOf(string(33, 3.4e-4) { k -> if (k <= 6) -3.0 else -30.0 }, string(45, 1.6e-4) { k -> if (k <= 6) -3.0 else -30.0 })
        val s = assertNotNull(OctaveSuggestion.bass(samples, temperamentLowMidi = 57))
        assertTrue(s.type != OctaveType.O10_5, "suggested ${s.type}, hardest beats ${s.hardestHz}")
    }

    @Test
    fun noTwoSampledBassKeysAnOctaveApartNoSuggestion() {
        val samples = listOf(string(33, 3.4e-4) { -6.0 }, string(43, 1.6e-4) { -6.0 }, string(69, 4e-4) { -6.0 })
        assertNull(OctaveSuggestion.bass(samples, temperamentLowMidi = 57))
    }

    @Test
    fun aTypeWhosePartialsWereNotMeasuredIsNotSuggestedOnThatOctave() {
        // partial 10 of the lower key not measured: 10:5 cannot be set on this octave, so it is not weighed there
        val lower = string(33, 3.4e-4) { -6.0 }.let { m -> m.copy(partials = m.partials.filter { it.k != 10 }) }
        val s = assertNotNull(OctaveSuggestion.bass(listOf(lower, string(45, 1.6e-4) { -6.0 }), temperamentLowMidi = 57))
        assertTrue(OctaveType.O10_5 !in s.hardestHz, "10:5 weighed without its partial 10: ${s.hardestHz}")
    }

    @Test
    fun takingTheSuggestionSetsTheBassAndItsPlacesAndNothingElse() {
        val six = at.clavierhaus.unisonmaster.settings.TunerSettings().withSixPoints(true)
        val taken = six.withBassOctave(OctaveType.O10_5)
        assertEquals(OctaveType.O10_5, taken.octaveBass)
        assertEquals(listOf(OctaveType.O10_5, OctaveType.O2_1, OctaveType.O4_1), taken.stretchPoints.sortedBy { it.midi }.map { it.type })
        assertEquals(six.copy(octaveBass = OctaveType.O10_5, stretchPoints = taken.stretchPoints), taken, "widths, middle and treble untouched")
    }
}
