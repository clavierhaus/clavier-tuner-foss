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
 * While sampling, with A4 measured, the engine's false-beat claims reach the
 * reading and every partial's target stands on the measured B.
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
 * What the engine claims on this corpus with both mended, written by this
 * check's own first run and not typed: **partial 2**, on all three strikes.
 * It agrees with the sound. Partial 3 it does not claim: its spread reads
 * 0.39 / 0.42 / 0.35 c against CleanReading's 0.5 c line. 0.67 Hz at 1321 Hz
 * is a plane separation of 0.88 c, and a phase reader on two planes 3.5 dB
 * apart reads a swing of about 0.4 of the separation -- 0.35 c, as measured.
 * The spread-in-cents proxy is a fraction of the separation that shrinks with
 * the level difference, so it is blind to exactly the class the ear hears.
 * That is the open item of the blueprint's §33 (the live verdict lacks the
 * two-plane measurement false_beats.py makes) and is not asserted here either
 * way: when the detector is mended this doc changes in writing, not this
 * check's expectation of a miss.
 *
 * Says so when it cannot find its corpus, instead of passing (ledger, 5 Oct).
 */
class SamplingFalseBeatRecordingsTest {
    private fun corpus(): File? {
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
    fun whileSamplingTheEnginesFalseClaimsReachTheReadingAndTheTargetsStandOnTheMeasuredB() {
        val dir = corpus() ?: run { println("SamplingFalseBeatRecordingsTest: no corpus (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val left = (1..3).map { File(dir, "Samick_A4-L-s${it}_unproc_20260930-161258.wav") }
        left.forEach { assertTrue(it.isFile, "missing ${it.name}") }
        val pcm = left.map { WavPcm.read(it.readBytes()) }
        val sr = pcm.first().sampleRateHz
        assertTrue(pcm.all { it.sampleRateHz == sr })

        // A4 as the sampling page measures it on Done: from the first strike, with the chosen pitch
        val a4 = 439.2
        val m = StringMeasure.measure(pcm[0].samples.copyOfRange(0, minOf(pcm[0].samples.size, sr * 3)), sr, TuningSession.MIDI_A4, a4, timeMs = 0L)
            ?: fail("A4 could not be measured from ${left[0].name}")
        assertTrue(abs(TuningSession.centsOff(m.f1Hz, a4)) < 5.0, "A4 measured at %.2f Hz, far from the corpus's 439.2".format(m.f1Hz))
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
        assertTrue(claimedOver.isNotEmpty(), "nothing was ever claimed false while sampling: the detector is not running there, or its window does not survive the sampling republishes")
        assertTrue(2 in claimedOver, "the left string's A5 (partial 2, 0.55 Hz by the corpus) was not claimed false while sampling; claimed: $claimedOver")
        for (k in 4..6) assertTrue(k !in claimedOver, "partial $k is clean by the corpus (second plane does not sustain) but was claimed false")
        if (3 !in claimedOver) println("   E6 (partial 3, 0.67 Hz, planes 3.5 dB apart by the corpus) not claimed: the spread proxy reads under 0.5 c on it -- blueprint §33, open")
    }
}
