package at.clavierhaus.unisonmaster.app

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.ui.TextBlock
import java.io.File

/*
 * What went wrong, said on the next start (Peter, 27 Oct 2026, after "0.5.0 crashed twice" in the Piano World
 * forum with nothing to go on). The F-Droid build has no crash reporting and must not have any that sends: the
 * error is written to one private file on the phone when the app stops, and the next start shows it with a
 * button to copy it. Nothing leaves the phone unless the tester pastes it somewhere. Both editions install it.
 */
object CrashNote {
    private const val FILE = "last-crash.txt"

    /** Keeps every uncaught error of the app in [Context.getFilesDir], then lets the phone handle it as before. */
    fun install(context: Context, version: String) {
        val dir = context.applicationContext.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { dir.mkdirs(); File(dir, FILE).writeText(report(version, thread.name, error, System.currentTimeMillis())) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The note: the app's version, the phone, the moment, the thread and the error with its trace. */
    fun report(version: String, threadName: String, error: Throwable, atMs: Long): String = buildString {
        append("Clavier Tuner ").append(version).append('\n')
        append("Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        append("time ").append(atMs).append(", thread ").append(threadName).append("\n\n")
        append(error.stackTraceToString())
    }

    /** The last crash's note, or null when the app did not stop on an error since the note was last closed. */
    fun read(context: Context): String? = File(context.applicationContext.filesDir, FILE).takeIf { it.isFile }?.readText()

    /**
     * What the window shows of [note]: its head (version, phone, moment), the error and the first lines of its trace --
     * nothing on a screen scrolls (the law walls), and Copy takes the whole note.
     */
    fun extract(note: String, traceLines: Int = 4): List<String> {
        val lines = note.lines().filter { it.isNotBlank() }
        val head = lines.take(4)
        val rest = lines.drop(4)
        return head + rest.take(1 + traceLines).map { it.trim() }
    }

    /** Forgets the note: the tester has seen it. */
    fun clear(context: Context) { File(context.applicationContext.filesDir, FILE).delete() }
}

/** The window that shows the last crash's note on the next start, with Copy and Close; nothing when there is none. */
@Composable
fun CrashNoteDialog() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var note by remember { mutableStateOf(CrashNote.read(context)) }
    val text = note ?: return
    val close = { CrashNote.clear(context); note = null }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextBlock(listOf(t(K.crash_title)), Modifier.fillMaxWidth(), size = 18.sp, minSize = 14.sp)
            TextBlock(listOf(t(K.crash_text)), Modifier.fillMaxWidth(), size = 14.sp, minSize = 11.sp, color = Color(Brand.WHITE_MUTED))
            Box(Modifier.fillMaxWidth().height(150.dp).background(Color(Brand.BLACK)).padding(8.dp)) {
                TextBlock(CrashNote.extract(text), Modifier.fillMaxWidth(), size = 12.sp, minSize = 10.sp,
                    color = Color(Brand.WHITE_MUTED), fontFamily = FontFamily.Monospace, gap = 1.dp)
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chip(t(K.crash_copy), selected = true, enabled = true, onClick = { clipboard.setText(AnnotatedString(text)) })
                Chip(t(K.crash_close), selected = false, enabled = true, onClick = close)
            }
        }
    }
}
