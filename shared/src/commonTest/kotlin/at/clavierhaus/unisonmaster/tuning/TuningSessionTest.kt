package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
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
private class QueueSource(private val signals: List<FloatArray>) : AudioSource {
    override val sampleRateHz: Int = SR
    private var next = 0
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
        assertTrue(pick >= 6, "suggested $pick")
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
        val s = TuningSession(440.0)
        s.select(68)
        assertEquals(67, s.stepped(-1))
        assertEquals(68, s.stepped(+1), "A4 is the reference, not a step target")
        s.select(57)
        assertEquals(57, s.stepped(-1))
        assertNull(s.below())
        s.select(60)
        assertEquals(59, s.below())
    }

    @Test
    fun doneOnA3CompletesTheOctave() {
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
        assertTrue(end.complete)
        assertEquals(57, end.midi)
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
        tuning.stepNote(+1)
        assertEquals(68, tuning.tuning.value?.midi)
        assertNotNull(tuning.suggested.value)
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
        tuning.tapPartial(5)                       // shown, not active -> active
        assertEquals(setOf(1, 3, 5), tuning.shownPartials.value)
        assertEquals(5, tuning.activePartial.value)
        tuning.tapPartial(5)                       // active -> removed
        assertEquals(setOf(1, 3), tuning.shownPartials.value)
        assertEquals(3, tuning.activePartial.value)
        tuning.tapPartial(1)                       // fundamental: active, never removed
        tuning.tapPartial(1)
        assertEquals(setOf(1, 3), tuning.shownPartials.value)
        assertEquals(1, tuning.activePartial.value)
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
        for (k in 2..8) {
            val tk = tuning.targets.value.first { it.k == k }.hz
            val lk = tuning.livePartials.value.first { it.k == k }.hz
            assertTrue(TuningSession.matched(lk, tk), "partial $k: target $tk, live $lk")
        }
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
}
