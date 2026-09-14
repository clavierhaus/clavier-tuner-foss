package at.clavierhaus.unisonmaster.dsp

import kotlin.math.sqrt

/**
 * Stiff-string model of one string: f_k = k * f1 * sqrt(1 + B*k^2).
 * [b] is the inharmonicity coefficient B (dimensionless).
 */
data class StringModel(val f0Hz: Double, val b: Double) {
    fun partialHz(k: Int): Double = k * f0Hz * sqrt(1.0 + b * k * k)
}

/**
 * Least-squares fit of the stiff-string model to measured partials.
 *
 * Linearization: with y = (f_k / k)^2 and x = k^2,
 *
 *     f_k = k*f1*sqrt(1 + B k^2)  =>  y = f1^2 + f1^2*B * x
 *
 * so an ordinary line fit y = a + c*x yields f1 = sqrt(a), B = c/a. Exact,
 * not an approximation — the nonlinearity is absorbed entirely by the
 * substitution.
 */
object InharmonicityFit {

    /**
     * Fits only f0, holding B at [b].
     *
     * Once any string of a unison has been measured, B is known: the strings
     * share speaking length and wire, and their B values agree within a few
     * per cent. Re-fitting it per string is then not merely redundant but
     * harmful — f0 and B are strongly correlated, so a string offering only
     * two or three measurable partials produces an underdetermined fit in
     * which B runs away and drags f0 with it. A string measured this way
     * reported B = 1.6e-3 and a pitch 33 cents adrift while its neighbours
     * sat at 2.5e-4.
     *
     * With B held, one usable partial determines f0, and every further
     * partial over-determines it. Weak strings become measurable.
     */
    fun fitWithFixedB(
        partials: List<Pair<Int, Double>>,
        b: Double,
        weights: List<Double>? = null,
    ): StringModel? {
        var sw = 0.0
        var acc = 0.0
        for ((i, p) in partials.withIndex()) {
            val (k, f) = p
            if (k <= 0 || f <= 0.0) continue
            val w = weights?.getOrElse(i) { 1.0 } ?: 1.0
            if (w <= 0.0) continue
            // f_k = k*f0*sqrt(1+b k^2)  =>  f0 = f_k / (k*sqrt(1+b k^2))
            val f0 = f / (k * sqrt(1.0 + b * k * k))
            acc += w * f0
            sw += w
        }
        if (sw <= 0.0) return null
        return StringModel(acc / sw, b)
    }

    /**
     * [partials] as (k, measured Hz), optionally with per-partial [weights].
     *
     * Weighting matters in the field: f0 and B are correlated parameters, so
     * a marginal partial drifting in and out of the level gate makes the
     * pair trade error and the reported f0 jump (observed on a Bosendorfer
     * 225, excursions of ~0.9 Hz coinciding with B swings between 8.8e-5
     * and 3.0e-4). Weighting by amplitude keeps the fit anchored on the
     * partials that are actually well measured.
     */
    fun fit(partials: List<Pair<Int, Double>>, weights: List<Double>? = null): StringModel? {
        if (weights != null) return fitWeighted(partials, weights)
        return fitWeighted(partials, List(partials.size) { 1.0 })
    }

    private fun fitWeighted(
        partials: List<Pair<Int, Double>>,
        weights: List<Double>,
    ): StringModel? {
        if (partials.isEmpty()) return null
        if (partials.size == 1) {
            val (k, f) = partials[0]
            return StringModel(f / k, 0.0)
        }
        var n = 0
        var sw = 0.0
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for ((i, p) in partials.withIndex()) {
            val (k, f) = p
            if (k <= 0 || f <= 0.0) continue
            val w = weights.getOrElse(i) { 1.0 }
            if (w <= 0.0) continue
            val x = (k * k).toDouble()
            val y = (f / k) * (f / k)
            n++; sw += w; sx += w * x; sy += w * y; sxx += w * x * x; sxy += w * x * y
        }
        if (n < 2) return null
        val denom = sw * sxx - sx * sx
        if (denom == 0.0) return null
        val c = (sw * sxy - sx * sy) / denom
        val a = (sy - c * sx) / sw
        if (a <= 0.0) return null
        // B is physically non-negative; clamp fit noise on near-ideal strings.
        // Physical bound. B on a piano string runs from ~1e-5 in the bass to
        // ~1e-2 in the top treble; 0.05 admitted fits no string produces.
        val b = (c / a).coerceIn(0.0, 0.01)
        return StringModel(sqrt(a), b)
    }
}
