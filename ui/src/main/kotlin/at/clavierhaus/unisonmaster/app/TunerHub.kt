package at.clavierhaus.unisonmaster.app

import androidx.compose.runtime.Composable
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.ui.HomeHub
import at.clavierhaus.unisonmaster.ui.HomeHubItem
import at.clavierhaus.unisonmaster.ui.formatHz

/**
 * The hub both editions open on (one tuner, two editions, 4 Oct 2026). What
 * it puts on the screen is the saved tuning's own two numbers — the day it
 * was last used and its A4, read back from the sealed session — and the
 * doors: *Continue Tuning* while one is saved, *New Tuning*, an edition's own
 * doors ([extra] — Pro's *My Pianos* / *My Customers*), and where the
 * edition has them *The Unisons* ([onUnisons]; Pro's — Peter, 4 Oct 2026,
 * "a USP for Pro only") and *Recordings* ([onRecordings]; Pro's, since the
 * reference recording belongs to the piano's record — blueprint §18). The
 * words are written here once; an app only says what happens behind each
 * door.
 */
@Composable
fun TunerHub(
    last: SessionStore.Load,
    onContinue: (SessionSnapshot) -> Unit,
    onNew: () -> Unit,
    onUnisons: (() -> Unit)? = null,
    onRecordings: (() -> Unit)? = null,
    onSettings: () -> Unit,
    extra: List<HomeHubItem> = emptyList(),
) {
    val entries = buildList {
        (last as? SessionStore.Load.Ok)?.snapshot?.let { snap ->
            add(HomeHubItem("Continue", " Tuning", "from ${lastUsed(snap.savedAtMs)}, A4 ${formatHz(snap.a4Hz)}") { onContinue(snap) })
        }
        add(
            HomeHubItem(
                "New", " Tuning",
                if (last is SessionStore.Load.Rejected) "the saved tuning could not be verified" else "starting from A4",
                onClick = onNew,
            ),
        )
        addAll(extra)
        if (onUnisons != null) add(HomeHubItem("Unisons", "", "the strings of a note read apart, partial by partial", lead = "The", onClick = onUnisons))
        if (onRecordings != null) add(HomeHubItem("Recordings", "", "every string alone, the drift, the compass: the studies", onClick = onRecordings))
    }
    HomeHub(entries = entries, onSettings = onSettings)
}

/** "15 Sep" — the day the saved tuning was last used. */
private fun lastUsed(ms: Long): String =
    java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date(ms))
