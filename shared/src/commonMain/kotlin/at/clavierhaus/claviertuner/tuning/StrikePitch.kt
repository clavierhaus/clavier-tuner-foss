package at.clavierhaus.claviertuner.tuning

/**
 * One pitch per strike, held, for the keys of the two bars (OctaveBars, from C#6 up; Peter, 18 Nov 2026, 21:00: "What if we
 * don't clear it but keep it as a reference value that does not change as long as the same note is struck with the same
 * pitch. But when the pin is turned, the measured pitch changes and our measurement is fast enough to catch it"; 21:09,
 * "Yes, it's worth a try"). An experiment; Law 3 and Amendment 3 are set aside for these keys (CLAUDE.md, Law 9's note).
 *
 * Up there a string rings 0.23-0.67 s (the 225, 22 Sept), its first 0.05-0.1 s stands off by up to 9 c and its fading tail
 * wanders by 2-3 c, while the loud part between holds one pitch within 0.5 c (Pro research/top_one_pitch_20261118.py): a
 * reading of short windows shows the attack and the tail, and flutters, where the ear hears one pitch. So each strike gives
 * one value: the partial's readings while it sounds within [WITHIN_DB] of the strike's loudest reading, weighted by their
 * power, as the ear weights the loud part. The reader's own skip after the strike keeps the attack out.
 *
 * The value stays after the sound ends, marked as no longer sounding, until the next strike replaces it or the key changes
 * ([reset]). A strike read for less than [ROUGH_S] is marked rough: on the Bechstein (18 Nov 2026, Pro
 * research/top_strike_by_strike_20261118.py) strikes read 0.13-0.18 s scattered 2.7 c, strikes read 0.28 s and more
 * repeated within 0.3 c; 0.25 s lies between. No threshold decides whether the value moves: every strike's value is shown,
 * and the change from the strike before is shown beside it.
 */
class StrikePitch {
    /** The strike's pitch [hz]; [readS] of sound behind it; [sounding] while it is still being read; [previousHz] the strike before. */
    data class Value(val hz: Double, val readS: Double, val sounding: Boolean, val previousHz: Double?) {
        val rough: Boolean get() = readS < ROUGH_S
    }

    private var sumW = 0.0
    private var sumWHz = 0.0
    private var hops = 0
    private var peakDb = -400.0
    private var done = false
    private var current: Value? = null
    private var before: Double? = null

    /** The value to show: this strike's while it sounds, the last strike's after. */
    val value: Value? get() = current

    /** Whether the strike now under way has given a reading of its own (the value shown may still be the one before). */
    val readThisStrike: Boolean get() = hops > 0

    private var standing: Value? = null
    private var standingBefore: Double? = null

    /** A new strike: the value standing becomes the one before, and this strike is read afresh. */
    fun strike() {
        standing = current; standingBefore = before
        current?.let { before = it.hz }
        sumW = 0.0; sumWHz = 0.0; hops = 0; peakDb = -400.0; done = false
        current = current?.copy(sounding = false)
    }

    /**
     * The strike was another key's (named only after its first readings, the identifier's 8 hops): what it gave is dropped
     * and the value from before it stands again, as it stood.
     */
    fun cancel() {
        current = standing?.copy(sounding = false); before = standingBefore
        sumW = 0.0; sumWHz = 0.0; hops = 0; peakDb = -400.0; done = true
    }

    /**
     * One hop: [hz] the partial as read this hop at [levelDbfs], or null when nothing is read; [hopS] the hop's length.
     * Readings are taken until the level falls [WITHIN_DB] below the strike's loudest or the reading stops; after that the
     * strike's value stands.
     */
    fun push(hz: Double?, levelDbfs: Double, hopS: Double) {
        if (done) return
        if (hz == null || (hops > 0 && levelDbfs < peakDb - WITHIN_DB)) {
            if (hops > 0) { done = true; current = current?.copy(sounding = false) }
            return
        }
        if (levelDbfs > peakDb) peakDb = levelDbfs
        val w = kotlin.math.exp(levelDbfs / 10.0 * kotlin.math.ln(10.0))
        sumW += w; sumWHz += w * hz; hops++
        current = Value(sumWHz / sumW, hops * hopS, sounding = true, previousHz = before)
    }

    /** Another key: nothing of this one is kept. */
    fun reset() {
        sumW = 0.0; sumWHz = 0.0; hops = 0; peakDb = -400.0; done = false; current = null; before = null
        standing = null; standingBefore = null
    }

    companion object {
        /** How far below the strike's loudest reading the readings still count, dB (the research's window, 18 Nov 2026). */
        const val WITHIN_DB = 20.0

        /** Read for less than this, a strike's value is rough, s (between 0.18 s, 2.7 c, and 0.28 s, 0.3 c; 18 Nov 2026). */
        const val ROUGH_S = 0.25
    }
}
