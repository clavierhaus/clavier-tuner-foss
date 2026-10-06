package at.clavierhaus.unisonmaster.picture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Auto mode's four bands: the fundamental, the two next loudest, and always the
 * highest partial that is audible and defined (Peter, 6 Oct 2026).
 */
class AutoFourTest {
    private fun bell(k: Int, level: Double, depth: Double?) =
        TuningPicture.Bell(k, 0.0, 0.0, level, true, 100.0 * k, 100.0 * k, null, depth)

    @Test
    fun theFundamentalTheTwoNextLoudestAndTheHighestThatIsDefined() {
        val bells = listOf(
            bell(1, 1.00, 40.0), bell(2, 0.86, 38.0), bell(3, 0.50, 30.0),
            bell(4, 0.72, 33.0), bell(5, 0.44, 28.0), bell(6, 0.20, 10.0),
        )
        // 1 is the fundamental; 6 is too quiet to be audible; 5 is the highest audible and defined;
        // of what is left, 2 and 4 are the loudest
        assertEquals(listOf(1, 2, 4, 5), TuningPicture.autoFour(bells))
    }

    /**
     * The level scale is the engine's: `1 + (db − loudest) / 48`. So 0.375 is 30 dB down —
     * the line — and 0.1 is 43.2 dB down, which is why it gets no band. An earlier comment
     * here called 0.1 "30 dB down"; it is not, and the figure was never wrong, only the words.
     */
    @Test
    fun aPartialTooQuietToHearGetsNoBand() {
        val bells = listOf(bell(1, 1.0, 40.0), bell(2, 0.8, 35.0), bell(3, 0.1, 35.0))
        assertTrue(3 !in TuningPicture.autoFour(bells), "43 dB down is not a band")
        assertEquals(-30.0, 48.0 * (TuningPicture.AUDIBLE_LEVEL - 1.0), 1e-9, "the line is 30 dB down")
    }

    /**
     * A band already drawn keeps its seat until it falls a margin below the line it entered at.
     * Without that, a partial sitting on the threshold enters and leaves on alternate frames and
     * the row reflows mid-note, exactly when the eye is trying to read motion.
     */
    @Test
    fun aBandOnTheThresholdDoesNotFlicker() {
        val enter = TuningPicture.AUDIBLE_LEVEL
        val below = enter - TuningPicture.HYSTERESIS_LEVEL / 2      // under the line, inside the margin
        val loud = listOf(bell(1, 1.0, 40.0), bell(2, 0.9, 38.0), bell(4, 0.8, 36.0))

        val fresh = TuningPicture.autoFour(loud + bell(6, below, 30.0))
        assertTrue(6 !in fresh, "under the line, it never gets in")

        val held = TuningPicture.autoFour(loud + bell(6, below, 30.0), previous = setOf(1, 2, 4, 6))
        assertTrue(6 in held, "already drawn, it keeps its seat inside the margin")

        val gone = TuningPicture.autoFour(loud + bell(6, enter - TuningPicture.HYSTERESIS_LEVEL * 2, 30.0), previous = setOf(1, 2, 4, 6))
        assertTrue(6 !in gone, "past the margin it goes")
    }

    @Test
    fun aLoudPartialAtTheFloorMayStillBeDrawnButNeverAsTheHighest() {
        // 5 is loud but stands only 8 dB over the noise: not defined, so it cannot be the highest pick
        val bells = listOf(bell(1, 1.0, 40.0), bell(2, 0.9, 38.0), bell(3, 0.8, 36.0), bell(5, 0.85, 8.0))
        val four = TuningPicture.autoFour(bells)
        assertEquals(listOf(1, 2, 3, 5), four, "it is still among the loudest")
        // and with nothing defined above the fundamental the list is simply the loudest
        val none = listOf(bell(1, 1.0, 5.0), bell(2, 0.9, 4.0), bell(3, 0.8, 3.0))
        assertEquals(listOf(1, 2, 3), TuningPicture.autoFour(none))
    }

    @Test
    fun aStrikeWithThreePartialsGivesThreeBandsAndNeverInventsOne() {
        val bells = listOf(bell(1, 1.0, 40.0), bell(2, 0.8, 35.0), bell(4, 0.6, 30.0))
        assertEquals(listOf(1, 2, 4), TuningPicture.autoFour(bells))
        assertEquals(emptyList(), TuningPicture.autoFour(emptyList()))
    }
}
