package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
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

class PhaseReaderStrikeTest {
    @Test
    fun aStrikeRestartsTheMemoryAndTheReadingSettlesOnTheNewString() {
        // one string, then a second strike of the same string pulled 3 cents sharp:
        // after the strike the reader holds (settling), then reads the new pitch
        // without dragging the old one through its memory
        val a = SyntheticString.strike(220.0, 0.0, 2.0, partials = 1, decayS = 0.6)
        val b = SyntheticString.strike(220.38, 0.0, 2.0, partials = 1, strikeS = 0.0, amplitude = 0.3)
        val sig = a + b
        val reader = PhaseReader(SR, 220.0)
        val rs = readAll(reader, sig)
        // a strike may straddle two hops and be heard in both
        assertTrue(reader.strikes in 2..3, "two strikes heard: ${reader.strikes}")
        val afterSecond = rs.drop((2.0 * SR / 1024).toInt())
        assertTrue(afterSecond.take(3).all { it.settling }, "settling right after the strike")
        val firstShown = afterSecond.indexOfFirst { it.shown }
        // The skip is SETTLE_TIME_CONSTANTS (five) time constants of each of the four stages, plus
        // the memory: a second at 220 Hz (the band is ±25 cents, 3.2 Hz), a quarter of that two
        // octaves up. Until 9 Oct 2026 this check pinned that number to 30-50 hops; what it is
        // for is the loop below -- the readings shown after the skip are the new string, not the
        // old one dragged through the memory and not the filter's transient -- so the range is
        // left wide and the loop does the checking. Halving the skip was tried on 9 Oct and
        // showed the transient on the Samick's B1 (+27 c for half a second): the five stay
        // (PhaseReader.SETTLE_TIME_CONSTANTS, SAMICK findings §24).
        assertTrue(firstShown in 15..50, "shown again after $firstShown hops")
        for (i in firstShown until minOf(firstShown + 5, afterSecond.size)) {
            assertTrue(abs(afterSecond[i].hz - 220.38) < 0.05, "reading ${i - firstShown} after the skip is the new string: %.3f".format(afterSecond[i].hz))
        }
    }

    /**
     * The room before a strike has its own faint content at the partial's
     * place, and the reader has a reading of it. The strike resets the
     * memory and skips the settle; the hop in which the skip ends may hold
     * no fresh rate yet, and that hop must not pass the pre-strike reading
     * off as a reading of the string. Seen on a synthetic F3 in a room floor
     * (3 Oct 2026): partial 8's first shown reading after the strike was the
     * room's −4.26 c, one hop long, and it alone spread the partial's window
     * to 0.97 c — a false beat called on a clean string.
     */
    @Test
    fun theReadingHeldFromBeforeAStrikeIsNeverShownAfterIt() {
        val target = 1401.7
        val before = target * 2.0.pow(-4.0 / 1200)
        val sig = FloatArray((1.2 * SR).toInt()) { i ->
            val t = i.toDouble() / SR
            var x = 0.002 * sin(2 * PI * before * t)
            if (t >= 0.3) x += 0.2 * (1 - exp(-(t - 0.3) / 0.002)) * sin(2 * PI * target * (t - 0.3))
            x.toFloat()
        }
        val reader = PhaseReader(SR, target)
        val rs = readAll(reader, sig, fromS = 0.3)
        assertTrue(reader.strikes >= 1, "the strike is heard")
        val shown = rs.filter { it.shown }
        assertTrue(shown.isNotEmpty(), "the string is shown after the strike")
        val worst = shown.maxByOrNull { abs(it.cents) }!!
        assertTrue(abs(worst.cents) < 0.5, "every shown reading after the strike is the string: %.2f c".format(worst.cents))
    }

    @Test
    fun noiseAloneIsNotShownAndAPartialAboveItIs() {
        val rng = kotlin.random.Random(3)
        val noise = FloatArray(SR * 2) { (0.02 * (rng.nextDouble() * 2 - 1)).toFloat() }
        assertTrue(readAll(PhaseReader(SR, 440.0), noise, fromS = 0.5).none { it.shown }, "noise is never shown")
        val tone = FloatArray(SR * 2) { i -> (noise[i] + 0.05 * sin(2 * PI * 440.2 * i / SR)).toFloat() }
        val rs = readAll(PhaseReader(SR, 440.0), tone, fromS = 0.5)
        assertTrue(rs.all { it.shown }, "a partial standing above the noise is shown")
        assertTrue(abs(rs.last().hz - 440.2) < 0.05)
    }

    @Test
    fun theReaderFollowsAMovingPin() {
        // the tuner turns the pin: partial 1 rises 1 Hz over two seconds
        val sig = SyntheticString.strike(220.0, 0.0, 3.0, partials = 1, strikeS = 0.0, decayS = 10.0, driftHzPerS = 0.5)
        val rs = readAll(PhaseReader(SR, 220.5), sig, fromS = 1.0)
        // the reading lags a moving string by the filter's delay and the memory,
        // together a few tenths of a second at this band, and by nothing more:
        // it rises as the string does, at the same rate
        for ((i, r) in rs.withIndex()) {
            val t = 1.0 + i * 1024.0 / SR
            val lag = t - (r.hz - 220.0) / 0.5
            assertTrue(lag in 0.15..0.45, "at %.2f s read %.3f: %.2f s behind".format(t, r.hz, lag))
        }
        val rate = (rs.last().hz - rs.first().hz) / ((rs.size - 1) * 1024.0 / SR)
        assertEquals(0.5, rate, 0.02)
    }

    /** A string struck at [strikeS] at [hz], a 30 ms noise burst on the strike, a 4 s decay: a tone whose pitch is known. */
    private fun struck(hz: Double, seconds: Double, strikeS: Double = 0.25): FloatArray {
        val rnd = kotlin.random.Random(1)
        return FloatArray((SR * seconds).toInt()) { i ->
            val t = (i - SR * strikeS) / SR
            if (t < 0) (1e-4 * (rnd.nextDouble() * 2 - 1)).toFloat()
            else (0.3 * exp(-t / 4.0) * sin(2 * PI * hz * t) + if (t < 0.03) 0.3 * (rnd.nextDouble() * 2 - 1) else 0.0).toFloat()
        }
    }

    /** The shown readings of [reader] over [signal], each with its time after the strike at [strikeS]. */
    private fun shown(reader: PhaseReader, signal: FloatArray, strikeS: Double = 0.25): List<Pair<Double, PhaseReader.Reading>> {
        val out = ArrayList<Pair<Double, PhaseReader.Reading>>()
        val buf = FloatArray(1024); var pos = 0
        while (pos + 1024 <= signal.size) {
            signal.copyInto(buf, 0, pos, pos + 1024); pos += 1024
            val r = reader.push(buf) ?: continue
            val t = pos.toDouble() / SR - strikeS
            if (t > 0 && r.shown) out += t to r
        }
        return out
    }

    /**
     * The strobe's reader (window, 17 Oct 2026; SAMICK findings §27). A string struck at 165 Hz, A0's
     * sixth partial, three cents sharp: the four stages read nothing for twenty of their time
     * constants after the strike, 1.33 s there; the window is read once it holds nothing from before
     * the strike, its own length, 0.41 s. Both read the string from their first reading on.
     */
    @Test
    fun theWindowReadsAStruckStringAsSoonAsItHoldsNothingFromBeforeTheStrike() {
        val f = 165.0; val sig = struck(f * 2.0.pow(3.0 / 1200), 3.0)
        val stages = shown(PhaseReader(SR, f), sig)
        val window = shown(PhaseReader(SR, f, window = true), sig)
        assertTrue(stages.first().first > 1.2, "the four stages wait: %.2f s".format(stages.first().first))
        assertTrue(window.first().first < 0.5, "the window is up %.2f s after the strike".format(window.first().first))
        for ((name, rs) in listOf("stages" to stages, "window" to window)) {
            val worst = rs.maxBy { abs(it.second.cents - 3.0) }
            assertTrue(abs(worst.second.cents - 3.0) < 0.05, "$name: %+.3f c at %.2f s for +3.00".format(worst.second.cents, worst.first))
        }
    }

    /**
     * The window's phase is read as often as the four stages' blocks, so a string far off its target
     * in the treble does not turn its phase past half a cycle between two readings: at C8 a hop of
     * 1024 samples would wrap at 23 Hz, 9.5 cents, where the band reaches 25 cents (a wrap reads
     * 60 cents off). Read from 1 s on: the test strike's 30 ms of noise fills the first windows at
     * C8, 33 ms long, as a hammer's would, and the reading's 0.15 s memory carries it for a while
     * (-20.105 c at 0.5 s). At 20 cents the string stands near the band's edge, where the window's
     * lobe must still reach.
     */
    @Test
    fun theWindowReadsATrebleStringFarOffItsTargetWithoutWrapping() {
        val f = 4186.0
        for (off in listOf(+20.0, -20.0, +8.0)) {
            val rs = shown(PhaseReader(SR, f, window = true), struck(f * 2.0.pow(off / 1200), 3.0)).filter { it.first >= 1.0 }
            assertTrue(rs.size > 40, "read at $off c: ${rs.size}")
            val worst = rs.maxBy { abs(it.second.cents - off) }
            assertTrue(abs(worst.second.cents - off) < 0.1, "%+.0f c off: read %+.3f".format(off, worst.second.cents))
        }
    }

    /**
     * A move to another partial mid-sound (the screen following a key the identifier has named)
     * reads again what the window has heard: the reading at the new partial is there within a hop or
     * two of the move, and right, where the four stages start empty.
     */
    @Test
    fun theWindowMovedToAnotherPartialReadsWhatItHasHeardAgain() {
        val sig = struck(330.0 * 2.0.pow(-2.0 / 1200), 2.0)
        val r = PhaseReader(SR, 311.1, window = true)
        val buf = FloatArray(1024); var pos = 0; var moved = -1.0; var first: Pair<Double, Double>? = null
        while (pos + 1024 <= sig.size) {
            sig.copyInto(buf, 0, pos, pos + 1024); pos += 1024
            val t = pos.toDouble() / SR - 0.25
            if (moved < 0 && t >= 0.55) { r.retarget(330.0); moved = t }
            val rd = r.push(buf) ?: continue
            if (moved >= 0 && first == null && rd.shown) first = t to rd.cents
        }
        val (at, cents) = first ?: error("never read after the move")
        assertTrue(at - moved < 0.07, "read %.3f s after the move".format(at - moved))
        assertEquals(-2.0, cents, 0.05)
    }
}
