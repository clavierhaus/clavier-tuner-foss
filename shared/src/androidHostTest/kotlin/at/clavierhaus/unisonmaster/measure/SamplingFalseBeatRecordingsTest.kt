package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import at.clavierhaus.unisonmaster.tuning.TuningSession
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * While sampling, with A4 measured, every partial's target stands on the measured B, and since 10 Nov 2026
 * nothing is claimed false there: the false beats are the string check's, one strike of one string alone.
 * The history below is of the claims sampling made until then.
 *
 * The sound first (Amendment 8). `research/false_beats.py` on the three left
 * strikes of `2026-09-30/strikes-161258`, in `SAMICK-2026-09-30-FINDINGS.md`
 * §8: partial 2 (A5) false-beats at 0.533 / 0.551 / 0.549 Hz, second plane
 * 9.2 dB down -- a false beat; partial 3 (E6) at 0.690 / 0.659 / 0.685 Hz,
 * planes 3.5 dB apart -- the audible one; partials 4-6 clean, their second
 * plane does not sustain.
 *
 * Two faults this guards (6 Oct 2026, Peter's word for "b"), each of which
 * alone leaves the claim empty:
 *  1. CleanReading, the detector that feeds FalseBeatTally, ran `!s.sampling`
 *     (dfe615a, 28 Sept, written about the combined hz), so nothing was ever
 *     tallied while sampling.
 *  2. Every published generation reset CleanReading's window, and sampling
 *     publishes a strike twice (caught, then remeasured), so the window was
 *     wiped mid-strike before a spread could stand.
 *
 * What the engine claims on these recordings, since 8 Oct 2026 by the beat measured from the band
 * power (BeatMeasure: rate 0.5-8 Hz, second plane within 10 dB, the findings' own line): partials
 * 2 and 3 on all three strikes -- the §8 answer -- and not 1 (0.25 Hz, too slow), not 4-6 (no
 * second plane). From 7 Oct to 8 Oct the verdict was a spread over 0.5 c and claimed only 2;
 * 3 read 0.39 / 0.42 / 0.35 c against that line (§20) and the doc here said why in words rather
 * than assert the miss. The miss is gone, and so are the words.
 *
 * Says so when it cannot find its recordings, instead of passing (ledger, 5 Oct).
 */
class SamplingFalseBeatRecordingsTest {
    private fun recordings(): File? {
        val base = System.getenv("CLAVIER_DATA")?.let { File(it) } ?: run {
            var d: File? = File(".").absoluteFile
            while (d != null && !File(d, "data/recordings").isDirectory) d = d.parentFile
            d?.let { File(it, "data/recordings") }
        } ?: return null
        return File(base, "2026-09-30/strikes-161258").takeIf { it.isDirectory }
    }

    private class Player(val signal: FloatArray, val after: (Int) -> Unit) : AudioSource {
        override val sampleRateHz = 48_000
        override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
            val buf = FloatArray(bufferSize)
            var pos = 0; var hop = 0
            while (pos + bufferSize <= signal.size) {
                signal.copyInto(buf, 0, pos, pos + bufferSize)
                onBuffer(buf); after(hop++)
                pos += bufferSize
            }
        }
        override fun stop() = Unit
    }

    @Test
    fun whileSamplingNothingIsClaimedFalseAndTheTargetsStandOnTheMeasuredB() {
        val dir = recordings() ?: run { println("SamplingFalseBeatRecordingsTest: no recordings (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val left = (1..3).map { File(dir, "Samick_A4-L-s${it}_unproc_20260930-161258.wav") }
        left.forEach { assertTrue(it.isFile, "missing ${it.name}") }
        val pcm = left.map { WavPcm.read(it.readBytes()) }
        val sr = pcm.first().sampleRateHz
        assertTrue(pcm.all { it.sampleRateHz == sr })

        // A4 as the sampling page measures it on Done: from the first strike, with the chosen pitch
        val a4 = 439.2
        val m = StringMeasure.measure(pcm[0].samples.copyOfRange(0, minOf(pcm[0].samples.size, sr * 3)), sr, TuningSession.MIDI_A4, a4, timeMs = 0L)
            ?: fail("A4 could not be measured from ${left[0].name}")
        assertTrue(abs(TuningSession.centsOff(m.f1Hz, a4)) < 5.0, "A4 measured at %.2f Hz, far from the recordings' 439.2".format(m.f1Hz))
        assertTrue(m.b > 1e-4, "A4's B measured as ${m.b}")

        val all = FloatArray(pcm.sumOf { it.samples.size })
        var at = 0
        for (p in pcm) { p.samples.copyInto(all, at); at += p.samples.size }

        lateinit var c: TuningController
        val claimedOver = LinkedHashSet<Int>()
        var hopsInSampling = 0
        var usedHops = 0; var liveHops = 0; var readings = 0
        val trace = ArrayList<String>()
        val player = Player(all) { hop ->
            val t = c.tuning.value
            if (t?.sampling == true) hopsInSampling++
            val r = c.reading.value
            if (r != null) readings++
            if (r?.usedPartials?.isNotEmpty() == true) usedHops++
            if (r?.live == true) liveHops++
            r?.falsePartials?.let { claimedOver += it }
            if (hop % 40 == 0) trace += "%5.1fs k=%s live=%s hz=%s used=%s false=%s coarse=%s".format(
                (hop + 1) * 1024.0 / sr, r?.k, r?.live, r?.hz?.let { "%.2f".format(it) }, r?.usedPartials, r?.falsePartials, r?.coarseCents?.let { "%.0f".format(it) })
        }
        c = TuningController(player)
        c.applySettings(TunerSettings())
        c.restore(SessionSnapshot(0, a4, TuningSession.MIDI_A4, listOf(m), sampling = true))
        val t0 = c.tuning.value ?: fail("no session after restore")
        assertTrue(t0.sampling, "the session must be in sampling")
        assertTrue(t0.measured.contains(TuningSession.MIDI_A4), "A4 must count as measured")

        // the targets: the chosen pitch times the string's own ratios, from its measured B (Rule 2)
        for (p in c.targets.value) {
            val want = p.k * a4 * Inharmonicity.ratio(p.k, m.b)
            assertTrue(abs(TuningSession.centsOff(p.hz, want)) < 0.01, "target of partial ${p.k}: %.3f Hz, from the measured B %.3f".format(p.hz, want))
        }
        assertTrue(c.targets.value.any { it.k == 3 }, "partial 3 has a target: ${c.targets.value.map { it.k }}")

        c.startLive()
        println("sampling, A4 measured f1=%.3f B=%.2e; hops in sampling %d; readings %d, live %d, combined %d; listening=%s; targets=%s; partials ever claimed false: %s"
            .format(m.f1Hz, m.b, hopsInSampling, readings, liveHops, usedHops, t0.listening, c.targets.value.map { "%d:%.1f".format(it.k, it.hz) }, claimedOver.sorted()))
        println(trace.joinToString("\n"))
        assertTrue(hopsInSampling > 0, "the controller never ran in sampling")
        // Since 10 Nov 2026 sampling claims nothing: the string check does, of one string alone (StringCheckRecordingsTest
        // reads these three takes). Until then sampling claimed 2 and 3 here and kept them with A4.
        assertTrue(claimedOver.isEmpty(), "sampling claimed $claimedOver false; the string check claims, sampling measures")
        val facts = c.snapshot()?.measurements?.firstOrNull { it.midi == TuningSession.MIDI_A4 }?.falseBeats ?: emptyList()
        assertTrue(facts.isEmpty(), "sampling kept false beats with A4: $facts")
    }
}
