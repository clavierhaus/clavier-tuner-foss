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
 * There is no guard on a gap. A long gap simply advances the phase by whatever
 * the rate asks and wraps; the phase has no meaning to protect.
 *
 * The drawn speed has one limit, [MAX_DRAWN_RATE], since 9 November 2026. A band
 * redrawn sixty times a second cannot show a beat near sixty hertz: one whole
 * period per frame looks like no motion at all, and between thirty and sixty it
 * looks backwards. At the top of the piano one cent is 1.5 to 2.5 Hz, so a string
 * a few tens of cents off lies there: the 225's E7 stood 60.6 Hz below its target
 * in the recording session_20261109-190529, where a band is at rest. How it came to
 * stand there the recording cannot show. The limit is on the picture only; the
 * reading it comes from is not touched.
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
            phase[k] = wrap(was + rate.coerceIn(-MAX_DRAWN_RATE, MAX_DRAWN_RATE) * dt)
        }
        return rates.keys.associateWith { phase[it] ?: 0.0 }
    }

    companion object {
        /**
         * The fastest a band is drawn, in periods per second: a sixth of a period per frame at sixty frames a second,
         * six frames to the period, where the way it moves can still be seen. A string further off runs at this speed
         * the way it lies, and the band slows only as the string comes within it.
         */
        const val MAX_DRAWN_RATE = 10.0
    }

    private fun wrap(x: Double): Double {
        val m = x % 1.0
        return if (m < 0.0) m + 1.0 else m
    }
}
