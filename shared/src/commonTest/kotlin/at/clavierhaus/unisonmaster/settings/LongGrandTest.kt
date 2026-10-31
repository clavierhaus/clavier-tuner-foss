package at.clavierhaus.unisonmaster.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bass's octave type recommended by the piano's size (Peter, 31 Oct 2026: "toggle for ><210cm ... and from that
 * switch automatically determine the octave setting to either 6:3 or 10:5 ... we just give recommendations"):
 * 10:5 on a grand of 210 cm or more, 6:3 on anything shorter; the type stays the tuner's to change afterwards.
 */
class LongGrandTest {
    @Test fun theToggleSetsTheBassTypeAndNothingElse() {
        val s = TunerSettings(octaveMiddle = OctaveType.O2_1, octaveTreble = OctaveType.O3_1, widthBass = 2.0)
        val long = s.withLongGrand(true)
        assertEquals(OctaveType.O10_5, long.octaveBass); assertEquals(true, long.longGrand)
        assertEquals(s.copy(longGrand = true, octaveBass = OctaveType.O10_5), long, "middle, treble and widths untouched")
        val short = long.withLongGrand(false)
        assertEquals(OctaveType.O6_3, short.octaveBass); assertEquals(false, short.longGrand)
    }

    @Test fun theTunerCanStillChooseAnyBassTypeAndTheChoiceIsKept() {
        val store = MemoryStore()
        SettingsModel(store).update { it.withLongGrand(true).copy(octaveBass = OctaveType.O4_2) }
        val back = SettingsModel(store).settings.value
        assertEquals(true, back.longGrand); assertEquals(OctaveType.O4_2, back.octaveBass)
    }

    @Test fun shorterThan210CmIsTheDefault() {
        assertEquals(false, TunerSettings().longGrand)
        assertEquals(OctaveType.O6_3, TunerSettings().octaveBass)
    }
}
