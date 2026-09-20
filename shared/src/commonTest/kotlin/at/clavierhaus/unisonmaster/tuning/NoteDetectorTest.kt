package at.clavierhaus.unisonmaster.tuning

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SR = 48_000

/**
 * A key of the compass as the microphone hears it, unmuted: three strings
 * a little apart, a stiff string's partials, a bass string's weak
 * fundamental and dense upper partials, a treble string's few, the room's
 * rumble and a little noise. Every value is chosen to be unkind to a
 * detector, not to a listener.
 */
private fun key(midi: Int, a4: Double = 442.0, seconds: Double = 1.0, seed: Int = midi): FloatArray {
    val f1 = a4 * 2.0.pow((midi - 69) / 12.0)
    val b = 1.0e-4 * 2.0.pow((midi - 40) / 10.0)
    val rnd = Random(seed)
    val strings = listOf(-0.0015, 0.0, 0.0012)               // a unison two and a half cents wide
    val count = when { midi < 40 -> 30; midi < 60 -> 16; midi < 84 -> 8; else -> 3 }
    val amps = DoubleArray(count + 1) { k ->
        if (k == 0) 0.0 else when {
            midi < 40 -> if (k == 1) 0.06 else 0.5 / sqrt(k.toDouble())   // wound bass: p1 faint, p2..p5 loud
            midi < 60 -> 0.5 / k
            else -> 0.6 / (k * k)
        }
    }
    return FloatArray((seconds * SR).toInt()) { i ->
        val t = i.toDouble() / SR
        var v = 0.0
        for (s in strings) for (k in 1..count) {
            val f = k * f1 * (1 + s) * sqrt(1 + b * k * k)
            if (f < SR / 2.2) v += amps[k] / 3 * sin(2 * PI * f * t + k + s * 100) * exp(-t * (0.3 + 0.15 * k))
        }
        // the room: a rumble at 47 and 118 Hz, 30 dB under the note, and noise
        v += 0.015 * sin(2 * PI * 47 * t) + 0.012 * sin(2 * PI * 118 * t + 1) + 0.002 * (rnd.nextDouble() * 2 - 1)
        v.toFloat()
    }
}

/** The detector's answer on the frame that begins at [fromS]. */
private fun read(signal: FloatArray, fromS: Double = 0.15, a4: Double = 442.0): Int? {
    val tracker = PartialTracker(SR, 16384, 12)
    val start = (fromS * SR).toInt()
    tracker.spectrum(signal.copyOfRange(start, start + 16384))
    return tracker.detectKey(NoteDetector(SR, 16384), a4)
}

class NoteDetectorTest {

    @Test
    fun everyKeyOfTheCompassIsReadAsItself() {
        val wrong = (21..108).mapNotNull { midi -> val r = read(key(midi)); if (r != midi) "$midi→$r" else null }
        assertTrue(wrong.isEmpty(), "misread: $wrong")
    }

    @Test
    fun aBassStringWithAFaintFundamentalIsNotReadAnOctaveOrATwelfthUp() {
        for (midi in listOf(24, 28, 33, 36)) assertEquals(midi, read(key(midi)), "C1 / E1 / A1 / C2 with partial 1 twenty dB under partial 2")
    }

    @Test
    fun aTrebleStringWithThreePartialsIsReadAsItself() {
        for (midi in listOf(93, 100, 105, 108)) assertEquals(midi, read(key(midi)))
    }

    @Test
    fun theRoomAloneIsNotANote() {
        val rnd = Random(3)
        val room = FloatArray(SR) { i -> val t = i.toDouble() / SR; (0.015 * sin(2 * PI * 47 * t) + 0.012 * sin(2 * PI * 118 * t) + 0.002 * (rnd.nextDouble() * 2 - 1)).toFloat() }
        assertNull(read(room))
    }

    @Test
    fun theKeyIsNamedOnTheSessionsReference() {
        // the same string read with A4 at 442 is A4; named on 415 it is the key a semitone up
        val a4 = key(69, a4 = 442.0)
        assertEquals(69, read(a4, a4 = 442.0))
        assertEquals(70, read(a4, a4 = 417.0))
    }
}
