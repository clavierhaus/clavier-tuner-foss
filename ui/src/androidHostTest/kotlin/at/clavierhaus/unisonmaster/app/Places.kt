package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.StretchPoint
import at.clavierhaus.unisonmaster.settings.TunerSettings

/**
 * [s] with six places, as Pro's six-point preset sets them (Pro pro/.../stretch/SixPoints.kt; the preset left the free app
 * on 10 Nov 2026, Peter: "The 6 point fine grained preset is a Pro feature"). The screens that draw places are foss's and
 * are tested here on this stretch.
 */
internal fun withSixPlaces(s: TunerSettings) = s.copy(
    octaveBass = OctaveType.O6_3, octaveMiddle = OctaveType.O4_2, octaveTreble = OctaveType.O4_1,
    widthBass = 0.63, widthBassFar = 3.46, widthMiddle = 0.63, widthTreble = 0.63, widthTrebleFar = 0.0,
    stretchPoints = listOf(StretchPoint(45, OctaveType.O6_3, 0.63), StretchPoint(77, OctaveType.O2_1, 0.74), StretchPoint(93, OctaveType.O4_1, 0.49)),
)
