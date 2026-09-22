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
 * Nothing here finds a note, chooses a partial, or averages over a window:
 * the note and the partial are inputs, the reading is the phase.
 */
class PhaseReader(
    private val sampleRate: Int,
    targetHz: Double,
    private val bandCents: Double = DEFAULT_BAND_CENTS,
    private val memoryS: Double = DEFAULT_MEMORY_S,
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
        haveAngle = false; inBlock = 0; sinceRenorm = 0
        memoryFilled = false; last = null; powerFilled = false
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
            skipUntil = sample + settleSamples
        }

        var n = 0
        for (x in chunk) {
            val xd = x.toDouble()
            main.push(xd, alpha); below.push(xd, alpha); above.push(xd, alpha)
            sample++
            if (++sinceRenorm >= RENORMALISE_EVERY) {
                main.renormalise(sinceRenorm); below.renormalise(sinceRenorm); above.renormalise(sinceRenorm)
                sinceRenorm = 0
            }
            if (++inBlock >= block) {
                inBlock = 0
                val angle = atan2(main.q, main.i)
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
            // no fresh rate this hop: settling after a strike, or before the first block
            val held = last ?: return null
            return held.copy(levelDbfs = levelDbfs, noiseDbfs = noiseDbfs, quality = quality, settling = sample < skipUntil)
        }
        var sum = 0.0
        for (k in 0 until n) sum += rates[k]
        val mean = sum / n
        memory = if (memoryFilled) memory + memoryAlpha * (mean - memory) else mean
        memoryFilled = true
        val hz = targetHz + memory
        val cents = 1200.0 * ln(hz / targetHz) / ln(2.0)
        return Reading(hz, cents, levelDbfs, quality, atan2(main.q, main.i), settling = false, noiseDbfs = noiseDbfs)
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
        /** After a strike, rates are skipped for this many time constants of each filter stage, times the stages. */
        const val SETTLE_TIME_CONSTANTS = 5.0
        /** Where the noise is read: either side of the target, clear of the string, inside the semitone. */
        const val NEIGHBOUR_CENTS = 60.0
        /** A reading is shown when the partial stands this far above the noise beside it. */
        const val SHOWN_ABOVE_NEIGHBOURS_DB = 10.0
        private const val RATES_PER_HOP = 256
        private const val RENORMALISE_EVERY = 4096
    }
}
