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
        a.update { it.copy(matchCents = 0.3, octaveMiddle = OctaveType.O2_1, octaveBass = OctaveType.O10_5, lowestUnwoundMidi = 40, widthBass = 9.5, widthTreble = 2.0, recordPcm = true, lowestKeyMidi = 17, autoNote = false) }
        val b = SettingsModel(store)
        assertEquals(true, b.settings.value.recordPcm)
        assertEquals(false, b.settings.value.autoNote)
        // a model with no fixed lowest key (Pro's, since 19 Oct 2026): the range the tuner set is the range he gets back
        assertEquals(17, b.settings.value.lowestKeyMidi, "the lowest key as set")
        val other = MemoryStore()
        SettingsModel(other).update { it.copy(temperamentFirst = true) }
        assertEquals(true, SettingsModel(other).settings.value.temperamentFirst, "the temperament's order is the tuner's choice and is kept")
        assertEquals(0.3, b.settings.value.matchCents)
        assertEquals(OctaveType.O2_1, b.settings.value.octaveMiddle)
        assertEquals(OctaveType.O10_5, b.settings.value.octaveBass)
        assertEquals(40, b.settings.value.lowestUnwoundMidi)
        assertEquals(9.5, b.settings.value.widthBass)
        assertEquals(2.0, b.settings.value.widthTreble)
        assertEquals(0.0, b.settings.value.widthMiddle)
    }

    /**
     * What the strobe shows is one of three, kept with the rest (StrobeMode, 31 Oct 2026), and a phone that kept the two
     * settings from before reads them as what they meant: the octave's partial is one band, a number is the limit, 0 all.
     */
    @Test
    fun theStrobesModeIsKeptAndTheOldSettingsAreReadAsWhatTheyMeant() {
        val store = MemoryStore()
        assertEquals(StrobeMode.ALL, SettingsModel(store).settings.value.strobeMode)
        assertEquals(4, SettingsModel(store).settings.value.strobeCount, "the limit is 4 until changed")
        SettingsModel(store).update { it.copy(strobeMode = StrobeMode.LIMIT, strobeCount = 6) }
        assertEquals(StrobeMode.LIMIT, SettingsModel(store).settings.value.strobeMode)
        assertEquals(6, SettingsModel(store).settings.value.strobeCount)
        fun old(vararg kv: Pair<String, String>) = SettingsModel(MemoryStore().also { st -> kv.forEach { (k, v) -> st.put(k, v) } }).settings.value
        assertEquals(StrobeMode.ONE, old("strobeOne" to "true", "strobeCount" to "0").strobeMode)
        old("strobeOne" to "false", "strobeCount" to "5").let { assertEquals(StrobeMode.LIMIT, it.strobeMode); assertEquals(5, it.strobeCount) }
        old("strobeOne" to "false", "strobeCount" to "0").let { assertEquals(StrobeMode.ALL, it.strobeMode); assertEquals(4, it.strobeCount) }
    }

    /** Help's text size (Peter, 12 Oct 2026): A− and A+ step it from 12 to 22 sp, and the phone keeps it. */
    @Test
    fun helpsTextSizeIsKeptAndStaysWithinItsSteps() {
        val store = MemoryStore()
        assertEquals(14, SettingsModel(store).settings.value.helpTextSp, "14 sp until the tuner changes it")
        SettingsModel(store).update { it.copy(helpTextSp = 20) }
        assertEquals(20, SettingsModel(store).settings.value.helpTextSp, "kept on the phone")
        assertEquals(22, TunerSettings.helpTextStep(22, +1), "A+ stops at 22")
        assertEquals(12, TunerSettings.helpTextStep(12, -1), "A− stops at 12")
        assertEquals(16, TunerSettings.helpTextStep(14, +1)); assertEquals(12, TunerSettings.helpTextStep(14, -1))
        store.put("helpTextSp", "40")
        assertEquals(22, SettingsModel(store).settings.value.helpTextSp, "a stored size beyond the range is kept at its end")
    }

    /**
     * The free app's range is A0–C8 and cannot be changed; Pro sets the lowest key down to C0, for
     * the 225 (F0), the Imperial and the Érards (Peter, 19 Oct 2026, 21:59). A lowest key stored before,
     * when the free app let it be set, does not survive into the free app's session.
     */
    @Test
    fun theFreeAppsRangeIsA0ToC8WhateverIsStoredAndProSetsItDownToC0() {
        val store = MemoryStore()
        SettingsModel(store).update { it.copy(lowestKeyMidi = 17) }          // set to F0 in a free app before 19 Oct
        val free = SettingsModel(store, lowestKeyFixed = TunerSettings.MIDI_A0)
        assertEquals(21, free.settings.value.lowestKeyMidi, "the stored F0 gives way to A0")
        free.update { it.copy(lowestKeyMidi = 12) }
        assertEquals(21, free.settings.value.lowestKeyMidi, "and nothing sets it lower")
        val pro = SettingsModel(MemoryStore())
        pro.update { it.copy(lowestKeyMidi = TunerSettings.MIN_LOWEST_KEY) }
        assertEquals(12, pro.settings.value.lowestKeyMidi, "Pro goes down to C0")
        assertEquals(null, pro.lowestKeyFixed)
    }

    /** The phone's bars outside the tuning screen are the tuner's to show or not (Peter, 23 Oct 2026): shown until he says otherwise, and kept. */
    @Test
    fun theNavigationAndStatusBarsAreShownUntilSwitchedOffAndKept() {
        val store = MemoryStore()
        assertEquals(true, SettingsModel(store).settings.value.showNavigationBar)
        assertEquals(true, SettingsModel(store).settings.value.showStatusBar)
        SettingsModel(store).update { it.copy(showNavigationBar = false, showStatusBar = false) }
        assertEquals(false, SettingsModel(store).settings.value.showNavigationBar)
        assertEquals(false, SettingsModel(store).settings.value.showStatusBar)
    }

    /**
     * The treble's default octave is 4:1 (Peter, 23 Oct 2026, at the Steinway B: "4:2 should stay, but
     * treble default should go to 4:1"). 4:2 since 4 Oct stood 3.4 c (median) sharp of the 225 as tuned
     * and C7 10.6 c sharp, as a forum tester found against Verituner; 4:1 met it within half a cent
     * (SAMICK findings §32). 4:2 stays a choice.
     */
    @Test
    fun theTreblesDefaultOctaveIs4To1() {
        assertEquals(OctaveType.O4_1, TunerSettings().octaveTreble)
        assertEquals(OctaveType.O4_1, SettingsModel(MemoryStore()).settings.value.octaveTreble)
        assertTrue(OctaveType.O4_2 in OctaveType.entries)
    }

    @Test
    fun aWidthBeyondTheLimitIsKeptAtTheLimit() {
        val store = MemoryStore()
        store.put("widthBass", "55")
        assertEquals(TunerSettings.MAX_WIDTH_CENTS, SettingsModel(store).settings.value.widthBass)
    }

    @Test
    fun theOctaveTypeBlendsByKeyAndTheNoteIsReadOnTheOneWithTheLargerShare() {
        // no borders (29 Oct 2026): the middle's type next to the temperament octave, the outer one at the ends
        val s = TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = 40, octaveBass = OctaveType.O8_4,
            octaveMiddle = OctaveType.O4_2, octaveTreble = OctaveType.O4_1, widthBass = 4.0, widthMiddle = 1.0, widthTreble = 2.0)
        assertEquals(0.0, s.outerShare(56), 1e-12); assertEquals(1.0, s.outerShare(21), 1e-12)
        assertEquals(0.0, s.outerShare(70), 1e-12); assertEquals(1.0, s.outerShare(108), 1e-12)
        assertEquals(18.0 / 35, s.outerShare(38), 1e-12, "D2: 18 of the 35 steps from G#3 to A0")
        assertEquals(OctaveType.O8_4, s.octaveTypeFor(38)); assertEquals(OctaveType.O4_2, s.octaveTypeFor(39))
        assertEquals(OctaveType.O4_2, s.octaveTypeFor(52), "E3, an octave above the lowest plain string: the middle's, the floor decides nothing")
        assertEquals(OctaveType.O4_2, s.octaveTypeFor(70)); assertEquals(OctaveType.O4_1, s.octaveTypeFor(108))
        assertEquals(1.0, s.widthFor(56), 1e-12); assertEquals(4.0, s.widthFor(21), 1e-12)
        assertEquals(1.0, s.widthFor(69), 1e-12); assertEquals(2.0, s.widthFor(108), 1e-12)
        assertEquals(1.0 + 3.0 * 18 / 35, s.widthFor(38), 1e-12, "the width blends as the type does")
        assertEquals(OctaveType.O4_1, TunerSettings().octaveTreble, "4:1 above A4 by default (Peter, 23 Oct 2026; 4:2 from 4 Oct)")
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
    fun theFarWidthIsWhereTheOuterWidthArrivesAtTheEnd() {
        val s = TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = 40, widthBass = 4.0, widthBassFar = 12.0, widthTreble = 1.0, widthTrebleFar = 5.0)
        assertEquals(0.0, s.widthFor(56), 1e-9)                 // next to the temperament octave: the middle's
        assertEquals(12.0, s.widthFor(21), 1e-9)                // A0: the far width
        val w = 18.0 / 35                                       // D2
        assertEquals((4.0 + 8.0 * w) * w, s.widthFor(38), 1e-9)
        assertEquals(0.0, s.widthFor(70), 1e-9)                 // just above the temperament octave
        assertEquals(5.0, s.widthFor(108), 1e-9)                // C8
    }

    /** The page a note's stretch is set on: the bass below the temperament octave, the treble above, whatever the lowest plain string. */
    @Test
    fun theRegionOfANoteIsTheStretchPageItOpens() {
        for (floor in listOf(40, 48)) {
            val s = TunerSettings(lowestKeyMidi = 21, lowestUnwoundMidi = floor)
            assertEquals(0, s.regionOf(47)); assertEquals(0, s.regionOf(56))
            assertEquals(1, s.regionOf(57)); assertEquals(1, s.regionOf(69))
            assertEquals(2, s.regionOf(70))
        }
    }
}
