package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import kotlin.math.abs

/**
 * The bass's octave type suggested by the sampling (Peter, 10 Nov 2026: "Aus der Messung der Inharmonizität sollte es
 * doch möglich sein, einen Oktavtyp vorzuschlagen, der dem Klavier angemessen ist ... ein erfahrener Stimmer wird sich
 * gegen unseren Vorschlag entscheiden und etwas anderes wählen"). What it reads: the sampled strings, each with its
 * partials measured by phase and their levels. What it measures of them: for every two sampled keys an octave apart in
 * the bass and every type the bass offers, the hardest beat that type would leave between them. The lower key is moved
 * until the type stands beatless, every partial keeping its measured place relative to the others; then every pair of
 * partials that meet in the octave (2:1, 4:2 ... 12:6) beats at the difference of the two, and the hardest of those whose
 * partials both stand within [LEVEL_DB] of their own string's loudest is the octave's. A type's figure is its hardest
 * over the octaves; the suggestion is the type whose figure is smallest. Nothing is modelled: the partials are the
 * sampling's, and a type whose two partials were not both measured on an octave is not weighed there.
 *
 * It is a suggestion beside the tuner's choice and never sets a type itself. The measure is the one that compared the
 * types on six pianos (Pro STRETCH §8, 30 Oct 2026): 6:3 leaves the typical interval cleanest, 10:5 leaves none
 * beating hard, and which a piano gets depends on how loud a partial must be before its beat counts. [LEVEL_DB] is that
 * level: 20 dB to start (Peter's word, 10 Nov 2026), the first ear on it a tester's on a Yamaha b2 tuned to 6:3, who
 * heard the bass "about 4 cents too high" where its partials 10 and 5, 16-18 dB under each string's loudest, beat
 * 2.3-5.3 Hz (Pro STRETCH §23); at 12 dB the same strings suggest 6:3 from E2 up.
 */
object OctaveSuggestion {
    /** A partial's beat counts when it stands within this of its own string's loudest, dB. */
    const val LEVEL_DB = 20.0

    /**
     * [type] suggested; [hardestHz] the hardest beat each type weighed would leave, over [octaves] octaves of sampled
     * keys; [worstAt] the octave (its lower key) where the suggested type leaves its hardest.
     */
    data class Suggestion(val type: OctaveType, val hardestHz: Map<OctaveType, Double>, val octaves: Int, val worstAt: Int)

    /**
     * The suggestion for the bass from [samples]: octaves whose lower key stands below [temperamentLowMidi], both keys
     * sampled. Null when no two sampled bass keys stand an octave apart, or no type could be weighed.
     */
    fun bass(
        samples: List<NoteMeasurement>,
        temperamentLowMidi: Int,
        types: List<OctaveType> = TunerSettings.BASS_OCTAVES,
        levelDb: Double = LEVEL_DB,
    ): Suggestion? {
        val by = samples.associateBy { it.midi }
        val pairs = by.keys.filter { it < temperamentLowMidi && (it + 12) in by }.sorted()
        if (pairs.isEmpty()) return null
        val hardest = HashMap<OctaveType, Double>()
        val where = HashMap<OctaveType, Int>()
        for (lowMidi in pairs) {
            val low = by.getValue(lowMidi); val high = by.getValue(lowMidi + 12)
            for (type in types) {
                val beat = hardestBeat(low, high, type, levelDb) ?: continue
                if (beat > (hardest[type] ?: -1.0)) { hardest[type] = beat; where[type] = lowMidi }
            }
        }
        val best = hardest.minByOrNull { it.value } ?: return null
        return Suggestion(best.key, hardest, pairs.size, where.getValue(best.key))
    }

    /** The hardest beat [type] leaves between [low] and [high], an octave apart; null when its two partials were not both measured. */
    fun hardestBeat(low: NoteMeasurement, high: NoteMeasurement, type: OctaveType, levelDb: Double = LEVEL_DB): Double? {
        val lowAt = low.partialHz(type.low) ?: return null
        val highAt = high.partialHz(type.high) ?: return null
        val moved = highAt / lowAt   // the lower string moved until the type stands beatless
        var worst = 0.0
        // every even partial the lower string has measured, against the upper's half of it: no partial count set here
        for (a in low.partials) {
            if (a.k % 2 != 0) continue
            val j = a.k / 2
            val b = high.partials.firstOrNull { it.k == j } ?: continue
            if (a.levelDb < -levelDb || b.levelDb < -levelDb) continue
            val beat = abs(low.partialHz(2 * j)!! * moved - high.partialHz(j)!!)
            if (beat > worst) worst = beat
        }
        return worst
    }
}
