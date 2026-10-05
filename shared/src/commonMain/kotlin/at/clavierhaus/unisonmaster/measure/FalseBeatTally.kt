package at.clavierhaus.unisonmaster.measure

/**
 * How many separate strikes of the note each partial has been heard beating
 * against itself in, and therefore which partials may be called false.
 *
 * A false beat is claimed only when it repeats. CLAUDE.md, of the corpus:
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
    private val strikes = HashMap<Int, MutableSet<Long>>()

    /** Partial [k] was heard beating during the strike numbered [strike]. */
    fun heard(k: Int, strike: Long) { strikes.getOrPut(k) { HashSet() }.add(strike) }

    /** The partials heard beating on at least [strikesNeeded] separate strikes, in order. */
    fun claimed(): List<Int> = strikes.filterValues { it.size >= strikesNeeded }.keys.sorted()

    /** How many strikes partial [k] has been heard beating in: the evidence behind the claim. */
    fun strikesFor(k: Int): Int = strikes[k]?.size ?: 0

    fun clear() = strikes.clear()

    companion object {
        /** The corpus rule: three strikes per string is the minimum for any claim about a false beat. */
        const val STRIKES = 3
    }
}
