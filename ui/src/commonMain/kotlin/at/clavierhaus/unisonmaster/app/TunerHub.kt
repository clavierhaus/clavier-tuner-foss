package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.i18n.K
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.HomeHub
import at.clavierhaus.unisonmaster.ui.HomeHubItem
import at.clavierhaus.unisonmaster.ui.formatHz

/**
 * The hub both editions open on (one tuner, two editions, 4 Oct 2026). What
 * it puts on the screen is the saved tuning's own two numbers — the day it
 * was last used and its A4, read back from the sealed session — and the
 * doors: *Continue Tuning* while one is saved, *New Tuning*, *False Beats*
 * ([onFalseBeats]: single strings checked one at a time, 10 Nov 2026), an edition's own
 * doors ([extra] — Pro's *My Pianos* / *My Customers*). The recorder's door went with the recorder to Pro on
 * 5 Oct 2026 and arrives through [extra] like the book's. The words of the
 * doors kept here are written here once; an app only says what happens behind
 * each door.
 */
@Composable
fun TunerHub(
    last: SessionStore.Load,
    onContinue: (SessionSnapshot) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    /** The false-beat check of single strings; null where an app has no door to it. */
    onFalseBeats: (() -> Unit)? = null,
    extra: List<HomeHubItem> = emptyList(),
) {
    val saved = (last as? SessionStore.Load.Ok)?.snapshot
    // A door that starts over took the tuning that could be continued without a word (Peter, 9 Nov 2026: "Confirmation
    // is good"). With nothing to continue there is nothing to lose, and no question.
    var asking by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun afterAsking(go: () -> Unit): () -> Unit = if (saved == null) go else ({ asking = go })
    val entries = buildList {
        saved?.let { snap ->
            add(HomeHubItem(t(K.hub_continue_verb), t(K.hub_continue_rest), t(K.hub_continue_note, lastUsed(snap.savedAtMs), formatHz(snap.a4Hz))) { onContinue(snap) })
        }
        add(
            HomeHubItem(
                t(K.hub_new_verb), t(K.hub_new_rest),
                if (last is SessionStore.Load.Rejected) t(K.hub_new_rejected) else t(K.hub_new_note),
                onClick = afterAsking(onNew),
            ),
        )
        if (onFalseBeats != null) add(HomeHubItem(t(K.hub_falseBeats_verb), t(K.hub_falseBeats_rest), t(K.hub_falseBeats_note), onClick = onFalseBeats))
        addAll(extra.map { if (it.startsOver) it.copy(onClick = afterAsking(it.onClick)) else it })
    }
    HomeHub(entries = entries, onSettings = onSettings)
    val go = asking
    if (go != null && saved != null) StartOverDialog(saved, onStart = { asking = null; go() }, onKeep = { asking = null })
}

/** The question before [saved] is replaced by a new tuning: which tuning, and what is lost. It says "continued", which is true in both editions. */
@Composable
fun StartOverDialog(saved: SessionSnapshot, onStart: () -> Unit, onKeep: () -> Unit) {
    Dialog(onDismissRequest = onKeep) {
        Column(
            Modifier.background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t(K.hub_new_ask), color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(t(K.hub_new_askNote, lastUsed(saved.savedAtMs), formatHz(saved.a4Hz)), color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(t(K.hub_new_askYes), selected = true, enabled = true, onClick = onStart)
                Chip(t(K.app_cancel), selected = false, enabled = true, onClick = onKeep)
            }
        }
    }
}

/** "15 Sep", "15. Sep." — the day the saved tuning was last used, in the language in force. */
internal fun lastUsed(ms: Long): String =
    at.clavierhaus.unisonmaster.ui.formatDate(ms, t(K.hub_dateFormat), at.clavierhaus.unisonmaster.i18n.Strings.language.value)

/** Which tuning the single strings checked go with: the one saved, or the next one begun (StringCheckPage). */
fun checksFor(last: SessionStore.Load): String =
    (last as? SessionStore.Load.Ok)?.snapshot?.let { t(K.check_forTuning, lastUsed(it.savedAtMs), formatHz(it.a4Hz)) } ?: t(K.check_forNext)
