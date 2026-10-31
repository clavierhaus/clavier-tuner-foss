package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.tuning.measuredString
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sampling ended with Done moves samplingDone; a tuning restored past its sampling does not (31 Oct 2026). */
class SamplingDoneTest {
    private object Silent : AudioSource {
        override val sampleRateHz = 48_000
        override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) = Unit
        override fun stop() = Unit
    }

    @Test fun aRestoredTuningDoesNotCountAsTheSamplingEnded() {
        val c = TuningController(Silent)
        c.restore(SessionSnapshot(0, 440.0, 57, emptyList(), sampling = false))
        assertEquals(0, c.samplingDone.value)
        c.finishSampling()
        assertEquals(0, c.samplingDone.value, "nothing to finish: no sampling under way")
    }

    @Test fun doneAtTheEndOfTheSamplingMovesIt() {
        val c = TuningController(Silent)
        val keys = listOf(69, 21, 28, 33, 40, 45, 52, 57, 64, 76, 88)
        c.restore(SessionSnapshot(0, 440.0, 69, keys.map { measuredString(it, 440.0 * 2.0.pow((it - 69) / 12.0), 2e-4) }, sampling = true))
        assertEquals(0, c.samplingDone.value)
        c.finishSampling()
        assertEquals(1, c.samplingDone.value)
    }
}
