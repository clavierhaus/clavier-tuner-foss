package at.clavierhaus.unisonmaster.picture

/**
 * Where each band's segments stand, carried forward frame by frame.
 *
 * A band's phase says nothing on its own. Only the speed at which it changes is
 * the measurement, because that speed is the beat — the partial as read against
 * the partial as asked. So the phase is **accumulated**: every frame adds
 * rate × dt to where the pattern already stood, and the phase is never computed
 * from an absolute clock.
 *
 * Why it must be accumulated, measured rather than argued: until 6 October 2026
 * the view drew `phase = rate × t` with `t` the frame clock's own time, which is
 * hours old. That ties the whole elapsed session to every whisper in the live
 * reading — with the clock an hour old a rate moving by a thousandth of a hertz
 * moves the pattern three and a half periods between two frames. Mistral's bench
 * read of the 93 frames of `docs/screens/p8pro-20261006/` measured segment phases
 * of 248, 332, 186, 0, 370, 349, 66, 180 px in the window where the target stood
 * at the string's own measured 439.2 Hz and the pattern should not have moved at
 * all. The arrival is read by motion's absence, and motion's absence could not be
 * drawn.
 *
 * There is no guard here and no clamp. A long gap simply advances the phase by
 * whatever the rate asks and wraps; the phase has no meaning to protect.
 */
class BandPhase {
    private var lastMs: Long? = null
    private val phase = mutableMapOf<Int, Double>()

    /**
     * Advances every band to [nowMs] and returns where each stands, in [0,1) of
     * one segment. [rates] is the pattern rate of each band in periods per
     * second; a band absent from it is forgotten, so a band that leaves the set
     * does not come back holding a stale position.
     */
    fun advance(nowMs: Long, rates: Map<Int, Double>): Map<Int, Double> {
        val dt = lastMs?.let { (nowMs - it) / 1000.0 }?.coerceAtLeast(0.0) ?: 0.0
        lastMs = nowMs
        phase.keys.retainAll(rates.keys)
        for ((k, rate) in rates) {
            val was = phase[k] ?: 0.0
            phase[k] = wrap(was + rate * dt)
        }
        return rates.keys.associateWith { phase[it] ?: 0.0 }
    }

    private fun wrap(x: Double): Double {
        val m = x % 1.0
        return if (m < 0.0) m + 1.0 else m
    }
}
