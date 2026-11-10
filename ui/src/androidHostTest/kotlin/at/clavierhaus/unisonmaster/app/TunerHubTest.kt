package at.clavierhaus.unisonmaster.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.ui.HomeHubItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A door that starts over replaced the tuning that could be continued without a word (Peter, 9 Nov 2026: "Confirmation
 * is good"). Now it asks while there is one to lose, and only then.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TunerHubTest {
    @get:Rule val rule = createComposeRule()
    private val saved = SessionStore.Load.Ok(SessionSnapshot(savedAtMs = 1L, a4Hz = 443.0, currentMidi = 69, measurements = emptyList(), sampling = true))
    private var started = 0

    private fun hub(last: SessionStore.Load, extra: List<HomeHubItem> = emptyList()) {
        Strings.use("en")
        rule.setContent { TunerHub(last = last, onContinue = {}, onNew = { started++ }, onSettings = {}, extra = extra) }
        rule.waitForIdle()
    }

    private fun tap(words: String) {
        rule.onNodeWithText(words, substring = true).performClick()
        rule.waitForIdle()
    }

    private fun asked() = rule.onAllNodes(hasText("Start a new tuning?")).fetchSemanticsNodes().isNotEmpty()

    @Test fun aSavedTuningIsNotReplacedBeforeTheTunerSaysSo() {
        hub(saved)
        tap("New Tuning")
        assertTrue("the question is on the screen", asked())
        assertEquals("nothing started on the first tap", 0, started)
        tap("Cancel")
        assertFalse("the question is gone", asked())
        assertEquals("Cancel starts nothing", 0, started)
        tap("New Tuning")
        tap("Start new")
        assertFalse("the question is gone", asked())
        assertEquals("the yes starts it, once", 1, started)
    }

    @Test fun withNothingSavedItStartsAtOnce() {
        hub(SessionStore.Load.None)
        tap("New Tuning")
        assertFalse(asked())
        assertEquals(1, started)
    }

    /** False Beats (10 Nov 2026) checks strings for the tuning saved and replaces nothing: it opens without a question. */
    @Test fun theFalseBeatsDoorOpensWithoutAsking() {
        var checks = 0
        Strings.use("en")
        rule.setContent { TunerHub(last = saved, onContinue = {}, onNew = { started++ }, onSettings = {}, onFalseBeats = { checks++ }) }
        rule.waitForIdle()
        tap("False Beats")
        assertFalse(asked())
        assertEquals(1, checks)
        assertEquals(0, started)
    }

    @Test fun anEditionsOwnDoorAsksOnlyIfItStartsOver() {
        var own = 0
        var other = 0
        hub(saved, listOf(HomeHubItem("Own", "", startsOver = true) { own++ }, HomeHubItem("Other", "") { other++ }))
        tap("Other")
        assertFalse("a door that starts nothing over asks nothing", asked())
        assertEquals(1, other)
        tap("Own")
        assertTrue(asked())
        assertEquals(0, own)
        tap("Start new")
        assertEquals(1, own)
        assertEquals("New Tuning's own action was not the one run", 0, started)
    }
}
