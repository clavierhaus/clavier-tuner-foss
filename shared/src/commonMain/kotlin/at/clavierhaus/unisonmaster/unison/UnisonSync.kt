package at.clavierhaus.unisonmaster.unison

import at.clavierhaus.unisonmaster.measure.PartialFinder
import at.clavierhaus.unisonmaster.measure.PhaseReader
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import at.clavierhaus.unisonmaster.tuning.KeyIdentifier
import at.clavierhaus.unisonmaster.tuning.TuningSession
import kotlin.math.log10
import kotlin.math.ln
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How many partials of a unison are in sync (docs/UNISON-SYNC.md).
 *
 * Every partial 1..[partials] of the note struck is read by its own
 * [PhaseReader]. Where the strings of a unison differ at a partial, the
 * reading of that partial swings at the beat; where they agree, it stands
 * still. The **wobble** of a partial is the spread (standard deviation, in
 * cents) of its readings over the current strike; a partial is **steady**
 * below [STEADY_CENTS]. On the tuning recordings of 22 September the best
 * state of A3 had seven of eight partials steady and the eighth was the
 * centre string's own beat; the best of E3 five of eight. Both were passed
 * by: later strikes had fewer — which is why the best state is kept.
 *
 * A string can beat by itself (a false beat: an old or uneven wire). No pin
 * position removes that, so chasing it spoils the rest. [checkString]
 * listens to the next strike as one string alone; every partial that
 * wobbles on it is marked the string's **own beat** for this note and left
 * out of the count.
 *
 * Nothing here is a constant of one piano: the note is named by the
 * [KeyIdentifier], the partials are placed where the [PartialFinder] hears
 * them, and the threshold is the one at which the tuner's ear, on those
 * recordings, called a partial still.
 */
class UnisonSync(
    private val sampleRate: Int,
    private val a4Hz: () -> Double,
    val partials: Int = PARTIALS,
    /**
     * The note detector chooses from here up. A0 by default even on a piano
     * that goes lower: the keys below it are single strings, no unison to
     * tune, and offered to the detector they took A3 for F0 (its tenth
     * partial) on the single-string take of 22 September.
     */
    private val lowMidi: Int = 21,
) {
    /** One partial: [wobbleCents] null until enough of the strike has been read. */
    data class Partial(
        val k: Int,
        val heard: Boolean,
        val wobbleCents: Double?,
        val steady: Boolean,
        /** Marked by a single-string check: the string's own beat, not counted. */
        val ownBeat: Boolean,
    )

    data class State(
        val midi: Int? = null,
        val partials: List<Partial> = emptyList(),
        /** Steady partials, and how many are counted (heard, not an own beat). */
        val inSync: Int = 0,
        val counted: Int = 0,
        /** The best of this note so far: partials in sync, of how many, and when (s since listening began). */
        val best: Int = 0,
        val bestOf: Int = 0,
        val bestAtS: Double? = null,
        /** The next strike is taken as one string alone. */
        val checking: Boolean = false,
        /** Single strings checked on this note. */
        val stringsChecked: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val readers = arrayOfNulls<PhaseReader>(partials + 1)
    private val window = Array(partials + 1) { DoubleArray(WINDOW_HOPS) }
    private val filled = IntArray(partials + 1)
    private val ring = FloatArray(IDENTIFY_SAMPLES)
    private var ringFilled = 0
    private var ringPos = 0
    private var samples = 0L
    private val recentDb = DoubleArray(5) { -200.0 }
    private var hop = 0L
    private var strikeHop = -1L
    private var placed = false
    private var midi: Int? = null
    private var candidate: Int? = null
    private var lastKey: Int? = null
    private val detector by lazy { KeyIdentifier(sampleRate, IDENTIFY_SAMPLES, lowMidi, TuningSession.MIDI_C8) }
    private val own = HashMap<Int, MutableSet<Int>>()          // note -> partials with their own beat
    private val checked = HashMap<Int, Int>()
    @kotlin.concurrent.Volatile private var checkArmed = false
    private var checkingStrike = false
    private var judged = true
    private var best = 0; private var bestOf = 0; private var bestAtS: Double? = null

    /** The next strike is one string alone: its wobbling partials are its own beats. */
    fun checkString() { checkArmed = true; _state.value = _state.value.copy(checking = true) }

    /** Forgets the single-string checks of the note on screen. */
    fun forgetChecks() {
        midi?.let { own.remove(it); checked.remove(it) }
        publish()
    }

    /** One buffer of input, from the audio thread. */
    fun push(chunk: FloatArray) {
        for (x in chunk) { ring[ringPos] = x; if (++ringPos == ring.size) ringPos = 0 }
        ringFilled = minOf(ring.size, ringFilled + chunk.size)
        samples += chunk.size
        hop++
        var ss = 0.0
        for (x in chunk) ss += x.toDouble() * x
        val db = 10 * log10(maxOf(ss / chunk.size, 1e-24))
        val loudest = recentDb.max()
        recentDb[(hop % recentDb.size).toInt()] = db
        // a strike, not the same strike heard again in its next hop
        val sinceStrike = if (strikeHop < 0) Long.MAX_VALUE else (hop - strikeHop) * chunk.size
        if (hop > recentDb.size && db - loudest >= PhaseReader.ONSET_DB && db > SILENCE_DB && sinceStrike >= REPEAT_S * sampleRate) strike()

        // the note and its partials are placed once the strike fills the detector's window
        // (the first window still holds the hammer's knock: the key is named on the
        // windows after it, and twice alike)
        if (!placed && strikeHop >= 0 && ringFilled == ring.size && hop % 2 == 0L &&
            (hop - strikeHop) * chunk.size >= IDENTIFY_SAMPLES + PLACE_AFTER_S * sampleRate) {
            val snap = FloatArray(ring.size)
            ring.copyInto(snap, 0, ringPos, ring.size); ring.copyInto(snap, ring.size - ringPos, 0, ringPos)
            val key = detector.detectIn(snap, a4Hz())
            if (key != null && key == lastKey) { placed = true; place(key, snap) }
            lastKey = key
            if ((hop - strikeHop) * chunk.size >= PLACE_UNTIL_S * sampleRate) placed = true
        }
        var any = false
        for (k in 1..partials) {
            val r = readers[k] ?: continue
            val rd = r.push(chunk) ?: continue
            // the attack's glide is not the unison: read from a little into the strike
            // (a single string is judged on the early decay only: late, near the noise,
            // every partial wanders and would be marked as the string's own beat)
            val into = (hop - strikeHop) * chunk.size
            if (rd.shown && into >= FROM_S * sampleRate && (!checkingStrike || into <= CHECK_UNTIL_S * sampleRate)) {
                val w = window[k]
                w[filled[k] % WINDOW_HOPS] = rd.cents
                filled[k]++
                any = true
            }
        }
        val judgeAt = if (checkingStrike) CHECK_UNTIL_S else JUDGE_S
        if (strikeHop >= 0 && !judged && (hop - strikeHop) * chunk.size >= judgeAt * sampleRate) judge()
        if (any || hop % 8 == 0L) publish()
    }

    private fun strike() {
        if (strikeHop >= 0 && !judged) judge()
        judged = false
        strikeHop = hop
        placed = false
        lastKey = null
        filled.fill(0)
        if (checkArmed) { checkArmed = false; checkingStrike = true }
    }

    private fun place(heard: Int, snap: FloatArray) {
        // another note only when two strikes in a row say so: one strike named
        // wrongly (a string pulled far off, a knock) must not cost the note its best
        if (heard != midi) {
            val first = midi == null
            if (!first && heard != candidate) { candidate = heard; return }
            midi = heard
            best = 0; bestOf = 0; bestAtS = null
        }
        candidate = null
        val key = heard
        val finder = PartialFinder(snap, sampleRate)
        val f1 = TuningSession.targetF1(key, a4Hz())
        for (k in 1..partials) {
            val predicted = k * f1 * Inharmonicity.ratio(k, TYPICAL_B)
            if (predicted > sampleRate * 0.4) { readers[k] = null; continue }
            val hz = finder.near(predicted, PLACE_CENTS)?.hz ?: predicted
            val r = readers[k]
            if (r == null) readers[k] = PhaseReader(sampleRate, hz)
            else if (kotlin.math.abs(1200 * ln(hz / r.targetHz) / ln(2.0)) > RETARGET_CENTS) r.retarget(hz)
        }
    }

    private fun wobble(k: Int, minReadings: Int = MIN_READINGS): Double? {
        val n = minOf(filled[k], WINDOW_HOPS)
        if (n < minReadings) return null
        val w = window[k]
        var mean = 0.0
        for (i in 0 until n) mean += w[i]
        mean /= n
        var ss = 0.0
        for (i in 0 until n) ss += (w[i] - mean) * (w[i] - mean)
        return sqrt(ss / n)
    }

    /**
     * The strike is over (the next one came, or [JUDGE_S] passed): a
     * single-string check marks its own beats; otherwise the strike's count
     * may be the note's best. Judged on the whole strike, never on its first
     * readings, where a few steady ones would pass for sync.
     */
    private fun judge() {
        judged = true
        if (!checkingStrike) {
            val st = snapshot(JUDGE_READINGS)
            if (st.counted >= 2 && st.inSync > best) { best = st.inSync; bestOf = st.counted; bestAtS = samples.toDouble() / sampleRate }
            publish()
            return
        }
        // a check strike whose note could not be named stays armed for the next
        val m = midi ?: run { checkArmed = true; checkingStrike = false; publish(); return }
        checkingStrike = false
        val set = own.getOrPut(m) { HashSet() }
        for (k in 1..partials) {
            val w = wobble(k) ?: continue
            if (w >= OWN_CENTS) set += k
        }
        checked[m] = (checked[m] ?: 0) + 1
        publish()
    }

    private fun publish() { _state.value = snapshot() }

    private fun snapshot(minReadings: Int = MIN_READINGS): State {
        val m = midi
        val ownSet = m?.let { own[it] } ?: emptySet<Int>()
        val list = (1..partials).map { k ->
            val w = wobble(k, minReadings)
            Partial(k, heard = w != null, wobbleCents = w, steady = w != null && w < STEADY_CENTS, ownBeat = k in ownSet)
        }
        val counted = list.filter { it.heard && !it.ownBeat }
        val inSync = counted.count { it.steady }
        return State(m, list, inSync, counted.size, best, bestOf, bestAtS, checkArmed || checkingStrike, m?.let { checked[it] } ?: 0)
    }

    companion object {
        const val PARTIALS = 8
        /** Steady: the partial's reading spreads less than this over the strike (cents). */
        const val STEADY_CENTS = 0.4
        /**
         * A single string's partial is its own beat above this: clear of the
         * few tenths of a cent every string wanders as it decays (E3's centre
         * string, 0.1–0.35 c), below what a false beat shows (A3's, 0.55–0.65 c).
         */
        const val OWN_CENTS = 0.5
        /** The spread is taken over at most this many readings (6 s at hop 1024) ... */
        const val WINDOW_HOPS = 280
        /** ... and not before this many. */
        const val MIN_READINGS = 20
        /** Readings count from this far into a strike: the attack's glide is over. */
        const val FROM_S = 0.8
        /** A single-string check reads its strike up to here, and is judged then. */
        const val CHECK_UNTIL_S = 3.5
        /** A rise within this of a strike is the same strike. */
        const val REPEAT_S = 0.3
        /** A strike is judged — for the best, or as a single-string check — after this long at most. */
        const val JUDGE_S = 6.0
        /** A partial counts in a strike's judgement with at least this many readings (1.3 s): a slow beat needs time to show. */
        const val JUDGE_READINGS = 60
        /** The key is named from this long after the detector's window is full of the strike, until [PLACE_UNTIL_S]. */
        const val PLACE_AFTER_S = 0.05
        const val PLACE_UNTIL_S = 1.5
        const val PLACE_CENTS = 50.0
        const val RETARGET_CENTS = 8.0
        const val TYPICAL_B = 4e-4
        const val SILENCE_DB = -70.0
        private const val IDENTIFY_SAMPLES = 16384
    }
}
