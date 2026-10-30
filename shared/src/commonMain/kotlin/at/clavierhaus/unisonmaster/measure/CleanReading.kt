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
 * *clean*: present (the reader's gate), not beating on its own (the band
 * power's swing measured by [BeatMeasure] -- rate 0.5-8 Hz, second plane
 * within 10 dB, the ear's criterion; until 8 Oct 2026 this was a spread of
 * the frequency reading over 0.5 c, which is neither), and in
 * agreement with the partial being tuned (within [AGREE_CENTS] of its
 * deviation — the target is defined on that partial, and a partial the
 * model places wrongly must not pull the reading; when that partial beats
 * on its own, the median of the steady ones stands in for it). The spread
 * is judged only from readings in which the partial stands
 * [JUDGE_OVER_NOISE_DB] over the noise beside it. A partial decaying onto
 * the room's floor scatters by the floor's phase, not the string's: on the
 * Samick's clean F3 (nine single-string recordings of 30 Sept 2026, read 3 Oct)
 * the largest two-second spread of any partial was 0.22–0.30 c while it
 * stood 25 dB or more over the noise beside it, 0.27–0.52 c at 20 dB, and
 * 0.32–0.88 c at 15 dB — three partials of one recording were called false for
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
 * Whose beat it is depends on who is sounding, and the caller says (push's
 * `alone`). With the string alone -- sampling, the mutes in -- a beat is the
 * string's and takes the partial out. With the unison sounding it may be the
 * partner's, and the partial is read all the same; what sampling found of
 * the string (its `NoteMeasurement.falseBeats`) comes in as the partials
 * `known` false and those stay out. The beat is measured and reported on
 * every partial either way.
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
        /**
         * Beating on its own: the band power swings at 0.5-8 Hz with the second plane within 10 dB
         * ([BeatMeasure], the ear's criterion from SAMICK findings §8), called from a window of
         * sustained readings and held until the index has fallen under [CLEAR_INDEX]. Until 8 Oct
         * 2026 this was a spread of the frequency reading over 0.5 c, which coloured clean strings
         * and missed audible beats (§20, §21).
         */
        val beats: Boolean = false,
        /** The beat last measured on this partial -- rate, index, fit -- whatever the verdict; null until a window has been read. */
        val beat: BeatMeasure.Beat? = null,
    )

    data class Result(
        /** The string's deviation from target, cents, from the clean partials; null when none is clean. */
        val cents: Double?,
        val partials: List<Partial>,
    ) {
        val used: List<Int> get() = partials.filter { it.clean }.map { it.k }
        /** Partials present but beating on their own, by the one hysteresis, however the string was read. */
        val falseBeating: List<Int> get() = partials.filter { it.present && it.beats }.map { it.k }
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
    // a partial called false stays so until its beat's index has fallen under the clear line: no flicker at the threshold
    private val falseNow = BooleanArray(count + 1)
    // The band power per shown reading, from a second after the strike (the attack's transient is
    // not a plane), kept for BeatMeasure: its time in seconds and its value, a ring per partial.
    private val powerT = Array(count + 1) { DoubleArray(WINDOW_HOPS) }
    private val power = Array(count + 1) { DoubleArray(WINDOW_HOPS) }
    private val powerFilled = IntArray(count + 1)
    /** The hop of the last strike: the beat is measured from [SUSTAIN_S] after it. */
    private var strikeHop = 0L
    /** Seconds per hop, from the chunks pushed. */
    private var hopS = 1024.0 / sampleRate
    /** The last beat measured on each partial, for the record (Pro's false-beat map wants the rate and the depth). */
    private val beat = arrayOfNulls<BeatMeasure.Beat>(count + 1)

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
            if (t <= 0.0 || t > sampleRate * 0.45) { readers[k] = null; targets[k] = 0.0; filled[k] = 0; last[k] = null; falseNow[k] = false; powerFilled[k] = 0; beat[k] = null; continue }
            val r = readers[k]
            if (r == null) readers[k] = PhaseReader(sampleRate, t, memoryS = memoryS)
            else if (abs(cents(t, r.targetHz)) > PhaseReader.DEFAULT_BAND_CENTS / 2) r.retarget(t)
            val old = targets[k]
            targets[k] = t
            if (reset) { filled[k] = 0; last[k] = null; falseNow[k] = false; powerFilled[k] = 0; beat[k] = null }
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
    fun strike() { filled.fill(0); last.fill(null); falseNow.fill(false); lastAt.fill(0L); powerFilled.fill(0); beat.fill(null); strikeHop = hops }

    /**
     * One hop of input; the result as of this hop, anchored on partial [anchorK], the one being
     * tuned.
     *
     * [alone] says whether the string sounds by itself. While it does (sampling, the mutes in),
     * a beat a partial carries is the string's own and takes the partial out of the combination.
     * With the unison sounding it is not known whose the beat is -- two strings a fraction of a
     * hertz apart beat at one partial exactly as two planes of one string do (FALSE-BEATS §15) --
     * and a partial beating is then still read: taking it out read the C7 unison of 22 Sept to
     * silence at 13.8 s, its one listened partial excluded for beating against its partner, and
     * the tuner tuning a unison would be shown nothing at the moment it matters most. What is
     * known of the string from sampling comes in as [known], the partials its facts say are
     * false, and those stay out either way. The beat is measured and reported on every partial
     * in both cases (Rule 0): only what is done with it depends on who is sounding.
     */
    fun push(chunk: FloatArray, anchorK: Int = 1, combine: Boolean = true, alone: Boolean = true, known: Set<Int> = emptySet()): Result {
        hops++
        hopS = chunk.size.toDouble() / sampleRate
        val sustained = (hops - strikeHop) * hopS >= SUSTAIN_S
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
                if (sustained && rd.levelDbfs - rd.noiseDbfs >= BEAT_OVER_NOISE_DB) {
                    val pa = powerFilled[k] % WINDOW_HOPS
                    powerT[k][pa] = hops * hopS; power[k][pa] = rd.rawPower; powerFilled[k]++
                }
            }
        }
        // The beat, measured every quarter second on each partial with a window's worth of sustained
        // readings: the verdict goes in at the ear's 10 dB and comes out two decibels further down,
        // so a plane at the line does not flicker the colour. A partial with no beat measured yet,
        // or whose window has gone with the reading, is not called.
        if (hops % BEAT_EVERY_HOPS == 0L) {
            for (k in 1..count) {
                val n = minOf(powerFilled[k], WINDOW_HOPS)
                if (n < BeatMeasure.MIN_READINGS) continue
                val t = DoubleArray(n); val p = DoubleArray(n)
                val start = powerFilled[k] - n
                for (i in 0 until n) { val src = (start + i) % WINDOW_HOPS; t[i] = powerT[k][src]; p[i] = power[k][src] }
                val b = BeatMeasure.measure(t, p) ?: continue
                beat[k] = b
                falseNow[k] = if (falseNow[k]) b.rateHz >= BeatMeasure.MIN_RATE_HZ && b.rateHz <= BeatMeasure.MAX_RATE_HZ && b.index >= CLEAR_INDEX && b.fit >= BeatMeasure.MIN_FIT
                else b.audible
            }
        }
        // a partial not read within the hold time is gone, and so is the call made on it:
        // a "false" verdict must not outlive the reading behind it (30 Sept)
        for (k in 1..count) {
            if (lastAt[k] != 0L && hops - lastAt[k] > holdHops) { last[k] = null; filled[k] = 0; falseNow[k] = false; lastAt[k] = 0L; powerFilled[k] = 0; beat[k] = null }
        }
        return result(anchorK, combine, alone, known)
    }

    private fun result(anchorK: Int, combine: Boolean, alone: Boolean, known: Set<Int>): Result {
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
                }
            }
            // the spread is how well the partial is read, for the record; the verdict is the beat's
            parts += Partial(k, rd.shown, mean, spread, rd.levelDbfs, clean = false, disagrees = false, beats = falseNow[k], beat = beat[k])
        }
        // the steady partials; where the string is, the partial being tuned says — or, when it
        // beats on its own, the median of the steady ones
        // Steady enough to be averaged in: read within STEADY_CENTS over the strike and not beating
        // on its own. The two are different questions -- a partial dying through its reader's filter
        // scatters by whole cents and must not anchor the string (the test of the stand-in), while a
        // false partial can read within 0.35 c and still beat audibly (E6 on the Samick's left A4) --
        // and until 8 Oct 2026 one spread line answered both.
        val steady = parts.filter { it.present && it.cents != null && it.spreadCents != null && it.spreadCents < STEADY_CENTS && !((alone && falseNow[it.k]) || it.k in known) }
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
        /**
         * A partial is averaged into the string's reading only while its readings over the strike
         * spread less than this, cents: the clean F3's partials spread 0.14-0.30 c while 25 dB over
         * the noise beside them (30 Sept 2026, nine recordings), and a partial dying through its reader's
         * filter scatters by whole cents. This is steadiness for the combination, not a verdict on
         * the string: the false-beat verdict is BeatMeasure's.
         */
        const val STEADY_CENTS = 0.5
        /**
         * A reading goes into the beat's window only while the partial stands this far over the
         * noise beside it. The reader's own gate is 10 dB and the spread's judge 25; the beat sits
         * between, measured on the recordings (8 Oct 2026): the Samick's E6 stands 22-24 dB over the
         * noise through its one window and must be kept, and the strays -- a clean F3's partial
         * at 7 s into its decay, C7's listened partial at the end of a session -- are read in the
         * 10-15 dB tail, where the power is as much the room's as the string's.
         */
        const val BEAT_OVER_NOISE_DB = 15.0
        /** The beat is measured from readings this long after the strike: the attack's transient is not a plane (SAMICK findings §3, §8). */
        const val SUSTAIN_S = 1.0
        /** … and every this many hops, a quarter second: a verdict four times a second is as often as the eye reads it. */
        const val BEAT_EVERY_HOPS = 12L
        /** A partial called false is clean again once its second plane has fallen 2 dB further than the ear's line: the index of two planes 12 dB apart, 2ρ/(1+ρ²) at ρ = 10^(−0.6). */
        val CLEAR_INDEX: Double = run { val rho = 10.0.pow(-0.6); 2 * rho / (1 + rho * rho) }
        /** A steady partial farther than this from the one being tuned is not where the string is: the model placed it wrongly (an unmeasured string: partial k sits ~865·k²·ΔB cents off for a B error ΔB). */
        const val AGREE_CENTS = 0.3
        /** Readings kept per partial: 2 s at hop 1024. */
        const val WINDOW_HOPS = 94
        /** A deviation and a spread are known from this many readings on (about 0.4 s). */
        const val MIN_READINGS = 18
        /**
         * A reading counts towards a partial's spread only while the partial stands this far
         * over the noise beside it (PhaseReader's own measure; its gate is 10 dB). Nearer the
         * floor the scatter is the floor's, not the string's. Measured on the nine F3 recordings of
         * 30 Sept 2026 (research: the largest two-second spread of any partial, by depth):
         * ≥ 30 dB 0.14–0.30 c, ≥ 25 dB 0.22–0.30 c, ≥ 22 dB 0.24–0.52 c, ≥ 20 dB 0.26–0.52 c,
         * ≥ 15 dB 0.32–0.88 c. 25 dB is where no clean take came within 0.2 c of the old
         * 0.5 c line; the spread is now the record of how well a partial is read, not a verdict.
         */
        const val JUDGE_OVER_NOISE_DB = 25.0
        /** The deviation is the mean of the most recent readings: half a second, as the display's memory. */
        const val MEAN_HOPS = 12
    }
}
