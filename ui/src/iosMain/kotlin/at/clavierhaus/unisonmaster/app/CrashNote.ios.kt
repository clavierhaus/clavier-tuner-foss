@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package at.clavierhaus.unisonmaster.app

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
import at.clavierhaus.unisonmaster.ui.nowMs
import kotlin.native.setUnhandledExceptionHook
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSThread
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIDevice

/*
 * What went wrong, said on the next start, on the iPhone as on Android (CrashNote.kt; Peter, 27 Oct 2026, after
 * "0.5.0 crashed twice" with nothing to go on). An uncaught Kotlin error is written to one file in the folder the
 * app names at start, and the next start shows it with Copy and Close. Nothing leaves the phone unless the tester
 * pastes it somewhere. Until 2 Nov 2026 the iPhone kept no note at all. An error that the iPhone's own code raises
 * outside Kotlin (an Objective-C exception) is not caught here; the phone's own crash log has it.
 */
object CrashNote {
    private const val FILE = "last-crash.txt"
    private var folder: String? = null

    /** Keeps every uncaught Kotlin error in [folder], then lets the app stop as before. Both editions install it. */
    fun install(version: String, folder: String) {
        this.folder = folder
        var previous: ((Throwable) -> Unit)? = null
        previous = setUnhandledExceptionHook { error ->
            runCatching { keep(report(version, threadName(), error, nowMs())) }
            previous?.invoke(error)
        }
    }

    /** The note: the app's version, the phone, the moment, the thread and the error with its trace. */
    fun report(version: String, threadName: String, error: Throwable, atMs: Long): String = buildString {
        val d = UIDevice.currentDevice
        append("Clavier Tuner ").append(version).append('\n')
        append(d.systemName).append(' ').append(d.systemVersion).append('\n')
        append(d.model).append('\n')
        append("time ").append(atMs).append(", thread ").append(threadName).append("\n\n")
        append(error.stackTraceToString())
    }

    private fun threadName(): String =
        if (NSThread.currentThread == NSThread.mainThread) "main" else (NSThread.currentThread.name ?: "").ifEmpty { "other" }

    private fun path(): String? = folder?.let { "$it/$FILE" }

    /** Writes [note] as the last crash's note, in the folder [install] named. */
    internal fun keep(note: String) {
        val dir = folder ?: return
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        @Suppress("CAST_NEVER_SUCCEEDS")
        (note as NSString).writeToFile("$dir/$FILE", true, NSUTF8StringEncoding, null)
    }

    /** The last crash's note, or null when the app did not stop on an error since the note was last closed. */
    fun read(): String? = path()?.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
        ?.let { NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null) }

    /**
     * What the window shows of [note]: its head (version, phone, moment), the error and the first lines of its
     * trace -- nothing on a screen scrolls, and Copy takes the whole note. The same as Android's.
     */
    fun extract(note: String, traceLines: Int = 4): List<String> {
        val lines = note.lines().filter { it.isNotBlank() }
        val head = lines.take(4)
        val rest = lines.drop(4)
        return head + rest.take(1 + traceLines).map { it.trim() }
    }

    /** Forgets the note: the tester has seen it. */
    fun clear() {
        path()?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
    }
}

/** The window that shows the last crash's note on the next start, with Copy and Close; nothing when there is none. */
@Composable
actual fun CrashNoteDialog() {
    val clipboard = LocalClipboardManager.current
    var note by remember { mutableStateOf(CrashNote.read()) }
    val text = note ?: return
    val close = { CrashNote.clear(); note = null }
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
