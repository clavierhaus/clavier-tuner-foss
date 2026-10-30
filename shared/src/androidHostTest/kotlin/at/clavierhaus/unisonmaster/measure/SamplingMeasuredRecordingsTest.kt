package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.TuningSession
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A sampled string is measured when more sound no longer changes it (Peter, 18 Oct 2026, 15:52;
 * SAMICK findings §30), and the page is told: measuring while it still changes, measured once it
 * does not. On the 225's strings of 22 Sept, C4's centre string and A#1's left string: each is
 * measured before the fixed 2.5 s that stood until then and not before the half second of history
 * the criterion needs after the decision (0.853 + 0.512 s) -- both at 1.38 s on 18 Oct 2026, the
 * earliest it can say -- and by the time the measurement's own decay window is full at the latest.
 * At that moment the kept B stands as close to the string measured over 2.5 s as the probe found
 * (§30: 1.4 % in the bass, 0.7 % in the middle, median; here 0.2 % and 0.6 %).
 *
 * Says so when it cannot find its recordings, instead of passing (ledger, 5 Oct).
 */
class SamplingMeasuredRecordingsTest {
    private val hop = 1024

    private fun recordings(): File? {
        val base = System.getenv("CLAVIER_DATA")?.let { File(it) } ?: run {
            var d: File? = File(".").absoluteFile
            while (d != null && !File(d, "data/recordings").isDirectory) d = d.parentFile
            d?.let { File(it, "data/recordings") }
        } ?: return null
        return File(base, "2026-09-22").takeIf { it.isDirectory }
    }

    private class Player(val signal: FloatArray, val after: (Int) -> Unit) : AudioSource {
        override val sampleRateHz = 48_000
        override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
            val buf = FloatArray(bufferSize); var pos = 0; var h = 0
            while (pos + bufferSize <= signal.size) { signal.copyInto(buf, 0, pos, pos + bufferSize); onBuffer(buf); after(h++); pos += bufferSize }
        }
        override fun stop() = Unit
    }

    private fun strike(x: FloatArray): Int { var pos = 0
        while (pos + hop <= x.size) { var sq = 0.0; for (i in pos until pos + hop) sq += x[i].toDouble() * x[i]
            if (20 * log10(sqrt(sq / hop) + 1e-12) > -40) return pos; pos += hop }
        return -1 }

    private class Run(val measuredAtS: Double?, val states: List<TuningController.Measured>, val keptB: Double?, val fullB: Double)

    private fun sample(dir: File, fileName: String, midi: Int): Run {
        val a4File = File(dir, "Boesendorfer_A4-C-s1_unproc_20260922-140748.wav")
        val f = File(dir, fileName)
        assertTrue(a4File.isFile && f.isFile, "missing ${a4File.name} or ${f.name}")
        val a4pcm = WavPcm.read(a4File.readBytes()); val sr = a4pcm.sampleRateHz
        val a4at = strike(a4pcm.samples)
        val a4 = StringMeasure.measure(a4pcm.samples.copyOfRange(a4at, minOf(a4pcm.samples.size, a4at + sr * 3)), sr, TuningSession.MIDI_A4, 443.0, timeMs = 0L)
            ?: fail("A4 could not be measured")
        val x = WavPcm.read(f.readBytes()).samples
        val at = strike(x)
        val lead = sr / 2
        val sig = FloatArray(lead) + x.copyOfRange(at, minOf(x.size, at + 4 * sr))
        val strikeS = lead.toDouble() / sr
        val fullRing = sig.copyOfRange(0, lead + (2.5 * sr).toInt())
        val full = StringMeasure.measure(fullRing, sr, midi, 443.0 * 2.0.pow((midi - 69) / 12.0), timeMs = 0L)?.b ?: fail("$fileName not measurable over 2.5 s")

        lateinit var c: TuningController
        var measuredAt: Double? = null; var keptB: Double? = null
        val states = ArrayList<TuningController.Measured>()
        val player = Player(sig) { h ->
            if (c.acceptReady()) c.acceptLive()
            val v = c.tuning.value ?: return@Player
            if (v.caught != midi) return@Player
            if (states.lastOrNull() != v.caughtMeasured) states += v.caughtMeasured
            if (measuredAt == null && v.caughtMeasured == TuningController.Measured.MEASURED) {
                measuredAt = (h + 1) * hop.toDouble() / sr - strikeS
                keptB = c.snapshot()?.measurements?.firstOrNull { it.midi == midi }?.b
            }
        }
        c = TuningController(player)
        c.applySettings(TunerSettings())
        c.restore(SessionSnapshot(0, a4.f1Hz, TuningSession.MIDI_A4, listOf(a4), sampling = true))
        c.startLive()
        return Run(measuredAt, states, keptB, full)
    }

    @Test
    fun aSampledStringIsMeasuredWhenMoreSoundNoLongerChangesItAndThePageIsTold() {
        val dir = recordings() ?: run { println("SamplingMeasuredRecordingsTest: no recordings (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val mid = sample(dir, "Boesendorfer_C4-C-s1_unproc_20260922-140620.wav", 60)
        val bass = sample(dir, "Boesendorfer_A#1-L-s1_unproc_20260922-135758.wav", 34)
        for ((name, r) in listOf("C4" to mid, "A#1" to bass))
            println("%s: states %s, measured %.2f s after the strike, kept B %.3e against %.3e over 2.5 s".format(name, r.states, r.measuredAtS ?: Double.NaN, r.keptB ?: Double.NaN, r.fullB))
        for ((name, r) in listOf("C4" to mid, "A#1" to bass)) {
            assertEquals(TuningController.Measured.MEASURING, r.states.firstOrNull(), "$name: the page is told it is measuring when the key is caught: ${r.states}")
            assertTrue(TuningController.Measured.SHORT !in r.states, "$name: no strike came, nothing is short: ${r.states}")
        }
        val m = mid.measuredAtS ?: fail("C4 was never measured")
        assertTrue(m >= 1.36 && m < 2.0, "C4 measured %.2f s after the strike: not before the criterion's half second of history, and before the old fixed 2.5 s".format(m))
        val b = bass.measuredAtS ?: fail("A#1 was never measured")
        assertTrue(b >= 1.36 && b <= 2.4, "A#1 measured %.2f s after the strike, by the time the decay window is full".format(b))
        assertTrue(abs(mid.keptB!! / mid.fullB - 1) < 0.015, "C4 kept B %.3e against %.3e over 2.5 s".format(mid.keptB, mid.fullB))
        assertTrue(abs(bass.keptB!! / bass.fullB - 1) < 0.04, "A#1 kept B %.3e against %.3e over 2.5 s".format(bass.keptB, bass.fullB))
    }

    /**
     * Sampling takes keys up to G6 and no higher (Peter, 26 Oct 2026, 21:28). Above it the string's B does not
     * repeat from strike to strike -- on these recordings F6 0.16 c at partial 4, G#6 4.4 c, B6 32 c, E7 109 c -- so
     * a sample there would enter the curve as noise; the tuning above G6 is not touched. G6's centre string is
     * caught and measured; A6's is not caught at all.
     */
    @Test
    fun samplingTakesKeysUpToG6AndNoHigher() {
        val dir = recordings() ?: run { println("SamplingMeasuredRecordingsTest: no recordings (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val g6 = sample(dir, "Boesendorfer_G6-C-s1_unproc_20260922-141210.wav", 91)
        val a6 = sample(dir, "Boesendorfer_A6-C-s1_unproc_20260922-141248.wav", 93)
        println("G6: states %s, measured %s s; A6: states %s".format(g6.states, g6.measuredAtS, a6.states))
        assertTrue(TuningController.Measured.MEASURED in g6.states, "G6 is sampled: ${g6.states}")
        assertTrue(a6.states.isEmpty(), "A6 is not caught while sampling: ${a6.states}")
    }
}

