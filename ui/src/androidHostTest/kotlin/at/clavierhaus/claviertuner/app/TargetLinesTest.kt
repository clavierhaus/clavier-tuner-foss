package at.clavierhaus.claviertuner.app

import at.clavierhaus.claviertuner.i18n.Strings
import at.clavierhaus.claviertuner.settings.OctaveType
import at.clavierhaus.claviertuner.settings.TunerSettings
import at.clavierhaus.claviertuner.tuning.TuningSession
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the tuning screen says the key is tuned to (targetLines, 25 Nov 2026): the engine's listening in the info line's
 * words, the partner's check on a line of its own, its figure the engine's, never worked out here.
 */
class TargetLinesTest {
    private val cfg = TunerSettings()

    private fun curve(width: Double, check: Double?) = TuningSession.Listening(
        midi = 55, k = 2, targetHz = 392.0, source = TuningSession.Source.CURVE, type = OctaveType.O6_3, refMidi = 67,
        widthCents = width, checkCents = check,
    )

    @Test fun theSourceWithItsWidthAndThePartnersCheckOnALineOfItsOwn() {
        Strings.use("en")
        assertEquals(listOf("6:3 octave to G4 on the curve, 0.3 c wide", "G4 stands 0.4 c narrow"), targetLines(curve(0.3, -0.4), 440.0, cfg))
        assertEquals(listOf("6:3 octave to G4 on the curve, beatless", "G4 stands 1.2 c wide"), targetLines(curve(0.0, 1.2), 440.0, cfg))
    }

    @Test fun noCheckWithoutAMeasuredPartner() {
        Strings.use("en")
        assertEquals(listOf("6:3 octave to G4 on the curve, 0.5 c narrow"), targetLines(curve(-0.5, null), 440.0, cfg))
    }

    @Test fun theTemperamentOctaveAndTheReference() {
        Strings.use("en")
        val temperament = TuningSession.Listening(60, 1, 261.6, TuningSession.Source.TEMPERAMENT)
        assertEquals(listOf("equal temperament on A4 440.0 Hz"), targetLines(temperament, 440.0, cfg))
        assertEquals(listOf("the reference, set when sampling"), targetLines(TuningSession.Listening(69, 1, 440.0, TuningSession.Source.REFERENCE), 440.0, cfg))
    }

    @Test fun theCheckLineKeepsItsWordsInEveryLanguage() {
        Strings.use("de")
        assertEquals("G4 steht 0,4 c eng", targetLines(curve(0.3, -0.4), 440.0, cfg)[1])
        Strings.use("fr")
        assertEquals("G4 est à 0.4 c étroite", targetLines(curve(0.3, -0.4), 440.0, cfg)[1])
        Strings.use("en")
    }
}
