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
 * on its own, the median of the steady ones stands in for it). The
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
        val falseBeating: List<Int> get() = partials.filter { it.present && it.spreadCents != null && it.spreadCents >= OWN_CENTS }.map { it.k }
    }

    private val readers = arrayOfNulls<PhaseReader>(count + 1)
    private val targets = DoubleArray(count + 1)
    private val window = Array(count + 1) { DoubleArray(WINDOW_HOPS) }
    private val filled = IntArray(count + 1)
    private val last = arrayOfNulls<PhaseReader.Reading>(count + 1)

    /** Sets the partials' targets; a partial whose target is absent (0) is not read. Readings are kept unless [reset]. */
    fun target(hz: Map<Int, Double>, reset: Boolean) {
        for (k in 1..count) {
            val t = hz[k] ?: 0.0
            if (t <= 0.0 || t > sampleRate * 0.45) { readers[k] = null; targets[k] = 0.0; filled[k] = 0; last[k] = null; continue }
            val r = readers[k]
            if (r == null) readers[k] = PhaseReader(sampleRate, t)
            else if (abs(1200.0 * kotlin.math.ln(t / r.targetHz) / kotlin.math.ln(2.0)) > 0.01) r.retarget(t)
            targets[k] = t
            if (reset) { filled[k] = 0; last[k] = null }
        }
    }

    /** A new strike: the readings start again. */
    fun strike() { filled.fill(0); last.fill(null) }

    /** One hop of input; the result as of this hop, anchored on partial [anchorK], the one being tuned. */
    fun push(chunk: FloatArray, anchorK: Int = 1, combine: Boolean = true): Result {
        for (k in 1..count) {
            val r = readers[k] ?: continue
            val rd = r.push(chunk) ?: continue
            last[k] = rd
            if (rd.shown) { window[k][filled[k] % WINDOW_HOPS] = rd.cents; filled[k]++ }
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
                val w = window[k]
                // the spread over the whole window (a slow beat needs it); the deviation from the
                // most recent readings only — the reader is still settling in the first ones
                var m = 0.0; for (i in 0 until n) m += w[i]; m /= n
                var ss = 0.0; for (i in 0 until n) ss += (w[i] - m) * (w[i] - m)
                val recent = minOf(n, MEAN_HOPS)
                var r = 0.0
                for (j in 0 until recent) r += w[(filled[k] - 1 - j) % WINDOW_HOPS]
                mean = r / recent; spread = sqrt(ss / n)
            }
            parts += Partial(k, rd.shown, mean, spread, rd.levelDbfs, clean = false, disagrees = false)
        }
        // the steady partials; where the string is, the partial being tuned says — or, when it
        // beats on its own, the median of the steady ones
        val steady = parts.filter { it.present && it.cents != null && it.spreadCents != null && it.spreadCents < OWN_CENTS }
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
        /** A steady partial farther than this from the one being tuned is not where the string is: the model placed it wrongly (an unmeasured string: partial k sits ~865·k²·ΔB cents off for a B error ΔB). */
        const val AGREE_CENTS = 0.3
        /** Readings kept per partial: 2 s at hop 1024. */
        const val WINDOW_HOPS = 94
        /** A deviation and a spread are known from this many readings on (about 0.4 s). */
        const val MIN_READINGS = 18
        /** The deviation is the mean of the most recent readings: half a second, as the display's memory. */
        const val MEAN_HOPS = 12
    }
}
