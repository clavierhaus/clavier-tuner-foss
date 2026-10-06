package at.clavierhaus.unisonmaster.picture

import at.clavierhaus.unisonmaster.tuning.TuningSession.Companion.centsOff
import kotlin.math.abs

/**
 * What the tuning screen shows, as plain data: the one place a number reaches
 * that screen from (docs/UI.md).
 *
 * It is built once per reading from the engine's own readings of the strike
 * now sounding — the listened partial against its target, every live partial
 * against its target, which partials the engine measured false — and from the
 * tuner's hand (the partials he dropped, Pro). It holds no measurement of its
 * own and dies with the sound: when the reading is not live the picture is
 * [State.SILENT] and empty. Nothing is remembered from an earlier strike.
 *
 * The bells stand on an **anchor** partial, not on a target: each bell's
 * offset is its own error minus the anchor's error, so on a clean string every
 * bell stands at zero wherever the pitch is (CLAUDE.md Rule 1), and only a
 * false or off-series partial leaves the line. The **scale** alone carries the
 * pitch, through [pointerCents].
 *
 * Each bell has a **width**: how well its partial is read, the spread of its
 * last half second of readings (3 Oct 2026). A loud partial is a needle; a
 * partial at the room's floor is a wide, low bell, and its wandering says
 * "I am quiet", not "the string moved". A bell much wider than the narrowest
 * — [Builder.WIDE_FACTOR] times it, and over [Builder.WIDE_CENTS] — is
 * *wide*: it does not anchor the line (a needle outranks a wide bell) and
 * does not veto "on". On the Samick's nine single-string F3 takes the loud
 * partials read 0.2–0.5 c wide and the floor-huggers 2–6 c. The picture does
 * not say *why* a bell is wide — the floor, a false string, three strings
 * struck together all widen it, and on the Samick session width and depth
 * over the floor do not tell them apart — so it claims nothing beyond the
 * width itself.
 *
 * The anchor must be a reading that is trusted: a partial standing less than
 * [Builder.ANCHOR_DEPTH_DB] over the noise beside it does not anchor the
 * line, however narrow its last half second — at the floor a reading wanders
 * slowly as well as fast (F3-C-s1 at 7.5 s: partial 4 at 10 dB over the floor
 * anchored every needle 1.5 c off the line, 3 Oct 2026 17:45), and the
 * depth is the same one [at.clavierhaus.unisonmaster.measure.CleanReading]
 * trusts a reading at.
 */
class TuningPicture(
    /** "F3" */
    val note: String,
    /** The partial being tuned. */
    val listened: Int,
    /** The partial the bells stand on: counted, steady, named on screen. */
    val anchor: Int,
    /** Every partial the strike shows over the noise, up to the engine's limit. */
    val bells: List<Bell>,
    /** The listened partial against its target, cents; null while nothing sounds. */
    val pointerCents: Double?,
    val state: State,
    /** Measured false by the engine on this strike: red-violet, filled. */
    val falseMarked: Set<Int>,
    /** Taken out of the reading by the tuner: red-violet, outlined, dimmed. */
    val dropped: Set<Int>,
    /** Bells read much less well than the narrowest: they neither anchor nor veto. */
    val wide: Set<Int> = emptySet(),
    /**
     * The bands drawn, left to right: auto mode's four, chosen by [autoFour] with its hysteresis.
     * The picture decides them once so that no screen works the choice out a second time
     * (Amendment 3: two places that can put a number on one screen means one of them is wrong).
     */
    val bands: List<Int> = emptyList(),
) {
    /**
     * One partial of the sounding string. [offsetCents] is its distance from
     * the anchor's reading; [errorCents] its own distance from its own target,
     * for the readout table; [level] its strength, 0..1 of the loudest;
     * [widthCents] how well it is read (null until known).
     */
    class Bell(
        val k: Int, val offsetCents: Double, val errorCents: Double, val level: Double, val counted: Boolean,
        /** As read and as asked, Hz, for the table beside the bells. */
        val hz: Double, val targetHz: Double,
        val widthCents: Double? = null,
        /** Over the noise beside it, dB, when known. */
        val overNoiseDb: Double? = null,
    )

    companion object {
        /**
         * The four bands auto mode draws (Peter, 6 Oct 2026): the fundamental, the
         * two next loudest partials, and — always included — the highest partial
         * that is both **well audible** and **well defined**.
         *
         * Defined is [Builder.ANCHOR_DEPTH_DB] over the noise beside it, the same
         * depth at which a reading is already trusted to anchor the line; a partial
         * whose depth is not known is not called defined. Audible is a level within
         * [AUDIBLE_LEVEL] of the loudest partial of the strike — about thirty
         * decibels down, which is the order at which a partial stops being heard
         * inside the note (a split 44 dB down was measured inaudible, 1 Oct 2026).
         *
         * Both figures are starting values on Peter's word of 6 Oct — "take mean
         * values that make sense and we will refine from there" — and are the
         * house's own numbers rather than new ones. The tuner is never held to this
         * choice: he goes to Measure and takes the partials he wants.
         *
         * Fewer than four partials over the floor yields fewer than four bands; it
         * never invents one.
         */
        fun autoFour(bells: List<Bell>, previous: Set<Int> = emptySet()): List<Int> {
            if (bells.isEmpty()) return emptyList()
            // A band already on screen keeps its seat until it falls a margin below the line it
            // entered at. Without that, a partial sitting on the threshold enters and leaves on
            // alternate frames and the whole row reflows mid-note — the eye loses its seats exactly
            // when it is trying to read motion (Mistral, 6 Oct 2026). The anchor has had its
            // hysteresis since 3 Oct; this is the same law for the band set.
            fun audibleEnough(b: Bell) =
                b.level >= (if (b.k in previous) AUDIBLE_LEVEL - HYSTERESIS_LEVEL else AUDIBLE_LEVEL)
            fun definedEnough(b: Bell) = (b.overNoiseDb ?: 0.0) >=
                (if (b.k in previous) Builder.ANCHOR_DEPTH_DB - HYSTERESIS_DB else Builder.ANCHOR_DEPTH_DB)
            val audible = bells.filter { audibleEnough(it) }
            if (audible.isEmpty()) return bells.map { it.k }.sorted().take(1)
            val highest = audible.filter { definedEnough(it) }.maxByOrNull { it.k }?.k
            val first = audible.minByOrNull { it.k }!!.k
            val rest = audible.filter { it.k != first && it.k != highest }
                .sortedByDescending { it.level }.take(if (highest == null) 3 else 2).map { it.k }
            return (listOfNotNull(first, highest) + rest).distinct().sorted()
        }

        /**
         * A partial quieter than this share of the loudest is not drawn a band of its own.
         *
         * The share is the engine's own level scale, `1 + (db − loudest) / LEVEL_RANGE_DB` with
         * `LEVEL_RANGE_DB = 48`, so **0.375 is exactly 30 dB below the loudest partial** — the order
         * at which a partial stops being heard inside the note (a split 44 dB down was measured
         * inaudible, 1 Oct 2026). It is not an amplitude ratio.
         */
        const val AUDIBLE_LEVEL = 0.375

        /** The margin a band already drawn keeps its seat by, in dB and in the level scale above. */
        const val HYSTERESIS_DB = 4.0
        const val HYSTERESIS_LEVEL = HYSTERESIS_DB / 48.0
    }

    enum class State {
        /** No reading: nothing is drawn but the note and its target. */
        SILENT,
        /** A string sounds and some counted partial stands outside the gate. */
        SOUNDING,
        /** Every counted partial inside the gate — not yet held. */
        NEAR,
        /** Held inside the gate for [Builder.HOLD_S]: the wash, the tick. */
        ON,
    }

    /** The engine's readings of one moment, as the builder takes them. */
    class Frame(
        val note: String,
        val listened: Int,
        /** True while the engine has a live reading of the string. */
        val live: Boolean,
        /** The listened partial's reading, Hz, when live. */
        val listenedHz: Double?,
        /** The listened partial's target, Hz. */
        val listenedTargetHz: Double,
        /** Every live partial, by k. */
        val partials: Map<Int, Partial>,
        /** Every partial's target, Hz, by k. */
        val targets: Map<Int, Double>,
        /** Partials the engine measured false on this strike. */
        val falsePartials: Set<Int>,
        /** Partials the tuner took out of the reading. */
        val dropped: Set<Int>,
        /** The match window, cents. */
        val matchCents: Double,
        /** The clock, ms. */
        val nowMs: Long,
    ) {
        /** One live partial: as read, its level 0..1 of the loudest, its width and its depth over the noise when known. */
        class Partial(val hz: Double, val level: Double, val widthCents: Double? = null, val overNoiseDb: Double? = null)
    }

    /**
     * Builds the picture frame by frame, keeping only what a hold needs: when
     * the gate was first held and which anchor is waiting to take over; both
     * are forgotten the moment the sound stops. The anchor itself is not: it
     * belongs to the note and is dropped when the note changes (5 Oct 2026).
     */
    class Builder {
        private var onSinceMs: Long? = null
        private var pendingAnchor: Int? = null
        private var pendingSinceMs: Long = 0L
        private var anchor: Int? = null
        private var anchorNote: String? = null
        private var bands: List<Int> = emptyList()

        fun build(f: Frame): TuningPicture {
            // The anchor belongs to the note, not to the strike. It used to be forgotten the moment
            // the sound stopped, so every blow chose a line to stand on afresh, and on a clean string —
            // where the partials sit within a cent or two of one another and the choice is therefore
            // decided by noise — it changed from strike to strike: Peter's recording of a clean G#4
            // (5 Oct 2026, 15:47) reads "bells on 3", then "bells on 2", then "bells on 3" again, and
            // since every bell's offset is measured from the anchor, the whole picture slid sideways
            // each time while nothing but its reference had moved. The anchor now stands as long as
            // the note does. It is a choice of what to measure against and not a reading, so it does
            // not outlive its sound in the sense Rule 3 forbids: nothing of the old strike is shown,
            // the picture is empty while the string is silent, and the gate's hold is still dropped.
            if (f.note != anchorNote) { anchorNote = f.note; anchor = null; pendingAnchor = null; bands = emptyList() }
            if (!f.live || f.listenedHz == null) {
                onSinceMs = null; pendingAnchor = null
                return TuningPicture(f.note, f.listened, anchor ?: f.listened, emptyList(), null, State.SILENT, emptySet(), emptySet())
            }
            val errors = HashMap<Int, Double>()
            for ((k, p) in f.partials) {
                val t = f.targets[k] ?: continue
                errors[k] = centsOff(p.hz, t)
            }
            val counted = errors.keys.filter { it !in f.falsePartials && it !in f.dropped }.toSet()
            // wide: read much less well than the narrowest counted bell (an unknown width is not wide)
            val narrowest = counted.mapNotNull { f.partials[it]!!.widthCents }.minOrNull()
            val wide = if (narrowest == null) emptySet() else counted.filter { k ->
                val w = f.partials[k]!!.widthCents; w != null && w > maxOf(WIDE_FACTOR * narrowest, WIDE_CENTS)
            }.toSet()
            // a line to stand on: counted, not wide, and standing well over the noise beside it
            val sharp = (counted - wide).filter { k -> f.partials[k]!!.overNoiseDb?.let { it >= ANCHOR_DEPTH_DB } ?: true }.toSet()
            // the anchor: the listened partial while it is such a line, else the strongest such one;
            // a change waits ANCHOR_HOLD_S so the line never flaps between two partials
            val wanted = when {
                f.listened in sharp -> f.listened
                sharp.isNotEmpty() -> sharp.maxByOrNull { f.partials[it]!!.level }!!
                f.listened in counted -> f.listened
                counted.isNotEmpty() -> counted.maxByOrNull { f.partials[it]!!.level }!!
                else -> f.listened
            }
            val current = anchor ?: wanted.also { anchor = it }
            val anchorNow = if (wanted == current) {
                pendingAnchor = null; current
            } else {
                if (pendingAnchor != wanted) { pendingAnchor = wanted; pendingSinceMs = f.nowMs }
                if (f.nowMs - pendingSinceMs >= (ANCHOR_HOLD_S * 1000).toLong()) { anchor = wanted; pendingAnchor = null; wanted } else current
            }
            val anchorError = errors[anchorNow] ?: centsOff(f.listenedHz, f.listenedTargetHz)
            val bells = errors.keys.sorted().map { k ->
                val p = f.partials[k]!!
                Bell(k, errors[k]!! - anchorError, errors[k]!!, p.level, k in counted, p.hz, f.targets[k]!!, p.widthCents, p.overNoiseDb)
            }
            val pointer = centsOff(f.listenedHz, f.listenedTargetHz)
            // "on" is judged by the counted bells that are read well: a wide one cannot veto it
            val inside = sharp.isNotEmpty() && sharp.all { abs(errors[it]!!) <= f.matchCents + 1e-9 } &&
                abs(pointer) <= f.matchCents + 1e-9
            val state = when {
                !inside -> { onSinceMs = null; State.SOUNDING }
                else -> {
                    val since = onSinceMs ?: f.nowMs.also { onSinceMs = it }
                    if (f.nowMs - since >= (HOLD_S * 1000).toLong()) State.ON else State.NEAR
                }
            }
            bands = autoFour(bells, bands.toSet())
            return TuningPicture(f.note, f.listened, anchorNow, bells, pointer, state,
                f.falsePartials.intersect(errors.keys), f.dropped.intersect(errors.keys), wide, bands)
        }

        companion object {
            /** Inside the gate this long before it is "on": near is not on. */
            const val HOLD_S = 0.5
            /** A new anchor is the strongest counted partial for this long before the line moves to it. */
            const val ANCHOR_HOLD_S = 0.5
            /**
             * A bell this many times wider than the narrowest counted one is wide. Measured on
             * the Samick's nine single-string F3 takes (3 Oct 2026): loud partials 0.2–0.5 c,
             * partials at the floor 2–6 c — four times apart at the least.
             */
            const val WIDE_FACTOR = 3.0
            /** … and never for a width under this: a loud partial reads up to half a cent wide and is still a line to stand on. */
            const val WIDE_CENTS = 0.5
            /** A partial anchors the line only while it stands this far over the noise beside it: where a reading is trusted to a third of a cent (CleanReading.JUDGE_OVER_NOISE_DB, measured 3 Oct 2026). */
            const val ANCHOR_DEPTH_DB = at.clavierhaus.unisonmaster.measure.CleanReading.JUDGE_OVER_NOISE_DB
        }
    }
}
