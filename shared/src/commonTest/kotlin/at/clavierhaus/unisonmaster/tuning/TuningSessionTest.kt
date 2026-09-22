package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.measure.PartialMap
import at.clavierhaus.unisonmaster.measure.StringMeasure
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A measured string: f1 as given, partials 1..[maxK] on the stiff-string model with [b], plus [offsetCents] on partial [offsetK]. */
internal fun measuredString(midi: Int, f1: Double, b: Double, maxK: Int = 8, offsetK: Int = 0, offsetCents: Double = 0.0) =
    NoteMeasurement(midi, f1, b, 0.1, (1..maxK).map { k ->
        MeasuredPartial(k, Inharmonicity.centsOf(k, b) + if (k == offsetK) offsetCents else 0.0, -2.0 * k, 0.0)
    })

private fun cents(a: Double, b: Double) = TuningSession.centsOff(a, b)

class TuningSessionTest {
    private val a4 = 443.0
    private val foss = TunerSettings(lowestKeyMidi = 17, lowestUnwoundMidi = 40)    // F0, plain wire from E2
    private val pro = foss.copy(temperamentFirst = false)

    private fun et(midi: Int) = TuningSession.targetF1(midi, a4)

    @Test
    fun theWalkIsA4DownToTheLowestKeyThenUpToTheTop() {
        val s = TuningSession(a4, foss)
        assertEquals((69 downTo 17).toList() + (70..108).toList(), s.notes)
        assertEquals(17, s.stepLowMidi)
        assertEquals(108, s.stepHighMidi)
    }

    @Test
    fun fossWalksTheTemperamentOctaveFirstAndAsksForDoneThere() {
        val s = TuningSession(a4, foss)
        assertTrue(s.gated)
        assertFalse(s.recordsOnLeaving)
        s.record(measuredString(69, a4, 8e-4))
        s.select(40)                                   // any note may be visited ...
        assertEquals(68, s.next())                     // ... Done still walks the octave
        for (m in 68 downTo 57) s.record(measuredString(m, et(m), 6e-4))
        assertTrue(s.temperamentComplete)
        assertTrue(s.recordsOnLeaving)
        s.select(57)
        assertEquals(56, s.next())
    }

    @Test
    fun proRecordsOnLeavingFromTheStart() {
        val s = TuningSession(a4, pro)
        assertTrue(s.recordsOnLeaving)
        s.select(69)
        assertEquals(68, s.next())
    }

    @Test
    fun a4IsTheReferenceAndTheTemperamentOctaveIsEqualTemperamentOnTheFirstPartial() {
        val s = TuningSession(a4, foss)
        val l = s.listening(69)
        assertEquals(1, l.k); assertEquals(a4, l.targetHz); assertEquals(TuningSession.Source.REFERENCE, l.source)
        for (m in 57..68) {
            val t = s.listening(m)
            assertEquals(1, t.k)
            assertEquals(et(m), t.targetHz, 1e-9)
            assertEquals(TuningSession.Source.TEMPERAMENT, t.source)
        }
    }

    @Test
    fun belowTheOctaveTheListenedPartialMeetsThePartnersMeasuredPartial() {
        val s = TuningSession(a4, foss)
        // G#4 measured with its second partial 0.8 cents off its model: the octave
        // below must meet the partial as measured, not as the model has it
        val gs4 = measuredString(68, et(68) * 1.0003, 7e-4, offsetK = 2, offsetCents = 0.8)
        s.record(gs4)
        val l = s.listening(56)
        assertEquals(TuningSession.Source.OCTAVE, l.source)
        assertEquals(OctaveType.O4_2, l.type)
        assertEquals(68, l.refMidi)
        assertEquals(4, l.k)
        assertEquals(gs4.partialHz(2)!!, l.targetHz, 1e-9)
        assertFalse(l.refModelled)
    }

    @Test
    fun theOctaveWidthLowersTheLowerNoteAndRaisesTheUpper() {
        val s = TuningSession(a4, pro.copy(widthMiddle = 2.0, widthTreble = 3.0))
        val gs4 = measuredString(68, et(68), 7e-4)
        s.record(gs4)
        assertEquals(-2.0, cents(s.listening(56).targetHz, gs4.partialHz(2)!!), 1e-9)
        // the treble: A#4 is the upper note of a double octave (4:1) over A#2
        val as2 = measuredString(46, et(46), 2e-4)
        s.record(as2)
        val l = s.listening(70)
        assertEquals(OctaveType.O4_1, l.type)
        assertEquals(46, l.refMidi)
        assertEquals(1, l.k)
        assertEquals(3.0, cents(l.targetHz, as2.partialHz(4)!!), 1e-9)
        assertEquals(3.0, l.widthCents)
    }

    @Test
    fun theRegionsGiveTheOctaveType() {
        val s = TuningSession(a4, pro)
        for (m in listOf(56, 52, 39)) s.record(measuredString(m + 12, et(m + 12), 5e-4))
        assertEquals(OctaveType.O4_2, s.listening(56).type)           // middle
        assertEquals(4, s.listening(56).k)
        assertEquals(OctaveType.O6_3, s.listening(52).type)           // bass: within an octave of E2
        assertEquals(6, s.listening(52).k)
        assertEquals(OctaveType.O6_3, s.listening(39).type)           // wound, below E2
        val wound = TuningSession(a4, pro.copy(octaveWound = OctaveType.O8_4, widthWound = 10.0))
        val e3 = measuredString(51, et(51), 5e-4)
        wound.record(e3)
        val l = wound.listening(39)
        assertEquals(8, l.k)
        assertEquals(-10.0, cents(l.targetHz, e3.partialHz(4)!!), 1e-9)
    }

    @Test
    fun aPartnerNotYetTunedGivesEqualTemperamentOnTheRegistersPartialAndSaysSo() {
        val s = TuningSession(a4, foss)
        s.record(measuredString(69, a4, 8e-4))
        val l = s.listening(40)
        assertEquals(TuningSession.Source.PARTNER_UNTUNED, l.source)
        assertEquals(52, l.refMidi)
        assertEquals(PartialMap.listening(40), l.k)
        val b = s.predictedB(40)
        assertEquals(l.k * et(40) * Inharmonicity.ratio(l.k, b), l.targetHz, 1e-9)
        assertEquals(et(40), s.targetF1Of(40), 1e-9)
    }

    @Test
    fun aPartnerWithoutThatPartialIsPlacedByItsOwnFit() {
        val s = TuningSession(a4, pro)
        val gs4 = measuredString(68, et(68), 7e-4, maxK = 1)
        s.record(gs4)
        val l = s.listening(56)
        assertTrue(l.refModelled)
        assertEquals(2 * gs4.f1Hz * Inharmonicity.ratio(2, 7e-4), l.targetHz, 1e-9)
    }

    @Test
    fun theFirstPartialFollowsFromTheListenedOne() {
        val s = TuningSession(a4, pro)
        s.record(measuredString(68, et(68), 7e-4))
        s.record(measuredString(57, et(57), 4e-4))
        val l = s.listening(56)
        val b = s.predictedB(56)
        assertEquals(4e-4, b, 1e-12, "the nearest measured plain string")
        val f1 = s.targetF1Of(56)
        assertEquals(l.targetHz, 4 * f1 * Inharmonicity.ratio(4, b), 1e-9)
        val p = s.predictedPartials(56)
        assertEquals(l.targetHz, p.first { it.k == 4 }.hz, 1e-9)
    }

    @Test
    fun theInharmonicityOfAWoundStringComesFromAWoundString() {
        val s = TuningSession(a4, pro)
        s.record(measuredString(40, et(40), 1.5e-4))       // E2, plain
        s.record(measuredString(35, et(35), 6e-5))         // B1, wound
        assertEquals(6e-5, s.predictedB(38), 1e-12)
        assertEquals(1.5e-4, s.predictedB(45), 1e-12)
        assertEquals(StringMeasure.DEFAULT_B, TuningSession(a4, pro).predictedB(50))
    }

    @Test
    fun aMeasurementKeepsOnlyPartialsAPlainStringCanHave() {
        val s = TuningSession(a4, pro)
        val m = measuredString(57, et(57), 4e-4, offsetK = 3, offsetCents = -100.0)
        s.record(m)
        assertNull(s.measurements[57]!!.partials.firstOrNull { it.k == 3 }, "a partial a semitone flat is another string's")
        assertEquals(7, s.measurements[57]!!.partials.size)
    }

    @Test
    fun anotherNotesReadingIsNotKeptAndWhatTheNoteHadStays() {
        val s = TuningSession(a4, pro)
        s.record(measuredString(57, et(57), 4e-4))
        s.record(measuredString(57, et(58), 4e-4))      // A#3 still ringing, recorded as A3
        assertEquals(et(57), s.measurements[57]!!.f1Hz, 1e-9)
    }

    @Test
    fun aNewReferenceRetargetsEveryNote() {
        val s = TuningSession(a4, pro)
        s.a4Hz = 440.0
        assertEquals(TuningSession.targetF1(60, 440.0), s.listening(60).targetHz, 1e-9)
    }

    @Test
    fun aMeasuredPartialIsWhereItWasMeasured() {
        val m = measuredString(57, 220.0, 4e-4)
        assertEquals(4 * 220.0 * 2.0.pow(Inharmonicity.centsOf(4, 4e-4) / 1200), m.partialHz(4)!!, 1e-9)
        assertTrue(abs(m.partialHz(4)!! - 4 * 220.0 * Inharmonicity.ratio(4, 4e-4)) < 1e-9)
        assertNull(m.partialHz(9))
    }
}
