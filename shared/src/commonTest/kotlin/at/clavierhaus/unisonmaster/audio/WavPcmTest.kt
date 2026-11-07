package at.clavierhaus.unisonmaster.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WavPcmTest {

    private fun wav(
        format: Int, bits: Int, channels: Int, rate: Int, body: ByteArray,
        extraChunk: Boolean = false,
    ): ByteArray {
        val out = ArrayList<Byte>()
        fun str(s: String) = s.forEach { out.add(it.code.toByte()) }
        fun u32(v: Int) { for (i in 0 until 4) out.add(((v shr (8 * i)) and 0xFF).toByte()) }
        fun u16(v: Int) { for (i in 0 until 2) out.add(((v shr (8 * i)) and 0xFF).toByte()) }
        str("RIFF"); u32(0); str("WAVE")
        if (extraChunk) { str("LIST"); u32(4); str("INFO") }   // a chunk before fmt, as some writers do
        str("fmt "); u32(16); u16(format); u16(channels); u32(rate)
        u32(rate * channels * bits / 8); u16(channels * bits / 8); u16(bits)
        str("data"); u32(body.size); body.forEach { out.add(it) }
        return out.toByteArray()
    }

    @Test
    fun readsSixteenBitMono() {
        val body = ByteArray(8)
        // +16384 then -16384, little-endian
        body[0] = 0x00; body[1] = 0x40
        body[2] = 0x00; body[3] = 0xC0.toByte()
        body[4] = 0x00; body[5] = 0x00
        body[6] = 0xFF.toByte(); body[7] = 0x7F
        val pcm = WavPcm.read(wav(1, 16, 1, 48_000, body))
        assertEquals(48_000, pcm.sampleRateHz)
        assertEquals(4, pcm.samples.size)
        assertTrue(abs(pcm.samples[0] - 0.5f) < 1e-6)
        assertTrue(abs(pcm.samples[1] + 0.5f) < 1e-6)
        assertEquals(0f, pcm.samples[2])
        assertTrue(pcm.samples[3] > 0.999f)
    }

    @Test
    fun takesTheLeftChannelOfAStereoFile() {
        // frames: (L=+16384, R=-16384), (L=0, R=+16384)
        val body = byteArrayOf(0x00, 0x40, 0x00, 0xC0.toByte(), 0x00, 0x00, 0x00, 0x40)
        val pcm = WavPcm.read(wav(1, 16, 2, 48_000, body))
        assertEquals(2, pcm.samples.size)
        assertTrue(abs(pcm.samples[0] - 0.5f) < 1e-6)
        assertEquals(0f, pcm.samples[1])
    }

    @Test
    fun readsThirtyTwoBitFloat() {
        val v = 0.25f.toRawBits()
        val body = ByteArray(4) { ((v shr (8 * it)) and 0xFF).toByte() }
        val pcm = WavPcm.read(wav(3, 32, 1, 44_100, body))
        assertEquals(44_100, pcm.sampleRateHz)
        assertTrue(abs(pcm.samples[0] - 0.25f) < 1e-9)
    }

    @Test
    fun walksPastAChunkItDoesNotKnow() {
        val body = byteArrayOf(0x00, 0x40)
        val pcm = WavPcm.read(wav(1, 16, 1, 48_000, body, extraChunk = true))
        assertEquals(1, pcm.samples.size)
        assertTrue(abs(pcm.samples[0] - 0.5f) < 1e-6)
    }

    @Test
    fun secondsAreTheLengthOfTheRecording() {
        val body = ByteArray(2 * 4800)
        assertEquals(0.1, WavPcm.read(wav(1, 16, 1, 48_000, body)).seconds, 1e-9)
    }

    @Test
    fun refusesWhatItCannotPlayByName() {
        assertFailsWith<WavPcm.NotSupported> { WavPcm.read(ByteArray(100)) }
        // 24-bit PCM: common in studios, never written here
        val e = assertFailsWith<WavPcm.NotSupported> {
            WavPcm.read(wav(1, 24, 1, 48_000, ByteArray(30)))
        }
        assertTrue(e.message!!.contains("24"), "the refusal names the format: ${e.message}")
    }
}
