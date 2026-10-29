package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The stretch has no border (29 Oct 2026). On the 225, tuned with the bass at 10:5 and the lowest plain string
 * set to G2, F3-G3 came out under a whole 10:5 one key below G#3 under a whole 4:2, 4-4.5 c flatter than their
 * neighbour's rule, and the major thirds F3-A3 ... G#3-C4 beat 9.79, 10.21, 10.82, 9.28 Hz: falling where they
 * must rise. Here a piano of no one's: B falling through the wound bass and rising up the plain wire, the bass
 * at 10:5 and the plain wire from G2, the settings that did it.
 */
class ContinuousStretchTest {
    private val a4 = 443.0
    private val settings = TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = 43, octaveBass = OctaveType.O10_5)
    private fun b(m: Int) = 0.9e-4 * 2.0.pow((m - 48) / 12.0) + 2.5e-4 * exp(-0.09 * (m - 21))

    private fun sampled(): TuningSession {
        val s = TuningSession(a4, settings)
        for (m in listOf(69, 21, 28, 36, 41, 45, 50, 57, 64, 76, 84)) s.record(measuredString(m, TuningSession.targetF1(m, a4), b(m)))
        s.finishSampling()
        return s
    }

    private fun partial(s: TuningSession, m: Int, k: Int) = k * s.curveTargetF1(m) * Inharmonicity.ratio(k, s.curve.b(m)!!)

    @Test
    fun theMajorThirdsBelowTheTemperamentRiseKeyByKey() {
        val s = sampled()
        val rates = (53..57).map { m -> abs(partial(s, m + 4, 4) - partial(s, m, 5)) }
        for (i in 0 until rates.size - 1)
            assertTrue(rates[i] < rates[i + 1], "the third from ${53 + i} beats ${rates[i]} Hz, the next one up ${rates[i + 1]} Hz: they must rise")
    }

    @Test
    fun noKeyStandsFurtherFromItsNeighbourThanTheStretchCanMoveInOneStep() {
        val s = sampled()
        val off = (50..57).associateWith { TuningSession.centsOff(s.curveTargetF1(it), TuningSession.targetF1(it, a4)) }
        for (m in 50..56)
            assertTrue(abs(off[m]!! - off[m + 1]!!) < 1.0, "midi $m stands ${off[m]} c from ET, ${m + 1} ${off[m + 1]} c: a border")
    }

    @Test
    fun theOuterTypeIsBeatlessAtTheLowestKeyAndTheMiddlesNextToTheTemperament() {
        val s = sampled()
        assertEquals(0.0, TuningSession.centsOff(partial(s, 21, 10), partial(s, 33, 5)), 1e-6, "A0-A1 a beatless 10:5")
        assertEquals(0.0, TuningSession.centsOff(partial(s, 56, 4), partial(s, 68, 2)), 1e-6, "G#3-G#4 a beatless 4:2")
        assertEquals(0.0, TuningSession.centsOff(partial(s, 70, 2), partial(s, 58, 4)), 1e-6, "A#4-A#3 a beatless 4:2")
    }

    @Test
    fun theTemperamentOctaveIsABeatlessOctaveOfTheMiddlesTypeDividedEqually() {
        val s = sampled()
        assertEquals(443.0, s.curveTargetF1(69), 1e-12, "A4 is the reference")
        assertEquals(0.0, TuningSession.centsOff(partial(s, 57, 4), partial(s, 69, 2)), 1e-6, "A3-A4 a beatless 4:2")
        val octave = TuningSession.centsOff(s.curveTargetF1(69), s.curveTargetF1(57))
        for (m in 58..68)
            assertEquals(octave * (m - 57) / 12, TuningSession.centsOff(s.curveTargetF1(m), s.curveTargetF1(57)), 1e-9, "midi $m: an equal step of the stretched octave")
    }
}
