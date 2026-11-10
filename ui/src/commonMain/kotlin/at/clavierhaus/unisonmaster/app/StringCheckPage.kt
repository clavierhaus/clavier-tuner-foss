package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.i18n.noteName
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.measure.StringCheck
import at.clavierhaus.unisonmaster.tuning.FalseStrings
import at.clavierhaus.unisonmaster.tuning.StringPlace
import at.clavierhaus.unisonmaster.ui.CORNER_BAND
import at.clavierhaus.unisonmaster.ui.Corners
import at.clavierhaus.unisonmaster.ui.TextBlock
import at.clavierhaus.unisonmaster.ui.num

/*
 * The false-beat check of single strings on the screen (Peter, 10 Nov 2026): what the engine's StringCheck reads of
 * the strike sounding -- the key it named, the partials it heard, the ones it claimed false with their rate -- and
 * the tuner's choice of which of them to take out of the reading. Two doors lead here: the hub's, to go through the
 * piano string by string, and the tuning screen's Check string, for the key in hand when the strobe will not settle.
 *
 * Strictly single strings (Peter, 10 Nov 2026): the words say one string alone, the others muted, and what a false
 * beat is.
 *
 * A number on this panel is the last strike's, and dies with the next (Amendment 3). What an earlier check left is
 * shown as the key and the partials taken out, without a rate: the record is kept with the tuning, the rate is not
 * put on the screen a second time.
 */

private val PLACES = listOf(StringPlace.L to K.check_left, StringPlace.C to K.check_centre, StringPlace.R to K.check_right)

private fun placed(midi: Int, place: StringPlace?): String =
    noteName(midi) + (place?.let { p -> " " + t(PLACES.first { it.first == p }.second) } ?: "")

/**
 * The panel itself, drawn from what it is given: [now] the check under way, [last] the strike just checked, [strings]
 * what was checked and taken out; [keyInView] the tuning screen's key, whose earlier check stands here until a strike
 * brings a new one. [forWhat] says which tuning the checks are kept with; [top] goes at the end of the first row (the
 * dialog's Done).
 */
@Composable
fun StringCheckPanel(
    now: StringCheck.State?,
    last: TuningController.CheckResult?,
    strings: FalseStrings,
    keyInView: Int?,
    place: StringPlace?,
    onPlace: (StringPlace?) -> Unit,
    onExclude: (midi: Int, k: Int, out: Boolean) -> Unit,
    onDiscard: (midi: Int) -> Unit,
    forWhat: String?,
    modifier: Modifier = Modifier,
    top: (@Composable () -> Unit)? = null,
) {
    val muted = Color(Brand.WHITE_MUTED)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // what this is, and how: one string alone
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextBlock(listOf(t(K.check_title)), Modifier.fillMaxWidth(), size = 20.sp, minSize = 14.sp, fontWeight = FontWeight.Bold)
                TextBlock(listOf(t(K.check_orders), t(K.check_what)), Modifier.fillMaxWidth(), size = 14.sp, color = muted)
            }
            Spacer(Modifier.width(16.dp))
            // which string, if the tuner cares to say; a second tap takes the name back
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextBlock(listOf(t(K.check_place)), size = 13.sp, color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((p, key) in PLACES) Chip(t(key), selected = place == p, enabled = true) { onPlace(if (place == p) null else p) }
                }
            }
            if (top != null) { Spacer(Modifier.width(16.dp)); top() }
        }

        // the strike sounding
        now?.let { s ->
            val line = when (s.phase) {
                StringCheck.Phase.WAITING, StringCheck.Phase.DONE -> t(K.check_waiting)
                StringCheck.Phase.UNNAMED -> t(K.check_unnamed)
                else -> s.midi?.let { t(K.check_ringingKey, noteName(it), num(s.seconds, 0)) } ?: t(K.check_ringing, num(s.seconds, 0))
            }
            TextBlock(listOf(line), Modifier.fillMaxWidth(), size = 18.sp, minSize = 12.sp)
        }

        // the strike just checked, with its numbers; else the key in view as an earlier check left it
        val shown = last?.state?.midi ?: keyInView?.takeIf { strings.of(it).isNotEmpty() || strings.excludedAt(it).isNotEmpty() }
        if (shown != null) {
            val out = strings.excludedAt(shown)
            val fresh = last?.takeIf { it.state.midi == shown }
            val beats = fresh?.state?.falseBeats
            Row(verticalAlignment = Alignment.CenterVertically) {
                // none found is said only of a strike that judged something: one too short to judge is not clean
                val judgedAny = fresh?.state?.judged?.isNotEmpty() == true
                val verdict = when {
                    beats == null -> t(K.check_earlier, noteName(shown))
                    beats.isNotEmpty() -> t(K.check_false, placed(shown, fresh.place))
                    judgedAny -> t(K.check_clean, placed(shown, fresh.place))
                    else -> t(K.check_short, placed(shown, fresh.place))
                }
                TextBlock(
                    listOf(verdict), Modifier.weight(1f), size = 18.sp, minSize = 12.sp,
                    color = when { beats == null -> Color(Brand.WHITE); beats.isNotEmpty() -> Color(Brand.ORANGE); judgedAny -> Color(Brand.GO_GREEN); else -> Color(Brand.WHITE) },
                )
                Spacer(Modifier.width(12.dp))
                Chip(t(K.check_discard, noteName(shown)), selected = false, enabled = true) { onDiscard(shown) }
            }
            if (fresh != null) {
                // which partials the verdict speaks for, and which were heard too briefly for it to speak (Rule 0)
                val judged = fresh.state.judged
                val brief = fresh.state.heard - judged.toSet()
                val parts = listOfNotNull(
                    judged.takeIf { it.isNotEmpty() }?.let { t(K.check_judged, it.joinToString(" ")) },
                    brief.takeIf { it.isNotEmpty() }?.let { t(K.check_brief, it.joinToString(" ")) },
                )
                if (parts.isNotEmpty()) TextBlock(listOf(parts.joinToString(" · ")), Modifier.fillMaxWidth(), size = 13.sp, color = muted)
            }
            // the partials to take out: the ones this strike claimed, with their rate; of an earlier check the ones
            // claimed then and the ones taken out, by number only
            val offered: List<Pair<Int, String>> = if (beats != null) beats.map { it.k to t(K.check_partialRate, it.k, num(it.rateHz, 2)) }
                else (strings.of(shown).flatMap { c -> c.beats.map { it.k } } + out).distinct().sorted().map { it to t(K.tuning_partialOne, it) }
            if (offered.isNotEmpty()) {
                TextBlock(listOf(t(K.check_takeOut)), Modifier.fillMaxWidth(), size = 13.sp, color = muted)
                Wrap(6.dp) {
                    for ((k, label) in offered) Chip(label, selected = k in out, enabled = true) { onExclude(shown, k, k !in out) }
                }
            }
        }

        // the whole piano so far: how many keys a check said something of, and which beat
        val keys = strings.keys.filter { m -> strings.of(m).any { it.says } }
        if (keys.isNotEmpty() || forWhat != null) {
            val falseKeys = keys.filter { m -> strings.of(m).any { it.beats.isNotEmpty() } }
            val summary = when {
                keys.isEmpty() -> null
                falseKeys.isEmpty() -> t(K.check_summaryClean, keys.size)
                else -> t(K.check_summary, keys.size, falseKeys.joinToString(" ") { noteName(it) })
            }
            // one paragraph: which tuning they go with runs on where the list of keys ends
            TextBlock(listOf(listOfNotNull(summary, forWhat).joinToString(" · ")), Modifier.fillMaxWidth(), size = 13.sp, color = muted)
        }
    }
}

/** Chips in rows, as many to a row as fit: the claims of one strike are few, but never cut. */
@Composable
private fun Wrap(gap: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val g = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        var x = 0; var y = 0; var row = 0
        val at = placeables.map { p ->
            if (x > 0 && x + p.width > constraints.maxWidth) { x = 0; y += row + g; row = 0 }
            val here = x to y
            x += p.width + g; row = maxOf(row, p.height)
            here
        }
        layout(constraints.maxWidth, y + row) { placeables.forEachIndexed { i, p -> p.place(at[i].first, at[i].second) } }
    }
}

/** The controller's check, drawn by [StringCheckPanel]. */
@Composable
private fun ControllerPanel(controller: TuningController, keyInView: Int?, forWhat: String?, modifier: Modifier, top: (@Composable () -> Unit)? = null) {
    val now by controller.checking.collectAsState()
    val last by controller.lastCheck.collectAsState()
    val strings by controller.falseStrings.collectAsState()
    val place by controller.checkedPlace.collectAsState()
    @Suppress("UNUSED_VARIABLE") val language by Strings.language.collectAsState()
    StringCheckPanel(
        now, last, strings, keyInView, place,
        onPlace = { controller.nameCheckedString(it) },
        onExclude = { midi, k, out -> controller.excludePartial(midi, k, out) },
        onDiscard = { controller.discardChecks(it) },
        forWhat = forWhat, modifier = modifier, top = top,
    )
}

/**
 * The hub's door: the check page, between the corners. [forWhat] says which tuning the checks go with. The microphone
 * is the check's while the page is in front ([TunerApp.screenShown]).
 */
@Composable
fun BoxScope.StringCheckPage(controller: TuningController, forWhat: String?, onSettings: () -> Unit, onHome: () -> Unit) =
    StringCheckFrame(onSettings, onHome) { m -> ControllerPanel(controller, null, forWhat, m) }

/** The page around a panel, so the layout check draws it as the app does. */
@Composable
internal fun BoxScope.StringCheckFrame(onSettings: () -> Unit, onHome: () -> Unit, panel: @Composable (Modifier) -> Unit) {
    Corners(onSettings = onSettings, onHome = onHome)
    panel(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = CORNER_BAND + 4.dp, bottom = 12.dp))
}

/** The tuning screen's Check string: the same panel over the screen, the key on screen in view; Done gives the microphone back. */
@Composable
fun StringCheckDialog(controller: TuningController, keyInView: Int, onClose: () -> Unit) =
    StringCheckDialogFrame(onClose) { m, top -> ControllerPanel(controller, keyInView, null, m, top) }

/** The window around a panel, with its Done. */
@Composable
internal fun StringCheckDialogFrame(onClose: () -> Unit, panel: @Composable (Modifier, @Composable () -> Unit) -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(0.92f).background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(16.dp)) {
            panel(Modifier.fillMaxWidth()) { Chip(t(K.app_done), selected = true, enabled = true, onClick = onClose) }
        }
    }
}
