package at.clavierhaus.unisonmaster.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.OctaveSuggestion
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

    private fun shows(text: String, substring: Boolean = false) = rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

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

    /**
     * The sampling's suggestion for the bass (10 Nov 2026) stands in the window with its reason, and Use sets the bass's
     * type, its end and its place A2 with six points on, leaving the middle and the treble; with nothing to suggest from,
     * the window says what to sample.
     */
    @Test fun theSuggestionIsSaidWithItsReasonAndUseSetsTheBass() {
        Strings.use("en")
        val sug = OctaveSuggestion.Suggestion(OctaveType.O10_5, mapOf(OctaveType.O4_2 to 9.58, OctaveType.O6_3 to 7.62, OctaveType.O10_5 to 3.12), 5, 33)
        var s = base.withSixPoints(true)
        rule.setContent { TuningInfoDialog(s, { ch -> s = ch(s) }, {}, suggestion = sug, sampled = true) }
        rule.waitForIdle()
        assertTrue("the suggestion", shows("Suggested for the bass: 10:5"))
        rule.onNode(hasText("Use 10:5")).performClick()
        rule.waitForIdle()
        assertEquals(OctaveType.O10_5, s.octaveBass)
        assertEquals(OctaveType.O10_5, s.stretchPoints.first { it.midi == 45 }.type)
        assertEquals(listOf(OctaveType.O2_1, OctaveType.O4_1), s.stretchPoints.filter { it.midi > 57 }.map { it.type })
        assertEquals(OctaveType.O4_2, s.octaveMiddle)
    }

    @Test fun withNoOctaveSampledTheWindowSaysWhatToSample() {
        Strings.use("en")
        rule.setContent { TuningInfoDialog(base, {}, {}, suggestion = null, sampled = true) }
        rule.waitForIdle()
        assertTrue(shows("No suggestion for the bass yet"))
    }

    @Test fun theLineOverTheTuningScreenSaysAllSixTypes() {
        Strings.use("en")
        val line = tuningInfoLine(base.withSixPoints(true), 440.0)
        assertTrue(line, line.endsWith("6:3 | 6:3 | 4:2 | 2:1 | 4:1 | 4:1"))
        assertTrue(tuningInfoLine(base, 440.0).endsWith("6:3 | 4:2 | 4:1"))
    }
}
