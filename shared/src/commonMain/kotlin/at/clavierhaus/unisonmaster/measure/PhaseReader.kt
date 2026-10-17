package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The reading every tuning device has made for fifty years, in digital
 * form (docs/MEASUREMENT.md): one partial, band-limited, compared in
 * phase against a reference at the target frequency. The strobe disc and
 * Sanderson's LED ring show the phase of the input against the reference
 * and drift at the frequency difference; this does the same with numbers.
 *
 * How: the input is mixed with a synthesised reference at [targetHz]
 * (multiplied by e^(−iωt)), which moves the target partial to zero
 * frequency, and low-passed to ±[bandCents] around it. What remains is the
 * partial alone, as a slowly rotating vector: its angle is the phase
 * against the reference, its rate of rotation the frequency error, its
 * length the partial's level. The rate is read over blocks short enough
 * that a full band-width error cannot wrap (±π per block).
 *
 * The reading is the rate with an exponential memory of [memoryS]
 * (Verituner's ~150 ms), the only smoothing there is. Two things about a
 * piano string are handled, both measured on the tuning recordings of
 * 22 September (docs/ENGINE.md):
 *
 * - **A strike.** When the broadband level jumps ([ONSET_DB] above the
 *   loudest of the last five hops) the memory starts afresh and the block
 *   rates of the next [SETTLE_TIME_CONSTANTS] time constants of the filter
 *   are skipped: the old tone's phase and the filter's own step response
 *   are not the string. Meanwhile the last reading is held and marked
 *   [Reading.settling]. The skip follows from the band, so it is longer in
 *   the bass (narrow band) than in the treble — on the recordings the
 *   first reading after a strike came 190 ms later (median) and stood
 *   within 1.8 cents (median) of where the string settled.
 * - **Whether the partial is there.** The same band is demodulated
 *   [NEIGHBOUR_CENTS] either side of the target, where no string of the
 *   note sounds: that is the noise the reading stands in. The reading is
 *   shown when the partial stands [SHOWN_ABOVE_NEIGHBOURS_DB] above it
 *   ([Reading.quality] ≥ 0.5). On the recordings 92 % of shown readings
 *   agreed with their neighbours within a cent, of hidden ones 57 %.
 *
 * **The strobe's reader** ([window], 17 Oct 2026; Peter, 22:24: "build a strobe that comes up
 * reasonably fast, significantly faster than now"). The four stages give a rate only after the
 * twenty time constants of the strike's skip, 3.2 / bandHz s: on the corpus 1.07 s after the
 * strike on A0-B1's sixth partials (median), 0.64 s on the temperament octave's fundamentals.
 * With [window] the rate is read instead from a Blackman-Harris window over the last
 * [WINDOW_LOBES] / semitoneHz seconds of the demodulated partial: its main lobe ends at the
 * neighbouring key, and everything beyond lies under the window's -92 dB sidelobes. After a
 * strike the rates are read once the window holds nothing from before it, which is its own
 * length and no longer: 0.38 s on A0-B1, 0.23 s on the temperament octave, the same string once
 * settled (0.01 c median on 197 readings; SAMICK findings §27). A struck test tone is read
 * exactly from the first reading on, so what the window shows in a strike's first moments is
 * those moments, which the four stages wait out. The level, the noise beside the partial and
 * the presence gate stay the four stages' (Law 4). The window keeps the samples it has heard,
 * and a [retarget] reads them again at the new partial instead of starting afresh: the screen
 * follows a key 0.28 s after its strike, and the reading need not begin again there. The four
 * stages hear those samples again too, so the gate stands where it would have stood.
 *
 * Nothing here finds a note, chooses a partial, or averages over a window
 * of readings: the note and the partial are inputs, the reading is the phase.
 */
class PhaseReader(
    private val sampleRate: Int,
    targetHz: Double,
    private val bandCents: Double = DEFAULT_BAND_CENTS,
    private val memoryS: Double = DEFAULT_MEMORY_S,
    /** The rate from a finite window instead of the four stages: the strobe's reader (see above). */
    private val window: Boolean = false,
) {
    /**
     * One hop's reading. [hz] is the partial's measured frequency, [cents]
     * its distance from the target, [levelDbfs] the partial's level and
     * [noiseDbfs] the level beside it. [quality] is 0..1, ≥ 0.5 when the
     * reading is to be shown. [settling]: a strike was heard and the
     * reading is the last one from before it.
     */
    data class Reading(
        val hz: Double,
        val cents: Double,
        val levelDbfs: Double,
        val quality: Double,
        val phase: Double,
        val settling: Boolean = false,
        val noiseDbfs: Double = -200.0,
        /**
         * The partial's band power this hop, before the reading's memory: the demodulated
         * amplitude squared. Two planes of a false-beating string make it swing at their beat
         * rate -- a1² + a2² + 2·a1·a2·cos(2π·Δf·t) on the decay -- and CleanReading measures the
         * beat from that swing; the memory would smooth it (at 2 Hz by half) and the dB would
         * bend it, so the raw power is handed out beside the smoothed level (8 Oct 2026).
         */
        val rawPower: Double = 0.0,
    ) {
        val shown: Boolean get() = quality >= 0.5 && !settling
    }

    var targetHz: Double = targetHz
        private set

    /** One demodulator: a reference oscillator by rotation, and the cascaded low-pass. */
    private inner class Band {
        var re = 1.0; var im = 0.0            // e^(−iωt), advanced by rotation
        var stepRe = 1.0; var stepIm = 0.0
        var phase = 0.0                        // exact phase, to renormalise the rotation
        var step = 0.0
        val li = DoubleArray(STAGES); val lq = DoubleArray(STAGES)
        fun tune(hz: Double) {
            step = 2 * PI * hz / sampleRate
            stepRe = cos(step); stepIm = -sin(step)
            phase = 0.0; re = 1.0; im = 0.0
            li.fill(0.0); lq.fill(0.0)
        }
        fun push(x: Double, a: Double) {
            var i = x * re; var q = x * im
            for (st in 0 until STAGES) {
                li[st] += a * (i - li[st]); lq[st] += a * (q - lq[st])
                i = li[st]; q = lq[st]
            }
            val r = re * stepRe - im * stepIm
            im = re * stepIm + im * stepRe
            re = r
        }
        /** Exact again from the accumulated phase: rotation drifts over millions of samples. */
        fun renormalise(samples: Int) {
            phase = (phase + step * samples) % (2 * PI)
            re = cos(phase); im = -sin(phase)
        }
        val i get() = li[STAGES - 1]
        val q get() = lq[STAGES - 1]
        val amplitude get() = sqrt(i * i + q * q) * 2
    }

    private val main = Band()
    private val below = Band()
    private val above = Band()
    private var alpha = 0.0
    private var bandHz = 1.0
    private var block = 1
    private var settleSamples = 0L

    private var lastAngle = 0.0
    private var haveAngle = false
    private var inBlock = 0
    private var sinceRenorm = 0
    private var sample = 0L
    private var skipUntil = -1L

    // The window ([window] only): the samples heard, the partial demodulated against the reference
    // at their own absolute time, and the Blackman-Harris weights over the last [winN] of them.
    private var raw = FloatArray(0)
    private var zr = DoubleArray(0)
    private var zi = DoubleArray(0)
    private var ringHead = 0
    private var ringValid = 0
    private var winN = 0
    private var winW = DoubleArray(0)
    private var winStep = 0.0

    private val rates = DoubleArray(RATES_PER_HOP)
    private var memory = 0.0
    private var mainPower = 0.0
    private var noisePower = 0.0
    private var powerFilled = false
    private var memoryFilled = false
    private var last: Reading? = null
    private val recentDb = DoubleArray(ONSET_LOOKBACK) { -200.0 }
    private var hops = 0

    /** Hops in which a strike was heard, since this reader was made. */
    var strikes: Int = 0
        private set

    init { retarget(targetHz) }

    /** Moves the reference to a new target (the next note, an override); the memory starts afresh. */
    fun retarget(hz: Double) {
        require(hz > 0)
        targetHz = hz
        bandHz = hz * (2.0.pow(bandCents / 1200.0) - 1)
        // four identical one-pole stages with their corner at the band's edge:
        // 54 dB down a semitone away
        alpha = 1 - exp(-2 * PI * bandHz / sampleRate)
        // a full-band error must turn less than half a cycle per block, and a
        // block is never longer than a hop, so every hop yields a reading
        block = (sampleRate / (2.5 * bandHz)).toInt().coerceIn(8, MAX_BLOCK)
        val tau = 1.0 / (2 * PI * bandHz)
        settleSamples = (SETTLE_TIME_CONSTANTS * tau * STAGES * sampleRate).toLong()
        main.tune(hz)
        below.tune(hz * 2.0.pow(-NEIGHBOUR_CENTS / 1200.0))
        above.tune(hz * 2.0.pow(NEIGHBOUR_CENTS / 1200.0))
        if (window) windowOn(hz)
        haveAngle = false; inBlock = 0; sinceRenorm = 0
        memoryFilled = false; last = null; powerFilled = false
    }

    /**
     * The window at [hz]: as long as the neighbouring key needs to lie beyond its main lobe, the ring
     * grown to hold it if it must (keeping what it has heard), and what it has heard demodulated
     * again against the new reference -- the skip of the last strike still counts from the strike.
     */
    private fun windowOn(hz: Double) {
        // four lobes a semitone; held at MIN_WINDOW_S where that is shorter, as far as the lobe
        // still reaches twice the band the gate reads through (above 1.4 kHz the lobe would close
        // inside the band, and a string the gate shows would stand in the window's sidelobes)
        val semitoneS = WINDOW_LOBES / (hz * (SEMITONE - 1))
        val bandS = 2.0 / bandHz
        val n = (maxOf(semitoneS, minOf(MIN_WINDOW_S, bandS)) * sampleRate).toInt()
        if (n > raw.size) {
            val grown = FloatArray(n)
            for (j in 0 until ringValid) grown[n - ringValid + j] = raw[(ringHead - ringValid + j).mod(raw.size)]
            raw = grown; ringHead = 0
            zr = DoubleArray(n); zi = DoubleArray(n)
        }
        winN = n
        winW = DoubleArray(n) { m ->
            val x = 2 * PI * m / (n - 1)
            0.35875 - 0.48829 * cos(x) + 0.14128 * cos(2 * x) - 0.01168 * cos(3 * x)
        }
        winStep = 2 * PI * hz / sampleRate
        val size = raw.size
        for (j in 1..ringValid) {
            val at = (ringHead - j).mod(size)
            val ph = winStep * (sample - j).toDouble()
            zr[at] = raw[at] * cos(ph); zi[at] = -raw[at] * sin(ph)
        }
        // and the four stages, which keep the level and the gate, hear the same samples again at the
        // new partial: emptied by the move, they would hold the reading back while they fill
        for (j in ringValid downTo 1) {
            val x = raw[(ringHead - j).mod(size)].toDouble()
            main.push(x, alpha); below.push(x, alpha); above.push(x, alpha)
        }
        if (ringValid > 0) { main.renormalise(ringValid); below.renormalise(ringValid); above.renormalise(ringValid) }
    }

    /** The phase of the partial over the window's last [winN] samples. */
    private fun windowAngle(): Double {
        val size = raw.size
        val start = (ringHead - winN).mod(size)
        val first = minOf(winN, size - start)
        var ar = 0.0; var ai = 0.0
        for (m in 0 until first) { val j = start + m; ar += winW[m] * zr[j]; ai += winW[m] * zi[j] }
        for (m in first until winN) { val j = m - first; ar += winW[m] * zr[j]; ai += winW[m] * zi[j] }
        return atan2(ai, ar)
    }

    /**
     * One hop of samples in; the reading at its end, or null before the
     * first rate has been read (or right after a retarget).
     */
    fun push(chunk: FloatArray): Reading? {
        // a strike: the broadband level jumps above the loudest of the last hops
        var ss = 0.0
        for (x in chunk) ss += x.toDouble() * x
        val db = 10 * log10(maxOf(ss / chunk.size, 1e-24))
        var recentMax = -200.0
        for (v in recentDb) if (v > recentMax) recentMax = v
        val onset = hops >= ONSET_LOOKBACK && db - recentMax >= ONSET_DB
        recentDb[hops % ONSET_LOOKBACK] = db
        hops++
        if (onset) {
            strikes++
            memoryFilled = false
            // the window is read once it holds nothing from before the strike: this hop, in which
            // the strike falls, and the window's length after it
            skipUntil = sample + if (window) chunk.size + winN.toLong() else settleSamples
        }

        var n = 0
        for (x in chunk) {
            val xd = x.toDouble()
            main.push(xd, alpha); below.push(xd, alpha); above.push(xd, alpha)
            if (window) {
                val ph = winStep * sample.toDouble()
                raw[ringHead] = x; zr[ringHead] = xd * cos(ph); zi[ringHead] = -xd * sin(ph)
                ringHead = (ringHead + 1) % raw.size
                if (ringValid < raw.size) ringValid++
            }
            sample++
            if (++sinceRenorm >= RENORMALISE_EVERY) {
                main.renormalise(sinceRenorm); below.renormalise(sinceRenorm); above.renormalise(sinceRenorm)
                sinceRenorm = 0
            }
            if (++inBlock >= block) {
                inBlock = 0
                if (window && ringValid < winN) {
                    // the window not yet full of what was heard: no phase to read a rate between
                    haveAngle = false
                } else {
                    val angle = if (window) windowAngle() else atan2(main.q, main.i)
                    if (haveAngle && sample >= skipUntil) {
                        var d = angle - lastAngle
                        while (d > PI) d -= 2 * PI
                        while (d < -PI) d += 2 * PI
                        if (n < rates.size) rates[n++] = d * sampleRate / (2 * PI * block)
                    }
                    lastAngle = angle
                    haveAngle = true
                }
            }
        }

        // the partial's and the neighbours' power, with the reading's own memory:
        // noise alone stands 10 dB above its neighbours in one hop of twenty,
        // over a memory's worth of hops almost never
        val memoryAlpha = 1 - exp(-(chunk.size.toDouble() / sampleRate) / memoryS)
        val pMain = main.amplitude * main.amplitude
        val pNoise = 0.5 * (below.amplitude * below.amplitude + above.amplitude * above.amplitude)
        if (powerFilled && !onset) {
            mainPower += memoryAlpha * (pMain - mainPower)
            noisePower += memoryAlpha * (pNoise - noisePower)
        } else { mainPower = pMain; noisePower = pNoise; powerFilled = true }
        val levelDbfs = 10 * log10(maxOf(mainPower, 1e-24))
        val noiseDbfs = 10 * log10(maxOf(noisePower, 1e-24))
        val quality = ((levelDbfs - noiseDbfs) / (2 * SHOWN_ABOVE_NEIGHBOURS_DB)).coerceIn(0.0, 1.0)
        if (n == 0) {
            // no fresh rate this hop: settling after a strike, or before the first block. The
            // hop in which the skip ends may still hold no fresh rate; what is held then is the
            // reading from before the strike, and it is not the string now sounding (3 Oct 2026:
            // shown for one hop, it spread a clean partial's window to a cent)
            val held = last ?: return null
            return held.copy(levelDbfs = levelDbfs, noiseDbfs = noiseDbfs, quality = quality, settling = sample < skipUntil || !memoryFilled, rawPower = pMain)
        }
        var sum = 0.0
        for (k in 0 until n) sum += rates[k]
        val mean = sum / n
        memory = if (memoryFilled) memory + memoryAlpha * (mean - memory) else mean
        memoryFilled = true
        val hz = targetHz + memory
        val cents = 1200.0 * ln(hz / targetHz) / ln(2.0)
        return Reading(hz, cents, levelDbfs, quality, atan2(main.q, main.i), settling = false, noiseDbfs = noiseDbfs, rawPower = pMain)
            .also { last = it }
    }

    companion object {
        /** Half-width of the band around the target: 50 cents wide in all, Reyburn's narrowest; a semitone away is 54 dB down. */
        const val DEFAULT_BAND_CENTS = 25.0
        /** The longest block the rate is read over: 1024 samples, one hop at 48 kHz. */
        const val MAX_BLOCK = 1024
        /** Verituner's exponential memory. */
        const val DEFAULT_MEMORY_S = 0.15
        const val STAGES = 4
        /** A strike: the hop's level this far above the loudest of the last [ONSET_LOOKBACK] hops. */
        const val ONSET_DB = 6.0
        const val ONSET_LOOKBACK = 5
        /**
         * After a strike, rates are skipped for this many time constants of each filter stage,
         * times the stages: twenty in all, 3.2 / bandHz seconds -- 1.33 s at A0's sixth partial
         * (165 Hz, band 2.4 Hz), 0.5 s at 220 Hz, 0.2 s at 525 Hz. This is the strike-to-strobe
         * latency the tuner sees, measured on the Samick's unisons of 9 Oct 2026 as exactly this
         * number plus the memory (SAMICK findings §24: 1.37 s at A0, 0.60 at C2, 0.36 from G#2 up).
         * Halving it was tried that day: the first readings after the skip were then the
         * transient and not the string -- +27 c decaying to +2 over half a second on B1, −13 to
         * −1 on C2 -- a wrong number shown earlier in place of a right one later, which Amendment
         * 3 forbids. The latency is the price of the band's width in hertz at low frequencies,
         * and a change goes through the band, measured, not through this number.
         * Corrected 17 Oct 2026 (SAMICK findings §27): those first readings were the reader's
         * [retarget] when the walk's key was named, 0.28 s after the strike, emptying the filter
         * while the skip still counted from the strike; with the skip counted from the move as
         * well, half of it read B1 −0.3 c. The skip stays for the four stages; the strobe reads
         * through the window ([window]), whose skip is its length.
         */
        const val SETTLE_TIME_CONSTANTS = 5.0
        /**
         * The strobe's window is this many of its own frequency bins long per semitone: a
         * Blackman-Harris main lobe is four bins wide either side, so at four the lobe ends at the
         * neighbouring key, a semitone off -- 0.41 s at A0's sixth partial (165 Hz), 0.15 s at
         * 440 Hz, 0.02 s at C8 (held at [MIN_WINDOW_S]). Taken from the note, not chosen: shorter,
         * and the neighbouring key is inside the lobe (at three it reads 36 dB down instead of 124).
         */
        const val WINDOW_LOBES = 4.0
        /**
         * …and not shorter than this, seconds, about the limit below which a response is felt as
         * instant (Nielsen, Usability Engineering, 1993: 0.1 s), as long as its main lobe still
         * reaches twice the band's edge (2 / bandHz: shorter from 1.4 kHz up, 33 ms at C8). From
         * 670 Hz up four lobes a semitone would be shorter, and a shorter window only lets in more
         * noise: at C7, 32 ms, the D's C7 of 16 Sept scattered 4.3 c over its decay against 1.0 on
         * the four stages; at 65 ms, 0.8. Below 670 Hz it plays no part.
         */
        const val MIN_WINDOW_S = 0.1
        private val SEMITONE = 2.0.pow(1 / 12.0)
        /** Where the noise is read: either side of the target, clear of the string, inside the semitone. */
        const val NEIGHBOUR_CENTS = 60.0
        /** A reading is shown when the partial stands this far above the noise beside it. */
        const val SHOWN_ABOVE_NEIGHBOURS_DB = 10.0
        private const val RATES_PER_HOP = 256
        private const val RENORMALISE_EVERY = 4096
    }
}
