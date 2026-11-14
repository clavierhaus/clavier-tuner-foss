package at.clavierhaus.unisonmaster.picture

import at.clavierhaus.unisonmaster.measure.PartialMap
import kotlin.math.pow

/**
 * How fast the strobe moves in the treble: at the pace of the tuning pin, not of the beat in hertz (Peter, 14 Nov 2026:
 * "We need to reach a point where tuning single E7 feels like tuning E3"; the slowing "should somehow correspond to the
 * string length and the degree of turning the pin ... I don't [think] we should name any number for that"; and, the app
 * being universal, from no one piano's scale).
 *
 * Why: the hand turns the pin, and the pin stretches the wire. A turn of the pin by an angle lengthens the wire by the
 * same small amount on every string, which raises the tension by that amount over the string's length (Hooke), and the
 * pitch by half the tension's share (Mersenne). Per degree of pin, then, the pitch moves in proportion to 1 / (L^3 f^2),
 * with L the speaking length: the shorter the string, the further each degree carries it. The pin turn a beat of
 * Δf hertz asks for is in proportion to Δf L^3 f. Shown as hertz, the strobe races in the treble while the hand has
 * hardly anything left to do; shown in proportion to the pin's turn, a degree looks alike at every key.
 *
 * L is not known to the app and differs from piano to piano. Its share comes from what the sampling measured: a plain
 * steel string's inharmonicity is B = π^2 E d^2 / (64 ρ L^4 f^2), so with the wire's thickness d about the same over the
 * treble's few keys, L goes as (B f^2)^(-1/4), and L^3 f as B^(-3/4) f^(-1/2). Every factor is this piano's own: its B
 * from the curve the sampling built, its f from the key. No length, no table, no factor set by hand.
 *
 * From where: the first key the engine tunes on its fundamental ([FROM], C6). Below it nothing changes; there the pace is
 * 1, and above it the strobe slows by the ratio of the key's L^3 f to C6's. Where exactly the change should begin is for
 * the piano to say (Peter: "this is where we need iteration").
 *
 * What it does not do: change any reading, target, green or verdict. The bands' speed alone is drawn at this pace; the
 * pointer and every number stay the engine's own.
 */
object PinPace {
    /** The first key tuned on its fundamental (PartialMap.listening): where the strobe begins to move at the pin's pace. */
    val FROM: Int = PartialMap.B5 + 1

    /** The strobe's pace at [midi], 1 at and below [FROM]; [b] gives the inharmonicity the session predicts for a key. */
    fun of(midi: Int, b: (Int) -> Double): Double {
        if (midi <= FROM) return 1.0
        val b0 = b(FROM); val bm = b(midi)
        if (!(b0 > 0.0) || !(bm > 0.0)) return 1.0
        val f = 2.0.pow((midi - FROM) / 12.0)
        return ((bm / b0).pow(-0.75) * f.pow(-0.5)).coerceIn(0.0, 1.0)
    }
}
