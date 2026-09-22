package at.clavierhaus.unisonmaster.tuning

/**
 * Which partials the hub shows. Fundamental mode shows partial 1 only.
 * Full Spectrum shows every audible partial the tuner has not switched off.
 */
object PartialSelection {
    fun shown(fullSpectrum: Boolean, audible: Set<Int>, hidden: Set<Int>): Set<Int> =
        if (fullSpectrum) audible - hidden else setOf(1)

    /** A tap only acts in Full Spectrum mode, and only on an audible partial. */
    fun tap(k: Int, fullSpectrum: Boolean, audible: Set<Int>, hidden: Set<Int>): Set<Int> = when {
        !fullSpectrum || k !in audible -> hidden
        k in hidden -> hidden - k
        else -> hidden + k
    }
}
