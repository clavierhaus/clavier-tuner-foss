package at.clavierhaus.unisonmaster.settings

import at.clavierhaus.unisonmaster.tuning.TuningSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsTest {

    @Test
    fun defaultsRoundTripThroughTheStore() {
        val store = MemoryStore()
        val a = SettingsModel(store)
        assertEquals(TunerSettings(), a.settings.value)
        a.update { it.copy(matchCents = 0.3, octaveMiddle = OctaveType.O6_3, octaveWound = OctaveType.O8_4, lowestUnwoundMidi = 40, widthWound = 9.5, widthTreble = 2.0, recordPcm = true, lowestKeyMidi = 17, autoNote = false) }
        val b = SettingsModel(store)
        assertEquals(true, b.settings.value.recordPcm)
        assertEquals(false, b.settings.value.autoNote)
        // nothing is pinned by edition (4 Oct 2026): the compass the tuner set is the compass he gets back
        assertEquals(17, b.settings.value.lowestKeyMidi, "the lowest key as set")
        val other = MemoryStore()
        SettingsModel(other).update { it.copy(temperamentFirst = true) }
        assertEquals(true, SettingsModel(other).settings.value.temperamentFirst, "the temperament's order is the tuner's choice and is kept")
        assertEquals(0.3, b.settings.value.matchCents)
        assertEquals(OctaveType.O6_3, b.settings.value.octaveMiddle)
        assertEquals(OctaveType.O8_4, b.settings.value.octaveWound)
        assertEquals(40, b.settings.value.lowestUnwoundMidi)
        assertEquals(9.5, b.settings.value.widthWound)
        assertEquals(2.0, b.settings.value.widthTreble)
        assertEquals(0.0, b.settings.value.widthMiddle)
    }

    @Test
    fun aWidthBeyondTheLimitIsKeptAtTheLimit() {
        val store = MemoryStore()
        store.put("widthBass", "55")
        assertEquals(TunerSettings.MAX_WIDTH_CENTS, SettingsModel(store).settings.value.widthBass)
    }

    @Test
    fun theRegionsAreTheWoundStringsTheBassTheMiddleAndTheTreble() {
        val s = TunerSettings(lowestUnwoundMidi = 40, octaveWound = OctaveType.O8_4, octaveBass = OctaveType.O6_3,
            octaveMiddle = OctaveType.O4_2, octaveTreble = OctaveType.O4_1, widthWound = 8.0, widthBass = 4.0, widthMiddle = 1.0, widthTreble = 2.0)
        assertEquals(OctaveType.O8_4, s.octaveTypeFor(39)); assertEquals(8.0, s.widthFor(39))
        assertEquals(OctaveType.O6_3, s.octaveTypeFor(40)); assertEquals(4.0, s.widthFor(52))
        assertEquals(OctaveType.O4_2, s.octaveTypeFor(53)); assertEquals(1.0, s.widthFor(69))
        assertEquals(OctaveType.O4_1, s.octaveTypeFor(70)); assertEquals(2.0, s.widthFor(108))
    }

    @Test
    fun theMatchWindowIsASettingAndMeansTheSameEverywhereOnTheKeyboard() {
        // 415.45 against 415.30 is 0.63 cents: inside the default window of 1.5
        // cents (the tuner's tolerance, set at the piano on 1 Oct 2026), outside a
        // window tightened to a fifth of a cent. The window is a setting.
        assertTrue(TuningSession.matched(415.45, 415.30))
        assertTrue(!TuningSession.matched(415.45, 415.30, windowCents = 0.2))
        // two cents is outside the default: the window is a window, not the scale
        assertTrue(!TuningSession.matched(415.30 * 1.001156, 415.30))
        // and it is in cents, so one setting is one musical distance from the bottom
        // of the piano to the top. In Hertz it was not: a tenth of a Hertz is a cent
        // at F3 and a twelfth of a cent at C7.
        for (hz in doubleArrayOf(27.5, 174.6, 440.0, 2093.0, 4186.0)) {
            val halfACentSharp = hz * kotlin.math.exp(0.5 / 1200.0 * kotlin.math.ln(2.0))
            assertTrue(TuningSession.matched(halfACentSharp, hz, windowCents = 0.6),
                "half a cent is inside a 0.6 c window at $hz Hz")
            assertTrue(!TuningSession.matched(halfACentSharp, hz, windowCents = 0.4),
                "half a cent is outside a 0.4 c window at $hz Hz")
        }
    }

    @Test
    fun theHighestUsefulPartialIsAFrequencyCap() {
        val dSharp7 = TuningSession.targetF1(99, 440.0)          // 2489 Hz
        assertEquals(5, TuningSession.highestUsefulPartial(440.0, dSharp7))   // A4: up to 2200 Hz
        assertEquals(11, TuningSession.highestUsefulPartial(220.0, dSharp7))  // A3: up to 2420 Hz
        assertEquals(12, TuningSession.highestUsefulPartial(110.0, dSharp7))  // never beyond 12
    }

    @Test
    fun theLowestKeySetsTheSessionsFoot() {
        val s = TuningSession(440.0, TunerSettings(lowestKeyMidi = 40, lowestUnwoundMidi = 40, temperamentFirst = false))   // a session cut short at E2
        assertEquals((69 downTo 40).toList() + (70..108).toList(), s.notes)
        s.select(41)
        assertEquals(40, s.stepped(-1))
        s.select(40)
        assertEquals(40, s.stepped(-1))
    }

    @Test
    fun theWidthSlidesFromTheRegionsNearEndToItsFarEndWhenOneIsSet() {
        val s = TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = 40, widthWound = 4.0, widthWoundFar = 12.0, widthTreble = 1.0, widthTrebleFar = 5.0)
        assertEquals(4.0, s.widthFor(39), 1e-9)                 // the top of the wound region: the near width
        assertEquals(12.0, s.widthFor(21), 1e-9)                // A0: the far width
        assertEquals(8.0, s.widthFor(30), 1e-9)                 // halfway between 39 and 21
        assertEquals(1.0, s.widthFor(70), 1e-9)                 // just above the temperament octave
        assertEquals(5.0, s.widthFor(108), 1e-9)                // C8
        assertEquals(0.0, s.widthFor(45), 1e-9)                 // the plain bass: no far width set, constant
    }

    /** The region a note lies in is the order of the Stretch pages: the tuning screen opens the right one with one tap. */
    @Test
    fun theRegionOfANoteIsTheStretchPageItOpens() {
        val s = TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = 48)
        assertEquals(0, s.regionOf(47), "below the lowest plain string: wound")
        assertEquals(1, s.regionOf(48), "the lowest plain string: bass")
        assertEquals(1, s.regionOf(60), "an octave above it: still bass")
        assertEquals(2, s.regionOf(61), "then the middle")
        assertEquals(2, s.regionOf(69), "A4 is the temperament's: middle")
        assertEquals(3, s.regionOf(70), "above A4: treble")
    }
}
