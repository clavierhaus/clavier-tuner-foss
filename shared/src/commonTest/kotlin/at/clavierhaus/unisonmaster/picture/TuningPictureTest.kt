package at.clavierhaus.unisonmaster.picture

import at.clavierhaus.unisonmaster.picture.TuningPicture.State
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TuningPictureTest {
    private val f1 = 174.0; private val b = 2.1e-4
    private fun series(f0: Double, ks: IntRange = 1..8) = ks.associateWith { k -> k * f0 * Inharmonicity.ratio(k, b) }
    private val targets = series(f1)

    /** A string whose every partial stands [cents] off its target, plus the extras given, levels falling with k. */
    private fun frame(
        cents: Double, nowMs: Long, extra: Map<Int, Double> = emptyMap(), live: Boolean = true,
        falseP: Set<Int> = emptySet(), dropped: Set<Int> = emptySet(), listened: Int = 4,
    ): TuningPicture.Frame {
        val partials = targets.mapValues { (k, t) -> (t * 2.0.pow((cents + (extra[k] ?: 0.0)) / 1200.0)) to (1.0 / k) }
        return TuningPicture.Frame("F3", listened, live, partials[listened]?.first, targets[listened]!!,
            partials, targets, falseP, dropped, 1.5, nowMs)
    }

    @Test
    fun aCleanStringStacksOnOneLineWhereverThePitchIs() {
        val p = TuningPicture.Builder().build(frame(+10.0, 0))
        assertEquals(8, p.bells.size)
        assertTrue(p.bells.all { abs(it.offsetCents) < 1e-9 }, "every bell at zero from the anchor: ${p.bells.map { it.offsetCents }}")
        assertEquals(10.0, p.pointerCents!!, 1e-9, "the scale alone carries the pitch")
        assertEquals(4, p.anchor); assertEquals(State.SOUNDING, p.state)
    }

    @Test
    fun onlyAnOffSeriesPartialLeavesTheLineAndAFalseOneIsMarked() {
        val p = TuningPicture.Builder().build(frame(-2.0, 0, extra = mapOf(2 to 3.0), falseP = setOf(2)))
        assertEquals(3.0, p.bells.first { it.k == 2 }.offsetCents, 1e-9)
        assertTrue(p.bells.filter { it.k != 2 }.all { abs(it.offsetCents) < 1e-9 })
        assertEquals(setOf(2), p.falseMarked)
        assertTrue(!p.bells.first { it.k == 2 }.counted)
    }

    @Test
    fun theAnchorLeavesAFalseListenedPartialOnlyAfterTheHoldAndNeverFlaps() {
        val bld = TuningPicture.Builder()
        bld.build(frame(0.0, 0))
        val p1 = bld.build(frame(0.0, 100, falseP = setOf(4)))
        assertEquals(4, p1.anchor, "a false reading on one frame does not move the line")
        val p2 = bld.build(frame(0.0, 700, falseP = setOf(4)))
        assertEquals(1, p2.anchor, "after the hold the strongest counted partial carries the line")
        assertEquals(4, p2.listened, "the partial being tuned does not change")
        val p3 = bld.build(frame(0.0, 800))
        assertEquals(1, p3.anchor, "and it does not flap back the moment the listened partial is clean again")
        assertEquals(4, bld.build(frame(0.0, 1400)).anchor)
    }

    @Test
    fun nearIsNotOnAndOnIsEarnedByTheHold() {
        val bld = TuningPicture.Builder()
        assertEquals(State.NEAR, bld.build(frame(0.5, 0)).state)
        assertEquals(State.NEAR, bld.build(frame(0.5, 300)).state)
        assertEquals(State.ON, bld.build(frame(0.5, 500)).state)
        assertEquals(State.SOUNDING, bld.build(frame(3.0, 600)).state, "leaving the gate ends it")
        assertEquals(State.NEAR, bld.build(frame(0.5, 700)).state, "and the hold starts again")
    }

    @Test
    fun aDroppedPartialDoesNotVetoOn() {
        val bld = TuningPicture.Builder()
        bld.build(frame(0.2, 0, extra = mapOf(6 to 20.0), dropped = setOf(6)))
        val p = bld.build(frame(0.2, 600, extra = mapOf(6 to 20.0), dropped = setOf(6)))
        assertEquals(State.ON, p.state)
        assertEquals(setOf(6), p.dropped); assertTrue(p.bells.first { it.k == 6 }.counted.not())
        assertEquals(20.0, p.bells.first { it.k == 6 }.offsetCents, 1e-9, "the dropped partial is still drawn where it stands")
    }

    @Test
    fun thePictureDiesWithItsSound() {
        val bld = TuningPicture.Builder()
        bld.build(frame(0.2, 0)); bld.build(frame(0.2, 600))
        val gone = bld.build(frame(0.2, 700, live = false))
        assertEquals(State.SILENT, gone.state); assertTrue(gone.bells.isEmpty()); assertNull(gone.pointerCents)
        assertEquals(State.NEAR, bld.build(frame(0.2, 800)).state, "a new strike earns its own hold")
    }
}
