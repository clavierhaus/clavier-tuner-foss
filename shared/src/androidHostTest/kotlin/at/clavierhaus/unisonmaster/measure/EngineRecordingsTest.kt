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
    fun theSampleSetGivesACurveWhoseTargetsAreWhereThePianoWasTuned() {
        val dir = day() ?: run { println("EngineRecordingsTest: no recordings of 22 September here"); return }
        val (a4, ms) = measured(dir)
        val files = partA(dir)
        println("A4 of the piano as it stood: %.1f Hz; %d of %d strings measured".format(a4, ms.size, files.size))
        val missing = files.keys - ms.keys
        if (missing.isNotEmpty()) println("not measured: ${missing.sorted().map { Notes.name(it) }}")
        assertTrue(ms.size >= files.size - 2, "measured ${ms.size} of ${files.size}")

        // a sampling as a tuner might make it: every fourth semitone from the lowest key to C7
        // and the lowest plain string, then the curve
        val s = TuningSession(a4, settings)
        ms[69]?.let { s.record(it) }
        for (m in ((settings.lowestKeyMidi..96 step 4) + settings.lowestUnwoundMidi).filter { it != 69 }) ms[m]?.let { s.record(it) }
        assertTrue(s.samplingReady)
        s.finishSampling()
        val c = s.curve
        assertTrue(c.ready)
        println("sampled ${c.count} strings: ${c.sampled.map { Notes.name(it) }}")

        // 1. the curve's B against every string not sampled
        val errs = ms.values.filter { it.midi !in c.sampled && it.b > 0 && it.partials.size >= 3 }.map { m ->
            val pred = c.b(m.midi)!!
            Triple(m.midi, m.b, pred)
        }
        val relErr = errs.map { (_, b, p) -> abs(kotlin.math.ln(p / b)) }.sorted()
        println("B predicted for %d unsampled strings: median |ln(pred/meas)| %.2f, p90 %.2f".format(errs.size, relErr[relErr.size / 2], relErr[relErr.size * 9 / 10]))
        for ((m, b, p) in errs.filter { (_, b, p) -> abs(kotlin.math.ln(p / b)) > 0.4 }) println("   %-4s measured %.2e, curve %.2e".format(Notes.name(m), b, p))

        // 2. the computed targets against where the tuner left every string
        println("note  target f1  stood     cents   (+ = the string stands sharp of the computed target)")
        val byRegion = HashMap<String, MutableList<Double>>()
        for (midi in ms.keys.sorted()) {
            val stood = ms[midi]!!.f1Hz
            val target = s.curveTargetF1(midi, c)
            val cents = TuningSession.centsOff(stood, target)
            val region = when {
                midi in settings.temperamentLowMidi..TunerSettings.TEMPERAMENT_HIGH -> "temperament"
                settings.isWound(midi) -> "wound"
                midi <= settings.bassBoundaryMidi -> "bass"
                midi > TunerSettings.TEMPERAMENT_HIGH -> "treble"
                else -> "middle"
            }
            byRegion.getOrPut(region) { ArrayList() } += cents
            println("%-4s %9.2f %9.2f %+7.2f   %s".format(Notes.name(midi), target, stood, cents, region))
        }
        for ((region, v) in byRegion) {
            val sorted = v.sorted()
            println("%-11s n=%2d  median %+5.2f c   p10..p90 %+5.1f..%+5.1f".format(region, v.size, sorted[v.size / 2], sorted[v.size / 10], sorted[v.size * 9 / 10]))
        }
        // beatless octaves of the set types (width 0): the middle and the treble were
        // tuned within a few cents of them; the bass was tuned wider — the tuner's
        // width, a setting, printed here and not asserted
        fun median(r: String) = byRegion[r]!!.sorted().let { it[it.size / 2] }
        assertTrue(abs(median("temperament")) < 2.0, "temperament ${median("temperament")}")
        assertTrue(abs(median("middle")) < 3.0, "middle ${median("middle")}")
        assertTrue(abs(median("treble")) < 4.0, "treble ${median("treble")}")
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
            c.restore(SessionSnapshot(0, a4, midi, ms.values.filter { it.midi != midi }.toList(), sampling = false))
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

    /** Part B: every key as it stood, unmuted; the last take of each. */
    private fun partB(dir: File): Map<Int, File> {
        val rows = File(dir, "Boesendorfer_manifest.csv").readLines().drop(1).map { it.split(",") }
        val out = LinkedHashMap<Int, File>()
        for (r in rows.filter { it[3].startsWith("B") }.sortedBy { it[0] }) {
            val midi = Notes.midi(r[1].substringBefore("-")) ?: continue
            out[midi] = File(dir, r[2])
        }
        return out
    }

    @Test
    fun theScreenFollowsAWalkAcrossTheCompassKeyByKey() {
        val dir = day() ?: run { println("EngineRecordingsTest: no recordings of 22 September here"); return }
        val (a4, ms) = measured(dir)
        val b = partB(dir)
        // the unmuted keys one after another, 2.5 s each from a little before the strike,
        // walking down from C6 to F0 as a tuner walks, and up again through the treble
        val walk = (84 downTo 17) + (85..96)
        val pieces = walk.mapNotNull { m -> b[m]?.let { f ->
            val (sr, x) = readWav(f)
            var on = 0
            while (on + 1024 < x.size) { var q = 0.0; for (i in on until on + 1024) q += x[i].toDouble() * x[i]; if (q / 1024 > 1e-5) break; on += 1024 }
            val from = maxOf(0, on - sr / 5)
            m to x.copyOfRange(from, minOf(x.size, from + (2.5 * sr).toInt()))
        } }
        val ends = ArrayList<Int>()
        var total = 0
        for ((_, p) in pieces) { total += p.size; ends += total }
        val all = FloatArray(total); var at = 0
        for ((_, p) in pieces) { p.copyInto(all, at); at += p.size }
        lateinit var c: TuningController
        val onScreen = ArrayList<Int>()
        var next = 0
        val player = Player(all) { hop ->
            val sample = (hop + 1) * 1024
            if (next < ends.size && sample >= ends[next] - 1024) { onScreen += c.tuning.value!!.midi; next++ }
        }
        c = TuningController(player)
        c.applySettings(settings)
        c.restore(SessionSnapshot(0, a4, walk.first(), ms.values.toList(), sampling = false))
        c.startLive()
        val wrong = pieces.zip(onScreen).filter { (p, s) -> p.first != s }.map { (p, s) -> "${Notes.name(p.first)}→${Notes.name(s)}" }
        println("followed ${pieces.size - wrong.size} of ${pieces.size} keys; not followed: $wrong")
        assertTrue(wrong.size <= pieces.size / 10, "followed ${pieces.size - wrong.size} of ${pieces.size}: $wrong")
    }
}
