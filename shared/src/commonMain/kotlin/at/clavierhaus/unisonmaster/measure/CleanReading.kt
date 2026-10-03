package at.clavierhaus.unisonmaster.measure

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The string read on its clean partials together.
 *
 * One string, several partials, one pitch: every partial says where the
 * string stands, and on a string with a false beat one of them says it
 * wrongly, wobbling at the beat's rate. This reads partials 1..[count]
 * each by its own [PhaseReader] against its own target — the string's own
 * measured partial table when it has one, the model otherwise — keeps each
 * partial's readings over the strike, and combines the ones that are
 * *clean*: present (the reader's gate), steady (spread over the strike
 * under [OWN_CENTS], the mark of a partial that beats on its own), and in
 * agreement with the partial being tuned (within [AGREE_CENTS] of its
 * deviation — the target is defined on that partial, and a partial the
 * model places wrongly must not pull the reading; when that partial beats
 * on its own, the median of the steady ones stands in for it). The spread
 * is judged only from readings in which the partial stands
 * [JUDGE_OVER_NOISE_DB] over the noise beside it. A partial decaying onto
 * the room's floor scatters by the floor's phase, not the string's: on the
 * Samick's clean F3 (nine single-string takes of 30 Sept 2026, read 3 Oct)
 * the largest two-second spread of any partial was 0.22–0.30 c while it
 * stood 25 dB or more over the noise beside it, 0.27–0.52 c at 20 dB, and
 * 0.32–0.88 c at 15 dB — three partials of one take were called false for
 * it. A partial read only at less than that is present, and is shown, but
 * is neither counted nor accused; its verdict, when it had one, expires
 * with the readings that made it. The
 * combined deviation is their level-weighted mean, the loud partials
 * counting most, as the ear does. Four clean partials read a string to
 * better than a tenth of a cent; a single one is still a reading; none is
 * no reading, and the caller falls back to the partial being tuned.
 *
 * Only a string whose own partial table is known is combined: against the
 * model, every partial carries the model's error (the fundamental of an
 * unmeasured string sits a tenth of a cent off for a B a third wrong), and
 * a mean of errors is not a better reading. An unmeasured string is read
 * on the partial being tuned alone — and, when that partial beats on its
 * own, on the steadiest other partial instead, which is the case that
 * matters on a false string.
 *
 * TuneLab's "combined" multi-partial mode averages the partials weighted by
 * strength; the difference here is the exclusion — a false partial is not
 * averaged in, it is named. Docs: ENGINE.md, 28 September.
 */
class CleanReading(
    private val sampleRate: Int,
    val count: Int = PARTIALS,
    /** The readers' memory (TunerSettings.readingMemoryS); a change applies to readers made from then on. */
    var memoryS: Double = PhaseReader.DEFAULT_MEMORY_S,
    /** Hops after which a partial's last reading, and any verdict on it, expire (TuningController.holdHops). */
    private val holdHops: Long = 23L,
) {
    /** One partial as read over the strike. */
    data class Partial(
        val k: Int,
        val present: Boolean,
        /** Mean deviation from the partial's target over the strike, cents; null until enough readings. */
        val cents: Double?,
        /** Spread of the readings over the strike, cents; null until enough readings. */
        val spreadCents: Double?,
        val levelDbfs: Double,
        val clean: Boolean,
        /** Present and steady, but not where the others put the string. */
        val disagrees: Boolean,
    )

    data class Result(
        /** The string's deviation from target, cents, from the clean partials; null when none is clean. */
        val cents: Double?,
        val partials: List<Partial>,
    ) {
        val used: List<Int> get() = partials.filter { it.clean }.map { it.k }
        /** Partials present but beating on their own. */
        val falseBeating: List<Int> get() = partials.filter { it.present && !it.clean && !it.disagrees && it.spreadCents != null && it.spreadCents >= OWN_CLEAR_CENTS }.map { it.k }
    }

    private val readers = arrayOfNulls<PhaseReader>(count + 1)
    private val targets = DoubleArray(count + 1)
    private val window = Array(count + 1) { DoubleArray(WINDOW_HOPS) }
    /** For each reading in [window]: whether the partial stood [JUDGE_OVER_NOISE_DB] over the noise beside it, and so counts towards the spread. */
    private val judged = Array(count + 1) { BooleanArray(WINDOW_HOPS) }
    private val filled = IntArray(count + 1)
    private val last = arrayOfNulls<PhaseReader.Reading>(count + 1)
    private val lastAt = LongArray(count + 1)
    private var hops = 0L
    // a partial called false stays so until its spread has fallen well under the line: no flicker at the threshold
    private val falseNow = BooleanArray(count + 1)

    /**
     * Sets the partials' targets; a partial whose target is absent (0) is not read.
     * Readings are kept unless [reset]: a target that moves within the reader's band —
     * the strike's own series replacing the model's placing, 1.5 s into the strike —
     * leaves the reader where it is and re-expresses what it has read against the new
     * target, which is the same measurement said against another reference, not a
     * second measurement; only a target that leaves the band moves the reader, and
     * with it its memory. The readings the controller sees are against the current
     * targets whichever way.
     */
    fun target(hz: Map<Int, Double>, reset: Boolean) {
        for (k in 1..count) {
            val t = hz[k] ?: 0.0
            if (t <= 0.0 || t > sampleRate * 0.45) { readers[k] = null; targets[k] = 0.0; filled[k] = 0; last[k] = null; falseNow[k] = false; continue }
            val r = readers[k]
            if (r == null) readers[k] = PhaseReader(sampleRate, t, memoryS = memoryS)
            else if (abs(cents(t, r.targetHz)) > PhaseReader.DEFAULT_BAND_CENTS / 2) r.retarget(t)
            val old = targets[k]
            targets[k] = t
            if (reset) { filled[k] = 0; last[k] = null; falseNow[k] = false }
            else if (old > 0.0 && old != t && filled[k] > 0) {
                // what was read against the old target, said against the new one
                val shift = cents(old, t)
                val w = window[k]; for (i in 0 until minOf(filled[k], WINDOW_HOPS)) w[i] += shift
                last[k]?.let { last[k] = it.copy(cents = it.cents + shift) }
            }
        }
    }

    private fun cents(hz: Double, ref: Double): Double = 1200.0 * kotlin.math.ln(hz / ref) / kotlin.math.ln(2.0)

    /** A new strike: the readings start again. */
    fun strike() { filled.fill(0); last.fill(null); falseNow.fill(false); lastAt.fill(0L) }

    /** One hop of input; the result as of this hop, anchored on partial [anchorK], the one being tuned. */
    fun push(chunk: FloatArray, anchorK: Int = 1, combine: Boolean = true): Result {
        hops++
        for (k in 1..count) {
            val r = readers[k] ?: continue
            val read = r.push(chunk) ?: continue
            // the reader's cents are against its own reference; the partial's are against its target
            val rd = if (r.targetHz == targets[k]) read else read.copy(cents = read.cents + cents(r.targetHz, targets[k]))
            last[k] = rd
            if (rd.shown) {
                val at = filled[k] % WINDOW_HOPS
                window[k][at] = rd.cents; judged[k][at] = rd.levelDbfs - rd.noiseDbfs >= JUDGE_OVER_NOISE_DB
                filled[k]++; lastAt[k] = hops
            }
        }
        // a partial not read within the hold time is gone, and so is the call made on it:
        // a "false" verdict must not outlive the reading behind it (30 Sept)
        for (k in 1..count) {
            if (lastAt[k] != 0L && hops - lastAt[k] > holdHops) { last[k] = null; filled[k] = 0; falseNow[k] = false; lastAt[k] = 0L }
        }
        return result(anchorK, combine)
    }

    private fun result(anchorK: Int, combine: Boolean): Result {
        val parts = ArrayList<Partial>()
        for (k in 1..count) {
            val rd = last[k]
            if (readers[k] == null || rd == null) continue
            val n = minOf(filled[k], WINDOW_HOPS)
            var mean: Double? = null; var spread: Double? = null
            if (n >= MIN_READINGS) {
                val w = window[k]; val j = judged[k]
                // the deviation from the most recent readings only — the reader is still settling
                // in the first ones; the spread over the whole window (a slow beat needs it), from
                // the readings that stood well over the noise, and only when there are enough of them
                val recent = minOf(n, MEAN_HOPS)
                var r = 0.0
                for (i in 0 until recent) r += w[(filled[k] - 1 - i) % WINDOW_HOPS]
                mean = r / recent
                var nj = 0; var m = 0.0
                for (i in 0 until n) if (j[i]) { m += w[i]; nj++ }
                if (nj >= MIN_READINGS) {
                    m /= nj
                    var ss = 0.0; for (i in 0 until n) if (j[i]) ss += (w[i] - m) * (w[i] - m)
                    spread = sqrt(ss / nj)
                    // hysteresis: false above OWN_CENTS, clean again only below OWN_CLEAR_CENTS
                    if (spread >= OWN_CENTS) falseNow[k] = true else if (spread < OWN_CLEAR_CENTS) falseNow[k] = false
                } else falseNow[k] = false
            } else falseNow[k] = false
            parts += Partial(k, rd.shown, mean, spread, rd.levelDbfs, clean = false, disagrees = false)
        }
        // the steady partials; where the string is, the partial being tuned says — or, when it
        // beats on its own, the median of the steady ones
        val steady = parts.filter { it.present && it.cents != null && it.spreadCents != null && !falseNow[it.k] }
        if (steady.isEmpty()) return Result(null, parts)
        if (!combine) {
            // the partial being tuned alone; when it beats on its own, the steadiest other partial
            val one = steady.firstOrNull { it.k == anchorK } ?: steady.minByOrNull { it.spreadCents!! }!!
            return Result(one.cents, parts.map { it.copy(clean = it.k == one.k) })
        }
        val anchor = steady.firstOrNull { it.k == anchorK }?.cents ?: run {
            val sorted = steady.map { it.cents!! }.sorted()
            if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
        val judged = parts.map { p ->
            val isSteady = p in steady
            val agrees = isSteady && abs(p.cents!! - anchor) <= AGREE_CENTS
            p.copy(clean = agrees, disagrees = isSteady && !agrees)
        }
        val clean = judged.filter { it.clean }
        if (clean.isEmpty()) return Result(null, judged)
        // level-weighted: amplitude, relative to the loudest clean partial
        val top = clean.maxOf { it.levelDbfs }
        var sw = 0.0; var s = 0.0
        for (p in clean) { val w = 10.0.pow((p.levelDbfs - top) / 20.0); sw += w; s += w * p.cents!! }
        return Result(s / sw, judged)
    }

    companion object {
        const val PARTIALS = 8
        /** A partial that spreads more than this over the strike beats on its own (UnisonSync's OWN_CENTS). */
        const val OWN_CENTS = 0.5
        /** … and is clean again only once its spread has fallen below this: no flicker at the line. */
        const val OWN_CLEAR_CENTS = 0.3
        /** A steady partial farther than this from the one being tuned is not where the string is: the model placed it wrongly (an unmeasured string: partial k sits ~865·k²·ΔB cents off for a B error ΔB). */
        const val AGREE_CENTS = 0.3
        /** Readings kept per partial: 2 s at hop 1024. */
        const val WINDOW_HOPS = 94
        /** A deviation and a spread are known from this many readings on (about 0.4 s). */
        const val MIN_READINGS = 18
        /**
         * A reading counts towards a partial's spread only while the partial stands this far
         * over the noise beside it (PhaseReader's own measure; its gate is 10 dB). Nearer the
         * floor the scatter is the floor's, not the string's. Measured on the nine F3 takes of
         * 30 Sept 2026 (research: the largest two-second spread of any partial, by depth):
         * ≥ 30 dB 0.14–0.30 c, ≥ 25 dB 0.22–0.30 c, ≥ 22 dB 0.24–0.52 c, ≥ 20 dB 0.26–0.52 c,
         * ≥ 15 dB 0.32–0.88 c. The verdict line is [OWN_CENTS]; 25 dB is where no clean take
         * came within 0.2 c of it.
         */
        const val JUDGE_OVER_NOISE_DB = 25.0
        /** The deviation is the mean of the most recent readings: half a second, as the display's memory. */
        const val MEAN_HOPS = 12
    }
}
