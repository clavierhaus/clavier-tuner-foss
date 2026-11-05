package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.persistence.SessionCodec
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.tuning.measuredString
import kotlin.math.abs
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object Silence : AudioSource {
    override val sampleRateHz = 48_000
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) = Unit
    override fun stop() = Unit
}

/**
 * A tuning keeps its own stretch (5 Nov 2026, Peter, after a treble tuned at 3:1 on the 225 was to be corrected to
 * 4:1): Settings › Stretch is the default for the tunings to come, the hub's window changes the tuning under way, and
 * the tuning is saved and continued with its own.
 */
class TuningStretchTest {
    private val a4 = 443.1
    private val defaults = TunerSettings(lowestKeyMidi = 17, lowestUnwoundMidi = 40, octaveTreble = OctaveType.O3_1)
    private val c7 = 96

    private fun et(midi: Int) = TuningSession.targetF1(midi, a4)
    private fun cents(a: Double, b: Double) = 1200 * log2(a / b)

    // a sampling with the treble's inharmonicity of a grand, so that 3:1 and 4:1 put C7 in different places
    private val samples = listOf(
        measuredString(36, et(36), 0.6e-4), measuredString(48, et(48), 1.1e-4),
        measuredString(60, et(60), 2.2e-4), measuredString(69, a4, 4.0e-4),
        measuredString(81, et(81), 1.2e-3), measuredString(93, et(93), 4.0e-3),
    )
    private fun tuning(saved: SessionSnapshot = SessionSnapshot(0, a4, 60, samples, sampling = false)): TuningController {
        val c = TuningController(Silence) { 1000L }
        c.applySettings(defaults)
        c.restore(saved)
        return c
    }

    @Test
    fun aChangeToTheDefaultsMovesNoTargetOfATuningUnderWay() {
        val c = tuning()
        val before = assertNotNull(c.computedTargetF1(c7))
        c.applySettings(defaults.copy(octaveTreble = OctaveType.O4_1, widthTreble = 3.0, temperamentLowMidi = 50))
        assertEquals(before, assertNotNull(c.computedTargetF1(c7)), "C7 stays where the tuning's own 3:1 put it")
        assertEquals(OctaveType.O3_1, c.settings.value.octaveTreble, "the screens show the tuning's stretch")
        // what is not the stretch still reaches the tuning at once
        c.applySettings(defaults.copy(octaveTreble = OctaveType.O4_1, matchCents = 0.7))
        assertEquals(0.7, c.settings.value.matchCents)
    }

    @Test
    fun theTuningsOwnChangeMovesItsTargetsAndIsSavedAndContinuedWithIt() {
        val c = tuning()
        val at31 = assertNotNull(c.computedTargetF1(c7))
        assertTrue(c.changeTuning { it.copy(octaveTreble = OctaveType.O4_1) })
        // the window's switch, the piano's size, is the tuning's too: it stays as switched, with the bass it recommends
        c.changeTuning { it.withLongGrand(true) }
        assertTrue(c.settings.value.longGrand); assertEquals(OctaveType.O10_5, c.settings.value.octaveBass)
        c.changeTuning { it.withLongGrand(false) }
        val at41 = assertNotNull(c.computedTargetF1(c7))
        assertTrue(abs(cents(at41, at31)) > 0.5, "4:1 puts C7 elsewhere than 3:1: ${cents(at41, at31)} c")
        // saved and continued under other defaults, the tuning keeps its 4:1
        val text = SessionCodec.encode(assertNotNull(c.snapshot()))
        val again = TuningController(Silence) { 1000L }
        again.applySettings(defaults.copy(octaveTreble = OctaveType.O2_1))
        again.restore(SessionCodec.decode(text))
        assertEquals(OctaveType.O4_1, again.settings.value.octaveTreble)
        assertFalse(again.settings.value.longGrand)
        assertEquals(at41, assertNotNull(again.computedTargetF1(c7)), 1e-9)
    }

    @Test
    fun aNewTuningTakesTheDefaultsAndWithoutATuningAChangeIsTheDefaults() {
        val c = tuning()
        c.changeTuning { it.copy(octaveTreble = OctaveType.O4_1) }
        c.resetSession()
        assertEquals(OctaveType.O3_1, c.settings.value.octaveTreble, "after the tuning, the defaults")
        assertFalse(c.changeTuning { it.copy(octaveTreble = OctaveType.O4_2) }, "no tuning: the app changes its defaults")
        assertEquals(OctaveType.O3_1, c.settings.value.octaveTreble)
    }

    @Test
    fun aTuningSavedBeforeItsStretchWasKeptTakesTheDefaultsInForce() {
        val old = SessionCodec.decode(SessionCodec.encode(SessionSnapshot(0, a4, 60, samples, sampling = false)))
        assertNull(old.stretch)
        val c = tuning(old)
        assertEquals(defaults.stretch(), assertNotNull(c.snapshot()).stretch, "from then on it keeps them")
    }

    @Test
    fun aStretchOutOfRangeIsDroppedAndTheRestOfTheFileKept() {
        val good = SessionCodec.encode(SessionSnapshot(0, a4, 60, samples, sampling = false, stretch = defaults.stretch()))
        assertEquals(defaults.stretch(), SessionCodec.decode(good).stretch)
        val bad = good.replace(Regex("stretch \\d+ "), "stretch 99 ")
        val read = SessionCodec.decode(bad)
        assertNull(read.stretch)
        assertEquals(samples.size, read.measurements.size)
    }
}
