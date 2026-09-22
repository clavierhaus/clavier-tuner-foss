package at.clavierhaus.unisonmaster.measure

/**
 * A partial of the string being heard, as the screens draw it: [k], its
 * frequency, its distance in cents from k times the target's first
 * partial, and its level 0..1 relative to the loudest partial shown.
 */
data class LivePartial(val k: Int, val hz: Double, val cents: Double, val level: Double)

object Partials {
    /** The highest partial the app reads or keeps. */
    const val MAX = 12

    /**
     * A plain-wire partial is sharp of k·f1 by an amount that grows with k²,
     * and never flat by more than measurement noise. A partial outside that
     * is another string's, or nothing.
     */
    fun plausibleCents(k: Int, cents: Double): Boolean = cents >= -25.0 && cents <= 20.0 + 1.6 * k * k
}
