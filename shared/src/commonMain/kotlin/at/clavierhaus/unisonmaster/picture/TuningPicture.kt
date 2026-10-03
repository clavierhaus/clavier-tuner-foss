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
    )

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
        /** One live partial: as read, its level 0..1 of the loudest, and its width when known. */
        class Partial(val hz: Double, val level: Double, val widthCents: Double? = null)
    }

    /**
     * Builds the picture frame by frame, keeping only what a hold needs: when
     * the gate was first held and which anchor is waiting to take over. Both
     * are forgotten the moment the sound stops.
     */
    class Builder {
        private var onSinceMs: Long? = null
        private var pendingAnchor: Int? = null
        private var pendingSinceMs: Long = 0L
        private var anchor: Int? = null

        fun build(f: Frame): TuningPicture {
            if (!f.live || f.listenedHz == null) {
                onSinceMs = null; pendingAnchor = null; anchor = null
                return TuningPicture(f.note, f.listened, f.listened, emptyList(), null, State.SILENT, emptySet(), emptySet())
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
            val sharp = counted - wide
            // the anchor: the listened partial while it is counted and not wide, else the strongest
            // such one; a change waits ANCHOR_HOLD_S so the line never flaps between two partials
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
                Bell(k, errors[k]!! - anchorError, errors[k]!!, p.level, k in counted, p.hz, f.targets[k]!!, p.widthCents)
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
            return TuningPicture(f.note, f.listened, anchorNow, bells, pointer, state,
                f.falsePartials.intersect(errors.keys), f.dropped.intersect(errors.keys), wide)
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
        }
    }
}
