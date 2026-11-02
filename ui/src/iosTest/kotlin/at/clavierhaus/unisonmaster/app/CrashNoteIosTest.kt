@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package at.clavierhaus.unisonmaster.app

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iPhone keeps the last crash's note as Android does (CrashNoteTest): written to the app's folder, read back on
 * the next start, forgotten on Close. Before 2 Nov 2026 the iPhone read nothing, so the first check failed there.
 */
class CrashNoteIosTest {

    @Test
    fun aNoteIsKeptReadAndForgotten() {
        val folder = NSTemporaryDirectory() + "crash-note-" + Random.nextLong().toULong()
        CrashNote.install("9.9.9", folder)
        try {
            assertNull(CrashNote.read(), "a note before anything crashed")
            val note = CrashNote.report("9.9.9", "main", IllegalStateException("the string broke"), 1234L)
            CrashNote.keep(note)
            assertEquals(note, CrashNote.read())
            CrashNote.clear()
            assertNull(CrashNote.read(), "the note outlived Close")
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(folder, null)
        }
    }

    @Test
    fun theWindowShowsTheVersionTheMomentAndTheError() {
        val shown = CrashNote.extract(CrashNote.report("9.9.9", "main", IllegalStateException("the string broke"), 1234L))
        assertEquals("Clavier Tuner 9.9.9", shown[0])
        assertTrue(shown[1].isNotBlank(), "no system named")
        assertEquals("time 1234, thread main", shown[3])
        assertTrue(shown[4].contains("the string broke"), shown[4])
    }
}
