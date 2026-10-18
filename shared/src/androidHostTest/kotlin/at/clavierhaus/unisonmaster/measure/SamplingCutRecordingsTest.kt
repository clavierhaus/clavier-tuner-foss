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
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A string accepted the moment its key is caught, and the next strike before its measurement is
 * complete: what is kept is the string measured from everything heard up to that strike, not the
 * measurement of the decision 0.85 s after its own (SAMICK findings §29: in the bass 10.6 % off
 * the full measurement, median of 209 takes, where the strikes of one string agree to 1.3 %).
 * The 225's B0, left string (22 Sept), struck again 1.1 s after itself, before the half second
 * of history the measurement needs to call itself complete (1.36 s at the earliest). Until 18 Oct 2026 the
 * decision's measurement stood, because the second measurement came at a fixed 2.5 s and only if
 * no strike had come first.
 *
 * Says so when it cannot find its corpus, instead of passing (ledger, 5 Oct).
 */
class SamplingCutRecordingsTest {
    private val hop = 1024

    private fun corpus(): File? {
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

    @Test
    fun aStringStruckAgainBeforeItsMeasurementIsCompleteKeepsAllItHadUpToTheStrike() {
        val dir = corpus() ?: run { println("SamplingCutRecordingsTest: no corpus (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val a4File = File(dir, "Boesendorfer_A4-C-s1_unproc_20260922-140748.wav")
        val bassFile = File(dir, "Boesendorfer_B0-L-s1_unproc_20260922-135541.wav")
        assertTrue(a4File.isFile && bassFile.isFile, "missing ${a4File.name} or ${bassFile.name}")
        val a4pcm = WavPcm.read(a4File.readBytes()); val sr = a4pcm.sampleRateHz
        val a4at = strike(a4pcm.samples)
        val a4 = StringMeasure.measure(a4pcm.samples.copyOfRange(a4at, minOf(a4pcm.samples.size, a4at + sr * 3)), sr, TuningSession.MIDI_A4, 443.0, timeMs = 0L)
            ?: fail("A4 could not be measured")
        val bass = WavPcm.read(bassFile.readBytes()).samples
        val at = strike(bass)
        // half a second of the room, the string from its strike for 1.1 s, then the same string struck again
        val lead = sr / 2; val gap = (1.1 * sr).toInt() / hop * hop
        val sig = FloatArray(lead + gap + 3 * sr)
        bass.copyInto(sig, lead, at, at + gap)
        bass.copyInto(sig, lead + gap, at, minOf(bass.size, at + 3 * sr))
        val secondHop = (lead + gap) / hop     // the hop the second strike's take begins in; the engine hears it rise in this one or the next

        lateinit var c: TuningController
        val keptB = HashMap<Int, Double>()
        var cutHop = -1
        val player = Player(sig) { h ->
            if (c.acceptReady()) c.acceptLive()
            c.snapshot()?.measurements?.firstOrNull { it.midi == 23 }?.let { keptB[h] = it.b }
            if (cutHop < 0 && c.tuning.value?.caughtMeasured == TuningController.Measured.SHORT) cutHop = h
        }
        c = TuningController(player)
        c.applySettings(TunerSettings())
        c.restore(SessionSnapshot(0, a4.f1Hz, TuningSession.MIDI_A4, listOf(a4), sampling = true))
        c.startLive()

        val decision = keptB.filterKeys { it < cutHop }.minByOrNull { it.key }?.value ?: fail("B0 was never caught and kept before the second strike: ${keptB.keys.sorted().take(5)}")
        assertTrue(cutHop in secondHop..secondHop + 1, "the second strike is heard at hop $cutHop and the measurement called short there; the take begins in hop $secondHop")
        val atCut = keptB[cutHop] ?: fail("nothing kept at the second strike's hop")
        // everything heard up to the second strike: the ring as it stood before the hop the strike was heard in
        val upTo = sig.copyOfRange(maxOf(0, cutHop * hop - 3 * sr), cutHop * hop)
        val want = StringMeasure.measure(upTo, sr, 23, 443.0 * Math.pow(2.0, (23 - 69) / 12.0), timeMs = 0L)?.b ?: fail("B0 not measurable up to the strike")
        println("B0: B kept at the decision %.3e, at the second strike %.3e, measured from all up to it %.3e".format(decision, atCut, want))
        assertTrue(abs(decision / want - 1) > 0.01, "the take does not tell the two apart: the decision's B %.3e, all up to the strike %.3e".format(decision, want))
        assertTrue(abs(atCut / want - 1) < 0.003,
            "kept at the second strike: B %.3e, where everything heard up to it measures %.3e (the decision's was %.3e)".format(atCut, want, decision))
    }
}
