package at.clavierhaus.unisonmaster

/**
 * clavierhaus CI: black, white, orange.
 *
 * Colors live in shared code as plain ARGB values so Android (Compose) and
 * later iOS (SwiftUI) render the identical palette.
 *
 * ORANGE is the official CI value, sampled from the master logo file
 * (logo-inverted-uhd.jpg): #FF9630.
 */
object Brand {
    const val ORANGE: Long = 0xFFFF9630
    const val BLACK: Long = 0xFF000000
    const val NEAR_BLACK: Long = 0xFF0D0D0D // panels, one step off pure black
    const val WHITE: Long = 0xFFFFFFFF
    const val WHITE_MUTED: Long = 0xB3FFFFFF // 70 % white for secondary text

    /**
     * Functional alert colour, deliberately OUTSIDE the CI palette. Reserved
     * for the reference-capture control: unison comparison is meaningless
     * without a captured reference, and a control that blends into the
     * orange UI is one an operator forgets. It is not a brand colour and
     * must not be used decoratively.
     */
    /**
     * The three strings of a unison, as hues. Hue carries identity (which
     * string), never magnitude — magnitude and reliability are separate
     * channels. Centre is the CI orange; left and right are chosen for
     * separability against it and against each other on a dark ground.
     */
    const val STRING_LEFT: Long = 0xFF32D74B   // green
    const val STRING_CENTRE: Long = 0xFFFF9630 // CI orange
    const val STRING_RIGHT: Long = 0xFF3B9EFF  // blue

    /** Confirmation (the "Done" action). Functional, not decorative. */
    const val GO_GREEN: Long = 0xFF32D74B

    /** Calculated targets, and nothing else. Functional, not decorative. */
    const val TARGET_BLUE: Long = 0xFF8FD0FF

    /**
     * The progress keyboard. Green marks a note that is done, which is the
     * same functional meaning GO_GREEN carries elsewhere: a match. A black
     * key that is done takes a deeper green, so that it stays readable as a
     * black key against its lit neighbours instead of merging into them.
     */
    const val KEY_WHITE: Long = 0xFFE6E6E6
    const val KEY_BLACK: Long = 0xFF141414
    const val KEY_DONE_WHITE: Long = GO_GREEN
    const val KEY_DONE_BLACK: Long = 0xFF1B7A2C

    const val ALERT_RED: Long = 0xFFE02020
    const val ALERT_RED_DIM: Long = 0xFF7A1414
}
