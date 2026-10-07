package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The false-beat verdict on recorded clean strings. The Samick's F3 of
 * 30 September 2026 — three strings, three strikes each, one string at a
 * time with the others muted — has no false beat on any string: the
 * tuner's ear says so (3 Oct), and so does the spectrum, one line per
 * partial with nothing within 25 dB of it while the partial stands. Read
 * against its own series, as the controller reads a string once the strike
 * has spoken, no partial of any take may be called false at any hop of the
 * strike — before 3 Oct the engine called partials 2, 3 and 8 of the first
 * take false as they decayed onto the room's floor. Skipped where the
 * recordings are not checked out ([CLAVIER_DATA] or a `data/recordings`
 * directory up the tree).
 */
class CleanReadingRecordingsTest {
    private fun takes(): List<File> {
        val base = System.getenv("CLAVIER_DATA")?.let { File(it) } ?: run {
            var d: File? = File(".").absoluteFile
            while (d != null && !File(d, "data/recordings").isDirectory) d = d.parentFile
            d?.let { File(it, "data/recordings") }
        } ?: return emptyList()
        val dir = File(base, "2026-09-30/strikes-162337")
        return dir.listFiles { f -> f.name.startsWith("Samick_F3-") && f.name.endsWith(".wav") }?.sortedBy { it.name } ?: emptyList()
    }

    @Test
    fun noPartialOfTheSamicksCleanF3IsCalledFalseOnAnyStrike() = cleanF3CallsNothing(combine = true)

    /**
     * The same takes read the way an unmeasured string is read -- the partial being tuned alone,
     * the others judged but not combined. Until 7 Oct 2026 this way called k2 false on two of the
     * nine takes at exactly 0.30 c, the hysteresis floor, because the false list was taken from
     * "neither clean nor disagreeing" and that is every steady partial but one here; at the piano
     * a clean G#4 had k2 and k4 claimed and coloured after three strikes (Peter, 15:22).
     */
    @Test
    fun noPartialOfTheSamicksCleanF3IsCalledFalseWhenReadUncombined() = cleanF3CallsNothing(combine = false)

    private fun cleanF3CallsNothing(combine: Boolean) {
        val files = takes()
        if (files.isEmpty()) { println("CleanReadingRecordingsTest: no corpus (CLAVIER_DATA or data/recordings); nothing checked"); return }
        assertTrue(files.size == 9, "nine takes of F3: ${files.map { it.name }}")
        val hop = 1024
        val called = LinkedHashMap<String, String>()
        for (f in files) {
            val pcm = WavPcm.read(f.readBytes()); val sr = pcm.sampleRateHz
            // the string's own series from the strike, as the controller measures it
            val f1Guess = 440.0 * Math.pow(2.0, (53 - 69) / 12.0)
            val m = StringMeasure.measure(pcm.samples.copyOfRange(0, minOf(pcm.samples.size, sr * 3)), sr, 53, f1Guess, 2e-4, timeMs = 0L)
                ?: error("${f.name}: the string could not be measured")
            val targets = (1..CleanReading.PARTIALS).associateWith { k -> m.partialHz(k) ?: (k * m.f1Hz * Inharmonicity.ratio(k, m.b)) }
            val r = CleanReading(sr); r.target(targets, reset = true)
            var present = 0
            var i = 0
            while (i + hop <= pcm.samples.size) {
                val res = r.push(pcm.samples.copyOfRange(i, i + hop), 4, combine = combine)
                present = maxOf(present, res.partials.count { it.present })
                for (k in res.falseBeating) called.putIfAbsent("${f.name.substringBefore("_unproc")} k$k", "at %.2f s, spread %.2f c".format(i.toDouble() / sr, res.partials.first { it.k == k }.spreadCents))
                i += hop
            }
            assertTrue(present >= 6, "${f.name}: the partials were read while they stood ($present present at most)")
        }
        assertTrue(called.isEmpty(), "a clean string was called false (combine = $combine): $called")
    }
}
