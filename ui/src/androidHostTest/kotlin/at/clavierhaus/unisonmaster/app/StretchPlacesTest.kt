package at.clavierhaus.unisonmaster.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.settings.TunerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The six-point stretch has to be seen (Peter, 9 Nov 2026: "It is as it was, bass, midrange and treble ... it must be
 * obvious for a tuner"): with it on, the window behind the info line shows its six places instead of the three regions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StretchPlacesTest {
    @get:Rule val rule = createComposeRule()
    private val places = listOf("A0", "A2", "A3–A4", "F5", "A6", "C8")
    private val base = TunerSettings(lowestKeyMidi = 21)

    private fun shows(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test fun withSixPointsOnTheWindowShowsThePlaces() {
        Strings.use("en")
        rule.setContent { TuningInfoDialog(base.withSixPoints(true), {}, {}) }
        rule.waitForIdle()
        for (p in places) assertTrue("the place $p is on the screen", shows(p))
        assertFalse("the regions have made way", shows("Bass"))
    }

    @Test fun withSixPointsOffTheWindowShowsTheRegions() {
        Strings.use("en")
        rule.setContent { TuningInfoDialog(base, {}, {}) }
        rule.waitForIdle()
        assertTrue(shows("Bass")); assertTrue(shows("Midrange")); assertTrue(shows("Treble"))
        assertFalse(shows("A2"))
    }

    @Test fun aNotesRegionOpensThePageItIsSetOn() {
        assertEquals(listOf(1, 2, 3), (0..2).map { stretchPageOf(it, base) })
        assertEquals(listOf(1, 1, 1), (0..2).map { stretchPageOf(it, base.withSixPoints(true)) })
    }

    @Test fun theLineOverTheTuningScreenSaysAllSixTypes() {
        Strings.use("en")
        val line = tuningInfoLine(base.withSixPoints(true), 440.0)
        assertTrue(line, line.endsWith("6:3 | 6:3 | 4:2 | 2:1 | 4:1 | 4:1"))
        assertTrue(tuningInfoLine(base, 440.0).endsWith("6:3 | 4:2 | 4:1"))
    }
}
