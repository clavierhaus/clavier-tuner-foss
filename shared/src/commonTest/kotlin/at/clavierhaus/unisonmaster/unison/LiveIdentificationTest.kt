package at.clavierhaus.unisonmaster.unison

import at.clavierhaus.unisonmaster.dsp.StringModel
import at.clavierhaus.unisonmaster.model.CapturedString
import at.clavierhaus.unisonmaster.model.StringSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Tests: live identification
 *
 * Every test here that matters uses a **well-tuned** unison. Identification
 * succeeds when the strings are far apart and fails when they are close, so a
 * suite built on detuned strings passes while the field fails — which is what
 * happened for forty-nine iterations. See §8.3 of ENGINEERING.md.
 */
class LiveIdentificationTest {

    private val f0 = 262.0 // C4
    private val b = 3.2e-4
    private val maxCents = 45.0

    /** Cents offset converted to a fundamental, for readable test setup. */
    private fun at(cents: Double) =
        StringModel(f0 * kotlin.math.exp(cents / 1200.0 * kotlin.math.ln(2.0)), b)

    private fun cap(slot: StringSlot, cents: Double) =
        CapturedString(slot, at(cents), emptyMap(), (1..10).toSet())

    /**
     * The field case, 2026-09-01: an untouched C4, three strings inside a
     * cent. The left string is struck. Its reading lands nearer the centre
     * string's model than its own, because the separation between them is
     * smaller than the fit residual.
     *
     * Attributing the strike to CENTER is worse than attributing it to
     * nothing: it moves a mark that must stay still and freezes the one that
     * should move.
     */
    @Test
    fun onAWellTunedUnisonAnAmbiguousReadingIdentifiesNothing() {
        val captured = mapOf(
            StringSlot.CENTER to cap(StringSlot.CENTER, 0.0),
            StringSlot.LEFT to cap(StringSlot.LEFT, -0.45),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, +0.40),
        )
        // The left string sounding, measured with ordinary noise: 0.13 ¢ from
        // CENTER, 0.32 ¢ from LEFT. Nearest is the wrong string.
        val liveHz = at(-0.13).partialHz(1)
        val slot = LiveIdentification.identify(
            liveHz = liveHz,
            partial = 1,
            captured = captured,
            currentModels = emptyMap(),
            referenceSlot = StringSlot.CENTER,
            maxCents = maxCents,
            uncertaintyCents = 0.3,
        )
        assertNull(slot, "identified $slot on a unison too clean to separate")
    }

    /**
     * The same geometry with the reference excluded still leaves LEFT and
     * RIGHT indistinguishable. Excluding the reference does not make the
     * remaining pair separable.
     */
    @Test
    fun excludingTheReferenceDoesNotMakeTheRestSeparable() {
        val captured = mapOf(
            StringSlot.CENTER to cap(StringSlot.CENTER, 0.0),
            StringSlot.LEFT to cap(StringSlot.LEFT, -0.20),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, +0.20),
        )
        val slot = LiveIdentification.identify(
            liveHz = at(0.0).partialHz(1), 1, captured, emptyMap(),
            StringSlot.CENTER, maxCents, uncertaintyCents = 0.5,
        )
        assertNull(slot)
    }

    /** A detuned unison separates cleanly, which is why this always worked. */
    @Test
    fun aDetunedStringIsIdentifiedNormally() {
        val captured = mapOf(
            StringSlot.CENTER to cap(StringSlot.CENTER, 0.0),
            StringSlot.LEFT to cap(StringSlot.LEFT, -14.0),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, +9.0),
        )
        val slot = LiveIdentification.identify(
            liveHz = at(-13.6).partialHz(1), 1, captured, emptyMap(),
            StringSlot.CENTER, maxCents, uncertaintyCents = 0.3,
        )
        assertEquals(StringSlot.LEFT, slot)
    }

    /**
     * A string part-way through being tuned in sounds far from its own
     * capture. Matching on captures alone gave its sound to its neighbour, so
     * the present position wins where one exists.
     */
    @Test
    fun aStringPartWayTunedIsMatchedOnWhereItIsNow() {
        val captured = mapOf(
            StringSlot.CENTER to cap(StringSlot.CENTER, 0.0),
            StringSlot.LEFT to cap(StringSlot.LEFT, -14.0),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, +9.0),
        )
        // LEFT has been brought most of the way in; RIGHT has not moved.
        val current = mapOf(StringSlot.LEFT to at(-2.0))
        val slot = LiveIdentification.identify(
            liveHz = at(-2.1).partialHz(1), 1, captured, current,
            StringSlot.CENTER, maxCents, uncertaintyCents = 0.3,
        )
        assertEquals(StringSlot.LEFT, slot)
    }

    /** A single candidate needs nothing to beat, and is returned. */
    @Test
    fun aSingleCandidateIsIdentifiedWithoutASeparationTest() {
        val captured = mapOf(
            StringSlot.CENTER to cap(StringSlot.CENTER, 0.0),
            StringSlot.LEFT to cap(StringSlot.LEFT, 0.05),
        )
        val slot = LiveIdentification.identify(
            liveHz = at(0.04).partialHz(1), 1, captured, emptyMap(),
            StringSlot.CENTER, maxCents, uncertaintyCents = 5.0,
        )
        assertEquals(StringSlot.LEFT, slot)
    }

    /** A wrong note or a knock belongs to no captured string. */
    @Test
    fun aReadingFarFromEveryStringIdentifiesNothing() {
        val captured = mapOf(
            StringSlot.CENTER to cap(StringSlot.CENTER, 0.0),
            StringSlot.LEFT to cap(StringSlot.LEFT, -14.0),
        )
        val slot = LiveIdentification.identify(
            liveHz = at(300.0).partialHz(1), 1, captured, emptyMap(),
            StringSlot.CENTER, maxCents, uncertaintyCents = 0.3,
        )
        assertNull(slot)
    }

    /**
     * Drift detection deliberately does not apply the separation rule: it
     * only counts frames, and acts after several of them.
     */
    @Test
    fun driftDetectionStillAnswersWhenTheStringsAreClose() {
        val captured = mapOf(
            StringSlot.CENTER to cap(StringSlot.CENTER, 0.0),
            StringSlot.LEFT to cap(StringSlot.LEFT, -0.20),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, +0.20),
        )
        val slot = LiveIdentification.nearestByFrequency(
            liveHz = at(+0.18).partialHz(1), 1, captured, emptyMap(),
            referenceSlot = StringSlot.CENTER,
        )
        assertEquals(StringSlot.RIGHT, slot)
    }
}
