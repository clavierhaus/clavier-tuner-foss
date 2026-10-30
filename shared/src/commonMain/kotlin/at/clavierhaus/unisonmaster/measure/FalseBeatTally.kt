package at.clavierhaus.unisonmaster.measure

/**
 * A false beat of a string, as sampling measured it with the string alone: partial [k]
 * beats against itself at [rateHz], its second plane [depthDb] below the first. The
 * string's own fact, kept with its f1 and B (a note's `NoteMeasurement.falseBeats`):
 * what the string does on every strike, whichever unison it stands in, and so what the
 * tuning screen marks without having to hear it again -- where it could not tell the
 * string's beat from the unison's (FALSE-BEATS §15).
 */
data class FalseBeat(val k: Int, val rateHz: Double, val depthDb: Double)

/**
 * How many separate strikes of the note each partial has been heard beating
 * against itself in, and therefore which partials may be called false.
 *
 * A false beat is claimed only when it repeats. CLAUDE.md, of the recordings:
 * "Three strikes per string is the minimum for any claim about a false beat:
 * a rate that does not repeat is not a rate." The engine's judgement of a
 * single strike is evidence towards that claim and is not the claim.
 *
 * Earned at the Samick on 5 Oct 2026, 15:47. On one clean G#4 held for eleven
 * seconds a partial was marked false on 7 of 22 frames, while 4 further frames
 * that had bells at all marked none — the same string called false and clean
 * by turns, and the violet the loudest thing on the screen.
 *
 * A strike counts once however many hops it is heard over, which is what the
 * strike's own number is for. The tally is emptied with the note: it is the
 * evidence for a verdict, never a reading, and no number of it reaches the
 * screen (Amendment 3).
 */
class FalseBeatTally(private val strikesNeeded: Int = STRIKES) {
    /** One strike's evidence on a partial: the rate its best-fitting window measured, that fit, and the depth it gave. */
    private class Heard(val rateHz: Double, val fit: Double, val depthDb: Double)

    /** Per partial, per strike: what the strike's best-fitting window measured (rate 0 when the caller gave none). */
    private val strikes = HashMap<Int, HashMap<Long, Heard>>()

    /**
     * Partial [k] was heard beating during the strike numbered [strike], at [rateHz] by a window
     * that fitted with [fit] and put the second plane [depthDb] under the first. A strike heard
     * again keeps the rate of its best-fitting window: one strike is one strike, and its rate of
     * record is the one its own evidence trusts most.
     *
     * Two other choices were tried on the recordings on 8 Oct 2026 (evening) and were worse where it
     * matters, which is the claim: the median over the strike's windows claimed the Bösendorfer
     * A3's slow fundamental (a 0.23 Hz wow, FALSE-BEATS §6) and its partial 7; the window in
     * which the partial stood loudest claimed both as well. The best-fitting window claims what
     * ESPRIT hears on that string and on the Samick's A4 and nothing else -- at the cost of the
     * rate, which it takes from the tail on a partial whose first plane dies fast (A3's k5:
     * 0.69 Hz kept, 1.13 by ESPRIT, 0.93-1.01 in the loud windows). The rate's limits are the
     * window's and are written in the findings; the claim is the tally's business.
     */
    fun heard(k: Int, strike: Long, rateHz: Double = 0.0, fit: Double = 0.0, depthDb: Double = 0.0) {
        val by = strikes.getOrPut(k) { HashMap() }
        val was = by[strike]
        if (was == null || fit > was.fit) by[strike] = Heard(rateHz, fit, depthDb)
    }

    /**
     * The partials heard beating on at least [strikesNeeded] separate strikes **at one rate**, in
     * order. The findings' rule (FALSE-BEATS §3, SAMICK §8): a false beat is real only when the
     * fitted rate repeats across strikes. Since 8 Oct 2026 the tally applies it: the strikes'
     * rates must lie within [RATE_AGREE] of their median -- the Samick's E6 reads 0.63 / 0.63 /
     * 0.68 Hz, the Bösendorfer A3's k3 and k4 0.63 ×3 and 0.80 ×3, while the A3's slow k1 gave
     * early windows at 0.74, 0.59 and nothing, which three strikes at one rate they are not.
     * Rates recorded as 0 (a caller without one) are not held to agree.
     */
    fun claimed(): List<Int> = strikes.filter { (_, byStrike) -> agrees(byStrike) }.keys.sorted()

    /**
     * The claims as the string's facts: for each partial in [claimed], the median rate and the
     * median depth over the strikes that made the claim. A median, not a mean: three strikes is
     * the minimum, and one window that fitted a harmonic of the rate must not pull the figure.
     * The rate is as fine as a two-second window reads it, which on the recordings is within a
     * quarter of the ESPRIT figure and no finer (SAMICK findings, 8 Oct 2026).
     */
    fun claims(): List<FalseBeat> = claimed().map { k ->
        val by = strikes.getValue(k).values
        FalseBeat(k, median(by.map { it.rateHz }.filter { it > 0.0 }), median(by.map { it.depthDb }))
    }

    private fun agrees(byStrike: Map<Long, Heard>): Boolean {
        if (byStrike.size < strikesNeeded) return false
        val rates = byStrike.values.map { it.rateHz }.filter { it > 0.0 }.sorted()
        if (rates.size < strikesNeeded) return rates.isEmpty()
        val median = rates[rates.size / 2]
        return rates.count { it >= median / RATE_AGREE && it <= median * RATE_AGREE } >= strikesNeeded
    }

    private fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** How many strikes partial [k] has been heard beating in: the evidence behind the claim. */
    fun strikesFor(k: Int): Int = strikes[k]?.size ?: 0

    /** The rates partial [k] was heard at, one per strike, for the record. */
    fun ratesFor(k: Int): List<Double> = strikes[k]?.values?.map { it.rateHz }?.sorted() ?: emptyList()

    fun clear() = strikes.clear()

    companion object {
        /** The recordings rule: three strikes per string is the minimum for any claim about a false beat. */
        const val STRIKES = 3
        /** Strikes agree on a rate when each lies within this factor of their median: two and a half steps of BeatMeasure's 8 % grid. */
        const val RATE_AGREE = 1.25
    }
}
