package at.clavierhaus.claviertuner.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The presets (23 Nov 2026): each is what its name says, and the choice reads back as the preset it set. */
class StretchPresetTest {
    @Test
    fun eachPresetReadsBackAsItself() {
        for (p in StretchPreset.entries) assertEquals(p, StretchPreset.of(p.applyTo(TunerSettings())), "$p")
        for (p in StretchPreset.entries) for (q in StretchPreset.entries) assertEquals(q, StretchPreset.of(q.applyTo(p.applyTo(TunerSettings()))), "$p then $q")
    }

    @Test
    fun schubertIsRonKovalsPost() {
        val s = StretchPreset.SCHUBERT.applyTo(TunerSettings())
        // the octave each place reads on: its heaviest type
        assertEquals(OctaveType.O12_6, s.octaveTypeFor(21), "A0, about 75 % 12:6")
        assertEquals(OctaveType.O4_1, s.octaveTypeFor(33), "A1, 80 % 4:1")
        assertEquals(OctaveType.O6_3, s.octaveTypeFor(45), "A2, pure 6:3")
        assertEquals(OctaveType.O4_2, s.octaveTypeFor(81), "A5, pure 4:2")
        assertEquals(OctaveType.O4_1, s.octaveTypeFor(93), "A6, about 60 % 4:1")
        assertEquals(OctaveType.O4_1, s.octaveTypeFor(108), "C8, 4:1")
        assertEquals(0.3, s.mixMiddle?.share); assertEquals(OctaveType.O2_1, s.mixMiddle?.type)
        assertEquals(listOf(0.0), (s.stretchPoints.map { it.widthCents } + listOf(s.widthBass, s.widthMiddle, s.widthTreble)).distinct(), "no widths: he gives none")
        assertNull(s.widthBassFar); assertNull(s.widthTrebleFar)
        assertEquals(TunerSettings().language, s.language, "only the stretch changes")
    }
}
