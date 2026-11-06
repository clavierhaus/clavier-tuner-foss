package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.measure.EngineRate
import at.clavierhaus.unisonmaster.measure.SyntheticString
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.TuningSession
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Plays what it is given at [sampleRateHz], one signal per start(), synchronously; [afterBuffer] sees the time after each buffer. */
private class RatedSource(override val sampleRateHz: Int) : AudioSource {
    var signal = FloatArray(0)
    var afterBuffer: ((Double) -> Unit)? = null
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        var pos = 0
        val buf = FloatArray(bufferSize)
        while (pos + bufferSize <= signal.size) {
            signal.copyInto(buf, 0, pos, pos + bufferSize)
            onBuffer(buf)
            pos += bufferSize
            afterBuffer?.invoke(pos.toDouble() / sampleRateHz)
        }
    }
    override fun stop() = Unit
}

/**
 * The engine reads a string the same at 48 kHz and at 96 kHz (6 Nov 2026, Peter: "keep 96 throughout", for Pro's
 * audio interface): the same synthetic strings, made at each rate, give the same A4, the same key caught at the
 * same moment, and the same f1 and B kept. Until that day the engine's hop and windows were counts of samples, so
 * at 96 kHz every step and window lasted half as long and the sampling caught its key at other moments.
 */
class EngineRateTest {
    private val a4 = 443.1
    private fun et(midi: Int) = TuningSession.targetF1(midi, a4)

    private class Run(val a4: Double, val caughtAtS: Map<Int, Double>, val f1: Map<Int, Double>, val b: Map<Int, Double>)

    private fun sample(rate: Int): Run {
        val src = RatedSource(rate)
        val c = TuningController(src) { 1000L }
        c.applySettings(TunerSettings(lowestKeyMidi = 21, temperamentFirst = false))
        fun hear(signal: FloatArray) { c.stopLive(); src.signal = signal; assertTrue(c.startLive()) }
        hear(SyntheticString.strike(a4, 8e-4, 2.5, sampleRate = rate))
        val heardA4 = assertNotNull(c.acceptLive())
        val caughtAt = HashMap<Int, Double>()
        // A0 twelve cents flat, E2 eight sharp, C5: the bass's close partials, the middle, the treble
        for ((midi, cents, b) in listOf(Triple(21, -12.0, 1.2e-4), Triple(40, 8.0, 1.9e-4), Triple(72, 0.0, 9e-4))) {
            var at: Double? = null
            src.afterBuffer = { s -> if (at == null && c.tuning.value?.caught == midi) at = s }
            hear(SyntheticString.strike(et(midi) * 2.0.pow(cents / 1200), b, 3.0, sampleRate = rate))
            src.afterBuffer = null
            assertEquals(midi, c.tuning.value?.caught, "$rate Hz: caught")
            caughtAt[midi] = assertNotNull(at, "$rate Hz: caught while it sounded")
            assertNotNull(c.acceptLive(), "$rate Hz: kept")
        }
        val m = c.measurements()
        return Run(heardA4, caughtAt, caughtAt.keys.associateWith { m[it]!!.f1Hz }, caughtAt.keys.associateWith { m[it]!!.b })
    }

    @Test
    fun theSameStringsReadTheSameAt48And96Kilohertz() {
        assertEquals(listOf(1, 2, 4, 1, 1), listOf(48_000, 96_000, 192_000, 44_100, 88_200).map { EngineRate.scaleOf(it) })
        val at48 = sample(48_000)
        val at96 = sample(96_000)
        assertEquals(at48.a4, at96.a4, 0.01, "A4")
        val hopS = 1024.0 / 48_000
        for (midi in at48.caughtAtS.keys) {
            assertTrue(abs(at48.caughtAtS[midi]!! - at96.caughtAtS[midi]!!) <= hopS + 1e-9,
                "key $midi caught at ${at48.caughtAtS[midi]} s at 48 kHz and ${at96.caughtAtS[midi]} s at 96 kHz")
            assertEquals(0.0, TuningSession.centsOff(at96.f1[midi]!!, at48.f1[midi]!!), 0.05, "key $midi: f1")
            assertTrue(abs(at96.b[midi]!! / at48.b[midi]!! - 1) < 0.02, "key $midi: B ${at48.b[midi]} at 48 kHz, ${at96.b[midi]} at 96 kHz")
        }
    }
}
