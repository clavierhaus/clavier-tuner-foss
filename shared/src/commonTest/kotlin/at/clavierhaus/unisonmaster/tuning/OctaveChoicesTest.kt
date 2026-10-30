package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.settings.MemoryStore
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.settings.TunerSettings
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The octave types the settings offer (30 Oct 2026). Every choice the screens offer, in every combination and with
 * every temperament octave the tuner can set, gives every key a target -- until that day 4:1 in the bass, and 4:1 or
 * 3:1 in the middle, sent the engine round in a circle (bass to treble and back) and never returned -- on the JVM a StackOverflowError, caught here as any Throwable because Kotlin/Native has no such class
 * (research/ProbeOctaveStyle.kt in Pro). And a type no longer offered, stored by an earlier version, comes back as
 * the region's default rather than as a choice the screen cannot show.
 */
class OctaveChoicesTest {
    private fun session(s: TunerSettings): TuningSession {
        val t = TuningSession(440.0, s)
        for (m in listOf(69, 21, 33, 45, 57, 64, 76, 88, 100)) t.record(measuredString(m, 440.0 * 2.0.pow((m - 69) / 12.0), 1e-4 * 2.0.pow((m - 45) / 24.0), maxK = 12))
        t.finishSampling()
        return t
    }

    @Test fun everyOfferedCombinationGivesEveryKeyATarget() {
        for (bass in TunerSettings.BASS_OCTAVES) for (middle in TunerSettings.MIDDLE_OCTAVES) for (treble in TunerSettings.TREBLE_OCTAVES)
            for (low in TunerSettings.MIN_TEMPERAMENT_LOW..TunerSettings.MAX_TEMPERAMENT_LOW) {
                val s = TunerSettings(lowestKeyMidi = 21, octaveBass = bass, octaveMiddle = middle, octaveTreble = treble, temperamentLowMidi = low)
                val t = try { session(s) } catch (e: Throwable) { fail("bass ${bass.label}, middle ${middle.label}, treble ${treble.label}, temperament from $low: no target") }
                for (m in 21..108) {
                    val hz = try { t.listening(m).targetHz } catch (e: Throwable) { fail("bass ${bass.label}, middle ${middle.label}, treble ${treble.label}, temperament from $low: no target for $m") }
                    assertTrue(hz.isFinite() && hz > 0, "key $m: $hz")
                }
            }
    }

    @Test fun aTypeNoLongerOfferedComesBackAsTheRegionsDefault() {
        val store = MemoryStore()
        for (t in OctaveType.entries) {
            store.put("octaveBass", t.name); store.put("octaveMiddle", t.name); store.put("octaveTreble", t.name)
            val s = SettingsModel(store).settings.value
            assertEquals(if (t in TunerSettings.BASS_OCTAVES) t else TunerSettings().octaveBass, s.octaveBass)
            assertEquals(if (t in TunerSettings.MIDDLE_OCTAVES) t else TunerSettings().octaveMiddle, s.octaveMiddle)
            assertEquals(if (t in TunerSettings.TREBLE_OCTAVES) t else TunerSettings().octaveTreble, s.octaveTreble)
        }
    }
}
