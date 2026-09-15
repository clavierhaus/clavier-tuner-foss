package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.dsp.StringModel
import at.clavierhaus.unisonmaster.model.StringSlot
import at.clavierhaus.unisonmaster.tuning.EqualTemperament
import at.clavierhaus.unisonmaster.tuning.Notes
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** Deterministic AudioSource that plays back a prepared signal in hops. */
private class FakeAudioSource(
    override val sampleRateHz: Int,
    private val signal: FloatArray,
) : AudioSource {
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        var pos = 0
        val buffer = FloatArray(bufferSize)
        while (pos + bufferSize <= signal.size) {
            signal.copyInto(buffer, 0, pos, pos + bufferSize)
            onBuffer(buffer)
            pos += bufferSize
        }
    }

    override fun stop() = Unit
}

class PartialMonitorTest {

    @Test
    fun liveMonitorMeasuresSyntheticA3() {
        val sr = 48_000
        val ref = 443.0
        val midi = 57 // A3
        val f0 = EqualTemperament.frequencyOf(midi, ref)

        // 2 s of a decaying "piano-ish" tone: partials 1..3 with falling weights
        val n = sr * 2
        val signal = FloatArray(n) { i ->
            val t = i.toDouble()
            (0.4 * sin(2.0 * PI * f0 * t / sr) +
                0.2 * sin(2.0 * PI * 2 * f0 * t / sr) +
                0.1 * sin(2.0 * PI * 3 * f0 * t / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.select(midi)
        monitor.start() // FakeAudioSource runs synchronously to end of signal

        val levels = monitor.levels.value
        assertTrue(levels.size >= 4, "expected at least 4 partials, got ${levels.size}")
        assertTrue(levels[0].levelDb > levels[1].levelDb, "partial 1 should exceed 2")
        assertTrue(levels[1].levelDb > levels[2].levelDb, "partial 2 should exceed 3")
        assertTrue(
            levels[2].levelDb - levels[3].levelDb > 20.0,
            "absent partial 4 should sit far below partial 3",
        )
    }

    @Test
    fun refinedFrequencyTracksDetunedPartial() {
        val sr = 48_000
        val ref = 443.0
        val midi = 57 // A3
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val detuned2 = 2 * f0 * 1.003 // partial 2 ~5.2 cents sharp (+1.33 Hz)

        val n = sr * 2
        val signal = FloatArray(n) { i ->
            val t = i.toDouble()
            (0.4 * sin(2.0 * PI * f0 * t / sr) +
                0.2 * sin(2.0 * PI * detuned2 * t / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.select(midi)
        monitor.start()

        val levels = monitor.levels.value
        val p1 = levels[0]
        val p2 = levels[1]
        assertTrue(p1.measuredHz != null, "fundamental should be refined")
        assertTrue(p2.measuredHz != null, "partial 2 should be refined")
        assertTrue(
            kotlin.math.abs(p1.measuredHz!! - f0) < 0.02,
            "p1 measured ${p1.measuredHz}, expected $f0",
        )
        assertTrue(
            kotlin.math.abs(p2.measuredHz!! - detuned2) < 0.05,
            "p2 measured ${p2.measuredHz}, expected $detuned2 (target was ${p2.frequencyHz})",
        )
    }

    @Test
    fun autoDetectIdentifiesPlayedNote() {
        val sr = 48_000
        val ref = 440.0
        val playedMidi = 52 // E3
        val f0 = EqualTemperament.frequencyOf(playedMidi, ref)

        val n = sr * 2
        val signal = FloatArray(n) { i ->
            val t = i.toDouble()
            (0.4 * sin(2.0 * PI * f0 * t / sr) +
                0.15 * sin(2.0 * PI * 2 * f0 * t / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.select(69) // deliberately wrong start (A4)
        // autoDetect defaults to true
        monitor.start()

        assertTrue(
            monitor.selectedMidi.value == playedMidi,
            "auto-detect selected ${monitor.selectedMidi.value}, expected $playedMidi",
        )
        // and the analyzer retargeted: fundamental refined near f0
        val p1 = monitor.levels.value.firstOrNull()
        assertTrue(p1?.measuredHz != null, "fundamental should be measured after retarget")
        assertTrue(
            kotlin.math.abs(p1!!.measuredHz!! - f0) < 0.05,
            "measured ${p1.measuredHz}, expected $f0",
        )
    }

    @Test
    fun partialTogglesFlipMembership() {
        val tuning = TuningController(FakeAudioSource(48_000, FloatArray(0)))
        val monitor = PartialMonitor(FakeAudioSource(48_000, FloatArray(0)), tuning)
        assertTrue(monitor.enabledPartials.value == (1..monitor.partialCount).toSet())
        monitor.togglePartial(3)
        assertTrue(3 !in monitor.enabledPartials.value)
        assertTrue(monitor.enabledPartials.value.size == monitor.partialCount - 1)
        monitor.togglePartial(3)
        assertTrue(3 in monitor.enabledPartials.value)
        monitor.togglePartial(0)  // out of range: no-op
        monitor.togglePartial(monitor.partialCount + 1)
        assertTrue(monitor.enabledPartials.value == (1..monitor.partialCount).toSet())
    }

    @Test
    fun onsetResetSnapsToRetunedString() {
        // Strike A, silence (pin turned), strike B two Hz higher: after the
        // second onset the smoothed frequency must reflect B, not an average.
        val sr = 48_000
        val ref = 440.0
        val midi = 57 // A3, f0 = 220
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val fB = f0 + 2.0

        val secA = sr; val gap = sr / 3; val secB = sr
        val signal = FloatArray(secA + gap + secB)
        for (i in 0 until secA) {
            signal[i] = (0.35 * sin(2.0 * PI * f0 * i / sr)).toFloat()
        }
        // gap stays ~0 (below release/onset thresholds)
        var phase = 0.0
        for (i in 0 until secB) {
            phase += 2.0 * PI * fB / sr
            signal[secA + gap + i] = (0.35 * sin(phase)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()

        val p1 = monitor.levels.value.firstOrNull()
        assertTrue(p1?.measuredHz != null, "fundamental should be measured")
        assertTrue(
            // Slightly wider than before: since each strike re-seeds the
            // model (I41), the first frames of the new strike carry a fresh
            // fit rather than a carried-over one.
            kotlin.math.abs(p1!!.measuredHz!! - fB) < 0.25,
            "after re-strike measured ${p1.measuredHz}, expected $fB (old was $f0)",
        )
    }

    @Test
    fun lockPreventsAutoRetarget() {
        // Same E3 signal that autoDetectIdentifiesPlayedNote proves will
        // retarget an unlocked monitor — with the lock engaged it must not.
        val sr = 48_000
        val ref = 440.0
        val f0 = EqualTemperament.frequencyOf(52, ref) // E3 played

        val n = sr * 2
        val signal = FloatArray(n) { i ->
            val t = i.toDouble()
            (0.4 * sin(2.0 * PI * f0 * t / sr) +
                0.15 * sin(2.0 * PI * 2 * f0 * t / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.select(69) // tuner locked A4
        monitor.setLocked(true)
        monitor.start()

        assertTrue(
            monitor.selectedMidi.value == 69,
            "locked note changed to ${monitor.selectedMidi.value}",
        )
    }

    @Test
    fun twoStringUnisonFlagsMultiString() {
        // Partial 1 carries TWO components 0.7 Hz apart (a beating unison);
        // partial 2 is a single clean component. The detector must flag
        // exactly the beating partial.
        val sr = 48_000
        val ref = 440.0
        val midi = 57 // A3
        val f0 = EqualTemperament.frequencyOf(midi, ref)

        val n = (sr * 3.5).toInt()
        val signal = FloatArray(n) { i ->
            val t = i.toDouble()
            (0.22 * sin(2.0 * PI * f0 * t / sr) +
                0.22 * sin(2.0 * PI * (f0 + 0.7) * t / sr) +
                0.18 * sin(2.0 * PI * 2 * f0 * t / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()

        val levels = monitor.levels.value
        assertTrue(levels[0].multiString, "beating partial 1 not flagged")
        assertTrue(!levels[1].multiString, "clean partial 2 falsely flagged")
    }

    @Test
    fun singleStringStaysUnflagged() {
        val sr = 48_000
        val ref = 440.0
        val midi = 57
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val n = (sr * 3.5).toInt()
        val signal = FloatArray(n) { i ->
            val t = i.toDouble()
            (0.35 * sin(2.0 * PI * f0 * t / sr) +
                0.15 * sin(2.0 * PI * 2 * f0 * t / sr)).toFloat()
        }
        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()

        val levels = monitor.levels.value
        assertTrue(!levels[0].multiString, "single string falsely flagged")
        assertTrue(!levels[1].multiString, "single partial 2 falsely flagged")
    }

    @Test
    fun stiffStringPartialsMeasuredAndCentered() {
        // A single C4 string with realistic inharmonicity B = 3e-4.
        // Partial 6 sits ~8 Hz sharp and partial 8 ~20 Hz sharp of their
        // HARMONIC targets — beyond the fine-stage capture range, i.e.
        // exactly the case that previously wrapped and drew bells on the
        // wrong (left) side. All partials must now be measured correctly
        // and sit within 1 cent of their model-predicted positions.
        val sr = 48_000
        val ref = 440.0
        val midi = 60 // C4
        val f1 = EqualTemperament.frequencyOf(midi, ref)
        val bTrue = 3.0e-4

        val n = (sr * 3.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..8) {
            val fk = k * f1 * kotlin.math.sqrt(1.0 + bTrue * k * k)
            val amp = 0.30 / k
            for (i in 0 until n) {
                signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
            }
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()

        val model = monitor.stringModel.value
        assertTrue(model != null, "no string model fitted")
        assertTrue(
            kotlin.math.abs(model!!.b - bTrue) < 5e-5,
            "B fitted ${model.b}, expected $bTrue",
        )
        assertTrue(
            kotlin.math.abs(model.f0Hz - f1 * kotlin.math.sqrt(1.0 + bTrue)) < 0.5,
            "f1 fitted ${model.f0Hz}",
        )

        val levels = monitor.levels.value
        // The synthetic signal carries partials 1..8 only; higher indices
        // are legitimately absent now that the monitor reaches 16.
        for (p in levels.filter { it.index <= 8 }) {
            val m = p.measuredHz
            assertTrue(m != null, "partial ${p.index} not measured")
            val trueFk = p.index * f1 * kotlin.math.sqrt(1.0 + bTrue * p.index * p.index)
            assertTrue(
                kotlin.math.abs(m!! - trueFk) < 0.3,
                "partial ${p.index} measured $m, true $trueFk",
            )
            // Displayed deviation = residual against the fitted model
            val residualCents = Notes.centsOff(m, p.frequencyHz)
            assertTrue(
                kotlin.math.abs(residualCents) < 1.0,
                "partial ${p.index} residual $residualCents cents (should centre)",
            )
        }
    }

    @Test
    fun modelHoldsWhenToneDecays() {
        // Field bug: with the tone decayed the fit ran on noise and B
        // wandered (3.5e-4 -> 5.6e-4 -> 3.2e-4 across one recording).
        // After a strike decays into noise, B must hold its fitted value.
        val sr = 48_000
        val ref = 440.0
        val midi = 60
        val f1 = EqualTemperament.frequencyOf(midi, ref)
        val bTrue = 3.0e-4
        val rng = kotlin.random.Random(7)

        val tone = (sr * 2.5).toInt()
        val quiet = (sr * 2.0).toInt()
        val signal = FloatArray(tone + quiet)
        for (k in 1..8) {
            val fk = k * f1 * kotlin.math.sqrt(1.0 + bTrue * k * k)
            val amp = 0.30 / k
            for (i in 0 until tone) {
                signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
            }
        }
        for (i in signal.indices) {
            signal[i] += ((rng.nextDouble() - 0.5) * 4e-4).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start() // runs through tone AND the quiet tail

        val model = monitor.stringModel.value
        assertTrue(model != null, "model lost")
        assertTrue(
            kotlin.math.abs(model!!.b - bTrue) < 1e-4,
            "B drifted to ${model.b} after decay (expected ~$bTrue)",
        )
    }

    @Test
    fun capturedReferenceSurvivesUnison() {
        // Capture the model from a single string, then sound a unison whose
        // second string is 3 cents sharp. The reference must not move, and
        // the displayed deviation must reflect the unison error rather than
        // being absorbed into a re-fitted model.
        val sr = 48_000
        val ref = 440.0
        val midi = 60
        val f1 = EqualTemperament.frequencyOf(midi, ref)
        val bTrue = 3.0e-4

        fun stiff(k: Int, detuneCents: Double = 0.0) =
            k * f1 * kotlin.math.sqrt(1.0 + bTrue * k * k) *
                kotlin.math.exp(detuneCents / 1731.234)

        // Phase 1: single string
        val single = FloatArray((sr * 2.5).toInt())
        for (k in 1..6) {
            val fk = stiff(k)
            for (i in single.indices) {
                single[i] += (0.30 / k * sin(2.0 * PI * fk * i / sr)).toFloat()
            }
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, single), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()
        monitor.captureReference()

        val captured = monitor.referenceModel.value
        assertTrue(captured != null, "no reference captured")
        assertTrue(
            kotlin.math.abs(captured!!.b - bTrue) < 1e-4,
            "captured B ${captured.b}",
        )

        // Phase 2: unison, second string +3 cents on every partial
        val unison = FloatArray((sr * 3.0).toInt())
        for (k in 1..6) {
            val fa = stiff(k)
            val fb = stiff(k, 3.0)
            for (i in unison.indices) {
                unison[i] += (0.15 / k * sin(2.0 * PI * fa * i / sr)).toFloat()
                unison[i] += (0.15 / k * sin(2.0 * PI * fb * i / sr)).toFloat()
            }
        }
        val monitor2 = PartialMonitor(FakeAudioSource(sr, unison), tuning)
        monitor2.setAutoDetect(false)
        monitor2.select(midi)
        monitor2.clearReference()
        // transplant the captured reference
        monitor2.setReferenceForTest(captured)
        monitor2.start()

        assertTrue(
            monitor2.referenceModel.value === captured,
            "reference model was replaced during unison",
        )
        val p1 = monitor2.levels.value.first()
        assertTrue(p1.measuredHz != null, "no measurement during unison")
        // Two equal components +-1.5 cents about the mean => measured near
        // the midpoint, i.e. ~1.5 cents sharp of the reference string.
        val dev = Notes.centsOff(p1.measuredHz!!, p1.frequencyHz)
        assertTrue(
            dev > 0.5,
            "unison error not visible against the captured reference (dev $dev cents)",
        )
    }

    @Test
    fun referenceDeltaMatchesKnownDetuning() {
        // Reproduces the A3 field protocol: capture a reference string, then
        // sound a string detuned by a known amount and read the departure.
        val sr = 48_000
        val ref = 443.2
        val midi = 57 // A3
        val bTrue = 2.4e-4
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val detuneCents = 6.0
        val ratio = kotlin.math.exp(detuneCents / 1200.0 * kotlin.math.ln(2.0))

        fun stiffTone(scale: Double, seconds: Double): FloatArray {
            val n = (sr * seconds).toInt()
            val out = FloatArray(n)
            for (k in 1..6) {
                val fk = k * f0 * scale * kotlin.math.sqrt(1.0 + bTrue * k * k)
                val amp = 0.30 / k
                for (i in 0 until n) out[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
            }
            return out
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)

        // Reference string
        val m1 = PartialMonitor(FakeAudioSource(sr, stiffTone(1.0, 2.5)), tuning)
        m1.setAutoDetect(false); m1.select(midi); m1.start()
        val refModel = m1.stringModel.value
        assertTrue(refModel != null, "no model for reference string")

        // Detuned string, same reference model applied
        val m2 = PartialMonitor(FakeAudioSource(sr, stiffTone(ratio, 2.5)), tuning)
        m2.setAutoDetect(false); m2.select(midi)
        m2.setReferenceForTest(refModel!!)
        m2.start()

        val delta = m2.refDeltaCents.value
        val beat = m2.refBeatHz.value
        assertTrue(delta != null && beat != null, "no reference delta published")
        assertTrue(
            kotlin.math.abs(delta!! - detuneCents) < 0.5,
            "delta $delta cents, expected $detuneCents",
        )
        // 6 cents at ~221 Hz is ~0.77 Hz, as measured in the field
        assertTrue(
            kotlin.math.abs(beat!! - 0.77) < 0.15,
            "beat $beat Hz, expected ~0.77",
        )
    }

    @Test
    fun tunabilityPrefersSustainOverLoudness() {
        // Partial 2 starts loud and dies fast; partial 4 is quieter but
        // sustains. Level alone would pick 2. Only 4 can actually be tuned.
        val sr = 48_000
        val ref = 440.0
        val midi = 57 // A3
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val n = (sr * 3.0).toInt()

        val signal = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val p1 = 0.30 * kotlin.math.exp(-t / 3.0) * sin(2.0 * PI * f0 * i / sr)
            val p2 = 0.45 * kotlin.math.exp(-t / 0.12) * sin(2.0 * PI * 2 * f0 * i / sr)
            val p4 = 0.12 * kotlin.math.exp(-t / 3.0) * sin(2.0 * PI * 4 * f0 * i / sr)
            signal[i] = (p1 + p2 + p4).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()

        val tunable = monitor.tunablePartials.value
        assertTrue(4 in tunable, "sustaining partial 4 should be tunable, got $tunable")
        assertTrue(2 !in tunable, "fast-decaying partial 2 should not be tunable, got $tunable")
        assertTrue(
            monitor.recommendedPartial.value == 4,
            "recommended ${monitor.recommendedPartial.value}, expected the highest sustaining partial 4",
        )
    }

    @Test
    fun usablePartialsAreTheIntersectionAcrossStrings() {
        val sr = 48_000
        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        val monitor = PartialMonitor(FakeAudioSource(sr, FloatArray(0)), tuning)
        val model = at.clavierhaus.unisonmaster.dsp.StringModel(220.0, 2.4e-4)

        fun cap(slot: at.clavierhaus.unisonmaster.model.StringSlot, tunable: Set<Int>) {
            monitor.captureStringForTest(slot, model, tunable)
        }
        // Left sustains 1..10, centre 1..8, right 1..10 — but 9 and 10 die on
        // the centre string, so they cannot be used to compare the unison.
        cap(at.clavierhaus.unisonmaster.model.StringSlot.LEFT, (1..10).toSet())
        cap(at.clavierhaus.unisonmaster.model.StringSlot.CENTER, (1..8).toSet())
        cap(at.clavierhaus.unisonmaster.model.StringSlot.RIGHT, (1..10).toSet())

        val usable = monitor.usablePartials.value
        assertTrue(usable == (1..8).toSet(), "usable was $usable, expected 1..8")
        assertTrue(9 !in usable && 10 !in usable)
    }

    @Test
    fun armedSlotCapturesItselfOnAGoodReading() {
        // Arming listens; a settled string is captured without further input,
        // and the slot disarms.
        val sr = 48_000
        val ref = 443.2
        val midi = 57
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val bTrue = 2.4e-4
        // THREE strikes with gaps: a capture requires three agreeing
        // readings, one per strike, so fewer must not be enough.
        val burst = (sr * 2.2).toInt()
        val gap = (sr * 0.6).toInt()
        val n = burst * 3 + gap * 2
        val signal = FloatArray(n)
        fun strike(offset: Int) {
            for (k in 1..8) {
                val fk = k * f0 * kotlin.math.sqrt(1.0 + bTrue * k * k)
                val amp = 0.30 / k
                for (i in 0 until burst) {
                    signal[offset + i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
                }
            }
        }
        strike(0)
        strike(burst + gap)
        strike(2 * (burst + gap))

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        // Arming also starts listening; with the synchronous fake source the
        // whole signal is consumed inside this call.
        monitor.armString(at.clavierhaus.unisonmaster.model.StringSlot.CENTER)

        assertTrue(
            monitor.captured.value.containsKey(at.clavierhaus.unisonmaster.model.StringSlot.CENTER),
            "armed slot did not capture itself",
        )
        assertTrue(monitor.armedSlot.value == null, "slot should disarm after capturing")
    }

    @Test
    fun beatRateMeasuredFromTwoSoundingStrings() {
        // The tuning situation: two strings sounding together, components far
        // too close to separate spectrally. The beat must be measured from
        // the envelope, and must match the known frequency difference.
        val sr = 48_000
        val ref = 443.0
        val midi = 57 // A3
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val deltaHz = 0.9 // beat at the fundamental

        val n = (sr * 9.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..6) {
            val amp = 0.28 / k
            val fk = k * f0
            for (i in 0 until n) {
                val t = i.toDouble() / sr
                // two strings: the second detuned so partial k beats at k*delta
                val a = amp * sin(2.0 * PI * fk * i / sr)
                val b = amp * sin(2.0 * PI * (fk + k * deltaHz) * i / sr)
                signal[i] += ((a + b) * kotlin.math.exp(-t / 12.0)).toFloat()
            }
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()

        val beats = monitor.measuredBeatHz.value
        assertTrue(beats.isNotEmpty(), "no beat measured at all")
        val b1 = beats[1]
        assertTrue(b1 != null, "no beat at partial 1; measured ${beats.keys}")
        assertTrue(
            kotlin.math.abs(b1!! - deltaHz) < 0.25,
            "partial 1 beat $b1, expected ~$deltaHz",
        )
        // The whole point: the beat grows with the partial number. Asserted
        // unconditionally — a guarded version passes vacuously when the
        // estimate is missing, which is exactly when it should fail.
        val b2 = beats[2]
        assertTrue(b2 != null, "no beat at partial 2; measured ${beats.keys}")
        assertTrue(
            kotlin.math.abs(b2!! - 2 * deltaHz) < 0.35,
            "partial 2 beat $b2, expected ~${2 * deltaHz} (octave error reports ~$deltaHz)",
        )
    }

    @Test
    fun implausibleInharmonicityMarksASlotSuspect() {
        // Reproduces a field capture: two strings of a unison at B = 2.5e-4
        // and a third recorded at 7.5e-4, which cannot be the same unison.
        val sr = 48_000
        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        val monitor = PartialMonitor(FakeAudioSource(sr, FloatArray(0)), tuning)
        val good = at.clavierhaus.unisonmaster.dsp.StringModel(220.0, 2.5e-4)
        val bad = at.clavierhaus.unisonmaster.dsp.StringModel(220.0, 7.5e-4)
        monitor.captureStringForTest(StringSlot.LEFT, good, (1..8).toSet())
        monitor.captureStringForTest(StringSlot.CENTER, good, (1..8).toSet())
        monitor.captureStringForTest(StringSlot.RIGHT, bad, (1..8).toSet())

        val suspect = monitor.suspectSlots.value
        assertTrue(StringSlot.RIGHT in suspect, "outlier not flagged; suspect = $suspect")
        assertTrue(StringSlot.LEFT !in suspect && StringSlot.CENTER !in suspect,
            "consistent strings wrongly flagged; suspect = $suspect")
    }

    @Test
    fun oneStrikeIsNotEnoughToCapture() {
        // A single strike must never capture: one reading can be an accident
        // (a mute in contact, a glancing blow) and there is nothing to check
        // it against. Two agreeing readings are required.
        val sr = 48_000
        val ref = 443.2
        val midi = 57
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val bTrue = 2.4e-4
        val burst = (sr * 2.2).toInt()
        val gap = (sr * 0.6).toInt()
        val n = burst + gap
        val signal = FloatArray(n)
        fun strike(offset: Int) {
            for (k in 1..8) {
                val fk = k * f0 * kotlin.math.sqrt(1.0 + bTrue * k * k)
                val amp = 0.30 / k
                for (i in 0 until burst) {
                    signal[offset + i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
                }
            }
        }
        strike(0)

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.armString(StringSlot.CENTER)

        assertTrue(
            !monitor.captured.value.containsKey(StringSlot.CENTER),
            "captured on a single strike; two agreeing readings are required",
        )
        assertTrue(monitor.armedSlot.value == StringSlot.CENTER, "should still be armed")
    }

    @Test
    fun armingClearsThePreviousStringsState() {
        // Remnants of an earlier measurement must not leak into the next
        // string: arming wipes the per-partial state.
        val sr = 48_000
        val ref = 443.2
        val midi = 57
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val n = (sr * 2.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..6) {
            val amp = 0.30 / k
            for (i in 0 until n) signal[i] += (amp * sin(2.0 * PI * k * f0 * i / sr)).toFloat()
        }
        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()
        assertTrue(monitor.levels.value.isNotEmpty(), "expected a measurement to exist")

        monitor.armString(StringSlot.LEFT)
        // start() inside armString replays the signal, so disarm first to
        // observe the wipe itself.
        monitor.armString(null)
        monitor.resetSampling()
        assertTrue(monitor.levels.value.isEmpty(), "levels survived a reset")
        assertTrue(monitor.stringModel.value == null, "model survived a reset")
        assertTrue(monitor.measuredBeatHz.value.isEmpty(), "beats survived a reset")
        assertTrue(monitor.usablePartials.value.isEmpty(), "usable partials survived a reset")
        assertTrue(monitor.captured.value.isEmpty(), "captures survived a reset")
    }

    @Test
    fun recoversFromACorruptedModel() {
        // The field failure: a model with a wildly wrong B points the filters
        // at wrong frequencies, so nothing refits and the wrong model is held
        // forever. A clean string must break the app out of that state.
        val sr = 48_000
        val ref = 442.2
        val midi = 57 // A3
        val bTrue = 2.5e-4
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        // A string a little out of tune — exactly the operator's description.
        val f0Actual = f0 * 0.995 // ~9 cents flat

        val n = (sr * 6.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..8) {
            val fk = k * f0Actual * kotlin.math.sqrt(1.0 + bTrue * k * k)
            val amp = 0.30 / k
            for (i in 0 until n) signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        // Corrupt the model the way the field failure did: B six times too
        // large and the fundamental far off.
        monitor.setModelForTest(at.clavierhaus.unisonmaster.dsp.StringModel(217.0, 1.6e-3))
        monitor.start()

        val model = monitor.stringModel.value
        assertTrue(model != null, "no model after recovery")
        assertTrue(
            kotlin.math.abs(model!!.b - bTrue) < 1.5e-4,
            "B did not recover: ${model.b} (started corrupted at 1.6e-3)",
        )
        val measuredF1 = model.partialHz(1)
        val trueF1 = f0Actual * kotlin.math.sqrt(1.0 + bTrue)
        assertTrue(
            kotlin.math.abs(Notes.centsOff(measuredF1, trueF1)) < 3.0,
            "f1 did not recover: $measuredF1 vs $trueF1",
        )
    }

    @Test
    fun aDetunedStringMeasuresTheSameWithOrWithoutAReference() {
        // The field failure: left and centre sampled correctly, the third
        // string did not — because a captured reference was being used as the
        // targeting model, so every later string was measured at the
        // reference string's partial frequencies. Sampling must be identical
        // for all three slots.
        val sr = 48_000
        val ref = 442.2
        val midi = 57 // A3
        val bTrue = 2.5e-4
        val inTune = EqualTemperament.frequencyOf(midi, ref)
        val detuned = inTune * 1.0087 // ~15 cents sharp, "a little out of tune"

        fun tone(f0: Double): FloatArray {
            val n = (sr * 3.0).toInt()
            val out = FloatArray(n)
            for (k in 1..8) {
                val fk = k * f0 * kotlin.math.sqrt(1.0 + bTrue * k * k)
                val amp = 0.30 / k
                for (i in 0 until n) out[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
            }
            return out
        }

        fun measure(withReference: Boolean): StringModel {
            val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
            tuning.setReference(ref)
            val m = PartialMonitor(FakeAudioSource(sr, tone(detuned)), tuning)
            m.setAutoDetect(false)
            m.select(midi)
            if (withReference) {
                // A reference captured from the IN-TUNE string, as would
                // exist while sampling the third string of a unison.
                m.captureStringForTest(
                    StringSlot.CENTER,
                    at.clavierhaus.unisonmaster.dsp.StringModel(inTune, bTrue),
                    (1..8).toSet(),
                )
            }
            m.start()
            return m.stringModel.value ?: error("no model measured")
        }

        val without = measure(false)
        val with = measure(true)

        val trueF1 = detuned * kotlin.math.sqrt(1.0 + bTrue)
        for ((label, model) in listOf("without ref" to without, "with ref" to with)) {
            assertTrue(
                kotlin.math.abs(Notes.centsOff(model.partialHz(1), trueF1)) < 2.0,
                "$label: f1 ${model.partialHz(1)} should be ~$trueF1",
            )
            assertTrue(
                kotlin.math.abs(model.b - bTrue) < 1.0e-4,
                "$label: B ${model.b} should be ~$bTrue",
            )
        }
        // And the two must agree with each other: presence of a reference
        // must make no difference whatsoever to the measurement.
        assertTrue(
            kotlin.math.abs(Notes.centsOff(with.partialHz(1), without.partialHz(1))) < 1.0,
            "a captured reference changed the measurement: ${with.partialHz(1)} vs ${without.partialHz(1)}",
        )
    }

    @Test
    fun theCentreStringIsAlwaysTheReference() {
        // A temperament is laid with the outer strings felted, so the centre
        // string is tuned by ear and is by definition where the note belongs.
        // It is the reference regardless of measurement, and it is never the
        // string being adjusted.
        val sr = 48_000
        val ref = 442.3
        val midi = 57
        val bTrue = 2.5e-4
        val target = EqualTemperament.frequencyOf(midi, ref)

        fun model(centsOff: Double) = StringModel(
            target * kotlin.math.exp(centsOff / 1200.0 * kotlin.math.ln(2.0)),
            bTrue,
        )

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, FloatArray(0)), tuning)
        monitor.select(midi)

        // RIGHT sits closest to equal temperament; CENTER does not. The
        // centre string is the reference all the same.
        monitor.captureStringForTest(StringSlot.LEFT, model(-14.0), (1..8).toSet())
        monitor.captureStringForTest(StringSlot.CENTER, model(+9.0), (1..8).toSet())
        monitor.captureStringForTest(StringSlot.RIGHT, model(+1.5), (1..8).toSet())

        assertTrue(
            monitor.referenceSlot.value == StringSlot.CENTER,
            "reference went to ${monitor.referenceSlot.value}; the centre string is the reference",
        )
    }

    @Test
    fun theSoundingStringIsIdentifiedAtAHighPartial() {
        // The symptom: at the partials this tool exists to work on, the
        // sounding string matched no sampled mark, so no mark moved. A
        // string 20 cents out at partial 5 must still be recognised.
        val sr = 48_000
        val ref = 442.3
        val midi = 57 // A3
        val bTrue = 2.5e-4
        val focus = 5
        val target = EqualTemperament.frequencyOf(midi, ref)

        fun model(centsOff: Double) = StringModel(
            target * kotlin.math.exp(centsOff / 1200.0 * kotlin.math.ln(2.0)),
            bTrue,
        )
        // In tune (becomes the reference), and one 20 cents sharp.
        val refModel = model(0.0)
        val outModel = model(20.0)

        // Sound the out-of-tune string.
        val n = (sr * 3.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..8) {
            val fk = outModel.partialHz(k)
            val amp = 0.30 / k
            for (i in 0 until n) signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.captureStringForTest(StringSlot.CENTER, refModel, (1..8).toSet())
        monitor.captureStringForTest(StringSlot.LEFT, outModel, (1..8).toSet())
        monitor.captureStringForTest(StringSlot.RIGHT, model(-18.0), (1..8).toSet())
        monitor.selectOnlyPartial(focus)
        monitor.start()

        assertTrue(
            monitor.liveSlot.value == StringSlot.LEFT,
            "sounding string identified as ${monitor.liveSlot.value}; expected LEFT",
        )
        assertTrue(
            monitor.referenceSlot.value != monitor.liveSlot.value,
            "the reference must never be the string being adjusted",
        )
    }

    @Test
    fun aFitOfTheWrongNoteIsRejected() {
        // Observed in the field: 387.6 Hz reported while measuring A3, with
        // a residual of 0.3 cents. A fit can be internally consistent and
        // still be of the wrong thing, so the residual cannot catch it.
        val sr = 48_000
        val ref = 443.2
        val midi = 57 // A3, ~221 Hz
        val bTrue = 2.5e-4
        // Sound something a fifth above — the app must not adopt it as A3.
        val wrong = EqualTemperament.frequencyOf(midi + 7, ref)

        val n = (sr * 3.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..8) {
            val fk = k * wrong * kotlin.math.sqrt(1.0 + bTrue * k * k)
            val amp = 0.30 / k
            for (i in 0 until n) signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false) // note stays A3
        monitor.select(midi)
        monitor.start()

        val model = monitor.stringModel.value
        val target = EqualTemperament.frequencyOf(midi, ref)
        if (model != null) {
            assertTrue(
                kotlin.math.abs(Notes.centsOff(model.partialHz(1), target)) < 200.0,
                "adopted a fit ${model.partialHz(1)} Hz far from the note being measured",
            )
        }
    }

    @Test
    fun fastBeatsAreCountableAtTheArmedPartial() {
        // A string 10 cents out beats far above the 5.9 /s ceiling that one
        // envelope reading per hop allows. At the armed partial the envelope
        // is read four times per hop, so such beats stay countable.
        val sr = 48_000
        val ref = 443.0
        val midi = 57 // A3
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val focus = 5
        val bTrue = 2.5e-4
        // Two full strings; detuning chosen so the beat at the armed partial
        // is 9 /s — above the 5.9 /s ceiling of one reading per hop, where it
        // would alias to about 2.7 and read as a nearly-tuned string.
        val deltaF0 = 9.0 / focus
        val beatHz = deltaF0 * focus

        val n = (sr * 8.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..8) {
            val amp = 0.30 / k
            val fa = k * f0 * kotlin.math.sqrt(1.0 + bTrue * k * k)
            val fb = k * (f0 + deltaF0) * kotlin.math.sqrt(1.0 + bTrue * k * k)
            for (i in 0 until n) {
                signal[i] += (amp * (sin(2.0 * PI * fa * i / sr) + sin(2.0 * PI * fb * i / sr))).toFloat()
            }
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.selectOnlyPartial(focus)
        monitor.start()

        val measured = monitor.measuredBeatHz.value[focus]
        assertTrue(measured != null, "no beat measured at the armed partial")
        assertTrue(
            kotlin.math.abs(measured!! - beatHz) < 1.2,
            "beat measured $measured, expected ~$beatHz (coarse ceiling would cap near 5.9)",
        )
    }

    @Test
    fun theFundamentalQualifiesAsTunable() {
        // The fundamental is the strongest and longest-sustaining partial of
        // a struck string. If it fails the usability test, the test is wrong.
        val sr = 48_000
        val ref = 442.4
        val midi = 57 // A3
        val bTrue = 2.5e-4
        val f0 = EqualTemperament.frequencyOf(midi, ref)

        val n = (sr * 5.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..8) {
            val fk = k * f0 * kotlin.math.sqrt(1.0 + bTrue * k * k)
            // Upper partials decay faster, as on a real string.
            val tau = 6.0 / k
            val amp = 0.30 / k
            for (i in 0 until n) {
                val t = i.toDouble() / sr
                signal[i] += (amp * kotlin.math.exp(-t / tau) * sin(2.0 * PI * fk * i / sr)).toFloat()
            }
            // False beats: a real string has two polarisation modes at
            // slightly different frequencies, most audible at the low
            // partials. This is a property of every real string and must not
            // disqualify a partial from being tuned on.
            val fFalse = fk + 0.35
            for (i in 0 until n) {
                val t = i.toDouble() / sr
                signal[i] += (0.5 * amp * kotlin.math.exp(-t / tau) * sin(2.0 * PI * fFalse * i / sr)).toFloat()
            }
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.start()

        val tunable = monitor.tunablePartials.value
        val sustain = monitor.sustainSeconds.value
        assertTrue(
            1 in tunable,
            "fundamental not tunable. tunable=$tunable sustain[0]=${sustain.getOrNull(0)}",
        )
    }

    @Test
    fun silenceBetweenStrikesDoesNotShrinkTheUsableSet() {
        // Arming spans the operator's whole working period, most of which is
        // silence between strikes. Partials must be judged on the frames in
        // which the string was actually sounding.
        val sr = 48_000
        val ref = 442.4
        val midi = 57
        val bTrue = 2.5e-4
        val f0 = EqualTemperament.frequencyOf(midi, ref)

        val ring = (sr * 2.0).toInt()
        val quiet = (sr * 4.0).toInt() // long pauses, as when handling mutes
        val n = (ring + quiet) * 3
        val signal = FloatArray(n)
        for (strike in 0 until 3) {
            val off = strike * (ring + quiet)
            for (k in 1..8) {
                val fk = k * f0 * kotlin.math.sqrt(1.0 + bTrue * k * k)
                val tau = 6.0 / k
                val amp = 0.30 / k
                for (i in 0 until ring) {
                    val t = i.toDouble() / sr
                    signal[off + i] += (amp * kotlin.math.exp(-t / tau) *
                        sin(2.0 * PI * fk * i / sr)).toFloat()
                }
            }
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.armString(StringSlot.CENTER)

        val cap = monitor.captured.value[StringSlot.CENTER]
        assertTrue(
            cap != null,
            "not captured across three strikes; blows=${monitor.armBlows.value}",
        )
        assertTrue(
            1 in cap!!.tunable,
            "fundamental missing from the captured set: ${cap.tunable}",
        )
        assertTrue(
            cap.tunable.size >= 3,
            "only ${cap.tunable.size} partials survived: ${cap.tunable}",
        )
    }

    @Test
    fun autoDetectSurvivesOctaveFlicker() {
        // Real piano tone with a weak fundamental makes a lag-domain
        // estimator flip between the note and its octave. A rule requiring
        // two CONSECUTIVE agreeing detections never completes under that
        // flicker, and the app sits on its previous note indefinitely.
        val sr = 48_000
        val ref = 443.2
        val played = 69 // A4
        val stale = 60  // C4
        val bTrue = 4.0e-4
        val f0 = EqualTemperament.frequencyOf(played, ref)

        val n = (sr * 5.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..10) {
            val fk = k * f0 * kotlin.math.sqrt(1.0 + bTrue * k * k)
            // Weak fundamental, strong upper partials — as on many pianos,
            // and the condition that provokes octave errors.
            val amp = if (k == 1) 0.04 else 0.30 / k
            for (i in 0 until n) signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.select(stale)
        monitor.start()

        assertTrue(
            monitor.selectedMidi.value == played,
            "note stayed at ${Notes.name(monitor.selectedMidi.value)}; expected ${Notes.name(played)}",
        )
    }

    @Test
    fun autoDetectMovesFromAStaleNoteToTheNotePlayed() {
        // Field failure: the app sat on its default note while a different
        // note was played, so every measurement was of the wrong target.
        val sr = 48_000
        val ref = 443.2
        val played = 69 // A4, what the operator strikes
        val stale = 60  // C4, where the app was sitting
        val bTrue = 4.0e-4
        val f0 = EqualTemperament.frequencyOf(played, ref)

        val n = (sr * 4.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..8) {
            val fk = k * f0 * kotlin.math.sqrt(1.0 + bTrue * k * k)
            val amp = 0.30 / k
            for (i in 0 until n) signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.select(stale)
        monitor.start()

        assertTrue(
            monitor.selectedMidi.value == played,
            "note stayed at ${Notes.name(monitor.selectedMidi.value)}; expected ${Notes.name(played)}",
        )
    }

    @Test
    fun aTunedStringIsStillRecognisedAsItself() {
        // Field failure: once one outer string had been tuned in toward the
        // reference, it sounded far from its own captured mark, so its sound
        // was attributed to the other outer string — which then moved on its
        // neighbour's strikes while its own were ignored.
        val sr = 48_000
        val ref = 443.0
        val midi = 57 // A3
        val bTrue = 2.3e-4
        val focus = 4
        val base = EqualTemperament.frequencyOf(midi, ref)

        fun model(centsOff: Double) = StringModel(
            base * kotlin.math.exp(centsOff / 1200.0 * kotlin.math.ln(2.0)),
            bTrue,
        )
        val centre = model(0.0)
        val leftCaptured = model(+18.0)   // as sampled
        val rightCaptured = model(-18.0)  // as sampled
        val leftNow = model(+0.5)         // left has since been tuned in

        // Sound the LEFT string at its new, nearly-tuned position.
        val n = (sr * 3.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..8) {
            val amp = 0.30 / k
            val fk = leftNow.partialHz(k)
            for (i in 0 until n) signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.captureStringForTest(StringSlot.CENTER, centre, (1..8).toSet())
        monitor.captureStringForTest(StringSlot.LEFT, leftCaptured, (1..8).toSet())
        monitor.captureStringForTest(StringSlot.RIGHT, rightCaptured, (1..8).toSet())
        monitor.setCurrentModelForTest(StringSlot.LEFT, leftNow)
        monitor.selectOnlyPartial(focus)
        monitor.start()

        assertTrue(
            monitor.liveSlot.value == StringSlot.LEFT,
            "sounding string identified as ${monitor.liveSlot.value}; it is LEFT, already tuned in",
        )
    }

    @Test
    fun headlineFiguresBelongToTheArmedPartial() {
        // Field failure: the display showed partial 10 while the headline
        // reported the fundamental, understating the beat tenfold — the
        // number the operator is listening to is the one at the armed
        // partial, not at the fundamental.
        val sr = 48_000
        val ref = 443.1
        val midi = 57 // A3
        val bTrue = 2.9e-4
        val focus = 10
        val base = EqualTemperament.frequencyOf(midi, ref)

        fun model(centsOff: Double) = StringModel(
            base * kotlin.math.exp(centsOff / 1200.0 * kotlin.math.ln(2.0)),
            bTrue,
        )
        val centre = model(0.0)
        val outer = model(16.0) // the field value

        val n = (sr * 4.0).toInt()
        val signal = FloatArray(n)
        for (k in 1..12) {
            val amp = 0.30 / k
            val fk = outer.partialHz(k)
            for (i in 0 until n) signal[i] += (amp * sin(2.0 * PI * fk * i / sr)).toFloat()
        }

        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        tuning.setReference(ref)
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning)
        monitor.setAutoDetect(false)
        monitor.select(midi)
        monitor.captureStringForTest(StringSlot.CENTER, centre, (1..12).toSet())
        monitor.captureStringForTest(StringSlot.LEFT, outer, (1..12).toSet())
        monitor.captureStringForTest(StringSlot.RIGHT, model(-15.0), (1..12).toSet())
        monitor.selectOnlyPartial(focus)
        monitor.start()

        val beat = monitor.armedBeatHz.value
        val delta = monitor.armedDeltaCents.value
        assertTrue(beat != null && delta != null, "no figures published for the armed partial")
        // 16 cents at partial 10 of A3 is about 20 Hz, not the 2 Hz it is at
        // the fundamental.
        val expected = kotlin.math.abs(outer.partialHz(focus) - centre.partialHz(focus))
        assertTrue(
            kotlin.math.abs(beat!! - expected) < 1.0,
            "beat $beat at partial $focus, expected ~$expected",
        )
        assertTrue(beat > 10.0, "beat $beat looks like a fundamental figure, not partial $focus")
    }

    @Test
    fun attachedTapReceivesEveryBuffer() {
        val sr = 48_000
        val signal = FloatArray(sr) { i -> (0.3 * sin(2.0 * PI * 440.0 * i / sr)).toFloat() }
        val tuning = TuningController(FakeAudioSource(sr, FloatArray(0)))
        val monitor = PartialMonitor(FakeAudioSource(sr, signal), tuning, hopSize = 4096)
        var samples = 0
        var buffers = 0
        monitor.addTap { b -> samples += b.size; buffers++ }
        assertTrue(monitor.sampleRateHz == sr, "sample rate not exposed")
        monitor.start()
        assertTrue(buffers == sr / 4096, "expected ${sr / 4096} buffers, got $buffers")
        assertTrue(samples == (sr / 4096) * 4096, "expected every sample, got $samples")
    }
}
