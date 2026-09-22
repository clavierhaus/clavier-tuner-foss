package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.measure.SyntheticString
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.tuning.measuredString
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plays what it is given, one signal per start(), synchronously. */
private class PlayedSource : AudioSource {
    override val sampleRateHz = 48_000
    var signal = FloatArray(0)
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
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

class TuningControllerTest {
    private val a4 = 443.1
    private val settings = TunerSettings(lowestKeyMidi = 17, lowestUnwoundMidi = 40)

    private fun et(midi: Int) = TuningSession.targetF1(midi, a4)

    /** Plays [signal] through the controller, as one stretch of listening. */
    private fun TuningController.hear(source: PlayedSource, signal: FloatArray) {
        stopLive()
        source.signal = signal
        assertTrue(startLive())
    }

    private fun controller(s: TunerSettings = settings): Pair<TuningController, PlayedSource> {
        val src = PlayedSource()
        val c = TuningController(src) { 1000L }
        c.applySettings(s)
        return c to src
    }

    @Test
    fun theHubReadsTheA4StringAndDoneMakesItTheReference() {
        val (c, src) = controller()
        c.hear(src, SyntheticString.strike(443.12, 8e-4, 3.0))
        val hz = assertNotNull(c.liveHz.value, "the A4 string is read")
        assertTrue(abs(hz - 443.12) < 0.02, "read %.3f".format(hz))
        assertEquals(443.1, c.acceptLive()!!, 1e-9)
        val m = assertNotNull(c.measurements()[69])
        assertTrue(abs(m.b - 8e-4) / 8e-4 < 0.15, "A4 measured with its inharmonicity: B %.2e".format(m.b))
        assertTrue(m.partials.size >= 4)
        assertEquals(68, c.tuning.value!!.midi, "on to G#4")
    }

    @Test
    fun aTemperamentNoteMatchesOnTargetAndDoneRefusesItOffTarget() {
        val (c, src) = controller()
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4))))
        val t = c.tuning.value!!
        assertEquals(1, t.listening.k)
        // four cents flat: read, not matched
        c.hear(src, SyntheticString.strike(et(68) * 2.0.pow(-4.0 / 1200), 7e-4, 3.0))
        val r = c.reading.value!!
        assertTrue(r.live)
        assertEquals(-4.0, r.cents!!, 0.1)
        assertFalse(c.matchedNow())
        assertNull(c.acceptLive(), "Done is refused off target")
        // on target
        c.hear(src, SyntheticString.strike(et(68) + 0.02, 7e-4, 3.0))
        assertTrue(c.matchedNow())
        assertNotNull(c.acceptLive())
        assertEquals(et(68), c.measurements()[68]!!.f1Hz, 0.05)
        assertEquals(67, c.tuning.value!!.midi)
    }

    @Test
    fun belowTheOctaveTheStringIsTunedByItsPartialToThePartnersPartial() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        val gs4 = measuredString(68, et(68), 7e-4)
        c.restore(SessionSnapshot(0, a4, 56, listOf(measuredString(69, a4, 8e-4), gs4)))
        val t = c.tuning.value!!
        assertEquals(4, t.listening.k)
        val target = gs4.partialHz(2)!!
        assertEquals(target, t.listening.targetHz, 1e-9)
        // a string of its own stiffness, tuned so that its fourth partial meets the target
        val b = 3e-4
        val f1 = target / (4 * Inharmonicity.ratio(4, b))
        c.hear(src, SyntheticString.strike(f1, b, 3.0))
        val r = c.reading.value!!
        assertEquals(4, r.k)
        assertTrue(abs(r.hz!! - target) < 0.03, "partial 4 read %.3f for %.3f".format(r.hz, target))
        assertTrue(c.matchedNow())
        assertNotNull(c.acceptLive())
        val m = c.measurements()[56]!!
        assertEquals(target, m.partialHz(4)!!, 0.03, "the partial tuned is kept as read")
        assertTrue(abs(m.b - b) / b < 0.2, "and the string's own B: %.2e".format(m.b))
    }

    @Test
    fun aStringFarOffIsNotReadButSaidToBeFarOff() {
        val (c, src) = controller()
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4))))
        c.hear(src, SyntheticString.strike(et(68) * 2.0.pow(-70.0 / 1200), 7e-4, 3.0))
        val r = c.reading.value!!
        assertFalse(r.live, "70 cents off is outside the band")
        val coarse = assertNotNull(r.coarseCents, "the finder places it")
        assertEquals(-70.0, coarse, 3.0)
    }

    @Test
    fun leavingANoteKeepsItOnlyWhenItWasHeardNearItsTarget() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4))))
        c.stepNote(-1)                                  // not struck: nothing kept
        assertNull(c.measurements()[68])
        c.selectNote(68)
        c.hear(src, SyntheticString.strike(et(68) * 2.0.pow(1.0 / 1200), 7e-4, 3.0))
        c.stepNote(-1)                                  // struck, a cent sharp: kept as it stood
        val m = assertNotNull(c.measurements()[68])
        assertEquals(1.0, TuningSession.centsOff(m.f1Hz, et(68)), 0.1)
    }

    @Test
    fun aTappedPartialIsReadAgainstItsOwnPlace() {
        val (c, src) = controller()
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 7e-4))))
        c.toggleFullSpectrum()
        c.activatePartial(3)
        assertEquals(3, c.activePartial.value)
        val t = c.tuning.value!!
        val p3 = 3 * t.targetHz * Inharmonicity.ratio(3, t.b)
        c.hear(src, SyntheticString.strike(t.targetHz, t.b, 3.0))
        val r = c.reading.value!!
        assertEquals(3, r.k)
        assertTrue(abs(r.hz!! - p3) < 0.05, "partial 3 read %.3f for %.3f".format(r.hz, p3))
        assertTrue(c.liveAudible.value.containsAll(setOf(1, 2, 3)), "Full Spectrum hears ${c.liveAudible.value}")
    }

    @Test
    fun theScreenFollowsTheKeyStruckAndKeepsTheNoteItLeft() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4))))
        val first = SyntheticString.strike(et(68), 7e-4, 2.5)
        val second = SyntheticString.strike(et(66) * 2.0.pow(-8.0 / 1200), 7e-4, 2.5, seed = 2)
        c.hear(src, first + second)
        assertEquals(66, c.tuning.value!!.midi, "the screen moved to the key struck")
        assertEquals(et(68), c.measurements()[68]!!.f1Hz, 0.05, "and kept G#4 as it was heard, not as F#4")
        assertEquals(-8.0, c.reading.value!!.cents!!, 0.3, "F#4 read on its own target")
    }

    @Test
    fun withoutFollowingTheKeyStruckIsNamed() {
        val (c, src) = controller(settings.copy(temperamentFirst = false, autoNote = false))
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4))))
        c.hear(src, SyntheticString.strike(et(66), 7e-4, 1.2))
        assertEquals(68, c.tuning.value!!.midi)
        assertEquals(66, c.heardMidi.value)
    }

    @Test
    fun fullSpectrumBellsStandStillOnASteadyUnison() {
        // three strings a few hundredths of a cent apart, sounding together: every
        // shown partial is read by phase and stays within hundredths of a hertz
        val c = TuningController(TappedSource) { 0L }
        c.applySettings(settings)
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 7e-4))))
        c.toggleFullSpectrum()
        val t = c.tuning.value!!
        val strings = listOf(0.0, 0.04, -0.03).mapIndexed { i, cts -> SyntheticString.strike(t.targetHz * 2.0.pow(cts / 1200), t.b, 3.0, seed = 3 + i) }
        TappedSource.signal = FloatArray(strings[0].size) { i -> strings.sumOf { it[i].toDouble() }.toFloat() / 3 }
        val seen = HashMap<Int, MutableList<Double>>()
        TappedSource.afterHop = { pos -> if (pos > 48_000) for (lp in c.livePartials.value) seen.getOrPut(lp.k) { ArrayList() } += lp.hz }
        c.startLive()
        assertTrue(seen.size >= 4, "partials shown: ${seen.keys}")
        for ((k, hz) in seen) {
            val swing = hz.max() - hz.min()
            assertTrue(swing < 0.06 * k, "partial $k swings %.3f Hz over the strike".format(swing))
        }
    }
}

/** Plays [signal] and calls [afterHop] with the position after every buffer. */
private object TappedSource : AudioSource {
    override val sampleRateHz = 48_000
    var signal = FloatArray(0)
    var afterHop: (Int) -> Unit = {}
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        val buf = FloatArray(bufferSize); var p = 0
        while (p + bufferSize <= signal.size) { signal.copyInto(buf, 0, p, p + bufferSize); onBuffer(buf); p += bufferSize; afterHop(p) }
    }
    override fun stop() = Unit
}
