package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.NoteMeasurement
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.TuningSession
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The strobe's other bands stand where the strike puts them, never where a model puts them (Peter, 31 Oct 2026:
 * "Measure truthfully as fast as possible"). Until that day every band but the one being tuned was read against
 * the model's place for its partial for the first 1.5 s of a strike and against the string's own series after:
 * a band that moved by the model's error at 1.5 s, on a string that had not moved (findings §34).
 *
 * The recordings: single strings of the 225 (22 Sept, Part A, one string, the others muted), each played through
 * the controller with the session restored on that note and every other measured note in. Asserted with the
 * equalizer at its shortest: over every hop that draws band k in the first 1.5 s, the median of |band k - tuned
 * band| stays within [MAX_OFFSET_C] -- a band placed by the model carries the model's error there instead (up to
 * 5.6 c on these notes on the code before the change). With the equalizer over the whole strike the same is printed
 * and not asserted: there a band stands off the tuned one by as much as its partial's ratio moves while the string
 * rings (C3's partials 6-12 by about 2.7 c), which is the string, not an error. "from" is seconds from the start of
 * the file, not from the strike.
 *
 * Says so when it cannot find its recordings, instead of passing (ledger, 5 Oct).
 */
class BandsFromStrikeRecordingsTest {
    private val base = TunerSettings(lowestKeyMidi = 17, lowestUnwoundMidi = 40, temperamentFirst = false, octaveTreble = OctaveType.O4_1)

    private fun day(): File? {
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
            val buf = FloatArray(bufferSize); var pos = 0; var hop = 0
            while (pos + bufferSize <= signal.size) { signal.copyInto(buf, 0, pos, pos + bufferSize); onBuffer(buf); after(hop++); pos += bufferSize }
        }
        override fun stop() = Unit
    }

    private fun measured(dir: File): Pair<Double, Map<Int, NoteMeasurement>> {
        val ms = LinkedHashMap<Int, NoteMeasurement>()
        for (f in dir.listFiles()!!.filter { it.name.startsWith("Boesendorfer_") && it.name.endsWith(".wav") }.sortedBy { it.name }) {
            val note = f.name.substringAfter("Boesendorfer_").substringBefore("-")
            val midi = Notes.midi(note) ?: continue
            val pcm = WavPcm.read(f.readBytes()); val sr = pcm.sampleRateHz
            val guess = 443.0 * Math.pow(2.0, (midi - 69) / 12.0)
            StringMeasure.measure(pcm.samples.copyOfRange(0, minOf(pcm.samples.size, sr * 3)), sr, midi, guess, timeMs = 0L)?.let { ms[midi] = it }
        }
        return (ms[TuningSession.MIDI_A4]?.f1Hz ?: 443.0) to ms
    }

    /** At the equalizer's shortest every band follows the tuned band within the string's wobble: asserted. */
    @Test
    fun theOtherBandsStandWhereTheStrikePutsThem() = run(base.copy(bandAveragingS = TunerSettings.BAND_AVERAGING_STEPS.first()), assert = true)

    /** Over the whole strike the bands show how each partial's ratio moves while the string rings: printed, not asserted. */
    @Test
    fun overTheWholeStrikeTheBandsShowTheRatioMoving() = run(base.copy(bandAveragingS = 0.0), assert = false)

    private fun run(settings: TunerSettings, assert: Boolean) {
        val dir = day() ?: run { println("BandsFromStrikeRecordingsTest: no recordings (CLAVIER_DATA or data/recordings); nothing checked"); return }
        val (a4, ms) = measured(dir)
        val notes = listOf("C3", "E3", "G3", "A3", "C4", "E4")
        val worst = ArrayList<String>()
        var checked = 0
        for (note in notes) {
            val midi = Notes.midi(note)!!
            val f = dir.listFiles()!!.filter { it.name.startsWith("Boesendorfer_$note-") && it.name.endsWith(".wav") }.minByOrNull { it.name } ?: continue
            val pcm = WavPcm.read(f.readBytes()); val sr = pcm.sampleRateHz
            val limitHop = (1.5 * sr / 1024).toInt()
            val offsets = HashMap<Int, ArrayList<Double>>(); val first = HashMap<Int, Int>()
            lateinit var c: TuningController
            var lK = 0
            val player = Player(pcm.samples) { hop ->
                if (hop > limitHop) return@Player
                val lp = c.livePartials.value.associateBy { it.k }
                val tuned = lp[lK] ?: return@Player
                for ((k, p) in lp) if (k != lK) { first.putIfAbsent(k, hop); offsets.getOrPut(k) { ArrayList() } += p.cents - tuned.cents }
            }
            c = TuningController(player)
            c.applySettings(settings)
            c.restore(SessionSnapshot(0, a4, midi, ms.values.filter { it.midi != midi }.toList(), sampling = false))
            lK = c.tuning.value!!.listening.k
            c.startLive()
            val med = offsets.mapValues { (_, v) -> v.map { abs(it) }.sorted().let { it[it.size / 2] } }
            println("%s %-3s L=k%d | %s".format(if (settings.bandAveragingS == 0.0) "whole strike" else "%.2f s".format(settings.bandAveragingS), note, lK, med.keys.sorted().joinToString("  ") { k -> "k%d from %.2fs %.2fc".format(k, first.getValue(k) * 1024.0 / sr, med.getValue(k)) }))
            for ((k, m) in med) { checked++; if (m > MAX_OFFSET_C) worst += "$note k$k ${"%.2f".format(m)} c" }
        }
        if (!assert) return
        assertTrue(checked > 0, "no band other than the tuned one was drawn on any note: nothing checked")
        assertTrue(worst.isEmpty(), "bands standing off the tuned band by more than $MAX_OFFSET_C c (median, first 1.5 s): $worst")
    }

    companion object { const val MAX_OFFSET_C = 0.5 }
}
