package at.clavierhaus.unisonmaster

import kotlin.math.abs

/**
 * The tests' messages formatted the same way on every platform. `String.format` is the JVM's
 * and does not exist on iOS, where the core tests run on the simulator since 30 Oct 2026; this
 * stands in for it in the tests, on both platforms, so a failure reads the same on both.
 * It takes the patterns the tests use: `%d`, `%s`, `%.Nf`, `%+.Nf`, `%.Ne`, an optional width,
 * and `%%`. It writes a decimal point, never a comma, as `Locale.ROOT` did. Only messages go
 * through it; no test compares its output with a number.
 */
fun String.format(vararg args: Any?): String {
    val out = StringBuilder()
    var next = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c != '%') { out.append(c); i++; continue }
        if (i + 1 < length && this[i + 1] == '%') { out.append('%'); i += 2; continue }
        var j = i + 1
        var plus = false
        while (j < length && this[j] in "+-0 #") { if (this[j] == '+') plus = true; j++ }
        var width = 0
        while (j < length && this[j].isDigit()) { width = width * 10 + (this[j] - '0'); j++ }
        var precision = -1
        if (j < length && this[j] == '.') {
            j++; precision = 0
            while (j < length && this[j].isDigit()) { precision = precision * 10 + (this[j] - '0'); j++ }
        }
        require(j < length) { "unfinished pattern in \"$this\"" }
        val arg = args.getOrNull(next++)
        val text = when (val conv = this[j]) {
            'd' -> signed((arg as Number).toLong().toString(), plus)
            's' -> arg.toString()
            'f' -> signed(fixed((arg as Number).toDouble(), if (precision < 0) 6 else precision), plus)
            'e' -> signed(scientific((arg as Number).toDouble(), if (precision < 0) 6 else precision), plus)
            else -> error("pattern %$conv is not taken by the tests' format")
        }
        out.append(text.padStart(width))
        i = j + 1
    }
    return out.toString()
}

private fun signed(s: String, plus: Boolean) = if (plus && !s.startsWith('-') && s != "NaN") "+$s" else s

/**
 * The decimal digits of |x| and the power of ten of the first one, from the shortest
 * representation that reads back as x -- the decimal the JVM's formatter rounds, not the binary
 * value behind it (9.995 is 9.99499... in binary, and the JVM writes 10.00).
 */
private fun decimal(x: Double): Pair<String, Int> {
    val s = abs(x).toString().lowercase()
    val mant = s.substringBefore('e'); val e10 = if ('e' in s) s.substringAfter('e').toInt() else 0
    val intPart = mant.substringBefore('.'); val frac = if ('.' in mant) mant.substringAfter('.') else ""
    val all = (intPart + frac).trimStart('0')
    if (all.isEmpty()) return "0" to 0
    val lead = (intPart + frac).length - all.length
    return all.trimEnd('0').ifEmpty { "0" } to (intPart.length - 1 - lead + e10)
}

/** [digits] (first digit at 10^[point]) rounded half up to the place 10^-[p]: digits and new point. */
private fun roundAt(digits: String, point: Int, p: Int): Pair<String, Int> {
    val keep = point + 1 + p
    if (keep < 0) return "0" to point
    if (keep >= digits.length) return digits to point
    val head = digits.take(keep).ifEmpty { "0" }
    if (digits[keep] < '5') return (if (keep == 0) "0" else head) to (if (keep == 0) -p else point)
    val chars = (if (keep == 0) "0" else head).toCharArray()
    var i = chars.size - 1
    while (i >= 0) { if (chars[i] == '9') { chars[i] = '0'; i-- } else { chars[i] = chars[i] + 1; break } }
    return if (i < 0) ("1" + chars.concatToString()) to (if (keep == 0) -p + 1 else point + 1)
    else chars.concatToString() to (if (keep == 0) -p else point)
}

/** [x] with [p] decimals, rounded half up as the JVM's formatter does. */
private fun fixed(x: Double, p: Int): String {
    if (x.isNaN()) return "NaN"
    if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
    val (d0, pt0) = decimal(x)
    val (d, pt) = if (d0 == "0") "0" to 0 else roundAt(d0, pt0, p)
    // place the digits: d[0] is at 10^pt
    val sb = StringBuilder()
    val top = maxOf(pt, 0)
    for (place in top downTo -p) {
        val idx = pt - place
        sb.append(if (idx in d.indices) d[idx] else '0')
        if (place == 0 && p > 0) sb.append('.')
    }
    return if (negative(x)) "-$sb" else sb.toString()
}

/** The JVM keeps the sign of a negative value that rounds to zero, and of -0.0. */
private fun negative(x: Double) = x < 0 || (x == 0.0 && 1.0 / x < 0)

/** [x] as d.ddde±XX with [p] decimals, as the JVM's `%e`. */
private fun scientific(x: Double, p: Int): String {
    if (x.isNaN()) return "NaN"
    if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
    val sign = if (negative(x)) "-" else ""
    if (x == 0.0) return sign + "0" + (if (p > 0) "." + "0".repeat(p) else "") + "e+00"
    val (d0, pt0) = decimal(x)
    val (d, exp) = roundAt(d0, 0, p).let { (dd, ptNew) -> dd to (pt0 + ptNew) }
    val digits = d.padEnd(p + 1, '0')
    val m = digits.take(1) + (if (p > 0) "." + digits.substring(1, p + 1) else "")
    val e = (if (exp < 0) "-" else "+") + abs(exp).toString().padStart(2, '0')
    return sign + m + "e" + e
}

/**
 * java.util.Random's generator, written out so a seeded test signal is the same sequence on
 * every platform: the 48-bit linear congruential generator its documentation specifies.
 * `CommonFormatTest` on the JVM checks it against java.util.Random itself.
 */
class JavaRandom(seed: Long) {
    private var state = (seed xor 0x5DEECE66DL) and ((1L shl 48) - 1)

    private fun next(bits: Int): Int {
        state = (state * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return (state ushr (48 - bits)).toInt()
    }

    fun nextDouble(): Double = ((next(26).toLong() shl 27) + next(27)) * (1.0 / (1L shl 53))

    private var spare: Double? = null

    /**
     * The polar method java.util.Random uses, with its spare value. The JVM takes the log and
     * root from StrictMath; kotlin.math may differ from it in the last bit, which moves a test
     * signal's noise by parts in 1e16 and nothing a test reads.
     */
    fun nextGaussian(): Double {
        spare?.let { spare = null; return it }
        var v1: Double; var v2: Double; var s: Double
        do {
            v1 = 2 * nextDouble() - 1
            v2 = 2 * nextDouble() - 1
            s = v1 * v1 + v2 * v2
        } while (s >= 1 || s == 0.0)
        val multiplier = kotlin.math.sqrt(-2 * kotlin.math.ln(s) / s)
        spare = v2 * multiplier
        return v1 * multiplier
    }
}
