package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SR = 48_000
private const val HOP = 4096

/** A struck stiff string: partials 1..[count] at k·f0·√(1+B·k²), amplitude 1/k, slow decay. */
private fun stiffStrike(f0: Double, b: Double, count: Int, seconds: Double): FloatArray {
    val rnd = Random(11)
    return FloatArray((seconds * SR).toInt()) { i ->
        val t = i.toDouble() / SR
        var v = 0.0
        for (k in 1..count) v += 0.2 / k * sin(2 * PI * k * f0 * sqrt(1 + b * k * k) * t + k)
        (v * exp(-t / 4.0) + 1e-4 * (rnd.nextDouble() * 2 - 1)).toFloat()
    }
}

/** f0 that makes the sounding first partial land on [f1]. */
private fun f0For(f1: Double, b: Double) = f1 / sqrt(1 + b)

private fun exact(b: Double, ks: IntRange, noise: Double = 0.0): List<MeasuredPartial> {
    val rnd = Random(3)
    return ks.map { k ->
        MeasuredPartial(k, Inharmonicity.centsOf(k, b) + noise * (rnd.nextDouble() * 2 - 1), -2.0 * k, 3.0)
    }
}

/** Plays one prepared signal per start(), in order. */
private class QueueSource(initial: List<FloatArray>) : AudioSource {
    private val signals = initial.toMutableList()
    override val sampleRateHz: Int = SR
    private var next = 0
    fun add(signal: FloatArray) { signals.add(signal) }
    fun clear() { signals.clear(); next = 0 }
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        val signal = signals[next++]
        var pos = 0
        val buf = FloatArray(bufferSize)
        while (pos + bufferSize <= signal.size) {
            signal.copyInto(buf, 0, pos, pos + bufferSize)
            onBuffer(buf)
            pos += bufferSize
        }
    }
    override fun stop() = Unit
}

class TuningSessionTest {

    @Test
    fun fitRecoversInharmonicity() {
        val fit = Inharmonicity.fit(exact(3.7e-4, 1..12))
        assertTrue(abs(fit.b / 3.7e-4 - 1) < 0.01, "B ${fit.b}")
        assertTrue(fit.residualCents < 0.05, "residual ${fit.residualCents}")
        assertEquals(11, fit.count)
    }

    @Test
    fun fitToleratesMeasurementNoise() {
        val fit = Inharmonicity.fit(exact(3.7e-4, 1..12, noise = 0.3))
        assertTrue(abs(fit.b / 3.7e-4 - 1) < 0.05, "B ${fit.b}")
        assertTrue(fit.residualCents < 0.4, "residual ${fit.residualCents}")
    }

    @Test
    fun aNeighboursInharmonicityPredictsWithinACent() {
        // basis string B = 4.0e-4, the next string really has 4.2e-4
        val basis = NoteMeasurement(69, 440.0, 4.0e-4, 0.1, exact(4.0e-4, 1..12))
        val target = TuningSession.targetF1(68, 440.0)
        val predicted = Inharmonicity.predict(target, basis)
        for (k in 1..8) {
            val truth = k * target * Inharmonicity.ratio(k, 4.2e-4)
            val p = predicted.first { it.k == k }
            val off = TuningSession.centsOff(p.hz, truth)
            val limit = if (k <= 6) 1.0 else 1.5
            assertTrue(abs(off) < limit, "partial $k predicted $off ct off")
        }
    }

    @Test
    fun theBasisIsTheNearestGoodMeasurement() {
        val s = TuningSession(440.0)
        s.record(NoteMeasurement(69, 440.0, 4e-4, 0.2, exact(4e-4, 1..8)))
        assertEquals(68, s.nextUnmeasured())
        s.record(NoteMeasurement(68, 415.3, 4e-4, 3.0, exact(4e-4, 1..8)))   // poor fit
        assertEquals(69, s.basisFor(67)?.midi, "poor G#4 is skipped")
        s.record(NoteMeasurement(68, 415.3, 4e-4, 0.3, exact(4e-4, 1..8)))   // re-measured, good
        assertEquals(68, s.basisFor(67)?.midi, "G4 is predicted from G#4")
        assertEquals(67, s.nextUnmeasured())
    }

    @Test
    fun theHighestStrongLastingPartialIsRecommended() {
        val partials = listOf(
            MeasuredPartial(3, 1.0, -6.0, 4.0),
            MeasuredPartial(5, 2.0, -10.0, 3.0),
            MeasuredPartial(7, 3.0, -20.0, 1.0),   // too short
            MeasuredPartial(9, 4.0, -35.0, 4.0),   // too weak
        )
        assertEquals(5, TuningSession.recommend(partials))
        assertNull(TuningSession.recommend(listOf(MeasuredPartial(1, 0.0, 0.0, 9.0))))
    }

    @Test
    fun aDetunedStringShowsKTimesTheGapAtPartialK() {
        val b = 4.0e-4
        val target = TuningSession.targetF1(68, 440.0)
        val delta = -0.5
        val tracker = PartialTracker(SR, 16384)
        val signal = stiffStrike(f0For(target + delta, b), b, 8, 16384.0 / SR)
        val readings = tracker.analyse(signal, target + delta)
        for (k in listOf(2, 4, 6)) {
            val predictedHz = k * target * Inharmonicity.ratio(k, b)
            val gap = readings.first { it.k == k }.hz - predictedHz
            val expected = k * delta * Inharmonicity.ratio(k, b)
            assertTrue(abs(gap - expected) < 0.15, "partial $k gap $gap Hz, expected $expected Hz")
        }
    }

    @Test
    fun theSessionWalksDownAndSuggestsAPartial() {
        val b = 4.0e-4
        val a4 = 440.0
        val g4 = TuningSession.targetF1(68, a4)
        val tuning = TuningController(
            QueueSource(
                listOf(
                    FloatArray(HOP * 2) + stiffStrike(f0For(a4, b), b, 10, 3.5),
                    FloatArray(HOP * 2) + stiffStrike(f0For(g4, b), b, 10, 3.5),
                ),
            ),
            clock = { 1234L },
        )
        tuning.startLive()
        assertNull(tuning.tuning.value)
        assertEquals(440.0, tuning.acceptLive())
        tuning.stopLive()

        val view = assertNotNull(tuning.tuning.value)
        assertEquals(68, view.midi)
        assertTrue(abs(view.targetHz - g4) < 1e-9)
        assertTrue(view.predicted.size >= 8, "predicted ${view.predicted.map { it.k }}")
        val fixed = tuning.suggested.value
        assertNotNull(fixed, "the suggestion comes from A4's measurement")
        val a4m = tuning.measurements().getValue(69)
        assertTrue(abs(a4m.b / b - 1) < 0.1, "A4 B ${a4m.b}")
        assertEquals(1234L, a4m.timeMs)

        tuning.startLive()
        assertTrue(abs(tuning.liveHz.value!! - g4) < 0.05, "G#4 live ${tuning.liveHz.value}")
        val pick = tuning.suggested.value
        assertEquals(fixed, pick, "the suggestion does not change while the note sounds")
        assertNotNull(pick)
        assertEquals(5, pick, "the highest partial below D#7 for G#4")
        assertEquals(setOf(1), tuning.shownPartials.value)
        tuning.tapPartial(pick)
        assertEquals(setOf(1, pick), tuning.shownPartials.value)

        assertNotNull(tuning.acceptLive())
        val next = assertNotNull(tuning.tuning.value)
        assertEquals(67, next.midi)
        assertEquals(setOf(69, 68), next.measured)
        assertEquals(setOf(1), tuning.shownPartials.value)
    }

    @Test
    fun doneIsRefusedFarFromTheTarget() {
        val b = 4.0e-4
        val tuning = TuningController(
            QueueSource(
                listOf(
                    FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 8, 3.0),
                    FloatArray(HOP * 2) + stiffStrike(f0For(400.0, b), b, 8, 3.0), // far below G#4
                ),
            ),
        )
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        tuning.startLive()
        assertNull(tuning.acceptLive())
        assertEquals(68, tuning.tuning.value?.midi)
    }

    @Test
    fun greenMeansWithinATenthOfAHertz() {
        assertTrue(TuningSession.matched(415.35, 415.30))
        assertTrue(TuningSession.matched(415.20, 415.30))
        assertTrue(!TuningSession.matched(415.41, 415.30))
        assertTrue(!TuningSession.matched(null, 415.30))
    }

    @Test
    fun arrowsStepWithinTheSessionAndDoneGoesDown() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))   // the walk itself, not the gate
        s.select(68)
        assertEquals(67, s.stepped(-1))
        assertEquals(69, s.stepped(+1), "A4 is a note like any other: the arrows reach it")
        s.select(43)                               // the lowest plain string, G2 by default
        assertEquals(43, s.stepped(-1))
        assertNull(s.below())
        s.select(57)
        assertEquals(56, s.below(), "A3 is not the end: the plain wire continues")
    }

    @Test
    fun doneOnA3ContinuesBelowTheTemperament() {
        val b = 4.0e-4
        val signals = mutableListOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 8, 3.0))
        for (midi in 68 downTo 57) {
            signals.add(FloatArray(HOP * 2) + stiffStrike(f0For(TuningSession.targetF1(midi, 440.0), b), b, 8, 3.0))
        }
        val tuning = TuningController(QueueSource(signals))
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        for (midi in 68 downTo 57) {
            assertEquals(midi, tuning.tuning.value?.midi)
            tuning.startLive()
            assertNotNull(tuning.acceptLive(), "Done refused on ${Notes.name(midi)} at ${tuning.liveHz.value}")
            tuning.stopLive()
        }
        val end = assertNotNull(tuning.tuning.value)
        assertTrue(!end.complete, "the session continues below the temperament octave")
        assertEquals(56, end.midi)
        assertEquals((57..69).toSet(), end.measured)
    }

    @Test
    fun stepNoteMovesTheTargetAndKeepsTheSuggestionFixed() {
        val b = 4.0e-4
        val tuning = TuningController(
            QueueSource(listOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 10, 3.5))),
        )
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        tuning.stepNote(-1)
        assertEquals(67, tuning.tuning.value?.midi)
        assertTrue(abs(tuning.tuning.value!!.targetHz - TuningSession.targetF1(67, 440.0)) < 1e-9)
        tuning.stepNote(+1)
        assertEquals(68, tuning.tuning.value?.midi)
        assertNotNull(tuning.suggested.value)
        tuning.stepNote(+1)
        tuning.stepNote(+1)
        assertEquals(69, tuning.tuning.value?.midi, "held at A4: the temperament octave is unfinished, and A4 is its top")
    }

    @Test
    fun tapsAddActivateAndRemovePartials() {
        val b = 4.0e-4
        val tuning = TuningController(
            QueueSource(listOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 10, 3.5))),
        )
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        assertEquals(1, tuning.activePartial.value)

        tuning.tapPartial(5)                       // add -> active
        assertEquals(setOf(1, 5), tuning.shownPartials.value)
        assertEquals(5, tuning.activePartial.value)
        tuning.tapPartial(3)                       // add -> active
        assertEquals(3, tuning.activePartial.value)
        tuning.activatePartial(5)                  // readout line: active, nothing removed
        assertEquals(setOf(1, 3, 5), tuning.shownPartials.value)
        assertEquals(5, tuning.activePartial.value)
        tuning.tapPartial(5)                       // one tap removes, whether active or not
        assertEquals(setOf(1, 3), tuning.shownPartials.value)
        assertEquals(3, tuning.activePartial.value)
        tuning.tapPartial(1)                       // fundamental: active, never removed
        tuning.tapPartial(1)
        assertEquals(setOf(1, 3), tuning.shownPartials.value)
        assertEquals(1, tuning.activePartial.value)
        tuning.tapPartial(3)                       // not active, still one tap to remove
        assertEquals(setOf(1), tuning.shownPartials.value)
        tuning.tapPartial(3)
        assertEquals(setOf(1, 3), tuning.shownPartials.value)
        tuning.activatePartial(7)                  // not shown -> ignored
        assertEquals(3, tuning.activePartial.value)
        tuning.tapPartial(12)                      // not predicted, not heard -> ignored
        assertEquals(setOf(1, 3), tuning.shownPartials.value)

        tuning.stepNote(-1)                        // a new note starts from the fundamental
        assertEquals(setOf(1), tuning.shownPartials.value)
        assertEquals(1, tuning.activePartial.value)
    }

    @Test
    fun partialsAreReadToMillihertz() {
        val b = 4.0e-4
        val f1 = TuningSession.targetF1(68, 440.0)
        val live = LiveReference(SR)
        val signal = FloatArray(HOP * 2) + stiffStrike(f0For(f1, b), b, 8, 3.0)
        var pos = 0
        val buf = FloatArray(HOP)
        while (pos + HOP <= signal.size) { signal.copyInto(buf, 0, pos, pos + HOP); live.push(buf); pos += HOP }
        val f0 = f0For(f1, b)
        for (k in listOf(2, 5, 8)) {
            val truth = k * f0 * sqrt(1 + b * k * k)
            val read = live.partials.first { it.k == k }.hz
            assertTrue(abs(read - truth) < 0.01, "partial $k read $read, truth $truth")
        }
    }

    @Test
    fun targetsComeFromTheStringItself() {
        // A4 has B = 4.0e-4; the G#4 string is 15 % stiffer. With its fundamental
        // exactly on target every partial must match, whatever A4 predicted.
        val g4 = TuningSession.targetF1(68, 440.0)
        val tuning = TuningController(
            QueueSource(
                listOf(
                    FloatArray(HOP * 2) + stiffStrike(f0For(440.0, 4.0e-4), 4.0e-4, 10, 3.0),
                    FloatArray(HOP * 2) + stiffStrike(f0For(g4, 4.6e-4), 4.6e-4, 10, 3.0),
                ),
            ),
        )
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        val predicted5 = tuning.targets.value.first { it.k == 5 }.hz
        tuning.startLive()
        val target5 = tuning.targets.value.first { it.k == 5 }.hz
        val live5 = tuning.livePartials.value.first { it.k == 5 }.hz
        assertTrue(abs(predicted5 - live5) > 0.5, "the neighbour's prediction is off by ${live5 - predicted5} Hz")
        assertTrue(TuningSession.matched(live5, target5), "own target $target5, live $live5")
        for (k in 2..5) {
            val tk = tuning.targets.value.first { it.k == k }.hz
            val lk = tuning.livePartials.value.first { it.k == k }.hz
            assertTrue(TuningSession.matched(lk, tk), "partial $k: target $tk, live $lk")
        }
        assertTrue(tuning.targets.value.none { it.k > 5 }, "partials above D#7 are not offered")
    }

    @Test
    fun aPartialMagnifiesTheFundamentalsError() {
        val g4 = TuningSession.targetF1(68, 440.0)
        val delta = -0.05
        val tuning = TuningController(
            QueueSource(
                listOf(
                    FloatArray(HOP * 2) + stiffStrike(f0For(440.0, 4.0e-4), 4.0e-4, 10, 3.0),
                    FloatArray(HOP * 2) + stiffStrike(f0For(g4 + delta, 4.3e-4), 4.3e-4, 10, 3.0),
                ),
            ),
        )
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        tuning.startLive()
        assertTrue(TuningSession.matched(tuning.liveHz.value, g4), "fundamental within the window")
        val target5 = tuning.targets.value.first { it.k == 5 }.hz
        val live5 = tuning.livePartials.value.first { it.k == 5 }.hz
        val gap = live5 - target5
        assertTrue(abs(gap - 5 * delta) < 0.03, "partial 5 gap $gap Hz, expected ${5 * delta}")
        assertTrue(!TuningSession.matched(live5, target5), "partial 5 exposes the 0.05 Hz error")
    }

    @Test
    fun aDecayedPartialKeepsItsLastReading() {
        val b = 4.0e-4
        val live = LiveReference(SR)
        // partial 6 decays fast (tau 0.25 s) and is inaudible long before the note ends
        val f0 = f0For(440.0, b)
        val n = SR * 3
        val signal = FloatArray(HOP * 2) + FloatArray(n) { i ->
            val t = i.toDouble() / SR
            var v = 0.0
            for (k in 1..4) v += 0.2 / k * sin(2 * PI * k * f0 * sqrt(1 + b * k * k) * t)
            v += 0.1 * exp(-t / 0.25) * sin(2 * PI * 6 * f0 * sqrt(1 + b * 36) * t)
            (v * exp(-t / 4.0)).toFloat()
        }
        var pos = 0
        val buf = FloatArray(HOP)
        var seenAtOneSecond: Double? = null
        while (pos + HOP <= signal.size) {
            signal.copyInto(buf, 0, pos, pos + HOP); live.push(buf); pos += HOP
            if (pos == HOP * 2 + SR && seenAtOneSecond == null) seenAtOneSecond = live.partials.firstOrNull { it.k == 6 }?.hz
        }
        val p6 = live.partials.firstOrNull { it.k == 6 }
        assertNotNull(p6, "partial 6 stays in the list after it decayed")
        assertEquals(0.0, p6.level)
        assertTrue(abs(p6.hz - 6 * f0 * sqrt(1 + b * 36)) < 0.05, "held ${p6.hz}")
    }

    @Test
    fun belowTheTemperamentTheOctaveSetsTheTarget() {
        val bRef = 4.0e-4
        val bLow = 1.5e-4
        // A4 .. A3 on equal temperament, then G#3
        val signals = mutableListOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, bRef), bRef, 8, 3.0))
        for (midi in 68 downTo 57) {
            signals.add(FloatArray(HOP * 2) + stiffStrike(f0For(TuningSession.targetF1(midi, 440.0), bRef), bRef, 8, 3.0))
        }
        // the G#3 string, placed exactly where its partial 4 meets G#4's partial 2 (4:2 octave)
        val gs4 = TuningSession.targetF1(68, 440.0)
        val via = 2 * gs4 * Inharmonicity.ratio(2, bRef)
        val gs3 = via / (4 * Inharmonicity.ratio(4, bLow))
        signals.add(FloatArray(HOP * 2) + stiffStrike(f0For(gs3, bLow), bLow, 8, 3.0))
        val tuning = TuningController(QueueSource(signals))
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        for (midi in 68 downTo 57) { tuning.startLive(); assertNotNull(tuning.acceptLive()); tuning.stopLive() }

        val view = assertNotNull(tuning.tuning.value)
        assertEquals(56, view.midi)
        val link = assertNotNull(view.link, "G#3 is linked to a measured reference")
        assertEquals(68, link.refMidi)
        assertEquals(4, link.type.low)
        assertTrue(abs(link.viaHz - via) < 0.05, "reference partial ${link.viaHz}, expected $via")
        assertEquals(4, tuning.suggested.value, "the octave's own partial is the one to add")
        assertTrue(tuning.targetHz.value < TuningSession.targetF1(56, 440.0), "the octave is stretched: G#3 sits below equal temperament")

        tuning.startLive()
        assertTrue(abs(tuning.targetHz.value - gs3) < 0.01, "live target ${tuning.targetHz.value}, expected $gs3")
        assertTrue(TuningSession.matched(tuning.liveHz.value, tuning.targetHz.value), "fundamental on target")
        val t4 = tuning.targets.value.first { it.k == 4 }.hz
        val l4 = tuning.livePartials.value.first { it.k == 4 }.hz
        assertTrue(abs(t4 - via) < 0.05, "partial 4 target $t4 is the reference partial $via")
        assertTrue(TuningSession.matched(l4, t4), "the 4:2 octave is beatless: $l4 vs $t4")
        assertNotNull(tuning.acceptLive())
        tuning.stopLive()

        val next = assertNotNull(tuning.tuning.value)
        assertEquals(55, next.midi)
        assertEquals(at.clavierhaus.unisonmaster.settings.OctaveType.O6_3, assertNotNull(next.link).type, "G3 is at the bass boundary: 6:3")
        assertEquals(67, next.link!!.refMidi)
    }

    @Test
    fun theBassBoundaryIsAnOctaveAboveTheLowestPlainString() {
        val s = at.clavierhaus.unisonmaster.settings.TunerSettings()
        assertEquals(55, s.bassBoundaryMidi)                                            // G3 for G2
        assertEquals(s.octaveMiddle, s.octaveTypeFor(56))
        assertEquals(s.octaveBass, s.octaveTypeFor(55))
        val session = TuningSession(440.0, s)
        assertEquals(27 + 39, session.notes.size)                                       // A4 .. G2, then A#4 .. C8
        assertEquals(43, session.notes[26])                                             // the floor, where the walk turns up
        assertEquals(TuningSession.MIDI_C8, session.notes.last())
    }

    @Test
    fun aWobblingStringIsShownWobbling() {
        // 415 Hz whose pitch swings +-0.5 Hz twice a second: the display must show that swing
        val hop = 1024
        val live = LiveReference(SR, hopSize = hop, minHz = 350.0, maxHz = 500.0)
        var phase = 0.0
        val n = SR * 4
        val signal = FloatArray(HOP * 2) + FloatArray(n) { i ->
            val t = i.toDouble() / SR
            phase += 2 * PI * (415.0 + 0.5 * sin(2 * PI * 2.0 * t)) / SR
            (0.3 * exp(-t / 4.0) * sin(phase)).toFloat()
        }
        val readings = ArrayList<Double>()
        var pos = 0
        val buf = FloatArray(hop)
        while (pos + hop <= signal.size) {
            signal.copyInto(buf, 0, pos, pos + hop); live.push(buf); pos += hop
            val t = (pos - HOP * 2).toDouble() / SR
            if (t in 1.0..3.5) live.hz?.let { readings += it }
        }
        assertTrue(readings.size > 100, "about 47 readings per second: ${readings.size}")
        val pp = readings.max() - readings.min()
        assertTrue(pp > 0.4, "the swing is shown: $pp Hz peak to peak")
        assertTrue(abs(readings.average() - 415.0) < 0.05, "and centred on the string: ${readings.average()}")
    }

    // ---- the treble: the walk above A4, and the octave link read upward ----

    @Test
    fun theWalkRunsDownToThePlainWireFloorAndThenUpToTheTop() {
        val s = TuningSession(440.0, TunerSettings(lowestUnwoundMidi = 60))
        assertEquals(TuningSession.MIDI_A4, s.notes.first())
        assertEquals(60, s.notes[TuningSession.MIDI_A4 - 60])          // the floor
        assertEquals(70, s.notes[TuningSession.MIDI_A4 - 60 + 1])      // then A#4
        assertEquals(TuningSession.MIDI_C8, s.notes.last())
        assertEquals(s.notes.size, s.notes.distinct().size)
    }

    @Test
    fun doneAtTheFloorTurnsTheWalkUpwardInsteadOfEnding() {
        val s = TuningSession(440.0, TunerSettings(lowestUnwoundMidi = 60, temperamentFirst = false))
        s.select(61)
        assertEquals(60, s.next())
        s.select(60)
        assertEquals(70, s.next())          // the floor hands over to the treble
        s.select(TuningSession.MIDI_C8)
        assertNull(s.next())                // and the top ends the walk
    }

    @Test
    fun aTrebleNoteIsLinkedToAMeasuredNoteBelowIt() {
        val s = TuningSession(440.0, TunerSettings(octaveTreble = OctaveType.O4_1))
        val b = 4e-4
        s.record(NoteMeasurement(60, 261.6, b, 0.1, exact(b, 1..8)))   // C4 measured
        val link = s.octaveLink(84)                                    // C6, two octaves above
        assertNotNull(link)
        assertEquals(60, link.refMidi)
        assertEquals(OctaveType.O4_1, link.type)
        // the upper note meets the lower one at partial 4 of the lower, partial 1 of the upper
        assertEquals(1, link.ownK)
        assertEquals(4 * 261.6 * Inharmonicity.ratio(4, b), link.viaHz, 1e-6)
    }

    @Test
    fun theTrebleTargetPutsTheOwnPartialOnTheMeasuredOne() {
        val s = TuningSession(440.0, TunerSettings(octaveTreble = OctaveType.O4_1))
        val b = 4e-4
        s.record(NoteMeasurement(60, 261.6, b, 0.1, exact(b, 1..8)))
        val link = assertNotNull(s.octaveLink(84))
        // with the string's own ratio known, target · ownK · ratio == the reference partial
        val target = s.target(84, ownCentsLow = Inharmonicity.centsOf(link.ownK, b))
        assertEquals(link.viaHz, target * link.ownK * Inharmonicity.ratio(link.ownK, b), 1e-6)
    }

    @Test
    fun insideTheTemperamentOctaveThereIsStillNoLink() {
        val s = TuningSession(440.0)
        s.record(NoteMeasurement(60, 261.6, 4e-4, 0.1, exact(4e-4, 1..8)))
        for (midi in TunerSettings().temperamentLowMidi..TunerSettings.TEMPERAMENT_HIGH) {
            assertNull(s.octaveLink(midi), "note $midi is inside the temperament octave")
        }
    }

    @Test
    fun aTrebleNoteWithoutItsReferenceFallsBackToEqualTemperament() {
        val s = TuningSession(440.0, TunerSettings(octaveTreble = OctaveType.O4_1))
        assertNull(s.octaveLink(84))                       // nothing measured below
        assertEquals(TuningSession.targetF1(84, 440.0), s.target(84), 1e-9)
    }

    @Test
    fun theOctaveTypeIsChosenByRegister() {
        val s = TunerSettings(
            lowestUnwoundMidi = 43,
            octaveBass = OctaveType.O6_3, octaveMiddle = OctaveType.O4_2, octaveTreble = OctaveType.O4_1,
        )
        assertEquals(OctaveType.O6_3, s.octaveTypeFor(55))   // at the bass boundary
        assertEquals(OctaveType.O4_2, s.octaveTypeFor(56))   // just above it
        assertEquals(OctaveType.O4_2, s.octaveTypeFor(TunerSettings.TEMPERAMENT_HIGH))
        assertEquals(OctaveType.O4_1, s.octaveTypeFor(TunerSettings.TEMPERAMENT_HIGH + 1))
    }

    // ---- FOSS: the temperament octave is finished before anything else ----

    private fun measuredNote(midi: Int, hz: Double) =
        NoteMeasurement(midi, hz, 4e-4, 0.1, exact(4e-4, 1..8))

    @Test
    fun theOctaveBelowTheTemperamentIsClosedUntilTheTemperamentIsDone() {
        val s = TuningSession(440.0)
        assertTrue(s.selectable(60), "inside the temperament octave")
        assertTrue(!s.selectable(56), "one semitone below it")
        assertTrue(!s.selectable(80), "and the treble too")
        assertEquals(TunerSettings().temperamentLowMidi, s.stepLowMidi)
        assertEquals(TunerSettings.TEMPERAMENT_HIGH, s.stepHighMidi)
    }

    @Test
    fun theWholeCompassOpensOnceTheTemperamentOctaveIsMeasured() {
        val s = TuningSession(440.0)
        for (midi in s.temperamentNotes) s.record(measuredNote(midi, TuningSession.targetF1(midi, 440.0)))
        assertTrue(s.temperamentComplete)
        assertTrue(s.selectable(56))
        assertTrue(s.selectable(80))
        assertEquals(s.lowMidi, s.stepLowMidi)
        assertEquals(s.highMidi, s.stepHighMidi)
    }

    @Test
    fun theArrowsDoNotLeaveTheTemperamentOctaveWhileItIsUnfinished() {
        val s = TuningSession(440.0)
        s.select(57)                                  // A3, the foot of it
        assertEquals(57, s.stepped(-1), "there is nothing below yet")
        s.select(68)                                  // G#4
        assertEquals(69, s.stepped(+1), "and A4, the top of the octave, is reachable")
    }

    @Test
    fun doneReturnsToTheGapWhenANoteOfTheOctaveWasSkipped() {
        val s = TuningSession(440.0)
        for (midi in 69 downTo 59) s.record(measuredNote(midi, TuningSession.targetF1(midi, 440.0)))
        s.select(58)
        s.record(measuredNote(58, TuningSession.targetF1(58, 440.0)))
        // 57 is the one note of the octave still missing
        assertEquals(57, s.next())
        s.record(measuredNote(57, TuningSession.targetF1(57, 440.0)))
        s.select(57)
        assertEquals(56, s.next(), "and then the compass continues")
    }

    @Test
    fun proMaySampleAnywhere() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))
        assertTrue(s.selectable(56))
        assertTrue(s.selectable(84))
        assertEquals(s.lowMidi, s.stepLowMidi)
        assertEquals(s.highMidi, s.stepHighMidi)
    }

    @Test
    fun aSavedTuningOutsideTheGateResumesInsideItInsteadOfCrashing() {
        val b = 4.0e-4
        val tuning = TuningController(
            QueueSource(listOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 10, 3.5))),
        )
        // a snapshot from before the gate existed: two notes measured, the
        // tuner was down at G3, and the temperament octave is not finished
        val snap = at.clavierhaus.unisonmaster.persistence.SessionSnapshot(
            a4Hz = 440.0,
            currentMidi = 55,
            measurements = listOf(
                NoteMeasurement(69, 440.0, b, 0.1, exact(b, 1..8)),
                NoteMeasurement(68, 415.3, b, 0.1, exact(b, 1..8)),
            ),
            savedAtMs = 0L,
        )
        tuning.restore(snap)
        val at = tuning.tuning.value?.midi
        assertNotNull(at)
        assertTrue(at in 57..68, "resumed inside the temperament octave, was $at")
    }

    // ---- leaving a note registers it, once the session allows that ----

    /** A controller with A4 set and, on request, the temperament octave finished. */
    private fun walker(temperamentDone: Boolean, temperamentFirst: Boolean = true): TuningController {
        val b = 4.0e-4
        val signals = mutableListOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 8, 3.0))
        val notes = if (temperamentDone) (68 downTo 57).toList() else emptyList()
        for (midi in notes) signals.add(FloatArray(HOP * 2) + stiffStrike(f0For(TuningSession.targetF1(midi, 440.0), b), b, 8, 3.0))
        // one more strike, for the note the session stands on after the walk
        val next = if (temperamentDone) 56 else 68
        signals.add(FloatArray(HOP * 2) + stiffStrike(f0For(TuningSession.targetF1(next, 440.0), b), b, 8, 3.0))
        val tuning = TuningController(QueueSource(signals))
        tuning.applySettings(TunerSettings(temperamentFirst = temperamentFirst))
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        for (midi in notes) { tuning.startLive(); assertNotNull(tuning.acceptLive(), "Done refused on $midi"); tuning.stopLive() }
        return tuning
    }

    @Test
    fun insideTheTemperamentOctaveLeavingANoteDoesNotRegisterIt() {
        val tuning = walker(temperamentDone = false)
        assertEquals(68, tuning.tuning.value?.midi)
        assertTrue(!tuning.tuning.value!!.recordsOnLeaving)
        tuning.startLive()                          // G#4 sounds and is measured
        assertNotNull(tuning.liveHz.value)
        tuning.stopLive()
        tuning.stepNote(-1)                         // moving on without Done
        assertEquals(67, tuning.tuning.value?.midi)
        assertTrue(68 !in tuning.tuning.value!!.measured, "G#4 needs Done inside the temperament octave")
    }

    @Test
    fun afterTheTemperamentOctaveLeavingAMeasuredNoteRegistersIt() {
        val tuning = walker(temperamentDone = true)
        assertEquals(56, tuning.tuning.value?.midi)
        assertTrue(tuning.tuning.value!!.recordsOnLeaving)
        tuning.startLive()                          // G#3 sounds
        assertNotNull(tuning.liveHz.value)
        tuning.stopLive()
        tuning.stepNote(-1)                         // no Done: just move on
        assertEquals(55, tuning.tuning.value?.midi)
        assertTrue(56 in tuning.tuning.value!!.measured, "G#3 registers on leaving")
        assertTrue(56 in tuning.tuning.value!!.deviations, "and its value is on the curve")
    }

    @Test
    fun leavingANoteThatWasNeverStruckRegistersNothing() {
        val tuning = walker(temperamentDone = true)
        assertEquals(56, tuning.tuning.value?.midi)
        tuning.stepNote(-1)                         // G#3 was never played
        tuning.stepNote(-1)
        assertEquals(54, tuning.tuning.value?.midi)
        assertTrue(56 !in tuning.tuning.value!!.measured)
        assertTrue(55 !in tuning.tuning.value!!.measured)
    }

    @Test
    fun proRegistersOnLeavingFromTheStart() {
        val tuning = walker(temperamentDone = false, temperamentFirst = false)
        assertEquals(68, tuning.tuning.value?.midi)
        assertTrue(tuning.tuning.value!!.recordsOnLeaving)
        tuning.startLive(); assertNotNull(tuning.liveHz.value); tuning.stopLive()
        tuning.stepNote(-1)
        assertTrue(68 in tuning.tuning.value!!.measured, "Pro: G#4 registers on leaving, no Done needed")
    }

    // ---- what a stored partial may be, and what a reading on leaving may be ----

    @Test
    fun aStoredPartialNoPlainWireStringCanProduceIsNotUsedByAnOctaveLink() {
        val s = TuningSession(440.0)
        for (midi in s.temperamentNotes) s.record(measuredNote(midi, TuningSession.targetF1(midi, 440.0)))
        // D#4 as an older tracker saved it: partial 3 was the ringing D4's,
        // 90 cents flat of D#4's own third partial
        val dSharp4 = TuningSession.targetF1(63, 440.0)
        s.record(NoteMeasurement(63, dSharp4, 4.0e-4, 0.1, listOf(
            MeasuredPartial(1, 0.0, 0.0, 3.0),
            MeasuredPartial(3, -90.0, -6.0, 3.0),
        )))
        val target = s.target(51)                                  // D#3, 6:3 against D#4
        val et = TuningSession.targetF1(51, 440.0)
        assertTrue(abs(TuningSession.centsOff(target, et)) < 10.0,
            "D#3's target must stay D#3, was %.1f Hz (%.0f cents off)".format(target, TuningSession.centsOff(target, et)))
    }

    @Test
    fun aReadingNearerAnotherKeyIsNotRegisteredOnLeaving() {
        val b = 4.0e-4
        val signals = mutableListOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 8, 3.0))
        for (midi in 68 downTo 57) signals.add(FloatArray(HOP * 2) + stiffStrike(f0For(TuningSession.targetF1(midi, 440.0), b), b, 8, 3.0))
        // standing on G#3, what sounds is G3 — the neighbour, a semitone down
        signals.add(FloatArray(HOP * 2) + stiffStrike(f0For(TuningSession.targetF1(55, 440.0), b), b, 8, 3.0))
        val tuning = TuningController(QueueSource(signals))
        tuning.applySettings(TunerSettings(temperamentFirst = true))
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        for (midi in 68 downTo 57) { tuning.startLive(); assertNotNull(tuning.acceptLive(), "Done refused on $midi"); tuning.stopLive() }
        assertEquals(56, tuning.tuning.value?.midi)
        tuning.startLive()
        val heard = assertNotNull(tuning.liveHz.value)
        tuning.stopLive()
        assertEquals(55, Notes.nearestMidi(heard, 440.0), "the test needs G3 to be what sounded")
        tuning.stepNote(-1)
        assertTrue(56 !in tuning.tuning.value!!.measured, "G3's reading must not become G#3's value")
    }


    @Test
    fun aStoredFundamentalNearerAnotherKeyIsNotThisNotesMeasurement() {
        val s = TuningSession(440.0)
        for (midi in s.temperamentNotes) s.record(measuredNote(midi, TuningSession.targetF1(midi, 440.0)))
        // D#4 as a saved tuning from an earlier reader had it: its "fundamental" is D4's
        s.record(NoteMeasurement(63, 296.9, 4.0e-4, 0.1, listOf(MeasuredPartial(1, 0.0, 0.0, 3.0), MeasuredPartial(3, 5.0, -6.0, 3.0))))
        assertTrue(63 !in s.measurements, "a reading nearer D4 is not D#4's")
        val target = s.target(51)                                  // D#3, 6:3 against D#4
        val et = TuningSession.targetF1(51, 440.0)
        assertTrue(abs(TuningSession.centsOff(target, et)) < 10.0,
            "D#3's target must stay D#3, was %.1f Hz (%.0f cents off)".format(target, TuningSession.centsOff(target, et)))
    }

    @Test
    fun a4CanBeReturnedToAndReRecordedWithoutMovingTheReference() {
        val b = 4.0e-4
        val signals = mutableListOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 8, 3.0))
        signals.add(FloatArray(HOP * 2) + stiffStrike(f0For(440.04, b), b, 8, 3.0))   // A4 again, the pin has settled a little
        val tuning = TuningController(QueueSource(signals))
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()                 // the hub sets A4
        assertEquals(68, tuning.tuning.value?.midi)
        tuning.stepNote(+1)
        assertEquals(69, tuning.tuning.value?.midi, "A4 is reachable from G#4")
        tuning.startLive()
        assertNotNull(tuning.acceptLive(), "Done on A4 is accepted")
        tuning.stopLive()
        assertEquals(440.0, tuning.referenceA4Hz.value, 1e-9, "the reference set on the hub is untouched")
        val a4 = assertNotNull(tuning.measurements()[69])
        assertTrue(abs(a4.f1Hz - 440.04) < 0.02, "A4's measurement is the new reading, was ${a4.f1Hz}")
    }

    // ---- automatic note switching ----

    @Test
    fun afterTheTemperamentOctaveTheScreenFollowsTheKeyStruck() {
        val tuning = walker(temperamentDone = true)          // stands on G#3, one G#3 strike queued
        assertEquals(56, tuning.tuning.value?.midi)
        tuning.startLive(); assertNotNull(tuning.liveHz.value); tuning.stopLive()   // G#3 sounds and is read
        // now F#3 is struck, two semitones down, with the screen still on G#3
        val b = 4.0e-4
        val fSharp3 = TuningSession.targetF1(54, 440.0)
        (tuning.audioSourceForTest() as QueueSource).add(FloatArray(HOP * 2) + stiffStrike(f0For(fSharp3, b), b, 8, 3.0))
        val before = tuning.liveHz.value; val sb = tuning.measurements()[56]
        tuning.startLive(); tuning.stopLive()
        assertEquals(54, tuning.tuning.value?.midi, "the screen followed the key")
        assertTrue(56 in tuning.tuning.value!!.measured, "and G#3, left behind, was registered (live before: $before, stored before: ${sb?.f1Hz}, now: ${tuning.measurements().keys.sorted()})")
        val read = assertNotNull(tuning.liveHz.value)
        assertTrue(abs(read - fSharp3) < 0.1, "F#3 is read without a second strike, was $read")
    }

    @Test
    fun insideTheUnfinishedTemperamentOctaveTheScreenDoesNotFollowTheKey() {
        val tuning = walker(temperamentDone = false)         // on G#4, gate closed
        val b = 4.0e-4
        (tuning.audioSourceForTest() as QueueSource).let { q ->
            q.clear()
            q.add(FloatArray(HOP * 2) + stiffStrike(f0For(TuningSession.targetF1(64, 440.0), b), b, 8, 3.0))   // E4 struck
        }
        tuning.startLive(); tuning.stopLive()
        assertEquals(68, tuning.tuning.value?.midi, "the octave is walked with Done, not by ear")
    }

    @Test
    fun theSettingTurnsFollowingOff() {
        val tuning = walker(temperamentDone = true)
        tuning.applySettings(TunerSettings(autoNote = false))
        val b = 4.0e-4
        (tuning.audioSourceForTest() as QueueSource).let { q ->
            q.clear()
            q.add(FloatArray(HOP * 2) + stiffStrike(f0For(TuningSession.targetF1(54, 440.0), b), b, 8, 3.0))
        }
        tuning.startLive(); tuning.stopLive()
        assertEquals(56, tuning.tuning.value?.midi)
    }

    // ---- the inharmonicity sampling report ----

    /** A stiff-string note measured exactly, with B following the instrument's law. */
    private fun anchor(midi: Int, b: Double) =
        NoteMeasurement(midi, TuningSession.targetF1(midi, 440.0), b, 0.1, exact(b, 1..8))

    /** ln B linear in note number: B doubles about every ten semitones, roughly a real plain-wire scale (1e-4 at E2, 1e-2 at C8). */
    private fun lawB(midi: Int): Double = 1.0e-4 * Math.pow(2.0, (midi - 40) / 10.0)

    @Test
    fun fewerThanThreeAnchorsIsNoCurveYet() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))
        s.record(anchor(40, lawB(40)))
        s.record(anchor(60, lawB(60)))
        val r = s.curve()
        assertEquals(2, r.anchors)
        assertNull(r.worstCents)
        assertTrue(!r.representative)
    }

    @Test
    fun threeAnchorsAcrossTwoOctavesOnAWellBehavedScaleAreRepresentative() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))
        for (midi in listOf(40, 52, 64)) s.record(anchor(midi, lawB(midi)))   // E2, E3, E4
        val r = s.curve()
        assertEquals(3, r.anchors)
        assertEquals(24, r.spanSemitones)
        val worst = assertNotNull(r.worstCents)
        assertTrue(worst < 0.2, "exact law, worst held-out %.2f c".format(worst))
        assertTrue(r.representative)
    }

    @Test
    fun anchorsCrowdedInOneOctaveAreNotRepresentativeHoweverManyThereAre() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))
        for (midi in 57..69) s.record(anchor(midi, lawB(midi)))                // A3–A4 only
        val r = s.curve()
        assertEquals(13, r.anchors)
        assertTrue(!r.representative, "an octave does not stand for the compass")
    }

    @Test
    fun aNoteThatBreaksTheLawKeepsTheCurveFromBeingRepresentative() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))
        for (midi in listOf(40, 46, 52, 58, 64)) s.record(anchor(midi, lawB(midi)))
        s.record(anchor(49, lawB(49) * 2.5))                                    // a strut, a break: B off by 2.5×
        val r = s.curve()
        val worst = assertNotNull(r.worstCents)
        assertTrue(worst > TuningSession.CURVE_TOLERANCE_CENTS, "held-out error %.2f c must show the break".format(worst))
        assertTrue(!r.representative)
    }

    @Test
    fun notesAboveC5AreNotAnchors() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))
        for (midi in listOf(40, 52, 64, 76, 88)) s.record(anchor(midi, lawB(midi)))
        assertEquals(3, s.curve().anchors, "E5 and E6 are extrapolated, not sampled")
    }

    @Test
    fun aRepresentativeCurvePredictsBForANoteNotYetSampled() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))
        for (midi in listOf(40, 46, 52, 58, 64)) s.record(anchor(midi, lawB(midi)))
        assertTrue(s.curve().representative)
        val predicted = s.predictedB(49)                                         // C#3, between anchors
        assertTrue(abs(predicted / lawB(49) - 1.0) < 0.05, "predicted %.3e, law %.3e".format(predicted, lawB(49)))
        val treble = s.predictedB(84)                                            // C6, above the sampled range: extrapolated
        assertTrue(abs(treble / lawB(84) - 1.0) < 0.1, "extrapolated %.3e, law %.3e".format(treble, lawB(84)))
    }

    @Test
    fun withoutARepresentativeCurveTheNearestNoteStandsIn() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false))
        s.record(anchor(40, lawB(40)))
        s.record(anchor(52, lawB(52)))
        assertEquals(lawB(52), s.predictedB(58), 1e-12, "the nearest measured note")
    }

    // ---- Stretch Definition (Pro) ----

    @Test
    fun proSamplesUntilTheChosenNumberOfAnchorsThenTunesToTheCalculatedStretch() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false, calibrationNotes = 3))
        assertTrue(s.calibrating); assertTrue(!s.calibrated)
        s.record(anchor(40, lawB(40))); s.record(anchor(52, lawB(52)))
        assertTrue(s.calibrating, "two of three")
        assertEquals(TuningSession.targetF1(46, 440.0), s.target(46), 1e-9, "no reference measured: equal temperament, as before")
        s.record(anchor(64, lawB(64)))
        assertTrue(!s.calibrating); assertTrue(s.calibrated)
        // the bass is stretched flat of equal temperament, the treble sharp, by the calculated curve
        val bass = s.target(40); val bassEt = TuningSession.targetF1(40, 440.0)
        val treble = s.target(88); val trebleEt = TuningSession.targetF1(88, 440.0)
        assertTrue(bass < bassEt, "E2 %.3f must lie below ET %.3f".format(bass, bassEt))
        assertTrue(treble > trebleEt, "E6 %.3f must lie above ET %.3f".format(treble, trebleEt))
        assertTrue(TuningSession.centsOff(bass, bassEt) > -40 && TuningSession.centsOff(treble, trebleEt) < 40, "and by a plausible amount")
        // the target is the calculation, not the string: a live partial does not move it
        assertEquals(s.target(40), s.target(40, ownCentsLow = 30.0), 1e-9)
        // the link says so
        val link = assertNotNull(s.octaveLink(40))
        assertTrue(link.calculated)
        assertEquals(40 + link.type.semitones, link.refMidi)
        // inside the temperament octave the temperament stands
        assertEquals(TuningSession.targetF1(60, 440.0), s.target(60), 1e-9)
    }

    @Test
    fun theCalculatedStretchIsContinuousAcrossTheChain() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = false, calibrationNotes = 3))
        for (midi in listOf(40, 52, 64)) s.record(anchor(midi, lawB(midi)))
        var prev = TuningSession.centsOff(s.target(28), TuningSession.targetF1(28, 440.0))
        for (midi in 29..108) {
            val c = TuningSession.centsOff(s.target(midi), TuningSession.targetF1(midi, 440.0))
            assertTrue(abs(c - prev) < 6.0, "jump of %.1f c between %d and %d".format(c - prev, midi - 1, midi))
            prev = c
        }
    }

    @Test
    fun fossNeverCalibrates() {
        val s = TuningSession(440.0, TunerSettings(temperamentFirst = true, calibrationNotes = 3))
        for (midi in listOf(40, 52, 64)) s.record(anchor(midi, lawB(midi)))
        assertTrue(!s.calibrating); assertTrue(!s.calibrated)
        assertNull(s.octaveLink(46)?.takeIf { it.calculated })
    }

    /** A wound bass string as heard: partial 1 twenty dB under partial 2, thirty partials. */
    private fun bassStrike(f1: Double, b: Double, seconds: Double): FloatArray {
        val f0 = f1 / sqrt(1 + b)
        val rnd = Random(7)
        return FloatArray((seconds * SR).toInt()) { i ->
            val t = i.toDouble() / SR
            var v = 0.0
            for (k in 1..30) { val f = k * f0 * sqrt(1 + b * k * k); if (f < SR / 2.2) v += (if (k == 1) 0.03 else 0.25 / sqrt(k.toDouble())) * sin(2 * PI * f * t + k) * exp(-t * (0.3 + 0.1 * k)) }
            (v + 1e-4 * (rnd.nextDouble() * 2 - 1)).toFloat()
        }
    }

    @Test
    fun theTapHearsEveryBufferOfTheLiveScreenAsItArrives() {
        val b = 4.0e-4
        val signal = FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 8, 1.0)
        val tuning = TuningController(QueueSource(listOf(signal)))
        var heard = 0
        var sum = 0.0
        tuning.tap = { chunk -> heard += chunk.size; for (x in chunk) sum += x.toDouble() }
        tuning.startLive(); tuning.stopLive()
        val whole = signal.size / 1024 * 1024    // startLive reads in hops of 1024
        assertEquals(whole, heard, "every whole buffer, before any reading")
        assertTrue(abs(sum - signal.take(whole).sumOf { it.toDouble() }) < 1e-3, "the samples as delivered")
        tuning.tap = null
        val q = tuning.audioSourceForTest() as QueueSource
        q.add(signal)
        tuning.startLive(); tuning.stopLive()
        assertEquals(whole, heard, "nothing once nobody listens")
    }

    @Test
    fun stretchDefinitionRegistersEveryKeyStruckAcrossTheRangeWithoutDone() {
        val b = 4.0e-4
        val signals = mutableListOf(FloatArray(HOP * 2) + stiffStrike(f0For(440.0, b), b, 8, 3.0))    // A4 on the hub
        val tuning = TuningController(QueueSource(signals))
        tuning.applySettings(TunerSettings(temperamentFirst = false, calibrationNotes = 3, lowestUnwoundMidi = 40))
        tuning.startLive(); tuning.acceptLive(); tuning.stopLive()
        assertTrue(tuning.tuning.value!!.calibrating)
        val q = tuning.audioSourceForTest() as QueueSource
        // E2 — the lowest plain string, its fundamental faint — then E3, then C5, then G4: each struck, nothing pressed
        val played = listOf(40 to bassStrike(TuningSession.targetF1(40, 440.0), 1.8e-4, 3.0),
            52 to stiffStrike(f0For(TuningSession.targetF1(52, 440.0), 3.0e-4), 3.0e-4, 10, 3.0),
            72 to stiffStrike(f0For(TuningSession.targetF1(72, 440.0), 7.0e-4), 7.0e-4, 8, 3.0),
            67 to stiffStrike(f0For(TuningSession.targetF1(67, 440.0), 4.0e-4), 4.0e-4, 8, 3.0))
        for ((midi, sig) in played) {
            q.add(FloatArray(HOP * 2) + sig)
            tuning.startLive(); tuning.stopLive()
            assertEquals(midi, tuning.tuning.value?.midi, "the screen followed ${Notes.name(midi)}")
        }
        val m = tuning.measurements()
        for (midi in listOf(40, 52, 72)) assertTrue(midi in m && m.getValue(midi).partials.size >= 2, "${Notes.name(midi)} registered with its partials on leaving: ${m.keys.sorted()}")
        assertEquals(4, tuning.tuning.value?.anchors, "A4 from the hub and the three struck; G#4, never struck, not among them: ${m.keys.sorted()}")
        assertTrue(!tuning.tuning.value!!.calibrating, "Stretch Definition complete at three")
        val e2 = m.getValue(40)
        assertTrue(abs(TuningSession.centsOff(e2.f1Hz, TuningSession.targetF1(40, 440.0))) < 10.0, "E2's fundamental from its partials: %.2f Hz".format(e2.f1Hz))
    }
}
