package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.NoteMeasurement
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.TuningSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The engine on the recordings of 22 September (docs/ENGINE.md): every
 * single string of Part A measured as Done measures it, the session built
 * from those measurements, and the six tuning recordings played through the
 * controller as if the phone were on the music desk. Nothing here is a
 * constant of that piano: the piano's A4, its octave widths and its
 * inharmonicity come out of the recordings, and the assertions are about
 * the engine — that it reads what was played and that a target built from
 * one string's measured partial is where the tuner's own octave put the
 * next. Skipped where the recordings are not checked out.
 */
class EngineRecordingsTest {
    private fun readWav(f: File): Pair<Int, FloatArray> {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        b.position(12)
        var sr = 0
        while (b.remaining() >= 8) {
            val id = String(ByteArray(4).also { b.get(it) }); val size = b.getInt()
            when (id) {
                "fmt " -> { b.getShort(); b.getShort(); sr = b.getInt(); b.position(b.position() + size - 8) }
                "data" -> return sr to FloatArray(size / 2) { b.getShort() / 32768f }
                else -> b.position(b.position() + size)
            }
        }
        error("no data in ${f.name}")
    }

    private fun day(): File? {
        val base = System.getenv("CLAVIER_DATA")?.let { File(it) } ?: run {
            var d: File? = File(".").absoluteFile
            while (d != null && !File(d, "data/recordings").isDirectory) d = d.parentFile
            d?.let { File(it, "data/recordings") }
        }
        return base?.let { File(it, "2026-09-22") }?.takeIf { File(it, "Boesendorfer_manifest.csv").isFile }
    }

    private val settings = TunerSettings(lowestKeyMidi = 17, lowestUnwoundMidi = 40, temperamentFirst = false)

    /** Part A: one string per key; where a key was taken again, the last take. */
    private fun partA(dir: File): Map<Int, File> {
        val rows = File(dir, "Boesendorfer_manifest.csv").readLines().drop(1).map { it.split(",") }
        val out = LinkedHashMap<Int, File>()
        for (r in rows.filter { it[3].startsWith("A") }.sortedBy { it[0] }) {
            val take = r[1]
            if (!Regex("""^[A-G]#?\d-[LC]-s1$""").matches(take)) continue
            val midi = Notes.midi(take.substringBefore("-")) ?: continue
            out[midi] = File(dir, r[2])
        }
        return out
    }

    private fun measureAll(files: Map<Int, File>, a4: Double): Map<Int, NoteMeasurement> =
        files.mapNotNull { (midi, f) ->
            val (sr, x) = readWav(f)
            // where the string is: the finder on the listening partial, a
            // semitone either side of equal temperament; then Done's measurement
            val k = PartialMap.listening(midi)
            val et = TuningSession.targetF1(midi, a4)
            val onset = StringMeasure.decay(x, sr) ?: return@mapNotNull null
            val found = PartialFinder(onset.copyOfRange(0, minOf(onset.size, 32768)), sr).near(k * et, 60.0)
            val f1Guess = found?.let { it.hz / (k * StringMeasure.ratio(k, StringMeasure.DEFAULT_B)) } ?: et
            StringMeasure.measure(x, sr, midi, f1Guess)?.let { midi to it }
        }.toMap()

    private fun measured(dir: File): Pair<Double, Map<Int, NoteMeasurement>> {
        val files = partA(dir)
        val a4File = files[69] ?: error("no A4 take")
        val (sr, x) = readWav(a4File)
        val a4Hz = StringMeasure.measure(x, sr, 69, 443.0)!!.f1Hz
        val a4 = TuningSession.roundToTenth(a4Hz)
        return a4 to measureAll(files, a4)
    }

    @Test
    fun everyStringIsMeasuredAndTheOctavesItWasTunedToAreFound() {
        val dir = day() ?: run { println("EngineRecordingsTest: no recordings of 22 September here"); return }
        val (a4, ms) = measured(dir)
        val files = partA(dir)
        println("A4 of the piano as it stood: %.1f Hz; %d of %d strings measured".format(a4, ms.size, files.size))
        val missing = files.keys - ms.keys
        if (missing.isNotEmpty()) println("not measured: ${missing.sorted().map { Notes.name(it) }}")
        assertTrue(ms.size >= files.size - 2, "measured ${ms.size} of ${files.size}")
        val s = TuningSession(a4, settings)
        for (m in ms.values) s.record(m)
        println("note  k  partials   B        f1 vs ET   octave   width (+ = wide)")
        val widths = HashMap<String, MutableList<Double>>()
        for (midi in ms.keys.sorted()) {
            val m = s.measurements[midi] ?: continue
            val l = s.listening(midi)
            val own = m.partialHz(l.k)
            val region = when {
                l.source != TuningSession.Source.OCTAVE -> "temperament"
                settings.isWound(midi) -> "wound"
                midi <= settings.bassBoundaryMidi -> "bass"
                midi > TunerSettings.TEMPERAMENT_HIGH -> "treble"
                else -> "middle"
            }
            val width = if (own == null) null else {
                val dev = TuningSession.centsOff(own, l.targetHz)
                if (l.source == TuningSession.Source.OCTAVE && midi < l.refMidi!!) -dev else dev
            }
            if (width != null && l.source != TuningSession.Source.REFERENCE) widths.getOrPut(region) { ArrayList() } += width
            println("%-4s %2d  %2d      %.2e  %+7.2f   %-6s  %s".format(
                Notes.name(midi), l.k, m.partials.size, m.b, TuningSession.centsOff(m.f1Hz, TuningSession.targetF1(midi, a4)),
                l.type?.label?.let { "$it ${Notes.name(l.refMidi!!)}" } ?: region,
                width?.let { "%+6.2f".format(it) } ?: "partial ${l.k} not measured"))
        }
        for ((region, v) in widths) {
            val sorted = v.sorted()
            println("%-11s n=%2d  median %+5.2f c   p10..p90 %+5.1f..%+5.1f".format(region, v.size, sorted[v.size / 2], sorted[v.size / 10], sorted[v.size * 9 / 10]))
        }
        // the temperament octave was tuned to equal temperament on this A4 and
        // is found there; the middle and the treble were tuned by beatless
        // octaves and are found within a few cents of them. How wide the bass
        // was tuned is the tuner's, and is printed, not asserted.
        fun median(r: String) = widths[r]!!.sorted().let { it[it.size / 2] }
        assertTrue(abs(median("temperament")) < 2.0, "temperament octave median ${median("temperament")}")
        assertTrue(abs(median("middle")) < 3.0, "middle median ${median("middle")}")
        assertTrue(abs(median("treble")) < 3.0, "treble median ${median("treble")}")
    }

    /** Plays a file in hops, and hands over the controller's state after each one. */
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

    /**
     * The notes of the tuning recordings, in the order of their time stamps,
     * as the tuner played them (data/recordings/2026-09-22/NOTES.md). A
     * fact of the recordings, not of the engine: an octave's partials are
     * all partials of the note below, so the audio alone cannot tell C2
     * tuned from C3 ringing.
     */
    private val played = listOf("C2", "E2", "A4", "C6", "C7", "C3")

    @Test
    fun theTuningRecordingsReadThroughTheControllerEndWhereTheTunerLeftTheString() {
        val dir = day() ?: run { println("EngineRecordingsTest: no recordings of 22 September here"); return }
        val (a4, ms) = measured(dir)
        val sessions = dir.listFiles()!!.filter { it.name.startsWith("session_") }.sortedBy { it.name }
        assertTrue(sessions.isNotEmpty())
        assertTrue(sessions.size == played.size, "${sessions.size} tuning recordings, ${played.size} notes in NOTES.md")
        for ((f, note) in sessions.zip(played)) {
            val (sr, x) = readWav(f)
            val midi = Notes.midi(note)!!
            val timeline = ArrayList<String>()
            val shown = ArrayList<Pair<Double, Double>>()      // (t, cents) of live readings
            lateinit var c: TuningController
            var coarse = 0
            val player = Player(x) { hop ->
                val t = (hop + 1) * 1024.0 / sr
                val r = c.reading.value ?: return@Player
                if (r.live) shown += t to r.cents!!
                if (r.coarseCents != null) coarse++
                if (hop % 47 == 0) timeline += "%5.1f %s".format(t, when {
                    r.live -> "%+6.1f".format(r.cents)
                    r.coarseCents != null -> "far %+4.0f".format(r.coarseCents)
                    r.settling -> "  ~   "
                    else -> "  ·   "
                })
            }
            c = TuningController(player)
            c.applySettings(settings)
            c.restore(SessionSnapshot(0, a4, midi, ms.values.filter { it.midi != midi }.toList()))
            val l = c.tuning.value!!.listening
            c.startLive()
            val seconds = x.size.toDouble() / sr
            val end = shown.filter { it.first > seconds - 4.0 }.map { it.second }.sorted()
            val endCents = end.getOrNull(end.size / 2)
            val before = ms[midi]?.partialHz(l.k)?.let { TuningSession.centsOff(it, l.targetHz) }
            println("%s: %s, partial %d at %.2f Hz (%s), %.0f s; shown %d%% of hops, far off %d hops; ends at %s c, the string before the session %s c"
                .format(f.name, Notes.name(midi), l.k, l.targetHz, l.source, seconds, 100 * shown.size / (seconds * sr / 1024).toInt(), coarse,
                    endCents?.let { "%+.2f".format(it) } ?: "—", before?.let { "%+.2f".format(it) } ?: "—"))
            println("   " + timeline.chunked(10).joinToString("\n   ") { it.joinToString("  ") })
            assertTrue(endCents != null, "${f.name}: the ending unison is read")
            // the note was pulled flat and tuned back by the tuner's ear: it ends
            // near where the same string stood before the session (a few cents: the
            // wire is old, the other strings of the unison are in the reading, and
            // the tuner tuned it again, not the app)
            if (before != null) assertTrue(abs(endCents!! - before) < 5.0, "${f.name}: ends at $endCents, stood at $before")
        }
    }
}
