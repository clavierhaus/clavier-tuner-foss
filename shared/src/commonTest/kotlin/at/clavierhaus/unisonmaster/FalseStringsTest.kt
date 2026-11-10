package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.measure.FalseBeat
import at.clavierhaus.unisonmaster.persistence.SessionCodec
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.StrobeMode
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.CheckedString
import at.clavierhaus.unisonmaster.tuning.FalseStrings
import at.clavierhaus.unisonmaster.tuning.StringPlace
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.tuning.measuredString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private object Silent : AudioSource {
    override val sampleRateHz = 48_000
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) = Unit
    override fun stop() = Unit
}

/**
 * Single strings checked for false beats, and the partials the tuner takes out because of them (Peter, 10 Nov 2026):
 * kept with the tuning, undone by Discard, and on the tuning screen an excluded partial is not read and has no band.
 */
class FalseStringsTest {
    private val a4 = 440.0
    private val keys = listOf(33, 40, 45, 52, 57, 64, 76, 81, 88)
    private val samples = listOf(measuredString(69, a4, 4e-4)) + keys.map { measuredString(it, TuningSession.targetF1(it, a4), 1e-4 * (1 + (it - 33) / 20.0)) }
    private val c5a = CheckedString(72, null, 10L, listOf(FalseBeat(3, 0.66, 5.0)))
    private val c5l = CheckedString(72, StringPlace.L, 11L, listOf(FalseBeat(2, 0.59, 6.8), FalseBeat(3, 0.66, 5.2)), setOf(1, 2, 3, 4))

    private fun tuning(strobe: StrobeMode, strings: FalseStrings = FalseStrings()): TuningController {
        val c = TuningController(Silent) { 1000L }
        c.applySettings(TunerSettings(strobeMode = strobe))
        c.restore(SessionSnapshot(0, a4, 69, samples, sampling = false, tuned = setOf(69), strings = strings))
        c.selectNote(72)
        return c
    }

    @Test
    fun aStringCheckedAgainReplacesItsEarlierCheckAndAnotherStringStandsBesideIt() {
        val f = FalseStrings().with(c5a).with(c5l).with(c5a.copy(atMs = 12L, beats = emptyList()))
        assertEquals(2, f.of(72).size)
        assertEquals(emptyList(), f.of(72).first { it.place == null }.beats, "the unnamed string's newer check")
        assertEquals(listOf(72), f.keys)
        val named = f.named(72, 12L, StringPlace.L)
        assertEquals(listOf(StringPlace.L), named.of(72).map { it.place }, "named L, the earlier L gives way")
    }

    @Test
    fun excludingIsAPartialOfAKeyAndDiscardTakesTheChecksAndTheExclusions() {
        val f = FalseStrings().with(c5l).exclude(72, 3, out = true).exclude(72, 2, out = true).exclude(72, 2, out = false)
        assertEquals(setOf(3), f.excludedAt(72))
        assertEquals(emptySet(), f.excludedAt(71))
        val gone = f.discard(72)
        assertTrue(gone.isEmpty)
    }

    @Test
    fun theChecksAndExclusionsAreSavedWithTheTuningAndAFileWithoutThemReadsAsBefore() {
        val f = FalseStrings().with(c5a).with(c5l).exclude(72, 3, true)
        val snap = SessionSnapshot(5, a4, 69, samples, strings = f)
        val back = SessionCodec.decode(SessionCodec.encode(snap))
        assertEquals(f.excluded, back.strings.excluded)
        assertEquals(f.strings.toSet(), back.strings.strings.toSet())
        val old = SessionCodec.encode(snap.copy(strings = FalseStrings()))
        assertFalse("string " in old || "exclude " in old, "nothing written when nothing was checked")
        assertTrue(SessionCodec.decode(old).strings.isEmpty)
    }

    @Test
    fun anExcludedPartialIsNotReadAndHasNoBandOnEveryStrobe() {
        for (mode in StrobeMode.entries) {
            val c = tuning(mode)
            val t = assertNotNull(c.tuning.value)
            val other = c.targets.value.map { it.k }.first { it != t.listening.k }
            c.excludePartial(72, other, out = true)
            assertFalse(other in c.shownPartials.value, "$mode: partial $other still has a band")
            assertTrue(other in c.hiddenPartials.value, "$mode: partial $other not grey")
            c.discardChecks(72)
            assertFalse(other in c.hiddenPartials.value, "$mode: Discard did not bring partial $other back")
        }
    }

    @Test
    fun theListenedPartialTakenOutLeavesTheReadingOnAnotherAndKeepsItsTargets() {
        val c = tuning(StrobeMode.ONE)
        val t = assertNotNull(c.tuning.value)
        val listened = t.listening.k
        val targets = c.targets.value
        c.excludePartial(72, listened, out = true)
        assertTrue(listened !in c.shownPartials.value && c.activePartial.value != listened, "the listened partial is still read: shown ${c.shownPartials.value}, active ${c.activePartial.value}")
        assertEquals(1, c.shownPartials.value.size, "one band")
        assertEquals(targets, c.targets.value, "the targets do not move (Rule 9)")
        // another note is untouched, and the exclusion holds when the note is left and come back to
        c.selectNote(74); assertTrue(c.hiddenPartials.value.isEmpty())
        c.selectNote(72); assertTrue(listened in c.hiddenPartials.value)
    }

    @Test
    fun aRestoredTuningCarriesItsExclusionsAndANewTuningStartsWithout() {
        val c = tuning(StrobeMode.ALL, FalseStrings().with(c5l).exclude(72, 3, true))
        assertTrue(3 in c.hiddenPartials.value && 3 !in c.shownPartials.value)
        assertEquals(setOf(3), assertNotNull(c.snapshot()).strings.excludedAt(72))
        c.resetSession()
        assertTrue(c.falseStrings.value.isEmpty, "a tuning replaced takes its checks with it")
        val kept = tuning(StrobeMode.ALL, FalseStrings().with(c5l))
        kept.resetSession(keepChecks = true)
        assertEquals(1, kept.falseStrings.value.strings.size, "checks made before the tuning began go with it")
    }
}
