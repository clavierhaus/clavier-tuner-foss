package at.clavierhaus.unisonmaster.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioTapTest {
    /** A source that hands its buffers over the moment it is started. */
    private class Played(private val buffers: List<FloatArray>, private val fails: Boolean = false) : AudioSource {
        override val sampleRateHz = 48_000
        var stopped = 0
        override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
            if (fails) throw IllegalStateException("no microphone")
            for (b in buffers) onBuffer(b)
        }
        override fun stop() { stopped++ }
    }

    @Test
    fun everyTapHearsEveryBufferInOrder() {
        val a = FloatArray(4) { 1f }; val b = FloatArray(4) { 2f }
        val pump = AudioTap(Played(listOf(a, b)))
        val first = ArrayList<Float>(); val second = ArrayList<Float>()
        pump.add { first += it[0] }
        pump.add { second += it[0] }
        assertTrue(pump.start())
        assertTrue(pump.listening.value)
        assertEquals(listOf(1f, 2f), first)
        assertEquals(listOf(1f, 2f), second)
    }

    @Test
    fun stopClosesTheSourceOnceAndClearsTheFlag() {
        val src = Played(emptyList())
        val pump = AudioTap(src)
        pump.start(); pump.stop(); pump.stop()
        assertFalse(pump.listening.value)
        assertEquals(1, src.stopped)
    }

    @Test
    fun aSourceThatCannotOpenLeavesThePumpNotListening() {
        val pump = AudioTap(Played(emptyList(), fails = true))
        assertFalse(pump.start())
        assertFalse(pump.listening.value)
    }
}
