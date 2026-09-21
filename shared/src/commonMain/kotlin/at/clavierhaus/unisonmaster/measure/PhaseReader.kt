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
 * frequency, and low-passed to ±[bandCents] around it — the bandpass of the
 * hardware (Sanderson Q > 10, Reyburn 50–200 cents) as a low-pass on the
 * baseband. What remains is the partial alone, as a slowly rotating
 * vector: its angle is the phase against the reference, its rate of
 * rotation the frequency error, its length the partial's level. The rate
 * is read over blocks short enough that a full band-width error cannot
 * wrap (±π per block).
 *
 * The reading is the rate with an exponential memory of [memoryS]
 * (Verituner's ~150 ms), the only smoothing there is, and a [Reading.quality]
 * from how steadily the blocks of the last hop agreed and how far the
 * partial stands above the floor: the display goes dark on a low quality
 * as the SAT's lamps go dark beyond the capture range. Nothing here finds
 * a note, chooses a partial, or averages over a window: the note and the
 * partial are inputs, the reading is the phase.
 */
class PhaseReader(
    private val sampleRate: Int,
    targetHz: Double,
    private val bandCents: Double = DEFAULT_BAND_CENTS,
    private val memoryS: Double = DEFAULT_MEMORY_S,
) {
    /** One hop's reading. [hz] is the partial's measured frequency, [cents] its distance from the target. */
    data class Reading(val hz: Double, val cents: Double, val levelDbfs: Double, val quality: Double, val phase: Double)

    var targetHz: Double = targetHz
        private set

    // the reference oscillator, as a phase accumulator (exact over hours)
    private var refPhase = 0.0
    private var refStep = 2 * PI * targetHz / sampleRate
    // the low-pass: four cascaded one-pole stages on the baseband (I and Q),
    // 24 dB per octave — a semitone away is some 50 dB down
    private var alpha = 0.0
    private val li = DoubleArray(STAGES); private val lq = DoubleArray(STAGES)
    // the rate is read over blocks of [block] samples: short enough that ±band cannot wrap
    private var block = 1
    private var lastAngle = 0.0
    private var haveAngle = false
    private var inBlock = 0
    private val rates = DoubleArray(64)
    private var rateCount = 0
    private var memory = 0.0
    private var memoryFilled = false
    private var memoryAlpha = 0.0

    init { retarget(targetHz) }

    /** Moves the reference to a new target (the next note, an override); the memory starts afresh. */
    fun retarget(hz: Double) {
        require(hz > 0)
        targetHz = hz
        refStep = 2 * PI * hz / sampleRate
        val bandHz = hz * (2.0.pow(bandCents / 1200.0) - 1)
        // four identical one-pole stages with their corner at the band's edge:
        // the cascade is 3 dB down at 0.44 of it and 54 dB down a semitone away
        alpha = 1 - exp(-2 * PI * bandHz / sampleRate)
        // a full-band error of bandHz must turn less than half a cycle per block,
        // and a block is never longer than a hop, so every hop yields a reading
        block = (sampleRate / (2.5 * bandHz)).toInt().coerceIn(8, MAX_BLOCK)
        li.fill(0.0); lq.fill(0.0)
        haveAngle = false; inBlock = 0; rateCount = 0
        memoryFilled = false
    }

    /**
     * One hop of samples in; the reading at its end, or null before the
     * first block has completed.
     */
    fun push(chunk: FloatArray): Reading? {
        rateCount = 0
        for (x in chunk) {
            // mix down: the target partial lands at zero frequency
            val c = cos(refPhase); val s = sin(refPhase)
            refPhase += refStep
            if (refPhase > 2 * PI) refPhase -= 2 * PI
            var i = x * c; var q = -x * s
            for (st in 0 until STAGES) {
                li[st] += alpha * (i - li[st]); lq[st] += alpha * (q - lq[st])
                i = li[st]; q = lq[st]
            }
            if (++inBlock >= block) {
                inBlock = 0
                val angle = atan2(lq[STAGES - 1], li[STAGES - 1])
                if (haveAngle) {
                    var d = angle - lastAngle
                    while (d > PI) d -= 2 * PI
                    while (d < -PI) d += 2 * PI
                    val hzError = d * sampleRate / (2 * PI * block)
                    if (rateCount < rates.size) rates[rateCount++] = hzError
                }
                lastAngle = angle
                haveAngle = true
            }
        }
        if (rateCount == 0) return null
        // the rate over this hop, and its steadiness
        var sum = 0.0
        for (k in 0 until rateCount) sum += rates[k]
        val mean = sum / rateCount
        var ss = 0.0
        for (k in 0 until rateCount) { val r = rates[k] - mean; ss += r * r }
        val spread = sqrt(ss / rateCount)
        // the memory: exponential over memoryS, restarted on retarget
        val hopS = chunk.size.toDouble() / sampleRate
        memoryAlpha = 1 - exp(-hopS / memoryS)
        memory = if (memoryFilled) memory + memoryAlpha * (mean - memory) else mean
        memoryFilled = true
        val hz = targetHz + memory
        val cents = 1200.0 * ln(hz / targetHz) / ln(2.0)
        val iOut = li[STAGES - 1]; val qOut = lq[STAGES - 1]
        val amplitude = sqrt(iOut * iOut + qOut * qOut) * 2  // the partial's amplitude, full scale = 1
        val levelDbfs = 20 * log10(maxOf(amplitude, 1e-9))
        // steadiness: the blocks of this hop agree to within a tenth of the band
        val bandHz = targetHz * (2.0.pow(bandCents / 1200.0) - 1)
        val steady = (1 - spread / (bandHz * 0.1)).coerceIn(0.0, 1.0)
        val loud = ((levelDbfs - FLOOR_DBFS) / 15.0).coerceIn(0.0, 1.0)
        return Reading(hz, cents, levelDbfs, steady * loud, atan2(qOut, iOut))
    }

    companion object {
        /** Half-width of the band around the target: 50 cents wide in all, Reyburn's narrowest; a semitone away is 49 dB down. */
        const val DEFAULT_BAND_CENTS = 25.0
        /** The longest block the rate is read over: 1024 samples, one hop at 48 kHz. */
        const val MAX_BLOCK = 1024
        /** Verituner's exponential memory. */
        const val DEFAULT_MEMORY_S = 0.15
        /**
         * A partial at this level or below is not read: the quality falls to
         * zero. The band is narrow, so the noise in it is far under the
         * phone's broadband floor: on the takes of 16 September the fourth
         * partial of A4 read steadily at −76 dBFS.
         */
        const val FLOOR_DBFS = -95.0
        const val STAGES = 4
    }
}
