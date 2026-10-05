package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.measure.SyntheticString
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.tuning.measuredString
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plays what it is given, one signal per start(), synchronously; [afterBuffer] sees the time after each buffer. */
private class PlayedSource : AudioSource {
    override val sampleRateHz = 48_000
    var signal = FloatArray(0)
    var afterBuffer: ((Double) -> Unit)? = null
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        var pos = 0
        val buf = FloatArray(bufferSize)
        while (pos + bufferSize <= signal.size) {
            signal.copyInto(buf, 0, pos, pos + bufferSize)
            onBuffer(buf)
            pos += bufferSize
            afterBuffer?.invoke(pos.toDouble() / sampleRateHz)
        }
    }
    override fun stop() = Unit
}

class TuningControllerTest {
    private val a4 = 443.1
    // the temperament-first way, once the free app's pin and since 4 Oct 2026 the tuner's choice: the tests below describe it
    private val settings = TunerSettings(lowestKeyMidi = 17, lowestUnwoundMidi = 40, temperamentFirst = true)

    private fun et(midi: Int) = TuningSession.targetF1(midi, a4)

    /** Plays [signal] through the controller, as one stretch of listening. */
    private fun TuningController.hear(source: PlayedSource, signal: FloatArray) {
        stopLive()
        source.signal = signal
        assertTrue(startLive())
    }

    private fun controller(s: TunerSettings = settings): Pair<TuningController, PlayedSource> {
        val src = PlayedSource()
        val c = TuningController(src) { 1000L }
        c.applySettings(s)
        return c to src
    }

    @Test
    fun theHubsA4ReadingDiesWithItsSound() {
        // Law 3 on the sampling page (Peter, 4 Oct 2026: "a frequency display that actually reflects the A4 from the piano"):
        // a string struck and gone leaves no number behind
        val (c, src) = controller()
        c.hear(src, SyntheticString.strike(443.12, 8e-4, 6.0, decayS = 0.3))
        assertNull(c.liveHz.value, "the string has died away: no A4 is shown")
        c.hear(src, SyntheticString.strike(443.12, 8e-4, 3.0))
        assertNotNull(c.liveHz.value, "struck and sounding: read")
    }

    /**
     * Superseded 5 Oct 2026. Until then Done took the A4 string as it stood
     * and made *that* the reference, so the tuner had nothing to tune A4 to:
     * the pitch was whatever the string happened to be. Peter, 15:55: "we
     * define the initial pitch for A4" — it starts at 440.0 Hz, the two steps
     * move it by a tenth, and the string is brought to it. Done keeps the
     * pitch that was chosen and records the string as it was measured.
     */
    @Test
    fun theHubReadsTheA4StringAndDoneKeepsTheChosenPitch() {
        val (c, src) = controller()
        assertEquals(TuningController.DEFAULT_REFERENCE_HZ, c.referenceA4Hz.value, 1e-9, "the pitch starts at 440")
        c.setReference(441.0)
        c.hear(src, SyntheticString.strike(443.12, 8e-4, 3.0))
        val hz = assertNotNull(c.liveHz.value, "the A4 string is read")
        assertTrue(abs(hz - 443.12) < 0.02, "read %.3f".format(hz))
        assertEquals(441.0, c.acceptLive()!!, 1e-9, "the pitch chosen, not the string as it stands")
        assertEquals(441.0, c.referenceA4Hz.value, 1e-9)
        val m = assertNotNull(c.measurements()[69])
        assertTrue(abs(m.b - 8e-4) / 8e-4 < 0.15, "A4 measured with its inharmonicity: B %.2e".format(m.b))
        assertTrue(m.partials.size >= 4)
        val t = c.tuning.value!!
        assertTrue(t.sampling, "on to the sampling")
        assertNull(t.caught, "nothing struck yet")
        assertFalse(c.acceptReady()); assertFalse(c.doneReady())
    }

    @Test
    fun aTemperamentNoteMatchesOnTargetAndDoneRefusesItOffTarget() {
        val (c, src) = controller()
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        val t = c.tuning.value!!
        assertEquals(1, t.listening.k)
        // four cents flat: read, not matched
        c.hear(src, SyntheticString.strike(et(68) * 2.0.pow(-4.0 / 1200), 7e-4, 3.0))
        val r = c.reading.value!!
        assertTrue(r.live)
        assertEquals(-4.0, r.cents!!, 0.1)
        assertFalse(c.matchedNow())
        assertNull(c.acceptLive(), "Done is refused off target")
        // on target
        c.hear(src, SyntheticString.strike(et(68) + 0.02, 7e-4, 3.0))
        assertTrue(c.matchedNow())
        assertNotNull(c.acceptLive())
        assertEquals(et(68), c.measurements()[68]!!.f1Hz, 0.05)
        assertEquals(67, c.tuning.value!!.midi)
    }

    @Test
    fun everyPartialOfASoundingStringIsReadOnEveryNoteWithoutAnyMode() {
        val (c, src) = controller()
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        assertFalse(c.fullSpectrum.value, "no mode is switched on")
        c.hear(src, SyntheticString.strike(et(68), 7e-4, 3.0))
        val live = c.livePartials.value
        assertTrue(live.size >= 4, "the string's partials are read as it sounds: ${live.map { it.k }}")
        assertTrue(live.any { it.k == 1 } && live.any { it.k >= 3 }, "the listened one and the others alike: ${live.map { it.k }}")
        assertTrue(live.all { it.level in 0.0..1.0 } && live.any { it.level < 1.0 },
            "each with its level, not all at one: ${live.map { "%d:%.2f".format(it.k, it.level) }}")
        val cap = c.highestPartial(c.tuning.value!!.targetHz)
        assertEquals(c.targets.value.map { it.k }.filter { it <= cap }.toSet() + 1, c.shownPartials.value,
            "every partial the strike showed is read, up to the highest the setting offers")
        assertTrue(c.shownPartials.value.size > 1)
    }

    /**
     * A string of its own stiffness, struck once; its partial 4 — the one
     * being tuned — dies away at 2.7 s while the others ring on. Once the
     * strike has spoken (1.5 s in) the string's own series is the targets of
     * every partial, and the reading, when another partial stands in for
     * the dead one, is the same string seen another way: it must not move.
     * Until 3 Oct 2026 the clean partials were read against the model's
     * series for the whole strike, and the stand-in carried the model's
     * error at its partial — on the Samick's F3-C-s1 through the probe the
     * reading hopped 8 c as partial 1 stood in for partial 4 at 5.76 s.
     */
    @Test
    fun whenThePartialBeingTunedDiesTheStandInIsReadAgainstTheStringsOwnSeries() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        val gs4 = measuredString(68, et(68), 7e-4)
        c.restore(SessionSnapshot(0, a4, 56, listOf(measuredString(69, a4, 8e-4), gs4), sampling = false))
        val t = c.tuning.value!!
        assertEquals(4, t.listening.k)
        val target = t.listening.targetHz
        // the model's B for the unmeasured G#3 comes off the curve through G#4 and A4 (about 1.4e-4); the string's is 4e-4
        val b = 4e-4
        val f1 = target / (4 * Inharmonicity.ratio(4, b))
        val sr = 48_000
        val sig = FloatArray(5 * sr)
        run {
            val phase = DoubleArray(9); val start = (0.3 * sr).toInt()
            val rng = kotlin.random.Random(5)
            for (i in sig.indices) {
                var x = 1e-4 * (rng.nextDouble() * 2 - 1)
                if (i >= start) {
                    val tt = (i - start).toDouble() / sr
                    val env = exp(-tt / 3.0) * (1 - exp(-tt / 0.002))
                    for (k in 1..8) {
                        val hz = SyntheticString.partialHz(f1, b, k)
                        val gone = if (k == 4 && tt > 2.4) exp(-(tt - 2.4) / 0.03) else 1.0
                        phase[k] += 2 * PI * hz / sr
                        x += 0.2 * env * gone * 10.0.pow(-3.0 * (k - 1) / 20) * sin(phase[k])
                    }
                }
                sig[i] = x.toFloat()
            }
        }
        val trail = ArrayList<Pair<Double, TuningController.Reading>>()
        src.afterBuffer = { at -> c.reading.value?.let { trail += at to it } }
        c.hear(src, sig)
        val before = trail.filter { it.first in 2.3..2.6 }.map { it.second }
        assertTrue(before.isNotEmpty() && before.all { it.live && 4 in it.usedPartials }, "partial 4 is read before it dies")
        val after = trail.filter { it.first in 3.6..4.9 }.map { it.second }
        assertTrue(after.isNotEmpty() && after.all { it.live }, "the string is still read once partial 4 is gone")
        assertTrue(after.all { 4 !in it.usedPartials && it.usedPartials.isNotEmpty() }, "another partial stands in: ${after.map { it.usedPartials }.distinct()}")
        val was = before.last().cents!!
        val worst = after.maxByOrNull { abs(it.cents!! - was) }!!
        assertTrue(abs(worst.cents!! - was) < 0.3,
            "the stand-in is the same string: read %+.2f c on partial 4, %+.2f c on %s".format(was, worst.cents, worst.usedPartials))
    }

    /**
     * Each live partial carries its width — the robust spread of its last
     * half second of readings (blueprint §14, 3 Oct 2026). A loud partial is
     * read to hundredths of a cent; one standing a dozen dB over the floor
     * scatters by the floor's phase, and its width says so. The width is
     * cleared at a strike: a string whose fundamental beat on the strike
     * before must not lend that width to the clean string struck next.
     */
    /**
     * The bells show the string, not its distance from the target: a string
     * standing well below where it is to be tuned has partials to show, and
     * they stack on the anchor wherever the pitch stands (the blueprint's §2).
     * Peter at the piano, 4 Oct 2026, 17:11: at 45 and 31 cents flat the screen
     * said only "flat" and drew no bell at all; the bells appeared when he had
     * pulled the string to within ten cents.
     */
    @Test
    fun aStringFarBelowItsTargetStillShowsItsPartials() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        c.restore(SessionSnapshot(0, a4, 56, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        val t = c.tuning.value!!
        val b = 2.1e-4
        val off = -45.0
        // the string as it stands: its target's own f1, pulled 45 cents down
        val f1 = t.listening.targetHz / (t.listening.k * Inharmonicity.ratio(t.listening.k, b)) * 2.0.pow(off / 1200)
        c.hear(src, SyntheticString.strike(f1, b, 4.0))
        val partials = c.livePartials.value
        assertTrue(partials.size >= 3, "the strike's partials are there to be drawn: ${partials.map { it.k }}")
        for (p in partials) assertEquals(off, p.cents, 3.0, "partial ${p.k} stands with the string, ${p.cents} c")
        val r = assertNotNull(c.reading.value)
        assertEquals(off, TuningSession.centsOff(assertNotNull(r.hz), r.targetHz), 3.0, "and the reading says how far the string is from its target")
    }

    @Test
    fun aPartialsWidthSaysHowWellItIsReadAndStartsAfreshAtAStrike() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        c.restore(SessionSnapshot(0, a4, 56, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        val t = c.tuning.value!!
        val b = 2.1e-4
        val f1 = t.listening.targetHz / (t.listening.k * Inharmonicity.ratio(t.listening.k, b))
        val sr = 48_000
        // strike 1: partial 1 split 1.5 Hz (a false beat); strike 2 at 5.0 s: the same string clean;
        // white noise under it all, loud enough that partial 8 stands only a dozen dB over the
        // floor in its own band while partial 1 stands forty
        val sig = FloatArray(8 * sr)
        run {
            val rng = kotlin.random.Random(9)
            val phase = DoubleArray(9); val phase2 = DoubleArray(9)
            for (i in sig.indices) {
                val tt = i.toDouble() / sr
                var x = 0.08 * (rng.nextDouble() * 2 - 1)
                val strikeAt = if (tt < 5.0) 0.3 else 5.0
                if (tt >= strikeAt) {
                    val since = tt - strikeAt
                    // the first strike dies in two seconds so that the second stands clear of it
                    val env = exp(-since / (if (tt < 5.0) 2.0 else 3.0)) * (1 - exp(-since / 0.002))
                    for (k in 1..8) {
                        val hz = SyntheticString.partialHz(f1, b, k)
                        val a = 0.2 * env * 10.0.pow(-3.0 * (k - 1) / 20)
                        if (k == 1 && tt < 5.0) {
                            phase[1] += 2 * PI * (hz - 0.75) / sr; phase2[1] += 2 * PI * (hz + 0.75) / sr
                            x += a / 2 * sin(phase[1]) + a / 2 * sin(phase2[1])
                        } else { phase[k] += 2 * PI * hz / sr; x += a * sin(phase[k]) }
                    }
                }
                sig[i] = x.toFloat()
            }
        }
        val trail = ArrayList<Pair<Double, List<at.clavierhaus.unisonmaster.measure.LivePartial>>>()
        src.afterBuffer = { at -> trail += at to c.livePartials.value }
        c.hear(src, sig)
        fun width(at: Double, k: Int) = trail.lastOrNull { it.first <= at }?.second?.firstOrNull { it.k == k }?.widthCents
        val w1 = width(2.4, 1); val w2 = width(2.4, 2); val w8 = width(2.4, 8)
        assertTrue(w1 != null && w1 > 0.5, "the split fundamental is wide on the first strike: $w1")
        assertTrue(w2 != null && w2 < 0.1, "a loud clean partial is a needle: $w2")
        assertTrue(w8 != null && w8 > 2 * w2 && w8 > 0.15, "a partial near the floor is wide against the needle: $w8 vs $w2")
        // the second strike: the first readings of partial 1 after its settle carry no width from the beat before
        val first = trail.firstOrNull { (at, lp) -> at > 5.1 && lp.any { it.k == 1 && it.widthCents != null } }
        assertTrue(first != null, "partial 1 is read again on the second strike")
        val w1b = first!!.second.first { it.k == 1 }.widthCents!!
        assertTrue(w1b < 0.3, "the clean string's fundamental starts afresh at %.2f s: %.2f c wide".format(first.first, w1b))
    }

    @Test
    fun belowTheOctaveTheStringIsTunedByItsPartialToThePartnersPartial() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        val gs4 = measuredString(68, et(68), 7e-4)
        c.restore(SessionSnapshot(0, a4, 56, listOf(measuredString(69, a4, 8e-4), gs4), sampling = false))
        val t = c.tuning.value!!
        assertEquals(4, t.listening.k)
        val target = gs4.partialHz(2)!!
        assertEquals(target, t.listening.targetHz, 1e-9)
        // a string of its own stiffness, tuned so that its fourth partial meets the target
        val b = 3e-4
        val f1 = target / (4 * Inharmonicity.ratio(4, b))
        c.hear(src, SyntheticString.strike(f1, b, 3.0))
        val r = c.reading.value!!
        assertEquals(4, r.k)
        assertTrue(abs(r.hz!! - target) < 0.03, "partial 4 read %.3f for %.3f".format(r.hz, target))
        assertTrue(c.matchedNow())
        assertNotNull(c.acceptLive())
        val m = c.measurements()[56]!!
        assertEquals(target, m.partialHz(4)!!, 0.03, "the partial tuned is kept as read")
        assertTrue(abs(m.b - b) / b < 0.2, "and the string's own B: %.2e".format(m.b))
    }

    /**
     * Superseded 4 Oct 2026. This test asserted the opposite — that a string
     * 70 cents off its target is *not* read and only placed coarsely by the
     * finder ("70 cents off is outside the band"). At the Samick that evening
     * Peter found what that costs: "In the beginning there was nothing except
     * 'flat'. Only when tuning up did the bells appear." The band is the
     * reader's, around where it listens, and is no statement about what the
     * piano is allowed to stand at (Rule 0); the reader now goes to the
     * string and the cents are worked out against the target that stands.
     */
    /**
     * Superseded 4 Oct 2026. This test asserted the opposite — that a string
     * 70 cents off its target is *not* read and only placed coarsely by the
     * finder ("70 cents off is outside the band"). At the Samick that evening
     * Peter found what that costs: "In the beginning there was nothing except
     * 'flat'. Only when tuning up did the bells appear." The reader's band is
     * the reader's and says nothing about what the piano may be standing at
     * (Rule 0), so the reader now goes to the string.
     *
     * The limit is no longer the band but half a semitone, and it is not a
     * figure anyone chose: past 50 cents the key identifier names the
     * neighbouring key, because that is the nearer string, and the engine
     * cannot tell "G#4 pulled 70 cents flat" from "G4 struck" from the sound
     * alone. Measured here on 4 Oct 2026, G#4 with B = 7e-4: read exactly,
     * on all five partials, at 20, 30, 40 and 45 cents off; from 50 cents the
     * identifier says G4 and the screen has the coarse word only.
     */
    @Test
    fun aStringIsReadWhereItStandsUntilItIsNearerTheNextKey() {
        val (c, src) = controller()
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        c.hear(src, SyntheticString.strike(et(68) * 2.0.pow(-45.0 / 1200), 7e-4, 3.0))
        val r = assertNotNull(c.reading.value)
        assertTrue(r.live, "45 cents flat is read where it stands, not merely called flat")
        assertEquals(-45.0, TuningSession.centsOff(assertNotNull(r.hz), r.targetHz), 3.0)
        assertFalse(c.matchedNow(), "and 45 cents off is not a match")
        assertTrue(c.livePartials.value.size >= 4, "with its partials to draw: ${c.livePartials.value.map { it.k }}")

        // past half a semitone the nearer string is the next key, and the engine says so
        val (c2, src2) = controller()
        c2.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        c2.hear(src2, SyntheticString.strike(et(68) * 2.0.pow(-60.0 / 1200), 7e-4, 3.0))
        val r2 = assertNotNull(c2.reading.value)
        assertFalse(r2.live, "60 cents flat stands nearer G4 than G#4 and is not claimed as a reading")
        assertEquals(-60.0, assertNotNull(r2.coarseCents, "the finder still places it"), 3.0)
    }

    @Test
    fun leavingANoteKeepsItOnlyWhenItWasHeardNearItsTarget() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        c.stepNote(-1)                                  // not struck: nothing kept
        assertNull(c.measurements()[68])
        c.selectNote(68)
        c.hear(src, SyntheticString.strike(et(68) * 2.0.pow(1.0 / 1200), 7e-4, 3.0))
        c.stepNote(-1)                                  // struck, a cent sharp: kept as it stood
        val m = assertNotNull(c.measurements()[68])
        assertEquals(1.0, TuningSession.centsOff(m.f1Hz, et(68)), 0.1)
    }

    @Test
    fun aTappedPartialIsReadAgainstItsOwnPlace() {
        val (c, src) = controller()
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 7e-4)), sampling = false))
        c.toggleFullSpectrum()
        c.activatePartial(3)
        assertEquals(3, c.activePartial.value)
        val t = c.tuning.value!!
        val p3 = 3 * t.targetHz * Inharmonicity.ratio(3, t.b)
        c.hear(src, SyntheticString.strike(t.targetHz, t.b, 3.0))
        val r = c.reading.value!!
        assertEquals(3, r.k)
        assertTrue(abs(r.hz!! - p3) < 0.05, "partial 3 read %.3f for %.3f".format(r.hz, p3))
        assertTrue(c.liveAudible.value.containsAll(setOf(1, 2, 3)), "Full Spectrum hears ${c.liveAudible.value}")
    }

    @Test
    fun theScreenFollowsTheKeyStruckAndKeepsTheNoteItLeft() {
        val (c, src) = controller(settings.copy(temperamentFirst = false))
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        val first = SyntheticString.strike(et(68), 7e-4, 2.5)
        val second = SyntheticString.strike(et(66) * 2.0.pow(-8.0 / 1200), 7e-4, 2.5, seed = 2)
        c.hear(src, first + second)
        assertEquals(66, c.tuning.value!!.midi, "the screen moved to the key struck")
        assertEquals(et(68), c.measurements()[68]!!.f1Hz, 0.05, "and kept G#4 as it was heard, not as F#4")
        assertEquals(-8.0, c.reading.value!!.cents!!, 0.3, "F#4 read on its own target")
    }

    @Test
    fun withoutFollowingTheKeyStruckIsNamed() {
        val (c, src) = controller(settings.copy(temperamentFirst = false, autoNote = false))
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        c.hear(src, SyntheticString.strike(et(66), 7e-4, 1.2))
        assertEquals(68, c.tuning.value!!.midi)
        assertEquals(66, c.heardMidi.value)
    }

    @Test
    fun samplingCatchesTheKeyStruckAndAcceptKeepsIt() {
        // Pro: any key, in any order; six besides A4 and Done ends it
        val (c, src) = controller(settings.copy(lowestKeyMidi = 21, temperamentFirst = false))
        c.hear(src, SyntheticString.strike(443.1, 8e-4, 2.5))
        c.acceptLive()
        assertTrue(c.tuning.value!!.sampling)
        // A0 struck, twelve cents flat of its key: caught as A0, not yet kept
        c.hear(src, SyntheticString.strike(et(21) * 2.0.pow(-12.0 / 1200), 1.2e-4, 3.0))
        var t = c.tuning.value!!
        assertEquals(21, t.caught, "caught"); assertFalse(t.caughtAccepted)
        assertEquals(21, t.midi, "the screen is the key caught")
        assertTrue(c.acceptReady()); assertFalse(c.doneReady())
        assertNull(c.measurements()[21], "not kept until Accept")
        assertNotNull(c.acceptLive())
        t = c.tuning.value!!
        assertTrue(t.caughtAccepted); assertTrue(21 in t.sampled); assertEquals(1, t.samplesIn)
        assertEquals(-12.0, TuningSession.centsOff(c.measurements()[21]!!.f1Hz, et(21)), 0.5, "kept as it stood")
        assertFalse(c.acceptReady(), "kept once")
        // another key: caught afresh, Accept open again
        c.hear(src, SyntheticString.strike(et(40) * 2.0.pow(8.0 / 1200), 1.9e-4, 3.0))
        t = c.tuning.value!!
        assertEquals(40, t.caught); assertFalse(t.caughtAccepted); assertTrue(c.acceptReady())
        // A0 struck again, now eight cents flat: already sampled, so the new measurement replaces it, still accepted
        c.hear(src, SyntheticString.strike(et(21) * 2.0.pow(-8.0 / 1200), 1.2e-4, 3.0))
        t = c.tuning.value!!
        assertEquals(21, t.caught); assertTrue(t.caughtAccepted); assertFalse(c.acceptReady())
        assertEquals(-8.0, TuningSession.centsOff(c.measurements()[21]!!.f1Hz, et(21)), 0.5, "replaced")
        assertEquals(1, t.samplesIn, "E2 was never kept")
        // five more, kept: Done opens, and ends the sampling at A4's neighbour
        for ((m, b) in listOf(33 to 8e-5, 45 to 1.3e-4, 52 to 1.6e-4, 60 to 3.4e-4, 76 to 1.0e-3)) {
            c.hear(src, SyntheticString.strike(et(m), b, 3.0))
            assertEquals(m, c.tuning.value!!.caught, "caught ${m}")
            assertNotNull(c.acceptLive(), "kept ${m}")
        }
        t = c.tuning.value!!
        assertEquals(6, t.samplesIn); assertEquals(6, t.samplesNeeded); assertTrue(t.samplingReady); assertTrue(c.doneReady())
        c.finishSampling()
        t = c.tuning.value!!
        assertFalse(t.sampling); assertEquals(68, t.midi); assertTrue(t.curveReady)
    }

    @Test
    fun theTemperamentFirstWalkSamplesLikeAnyOther() {
        // one sampling law (4 Oct 2026): the walk's preference changes the route after the sampling, not what is sampled —
        // six strings wherever the tuner strikes them end it, none of the set of 25 Sept required
        val (c, src) = controller(settings.copy(lowestKeyMidi = 21, temperamentFirst = true))
        c.hear(src, SyntheticString.strike(443.1, 8e-4, 2.5))
        c.acceptLive()
        var t = c.tuning.value!!
        assertTrue(t.sampling); assertEquals(TunerSettings.MIN_SAMPLES, t.samplesNeeded)
        for ((m, b) in listOf(21 to 1.2e-4, 33 to 8e-5, 45 to 1.3e-4, 52 to 1.6e-4, 60 to 3.4e-4, 76 to 1.0e-3)) {
            c.hear(src, SyntheticString.strike(et(m), b, 3.0))
            assertEquals(m, c.tuning.value!!.caught, "caught ${m}")
            assertNotNull(c.acceptLive(), "kept ${m}")
        }
        t = c.tuning.value!!
        assertEquals(6, t.samplesIn); assertTrue(t.samplingReady); assertTrue(c.doneReady())
        c.finishSampling()
        t = c.tuning.value!!
        assertFalse(t.sampling); assertFalse(t.recordsOnLeaving, "the walk itself is still the temperament octave first, asking Done")
    }

    @Test
    fun fullSpectrumBellsStandStillOnASteadyUnison() {
        // three strings a few hundredths of a cent apart, sounding together: every
        // shown partial is read by phase and stays within hundredths of a hertz
        val c = TuningController(TappedSource) { 0L }
        c.applySettings(settings)
        c.restore(SessionSnapshot(0, a4, 68, listOf(measuredString(69, a4, 7e-4)), sampling = false))
        c.toggleFullSpectrum()
        val t = c.tuning.value!!
        val strings = listOf(0.0, 0.04, -0.03).mapIndexed { i, cts -> SyntheticString.strike(t.targetHz * 2.0.pow(cts / 1200), t.b, 3.0, seed = 3 + i) }
        TappedSource.signal = FloatArray(strings[0].size) { i -> strings.sumOf { it[i].toDouble() }.toFloat() / 3 }
        val seen = HashMap<Int, MutableList<Double>>()
        TappedSource.afterHop = { pos -> if (pos > 48_000) for (lp in c.livePartials.value) seen.getOrPut(lp.k) { ArrayList() } += lp.hz }
        c.startLive()
        assertTrue(seen.size >= 4, "partials shown: ${seen.keys}")
        for ((k, hz) in seen) {
            val swing = hz.max() - hz.min()
            assertTrue(swing < 0.06 * k, "partial $k swings %.3f Hz over the strike".format(swing))
        }
    }
    @Test
    fun theScreenKnowsWhetherTheReferenceWasMeasuredOrAssumed() {
        // 440 Hz is where the reference starts, not where this piano stands. A
        // session that never measured the A4 string tunes to an assumption, and on
        // a piano at 439.4 that is 2.3 cents -- more than a tuner works to. The
        // screen has to be able to say which it is.
        val (c, _) = controller()
        c.restore(SessionSnapshot(0, a4, 53, listOf(measuredString(60, et(60), 2.2e-4)), sampling = false))
        assertFalse(assertNotNull(c.tuning.value).referenceMeasured,
            "no A4 was measured: the reference is assumed")
        c.restore(SessionSnapshot(0, a4, 53, listOf(
            measuredString(TuningSession.MIDI_A4, a4, 4.0e-4), measuredString(60, et(60), 2.2e-4),
        ), sampling = false))
        assertTrue(assertNotNull(c.tuning.value).referenceMeasured,
            "A4 was measured from the piano: the reference is a measurement")
    }

    @Test
    fun theCurveIsComputedForEveryKeyFromTheMeasuredInharmonicity() {
        // The sample set is a handful of notes; the curve it asks for covers the
        // whole compass. Progress drew only how far the measured notes stand from
        // equal temperament -- a result, not a target -- while this was computed for
        // every key and shown for none.
        val (c, _) = controller()
        val samples = listOf(
            measuredString(36, et(36), 0.6e-4), measuredString(48, et(48), 1.1e-4),
            measuredString(60, et(60), 2.2e-4), measuredString(69, a4, 4.0e-4),
            measuredString(81, et(81), 1.2e-3), measuredString(93, et(93), 4.0e-3),
        )
        c.restore(SessionSnapshot(0, a4, 60, samples, sampling = false))
        val t = assertNotNull(c.tuning.value)
        val curve = t.curve
        assertTrue(curve.isNotEmpty(), "the curve is computed from the samples")
        for (m in t.lowestMidi..TuningSession.MIDI_C8) {
            assertTrue(m in curve, "every key of the compass has a target: $m is missing")
        }
        // the shape inharmonicity forces: the treble stretched sharp of equal
        // temperament, the bass flat of it, both growing with distance from A4
        val a4c = curve.getValue(TuningSession.MIDI_A4)
        assertTrue(curve.getValue(96) > a4c + 1.0, "the treble is stretched sharp: C7 ${curve[96]}")
        assertTrue(curve.getValue(36) < a4c - 1.0, "the bass is stretched flat: C2 ${curve[36]}")
        assertTrue(curve.getValue(96) > curve.getValue(84), "the stretch grows upward")
        assertTrue(curve.getValue(36) < curve.getValue(48), "the stretch grows downward")
    }

    @Test
    fun theTargetsFollowTheStringThatWasStruck() {
        // The partial ratios are a live measurement and are not stored. A string whose
        // inharmonicity is nothing like the model's must pull the targets onto its
        // own partials within one strike, so that its bells stand on one line.
        val (c, src) = controller()
        c.restore(SessionSnapshot(0, a4, 53, listOf(measuredString(69, a4, 8e-4)), sampling = false))
        val t = assertNotNull(c.tuning.value)
        val k = t.listening.k
        val playedB = 5.0e-4                       // the model for F3 predicts far less
        val f1 = t.listening.targetHz / (k * Inharmonicity.ratio(k, playedB))
        val before = c.targets.value.first { it.k == 2 }.hz
        c.hear(src, SyntheticString.strike(f1, playedB, 3.0))
        val after = c.targets.value
        // the partial being tuned still sits exactly on its own target
        assertEquals(t.listening.targetHz, after.first { it.k == k }.hz, t.listening.targetHz * 1e-6,
            "the listened partial stays on the target it is tuned to")
        // and every other partial is now where this string put it, not where the model did
        // within the measurement's own repeatability: on the 30 Sept corpus one
        // string's ratios repeat from strike to strike within 0.04-0.11 cents, so a
        // quarter of a cent is the honest bar for a single synthetic strike
        for (p in after.filter { it.k in 2..6 }) {
            val want = p.k * f1 * Inharmonicity.ratio(p.k, playedB)
            val off = abs(TuningSession.centsOff(p.hz, want))
            assertTrue(off < 0.25, "partial ${p.k} follows the string that sounded: %.3f c off".format(off))
        }
        assertTrue(abs(TuningSession.centsOff(after.first { it.k == 2 }.hz, before)) > 0.5,
            "the targets really moved off the model's prediction")
    }

    @Test
    fun everyPartialShownIsPlacedFromTheOneBeingTuned() {
        // The target of the listened partial comes from the interval it must make;
        // the partial table of a measured string is its own. The two are not the same
        // number, and before 30 Sept the table showed the second while the bell used
        // the first, so the row being tuned disagreed with the bell above it -- on the
        // Samick's F3, 697.0 against 697.77, and a clean string's partials scattered
        // over 2.5 cents instead of standing together within 0.24.
        val (c, _) = controller()
        c.restore(SessionSnapshot(0, a4, 53, listOf(
            measuredString(69, a4, 8e-4),
            measuredString(53, et(53), 2.2e-4, offsetK = 4, offsetCents = 2.0),
        ), sampling = false))
        val t = c.tuning.value!!
        val targets = c.targets.value
        val listened = targets.firstOrNull { it.k == t.listening.k }
        assertNotNull(listened, "the partial being tuned is in the table: ${targets.map { it.k }}")
        assertEquals(t.listening.targetHz, listened.hz, t.listening.targetHz * 1e-9,
            "the table's target for the listened partial is the one it is tuned to")
        // the ratios between the partials survive: anchoring moves the series bodily,
        // it does not reshape the string. Partial k over partial 1 must still be the
        // string's own, as it was measured.
        // Nothing has been struck, so the partials are the model's placing, from this
        // note's own measured B. The stored partial table must not reach the targets
        // partial 4 of the stored measurement was deliberately put two
        // cents off, and that offset must be nowhere in what the screen is tuned to.
        val m = assertNotNull(c.measurements()[53])
        val one = targets.first { it.k == 1 }
        for (p in targets) {
            val want = p.k * one.hz * Inharmonicity.ratio(p.k, m.b) / Inharmonicity.ratio(1, m.b)
            assertEquals(want, p.hz, want * 1e-9, "partial ${p.k} follows the string's own B")
        }
        val stored = m.partials.first { it.k == 4 }.cents - m.partials.first { it.k == 1 }.cents
        val modelled = 1200.0 * kotlin.math.ln(Inharmonicity.ratio(4, m.b) / Inharmonicity.ratio(1, m.b)) / kotlin.math.ln(2.0)
        assertTrue(abs(stored - modelled) > 1.0, "the stored table really does differ from the model here")
        val four = targets.first { it.k == 4 }
        assertTrue(abs(TuningSession.centsOff(four.hz, 4 * one.hz * 2.0.pow(stored / 1200.0))) > 1.0,
            "the stored partial table does not reach the targets")
    }
}


/** Plays [signal] and calls [afterHop] with the position after every buffer. */
private object TappedSource : AudioSource {
    override val sampleRateHz = 48_000
    var signal = FloatArray(0)
    var afterHop: (Int) -> Unit = {}
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        val buf = FloatArray(bufferSize); var p = 0
        while (p + bufferSize <= signal.size) { signal.copyInto(buf, 0, p, p + bufferSize); onBuffer(buf); p += bufferSize; afterHop(p) }
    }
    override fun stop() = Unit
}