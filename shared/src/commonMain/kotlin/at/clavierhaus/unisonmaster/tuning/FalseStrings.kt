package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.measure.FalseBeat

/** Which string of a key was checked, when the tuner says: left, centre, right. Never required (Peter, 10 Nov 2026). */
enum class StringPlace { L, C, R }

/**
 * One string checked alone ([at.clavierhaus.unisonmaster.measure.StringCheck]): its key, its place if named, when, its
 * false beats, and the partials it was heard long enough to judge -- none found on a partial not judged says nothing.
 */
data class CheckedString(val midi: Int, val place: StringPlace?, val atMs: Long, val beats: List<FalseBeat>, val judged: Set<Int> = emptySet()) {
    /** Judged on some partial, or claimed false: a check that says something of the string. */
    val says: Boolean get() = beats.isNotEmpty() || judged.isNotEmpty()
}

/**
 * The single strings checked for false beats, and the partials the tuner has taken out of a key's reading because of
 * them (Peter, 10 Nov 2026: "the tuner is given the option of excluding ... only the partial", "with another option to
 * discard the measurement and exclusion"). The checks are what the strings did; the exclusions are the tuner's choice,
 * kept as his and undone by him, never made by the app.
 *
 * Kept with the tuning ([at.clavierhaus.unisonmaster.persistence.SessionSnapshot]) and applied to it.
 */
data class FalseStrings(
    val strings: List<CheckedString> = emptyList(),
    /** The partials taken out, by key. */
    val excluded: Map<Int, Set<Int>> = emptyMap(),
) {
    fun of(midi: Int): List<CheckedString> = strings.filter { it.midi == midi }

    fun excludedAt(midi: Int): Set<Int> = excluded[midi].orEmpty()

    /** The keys checked, low to high. */
    val keys: List<Int> get() = strings.map { it.midi }.distinct().sorted()

    /** A string checked: the earlier check of the same key and place (or of the key, both unnamed) gives way to it. */
    fun with(check: CheckedString): FalseStrings =
        copy(strings = strings.filterNot { it.midi == check.midi && it.place == check.place } + check)

    /** The check of [midi] made at [atMs] named as the string at [place], or unnamed; one of that key and place before it gives way. */
    fun named(midi: Int, atMs: Long, place: StringPlace?): FalseStrings {
        val it = strings.firstOrNull { s -> s.midi == midi && s.atMs == atMs } ?: return this
        return copy(strings = strings.filterNot { s -> s === it || (s.midi == midi && s.place == place && place != null) } + it.copy(place = place))
    }

    /** Partial [k] of [midi] out of the reading, or back in. */
    fun exclude(midi: Int, k: Int, out: Boolean): FalseStrings {
        val now = excludedAt(midi).let { if (out) it + k else it - k }
        return copy(excluded = if (now.isEmpty()) excluded - midi else excluded + (midi to now))
    }

    /** Every check of [midi] and every partial taken out of it, gone. */
    fun discard(midi: Int): FalseStrings = copy(strings = strings.filterNot { it.midi == midi }, excluded = excluded - midi)

    val isEmpty: Boolean get() = strings.isEmpty() && excluded.isEmpty()
}
