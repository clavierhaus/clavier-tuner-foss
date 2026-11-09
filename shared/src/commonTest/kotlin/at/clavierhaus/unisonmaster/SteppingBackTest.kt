package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.tuning.measuredString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object Quiet : AudioSource {
    override val sampleRateHz = 48_000
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) = Unit
    override fun stop() = Unit
}

/**
 * Going back before tuning (Peter, 9 Nov 2026: a tester could not back up and change anything): every step before the
 * first tuned note can be gone back to and nothing measured is lost. Rule 9 from the other side: once a note is tuned
 * the way back is shut and the targets hold.
 */
class SteppingBackTest {
    private val a4 = 440.0
    private fun et(m: Int) = TuningSession.targetF1(m, a4)
    private val keys = listOf(33, 40, 45, 52, 57, 64, 76, 81, 88)
    private val samples = listOf(measuredString(69, a4, 4e-4)) + keys.map { measuredString(it, et(it), 1e-4 * (1 + (it - 33) / 20.0)) }

    private fun controller(sampling: Boolean, tuned: Set<Int> = setOf(69)): TuningController {
        val c = TuningController(Quiet) { 1000L }
        c.applySettings(TunerSettings())
        c.restore(SessionSnapshot(0, a4, 69, samples, sampling = sampling, tuned = tuned))
        return c
    }

    private fun bOf(c: TuningController) = assertNotNull(c.snapshot()).measurements.associate { it.midi to it.b }

    @Test
    fun backToA4KeepsEverySampleAndDoneNeedsNoNewStrike() {
        val c = controller(sampling = true)
        val before = bOf(c)
        assertTrue(c.backToA4())
        assertNull(c.tuning.value, "the A4 step shows no session")
        assertTrue(c.a4Status.value.ready, "A4 is measured already: Done is open")
        assertEquals(a4, c.acceptLive())
        val v = assertNotNull(c.tuning.value)
        assertTrue(v.sampling); assertEquals(keys.size, v.samplesIn)
        assertEquals(before, bOf(c))
    }

    @Test
    fun aNewPitchForA4KeepsTheSamplesEvenASemitoneAway() {
        val c = controller(sampling = true)
        val before = bOf(c)
        assertTrue(c.backToA4())
        c.setReference(415.0)
        assertEquals(415.0, c.acceptLive())
        assertEquals(keys.size, assertNotNull(c.tuning.value).samplesIn, "no sample goes for a new pitch")
        assertEquals(before, bOf(c))
        assertEquals(415.0, assertNotNull(c.snapshot()).a4Hz)
    }

    @Test
    fun theTuningsStretchIsKeptOnTheWayBack() {
        val c = controller(sampling = true)
        assertTrue(c.changeTuning { it.copy(octaveTreble = OctaveType.O3_1) })
        assertTrue(c.backToA4())
        assertNotNull(c.acceptLive())
        assertEquals(OctaveType.O3_1, c.settings.value.octaveTreble, "what the tuner set for this tuning stays")
    }

    @Test
    fun fromTheTuningBackToSamplingUntilANoteIsTuned() {
        val c = controller(sampling = false)
        val before = bOf(c)
        assertTrue(assertNotNull(c.tuning.value).canStepBack)
        assertTrue(c.backToSampling())
        val v = assertNotNull(c.tuning.value)
        assertTrue(v.sampling); assertEquals(keys.size, v.samplesIn)
        assertEquals(before, bOf(c))
        // the sampling goes on: another Done lays the basis down again
        c.finishSampling()
        assertFalse(assertNotNull(c.tuning.value).sampling)
        assertNotNull(c.computedTargetF1(60))
    }

    @Test
    fun fromTheTuningStraightBackToA4() {
        val c = controller(sampling = false)
        assertTrue(c.backToA4())
        assertNull(c.tuning.value)
        assertNotNull(c.acceptLive())
        val v = assertNotNull(c.tuning.value)
        assertTrue(v.sampling, "the tuner comes forward to the sampling, with what it holds")
        assertEquals(keys.size, v.samplesIn)
    }

    /** Rule 9: the first note tuned shuts the way back, and no target moves. */
    @Test
    fun oneNoteTunedShutsTheWayBackAndTheTargetsHold() {
        val c = controller(sampling = false, tuned = setOf(69, 64))
        val target = assertNotNull(c.computedTargetF1(60))
        assertFalse(assertNotNull(c.tuning.value).canStepBack)
        assertFalse(c.backToSampling())
        assertFalse(c.backToA4())
        assertNotNull(c.tuning.value, "still tuning")
        assertEquals(target, assertNotNull(c.computedTargetF1(60)), 1e-12)
    }

    /** Home from the A4 step: the session is saved all the same, and Continue comes back to the sampling. */
    @Test
    fun leavingTheA4StepLosesNothing() {
        val c = controller(sampling = true)
        assertTrue(c.backToA4())
        val saved = assertNotNull(c.snapshot(), "the session waits and is saved")
        assertEquals(samples.size, saved.measurements.size)
        val again = TuningController(Quiet) { 1000L }
        again.restore(saved)
        assertTrue(assertNotNull(again.tuning.value).sampling)
        assertEquals(keys.size, assertNotNull(again.tuning.value).samplesIn)
    }

    @Test
    fun newTuningForgetsTheSessionThatWaited() {
        val c = controller(sampling = true)
        assertTrue(c.backToA4())
        c.resetSession()
        assertNull(c.snapshot())
        assertFalse(c.a4Status.value.ready)
        assertNull(c.acceptLive(), "nothing is taken up: A4 has to be measured afresh")
    }
}
