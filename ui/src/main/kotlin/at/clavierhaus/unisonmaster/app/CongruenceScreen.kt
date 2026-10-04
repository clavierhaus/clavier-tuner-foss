package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioTap
import at.clavierhaus.unisonmaster.unison.CongruenceScope
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.ui.CORNER_LEFT_WIDTH
import at.clavierhaus.unisonmaster.ui.CORNER_RIGHT_WIDTH
import at.clavierhaus.unisonmaster.ui.Corners
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.HubHint
import at.clavierhaus.unisonmaster.ui.RecordButton
import at.clavierhaus.unisonmaster.unison.UnisonSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The congruence display: a unison read as marks that slide
 * together and fuse when the strings coincide, on four partials at once
 * with the fundamental excluded and the partials chosen by the instrument.
 *
 * The measurement and the drawing are the ones tuned at the instrument on
 * 14 September and judged "as good as it gets" (docs/CONGRUENCE.md). They
 * are restored from the retired screen unchanged: column span ±4 Hz, ticks
 * each 0.5 Hz, green when one peak or two within 0.15 Hz, the measured
 * profile drawn and nothing drawn that is not the measurement. What is new
 * is only the frame around them — the back arrow, gear and title of today's
 * screens — and the partial row no longer scrolls, because nothing on a
 * screen of this app scrolls.
 *
 * Microphone: the tuning controller's live capture is stopped by the
 * activity whenever this screen is shown; the pump takes the source here
 * and gives it back on leaving. The note is the one [UnisonSync] names
 * from the strike, its fundamental where the finder placed it.
 */
@Composable
fun CongruenceScreen(
    scope: CongruenceScope,
    pump: AudioTap,
    controller: TuningController,
    /** The red button, when recording is switched on in Settings → Research; null hides it. */
    recorder: SessionRecorder?,
    /** How many partials are in sync, strike by strike; the single-string check. */
    sync: UnisonSync,
    onSettings: () -> Unit,
    /** Home: the hub, from the corner every screen shares. */
    onHome: () -> Unit,
) {
    val listening by pump.listening.collectAsState()
    val syncState by sync.state.collectAsState()
    val detected = syncState.f1Hz
    val refA4 by controller.referenceA4Hz.collectAsState()
    var frame by remember { mutableStateOf(0) }
    var heldHz by remember { mutableStateOf(0.0) }
    // Measured split + profile per selected partial, refreshed ~8 Hz on a
    // background thread. The canvas only draws these.
    var measured by remember { mutableStateOf<Map<Int, Pair<DoubleArray, DoubleArray>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        while (true) {
            val ks = (1..CongruenceScope.PARTIALS).filter { scope.isOn(it) }
            val m = withContext(Dispatchers.Default) {
                ks.associateWith { k -> scope.combSplit(k) to scope.profile(k, 4.0, 0.05) }
            }
            measured = m
            delay(120)
        }
    }
    LaunchedEffect(detected) {
        val d = detected
        if (d != null && d > 0.0) { heldHz = d; scope.tune(d) }
    }
    // One 3-second measurement, then pick the four partials to tune by.
    LaunchedEffect(heldHz) {
        if (heldHz > 0.0 && !scope.autoSelected) {
            delay(3000)
            scope.autoSelectFour()
            frame++
        }
    }
    LaunchedEffect(Unit) {
        pump.start()
        while (true) { withFrameNanos { }; frame++ }
    }
    DisposableEffect(Unit) { onDispose { recorder?.stop(); pump.stop() } }

    Box(Modifier.fillMaxSize().background(Color(Brand.BLACK))) {
        // the corners every screen has; the header row keeps to the band between them
        Corners(onSettings = onSettings, onHome = onHome)
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = CORNER_LEFT_WIDTH - 12.dp, end = CORNER_RIGHT_WIDTH - 12.dp)) {
                Text(
                    (if (heldHz > 0.0) heldHz else detected)?.let {
                        "${Notes.name(Notes.nearestMidi(it, refA4))}  ·  %.1f Hz".format(it)
                    } ?: "strike the note",
                    color = Color(Brand.WHITE), fontFamily = DejaVuSerif,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (recorder != null) {
                    // what the microphone hears, as the analysis gets it: unison_<stamp>_unproc.wav
                    val recording by recorder.recording.collectAsState()
                    val seconds by recorder.seconds.collectAsState()
                    RecordButton(recording = recording, seconds = seconds, onToggle = {
                        if (recording) recorder.stop() else recorder.start("unison")
                    })
                    Spacer(Modifier.width(12.dp))
                }
                Button(
                    onClick = { if (listening) pump.stop() else pump.start() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(Brand.ORANGE), contentColor = Color(Brand.BLACK),
                    ),
                ) {
                    Text(if (listening) "Stop" else "Listen", fontFamily = DejaVuSerif, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            HubHint(
                if (!scope.autoSelected) "Listening 3 s, choosing four partials…"
                else "The marks close to one at unison; green is coincidence, the ear calls it. Bars: green steady, orange beating, blue the string's own beat.",
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))

            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                frame.let { }
                val w = size.width; val h = size.height
                val ks = (1..CongruenceScope.PARTIALS).filter { scope.isOn(it) }
                if (ks.isEmpty()) return@Canvas
                val colW = w / ks.size
                val halfHz = 4.0
                val axisY = h * 0.80f
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(170, 255, 255, 255)
                    textSize = 22f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(220, 255, 255, 255)
                    textSize = 30f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                ks.forEachIndexed { idx, k ->
                    val x0 = colW * idx + colW * 0.06f
                    val x1 = colW * (idx + 1) - colW * 0.06f
                    val cw = x1 - x0
                    val cx = (x0 + x1) / 2f

                    drawLine(Color(Brand.WHITE_MUTED).copy(alpha = 0.45f), Offset(x0, axisY), Offset(x1, axisY), 1.5f)
                    var t = -halfHz
                    while (t <= halfHz + 1e-9) {
                        val x = cx + (t / halfHz).toFloat() * (cw / 2f)
                        val halves = kotlin.math.round(t * 2).toInt()
                        val major = halves % 2 == 0
                        drawLine(
                            Color(Brand.WHITE_MUTED).copy(alpha = if (major) 0.6f else 0.22f),
                            Offset(x, axisY), Offset(x, axisY + if (major) 12f else 6f), 1.5f,
                        )
                        if (major && halves != 0) drawContext.canvas.nativeCanvas.drawText(
                            "%+d".format(halves / 2), x, axisY + 36f, paint,
                        )
                        t += 0.5
                    }
                    drawLine(Color(Brand.WHITE_MUTED).copy(alpha = 0.3f), Offset(cx, h * 0.06f), Offset(cx, axisY), 1f)
                    drawContext.canvas.nativeCanvas.drawText("partial $k", cx, h * 0.045f + 24f, labelPaint)

                    val pair = measured[k] ?: return@forEachIndexed
                    val offs = pair.first; val prof = pair.second
                    if (prof.size > 2) {
                        val spread = if (offs.size >= 2) offs.last() - offs.first() else 0.0
                        val congruent = offs.size < 2 || kotlin.math.abs(spread) < 0.15
                        val col = if (congruent) Color(Brand.GO_GREEN) else Color(Brand.ORANGE)
                        val peakH = axisY - h * 0.12f
                        val path = Path()
                        for (i in prof.indices) {
                            val x = x0 + cw * i / (prof.size - 1).toFloat()
                            val y = axisY - prof[i].toFloat() * peakH
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        val fill = Path().apply { addPath(path); lineTo(x1, axisY); lineTo(x0, axisY); close() }
                        drawPath(fill, col.copy(alpha = 0.22f))
                        drawPath(path, col, style = Stroke(width = 3f))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            SyncLine(syncState, onCheck = { sync.checkString() }, onForget = { sync.forgetChecks() })
            Spacer(Modifier.height(6.dp))
            // Twelve partials across the width; nothing scrolls on this app's screens.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val baseHz = if (heldHz > 0.0) heldHz else (detected ?: 0.0)
                for (k in 1..CongruenceScope.PARTIALS) {
                    val on = scope.isOn(k)
                    val tint = if (on) Color(Brand.ORANGE) else Color(Brand.WHITE_MUTED)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedButton(
                            onClick = { scope.toggle(k); frame++ },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
                        ) {
                            val name = if (baseHz > 0.0)
                                Notes.name(Notes.nearestMidi(baseHz * k, refA4)) else "-"
                            Text(name, color = tint, fontFamily = DejaVuSerif, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Text(k.toString(), color = tint, fontFamily = DejaVuSerif, fontSize = 10.sp, maxLines = 1)
                        // the partial's sync: green steady, orange beating, blue the string's own beat
                        val p = syncState.partials.firstOrNull { it.k == k }
                        Box(
                            Modifier.padding(top = 2.dp).fillMaxWidth(0.7f).height(6.dp)
                                .background(syncColor(p), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
}

private fun syncColor(p: UnisonSync.Partial?): Color = when {
    p == null || !p.heard -> Color(Brand.WHITE_MUTED).copy(alpha = 0.18f)
    p.ownBeat -> Color(Brand.TARGET_BLUE)
    p.steady -> Color(Brand.GO_GREEN)
    else -> Color(Brand.ORANGE)
}

/**
 * The sync line: how many partials of the strike are steady, the best of
 * this note so far, and the single-string check. A partial marked as a
 * string's own beat (blue) is left out of the count.
 */
@Composable
private fun SyncLine(s: UnisonSync.State, onCheck: () -> Unit, onForget: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val all = s.counted >= 2 && s.inSync == s.counted
        Text(
            if (s.counted == 0) "strike the unison" else "${s.inSync} of ${s.counted} in sync",
            color = if (all) Color(Brand.GO_GREEN) else Color(Brand.WHITE),
            fontFamily = DejaVuSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
        Spacer(Modifier.width(16.dp))
        val bestAt = s.bestAtS
        if (bestAt != null) {
            val t = bestAt.toInt()
            Text(
                "best ${s.best} of ${s.bestOf} at %d:%02d".format(t / 60, t % 60),
                color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 15.sp, maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        if (s.stringsChecked > 0) {
            Text(
                "${s.stringsChecked} string${if (s.stringsChecked == 1) "" else "s"} checked",
                color = Color(Brand.TARGET_BLUE), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onForget, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                Text("Forget", color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
        }
        OutlinedButton(onClick = onCheck, enabled = !s.checking, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
            Text(
                if (s.checking) "strike one string alone…" else "String alone",
                color = Color(Brand.TARGET_BLUE), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 1,
            )
        }
    }
}
