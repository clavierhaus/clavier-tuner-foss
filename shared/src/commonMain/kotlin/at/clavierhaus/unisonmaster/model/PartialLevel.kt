package at.clavierhaus.unisonmaster.model

/**
 * One measured partial. [index] is 1-based (1 = fundamental).
 * [frequencyHz] is the *target* frequency the Goertzel was pointed at —
 * in the current harmonic model index * f0; later, inharmonicity-corrected.
 * [levelDb] is dBFS relative to a full-scale sine.
 * [measuredHz] is the phase-refined actual frequency of the partial, when
 * the partial was strong enough to refine and the result fell inside the
 * estimator's capture range; null otherwise.
 */
data class PartialLevel(
    val index: Int,
    val frequencyHz: Double,
    val levelDb: Double,
    val measuredHz: Double? = null,
    /**
     * True when the partial's recent statistics indicate more than one
     * frequency component (i.e. multiple sounding strings): periodic
     * envelope modulation beyond clean-decay residuals and/or
     * frequency-estimate scatter beyond single-string phase noise.
     */
    val multiString: Boolean = false,
)
