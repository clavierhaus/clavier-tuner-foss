package at.clavierhaus.unisonmaster.model

import at.clavierhaus.unisonmaster.dsp.StringModel

/** The three strings of a unison, as physical objects the operator works on. */
enum class StringSlot { LEFT, CENTER, RIGHT }

/**
 * A string measured and frozen: its fitted stiff-string model together with
 * the partial levels observed at the moment of capture.
 *
 * The levels are retained deliberately. A captured model can predict a
 * partial's frequency at any k, but says nothing about whether that partial
 * was actually audible when measured; without the levels the display would
 * render a partial that was buried in noise exactly as confidently as one
 * that was ringing.
 */
data class CapturedString(
    val slot: StringSlot,
    val model: StringModel,
    val levelsDb: Map<Int, Double>,
    /**
     * Partials that sustained long enough to be tunable *on this string*.
     * Retained per string because usability is a property of the comparison,
     * not of one string: a partial is worth offering only if it survives on
     * every string of the unison.
     */
    val tunable: Set<Int> = emptySet(),
)
