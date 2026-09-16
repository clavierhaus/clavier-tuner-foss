package at.clavierhaus.unisonmaster.persistence

import at.clavierhaus.unisonmaster.tuning.LiveReference
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
)

/**
 * A small, strict, versioned text format. Every line is known, every count
 * is checked, every number is range-checked; anything else is rejected.
 *
 *     clavierhaustuner-session 1
 *     saved <ms>
 *     a4 <hz>
 *     current <midi>
 *     note <midi> <f1Hz> <b> <residualCents> <timeMs> <partialCount>
 *     p <k> <cents> <levelDb> <sustainS>        (partialCount times)
 *     end
 */
object SessionCodec {
    const val HEADER = "clavierhaustuner-session"
    const val VERSION = 1

    class FormatException(message: String) : Exception(message)

    fun encode(s: SessionSnapshot): String = buildString {
        append("$HEADER $VERSION\n")
        append("saved ${s.savedAtMs}\n")
        append("a4 ${s.a4Hz}\n")
        append("current ${s.currentMidi}\n")
        for (m in s.measurements) {
            val ps = m.partials.filter { it.cents.isFinite() && it.levelDb.isFinite() && it.sustainS.isFinite() }
            append("note ${m.midi} ${m.f1Hz} ${m.b} ${m.residualCents} ${m.timeMs} ${ps.size}\n")
            for (p in ps) append("p ${p.k} ${p.cents} ${p.levelDb} ${p.sustainS}\n")
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
        val current = int(tokens("current", 1)[0], 21, 108)

        val notes = ArrayList<NoteMeasurement>()
        val seen = HashSet<Int>()
        while (true) {
            if (i >= lines.size) fail("missing end")
            if (lines[i] == "end") { i++; break }
            val n = tokens("note", 6)
            val midi = int(n[0], 21, 108)
            if (!seen.add(midi)) fail("note $midi twice")
            val count = int(n[5], 0, LiveReference.PARTIALS)
            val partials = (0 until count).map {
                val p = tokens("p", 4)
                MeasuredPartial(
                    k = int(p[0], 1, LiveReference.PARTIALS),
                    cents = d(p[1], -200.0, 1200.0),
                    levelDb = d(p[2], -200.0, 0.0),
                    sustainS = d(p[3], 0.0, 600.0),
                )
            }
            if (partials.map { it.k }.toSet().size != partials.size) fail("partial repeated in note $midi")
            notes.add(
                NoteMeasurement(
                    midi = midi,
                    f1Hz = d(n[1], 20.0, 5000.0),
                    b = d(n[2], 0.0, 0.1),
                    residualCents = d(n[3], 0.0, 1000.0),
                    partials = partials,
                    timeMs = long(n[4]),
                ),
            )
        }
        if (i != lines.size) fail("data after end")
        return SessionSnapshot(saved, a4, current, notes)
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
        val bytes = try { file.read() } catch (e: Exception) { null } ?: return Load.None
        return try {
            Load.Ok(SessionCodec.decode(sealer.open(bytes).decodeToString(throwOnInvalidSequence = true)))
        } catch (e: Exception) {
            try { file.setAside() } catch (_: Exception) { }
            Load.Rejected
        }
    }
}
