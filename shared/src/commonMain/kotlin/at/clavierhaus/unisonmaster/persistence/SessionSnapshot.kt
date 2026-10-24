package at.clavierhaus.unisonmaster.persistence

import at.clavierhaus.unisonmaster.measure.FalseBeat
import at.clavierhaus.unisonmaster.measure.Partials
import at.clavierhaus.unisonmaster.tuning.MeasuredPartial
import at.clavierhaus.unisonmaster.tuning.NoteMeasurement

/** Everything needed to continue a tuning where it was left. */
data class SessionSnapshot(
    /** Wall-clock time of the save, ms: "last used". */
    val savedAtMs: Long,
    val a4Hz: Double,
    /** The note the tuner was on. */
    val currentMidi: Int,
    val measurements: List<NoteMeasurement>,
    /** True while the strings are still being sampled, before tuning. */
    val sampling: Boolean = false,
    /**
     * The inharmonicity sampled before tuning began, by key: the basis of every target for the
     * rest of the session (TuningSession.frozenBasis, since 11 Oct 2026). Null while sampling,
     * and in a file saved before then.
     */
    val basis: Map<Int, Double>? = null,
    /** The offsets an edition laid over the targets, cents by key (TuningSession.targetOffsetCents, since 24 Oct 2026). */
    val offsets: Map<Int, Double> = emptyMap(),
)

/**
 * A small, strict, versioned text format. Every line is known, every count
 * is checked, every number is range-checked; anything else is rejected.
 *
 *     clavierhaustuner-session 1
 *     saved <ms>
 *     a4 <hz>
 *     current <midi>
 *     sampling <0|1>                          (optional)
 *     basis <midi> <b>                          (the sampled inharmonicity, any number; since 11 Oct 2026)
 *     offset <midi> <cents>                     (an edition's offset of the target, any number; since 24 Oct 2026)
 *     note <midi> <f1Hz> <b> <residualCents> <timeMs> <partialCount>
 *     p <k> <cents> <levelDb> <sustainS>        (partialCount times)
 *     f <k> <rateHz> <depthDb>                  (the string's false beats, any number; since 8 Oct 2026)
 *     end
 */
object SessionCodec {
    const val HEADER = "clavierhaustuner-session"
    const val VERSION = 1

    class FormatException(message: String) : Exception(message)

    // The ranges the reader accepts. The writer applies the same ones, so a
    // session is never saved in a form the next start refuses. Until
    // 19 September the writer checked only for finite values, and a partial
    // the old tracker had placed 300 cents flat (another string's) made the
    // whole file unreadable: one bad number cost the tuner the session.
    private fun noteInRange(midi: Int, f1Hz: Double, b: Double, residualCents: Double, timeMs: Long) =
        midi in 12..108 && f1Hz.isFinite() && f1Hz in 15.0..5000.0 &&
            b.isFinite() && b in 0.0..0.1 &&
            residualCents.isFinite() && residualCents in 0.0..1000.0 && timeMs >= 0

    private fun partialInRange(k: Int, cents: Double, levelDb: Double, sustainS: Double) =
        k in 1..Partials.MAX &&
            cents.isFinite() && cents in -200.0..1200.0 &&
            levelDb.isFinite() && levelDb in -200.0..0.0 &&
            sustainS.isFinite() && sustainS in 0.0..600.0

    // a false beat the engine can claim: a rate the ear can follow (BeatMeasure's own bounds,
    // with room), a second plane no deeper than the measure's own floor of 60 dB
    private fun falseBeatInRange(k: Int, rateHz: Double, depthDb: Double) =
        k in 1..Partials.MAX &&
            rateHz.isFinite() && rateHz > 0.0 && rateHz <= 50.0 &&
            depthDb.isFinite() && depthDb in 0.0..60.0

    fun encode(s: SessionSnapshot): String = buildString {
        append("$HEADER $VERSION\n")
        append("saved ${s.savedAtMs}\n")
        append("a4 ${s.a4Hz}\n")
        append("current ${s.currentMidi}\n")
        if (s.sampling) append("sampling 1\n")
        s.basis?.toList()?.sortedBy { it.first }?.forEach { (midi, b) -> if (midi in 12..108 && b.isFinite() && b in 0.0..0.1) append("basis $midi $b\n") }
        s.offsets.toList().sortedBy { it.first }.forEach { (midi, c) -> if (midi in 12..108 && c.isFinite() && c in -100.0..100.0) append("offset $midi $c\n") }
        for (m in s.measurements) {
            if (!noteInRange(m.midi, m.f1Hz, m.b, m.residualCents, m.timeMs)) continue
            val ps = m.partials.filter { partialInRange(it.k, it.cents, it.levelDb, it.sustainS) }
            append("note ${m.midi} ${m.f1Hz} ${m.b} ${m.residualCents} ${m.timeMs} ${ps.size}\n")
            for (p in ps) append("p ${p.k} ${p.cents} ${p.levelDb} ${p.sustainS}\n")
            for (f in m.falseBeats) if (falseBeatInRange(f.k, f.rateHz, f.depthDb)) append("f ${f.k} ${f.rateHz} ${f.depthDb}\n")
        }
        append("end\n")
    }

    fun decode(text: String): SessionSnapshot {
        val lines = text.split('\n').let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        var i = 0
        fun fail(why: String): Nothing = throw FormatException(why)
        fun next(): List<String> {
            if (i >= lines.size) fail("unexpected end")
            return lines[i++].split(' ')
        }
        fun tokens(expect: String, n: Int): List<String> {
            val t = next()
            if (t.size != n + 1 || t[0] != expect) fail("expected '$expect' with $n values at line $i")
            return t.drop(1)
        }
        fun d(s: String, lo: Double, hi: Double): Double {
            val v = s.toDoubleOrNull() ?: fail("not a number: $s")
            if (!v.isFinite() || v < lo || v > hi) fail("out of range: $s")
            return v
        }
        fun int(s: String, lo: Int, hi: Int): Int {
            val v = s.toIntOrNull() ?: fail("not an integer: $s")
            if (v < lo || v > hi) fail("out of range: $s")
            return v
        }
        fun long(s: String): Long {
            val v = s.toLongOrNull() ?: fail("not an integer: $s")
            if (v < 0) fail("negative time")
            return v
        }

        val head = next()
        if (head.size != 2 || head[0] != HEADER) fail("not a session file")
        if (head[1] != VERSION.toString()) fail("unsupported version ${head[1]}")
        val saved = long(tokens("saved", 1)[0])
        val a4 = d(tokens("a4", 1)[0], 400.0, 480.0)
        val current = int(tokens("current", 1)[0], 12, 108)
        var sampling = false
        if (i < lines.size && lines[i].startsWith("sampling ")) sampling = int(tokens("sampling", 1)[0], 0, 1) == 1
        // the sampled inharmonicity, by prefix: a file from before 11 Oct 2026 has none
        var basis: LinkedHashMap<Int, Double>? = null
        while (i < lines.size && lines[i].startsWith("basis ")) {
            val t = tokens("basis", 2)
            val midi = int(t[0], 12, 108)
            val b = d(t[1], 0.0, 0.1)
            (basis ?: LinkedHashMap<Int, Double>().also { basis = it })[midi] = b
        }
        // an edition's offsets of the targets: a file from before 24 Oct 2026 has none
        val offsets = LinkedHashMap<Int, Double>()
        while (i < lines.size && lines[i].startsWith("offset ")) {
            val t = tokens("offset", 2)
            offsets[int(t[0], 12, 108)] = d(t[1], -100.0, 100.0)
        }

        // Structure — header, version, line shapes, the end marker — must be
        // right, or the file is not a session and is refused. Values are
        // another matter: a note or a partial that fails its range is dropped
        // and the rest is kept. A tuner's afternoon is not thrown away for one
        // number, and a value that is out of range is a measurement fault,
        // not evidence that the file was tampered with — the seal has already
        // said it was not.
        val notes = ArrayList<NoteMeasurement>()
        val seen = HashSet<Int>()
        while (true) {
            if (i >= lines.size) fail("missing end")
            if (lines[i] == "end") { i++; break }
            val n = tokens("note", 6)
            val count = int(n[5], 0, Partials.MAX)
            val partials = ArrayList<MeasuredPartial>()
            repeat(count) {
                val p = tokens("p", 4)
                val k = p[0].toIntOrNull() ?: return@repeat
                val cents = p[1].toDoubleOrNull() ?: return@repeat
                val levelDb = p[2].toDoubleOrNull() ?: return@repeat
                val sustainS = p[3].toDoubleOrNull() ?: return@repeat
                if (partialInRange(k, cents, levelDb, sustainS) && partials.none { it.k == k }) {
                    partials.add(MeasuredPartial(k, cents, levelDb, sustainS))
                }
            }
            // the string's false beats follow its partials, by prefix: a file from before 8 Oct
            // 2026 has none and reads as it did; one out of range is dropped like a partial
            val falseBeats = ArrayList<FalseBeat>()
            while (i < lines.size && lines[i].startsWith("f ")) {
                val f = tokens("f", 3)
                val k = f[0].toIntOrNull() ?: continue
                val rateHz = f[1].toDoubleOrNull() ?: continue
                val depthDb = f[2].toDoubleOrNull() ?: continue
                if (falseBeatInRange(k, rateHz, depthDb) && falseBeats.none { it.k == k }) falseBeats.add(FalseBeat(k, rateHz, depthDb))
            }
            val midi = n[0].toIntOrNull() ?: continue
            val f1Hz = n[1].toDoubleOrNull() ?: continue
            val b = n[2].toDoubleOrNull() ?: continue
            val residual = n[3].toDoubleOrNull() ?: continue
            val timeMs = n[4].toLongOrNull() ?: continue
            if (!noteInRange(midi, f1Hz, b, residual, timeMs)) continue
            if (!seen.add(midi)) continue
            notes.add(NoteMeasurement(midi, f1Hz, b, residual, partials, timeMs, falseBeats))
        }
        if (i != lines.size) fail("data after end")
        return SessionSnapshot(saved, a4, current, notes, sampling, basis, offsets)
    }
}

/** Authenticated encryption of the save file. The key never lives in the code. */
interface Sealer {
    fun seal(plain: ByteArray): ByteArray

    /** Returns the plain bytes, or throws if the data was changed or sealed elsewhere. */
    fun open(sealed: ByteArray): ByteArray
}

/** The one save file. Writes must be atomic: a crash never leaves half a file. */
interface SaveFile {
    fun read(): ByteArray?
    fun writeAtomic(bytes: ByteArray)

    /** Moves an unverifiable file out of the way, keeping it for inspection. */
    fun setAside()

    /** The most recent file set aside, if any. */
    fun readSetAside(): ByteArray? = null

    /** Removes every file set aside. */
    fun clearSetAside() {}

    /** Removes the file; nothing to continue afterwards. */
    fun delete()
}

/** The last tuning: saved sealed, loaded only if it verifies. */
class SessionStore(private val file: SaveFile, private val sealer: Sealer) {

    sealed class Load {
        data object None : Load()
        data class Ok(val snapshot: SessionSnapshot) : Load()
        /** The file existed but could not be verified; it has been set aside. */
        data object Rejected : Load()
    }

    fun save(s: SessionSnapshot): Boolean = try {
        file.writeAtomic(sealer.seal(SessionCodec.encode(s).encodeToByteArray()))
        true
    } catch (e: Exception) {
        false
    }

    /** "New": the saved tuning is discarded. */
    fun clear(): Boolean = try {
        file.delete()
        true
    } catch (e: Exception) {
        false
    }

    fun load(): Load {
        val bytes = try { file.read() } catch (e: Exception) { null }
        if (bytes != null) {
            return try {
                Load.Ok(SessionCodec.decode(sealer.open(bytes).decodeToString(throwOnInvalidSequence = true)))
            } catch (e: Exception) {
                try { file.setAside() } catch (_: Exception) { }
                Load.Rejected
            }
        }
        // Nothing current. A file an earlier reader set aside may read now that
        // the reader keeps what is valid — the seal is verified again, so
        // nothing unverifiable is ever loaded; only a file that passed the seal
        // and failed a value check gets its second reading.
        val aside = try { file.readSetAside() } catch (e: Exception) { null } ?: return Load.None
        return try {
            val snap = SessionCodec.decode(sealer.open(aside).decodeToString(throwOnInvalidSequence = true))
            if (save(snap)) try { file.clearSetAside() } catch (_: Exception) { }
            Load.Ok(snap)
        } catch (e: Exception) {
            Load.None
        }
    }
}
