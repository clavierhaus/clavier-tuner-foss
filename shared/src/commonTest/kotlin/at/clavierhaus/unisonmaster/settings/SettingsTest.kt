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
        a.update { it.copy(matchHz = 0.2, octaveMiddle = OctaveType.O6_3, octaveWound = OctaveType.O8_4, lowestUnwoundMidi = 40, widthWound = 9.5, widthTreble = 2.0, recordPcm = true, lowestKeyMidi = 17, autoNote = false) }
        val b = SettingsModel(store)
        assertEquals(true, b.settings.value.recordPcm)
        assertEquals(false, b.settings.value.autoNote)
        assertEquals(17, b.settings.value.lowestKeyMidi)
        assertEquals(0.2, b.settings.value.matchHz)
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
    fun theMatchWindowIsASetting() {
        assertTrue(!TuningSession.matched(415.45, 415.30))
        assertTrue(TuningSession.matched(415.45, 415.30, windowHz = 0.2))
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
}
