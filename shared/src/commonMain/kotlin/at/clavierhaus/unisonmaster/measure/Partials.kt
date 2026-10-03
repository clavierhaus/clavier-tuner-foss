package at.clavierhaus.unisonmaster.measure

/**
 * A partial of the string being heard, as the screens draw it: [k], its
 * frequency, its distance in cents from k times the target's first
 * partial, and its level 0..1 relative to the loudest partial shown.
 */
/**
 * One partial of the note as its reader reads it now: [hz], [cents] from its
 * target, [level] 0..1 of the loudest, and [widthCents] — how well it is read:
 * the robust spread of its last half second of readings (see
 * [ReadingSpread]), null until enough of them exist. A loud partial reads to a
 * few hundredths of a cent; one at the room's floor scatters by cents, and the
 * width says so before anyone takes the scatter for the string (3 Oct 2026).
 */
data class LivePartial(
    val k: Int, val hz: Double, val cents: Double, val level: Double, val widthCents: Double? = null,
    /** How far the partial stands over the noise beside it, dB, as its reader last read it; null when not known. */
    val overNoiseDb: Double? = null,
)

/**
 * The spread of a partial's recent readings, as a width in cents: the median
 * absolute deviation of the last [SPAN_HOPS] shown readings, scaled to a
 * standard deviation (×1.4826). Robust — one glitch of the floor does not
 * draw a broken string's width. Fed only by shown readings, so a dead sound
 * feeds nothing; cleared at a strike, so the dead sound's spread never blends
 * into the new one; known after [MIN_READINGS].
 */
class ReadingSpread(private val span: Int = SPAN_HOPS) {
    private val ring = DoubleArray(span)
    private var filled = 0

    fun push(cents: Double) { ring[filled % span] = cents; filled++ }
    fun clear() { filled = 0 }

    /** The width, cents; null until [MIN_READINGS] readings are in. */
    val widthCents: Double?
        get() {
            val n = minOf(filled, span)
            if (n < MIN_READINGS) return null
            val v = DoubleArray(n) { ring[it] }
            v.sort()
            val med = if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2
            val d = DoubleArray(n) { kotlin.math.abs(v[it] - med) }
            d.sort()
            val mad = if (n % 2 == 1) d[n / 2] else (d[n / 2 - 1] + d[n / 2]) / 2
            return MAD_TO_SIGMA * mad
        }

    companion object {
        /** Half a second of readings at a hop of 1024 samples, 48 kHz. */
        const val SPAN_HOPS = 24
        const val MIN_READINGS = 6
        /** The median absolute deviation of a normal spread is 0.6745 of its standard deviation. */
        const val MAD_TO_SIGMA = 1.4826
    }
}

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
