package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.i18n.K
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
 * "a USP for Pro only"). The recorder's door went with the recorder to Pro on
 * 5 Oct 2026 and arrives through [extra] like the book's. The words of the
 * doors kept here are written here once; an app only says what happens behind
 * each door.
 */
@Composable
fun TunerHub(
    last: SessionStore.Load,
    onContinue: (SessionSnapshot) -> Unit,
    onNew: () -> Unit,
    onUnisons: (() -> Unit)? = null,
    onSettings: () -> Unit,
    extra: List<HomeHubItem> = emptyList(),
) {
    val entries = buildList {
        (last as? SessionStore.Load.Ok)?.snapshot?.let { snap ->
            add(HomeHubItem(t(K.hub_continue_verb), t(K.hub_continue_rest), t(K.hub_continue_note, lastUsed(snap.savedAtMs), formatHz(snap.a4Hz))) { onContinue(snap) })
        }
        add(
            HomeHubItem(
                t(K.hub_new_verb), t(K.hub_new_rest),
                if (last is SessionStore.Load.Rejected) t(K.hub_new_rejected) else t(K.hub_new_note),
                onClick = onNew,
            ),
        )
        addAll(extra)
        if (onUnisons != null) add(HomeHubItem(t(K.hub_unisons_verb), "", t(K.hub_unisons_note), lead = t(K.hub_unisons_lead), onClick = onUnisons))
    }
    HomeHub(entries = entries, onSettings = onSettings)
}

/** "15 Sep", "15. Sep." — the day the saved tuning was last used, in the language in force. */
private fun lastUsed(ms: Long): String =
    at.clavierhaus.unisonmaster.ui.formatDate(ms, t(K.hub_dateFormat), at.clavierhaus.unisonmaster.i18n.Strings.language.value)
