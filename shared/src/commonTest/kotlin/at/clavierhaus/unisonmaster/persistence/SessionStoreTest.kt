package at.clavierhaus.unisonmaster.persistence

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.tuning.MeasuredPartial
import at.clavierhaus.unisonmaster.tuning.NoteMeasurement
import at.clavierhaus.unisonmaster.tuning.TuningSession
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Test-only keyed checksum; the app uses the Android Keystore. */
private class TestSealer(key: String) : Sealer {
    private val k = key.encodeToByteArray()
    private fun mac(data: ByteArray): ByteArray {
        var h = -0x340d631b7bdddcdbL
        for (b in k + data) { h = h xor (b.toLong() and 0xff); h *= 0x100000001b3L }
        return ByteArray(8) { (h ushr (56 - 8 * it)).toByte() }
    }
    override fun seal(plain: ByteArray) = mac(plain) + plain
    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.size >= 8)
        val plain = sealed.copyOfRange(8, sealed.size)
        require(mac(plain).contentEquals(sealed.copyOfRange(0, 8))) { "tampered" }
        return plain
    }
}

private class MemoryFile : SaveFile {
    var bytes: ByteArray? = null
    override fun read() = bytes
    override fun writeAtomic(bytes: ByteArray) { this.bytes = bytes.copyOf() }
    override fun setAside() { bytes = null }
    override fun delete() { bytes = null }
}

private const val SR = 48_000
private const val HOP = 4096

private fun strike(f1: Double, b: Double, seconds: Double): FloatArray {
    val f0 = f1 / sqrt(1 + b)
    return FloatArray(HOP * 2) + FloatArray((seconds * SR).toInt()) { i ->
        val t = i.toDouble() / SR
        var v = 0.0
        for (k in 1..8) v += 0.2 / k * sin(2 * PI * k * f0 * sqrt(1 + b * k * k) * t + k)
        (v * exp(-t / 4.0)).toFloat()
    }
}

private class Queue(private val signals: List<FloatArray>) : AudioSource {
    override val sampleRateHz = SR
    private var n = 0
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        val s = signals[n++]
        var pos = 0
        val buf = FloatArray(bufferSize)
        while (pos + bufferSize <= s.size) { s.copyInto(buf, 0, pos, pos + bufferSize); onBuffer(buf); pos += bufferSize }
    }
    override fun stop() = Unit
}

private val sample = SessionSnapshot(
    savedAtMs = 1_789_000_000_000L,
    a4Hz = 443.0,
    currentMidi = 66,
    measurements = listOf(
        NoteMeasurement(69, 443.02, 4.1e-4, 0.12, listOf(MeasuredPartial(1, 0.0, 0.0, 3.2), MeasuredPartial(2, 0.713, -4.5, 2.9)), 1_789_000_000_000L),
        NoteMeasurement(68, 418.11, 3.9e-4, 0.31, listOf(MeasuredPartial(1, 0.0, 0.0, 2.7)), 1_789_000_100_000L),
    ),
)

class SessionStoreTest {

    @Test
    fun theFormatRoundTripsExactly() {
        assertEquals(sample, SessionCodec.decode(SessionCodec.encode(sample)))
    }

    @Test
    fun theFormatIsStrict() {
        val good = SessionCodec.encode(sample)
        assertFailsWith<SessionCodec.FormatException> { SessionCodec.decode(good.replace("session 1", "session 2")) }
        assertFailsWith<SessionCodec.FormatException> { SessionCodec.decode(good.replace("a4 443.0", "a4 443.0 1")) }
        assertFailsWith<SessionCodec.FormatException> { SessionCodec.decode(good.replace("a4 443.0", "a4 999.0")) }
        assertFailsWith<SessionCodec.FormatException> { SessionCodec.decode(good.replace("end\n", "")) }
        assertFailsWith<SessionCodec.FormatException> { SessionCodec.decode(good + "extra\n") }
        assertFailsWith<SessionCodec.FormatException> { SessionCodec.decode(good.replace(" 2\np 1", " 3\np 1")) }
    }

    @Test
    fun aSealedSaveLoads() {
        val file = MemoryFile()
        val store = SessionStore(file, TestSealer("device-key"))
        assertIs<SessionStore.Load.None>(store.load())
        assertTrue(store.save(sample))
        assertEquals(SessionStore.Load.Ok(sample), store.load())
    }

    @Test
    fun newDiscardsTheSavedTuning() {
        val file = MemoryFile()
        val store = SessionStore(file, TestSealer("device-key"))
        store.save(sample)
        assertIs<SessionStore.Load.Ok>(store.load())
        assertTrue(store.clear())
        assertIs<SessionStore.Load.None>(store.load())
        assertTrue(store.clear(), "clearing twice is harmless")
    }

    @Test
    fun anyChangedByteIsRejectedAndSetAside() {
        val file = MemoryFile()
        val store = SessionStore(file, TestSealer("device-key"))
        store.save(sample)
        val original = file.bytes!!
        for (pos in listOf(0, 9, original.size / 2, original.size - 1)) {
            file.bytes = original.copyOf().also { it[pos] = (it[pos].toInt() xor 1).toByte() }
            assertIs<SessionStore.Load.Rejected>(store.load(), "byte $pos changed")
            assertEquals(null, file.bytes, "set aside")
        }
        file.bytes = original.copyOf(original.size - 3)
        assertIs<SessionStore.Load.Rejected>(store.load(), "truncated")
        file.bytes = original
        assertIs<SessionStore.Load.Rejected>(SessionStore(file, TestSealer("another-device")).load(), "sealed elsewhere")
    }

    @Test
    fun aSessionContinuesWhereItWasLeft() {
        val a4 = 441.0
        val g4 = TuningSession.targetF1(68, a4)
        val saved = ArrayList<SessionSnapshot>()
        val first = TuningController(Queue(listOf(strike(a4, 4e-4, 3.0), strike(g4, 4.2e-4, 3.0))), clock = { 42L })
        first.onSessionChanged = { saved.add(it) }
        first.startLive(); first.acceptLive(); first.stopLive()
        first.startLive(); assertNotNull(first.acceptLive()); first.stopLive()
        val snap = saved.last()
        assertEquals(42L, snap.savedAtMs)
        assertEquals(67, snap.currentMidi)
        assertEquals(setOf(69, 68), snap.measurements.map { it.midi }.toSet())

        val file = MemoryFile()
        SessionStore(file, TestSealer("k")).save(snap)
        val loaded = assertIs<SessionStore.Load.Ok>(SessionStore(file, TestSealer("k")).load()).snapshot

        val second = TuningController(Queue(emptyList()))
        second.restore(loaded)
        val view = assertNotNull(second.tuning.value)
        assertEquals(67, view.midi)
        assertEquals(setOf(69, 68), view.measured)
        assertEquals(a4, second.referenceA4Hz.value)
        val before = first.tuning.value!!
        assertTrue(abs(view.targetHz - before.targetHz) < 1e-9, "target ${view.targetHz} vs ${before.targetHz}")
        assertEquals(before.predicted.map { it.k }, view.predicted.map { it.k })
        for ((p, q) in before.predicted.zip(view.predicted)) assertTrue(abs(p.hz - q.hz) < 1e-9)
    }
}
