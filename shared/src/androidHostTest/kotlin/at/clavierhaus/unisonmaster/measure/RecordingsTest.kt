package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.tuning.Notes
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The reader on the recordings as they are: the committed WAV files of
 * data/recordings, decoded and nothing else. Each single-string take is
 * read on its listening partial (PartialMap) against equal temperament on
 * A4 440 — the reference is only a place to measure from; the cents are
 * the result — and the three strikes of a note must agree. The table is
 * printed with every file's name. Skipped where the recordings are not
 * checked out (the foss repository carries none).
 */
class RecordingsTest {

    private data class Wav(val sampleRate: Int, val samples: FloatArray)

    private fun readWav(f: File): Wav {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(String(ByteArray(4).also { b.get(it) }) == "RIFF"); b.getInt()
        require(String(ByteArray(4).also { b.get(it) }) == "WAVE")
        var sampleRate = 0; var channels = 1; var bits = 16
        while (b.remaining() >= 8) {
            val id = String(ByteArray(4).also { b.get(it) }); val size = b.getInt()
            if (id == "fmt ") {
                b.getShort(); channels = b.getShort().toInt(); sampleRate = b.getInt(); b.getInt(); b.getShort(); bits = b.getShort().toInt()
                b.position(b.position() + size - 16)
            } else if (id == "data") {
                require(bits == 16 && channels == 1) { "expected 16-bit mono, got $bits-bit $channels-channel" }
                val n = size / 2
                val s = FloatArray(n) { b.getShort() / 32768f }
                return Wav(sampleRate, s)
            } else b.position(b.position() + size)
        }
        error("no data chunk in ${f.name}")
    }

    private fun recordingsDir(): File? {
        System.getenv("CLAVIER_DATA")?.let { return File(it).takeIf { d -> d.isDirectory } }
        var d: File? = File(".").absoluteFile
        while (d != null) {
            val c = File(d, "data/recordings")
            if (c.isDirectory) return c
            d = d.parentFile
        }
        return null
    }

    private data class Result(val file: String, val note: String, val k: Int, val cents: Double, val spread: Double, val levelDbfs: Double, val quality: Double)

    /** The take read on its listening partial over the decay: 0.5 s to 2.5 s after the strike. */
    private fun measure(f: File): Result? {
        val m = Regex("""^(D|Boesendorfer)_([A-G]#?\d)-([CLRU])-s(\d)""").find(f.name) ?: return null
        val note = m.groupValues[2]
        val midi = Notes.midi(note) ?: return null
        val wav = readWav(f)
        val k = PartialMap.listening(midi)
        val target = k * 440.0 * 2.0.pow((midi - 69) / 12.0)
        val hop = 1024
        // the strike: the first hop above −40 dBFS
        var strike = -1
        var pos = 0
        while (pos + hop <= wav.samples.size) {
            var sq = 0.0
            for (i in pos until pos + hop) sq += wav.samples[i].toDouble() * wav.samples[i]
            if (20 * log10(sqrt(sq / hop) + 1e-12) > -40) { strike = pos; break }
            pos += hop
        }
        if (strike < 0) return null
        val reader = PhaseReader(wav.sampleRate, target)
        val readings = ArrayList<PhaseReader.Reading>()
        val buf = FloatArray(hop)
        pos = 0
        while (pos + hop <= wav.samples.size) {
            wav.samples.copyInto(buf, 0, pos, pos + hop)
            val r = reader.push(buf)
            pos += hop
            val t = (pos - strike).toDouble() / wav.sampleRate
            // over the decay, and only while the partial is there to be read: the
            // display goes dark on a low quality, and so does the table
            if (r != null && t >= 0.5 && t <= 2.5 && r.quality >= 0.5) readings += r
        }
        if (readings.size < 15) return Result(f.name, note, k, Double.NaN, Double.NaN, Double.NaN, 0.0)
        val cents = readings.map { it.cents }.sorted()
        val median = cents[cents.size / 2]
        val spread = cents[(cents.size * 0.9).toInt()] - cents[(cents.size * 0.1).toInt()]
        return Result(f.name, note, k, median, spread, readings.map { it.levelDbfs }.max(), readings.map { it.quality }.average())
    }

    @Test
    fun everySingleStringTakeReadsSteadilyOnItsListeningPartialAndTheStrikesAgree() {
        val dir = recordingsDir()
        if (dir == null) { println("RecordingsTest: no data/recordings here, nothing read"); return }
        val files = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".wav") && !it.name.contains("-U-") }.sortedBy { it.name }.toList()
        assertTrue(files.isNotEmpty(), "no recordings under $dir")
        val all = files.mapNotNull { measure(it) }
        println("file                                                 note  k   cents   spread   level   quality")
        for (r in all) {
            if (r.cents.isNaN()) println("%-52s %-4s %2d   faded before 0.5 s, or too weak to read".format(r.file, r.note, r.k))
            else println("%-52s %-4s %2d %+7.2f %8.2f %7.1f %8.2f".format(r.file, r.note, r.k, r.cents, r.spread, r.levelDbfs, r.quality))
        }
        val results = all.filter { !it.cents.isNaN() }
        assertTrue(results.size >= files.size * 0.8, "read ${results.size} of ${files.size}")
        // What the set says on 22 September, and what the assertions hold:
        // the three strikes of a string agree to a fraction of a cent (C1's
        // sixth partial to 0.07 c), and a string's reading over two seconds of
        // decay moves by a fraction of a cent to two cents — the 225's C5 on
        // its second partial moves 2.1–2.4 c on every strike, which is the
        // string, not the reader. Single takes that moved more are named
        // here for the next session; a note fails only when its strikes
        // disagree or most of them are unsteady.
        val moved = results.filter { it.spread > 2.0 }
        if (moved.isNotEmpty()) println("moved more than 2 cents over the decay: ${moved.map { "${it.file} (%.1f c)".format(it.spread) }}")
        val byNote = results.groupBy { it.file.substringBefore("-s") }
        val disagree = byNote.filter { (_, rs) -> rs.size >= 2 && rs.maxOf { it.cents } - rs.minOf { it.cents } > 1.5 }
        assertTrue(disagree.isEmpty(), "strikes of one string more than 1.5 cents apart: ${disagree.keys}")
        val unsteady = byNote.filter { (_, rs) -> rs.size >= 2 && rs.map { it.spread }.sorted()[rs.size / 2] > 2.5 }
        assertTrue(unsteady.isEmpty(), "strings whose typical strike moved more than 2.5 cents over the decay: ${unsteady.keys}")
    }
}
