package at.clavierhaus.unisonmaster.settings

import at.clavierhaus.unisonmaster.tuning.MeasuredPartial
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
        a.update { it.copy(matchHz = 0.2, octaveMiddle = OctaveType.O6_3, lowestUnwoundMidi = 40, suggestSustainS = 1.5, autoNote = false) }
        val b = SettingsModel(store)
        assertEquals(false, b.settings.value.autoNote)
        assertEquals(0.2, b.settings.value.matchHz)
        assertEquals(OctaveType.O6_3, b.settings.value.octaveMiddle)
        assertEquals(40, b.settings.value.lowestUnwoundMidi)
        assertEquals(1.5, b.settings.value.suggestSustainS)
    }

    @Test
    fun theMatchWindowIsASetting() {
        assertTrue(!TuningSession.matched(415.45, 415.30))
        assertTrue(TuningSession.matched(415.45, 415.30, windowHz = 0.2))
    }

    @Test
    fun theSuggestionFollowsTheSettings() {
        val partials = listOf(
            MeasuredPartial(3, 1.0, -6.0, 4.0),
            MeasuredPartial(5, 2.0, -10.0, 1.2),
            MeasuredPartial(7, 3.0, -20.0, 0.8),
        )
        assertEquals(3, TuningSession.recommend(partials, minSustainS = 2.0))
        assertEquals(5, TuningSession.recommend(partials, minSustainS = 1.0))
        assertEquals(7, TuningSession.recommend(partials, minSustainS = 0.5))
        assertEquals(5, TuningSession.recommend(partials, minSustainS = 0.5, maxK = 6), "capped by the highest useful partial")
    }

    @Test
    fun theHighestUsefulPartialIsAFrequencyCap() {
        val dSharp7 = TuningSession.targetF1(99, 440.0)          // 2489 Hz
        assertEquals(5, TuningSession.highestUsefulPartial(440.0, dSharp7))   // A4: up to 2200 Hz
        assertEquals(11, TuningSession.highestUsefulPartial(220.0, dSharp7))  // A3: up to 2420 Hz
        assertEquals(12, TuningSession.highestUsefulPartial(110.0, dSharp7))  // never beyond 12
    }

    @Test
    fun theLowestPlainStringSetsTheSessionsFoot() {
        val s = TuningSession(440.0, TunerSettings(lowestUnwoundMidi = 40, temperamentFirst = false))   // E2
        assertEquals((69 downTo 40).toList() + (70..108).toList(), s.notes)
        s.select(41)
        assertEquals(40, s.below())
        s.select(40)
        assertEquals(null, s.below())
    }
}
