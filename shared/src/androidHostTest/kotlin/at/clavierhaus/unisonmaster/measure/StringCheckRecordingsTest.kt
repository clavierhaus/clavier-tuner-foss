package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The one-strike check of a string on the sound (10 Nov 2026), on two strings measured by another method first.
 *
 * The Bösendorfer's A3 centre string of 16 Sept 2026, struck alone three times. ESPRIT (FALSE-BEATS.md §6) measured
 * its two planes a constant ratio apart, so the beat grows with the partial: 0.226 Hz at partial 1, a slow wow under
 * the ear's line, then 0.433, 0.595 and 0.932 Hz. The engine's claim over the three strikes together is partials 2 to
 * 6. On each strike alone the check must claim partial 3, must never claim partial 1, and must claim nothing the three
 * strikes do not; partial 4 on two recordings of the three, as the method did when first built (15 Oct 2026).
 *
 * The Samick's A4 left string of 30 Sept 2026, three strikes. `research/false_beats.py` (SAMICK-2026-09-30-FINDINGS
 * §8): partial 2 beats at 0.533 / 0.551 / 0.549 Hz, its second plane 9.2 dB down; partial 3 at 0.690 / 0.659 / 0.685 Hz,
 * planes 3.5 dB apart, the audible one; partials 4-6 clean, their second plane does not sustain. The check must claim
 * nothing beyond 2 and 3 on any strike, and nothing it did not judge. Measured 10 Nov 2026: partial 2 claimed on the
 * second and third takes (0.59 and 0.63 Hz), partial 3 judged on none (a beat measured in 2, 1 and 2 stretches), so
 * one strike does not catch the audible one here, and says it heard it too briefly.
 *
 * Every partial's verdict is printed, so what the screen will say is seen on the sound. Says so when it cannot find
 * its recordings, instead of passing (ledger, 5 Oct).
 */
class StringCheckRecordingsTest {
    private fun recordings(day: String): File? {
        val base = System.getenv("CLAVIER_DATA")?.let { File(it) } ?: run {
            var d: File? = File(".").absoluteFile
            while (d != null && !File(d, "data/recordings").isDirectory) d = d.parentFile
            d?.let { File(it, "data/recordings") }
        } ?: return null
        return File(base, day).takeIf { it.isDirectory }
    }

    /** The take fed as the microphone would feed it, after half a second of silence so the strike is heard as one. */
    private fun read(file: File, a4: Double): StringCheck.State {
        val pcm = WavPcm.read(file.readBytes())
        val check = StringCheck(pcm.sampleRateHz, { a4 })
        check.begin()
        val hop = 1024
        val quiet = FloatArray(hop)
        repeat((0.5 * pcm.sampleRateHz / hop).toInt()) { check.push(quiet) }
        var at = 0
        while (at + hop <= pcm.samples.size && check.state.value.phase != StringCheck.Phase.DONE) {
            check.push(pcm.samples.copyOfRange(at, at + hop)); at += hop
        }
        // a short take ends before the strike's first seconds are in: silence completes them, so the string can be
        // measured; nothing is read past the recording's end, where a reader's filters would ring on in silence
        var more = (StringCheck.MEASURE_S * pcm.sampleRateHz / hop).toInt() + 1
        while (more-- > 0 && check.state.value.phase == StringCheck.Phase.MEASURING) check.push(quiet)
        check.publishNow()
        return check.state.value
    }

    private fun table(name: String, s: StringCheck.State): String = buildString {
        append("== $name  ${s.phase}  midi=${s.midi} f1=%.3f B=%.2e  %.1f s%s\n".format(s.f1Hz ?: 0.0, s.b ?: 0.0, s.seconds, if (s.cut) "  (cut)" else ""))
        for (v in s.verdicts) append("  %2d  %6.1f dBFS  %d stretches  %s\n".format(v.k, v.levelDbfs, v.stretches,
            if (!v.heard) "not heard" else if (v.falseBeat) "false  %.2f Hz  %.1f dB".format(v.rateHz ?: 0.0, v.depthDb ?: 0.0) else "-"))
    }

    @Test
    fun theA3CentreStringAloneIsFalseAt3AndNeverAt1OnEachStrike() {
        val dir = recordings("2026-09-16") ?: run { println("StringCheckRecordingsTest: no recordings (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val takes = listOf("Boesendorfer_A3-C-s1_unproc_20260916-165836.wav", "Boesendorfer_A3-C-s2_unproc_20260916-165849.wav", "Boesendorfer_A3-C-s3_unproc_20260916-165901.wav").map { File(dir, it) }
        takes.forEach { assertTrue(it.isFile, "missing ${it.name}") }
        val threeStrikes = setOf(2, 3, 4, 5, 6)
        var fours = 0
        for (f in takes) {
            val s = read(f, 443.0)
            println(table(f.name, s))
            val claimed = s.falseBeats.map { it.k }
            assertTrue(s.midi == 57, "${f.name}: named ${s.midi}, not A3")
            assertTrue(3 in claimed, "${f.name}: partial 3 (0.595 Hz by ESPRIT) not claimed; claimed $claimed")
            assertTrue(1 !in claimed, "${f.name}: partial 1 claimed; its 0.23 Hz is a wow under the ear's line")
            assertTrue(threeStrikes.containsAll(claimed), "${f.name}: claimed $claimed, beyond the three strikes' $threeStrikes")
            if (4 in claimed) fours++
            assertTrue(s.judged.containsAll(claimed), "${f.name}: claimed $claimed beyond the judged ${s.judged}")
        }
        assertTrue(fours >= 2, "partial 4 (0.932 Hz by ESPRIT) claimed on $fours of 3 recordings")
    }

    @Test
    fun theSamicksA4LeftStringIsClaimedFalseNowhereButAt2And3() {
        val dir = recordings("2026-09-30/strikes-161258") ?: run { println("StringCheckRecordingsTest: no Samick recordings; nothing checked"); return }
        val takes = (1..3).map { File(dir, "Samick_A4-L-s${it}_unproc_20260930-161258.wav") }
        takes.forEach { assertTrue(it.isFile, "missing ${it.name}") }
        for (f in takes) {
            val s = read(f, 439.2)
            println(table(f.name, s))
            val claimed = s.falseBeats.map { it.k }
            assertTrue(s.midi == 69, "${f.name}: named ${s.midi}, not A4")
            assertTrue(setOf(2, 3).containsAll(claimed), "${f.name}: claimed $claimed; ESPRIT finds partials 4-6 clean")
            assertTrue(s.judged.containsAll(claimed), "${f.name}: claimed $claimed beyond the judged ${s.judged}")
        }
    }

    /**
     * A strike too short to judge is not called clean (10 Nov 2026). The Bösendorfer's A3 centre string of 22 Sept is a
     * take of four seconds; three strikes of the same string on 16 Sept claim partials 2 to 6. On four seconds no
     * partial is measured in the three stretches a claim needs: the check judges none, claims none, and the screen
     * says it was too short, where it had said "no false beat".
     */
    @Test
    fun aStrikeTooShortToJudgeIsNotCalledClean() {
        val dir = recordings("2026-09-22") ?: run { println("StringCheckRecordingsTest: no recordings of 22 Sept; nothing checked"); return }
        val f = dir.listFiles()!!.first { it.name.startsWith("Boesendorfer_A3-C-s1_") }
        val s = read(f, 443.0)
        println(table(f.name, s))
        assertEquals(57, s.midi)
        assertTrue(s.heard.isNotEmpty(), "nothing heard")
        assertTrue(s.judged.isEmpty() && s.falseBeats.isEmpty(), "judged ${s.judged}, claimed ${s.falseBeats.map { it.k }} on ${"%.1f".format(s.seconds)} s")
    }

    /**
     * The strike that ends a check opens the next (10 Nov 2026). Peter's Steinway B, F2-B4, every string alone: the
     * strings struck about seven seconds apart, each before the last had died, and the check, armed again after each,
     * needed five quiet hops before it could hear a strike, so the strike that ended one check was lost and every
     * second string with it (54 checks of 93 strings). Here the Samick's A4 left string struck three times, back to
     * back: the first two checks are done, each ended by the next strike, and the third is under way.
     */
    @Test
    fun theStrikeThatEndsACheckOpensTheNext() {
        val dir = recordings("2026-09-30/strikes-161258") ?: run { println("StringCheckRecordingsTest: no Samick recordings; nothing checked"); return }
        val all = (1..3).map { WavPcm.read(File(dir, "Samick_A4-L-s${it}_unproc_20260930-161258.wav").readBytes()).samples }
        val x = FloatArray(24_000) + all[0] + all[1] + all[2]
        val check = StringCheck(48_000, { 439.2 })
        check.begin()
        val done = ArrayList<StringCheck.State>()
        var at = 0
        while (at + 1024 <= x.size) {
            check.push(x.copyOfRange(at, at + 1024)); at += 1024
            if (check.state.value.phase == StringCheck.Phase.DONE) { done += check.state.value; check.next() }
        }
        assertEquals(2, done.size, "checks done: ${done.map { "${it.midi} ${"%.1f".format(it.seconds)} s" }}")
        assertTrue(done.all { it.midi == 69 && it.cut })
        assertEquals(StringCheck.Phase.LISTENING, check.state.value.phase, "the third strike is not being read")
    }

    /** A recording as the microphone: every buffer of it, in order, when started. */
    private class Played(var signal: FloatArray = FloatArray(0)) : AudioSource {
        override val sampleRateHz = 48_000
        override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
            var pos = 0; val buf = FloatArray(bufferSize)
            while (pos + bufferSize <= signal.size) { signal.copyInto(buf, 0, pos, pos + bufferSize); onBuffer(buf); pos += bufferSize }
        }
        override fun stop() = Unit
    }

    /**
     * Through the controller, as the app runs it: during a sampling pass the check is started, the Samick's A4 left
     * string struck and left to ring for its 8.7 s, then struck again, which ends the first check (a recording's zeros
     * would not: the readers ring on in digital silence, which no room gives). The check has the microphone to itself
     * (10 Nov 2026): the first strike is filed as a checked A4 with what it found, and the sampling caught nothing of
     * either -- no key heard, no sample. Then the microphone is the engine's again, and a strike reaches the sampling.
     */
    @Test
    fun theCheckHasTheMicrophoneAndTheTuningHearsNothingOfIt() {
        val dir = recordings("2026-09-30/strikes-161258") ?: run { println("StringCheckRecordingsTest: no Samick recordings; nothing checked"); return }
        val take = WavPcm.read(File(dir, "Samick_A4-L-s2_unproc_20260930-161258.wav").readBytes()).samples
        val next = WavPcm.read(File(dir, "Samick_A4-L-s3_unproc_20260930-161258.wav").readBytes()).samples
        val src = Played()
        val c = TuningController(src) { 1_000L }
        c.restore(SessionSnapshot(savedAtMs = 1L, a4Hz = 439.2, currentMidi = 69, measurements = emptyList(), sampling = true))
        c.startCheck()
        src.signal = FloatArray(24_000) + take + next
        c.startLive(); c.stopLive()
        val last = assertNotNull(c.lastCheck.value, "the strike was not checked: ${c.checking.value}")
        assertEquals(69, last.state.midi)
        assertTrue(last.state.cut, "the second strike ended the first")
        assertEquals(listOf(69), c.falseStrings.value.keys)
        assertTrue(setOf(2, 3).containsAll(c.falseStrings.value.of(69).single().beats.map { it.k }))
        assertEquals(null, c.heardMidi.value, "the sampling named the key the check was given")
        assertTrue(c.snapshot()!!.measurements.isEmpty(), "the sampling kept a sample of a check")
        // the microphone back: the same strike reaches the sampling, which names it
        c.stopCheck()
        src.signal = FloatArray(24_000) + take
        c.startLive(); c.stopLive()
        assertEquals(69, c.heardMidi.value, "with the check stopped the sampling hears the strike")
    }
}
