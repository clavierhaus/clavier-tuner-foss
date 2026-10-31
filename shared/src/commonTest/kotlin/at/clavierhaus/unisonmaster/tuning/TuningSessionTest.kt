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
    private val foss = TunerSettings(lowestKeyMidi = 17, lowestUnwoundMidi = 40, temperamentFirst = true)    // F0, plain wire from E2; the temperament octave walked first — the route only, since 4 Oct 2026 it samples like any other
    private val pro = foss.copy(temperamentFirst = false)

    private fun et(midi: Int) = TuningSession.targetF1(midi, a4)

    @Test
    fun theWalkIsA4DownToTheLowestKeyThenUpToTheTop() {
        val s = TuningSession(a4, foss, sampling = false)
        assertEquals((69 downTo 17).toList() + (70..108).toList(), s.notes)
        assertEquals(17, s.stepLowMidi)
        assertEquals(108, s.stepHighMidi)
    }

    @Test
    fun fossWalksTheTemperamentOctaveFirstAndAsksForDoneThere() {
        val s = TuningSession(a4, foss, sampling = false)
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
        val s = TuningSession(a4, pro, sampling = false)
        assertTrue(s.recordsOnLeaving)
        s.select(69)
        assertEquals(68, s.next())
    }

    @Test
    fun a4IsTheReferenceAndTheTemperamentOctaveIsEqualTemperamentOnTheFirstPartial() {
        val s = TuningSession(a4, foss, sampling = false)
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
        val s = TuningSession(a4, foss, sampling = false)
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
        // the treble as 4:1 here, chosen: the double octave is the case that reaches A#2 (the default is 4:2 since 4 Oct 2026)
        val s = TuningSession(a4, pro.copy(widthMiddle = 2.0, widthTreble = 3.0, octaveTreble = OctaveType.O4_1), sampling = false)
        val gs4 = measuredString(68, et(68), 7e-4)
        s.record(gs4)
        assertEquals(-2.0, cents(s.listening(56).targetHz, gs4.partialHz(2)!!), 1e-9)
        // the treble: C7, past half way to C8, is read as the upper note of a double octave (4:1) over C5; without a
        // curve, on the octave alone, widened by the width blended to its place (29 Oct 2026)
        val c5 = measuredString(72, et(72), 0.0)          // B unknown: the curve does not stand on it
        s.record(c5)
        val l = s.listening(96)
        assertEquals(OctaveType.O4_1, l.type)
        assertEquals(72, l.refMidi)
        assertEquals(1, l.k)
        val width = 2.0 + (3.0 - 2.0) * 26.0 / 38
        assertEquals(width, cents(l.targetHz, c5.partialHz(4)!!), 1e-9)
        assertEquals(width, l.widthCents, 1e-12)
    }

    @Test
    fun theNoteIsReadOnTheOctaveTypeWithTheLargerShare() {
        // F0 to G#3 is 39 steps (pro's lowest key): up to half way the middle's 4:2, beyond it the bass's
        val s = TuningSession(a4, pro, sampling = false)
        for (m in listOf(56, 52, 30)) s.record(measuredString(m + 12, et(m + 12), 5e-4))
        assertEquals(OctaveType.O4_2, s.listening(56).type); assertEquals(4, s.listening(56).k)
        assertEquals(OctaveType.O4_2, s.listening(52).type, "E3, an octave above E2: no border there any more (29 Oct 2026)")
        assertEquals(OctaveType.O6_3, s.listening(30).type); assertEquals(6, s.listening(30).k)
        // without a curve, the octave alone to its measured partner, widened by the width blended to its place
        val bass = TuningSession(a4, pro.copy(octaveBass = OctaveType.O8_4, widthBass = 10.0), sampling = false)
        val ds2 = measuredString(39, et(39), 5e-4)
        bass.record(ds2)
        val l = bass.listening(27)
        assertEquals(8, l.k)
        assertEquals(-10.0 * 29.0 / 39, cents(l.targetHz, ds2.partialHz(4)!!), 1e-9)
    }

    @Test
    fun aPartnerNotYetTunedGivesEqualTemperamentOnTheRegistersPartialAndSaysSo() {
        val s = TuningSession(a4, foss, sampling = false)
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
        val s = TuningSession(a4, pro, sampling = false)
        val gs4 = measuredString(68, et(68), 7e-4, maxK = 1)
        s.record(gs4)
        val l = s.listening(56)
        assertTrue(l.refModelled)
        assertEquals(2 * gs4.f1Hz * Inharmonicity.ratio(2, 7e-4), l.targetHz, 1e-9)
    }

    @Test
    fun theFirstPartialFollowsFromTheListenedOne() {
        val s = TuningSession(a4, pro, sampling = false)
        s.record(measuredString(68, et(68), 7e-4))
        s.record(measuredString(57, et(57), 0.0))       // B unknown: no curve yet
        val l = s.listening(56)
        val b = s.predictedB(56)
        assertEquals(7e-4, b, 1e-12, "the nearest measured plain string with a B")
        val f1 = s.targetF1Of(56)
        assertEquals(l.targetHz, 4 * f1 * Inharmonicity.ratio(4, b), 1e-9)
        val p = s.predictedPartials(56)
        assertEquals(l.targetHz, p.first { it.k == 4 }.hz, 1e-9)
    }

    @Test
    fun beforeTheCurveOneStringStandsInAndThenTheLineRunsThroughTheFloor() {
        val s = TuningSession(a4, pro, sampling = false)
        assertEquals(StringMeasure.DEFAULT_B, s.predictedB(50))
        s.record(measuredString(35, et(35), 6e-5))         // B1, wound: the only string, it stands in
        assertEquals(6e-5, s.predictedB(45), 1e-12)
        s.record(measuredString(40, et(40), 1.5e-4))       // E2, plain: two strings, a line, no cut between them (29 Oct 2026)
        val b38 = s.predictedB(38)
        assertTrue(b38 > 6e-5 && b38 < 1.5e-4, "D2 on the line between B1 and E2: $b38")
    }

    @Test
    fun aMeasurementKeepsOnlyPartialsAPlainStringCanHave() {
        val s = TuningSession(a4, pro, sampling = false)
        val m = measuredString(57, et(57), 4e-4, offsetK = 3, offsetCents = -100.0)
        s.record(m)
        assertNull(s.measurements[57]!!.partials.firstOrNull { it.k == 3 }, "a partial a semitone flat is another string's")
        assertEquals(7, s.measurements[57]!!.partials.size)
    }

    @Test
    fun anotherNotesReadingIsNotKeptAndWhatTheNoteHadStays() {
        val s = TuningSession(a4, pro, sampling = false)
        s.record(measuredString(57, et(57), 4e-4))
        s.record(measuredString(57, et(58), 4e-4))      // A#3 still ringing, recorded as A3
        assertEquals(et(57), s.measurements[57]!!.f1Hz, 1e-9)
    }

    @Test
    fun aNewReferenceRetargetsEveryNote() {
        val s = TuningSession(a4, pro, sampling = false)
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

    // ---- sampling and the curve ----

    @Test
    fun aSampleTakenBackLeavesTheSamplingAndA4StaysTheReference() {
        val s = TuningSession(a4, pro)
        s.record(measuredString(69, a4, 8e-4))
        for (m in listOf(24, 33, 40, 45, 52, 64, 76, 93)) s.record(measuredString(m, et(m), 1e-4 * 2.0.pow((m - 40) / 12.0)))
        assertTrue(s.samplingReady)
        assertTrue(s.forget(33), "a sampled string is taken back")
        assertFalse(33 in s.measurements); assertEquals(7, s.samplesIn); assertFalse(s.samplingReady, "seven again: Done waits")
        assertFalse(s.forget(33), "nothing left to take back")
        assertFalse(s.forget(69), "A4 is the reference, not a sample")
        s.record(measuredString(33, et(33), 8e-5))
        assertTrue(s.samplingReady, "struck anew, it counts again")
        s.finishSampling()
        assertFalse(s.forget(52), "the tuning has begun: nothing is taken back")
    }

    /**
     * An edition's offset over the targets (Pro's pitch raise, 24 Oct 2026): every target read on the
     * note moves by exactly its cents, the stretch itself does not, notes without one stand as computed,
     * and a partner's check is read against where the partner was asked to stand, not against the curve.
     */
    @Test
    fun anOffsetMovesTheNotesTargetAndNothingElse() {
        val s = TuningSession(a4, TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = 40))
        s.record(measuredString(69, a4, 8e-4))
        for (m in listOf(24, 33, 45, 52, 76, 93)) s.record(measuredString(m, et(m), 1e-4 * 2.0.pow((m - 40) / 12.0)))
        s.finishSampling()
        val before = (21..108).associateWith { s.listening(it).targetHz }
        val curve = (21..108).associateWith { s.curveTargetF1(it) }
        s.targetOffsetCents = mapOf(45 to 6.0, 69 to 4.0)
        assertEquals(6.0, cents(s.listening(45).targetHz, before.getValue(45)), 1e-9)
        assertEquals(4.0, cents(s.listening(69).targetHz, before.getValue(69)), 1e-9)
        for (m in 21..108) if (m != 45 && m != 69) assertEquals(before.getValue(m), s.listening(m).targetHz, 1e-9, "key $m has no offset")
        for (m in 21..108) assertEquals(curve.getValue(m), s.curveTargetF1(m), 1e-9, "the stretch stands, key $m")
        // A2 (45) tuned where it was asked to stand: its octave below, A1 (33), checks clean against it
        val a2 = s.listening(45)
        s.record(measuredString(45, a2.targetHz / (a2.k * Inharmonicity.ratio(a2.k, s.predictedB(45))), s.predictedB(45)))
        s.listening(33).checkCents?.let { assertEquals(0.0, it, 0.05, "the partner checked against its offset target") }
        s.targetOffsetCents = emptyMap()
        for (m in 21..108) if (m != 45) assertEquals(before.getValue(m), s.listening(m).targetHz, 1e-9, "offsets taken off, key $m")
    }

    /**
     * A sampled string is the piano as found, not a note tuned (Peter, 25 Oct 2026, the 1970 B: the
     * progress line in the treble was the sampling's, under a caption saying "as tuned"). A4 is tuned
     * before the sampling, to the reference, and counts; every other note counts once it is recorded
     * in the tuning. The temperament octave sampled is not the temperament octave done.
     */
    @Test
    fun aSampledStringIsNotATunedNote() {
        val s = TuningSession(a4, foss)
        s.record(measuredString(69, a4, 8e-4))
        for (m in listOf(24, 57, 60, 64, 76, 93)) s.record(measuredString(m, et(m) * 0.995, 1e-4 * 2.0.pow((m - 40) / 12.0)))
        for (m in s.temperamentNotes) s.record(measuredString(m, et(m) * 0.995, 4e-4))
        s.finishSampling()
        assertEquals(setOf(69), s.tuned, "after the sampling only A4 is tuned")
        assertFalse(s.temperamentComplete, "the temperament octave sampled is not tuned")
        assertTrue(s.gated, "the temperament octave is still walked first")
        assertEquals(s.notes.first { it != 69 }, s.nextUntuned(), "nothing but A4 is done")
        s.record(measuredString(60, et(60), 4e-4))
        assertEquals(setOf(60, 69), s.tuned, "C4 recorded in the tuning is tuned")
        s.keep(s.measurements.getValue(76))
        assertFalse(76 in s.tuned, "a fact kept on a sampled string does not tune it")
        // a session that never sampled: whatever is recorded is tuned
        val plain = TuningSession(a4, foss, sampling = false)
        plain.record(measuredString(60, et(60), 4e-4))
        assertEquals(setOf(60), plain.tuned)
    }

    @Test
    fun aNewSessionSamplesThenTunes() {
        // one sampling law (4 Oct 2026): any eight strings besides A4 (six until 30 Oct), where the tuner sees fit —
        // the same whether he then walks the temperament octave first or not
        for (way in listOf(foss, pro)) {
            val s = TuningSession(a4, way)
            assertTrue(s.sampling)
            assertFalse(s.recordsOnLeaving)
            assertEquals(TunerSettings.MIN_SAMPLES, s.samplesNeeded, "no set is prescribed, temperamentFirst=${way.temperamentFirst}")
            s.record(measuredString(69, a4, 8e-4))
            assertFalse(s.samplingReady)
            // none of these is in the temperament octave; none was in the set of 25 Sept but C1 and A6
            for (m in listOf(24, 33, 40, 45, 52, 64, 76)) s.record(measuredString(m, et(m), 1e-4 * 2.0.pow((m - 40) / 12.0)))
            assertFalse(s.samplingReady, "seven in, eight needed"); assertEquals(7, s.samplesIn)
            s.record(measuredString(93, et(93), 3e-3))
            assertTrue(s.samplingReady, "eight strings, wherever they stand, end the sampling")
            s.finishSampling()
            assertFalse(s.sampling)
            assertTrue(s.curve.ready)
            assertEquals(68, s.current, "the tuning begins at A4's neighbour")
        }
    }

    /**
     * The inharmonicity is measured once, while sampling, and is the basis of every target until
     * the session ends (Peter, 11 Oct 2026): "What we measure initially in the sampling is a PITCH
     * INDEPENDENT measurement and it is written in stone. ... I start tuning C2 according to initial
     * sampling and then tune 20 notes all over the place -- the calculated pitch of C2 needs to be
     * where we tuned it, to a 100th of a cent." Until then every note tuned was recorded into the
     * table the curve is fitted to, and moved the targets of notes already tuned.
     */
    @Test
    fun theSamplingIsTheBasisOfEveryTargetAndTuningDoesNotMoveIt() {
        val s = TuningSession(a4, pro)
        s.record(measuredString(69, a4, 8e-4))
        val sampled = listOf(21, 28, 33, 38, 45, 52, 60, 76, 88, 96)
        for (m in sampled) s.record(measuredString(m, et(m), 1e-4 * 2.0.pow((m - 40) / 12.0)))
        s.finishSampling()
        val watched = listOf(24, 36, 53, 84)                 // C1, C2, F3, C6
        val before = watched.associateWith { s.listening(it).targetHz }
        // twenty notes tuned all over the place, each string measured at 1.6 times the sampled
        // curve's inharmonicity -- and two of the sampled strings struck again, measured differently
        for (m in listOf(23, 25, 27, 30, 31, 34, 36, 37, 40, 43, 47, 50, 53, 57, 64, 72, 80, 84, 90, 100))
            s.record(measuredString(m, et(m), 1.6 * s.curve.b(m)!!))
        for (m in listOf(28, 52)) s.record(measuredString(m, et(m), 0.7e-4 * 2.0.pow((m - 40) / 12.0)))
        for (m in watched) {
            val now = s.listening(m).targetHz
            assertEquals(0.0, cents(now, before[m]!!), 1e-4, "midi $m: its target moved after the sampling")
        }
    }

    @Test
    fun theCurveIsOneSmoothLineAndBreaksOnlyWhereTheTunerMarksOne() {
        // a part rising up the plain wire and a part falling down the wound bass, sampled at nine keys
        fun b(m: Int) = 1.2e-4 * kotlin.math.exp(0.06 * (m - 60)) + 2.0e-4 * kotlin.math.exp(-0.08 * (m - 21))
        val samples = listOf(21, 28, 36, 41, 45, 50, 57, 64, 76).associateWith { b(it) }
        val c = InharmonicityCurve(samples)
        assertTrue(c.ready)
        for (m in listOf(24, 33, 39, 43, 48, 53, 60, 70))
            assertEquals(1.0, c.b(m)!! / b(m), 0.02, "midi $m: between samples, on the line")
        assertEquals(1.0, c.b(96)!! / b(96), 0.05, "beyond the top, on the line")
        // no cut at the lowest plain string any more (29 Oct 2026): the line runs through it
        assertEquals(1.0, c.b(43)!! / c.b(42)!!, 0.06, "G2 against F#2: one step of the same line")
        // a break the tuner marks at C4 splits it: below it, one flat run; above, another
        val split = (40..56 step 4).associateWith { 2e-4 } + (60..96 step 4).associateWith { 8e-4 }
        val d = InharmonicityCurve(split, breaks = setOf(60))
        assertEquals(2e-4, d.b(58)!!, 1e-9); assertEquals(8e-4, d.b(61)!!, 1e-9)
    }

    @Test
    fun withTheCurveTheTargetIsComputedAndThePartnerIsTheCheck() {
        val s = TuningSession(a4, pro.copy(widthMiddle = 1.0), sampling = false)
        for (m in listOf(40, 52, 64, 76, 88)) s.record(measuredString(m, et(m), 1e-4 * 2.0.pow((m - 40) / 12.0)))
        assertTrue(s.curve.ready)
        val l = s.listening(56)
        assertEquals(TuningSession.Source.CURVE, l.source)
        assertEquals(4, l.k); assertEquals(68, l.refMidi); assertEquals(1.0, l.widthCents)
        assertEquals(null, l.checkCents, "G#4 not tuned: nothing to check against")
        // the chain: G#4's second partial as the curve models it, a cent wide
        val bGs4 = s.curve.b(68)!!
        val gs4p2 = 2 * s.curveTargetF1(68) * Inharmonicity.ratio(2, bGs4)
        assertEquals(gs4p2 * 2.0.pow(-1.0 / 1200), l.targetHz, 1e-6)
        // G#4 tuned a cent sharp of the curve's place: the check says so
        s.record(measuredString(68, s.curveTargetF1(68) * 2.0.pow(1.0 / 1200), bGs4))
        val l2 = s.listening(56)
        assertEquals(l.targetHz, l2.targetHz, 1e-6, "the curve stays the target")
        assertEquals(-1.0, l2.checkCents!!, 0.05, "the partner stands a cent sharp: the octave to it a cent narrower")
        // the treble chains upward the same way: A5, under half way to C8, read on the middle's 4:2 over A4;
        // C8 on the treble's 4:1 over C6 (the default since 23 Oct 2026)
        val t = s.listening(81)
        assertEquals(TuningSession.Source.CURVE, t.source); assertEquals(69, t.refMidi); assertEquals(OctaveType.O4_2, t.type)
        val top = s.listening(108)
        assertEquals(84, top.refMidi); assertEquals(OctaveType.O4_1, top.type)
    }
}

/**
 * The twelfth, 3:1, as an octave type (Peter, 23 Oct 2026, at the Steinway B: "in the treble I would
 * go for 4:1 or 3:1, the 3:1 is missing"): a treble note's partial 1 meets partial 3 of the note a
 * twelfth below, chained from the temperament as every type is. On the 225 as tuned it stood
 * -1.04 c (median) from the treble, against +0.48 c for 4:1 (SAMICK findings §32, 19 Oct 2026).
 */
class TwelfthOctaveTypeTest {
    @Test
    fun aTrebleNoteOnTheTwelfthMeetsPartialThreeOfTheNoteATwelfthBelow() {
        val a4 = 443.0
        val s = TuningSession(a4, TunerSettings(lowestUnwoundMidi = 40, octaveTreble = OctaveType.O3_1))
        for ((m, b) in listOf(69 to 4e-4, 36 to 1.5e-4, 48 to 2e-4, 57 to 3e-4, 60 to 3.2e-4, 65 to 3.6e-4, 84 to 9e-4, 96 to 2e-3))
            s.record(measuredString(m, TuningSession.targetF1(m, a4), b))
        s.finishSampling()
        assertEquals(Triple(89, 1, 3), s.partner(108), "C8's partner on the twelfth is F6, its partial 3")
        val c = s.curve
        val c8 = s.curveTargetF1(108, c)
        val f6p3 = 3 * s.curveTargetF1(89, c) * Inharmonicity.ratio(3, c.b(89)!!)
        assertEquals(0.0, TuningSession.centsOff(c8 * Inharmonicity.ratio(1, c.b(108)!!), f6p3), 0.01, "C8's partial 1 on F6's partial 3: the twelfth beatless at the top (29 Oct 2026: blended below it)")
    }

}

class OneBandTest {
    private val a4 = 443.0
    private val pro = TunerSettings(lowestKeyMidi = 17, lowestUnwoundMidi = 40)

    /**
     * One band (TunerSettings.strobeOne, Gregor and Peter, 28 Oct 2026): the note is read on the partial on
     * which it meets its octave by the region's octave type, and A4 and the temperament octave on partial 2
     * (4:2, "the most neutral one"), at the place the same target puts it. Off, as before: A4 and the
     * temperament on the fundamental, an untuned partner's note on the register's map.
     */
    @Test
    fun oneBandReadsTheOctavesPartialAndPartial2InTheTemperament() {
        val off = TuningSession(a4, pro, sampling = false)
        val on = TuningSession(a4, pro.copy(strobeMode = at.clavierhaus.unisonmaster.settings.StrobeMode.ONE), sampling = false)
        // A4 and a note of the temperament octave: the fundamental off, partial 2 on, the same target pitch
        for (m in listOf(69, 60)) {
            assertEquals(1, off.listening(m).k)
            assertEquals(2, on.listening(m).k)
            val f1 = off.listening(m).targetHz
            assertEquals(2 * f1 * Inharmonicity.ratio(2, on.predictedB(m)), on.listening(m).targetHz, 1e-9)
        }
        // E5, under half way to C8, is read on the middle's 4:2 (29 Oct 2026): the upper note's partial 2;
        // C7, past half way, on the treble's 4:1 (the default), partner untuned: the map says partial 1 here too
        assertEquals(PartialMap.listening(76), off.listening(76).k)
        assertEquals(OctaveType.O4_2, on.listening(76).type)
        assertEquals(2, on.listening(76).k)
        assertEquals(OctaveType.O4_1, on.listening(96).type)
        assertEquals(1, on.listening(96).k)
        // a treble set out as 4:2: the upper note's partial 2
        val on42 = TuningSession(a4, pro.copy(strobeMode = at.clavierhaus.unisonmaster.settings.StrobeMode.ONE, octaveTreble = OctaveType.O4_2), sampling = false)
        assertEquals(2, on42.listening(76).k)
    }
}
