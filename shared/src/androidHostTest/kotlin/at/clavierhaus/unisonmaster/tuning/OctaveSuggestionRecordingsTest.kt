package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.measure.PhaseReader
import at.clavierhaus.unisonmaster.measure.StringMeasure
import at.clavierhaus.unisonmaster.settings.OctaveType
import java.io.File
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bass's suggestion on a piano heard by a tuner (10 Nov 2026). A tester tuned a five-year-old Yamaha b2 upright to the
 * app's default bass, 6:3, beatless within 0.02 c on A1-A2 and A2-A3, and heard the bass "about 4 cents too high"; then
 * struck every A and E from E1 to A6 twice, and sent the recording (Pro data/recordings/2026-11-10/tester-yamaha-b2,
 * STRETCH §23). Measured as sampling measures a string, each key's last strike, his bass octaves must suggest 10:5,
 * which his ear asked for; and 6:3 must leave a harder beat than 10:5. Says so when it cannot find the recording.
 */
class OctaveSuggestionRecordingsTest {
    private fun recording(): File? {
        val base = System.getenv("CLAVIER_DATA")?.let { File(it) } ?: run {
            var d: File? = File(".").absoluteFile
            while (d != null && !File(d, "data/recordings").isDirectory) d = d.parentFile
            d?.let { File(it, "data/recordings") }
        } ?: return null
        return File(base, "2026-11-10/tester-yamaha-b2/Yb2.wav").takeIf { it.isFile }
    }

    @Test
    fun theTestersYamahaB2SuggestsTenToFiveInTheBass() {
        val file = recording() ?: run { println("OctaveSuggestionRecordingsTest: no recording of the Yamaha b2 (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val pcm = WavPcm.read(file.readBytes()); val sr = pcm.sampleRateHz; val x = pcm.samples; val hop = 1024
        // the strikes, as the string check finds them; each named on two windows alike, measured as sampling measures it
        val onsets = ArrayList<Int>(); val recent = DoubleArray(5) { -200.0 }; var h = 0L; var at = 0
        while (at + hop <= x.size) {
            var ss = 0.0; for (i in at until at + hop) ss += x[i].toDouble() * x[i]
            val db = 10 * log10(maxOf(ss / hop, 1e-24)); val loudest = recent.max(); h++
            recent[(h % 5).toInt()] = db
            if (h > 5 && db - loudest >= PhaseReader.ONSET_DB && db > -70.0 && (onsets.isEmpty() || at - onsets.last() > 0.3 * sr)) onsets += at
            at += hop
        }
        val id = KeyIdentifier(sr, 16384)
        val last = HashMap<Int, NoteMeasurement>()
        onsets.forEachIndexed { i, on ->
            val end = minOf(if (i + 1 < onsets.size) onsets[i + 1] else x.size, on + 3 * sr)
            var key: Int? = null; var prev: Int? = null; var s = on + (0.05 * sr).toInt()
            while (s + 16384 <= end && s <= on + (1.5 * sr).toInt()) {
                val k = id.detectIn(x.copyOfRange(s, s + 16384), 440.0); if (k != null && k == prev) { key = k; break }; prev = k; s += 2 * hop
            }
            key?.let { k -> StringMeasure.measure(x.copyOfRange(on, end), sr, k, TuningSession.targetF1(k, 440.0))?.let { last[k] = it } }
        }
        val s = assertNotNull(OctaveSuggestion.bass(last.values.toList(), temperamentLowMidi = 57), "no suggestion from keys ${last.keys.sorted()}")
        println("Yamaha b2: suggested ${s.type.label} over ${s.octaves} octaves, hardest beat " + s.hardestHz.entries.joinToString { "${it.key.label} %.2f Hz".format(it.value) })
        assertEquals(OctaveType.O10_5, s.type)
        assertTrue(s.hardestHz.getValue(OctaveType.O6_3) > s.hardestHz.getValue(OctaveType.O10_5))
    }
}
