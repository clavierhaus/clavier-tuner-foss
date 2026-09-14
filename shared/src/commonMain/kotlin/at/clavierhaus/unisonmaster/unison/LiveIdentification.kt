package at.clavierhaus.unisonmaster.unison

import at.clavierhaus.unisonmaster.dsp.StringModel
import at.clavierhaus.unisonmaster.model.CapturedString
import at.clavierhaus.unisonmaster.model.StringSlot
import at.clavierhaus.unisonmaster.tuning.Notes
import kotlin.math.abs

/**
 * # Component: live identification
 *
 * **What this is.** Deciding which of the captured strings is the one
 * currently sounding, from a single frequency reading. Pure: models in, slot
 * out, no audio, no state, no clock.
 *
 * **What this is not.** It does not decide *whether* to act on the answer,
 * and it does not update anything. [at.clavierhaus.unisonmaster.PartialMonitor]
 * owns that.
 *
 * **Why it exists as its own file.** Because the rule it implements has a
 * failure mode that cannot be reproduced without it. Identification is by
 * proximity: the string whose present position is nearest the live reading.
 * That works while the strings are far apart and breaks down completely once
 * they are close, since being indistinguishable in position is precisely what
 * a finished unison *is*. Observed in the field on an untouched C4 — strikes
 * on the left string moved the centre string's mark while the left string's
 * mark stayed frozen — and never caught in forty-nine iterations, because a
 * detuned string is what anyone reaches for when testing identification.
 *
 * **The separation rule.** The nearest candidate is returned only when it is
 * nearer than the runner-up by more than the reading's own uncertainty. The
 * uncertainty is the live fit residual, which the analyzer already measures
 * and the status line already shows as `res` — so this is not a tuned
 * constant that would need confirming on a second instrument, but a
 * comparison of a separation against a measured error bar. When the two
 * cannot be told apart, the answer is *nothing*, and every mark stays where
 * it was captured. Attributing a strike to the wrong string is worse than
 * attributing it to none: a wrong attribution moves a mark that should be
 * still and freezes one that should move.
 *
 * **This is an interim.** Once the workflow states which pair is being
 * worked, the string being tuned is known rather than inferred, and this
 * question stops being asked after sampling completes.
 */
object LiveIdentification {

    /**
     * Which captured string the reading at [liveHz] belongs to, or null when
     * that cannot be decided.
     *
     * @param partial the partial [liveHz] was measured at.
     * @param captured the frozen strings.
     * @param currentModels where each string sits *now*, if it has moved
     *   since capture. A string tuned in toward the reference sounds far from
     *   its own captured mark, so matching on captures alone attributed its
     *   sound to its neighbour.
     * @param referenceSlot excluded from the answer: the reference is not
     *   worked on and its mark does not move.
     * @param maxCents beyond this distance a reading belongs to no captured
     *   string at all — a wrong note, or a knock.
     * @param uncertaintyCents the reading's own error bar, normally the live
     *   fit residual. The winner must beat the runner-up by more than this.
     */
    fun identify(
        liveHz: Double,
        partial: Int,
        captured: Map<StringSlot, CapturedString>,
        currentModels: Map<StringSlot, StringModel>,
        referenceSlot: StringSlot?,
        maxCents: Double,
        uncertaintyCents: Double,
    ): StringSlot? {
        val ranked = captured
            .filterKeys { it != referenceSlot }
            .map { (slot, cap) ->
                val here = currentModels[slot] ?: cap.model
                slot to abs(Notes.centsOff(liveHz, here.partialHz(partial)))
            }
            .filter { (_, d) -> d < maxCents }
            .sortedBy { (_, d) -> d }

        val best = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1) ?: return best.first
        // Indistinguishable candidates identify as nothing rather than as a
        // coin toss. See the component note above.
        if (runnerUp.second - best.second <= uncertaintyCents) return null
        return best.first
    }

    /**
     * The nearest captured string by absolute frequency, ignoring both the
     * distance limit and the separation rule.
     *
     * Used only to notice that the identity has drifted during continuous
     * playing, where no onset marks the change of string. Drift is counted
     * over several frames before it is acted on, so a momentary wrong answer
     * here is harmless — which is why this deliberately does not apply the
     * separation rule that [identify] does.
     */
    fun nearestByFrequency(
        liveHz: Double,
        partial: Int,
        captured: Map<StringSlot, CapturedString>,
        currentModels: Map<StringSlot, StringModel>,
        referenceSlot: StringSlot?,
    ): StringSlot? = captured
        .filterKeys { it != referenceSlot }
        .entries
        .minByOrNull {
            val here = currentModels[it.key] ?: it.value.model
            abs(here.partialHz(partial) - liveHz)
        }?.key
}
