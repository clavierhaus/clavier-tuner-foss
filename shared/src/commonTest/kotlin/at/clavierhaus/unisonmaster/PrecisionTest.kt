package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.dsp.PreciseF0
import at.clavierhaus.unisonmaster.dsp.Yin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class ChunkedSource(
    override val sampleRateHz: Int,
    private val signal: FloatArray,
) : AudioSource {
    private var stopped = false
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        var pos = 0
        val buffer = FloatArray(bufferSize)
        while (!stopped && pos + bufferSize <= signal.size) {
            signal.copyInto(buffer, 0, pos, pos + bufferSize)
            onBuffer(buffer)
            pos += bufferSize
        }
    }
    override fun stop() { stopped = true }
}

class PrecisionTest {

    private val sr = 48_000

    @Test
    fun phaseRefinementReachesMillihertz() {
        val target = 443.170
        val buf = FloatArray(16384) { (0.3 * sin(2.0 * PI * target * it / sr)).toFloat() }
        val coarse = Yin.estimateF0(buf, sr.toDouble(), 380.0, 500.0)
        assertNotNull(coarse)
        // YIN alone may be off by a substantial fraction of a Hz...
        val refined = PreciseF0.refine(buf, sr.toDouble(), coarse, 4096)
        // ...phase refinement must land within 5 mHz.
        assertTrue(
            abs(refined - target) < 0.005,
            "refined $refined, expected $target (coarse was $coarse)",
        )
    }
}
