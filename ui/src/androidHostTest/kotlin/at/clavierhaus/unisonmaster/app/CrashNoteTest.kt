package at.clavierhaus.unisonmaster.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import at.clavierhaus.unisonmaster.i18n.Strings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A crash is kept and said on the next start (27 Oct 2026): the app's error goes into the note with the version, the
 * phone handles the crash as it did before, the next start shows the note with Copy and Close, and Close forgets it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashNoteTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val before = Thread.getDefaultUncaughtExceptionHandler()

    @After fun restore() { Thread.setDefaultUncaughtExceptionHandler(before); CrashNote.clear(context) }

    @Test fun aCrashIsKeptAndThePhoneStillHandlesIt() {
        var handed: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> handed = e }
        CrashNote.install(context, "0.5.1")
        val boom = IllegalStateException("boom in the capture thread")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), boom)
        val note = CrashNote.read(context)!!
        assertTrue(note, note.startsWith("Clavier Tuner 0.5.1\n"))
        assertTrue(note, "IllegalStateException: boom in the capture thread" in note)
        assertEquals("the phone's own handler still gets the crash", boom, handed)
    }

    @Test fun theNextStartShowsTheNoteAndCloseForgetsIt() {
        Strings.use("en")
        context.filesDir.mkdirs()
        java.io.File(context.filesDir, "last-crash.txt").writeText(CrashNote.report("0.5.1", "main", RuntimeException("x"), 0L))
        rule.setContent { CrashNoteDialog() }
        rule.onNodeWithText("The app stopped last time").assertIsDisplayed()
        rule.onNodeWithText("Copy").assertIsDisplayed()
        rule.onNodeWithText("Close").performClick()
        rule.waitForIdle()
        assertNull(CrashNote.read(context))
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("Copy")).fetchSemanticsNodes().size)
    }
}
