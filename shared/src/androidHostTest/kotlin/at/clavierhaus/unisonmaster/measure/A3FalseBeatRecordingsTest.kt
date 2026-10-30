package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.TuningSession
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Bösendorfer's A3 centre string of 16 Sept 2026, three strikes: the string FALSE-BEATS.md §6
 * measured at four partials by ESPRIT, its two planes a constant ratio apart so the beat rate
 * grows with the partial -- 0.226, 0.433, 0.595, 0.932 Hz at k = 1..4, repeating across strikes
 * to 0.003-0.005 Hz. By the ear's line (0.5-8 Hz, second plane within 10 dB) partials 3 and 4
 * beat audibly and partial 1 is a slow wow. The live measure read the same series at 0.63-0.68
 * and 0.86-0.92 Hz (8 Oct 2026, research in the session); here the whole engine is driven in
 * sampling with A3 measured and the three strikes played, and the tally must claim 3 and 4
 * and not 1. Partial 2 at 0.43 Hz sits under the floor by less than a two-second window can
 * resolve and is reported, not asserted.
 *
 * Says so when it cannot find its recordings, instead of passing (ledger, 5 Oct).
 */
class A3FalseBeatRecordingsTest {
    private fun recordings(): File? {
        val base = System.getenv("CLAVIER_DATA")?.let { File(it) } ?: run {
            var d: File? = File(".").absoluteFile
            while (d != null && !File(d, "data/recordings").isDirectory) d = d.parentFile
            d?.let { File(it, "data/recordings") }
        } ?: return null
        return File(base, "2026-09-16").takeIf { it.isDirectory }
    }

    private class Player(val signal: FloatArray, val after: (Int) -> Unit) : AudioSource {
        override val sampleRateHz = 48_000
        override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
            val buf = FloatArray(bufferSize); var pos = 0; var hop = 0
            while (pos + bufferSize <= signal.size) { signal.copyInto(buf, 0, pos, pos + bufferSize); onBuffer(buf); after(hop++); pos += bufferSize }
        }
        override fun stop() = Unit
    }

    @Test
    fun theA3CentreStringIsClaimedFalseAtPartials3And4AndNotAt1() {
        val dir = recordings() ?: run { println("A3FalseBeatRecordingsTest: no recordings (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val strikes = listOf("Boesendorfer_A3-C-s1_unproc_20260916-165836.wav", "Boesendorfer_A3-C-s2_unproc_20260916-165849.wav", "Boesendorfer_A3-C-s3_unproc_20260916-165901.wav").map { File(dir, it) }
        strikes.forEach { assertTrue(it.isFile, "missing ${it.name}") }
        val pcm = strikes.map { WavPcm.read(it.readBytes()) }
        val sr = pcm.first().sampleRateHz
        val a4 = 443.0
        val midi = 57
        val m = StringMeasure.measure(pcm[0].samples.copyOfRange(0, minOf(pcm[0].samples.size, sr * 3)), sr, midi, a4 * Math.pow(2.0, (midi - 69) / 12.0), timeMs = 0L)
            ?: fail("A3 could not be measured from ${strikes[0].name}")
        val all = FloatArray(pcm.sumOf { it.samples.size }); var at = 0
        for (p in pcm) { p.samples.copyInto(all, at); at += p.samples.size }
        lateinit var c: TuningController
        val claimed = LinkedHashSet<Int>()
        val player = Player(all) { _ -> c.reading.value?.falsePartials?.let { claimed += it } }
        c = TuningController(player)
        c.applySettings(TunerSettings())
        c.restore(SessionSnapshot(0, a4, midi, listOf(m), sampling = true))
        c.startLive()
        println("A3 centre, three strikes: claimed false ${claimed.sorted()} (FALSE-BEATS §6: 0.23 / 0.43 / 0.60 / 0.93 Hz at k = 1..4)")
        val facts = c.snapshot()?.measurements?.firstOrNull { it.midi == midi }?.falseBeats ?: emptyList()
        println("A3's false beats as kept: " + facts.joinToString { "k%d %.3f Hz %.1f dB".format(it.k, it.rateHz, it.depthDb) })
        assertTrue(3 in claimed, "partial 3 (0.595 Hz by ESPRIT) was not claimed; claimed $claimed")
        assertTrue(4 in claimed, "partial 4 (0.932 Hz by ESPRIT) was not claimed; claimed $claimed")
        assertTrue(1 !in claimed, "partial 1 (0.226 Hz, a wow, not a beat) was claimed")
    }

    /**
     * The same three strikes while tuning, the string alone (Peter, 23 Oct 2026: the reference string is
     * tuned alone, and its false beats are what the unison sets aside): with referenceAlone the tuning
     * screen claims 3 and 4 and not 1, as sampling does, and keeps them with the note; without it nothing
     * is tallied while tuning (FALSE-BEATS §15).
     */
    @Test
    fun theReferenceStringTunedAloneIsClaimedFalseAsSamplingClaimsIt() {
        val dir = recordings() ?: run { println("A3FalseBeatRecordingsTest: no recordings (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val strikes = listOf("Boesendorfer_A3-C-s1_unproc_20260916-165836.wav", "Boesendorfer_A3-C-s2_unproc_20260916-165849.wav", "Boesendorfer_A3-C-s3_unproc_20260916-165901.wav").map { File(dir, it) }
        val pcm = strikes.map { WavPcm.read(it.readBytes()) }
        val sr = pcm.first().sampleRateHz
        val a4 = 443.0; val midi = 57
        val m = StringMeasure.measure(pcm[0].samples.copyOfRange(0, minOf(pcm[0].samples.size, sr * 3)), sr, midi, a4 * Math.pow(2.0, (midi - 69) / 12.0), timeMs = 0L)
            ?: fail("A3 could not be measured")
        val all = FloatArray(pcm.sumOf { it.samples.size }); var at = 0
        for (p in pcm) { p.samples.copyInto(all, at); at += p.samples.size }
        for (alone in listOf(true, false)) {
            lateinit var c: TuningController
            val claimed = LinkedHashSet<Int>()
            val player = Player(all) { _ -> c.reading.value?.falsePartials?.let { claimed += it } }
            c = TuningController(player)
            c.applySettings(TunerSettings())
            c.restore(SessionSnapshot(0, a4, midi, listOf(m.copy(falseBeats = emptyList())), sampling = false))
            c.referenceAlone = alone
            c.startLive()
            val kept = c.snapshot()?.measurements?.firstOrNull { it.midi == midi }?.falseBeats?.map { it.k } ?: emptyList()
            println("A3 tuned, referenceAlone=$alone: claimed ${claimed.sorted()}, kept $kept")
            if (alone) {
                assertTrue(3 in claimed && 4 in claimed, "tuned alone, partials 3 and 4 are claimed: $claimed")
                assertTrue(1 !in claimed, "and not partial 1: $claimed")
                assertTrue(3 in kept && 4 in kept, "and kept with the note: $kept")
            } else assertTrue(claimed.isEmpty(), "with the unison possibly sounding nothing is tallied: $claimed")
        }
    }
}
