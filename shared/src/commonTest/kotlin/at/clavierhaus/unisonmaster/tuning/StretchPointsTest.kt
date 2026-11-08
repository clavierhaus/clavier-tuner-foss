package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.persistence.SessionCodec
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.MemoryStore
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.settings.StretchPoint
import at.clavierhaus.unisonmaster.settings.TunerSettings
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stretch points (8 Nov 2026): a place between the temperament octave and the end of the range with an octave type and
 * a width of its own, the octaves blending from one place to the next. The sampling here is the sparse one a tuner in a
 * hurry leaves, eight strings on A and E keys besides A4, the minimum.
 */
class StretchPointsTest {
    private val a4 = 440.0
    private val six = TunerSettings().withSixPoints(true)
    private fun b(m: Int) = 0.9e-4 * 2.0.pow((m - 48) / 12.0) + 2.5e-4 * 2.0.pow(-(m - 21) / 8.0)

    private fun session(s: TunerSettings): TuningSession {
        val t = TuningSession(a4, s)
        for (m in listOf(69, 21, 33, 40, 45, 52, 57, 76, 81)) t.record(measuredString(m, a4 * 2.0.pow((m - 69) / 12.0), b(m), maxK = 12))
        t.finishSampling()
        return t
    }

    private fun partial(t: TuningSession, m: Int, k: Int) = t.targetPartialHz(m, k)

    @Test
    fun onAPlaceTheOctaveIsThatPlacesTypeAtThatPlacesWidth() {
        val t = session(six)
        assertEquals(0.74, TuningSession.centsOff(partial(t, 77, 1), partial(t, 65, 2)), 1e-6, "F5 on 2:1 over F4")
        assertEquals(0.49, TuningSession.centsOff(partial(t, 93, 1), partial(t, 69, 4)), 1e-6, "A6 on 4:1 over A4")
        assertEquals(0.0, TuningSession.centsOff(partial(t, 108, 1), partial(t, 84, 4)), 1e-6, "C8 on 4:1 over C6, beatless")
        assertEquals(-0.63, TuningSession.centsOff(partial(t, 45, 6), partial(t, 57, 3)), 1e-6, "A2 on 6:3 under A3, the lower note flatter")
        assertEquals(-3.46, TuningSession.centsOff(partial(t, 21, 6), partial(t, 33, 3)), 1e-6, "A0 on 6:3 under A1")
        assertEquals(-0.63, TuningSession.centsOff(partial(t, 57, 4), partial(t, 69, 2)), 1e-6, "A3-A4 on 4:2")
    }

    @Test
    fun betweenTwoPlacesTheTargetIsTheirBlendByKey() {
        val s = six
        val b = s.blend(85)           // C#6, half way from F5 (77) to A6 (93)
        assertEquals(OctaveType.O2_1, b.inner); assertEquals(OctaveType.O4_1, b.outer)
        assertEquals(0.5, b.share, 1e-12)
        assertEquals((0.74 + 0.49) / 2, b.width, 1e-12)
        assertEquals(b.width, s.widthFor(85), 1e-12)
        assertEquals(OctaveType.O4_1, s.octaveTypeFor(85), "the type with the larger share is read; at half, the outer")
        assertEquals(OctaveType.O2_1, s.octaveTypeFor(84))
    }

    @Test
    fun noKeyStepsPastItsNeighbourAcrossThePlacesOnASparseSampling() {
        val t = session(six)
        val off = (21..96).associateWith { TuningSession.centsOff(t.curveTargetF1(it), TuningSession.targetF1(it, a4)) }
        for (m in 21..95) assertTrue(abs(off[m]!! - off[m + 1]!!) < 3.0, "midi $m stands ${off[m]} c from ET, ${m + 1} ${off[m + 1]} c: a border")
        for (m in 44..95) assertTrue(abs(off[m]!! - off[m + 1]!!) < 1.0, "midi $m stands ${off[m]} c from ET, ${m + 1} ${off[m + 1]} c: a border")
    }

    @Test
    fun everyKeyHasATargetWithPointsOfEveryOfferedTypeAndTemperament() {
        for (bass in TunerSettings.BASS_OCTAVES) for (treble in TunerSettings.TREBLE_OCTAVES) for (low in TunerSettings.MIN_TEMPERAMENT_LOW..TunerSettings.MAX_TEMPERAMENT_LOW) {
            val pts = listOf(StretchPoint(35, bass, 1.0), StretchPoint(71, treble, -1.0), StretchPoint(80, treble, 0.5), StretchPoint(100, treble, 0.0))
            val t = session(TunerSettings(temperamentLowMidi = low, octaveBass = bass, octaveTreble = treble, stretchPoints = pts))
            for (m in 21..108) { val hz = t.listening(m).targetHz; assertTrue(hz.isFinite() && hz > 0, "key $m: $hz") }
        }
    }

    @Test
    fun aPointOutsideItsSideOrOfATypeItsSideDoesNotOfferCountsForNothing() {
        val plain = session(TunerSettings())
        val stray = listOf(StretchPoint(60, OctaveType.O2_1, 5.0), StretchPoint(21, OctaveType.O6_3, 5.0), StretchPoint(56, OctaveType.O10_5, 5.0),
            StretchPoint(70, OctaveType.O2_1, 5.0), StretchPoint(108, OctaveType.O2_1, 5.0), StretchPoint(40, OctaveType.O4_1, 5.0), StretchPoint(80, OctaveType.O6_3, 5.0))
        val t = session(TunerSettings(stretchPoints = stray))
        for (m in 21..108) assertEquals(plain.curveTargetF1(m), t.curveTargetF1(m), 1e-12, "key $m")
    }

    @Test
    fun offBringsBackTheDefaultsAndALongGrandsBass() {
        assertEquals(TunerSettings(), six.withSixPoints(false))
        assertFalse(TunerSettings().sixPoints); assertTrue(six.sixPoints)
        val long = TunerSettings().withLongGrand(true)
        assertEquals(OctaveType.O6_3, long.withSixPoints(true).octaveBass)
        assertEquals(OctaveType.O10_5, long.withSixPoints(true).withSixPoints(false).octaveBass)
    }

    @Test
    fun thePointsAreKeptByTheSettingsAndByTheTuning() {
        val store = MemoryStore()
        SettingsModel(store).update { six }
        assertEquals(six.stretchPoints, SettingsModel(store).settings.value.stretchPoints)
        store.put("stretchPoints", "77:O2_1:0.74,nonsense,200:O2_1:0,80:O4_1:99")
        assertEquals(listOf(StretchPoint(77, OctaveType.O2_1, 0.74)), SettingsModel(store).settings.value.stretchPoints)
        store.put("stretchPoints", "")
        assertEquals(emptyList(), SettingsModel(store).settings.value.stretchPoints)

        assertEquals(six, TunerSettings().withStretch(six.stretch()))
        val text = SessionCodec.encode(SessionSnapshot(0, a4, 60, emptyList(), sampling = false, stretch = six.stretch()))
        assertEquals(six.stretch(), SessionCodec.decode(text).stretch)
        val none = SessionCodec.encode(SessionSnapshot(0, a4, 60, emptyList(), sampling = false, stretch = TunerSettings().stretch()))
        assertFalse("points " in none, "a tuning without points is written as before")
        assertEquals(TunerSettings().stretch(), SessionCodec.decode(none).stretch)
    }
}
