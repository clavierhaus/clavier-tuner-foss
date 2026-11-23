package at.clavierhaus.claviertuner.settings

import at.clavierhaus.claviertuner.tuning.NamedTemperament
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ron Koval's Schubert concert tune as a temperament (23 Nov 2026): his octaves on, and off again by another temperament. */
class SchubertTuneTest {
    @Test
    fun itIsRonKovalsPostInEqualTemperament() {
        val s = SchubertTune.applyTo(TunerSettings(temperament = NamedTemperament.VALLOTTI))
        assertTrue(SchubertTune.isOn(s))
        assertEquals(NamedTemperament.EQUAL, s.temperament)
        // the octave each place reads on: its heaviest type
        assertEquals(OctaveType.O12_6, s.octaveTypeFor(21), "A0, about 75 % 12:6")
        assertEquals(OctaveType.O4_1, s.octaveTypeFor(33), "A1, 80 % 4:1")
        assertEquals(OctaveType.O6_3, s.octaveTypeFor(45), "A2, pure 6:3")
        assertEquals(OctaveType.O4_2, s.octaveTypeFor(81), "A5, pure 4:2")
        assertEquals(OctaveType.O4_1, s.octaveTypeFor(93), "A6, about 60 % 4:1")
        assertEquals(listOf(0.0), (s.stretchPoints.map { it.widthCents } + listOf(s.widthBass, s.widthMiddle, s.widthTreble)).distinct())
    }

    @Test
    fun anotherTemperamentOrAPresetGoesBackWithoutHisOctaves() {
        val on = SchubertTune.applyTo(TunerSettings())
        val w = SchubertTune.withTemperament(on, NamedTemperament.WERCKMEISTER_III)
        assertFalse(SchubertTune.isOn(w))
        assertEquals(NamedTemperament.WERCKMEISTER_III, w.temperament)
        assertEquals(TunerSettings().withSixPoints(true).stretch().copy(temperament = NamedTemperament.WERCKMEISTER_III), w.stretch(), "the six points")
        for (p in StretchPreset.entries) assertFalse(SchubertTune.isOn(p.applyTo(on)), "$p")
        // without his octaves, only the temperament changes
        val plain = TunerSettings().withSixPoints(false)
        assertEquals(plain.copy(temperament = NamedTemperament.KELLNER), SchubertTune.withTemperament(plain, NamedTemperament.KELLNER))
    }
}
