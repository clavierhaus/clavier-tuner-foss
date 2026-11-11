package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import at.clavierhaus.unisonmaster.tuning.KeyIdentifier
import at.clavierhaus.unisonmaster.tuning.TuningSession
import kotlin.math.log10
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One string, alone, struck once and left to ring: which of its partials beat on their own (Peter, 10 Nov 2026: "a
 * separate tuning mode that is not sampling, but doing nothing else but measuring a single string and identify false
 * beats"; "One strike, yes"). What it reads: the microphone's buffers while the tuner checks a string. What it
 * measures of the string: its key, its f1 and B, and for every partial whether it was heard, how loud it stood, and
 * whether it beats against itself, at what rate and how deep.
 *
 * Nothing beside the engine's own parts (Amendment 3): the key is named by the [KeyIdentifier] on two windows after
 * the hammer's knock, alike; the string is measured by [StringMeasure] from its first [MEASURE_S], as sampling
 * measures it; every partial 1..[count] is then read by [CleanReading], the string alone, from the strike on.
 *
 * A false beat is claimed by the engine's own rule for a claim, the [FalseBeatTally], applied to the stretches of the
 * one strike instead of to three strikes: [CleanReading] measures the beat on every window and calls it by the ear's
 * line (rate 0.5-8 Hz, second plane within 10 dB); the strike is cut into stretches as long as one window
 * ([CleanReading.WINDOW_HOPS]), so no two stretches share a reading; and the partial is claimed when it was called in
 * [FalseBeatTally.STRIKES] separate stretches at one rate. One window's call is not enough: on the Bösendorfer's A3
 * centre string (16 Sept 2026) a call in any window marked partials 1 to 10 false on one strike, partial 1's slow wow
 * called in one burst at rates wandering from 1.01 to 0.59 Hz, while partials 3 and 4 were called window after window
 * at one rate, which is what a false beat does. On the three recordings of that string, three strikes together claim
 * partials 2 to 6; one strike alone claims 2, 3, 4 and 5 on the first and third and 2, 3 and 5 on the second, never
 * 1 and never anything three strikes do not (the method as first built on 15 Oct 2026, its recording test the
 * same). What one strike misses it misses on the safe side, and a partial that sounds for fewer stretches than the
 * tally needs cannot be claimed: the strike has to be left to ring. Such a partial is not judged ([Verdict.judged]),
 * and a strike that judged none says so: the Bösendorfer's A3 of 22 Sept, a take of four seconds, judged nothing,
 * where the screen had called it clean (10 Nov 2026). And one strike can miss what three catch: on the Samick's A4
 * left string the audible partial 3 (0.66-0.69 Hz, planes 3.5 dB apart) has a beat measured in 2, 1 and 2 stretches
 * of its three 8.7-second takes, so it is judged on none and claimed on none; the check says it was heard too briefly.
 * (Written first, before the measurement, as "judged on each take": the recording test said otherwise, 10 Nov 2026.)
 * What it says is a false beat found, none found on the partials judged, or too short to judge.
 *
 * Nothing is dropped silently (Rule 0): every partial's verdict is kept, the false ones named with their rate.
 */
class StringCheck(
    private val sampleRate: Int,
    private val a4Hz: () -> Double,
    val count: Int = Partials.MAX,
    /** The key identifier chooses from here up: A0. */
    private val lowMidi: Int = 21,
) {
    enum class Phase {
        /** Armed, waiting for the strike. */
        WAITING,
        /** Struck: the first seconds are kept until the key can be named and the string measured. */
        MEASURING,
        /** Named and measured: every partial is read until the string has died. */
        LISTENING,
        /** The string has died, or the next strike came: the verdicts stand. */
        DONE,
        /** The strike could not be named or measured; armed again for the next one. */
        UNNAMED,
    }

    data class Verdict(
        val k: Int,
        /** Shown by its reader at least once in the strike. */
        val heard: Boolean,
        /**
         * In how many stretches of the strike a beat was measured on it: a partial can be claimed only from
         * [FalseBeatTally.STRIKES] of them, so one measured in fewer was not judged, whatever it did.
         */
        val stretches: Int,
        val levelDbfs: Double,
        val falseBeat: Boolean,
        /** The beat's rate when it was claimed, Hz. */
        val rateHz: Double?,
        /** How far the second plane stood under the first when it was claimed, dB. */
        val depthDb: Double?,
    ) {
        /** Heard long enough that a false beat on it would have been claimed. */
        val judged: Boolean get() = stretches >= FalseBeatTally.STRIKES
    }

    data class State(
        val phase: Phase = Phase.WAITING,
        val midi: Int? = null,
        val f1Hz: Double? = null,
        val b: Double? = null,
        val verdicts: List<Verdict> = emptyList(),
        /** Partials shown at the latest hop. */
        val sounding: Int = 0,
        /** Seconds since the strike. */
        val seconds: Double = 0.0,
        /** The next strike came before the string had died: what it found it found in a shorter strike. */
        val cut: Boolean = false,
    ) {
        /** The partials claimed false, lowest first. */
        val falseBeats: List<FalseBeat> get() = verdicts.filter { it.heard && it.falseBeat && it.rateHz != null }
            .map { FalseBeat(it.k, it.rateHz!!, it.depthDb ?: 0.0) }
        val heard: List<Int> get() = verdicts.filter { it.heard }.map { it.k }
        /** The partials heard long enough to judge: "no false beat found" says nothing of the others. */
        val judged: List<Int> get() = verdicts.filter { it.heard && it.judged }.map { it.k }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var phase = Phase.WAITING
    private val recentDb = DoubleArray(5) { -200.0 }
    private var hop = 0L
    private var hopSamples = 0
    private var buffer = FloatArray(0)
    private var buffered = 0
    /** Samples since the strike; the first [MEASURE_S] of them are kept in [buffer], the rest only counted. */
    private var since = 0L
    private var reading: CleanReading? = null
    private var readHops = 0L
    private var midi: Int? = null
    private var f1: Double? = null
    private var b: Double? = null
    private val heard = BooleanArray(count + 1)
    private val level = DoubleArray(count + 1) { -200.0 }
    private val stretches = IntArray(count + 1)
    private val lastStretch = LongArray(count + 1) { -1L }
    private val tally = FalseBeatTally()
    private var sounding = 0
    private var cut = false
    private val detector by lazy { KeyIdentifier(sampleRate, IDENTIFY_SAMPLES, lowMidi, TuningSession.MIDI_C8) }
    /** The buffer whose strike ended the last check: the next check's first. */
    private var pending: FloatArray? = null

    /** Arms for the next strike: whatever an earlier one found is gone. */
    fun begin() {
        pending = null
        phase = Phase.WAITING
        recentDb.fill(-200.0); hop = 0
        buffer = FloatArray(0); buffered = 0; since = 0
        reading = null; readHops = 0
        midi = null; f1 = null; b = null
        heard.fill(false); level.fill(-200.0); stretches.fill(0); lastStretch.fill(-1L); tally.clear()
        sounding = 0; cut = false
        publish()
    }

    /**
     * After a check is done: armed again, and when a strike ended it, that strike is read as the next string's at
     * once. A tuner going through the piano strikes the next string before the last has died; armed afresh, the check
     * needed five quiet hops before it could hear a strike, and every second string was lost (the Steinway B, 10 Nov
     * 2026: 54 checks of 93 strings).
     */
    fun next() {
        val strike = pending
        begin()
        if (strike != null) { phase = Phase.MEASURING; since = 0; keep(strike); publish() }
    }

    /** One buffer of input, from the audio thread. */
    fun push(chunk: FloatArray) {
        if (hopSamples == 0) hopSamples = chunk.size
        hop++
        var ss = 0.0
        for (x in chunk) ss += x.toDouble() * x
        val db = 10 * log10(maxOf(ss / chunk.size, 1e-24))
        val loudest = recentDb.max()
        recentDb[(hop % recentDb.size).toInt()] = db
        val struck = hop > recentDb.size && db - loudest >= PhaseReader.ONSET_DB && db > SILENCE_DB
        when (phase) {
            Phase.WAITING, Phase.UNNAMED -> if (struck) { phase = Phase.MEASURING; since = 0; keep(chunk) }
            Phase.MEASURING -> {
                // a strike after the one that opened the check is the one sounding, and the check starts again on it
                // (11 Nov 2026: on the Steinway B 1886 a click of one hop, -68.7 dBFS, opened a check, the key was
                // looked for in the silence after it, and G4's strike 2.25 s later went unnamed with the kept seconds)
                if (struck && since > REPEAT_S * sampleRate) { buffered = 0; since = 0 }
                keep(chunk)
                if (buffered >= MEASURE_S * sampleRate) measureAndRead()
            }
            Phase.LISTENING -> {
                since += chunk.size
                if (struck && since > REPEAT_S * sampleRate) { cut = true; pending = chunk.copyOf(); finish(); return }
                read(chunk)
            }
            Phase.DONE -> return
        }
        if (hop % 4 == 0L) publish()
    }

    private fun keep(chunk: FloatArray) {
        since += chunk.size
        if (buffered + chunk.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, buffered + chunk.size, sampleRate * 4))
        chunk.copyInto(buffer, buffered)
        buffered += chunk.size
    }

    /** The key named twice alike after the knock, the string measured, the readers set on it and fed the strike so far. */
    private fun measureAndRead() {
        val key = name() ?: run { phase = Phase.UNNAMED; buffer = FloatArray(0); buffered = 0; publish(); return }
        val m = StringMeasure.measure(buffer.copyOfRange(0, buffered), sampleRate, key, TuningSession.targetF1(key, a4Hz()), timeMs = 0L)
            ?: run { phase = Phase.UNNAMED; buffer = FloatArray(0); buffered = 0; publish(); return }
        midi = key; f1 = m.f1Hz; b = m.b
        val targets = (1..count).associateWith { k -> m.partialHz(k) ?: (k * m.f1Hz * Inharmonicity.ratio(k, m.b)) }
        val r = CleanReading(sampleRate, count)
        r.target(targets, reset = true)
        r.strike()
        reading = r
        phase = Phase.LISTENING
        // the strike from its first hop, at the hop the input arrives in; the kept seconds are then let go
        var at = 0
        while (at + hopSamples <= buffered && phase == Phase.LISTENING) {
            read(buffer.copyOfRange(at, at + hopSamples))
            at += hopSamples
        }
        buffer = FloatArray(0)
    }

    /** The key, named the same on two windows after the hammer's knock, from [NAME_AFTER_S] to [NAME_UNTIL_S]. */
    private fun name(): Int? {
        var start = (NAME_AFTER_S * sampleRate).toInt()
        val until = (NAME_UNTIL_S * sampleRate).toInt()
        var last: Int? = null
        while (start + IDENTIFY_SAMPLES <= buffered && start <= until) {
            val key = detector.detectIn(buffer.copyOfRange(start, start + IDENTIFY_SAMPLES), a4Hz())
            if (key != null && key == last) return key
            last = key
            start += 2 * hopSamples
        }
        return null
    }

    private fun read(chunk: FloatArray) {
        val r = reading ?: return
        readHops++
        val res = r.push(chunk, alone = true)
        var shown = 0
        for (p in res.partials) {
            if (p.k > count) continue
            if (p.present) {
                shown++
                heard[p.k] = true
                if (p.levelDbfs > level[p.k]) level[p.k] = p.levelDbfs
            }
            // a window's call is evidence for the stretch it falls in; the tally keeps each stretch's best-fitting window
            val beat = p.beat
            val stretch = readHops / CleanReading.WINDOW_HOPS
            // counted as the tally counts: a stretch in which a beat was measured on the partial, called or not
            if (beat != null && lastStretch[p.k] != stretch) { lastStretch[p.k] = stretch; stretches[p.k]++ }
            if (p.beats && beat != null) tally.heard(p.k, stretch, beat.rateHz, beat.fit, beat.depthDb)
        }
        sounding = shown
        // the string has died: no partial shown within the reader's hold time, so none is left in the result
        if (res.partials.isEmpty() && heard.any { it }) finish()
    }

    private fun finish() {
        phase = Phase.DONE
        buffer = FloatArray(0)
        publish()
    }

    /** The state as of now, without waiting for the next publishing hop (a caller that stops feeding, such as a test at a recording's end). */
    fun publishNow() = publish()

    private fun publish() {
        val claims = tally.claims().associateBy { it.k }
        val verdicts = if (midi == null) emptyList() else (1..count).map { k ->
            val claim = claims[k]
            Verdict(k, heard[k], stretches[k], level[k], claim != null, claim?.rateHz, claim?.depthDb)
        }
        _state.value = State(phase, midi, f1, b, verdicts, sounding, since.toDouble() / sampleRate, cut)
    }

    companion object {
        /** The string is measured from this much of the strike: the ring sampling measures it from. */
        const val MEASURE_S = TuningController.RING_S
        /**
         * The one-strike reading's own times, as first built and tested on the recordings (15 Oct 2026): a hop this
         * quiet is no strike; a rise within [REPEAT_S] of the strike is the same strike; the key is named from
         * [NAME_AFTER_S] after the knock until [NAME_UNTIL_S], on windows of [IDENTIFY_SAMPLES].
         */
        const val SILENCE_DB = -70.0
        const val REPEAT_S = 0.3
        const val NAME_AFTER_S = 0.05
        const val NAME_UNTIL_S = 1.5
        const val IDENTIFY_SAMPLES = 16384
    }
}
