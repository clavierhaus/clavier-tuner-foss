package at.clavierhaus.unisonmaster.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.audio.AndroidAudioSource
import at.clavierhaus.unisonmaster.research.Piano
import at.clavierhaus.unisonmaster.research.StrikeProtocol
import at.clavierhaus.unisonmaster.research.Take
import at.clavierhaus.unisonmaster.research.Wav
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.ui.BackArrow
import at.clavierhaus.unisonmaster.ui.DejaVuSerifFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

/*
 * Research: the wobble study. Records single strikes as raw 48 kHz WAV into
 * /sdcard/Recordings/ClavierTuner/ (Music/ClavierTuner on Android 10-11),
 * walking the tuner through StrikeProtocol with the instructions on screen.
 * Progress survives restarts. To be hidden once the study is done.
 */

private sealed class Phase {
    data object Idle : Phase()
    data class Recording(val seconds: Float, val of: Int, val levelDb: Float) : Phase()
    data object Saving : Phase()
    data class Saved(val name: String) : Phase()
    data class Failed(val message: String) : Phase()
}

private val Muted = Color(Brand.WHITE_MUTED)
private val White = Color(Brand.WHITE)
private val Orange = Color(Brand.ORANGE)
private val Panel = Color(0xFF1A1A1A)

@Composable
fun RecordStrikesScreen(firstPlainMidi: Int, micGranted: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wobble-study", Context.MODE_PRIVATE) }
    // progress is kept per piano; the first sessions (Steinway D) stored theirs under "done"
    fun loadDone(p: Piano): Set<String> =
        (prefs.getStringSet("done-${p.code}", null) ?: if (p == Piano.STEINWAY_D) prefs.getStringSet("done", emptySet()) else emptySet())!!.toSet()
    var piano by remember { mutableStateOf(Piano.ofCode(prefs.getString("piano", "D"))) }
    val takes = remember(piano, firstPlainMidi) { StrikeProtocol.takes(piano, firstPlainMidi) }
    var done by remember(piano) { mutableStateOf(loadDone(piano)) }
    var index by remember(piano, takes) { mutableIntStateOf(takes.indexOfFirst { it.id !in done }.let { if (it < 0) 0 else it }) }
    var phase by remember { mutableStateOf<Phase>(Phase.Idle) }
    var lastName by remember { mutableStateOf(prefs.getString("last", null)) }
    val scope = rememberCoroutineScope()
    val take = takes[index]
    val busy = phase is Phase.Recording || phase is Phase.Saving

    fun record() {
        if (busy || !micGranted) return
        val source = AndroidAudioSource(StrikeProtocol.SAMPLE_RATE)
        val seconds = StrikeProtocol.seconds(piano, take.midi)
        val total = StrikeProtocol.SAMPLE_RATE * seconds
        val samples = FloatArray(total)
        var filled = 0
        phase = Phase.Recording(0f, seconds, -99f)
        source.start(2048) { chunk ->
            val n = minOf(chunk.size, total - filled)
            chunk.copyInto(samples, filled, 0, n)
            filled += n
            var sq = 0.0
            for (x in chunk) sq += x.toDouble() * x
            val db = (20 * log10(maxOf(sqrt(sq / chunk.size), 1e-9))).toFloat()
            phase = Phase.Recording(filled.toFloat() / StrikeProtocol.SAMPLE_RATE, seconds, db)
            if (filled >= total) {
                source.stop()
                val unprocessed = source.usedUnprocessed
                phase = Phase.Saving
                scope.launch {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                    val name = StrikeProtocol.fileName(piano, take, unprocessed, stamp)
                    val result = withContext(Dispatchers.IO) {
                        runCatching { saveRecording(context, name, Wav.pcm16(samples, StrikeProtocol.SAMPLE_RATE)) }
                    }
                    phase = result.fold(
                        onSuccess = {
                            done = done + take.id
                            lastName = name
                            prefs.edit().putStringSet("done-${piano.code}", done).putString("last", name).apply()
                            val next = takes.indexOfFirst { it.id !in done }
                            if (next >= 0) index = next
                            Phase.Saved(name)
                        },
                        onFailure = { Phase.Failed(it.message ?: it.toString()) },
                    )
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Color(Brand.BLACK))
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        // ---- left: what to do ----
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackArrow(onClick = { if (!busy) onBack() })
                Text("Record Strikes", color = White, fontFamily = DejaVuSerifFamily, fontSize = 26.sp)
            }
            Text(
                "Wobble study: ${takes.size} recordings of single strings, ${StrikeProtocol.STRIKES} strikes each.",
                color = Muted, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text("SETUP", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            StrikeProtocol.setup.forEachIndexed { i, line ->
                Row(Modifier.padding(top = 4.dp)) {
                    Text("${i + 1}", color = Orange, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.width(20.dp))
                    Text(line, color = White, fontSize = 13.sp, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Files: /sdcard/${StrikeProtocol.DIRECTORY}/ — collect them on the X1 with scripts/collect-recordings.sh",
                color = Muted, fontSize = 12.sp,
            )
        }

        Spacer(Modifier.width(24.dp))

        // ---- right: this take ----
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in Piano.entries) {
                    Chip(p.label, selected = p == piano, enabled = !busy) {
                        piano = p
                        prefs.edit().putString("piano", p.code).apply()
                        phase = Phase.Idle
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Take ${index + 1} of ${takes.size}  ·  ${done.size} done", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("◀", selected = false, enabled = !busy && index > 0) { index--; phase = Phase.Idle }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Notes.name(take.midi)}  ·  ${take.string.label}",
                        color = if (take.id in done) Muted else White,
                        fontFamily = DejaVuSerifFamily, fontSize = 30.sp,
                    )
                    Text(
                        "strike ${take.strike} of ${StrikeProtocol.STRIKES}  ·  ${StrikeProtocol.seconds(piano, take.midi)} s" +
                            if (take.id in done) "  ·  recorded" else "",
                        color = Muted, fontSize = 16.sp,
                    )
                }
                Chip("▶", selected = false, enabled = !busy && index < takes.size - 1) { index++; phase = Phase.Idle }
            }
            Spacer(Modifier.height(8.dp))
            Text(take.string.instruction, color = White, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))

            val status = when (val p = phase) {
                Phase.Idle -> if (micGranted) "Ready." else "Microphone permission missing — allow it in Android settings."
                is Phase.Recording ->
                    if (p.seconds < 0.5f) "Recording …" else "Strike now — hold the key.  %.1f / %d s".format(p.seconds, p.of)
                Phase.Saving -> "Saving …"
                is Phase.Saved -> "Saved ${p.name}"
                is Phase.Failed -> "Not saved: ${p.message}"
            }
            Text(
                status,
                color = when (phase) { is Phase.Failed -> Color(Brand.ALERT_RED); is Phase.Recording -> Orange; else -> White },
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            LevelBar((phase as? Phase.Recording)?.levelDb ?: -99f)

            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .height(56.dp)
                        .width(180.dp)
                        .background(if (busy || !micGranted) Color(0xFF3A3A3A) else Orange, RoundedCornerShape(28.dp))
                        .clickable(enabled = !busy && micGranted) { record() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (phase is Phase.Recording) "Recording" else "Record",
                        color = Color(Brand.BLACK), fontFamily = DejaVuSerifFamily, fontSize = 20.sp,
                    )
                }
                Spacer(Modifier.width(16.dp))
                // Redo: step back to the take that was just saved and record it again
                val saved = phase as? Phase.Saved
                if (saved != null) {
                    Chip("Redo last", selected = false, enabled = true) {
                        val id = saved.name.split('_').getOrNull(1)
                        val i = takes.indexOfFirst { it.id == id }
                        if (i >= 0) {
                            index = i
                            done = done - takes[i].id
                            prefs.edit().putStringSet("done-${piano.code}", done).apply()
                        }
                        phase = Phase.Idle
                    }
                }
                Spacer(Modifier.weight(1f))
                Chip("Start over", selected = false, enabled = !busy && done.isNotEmpty()) {
                    done = emptySet()
                    prefs.edit().putStringSet("done-${piano.code}", emptySet()).putStringSet("done", emptySet()).apply()
                    index = 0
                    phase = Phase.Idle
                }
            }
            if (lastName != null && phase !is Phase.Saved) {
                Spacer(Modifier.height(6.dp))
                Text("Last file: $lastName", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .background(
                when { selected -> Orange; enabled -> Color(0xFF333333); else -> Color(0xFF1F1F1F) },
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color(Brand.BLACK) else if (enabled) White else Muted, fontSize = 16.sp)
    }
}

/** Input level, −60 … 0 dBFS. */
@Composable
private fun LevelBar(db: Float) {
    val fraction = ((db + 60f) / 60f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF2A2A2A), RoundedCornerShape(5.dp))) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(10.dp)
                .background(if (db > -3f) Color(Brand.ALERT_RED) else Color(Brand.GO_GREEN), RoundedCornerShape(5.dp)),
        )
    }
}

/** Writes into shared storage: /sdcard/Recordings/ClavierTuner (Android 12+), else /sdcard/Music/ClavierTuner. */
private fun saveRecording(context: Context, name: String, bytes: ByteArray) {
    check(Build.VERSION.SDK_INT >= 29) { "recording needs Android 10 or later" }
    val relative = if (Build.VERSION.SDK_INT >= 31) "${StrikeProtocol.DIRECTORY}/" else "Music/ClavierTuner/"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-wav")
        put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        ?: error("storage refused the file")
    try {
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
}
