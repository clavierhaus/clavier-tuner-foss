package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.persistence.SessionCodec
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.StrobeMode
import at.clavierhaus.unisonmaster.settings.TunerSettings
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

/** Plays one signal per start(), synchronously. */
private class Played(val signal: FloatArray) : AudioSource {
    override val sampleRateHz = 48_000
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        var pos = 0; val buf = FloatArray(bufferSize)
        while (pos + bufferSize <= signal.size) { signal.copyInto(buf, 0, pos, pos + bufferSize); onBuffer(buf); pos += bufferSize }
    }
    override fun stop() = Unit
}

/**
 * The engine's hooks for an edition (12 Nov 2026): partials left out of a key's reading, lines an edition keeps with the
 * tuning, the microphone lent. A partial left out is not read and has no band on any strobe, the targets do not move,
 * and both the partials and the lines are saved with the tuning; a file of 10-12 Nov 2026 reads as before.
 */
class LeftOutTest {
    private val a4 = 440.0
    private val keys = listOf(33, 40, 45, 52, 57, 64, 76, 81, 88)
    private val samples = listOf(measuredString(69, a4, 4e-4)) + keys.map { measuredString(it, TuningSession.targetF1(it, a4), 1e-4 * (1 + (it - 33) / 20.0)) }

    private fun tuning(strobe: StrobeMode, leftOut: Map<Int, Set<Int>> = emptyMap(), lines: List<String> = emptyList()): TuningController {
        val c = TuningController(Silent) { 1000L }
        c.applySettings(TunerSettings(strobeMode = strobe))
        c.restore(SessionSnapshot(0, a4, 69, samples, sampling = false, tuned = setOf(69), leftOut = leftOut, editionLines = lines))
        c.selectNote(72)
        return c
    }

    @Test
    fun aPartialLeftOutIsNotReadAndHasNoBandOnEveryStrobeAndComesBack() {
        for (mode in StrobeMode.entries) {
            val c = tuning(mode)
            val t = assertNotNull(c.tuning.value)
            val other = c.targets.value.map { it.k }.first { it != t.listening.k }
            c.excludePartial(72, other, out = true)
            assertFalse(other in c.shownPartials.value, "$mode: partial $other still has a band")
            assertTrue(other in c.hiddenPartials.value, "$mode: partial $other not grey")
            c.bringBack(72)
            assertFalse(other in c.hiddenPartials.value, "$mode: partial $other not brought back")
        }
    }

    @Test
    fun theListenedPartialLeftOutLeavesTheReadingOnAnotherAndKeepsItsTargets() {
        val c = tuning(StrobeMode.ONE)
        val listened = assertNotNull(c.tuning.value).listening.k
        val targets = c.targets.value
        c.excludePartial(72, listened, out = true)
        assertTrue(listened !in c.shownPartials.value && c.activePartial.value != listened, "the listened partial is still read")
        assertEquals(1, c.shownPartials.value.size, "one band")
        assertEquals(targets, c.targets.value, "the targets do not move (Rule 9)")
        c.selectNote(74); assertTrue(c.hiddenPartials.value.isEmpty())
        c.selectNote(72); assertTrue(listened in c.hiddenPartials.value)
    }

    @Test
    fun thePartialsLeftOutAndTheEditionsLinesAreSavedAndAFileWithoutThemReadsAsBefore() {
        val lines = listOf("check 72 L 11", "anything an edition writes")
        val snap = SessionSnapshot(5, a4, 69, samples, leftOut = mapOf(72 to setOf(3)), editionLines = lines + "bad\nline")
        val back = SessionCodec.decode(SessionCodec.encode(snap))
        assertEquals(mapOf(72 to setOf(3)), back.leftOut)
        assertEquals(lines, back.editionLines, "kept as they are; a line that is not one printable line is not written")
        val plain = SessionCodec.encode(snap.copy(leftOut = emptyMap(), editionLines = emptyList()))
        assertFalse("x " in plain || "exclude " in plain, "nothing written when there is nothing")
    }

    @Test
    fun aFileOfTheTenthToTheTwelfthKeepsItsCheckedStringsAsEditionLines() {
        // the shape written from 10 to 12 Nov 2026, when the engine wrote the single strings checked itself
        val file = SessionCodec.encode(SessionSnapshot(5, a4, 69, samples, leftOut = mapOf(72 to setOf(3))))
            .replace("exclude 72 3\n", "string 72 L 11 1,2,3,4 2\nb 2 0.59 6.8\nb 3 0.66 5.2\nstring 74 - 12 - 0\nexclude 72 3\n")
        val back = SessionCodec.decode(file)
        assertEquals(listOf("string 72 L 11 1,2,3,4 2", "b 2 0.59 6.8", "b 3 0.66 5.2", "string 74 - 12 - 0"), back.editionLines)
        assertEquals(mapOf(72 to setOf(3)), back.leftOut)
    }

    @Test
    fun aRestoredTuningCarriesWhatWasLeftOutAndANewTuningStartsWithout() {
        val c = tuning(StrobeMode.ALL, mapOf(72 to setOf(3)), listOf("one"))
        assertTrue(3 in c.hiddenPartials.value && 3 !in c.shownPartials.value)
        assertEquals(mapOf(72 to setOf(3)), assertNotNull(c.snapshot()).leftOut)
        assertEquals(listOf("one"), assertNotNull(c.snapshot()).editionLines)
        c.resetSession()
        assertTrue(c.leftOut.value.isEmpty() && c.editionLines.value.isEmpty(), "a tuning replaced takes its own with it")
        val kept = tuning(StrobeMode.ALL, mapOf(72 to setOf(3)), listOf("one"))
        kept.resetSession(keepLeftOut = true)
        assertEquals(listOf("one"), kept.editionLines.value, "what was made before the tuning began goes with it")
    }

    @Test
    fun whileTheMicrophoneIsLentNothingElseOfTheEngineHears() {
        val a = 0.2f
        val tone = FloatArray(48_000 * 2) { i -> (a * kotlin.math.sin(2 * kotlin.math.PI * 440.0 * i / 48_000)).toFloat() }
        val c = TuningController(Played(tone)) { 1000L }
        var heard = 0
        c.lend { heard++ }
        assertTrue(c.startLive())
        assertTrue(heard > 0, "the listener heard the buffers")
        assertEquals(0.0, c.liveLevel.value, "and the engine heard none of them")
        c.lend(null)
    }
}
