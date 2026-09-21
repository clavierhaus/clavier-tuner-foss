package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SR = 48_000

private fun readAll(reader: PhaseReader, signal: FloatArray, hop: Int = 1024, fromS: Double = 0.0): List<PhaseReader.Reading> {
    val out = ArrayList<PhaseReader.Reading>()
    var pos = 0
    val buf = FloatArray(hop)
    while (pos + hop <= signal.size) {
        signal.copyInto(buf, 0, pos, pos + hop)
        val r = reader.push(buf)
        pos += hop
        if (r != null && pos.toDouble() / SR >= fromS) out += r
    }
    return out
}

class PhaseReaderTest {

    @Test
    fun readsAPartialAgainstTheReferenceToAHundredthOfAHertz() {
        val f = 440.37
        val sig = FloatArray(SR * 2) { i -> (0.2 * sin(2 * PI * f * i / SR)).toFloat() }
        val rs = readAll(PhaseReader(SR, 440.0), sig, fromS = 1.0)
        assertTrue(rs.size > 40, "a reading every hop: ${rs.size}")
        val last = rs.last()
        assertTrue(abs(last.hz - f) < 0.01, "read %.3f for %.2f (quality %.2f, level %.1f)".format(last.hz, f, last.quality, last.levelDbfs))
        assertTrue(abs(last.cents - 1.455) < 0.05, "cents %.3f".format(last.cents))
        assertTrue(last.quality > 0.8, "quality ${last.quality}")
        assertTrue(rs.all { abs(it.hz - f) < 0.02 }, "steady after the memory has filled: ${rs.map { "%.3f".format(it.hz) }.take(5)}")
    }

    @Test
    fun readsTheBottomOfTheBassAndTheTopOfTheTreble() {
        for ((target, actual) in listOf(131.0 to 130.6, 4186.0 to 4190.5, 165.0 to 165.4)) {
            val sig = FloatArray(SR * 3) { i -> (0.1 * sin(2 * PI * actual * i / SR)).toFloat() }
            val last = readAll(PhaseReader(SR, target), sig, fromS = 2.0).last()
            assertTrue(abs(last.hz - actual) < 0.02, "target $target: read %.3f for %.2f".format(last.hz, actual))
        }
    }

    @Test
    fun aComponentOutsideTheBandIsNotRead() {
        // the neighbouring semitone, alone: nothing in the band, quality nil
        val sig = FloatArray(SR * 2) { i -> (0.2 * sin(2 * PI * 466.16 * i / SR)).toFloat() }
        val last = readAll(PhaseReader(SR, 440.0), sig, fromS = 1.0).last()
        assertTrue(last.levelDbfs < -14 - 30, "the band rejects a semitone away: %.1f dBFS for a −14 dBFS tone".format(last.levelDbfs))
    }

    @Test
    fun twoStringsABeatApartReadAsTheBeatNotAsAWanderingNumber() {
        // a unison 0.3 Hz apart: the phase against the reference of the sum swings at the beat
        // rate; the reading follows it — that is the beat, shown — and its mean over a full beat
        // is the stronger string
        val sig = FloatArray(SR * 8) { i ->
            val t = i.toDouble() / SR
            (0.2 * sin(2 * PI * 440.0 * t) + 0.1 * sin(2 * PI * 440.3 * t + 1.0)).toFloat()
        }
        val rs = readAll(PhaseReader(SR, 440.0), sig, fromS = 1.0)
        val mean = rs.map { it.hz }.average()
        assertTrue(abs(mean - 440.0) < 0.03, "the mean over beats is the stronger string: %.3f".format(mean))
        val swing = rs.maxOf { it.hz } - rs.minOf { it.hz }
        assertTrue(swing > 0.1 && swing < 0.6, "the beat shows as a swing of the reading: %.2f Hz".format(swing))
    }

    @Test
    fun theMapIsTheAccuTuners() {
        assertEquals(6, PartialMap.listening(17))     // F0
        assertEquals(6, PartialMap.listening(47))     // B2
        assertEquals(4, PartialMap.listening(48))     // C3
        assertEquals(4, PartialMap.listening(69))     // A4
        assertEquals(2, PartialMap.listening(72))     // C5
        assertEquals(1, PartialMap.listening(84))     // C6
        assertEquals(1, PartialMap.listening(108))
    }
}
