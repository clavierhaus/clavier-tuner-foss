package at.clavierhaus.unisonmaster.tuning

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * The instrument's inharmonicity over the compass, from the strings
 * sampled (docs/ENGINE.md §1): ln B against key number, one straight line
 * per segment. Physics, not one piano: on plain wire B grows roughly
 * geometrically up the scale (Young), so ln B is near a line within a
 * section of the scale; where the scaling changes — a bridge break, a strut,
 * the wound strings — the line breaks, so the segments are cut at the
 * plain-wire floor and at the breaks the tuner marks.
 *
 * Within a segment: two or more samples give a line; one gives its value
 * throughout; none borrows the nearest sample of the neighbouring segments.
 * Every string keeps its own measured B once it has one; the curve is for
 * the notes not yet measured.
 */
class InharmonicityCurve(
    samples: Map<Int, Double>,
    /** The lowest plain string: the wound strings below it are their own segment. */
    private val lowestUnwoundMidi: Int,
    /** Notes at which a new segment starts (the note itself is in the upper segment). */
    breaks: Set<Int> = emptySet(),
) {
    private class Segment(val from: Int, val to: Int, val slope: Double, val intercept: Double, val count: Int)

    private val segments: List<Segment>
    private val all = samples.filter { it.value > 0.0 }.toSortedMap()

    init {
        val cuts = (breaks + lowestUnwoundMidi).filter { it in 1..127 }.sorted()
        val starts = listOf(0) + cuts
        val ends = cuts.map { it - 1 } + listOf(127)
        segments = starts.zip(ends).map { (from, to) ->
            val inside = all.filter { it.key in from..to }
            when {
                inside.size >= 2 -> {
                    val xs = inside.keys.map { it.toDouble() }; val ys = inside.values.map { ln(it) }
                    val mx = xs.average(); val my = ys.average()
                    var sxx = 0.0; var sxy = 0.0
                    for (i in xs.indices) { sxx += (xs[i] - mx) * (xs[i] - mx); sxy += (xs[i] - mx) * (ys[i] - my) }
                    val slope = if (sxx > 0) sxy / sxx else 0.0
                    Segment(from, to, slope, my - slope * mx, inside.size)
                }
                inside.size == 1 -> Segment(from, to, 0.0, ln(inside.values.first()), 1)
                else -> Segment(from, to, 0.0, Double.NaN, 0)
            }
        }
    }

    /** How many strings the curve stands on. */
    val count: Int get() = all.size

    /** True when at least two plain strings are in: a line can be drawn. */
    val ready: Boolean get() = all.keys.count { it >= lowestUnwoundMidi } >= 2

    /** B expected of [midi]: its own sample, else its segment's line, else the nearest sample. */
    fun b(midi: Int): Double? {
        all[midi]?.let { return it }
        val seg = segments.firstOrNull { midi in it.from..it.to } ?: return null
        if (seg.count > 0) return exp(seg.intercept + seg.slope * midi)
        // an empty segment: the nearest sample of the same kind of wire, else any
        val sameWire = all.keys.filter { (it < lowestUnwoundMidi) == (midi < lowestUnwoundMidi) }
        val near = (sameWire.ifEmpty { all.keys }).minByOrNull { abs(it - midi) } ?: return null
        return all[near]
    }

    /** The notes sampled, lowest first. */
    val sampled: List<Int> get() = all.keys.toList()
}
