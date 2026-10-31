package at.clavierhaus.unisonmaster.measure

/**
 * Where the strobe's other bands stand: for each partial k, the ratio between it and the partial being tuned, as
 * the two have been read together since the strike (31 Oct 2026, Peter: "Measure truthfully as fast as possible,
 * with an adjustable wobble equalizer"). A band k is placed at the tuned partial's target times that ratio, so it
 * moves with the tuned band exactly as the string moves both, and stands off it only by what the string does to
 * partial k alone -- its wobble, its false beat. Until that day the bands were placed by the model for the first
 * 1.5 s of a strike and by the string's series after (findings §34).
 *
 * The equalizer is [windowHops]: how far back the ratio is averaged. Short, and a band follows the tuned band
 * closely, its wobble evened out; [SINCE_STRIKE], and the ratio is the whole strike's, every wobble shown against
 * it. A ratio is the strike's own and is never kept beyond it (Law 2): [clear] at every strike and every note.
 */
class BandPlaces(var windowHops: Long = SINCE_STRIKE) {
    private val read = HashMap<Int, ArrayDeque<Pair<Long, Double>>>()

    /** Partial k read at [hz] at [hop] together with the tuned partial at [tunedHz]. */
    fun push(hop: Long, k: Int, hz: Double, tunedHz: Double) {
        if (hz <= 0.0 || tunedHz <= 0.0) return
        read.getOrPut(k) { ArrayDeque() }.addLast(hop to hz / tunedHz)
    }

    /** Partial k's ratio to the tuned partial, averaged over the window ending at [hop]; null until both were read together. */
    fun ratio(k: Int, hop: Long): Double? {
        val q = read[k] ?: return null
        if (windowHops != SINCE_STRIKE) while (q.size > 1 && hop - q.first().first >= windowHops) q.removeFirst()
        if (q.isEmpty()) return null
        var sum = 0.0
        for ((_, r) in q) sum += r
        return sum / q.size
    }

    fun clear() = read.clear()

    companion object {
        /** The equalizer at its widest: the ratio of the whole strike so far. */
        const val SINCE_STRIKE = 0L
    }
}
