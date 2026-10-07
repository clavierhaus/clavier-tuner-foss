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
        /** Each partial's width, cents; absent = not yet known. */
        widths: Map<Int, Double> = emptyMap(),
        /** Each partial's depth over the noise beside it, dB; absent = not known. */
        depths: Map<Int, Double> = emptyMap(),
    ): TuningPicture.Frame {
        val partials = targets.mapValues { (k, t) ->
            TuningPicture.Frame.Partial(t * 2.0.pow((cents + (extra[k] ?: 0.0)) / 1200.0), 1.0 / k, widths[k], depths[k])
        }
        return TuningPicture.Frame("F3", listened, live, partials[listened]?.hz, targets[listened]!!,
            partials, targets, falseP, dropped, 1.5, nowMs)
    }

    /**
     * The anchor belongs to the note and not to the strike.
     *
     * It used to be dropped the moment the sound stopped, so each blow chose
     * a line to stand on afresh; on a clean string, where the partials sit
     * within a cent or two of one another, that choice is decided by noise and
     * changed from strike to strike. Peter's recording of a clean G#4 (5 Oct
     * 2026, 15:47) alternates "bells on 3" and "bells on 2", and because every
     * bell's offset is measured from the anchor the whole picture slid sideways
     * each time, with nothing but its reference moving.
     */
    @Test
    fun theAnchorStandsWhileTheNoteDoesAndFallsWithIt() {
        val b = TuningPicture.Builder()
        // the first strike: partial 1 is the narrowest line to stand on, so the bells stand on it
        var p = b.build(frame(0.0, 0, widths = mapOf(1 to 0.2, 4 to 1.9), listened = 4))
        assertEquals(1, p.anchor)
        // the string dies away: the picture is empty, and nothing of the strike is shown
        p = b.build(frame(0.0, 600, live = false))
        assertEquals(State.SILENT, p.state)
        assertTrue(p.bells.isEmpty(), "nothing of the old strike is drawn")
        assertEquals(1, p.anchor, "but the line the bells stand on is the note's, not the strike's")
        // struck again, and this time partial 4 reads a shade narrower: the line does not move for that
        p = b.build(frame(0.0, 1200, widths = mapOf(1 to 0.25, 4 to 0.2), listened = 4))
        assertEquals(1, p.anchor, "the anchor held across the silence")
        // a new note starts with no line of its own
        val other = frame(0.0, 1800, widths = mapOf(1 to 0.2, 4 to 1.9), listened = 4)
        p = b.build(TuningPicture.Frame("G3", other.listened, true, other.listenedHz, other.listenedTargetHz,
            other.partials, other.targets, emptySet(), emptySet(), 1.5, 1800))
        assertEquals("G3", p.note)
        assertEquals(1, p.anchor, "chosen afresh for the new note")
    }

    /**
     * The width is how well a partial is read. A bell much wider than the
     * narrowest is wide: it neither anchors the line nor vetoes "on" — a
     * needle outranks a wide bell (blueprint §14, 3 Oct 2026). Measured on the
     * Samick: loud partials 0.2–0.5 c, floor-huggers 2–6 c.
     */
    @Test
    fun aWideBellNeitherAnchorsNorVetoesOn() {
        val b = TuningPicture.Builder()
        // first the string sounds with every partial a needle: the listened partial 4 anchors
        var p = b.build(frame(0.0, 0, widths = mapOf(1 to 0.2, 4 to 0.3)))
        assertEquals(4, p.anchor); assertTrue(p.wide.isEmpty())
        // then partials 4 and 8 have fallen to the floor, 2.5 and 3 c wide, 8 wandering 2 c off;
        // the rest are needles on the line (the pointer is the engine's combined reading, which
        // CleanReading already keeps off a partial at the floor — here it stands on the line)
        val widths = mapOf(1 to 0.2, 2 to 0.25, 3 to 0.3, 4 to 2.5, 5 to 0.3, 6 to 0.4, 7 to 0.5, 8 to 3.0)
        p = b.build(frame(0.0, 100, extra = mapOf(8 to -2.0), widths = widths))
        assertEquals(setOf(4, 8), p.wide, "the two at the floor are wide")
        assertEquals(4, p.anchor, "the anchor waits its hold before it moves")
        p = b.build(frame(0.0, 700, extra = mapOf(8 to -2.0), widths = widths))
        assertEquals(1, p.anchor, "then the strongest needle anchors, not the wide listened partial")
        assertTrue(p.bells.filter { it.k !in p.wide }.all { abs(it.offsetCents) < 1e-9 }, "the needles stand on the line")
        // 2 c off on a wide partial does not keep "on" from being earned by the needles and the pointer
        p = b.build(frame(0.0, 1300, extra = mapOf(8 to -2.0), widths = widths))
        assertEquals(State.ON, p.state, "a wide bell cannot veto on")
        // a loud partial half a cent wide is not wide: 0.5 c against needles of 0.2
        p = b.build(frame(0.0, 1400, widths = mapOf(1 to 0.2, 4 to 0.5)))
        assertTrue(p.wide.isEmpty(), "half a cent on a loud partial is still a line to stand on")
    }

    /**
     * A partial at the floor wanders slowly as well as fast: its last half
     * second can be narrow while it stands a cent and more from where it was.
     * The anchor is a reading that is trusted — standing ANCHOR_DEPTH_DB over
     * the noise beside it — not merely a narrow one (F3-C-s1 at 7.5 s through
     * the probe, 3 Oct 2026 17:45: partial 4 at 10 dB over the floor anchored
     * every needle 1.5 c off the line).
     */
    @Test
    fun aPartialAtTheFloorDoesNotAnchorHoweverNarrowItsLastHalfSecond() {
        val b = TuningPicture.Builder()
        var p = b.build(frame(0.0, 0, widths = mapOf(1 to 0.1, 4 to 0.1), depths = mapOf(1 to 33.0, 4 to 30.0)))
        assertEquals(4, p.anchor)
        // partial 4 has fallen to 10 dB over the floor, narrow this half second, 1.5 c from where it was
        val deep = mapOf(1 to 32.0, 2 to 28.0, 3 to 27.0, 4 to 10.0, 5 to 26.0)
        val narrow = mapOf(1 to 0.1, 2 to 0.1, 3 to 0.15, 4 to 0.2, 5 to 0.2)
        p = b.build(frame(0.0, 100, extra = mapOf(4 to 1.5), widths = narrow, depths = deep))
        assertEquals(4, p.anchor, "the move waits its hold")
        p = b.build(frame(0.0, 700, extra = mapOf(4 to 1.5), widths = narrow, depths = deep))
        assertEquals(1, p.anchor, "the strongest trusted partial anchors; the one at the floor does not")
        assertTrue(p.bells.filter { it.k != 4 }.all { abs(it.offsetCents) < 1e-9 }, "the needles stand on the line: ${p.bells.map { it.offsetCents }}")
        assertEquals(1.5, p.bells.first { it.k == 4 }.offsetCents, 1e-9, "and the one at the floor shows where it wandered")
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

    /**
     * Seats fixed by the page stand whatever sounds: while sampling the picture shows
     * partials 1-5 in their seats, a quiet partial's seat empty and a loud high one's
     * absent, where autoFour would seat the loud ones (Peter, 7 Oct 2026, 10:18).
     */
    @Test
    fun seatsFixedByThePageOverrideAutoFour() {
        val b = TuningPicture.Builder()
        // partials 1, 4, 7 loud, the rest at the floor: autoFour would not seat 2, 3, 5
        val extra = emptyMap<Int, Double>()
        val loud = setOf(1, 4, 7)
        val partials = targets.mapValues { (k, t) -> TuningPicture.Frame.Partial(t, if (k in loud) 1.0 else 0.05, null, if (k in loud) 40.0 else 5.0) }
        fun f(seats: List<Int>?) = TuningPicture.Frame("A4", 1, true, partials[1]!!.hz, targets[1]!!,
            partials, targets, emptySet(), emptySet(), 1.5, 1000L, seats = seats)
        val free = b.build(f(null)).bands
        assertTrue(2 !in free && 3 !in free, "autoFour seats the loud ones, not 2 and 3: $free")
        val fixed = TuningPicture.Builder().build(f(listOf(1, 2, 3, 4, 5))).bands
        assertEquals(listOf(1, 2, 3, 4, 5), fixed)
    }
}
