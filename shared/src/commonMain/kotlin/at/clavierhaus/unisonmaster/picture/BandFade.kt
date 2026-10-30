package at.clavierhaus.unisonmaster.picture

/**
 * How far each band of the strobe has come up, 0 to 1, frame by frame (Peter, 30 Oct 2026: "smooth their
 * appearance for 300 ms", and the row coming up band by band or all at once as the tuner chooses).
 *
 * It draws nothing and reads nothing: which bands are read is the engine's (a band is read when its partial
 * has a bell in the picture), and a band read is drawn as read. This only says how strongly, while it comes
 * up. A band comes up over [fadeMs] from the frame in which it is first read; one that is no longer read
 * leaves at once and comes up again when it is read again. With `together`, no band is drawn until every
 * seat that will be read has been, or until [waitMs] after the first band was read, and from then on all
 * of them come up at once; a band read later comes up on its own. When nothing is read the row is empty and
 * the next strike starts afresh.
 */
class BandFade(private val fadeMs: Long, private val waitMs: Long) {
    private val since = HashMap<Int, Long>()
    private var firstAt: Long? = null
    private var rowAt: Long? = null

    /** Each read band's strength at [nowMs]; [expected] are the seats waited for with `together`. */
    fun alphas(nowMs: Long, read: Set<Int>, expected: Set<Int>, together: Boolean): Map<Int, Float> {
        since.keys.retainAll(read)
        if (read.isEmpty()) { firstAt = null; rowAt = null; return emptyMap() }
        for (k in read) since.getOrPut(k) { nowMs }
        val first = firstAt ?: nowMs.also { firstAt = it }
        val row = if (!together) null else rowAt ?: if (read.containsAll(expected) || nowMs - first >= waitMs) nowMs.also { rowAt = it } else null
        return read.associateWith { k ->
            val from = if (together) row?.let { maxOf(it, since.getValue(k)) } else since.getValue(k)
            if (from == null) 0f else ((nowMs - from).toFloat() / fadeMs).coerceIn(0f, 1f)
        }
    }
}
