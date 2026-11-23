package at.clavierhaus.claviertuner.tuning

import at.clavierhaus.claviertuner.persistence.SessionCodec
import at.clavierhaus.claviertuner.persistence.SessionSnapshot
import at.clavierhaus.claviertuner.settings.MemoryStore
import at.clavierhaus.claviertuner.settings.OctaveShare
import at.clavierhaus.claviertuner.settings.OctaveType
import at.clavierhaus.claviertuner.settings.SettingsModel
import at.clavierhaus.claviertuner.settings.StretchPoint
import at.clavierhaus.claviertuner.settings.TunerSettings
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Octaves mixed of two types (23 Nov 2026), as Ron Koval's Schubert concert tune lays them: the temperament "about 70 %
 * 4:2 and 30 % 2:1", A6 "about 60 % 4:1 and 40 % 4:2", A1 "80 % 4:1", the lowest octave "about 75 %" 12:6 over 6:3. A
 * mix of a share s puts the octave's target s of the way, in cents, from where the place's own type is beatless to where
 * the other is. Each check here is against the same session with the pure types, so that it cannot pass by the model it
 * tests; the sampling is StretchPointsTest's sparse one.
 */
class OctaveMixTest {
    private val a4 = 440.0
    private fun b(m: Int) = 0.9e-4 * 2.0.pow((m - 48) / 12.0) + 2.5e-4 * 2.0.pow(-(m - 21) / 8.0)

    private fun session(s: TunerSettings): TuningSession {
        val t = TuningSession(a4, s)
        for (m in listOf(69, 21, 33, 40, 45, 52, 57, 76, 81)) t.record(measuredString(m, a4 * 2.0.pow((m - 69) / 12.0), b(m), maxK = 12))
        t.finishSampling()
        return t
    }

    private fun et(t: TuningSession, m: Int) = TuningSession.centsOff(t.curveTargetF1(m), TuningSession.targetF1(m, a4))

    /** Ron Koval's places, without a name: what the engine must be able to hold. */
    private val ron = TunerSettings(
        octaveBass = OctaveType.O6_3, octaveMiddle = OctaveType.O4_2, octaveTreble = OctaveType.O4_1,
        mixBass = OctaveShare(OctaveType.O12_6, 0.75), mixMiddle = OctaveShare(OctaveType.O2_1, 0.3),
        stretchPoints = listOf(
            StretchPoint(33, OctaveType.O4_1, 0.0, OctaveShare(OctaveType.O6_3, 0.2)),
            StretchPoint(45, OctaveType.O6_3, 0.0),
            StretchPoint(81, OctaveType.O4_2, 0.0),
            StretchPoint(93, OctaveType.O4_1, 0.0, OctaveShare(OctaveType.O4_2, 0.4)),
        ),
    )

    @Test
    fun aPlacesMixLiesItsShareOfTheWayInCents() {
        val pts = { mix: OctaveShare? -> listOf(StretchPoint(81, OctaveType.O4_2, 0.0, mix)) }
        val pure42 = et(session(TunerSettings(stretchPoints = pts(null))), 81)
        val pure21 = et(session(TunerSettings(stretchPoints = listOf(StretchPoint(81, OctaveType.O2_1, 0.0)))), 81)
        val mixed = et(session(TunerSettings(stretchPoints = pts(OctaveShare(OctaveType.O2_1, 0.3)))), 81)
        assertTrue(abs(pure42 - pure21) > 0.3, "the two types stand apart at A5 ($pure42, $pure21), or the check says nothing")
        assertEquals(0.7 * pure42 + 0.3 * pure21, mixed, 1e-9)
    }

    @Test
    fun theTemperamentOctaveMixesAsAPlaceDoes() {
        val pure42 = et(session(TunerSettings()), 57)
        val pure21 = et(session(TunerSettings(octaveMiddle = OctaveType.O2_1)), 57)
        val mixed = et(session(TunerSettings(mixMiddle = OctaveShare(OctaveType.O2_1, 0.3))), 57)
        assertTrue(abs(pure42 - pure21) > 0.1, "A3 on 4:2 and on 2:1 stand apart ($pure42, $pure21)")
        assertEquals(0.7 * pure42 + 0.3 * pure21, mixed, 1e-9)
        assertEquals(0.0, et(session(TunerSettings(mixMiddle = OctaveShare(OctaveType.O2_1, 0.3))), 69), 1e-9, "A4 does not move")
    }

    @Test
    fun theLowestKeyMixesTwelveSixOverSixThree() {
        // A1 a place of its own, so that A0's partner stands where it stands whatever the lowest key is made of
        val pts = listOf(StretchPoint(33, OctaveType.O6_3, 0.0))
        val p63 = et(session(TunerSettings(stretchPoints = pts)), 21)
        val p126 = et(session(TunerSettings(stretchPoints = pts, octaveBass = OctaveType.O12_6)), 21)
        val mixed = et(session(TunerSettings(stretchPoints = pts, mixBass = OctaveShare(OctaveType.O12_6, 0.75))), 21)
        assertTrue(abs(p63 - p126) > 0.3, "A0 on 6:3 and on 12:6 stand apart ($p63, $p126)")
        assertEquals(0.25 * p63 + 0.75 * p126, mixed, 1e-9)
        val t = session(TunerSettings(stretchPoints = pts + StretchPoint(22, OctaveType.O12_6, 0.0)))
        assertEquals(0.0, TuningSession.centsOff(t.targetPartialHz(22, 12), t.targetPartialHz(34, 6)), 1e-9, "A#0 on 12:6, beatless")
    }

    @Test
    fun aDoubleOctaveInTheBassStandsWhereItsPartnersStayBelowTheTreble() {
        val t = session(TunerSettings(stretchPoints = listOf(StretchPoint(33, OctaveType.O4_1, 0.0), StretchPoint(45, OctaveType.O6_3, 0.0))))
        assertEquals(0.0, TuningSession.centsOff(t.targetPartialHz(33, 4), t.targetPartialHz(57, 1)), 1e-9, "A1 on 4:1 under A3, beatless")
        assertEquals(OctaveType.O4_1, t.settings.octaveTypeFor(33))
        val plain = session(TunerSettings())
        // a 4:1 whose keys would reach past the temperament octave's end counts for nothing, as place or as the lowest key's mix
        for (s in listOf(
            TunerSettings(stretchPoints = listOf(StretchPoint(50, OctaveType.O4_1, 0.0))),
            TunerSettings(stretchPoints = listOf(StretchPoint(33, OctaveType.O6_3, 0.0, OctaveShare(OctaveType.O4_1, 0.5)))),
            TunerSettings(mixBass = OctaveShare(OctaveType.O4_1, 0.5)),
        )) {
            val other = session(s)
            for (m in 21..108) assertEquals(plain.curveTargetF1(m), other.curveTargetF1(m), 1e-9, "key $m with $s")
        }
        // 12:6 and 4:1 are a place's, never a region's
        assertTrue(OctaveType.O12_6 !in TunerSettings.BASS_OCTAVES && OctaveType.O4_1 !in TunerSettings.BASS_OCTAVES)
    }

    @Test
    fun ronKovalsPlacesGiveEveryKeyATargetAndNoStep() {
        for (low in TunerSettings.MIN_TEMPERAMENT_LOW..TunerSettings.MAX_TEMPERAMENT_LOW) {
            val t = session(ron.copy(temperamentLowMidi = low))
            for (m in 21..108) assertTrue(t.listening(m).targetHz.let { it.isFinite() && it > 0 }, "key $m, temperament from $low")
        }
        val t = session(ron)
        val off = (21..96).associateWith { et(t, it) }
        for (m in 21..95) assertTrue(abs(off[m]!! - off[m + 1]!!) < 3.0, "midi $m stands ${off[m]} c from ET, ${m + 1} ${off[m + 1]} c: a border")
        for (m in 44..95) assertTrue(abs(off[m]!! - off[m + 1]!!) < 1.0, "midi $m stands ${off[m]} c from ET, ${m + 1} ${off[m + 1]} c: a border")
        assertEquals(OctaveType.O12_6, ron.octaveTypeFor(21), "the heaviest type is read: 75 % 12:6 at A0")
        assertEquals(OctaveType.O4_1, ron.octaveTypeFor(93), "60 % 4:1 at A6")
        assertEquals(OctaveType.O4_2, ron.octaveTypeFor(81))
    }

    @Test
    fun eitherPresetTakesTheMixesOut() {
        for (on in listOf(true, false)) {
            val s = ron.withSixPoints(on)
            assertNull(s.mixBass); assertNull(s.mixMiddle); assertNull(s.mixTreble)
            assertTrue(s.stretchPoints.none { it.mix != null })
        }
        assertEquals(TunerSettings(), ron.withSixPoints(false).copy(stretchPoints = emptyList()), "off is the defaults, mixes and all")
    }

    @Test
    fun theMixesAreKeptByTheSettingsAndByTheTuning() {
        assertEquals(ron.stretchPoints, StretchPoint.decode(StretchPoint.encode(ron.stretchPoints)))
        assertEquals(listOf(StretchPoint(77, OctaveType.O2_1, 0.74)), StretchPoint.decode("77:O2_1:0.74"), "a place of before reads as it did")
        assertEquals(emptyList(), StretchPoint.decode("77:O2_1:0.74:O4_1:1.5"), "a share out of range drops the place")
        assertNull(OctaveShare.decode("O2_1:0")); assertNull(OctaveShare.decode("")); assertNull(OctaveShare.decode("nonsense:0.3"))

        val store = MemoryStore()
        SettingsModel(store).update { ron }
        val back = SettingsModel(store).settings.value
        assertEquals(ron.stretch(), back.stretch())
        val kept = MemoryStore().apply { put("octaveBass", "O6_3") }
        assertNull(SettingsModel(kept, defaults = ron).settings.value.mixMiddle, "a phone that kept settings before has no mixes")

        val text = SessionCodec.encode(SessionSnapshot(0, a4, 60, emptyList(), sampling = false, stretch = ron.stretch()))
        assertEquals(ron.stretch(), SessionCodec.decode(text).stretch)
        val none = SessionCodec.encode(SessionSnapshot(0, a4, 60, emptyList(), sampling = false, stretch = TunerSettings().stretch()))
        assertTrue("mixes " !in none, "a tuning without mixes is written as before")
        assertNull(SessionCodec.decode(none).stretch?.mixMiddle)
    }
}
