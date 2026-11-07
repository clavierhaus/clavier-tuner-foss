package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.dsp.InharmonicityFit
import at.clavierhaus.unisonmaster.tuning.MeasuredPartial
import at.clavierhaus.unisonmaster.tuning.NoteMeasurement
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * What Done keeps of a string (docs/ENGINE.md): its partials, each found
 * where the string's model says it should be ([PartialFinder], ±60 cents)
 * and then read by phase over the strike ([PhaseReader], the median of the
 * shown readings); the model — fundamental and inharmonicity B, fitted
 * together, never assumed — refined as each partial comes in. The stored
 * partials are what the next notes are tuned against: a note an octave
 * below is tuned so that its partial meets one of these.
 *
 * On the Reference recordings of 22 September this found 11–12 partials
 * on every bass key and 5–7 in the treble, and targets taken from them
 * reproduced the operator's own tuning within 1–3 cents from F3 to C6.
 */
object StringMeasure {
    /**
     * Measures the string that sounds in [samples] (the last seconds of
     * input, a strike in them). [f1Guess] is where its first partial is
     * roughly (from the note and the reading); [bGuess] a start for B.
     * Null when too little of the string could be read.
     */
    fun measure(
        samples: FloatArray,
        sampleRate: Int,
        midi: Int,
        f1Guess: Double,
        bGuess: Double = DEFAULT_B,
        maxK: Int = Partials.MAX,
        timeMs: Long = 0L,
    ): NoteMeasurement? {
        val seg = decay(samples, sampleRate) ?: return null
        val finder = PartialFinder(seg, sampleRate)
        val found = LinkedHashMap<Int, Pair<Double, Double>>()   // k -> (hz, level dB)
        var f0 = f1Guess / sqrt(1 + bGuess)
        var b = bGuess
        for (k in 1..maxK) {
            val predicted = k * f0 * sqrt(1 + b * k * k)
            if (predicted > sampleRate * 0.45) break
            val peak = finder.near(predicted, SEARCH_CENTS) ?: continue
            val hz = readByPhase(seg, sampleRate, peak.hz) ?: continue
            found[k] = hz to peak.levelDb
            if (found.size >= 2) {
                InharmonicityFit.fit(found.map { (kk, v) -> kk to v.first })?.let {
                    if (it.b in 0.0..MAX_B && it.f0Hz > 0) { f0 = it.f0Hz; b = it.b }
                }
            } else if (k == 1) {
                f0 = hz / sqrt(1 + b)
            }
        }
        if (found.size < MIN_PARTIALS) return null
        val weights = found.values.map { 10.0.pow(it.second / 20) }
        val model = InharmonicityFit.fit(found.map { (k, v) -> k to v.first }, weights)
        if (model != null && model.b in 0.0..MAX_B) { f0 = model.f0Hz; b = model.b }
        val f1 = found[1]?.first ?: (f0 * sqrt(1 + b))
        val loudest = found.values.maxOf { it.second }
        val partials = found.map { (k, v) ->
            MeasuredPartial(k, 1200 * ln(v.first / (k * f1)) / ln(2.0), v.second - loudest, 0.0)
        }
        var ss = 0.0
        for ((k, v) in found) {
            val r = 1200 * ln(v.first / (k * f0 * sqrt(1 + b * k * k))) / ln(2.0)
            ss += r * r
        }
        return NoteMeasurement(midi, f1, b, sqrt(ss / found.size), partials, timeMs)
    }

    /** The phase reading at [hz] over [seg]: the median of the readings that were shown. */
    fun readByPhase(seg: FloatArray, sampleRate: Int, hz: Double, hop: Int = 1024): Double? {
        val reader = PhaseReader(sampleRate, hz)
        val values = ArrayList<Double>()
        val chunk = FloatArray(hop)
        var pos = 0
        while (pos + hop <= seg.size) {
            seg.copyInto(chunk, 0, pos, pos + hop)
            reader.push(chunk)?.let { if (it.shown) values += it.hz }
            pos += hop
        }
        if (values.size < MIN_READINGS) return null
        values.sort()
        return values[values.size / 2]
    }

    /**
     * The decay of the last strike in [samples]: from [SKIP_S] after the
     * last jump in level to at most [LENGTH_S] later. Null when too short.
     */
    fun decay(samples: FloatArray, sampleRate: Int, hop: Int = 1024): FloatArray? {
        val hops = samples.size / hop
        if (hops < 8) return null
        val db = DoubleArray(hops) { h ->
            var ss = 0.0
            for (i in h * hop until (h + 1) * hop) ss += samples[i].toDouble() * samples[i]
            10 * log10(maxOf(ss / hop, 1e-24))
        }
        var onset = 0
        for (h in PhaseReader.ONSET_LOOKBACK until hops) {
            var m = -300.0
            for (j in h - PhaseReader.ONSET_LOOKBACK until h) if (db[j] > m) m = db[j]
            if (db[h] - m >= PhaseReader.ONSET_DB) onset = h
        }
        val start = onset * hop + (SKIP_S * sampleRate).toInt()
        val end = minOf(samples.size, start + (LENGTH_S * sampleRate).toInt())
        if (end - start < (MIN_LENGTH_S * sampleRate).toInt()) {
            // no strike in view, or too recent: the whole buffer less its first hop
            if (samples.size - hop < (MIN_LENGTH_S * sampleRate).toInt()) return null
            return samples.copyOfRange(hop, samples.size)
        }
        return samples.copyOfRange(start, end)
    }

    /** A string of B within [0, MAX_B]: the stiffest piano wire known is far below. */
    const val MAX_B = 0.02
    const val DEFAULT_B = 4e-4
    const val SEARCH_CENTS = 60.0
    /** One is enough at the top of the treble, where the second partial is gone before it can be read: B is then the guess. */
    const val MIN_PARTIALS = 1
    const val MIN_READINGS = 5
    const val SKIP_S = 0.3
    const val LENGTH_S = 2.0
    const val MIN_LENGTH_S = 0.4

    /** The inharmonic ratio f_k / (k·f1) of a string with [b]. */
    fun ratio(k: Int, b: Double): Double = sqrt((1 + b * k * k) / (1 + b))

}
