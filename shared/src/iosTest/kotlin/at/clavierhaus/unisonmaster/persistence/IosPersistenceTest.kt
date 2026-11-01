package at.clavierhaus.unisonmaster.persistence

import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The iPhone's saved tuning (1 Nov 2026), run on the simulator in CI: the file is written whole, read back,
 * set aside and removed; the seal opens what it sealed and refuses a file changed by one bit. Each test is
 * one that fails if the part it checks does nothing: a pass-through sealer opens the changed file, and a
 * save file that keeps nothing reads back null.
 */
class IosPersistenceTest {
    private fun tempDir() = NSTemporaryDirectory() + "clavier-test-" + NSUUID().UUIDString

    @Test
    fun theFileIsWrittenReadSetAsideAndRemoved() {
        val f = IosSaveFile(tempDir())
        assertNull(f.read())
        f.writeAtomic(byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), f.read())
        f.writeAtomic(byteArrayOf(4, 5))
        assertContentEquals(byteArrayOf(4, 5), f.read())
        f.setAside()
        assertNull(f.read())
        assertContentEquals(byteArrayOf(4, 5), f.readSetAside())
        f.clearSetAside()
        assertNull(f.readSetAside())
        f.writeAtomic(byteArrayOf(9))
        f.delete()
        assertNull(f.read())
    }

    @Test
    fun theSealOpensWhatItSealedAndRefusesAChange() {
        val s = KeychainSealer("at.clavierhaus.claviertuner.test")
        val plain = "A4 440.0 and every note measured".encodeToByteArray()
        val sealed = s.seal(plain)
        assertEquals(1.toByte(), sealed[0])
        assertContentEquals(plain, s.open(sealed))
        // not the plain text: the bytes on disk say nothing of the tuning
        assertEquals(false, sealed.decodeToString().contains("A4 440.0"))
        val changed = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFailsWith<IllegalStateException> { s.open(changed) }
    }

    @Test
    fun aSessionSurvivesTheStoreBeingMadeAgain() {
        val dir = tempDir()
        val sealer = KeychainSealer("at.clavierhaus.claviertuner.test")
        val snap = SessionSnapshot(savedAtMs = 1L, a4Hz = 440.0, currentMidi = 69, measurements = emptyList())
        SessionStore(IosSaveFile(dir), sealer).save(snap)
        val back = SessionStore(IosSaveFile(dir), sealer).load()
        assertEquals(SessionStore.Load.Ok(snap), back)
    }
}
