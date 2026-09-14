package at.clavierhaus.unisonmaster.unison

import at.clavierhaus.unisonmaster.dsp.StringModel
import at.clavierhaus.unisonmaster.model.CapturedString
import at.clavierhaus.unisonmaster.model.StringSlot
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Tests: unison state
 *
 * These need no audio source, no `TuningController` and no coroutine. That
 * is the point of the extraction: the arithmetic that has produced nearly
 * every measurement fault since I39 is now reachable directly.
 *
 * They describe **I48 behaviour**, including the two-of-three fallback that
 * makes the partial row track the decay. That fallback is a known fault with
 * its own commit still to come; pinning it here means the corrective commit
 * has to change a test on purpose, in the open, rather than quietly.
 */
class UnisonStateTest {

    private fun cap(slot: StringSlot, f0: Double, b: Double, tunable: Set<Int>) =
        CapturedString(slot, StringModel(f0, b), emptyMap(), tunable)

    @Test
    fun withEveryStringCapturedTheOfferedSetIsTheIntersection() {
        val caps = mapOf(
            StringSlot.LEFT to cap(StringSlot.LEFT, 220.0, 2.5e-4, (1..10).toSet()),
            StringSlot.CENTER to cap(StringSlot.CENTER, 220.1, 2.5e-4, (1..8).toSet()),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, 219.9, 2.5e-4, (1..12).toSet()),
        )
        val sets = UnisonState.sets(caps, liveTunable = (1..16).toSet(), slotCount = 3, suspectBFraction = 0.40)
        assertEquals((1..8).toSet(), sets.usable)
        assertEquals((1..12).toSet(), sets.candidates)
    }

    @Test
    fun aGreyedPartialNamesTheStringsThatLackIt() {
        val caps = mapOf(
            StringSlot.LEFT to cap(StringSlot.LEFT, 220.0, 2.5e-4, (1..10).toSet()),
            StringSlot.CENTER to cap(StringSlot.CENTER, 220.1, 2.5e-4, (1..8).toSet()),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, 219.9, 2.5e-4, (1..8).toSet()),
        )
        val sets = UnisonState.sets(caps, (1..16).toSet(), 3, 0.40)
        assertEquals(setOf(StringSlot.CENTER, StringSlot.RIGHT), sets.missingOn[9])
        assertTrue(9 in sets.candidates && 9 !in sets.usable)
    }

    /**
     * Pins the I48 fallback rather than endorsing it. Below three captures
     * the offered set comes from live audio, so it changes as the note
     * decays — the row collapsing from eleven partials to three between
     * strikes on the field recording of 2026-09-01.
     */
    @Test
    fun belowThreeCapturesTheOfferedSetStillComesFromLiveAudio() {
        val caps = mapOf(
            StringSlot.CENTER to cap(StringSlot.CENTER, 220.0, 2.5e-4, (1..10).toSet()),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, 220.1, 2.5e-4, (1..10).toSet()),
        )
        val justStruck = UnisonState.sets(caps, liveTunable = (1..11).toSet(), slotCount = 3, suspectBFraction = 0.40)
        val decayed = UnisonState.sets(caps, liveTunable = setOf(1, 2, 3), slotCount = 3, suspectBFraction = 0.40)
        assertEquals((1..11).toSet(), justStruck.usable)
        assertEquals(setOf(1, 2, 3), decayed.usable)
    }

    @Test
    fun theCentreStringHoldsTheReferenceWheneverItIsCaptured() {
        val target = 220.0
        val caps = mapOf(
            // RIGHT sits nearest equal temperament; CENTER does not.
            StringSlot.RIGHT to cap(StringSlot.RIGHT, 220.05, 2.5e-4, (1..8).toSet()),
            StringSlot.CENTER to cap(StringSlot.CENTER, 221.4, 2.5e-4, (1..8).toSet()),
        )
        assertEquals(StringSlot.CENTER, UnisonState.chooseReference(caps, target))
    }

    @Test
    fun withoutTheCentreStringTheRoleGoesToTheOneNearestTemperament() {
        val target = 220.0
        val caps = mapOf(
            StringSlot.LEFT to cap(StringSlot.LEFT, 218.6, 2.5e-4, (1..8).toSet()),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, 220.05, 2.5e-4, (1..8).toSet()),
        )
        assertEquals(StringSlot.RIGHT, UnisonState.chooseReference(caps, target))
    }

    @Test
    fun nothingCapturedMeansNoReference() {
        assertNull(UnisonState.chooseReference(emptyMap(), 220.0))
    }

    @Test
    fun inharmonicityFarFromTheMedianIsFlaggedOnlyWithThreeCaptures() {
        val odd = mapOf(
            StringSlot.LEFT to cap(StringSlot.LEFT, 220.0, 2.5e-4, (1..8).toSet()),
            StringSlot.CENTER to cap(StringSlot.CENTER, 220.0, 2.6e-4, (1..8).toSet()),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, 220.0, 9.0e-4, (1..8).toSet()),
        )
        assertEquals(setOf(StringSlot.RIGHT), UnisonState.sets(odd, emptySet(), 3, 0.40).suspectSlots)
        // With two captures one of them is wrong and nothing says which.
        val two = odd.filterKeys { it != StringSlot.LEFT }
        assertTrue(UnisonState.sets(two, emptySet(), 3, 0.40).suspectSlots.isEmpty())
    }

    @Test
    fun capturedBeatsAreTheWidestDisagreementAtEachPartial() {
        val caps = mapOf(
            StringSlot.LEFT to cap(StringSlot.LEFT, 220.0, 0.0, setOf(1)),
            StringSlot.CENTER to cap(StringSlot.CENTER, 220.5, 0.0, setOf(1)),
            StringSlot.RIGHT to cap(StringSlot.RIGHT, 221.0, 0.0, setOf(1)),
        )
        val sets = UnisonState.sets(caps, emptySet(), 3, 0.40)
        assertTrue(abs(sets.capturedBeatsHz.getValue(1) - 1.0) < 1e-9)
    }

    @Test
    fun agreementRejectsAWideInharmonicityDifferenceAtTheSamePitch() {
        val a = StringModel(220.0, 2.5e-4)
        val b = StringModel(220.0, 8.0e-4)
        assertTrue(!UnisonState.modelsAgree(a, b, agreeCents = 3.0, agreeBFraction = 0.35))
        assertTrue(UnisonState.modelsAgree(a, StringModel(220.1, 2.6e-4), 3.0, 0.35))
    }
}
