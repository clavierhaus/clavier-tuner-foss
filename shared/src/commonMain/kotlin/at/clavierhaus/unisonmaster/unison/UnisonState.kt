package at.clavierhaus.unisonmaster.unison

import at.clavierhaus.unisonmaster.dsp.StringModel
import at.clavierhaus.unisonmaster.model.CapturedString
import at.clavierhaus.unisonmaster.model.StringSlot
import at.clavierhaus.unisonmaster.tuning.Notes
import kotlin.math.abs

/**
 * # Component: unison state
 *
 * **What this is.** The arithmetic of a three-string unison: given the set of
 * strings that have been captured, which partials may be compared across
 * them, which string holds the reference, and how far apart the captures sit
 * at each partial. Nothing else.
 *
 * **What this is not.** It does not measure, listen, hold state, or publish
 * flows. Every function here is pure: the same inputs give the same outputs,
 * with no audio, no coroutines and no clock. [at.clavierhaus.unisonmaster.PartialMonitor]
 * owns the state and the flows and calls into this file for the decisions.
 *
 * **Why it exists as its own file.** Every measurement fault recorded from
 * I39 onward has been in this arithmetic — the reference landing on the wrong
 * string (I38, I44), the offered partial set being computed from the wrong
 * evidence (I41, I47), figures taken at the wrong partial (I46). All of it
 * lived inside an 1818-line class next to the audio loop, so reproducing any
 * of it needed a `TuningController` and a fake audio source, and a superseded
 * rule was able to sit inline a hundred lines below its replacement without
 * anybody noticing (I49). Extracted here, each decision is a function with
 * arguments, and a fault in it is a five-line test.
 *
 * **Policy lives with the caller.** Thresholds are passed in rather than
 * declared here, so this file states *how* a quantity is computed and
 * `PartialMonitor` remains the single place where *what value* is chosen —
 * which is where ENGINEERING.md documents them.
 */

/**
 * Everything derivable from the captured strings alone.
 *
 * Returned as one value rather than four, because the four are computed from
 * the same inputs in one pass and must never disagree with each other: a
 * partial listed in [missingOn] but absent from [candidates] would be a chip
 * the display could not draw.
 */
data class UnisonSets(
    /**
     * Partials that may be compared across the strings — the ones the row
     * offers as workable.
     */
    val usable: Set<Int>,
    /**
     * Every partial any captured string can offer. Superset of [usable]:
     * partials in here but not in [usable] are shown greyed rather than
     * hidden, so that a partial the operator expected to see is accounted
     * for rather than silently absent (I47).
     */
    val candidates: Set<Int>,
    /**
     * For each greyed partial, the strings that lack it — the attribution
     * behind the "10 ✕ R" label.
     */
    val missingOn: Map<Int, Set<StringSlot>>,
    /**
     * Widest disagreement in hertz between any two captured models at each
     * usable partial. A snapshot of the unison **as it was captured**, not
     * as it currently sounds.
     */
    val capturedBeatsHz: Map<Int, Double>,
    /**
     * Strings whose fitted inharmonicity is far from the median of the three.
     * Requires three captures: with two, one of them is wrong and nothing in
     * the data says which.
     */
    val suspectSlots: Set<StringSlot>,
)

object UnisonState {

    /**
     * Derives every set the display needs from the captures.
     *
     * @param captured the strings frozen so far, keyed by slot.
     * @param liveTunable the partials currently usable from live audio. Used
     *   only as a fallback before enough strings have been captured to answer
     *   the question from the captures themselves.
     * @param slotCount how many strings make a complete unison (three).
     * @param suspectBFraction relative deviation from the median B above
     *   which a string is flagged.
     */
    fun sets(
        captured: Map<StringSlot, CapturedString>,
        liveTunable: Set<Int>,
        slotCount: Int,
        suspectBFraction: Double,
    ): UnisonSets {
        val suspect = if (captured.size < 3) emptySet() else {
            val all = captured.values.map { it.model.b }.sorted()
            val median = all[all.size / 2]
            captured.keys.filter { slot ->
                val mine = captured.getValue(slot).model.b
                val bigger = maxOf(mine, median)
                bigger > 0.0 && abs(mine - median) / bigger > suspectBFraction
            }.toSet()
        }

        // Behaviour preserved exactly from I48. The offered set falls back to
        // live audio until all three strings are captured, which is why the
        // partial row tracks the decay in the two-of-three state — the fault
        // seen on the field recording of 2026-09-01. Not corrected in this
        // extraction: structural commits carry no behaviour change. The fix
        // belongs in its own commit, with its own test, deployed on its own.
        val complete = captured.size == slotCount
        val usable = if (!complete) {
            liveTunable
        } else {
            captured.values.map { it.tunable }.reduce { acc, set -> acc intersect set }
        }

        val candidates = if (captured.isEmpty()) liveTunable
        else captured.values.map { it.tunable }.reduce { acc, set -> acc union set }

        val missingOn = candidates
            .filter { it !in usable }
            .associateWith { k -> captured.filterValues { k !in it.tunable }.keys }
            .filterValues { it.isNotEmpty() }

        val capturedBeats = if (captured.size < 2) emptyMap() else {
            val models = captured.values.map { it.model }
            usable.associateWith { k ->
                var worst = 0.0
                for (i in models.indices) for (j in i + 1 until models.size) {
                    val d = abs(models[i].partialHz(k) - models[j].partialHz(k))
                    if (d > worst) worst = d
                }
                worst
            }
        }

        return UnisonSets(usable, candidates, missingOn, capturedBeats, suspect)
    }

    /**
     * Which string holds the reference.
     *
     * The **centre** string, whenever it is captured. A temperament is laid
     * with a felt strip muting the outer strings, so the centre string of
     * each unison has been tuned by ear against the temperament and is by
     * definition where the note belongs; it is never touched during unison
     * work, and the outer strings are brought to it.
     *
     * Only when the centre string has not been captured does measurement
     * decide, and then the role goes to the captured string nearest equal
     * temperament — the one needing least movement.
     *
     * @param temperamentTargetHz where equal temperament puts this note,
     *   given the A4 measured at the start of the session.
     * @return the slot, or null when nothing is captured.
     */
    fun chooseReference(
        captured: Map<StringSlot, CapturedString>,
        temperamentTargetHz: Double,
    ): StringSlot? {
        if (captured.isEmpty()) return null
        if (captured.containsKey(StringSlot.CENTER)) return StringSlot.CENTER
        return captured.entries.minByOrNull {
            abs(Notes.centsOff(it.value.model.partialHz(1), temperamentTargetHz))
        }?.key
    }

    /**
     * Whether two settled readings describe the same string: pitch within
     * [agreeCents] and inharmonicity within [agreeBFraction].
     *
     * B is the more discriminating of the two. It is a property of the wire
     * and its terminations and barely moves with tuning, so a large
     * disagreement in B means the two readings were not of the same thing —
     * a neighbouring string, or a partial misidentified.
     */
    fun modelsAgree(
        a: StringModel,
        b: StringModel,
        agreeCents: Double,
        agreeBFraction: Double,
    ): Boolean {
        val cents = abs(Notes.centsOff(a.partialHz(1), b.partialHz(1)))
        if (cents > agreeCents) return false
        val bigger = maxOf(a.b, b.b)
        if (bigger <= 0.0) return true
        return abs(a.b - b.b) / bigger <= agreeBFraction
    }

    /** True when every reading in [models] agrees with every other. */
    fun allAgree(
        models: List<StringModel>,
        agreeCents: Double,
        agreeBFraction: Double,
    ): Boolean {
        for (i in models.indices) {
            for (j in i + 1 until models.size) {
                if (!modelsAgree(models[i], models[j], agreeCents, agreeBFraction)) return false
            }
        }
        return true
    }
}
