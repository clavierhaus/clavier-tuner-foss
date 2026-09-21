package at.clavierhaus.unisonmaster.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import at.clavierhaus.unisonmaster.research.StrikeProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the tuning screen's microphone signal, as it arrives and before
 * anything is read from it, to one WAV per recording in
 * /sdcard/Recordings/ClavierTuner: uncompressed PCM, 48 kHz, 16 bit, mono,
 * the unprocessed source when the phone has one. Started and stopped by the
 * red button; [push] is the controller's tap and costs nothing while idle.
 *
 * The samples are written as they come, so a recording is on disk up to the
 * moment the phone is put down; the header is completed on stop, and the
 * file is shown to the phone's media only then. [unprocessed] says, when
 * asked, whether the microphone is on the unprocessed source: it names the
 * file, by the research recordings' convention.
 */
class SessionRecorder(private val context: Context, private val sampleRate: Int, private val unprocessed: () -> Boolean) {

    private val _recording = MutableStateFlow(false)
    /** True from the button's first tap to its second. */
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _seconds = MutableStateFlow(0)
    /** Whole seconds written so far, for the display. */
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    /** The file of the last finished recording, e.g. "session_20260921-151203_unproc.wav". */
    var lastFile: String? = null
        private set

    private class Open(val uri: android.net.Uri, val pfd: ParcelFileDescriptor, val out: FileOutputStream) {
        var samples = 0L
        val bytes: ByteBuffer = ByteBuffer.allocate(8192 * 2).order(ByteOrder.LITTLE_ENDIAN)
    }

    @Volatile
    private var open: Open? = null
    private val lock = Any()

    /** Opens the file and starts writing. Returns false if storage refused. */
    fun start(): Boolean = synchronized(lock) {
        if (open != null) return@synchronized true
        if (Build.VERSION.SDK_INT < 29) return@synchronized false
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val name = "session_${stamp}_${if (unprocessed()) "unproc" else "mic"}.wav"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-wav")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${StrikeProtocol.DIRECTORY}/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return@synchronized false
        val pfd = runCatching { resolver.openFileDescriptor(uri, "rw") }.getOrNull()
        if (pfd == null) { resolver.delete(uri, null, null); return@synchronized false }
        val out = FileOutputStream(pfd.fileDescriptor)
        val o = Open(uri, pfd, out)
        if (runCatching { out.write(header(0)) }.isFailure) { close(o, keep = false); return@synchronized false }
        lastFile = name
        _seconds.value = 0
        open = o
        _recording.value = true
        true
    }

    /** From the capture thread: converts and appends one buffer. */
    fun push(chunk: FloatArray) {
        val o = open ?: return
        synchronized(lock) {
            if (open !== o) return
            val b = o.bytes
            var i = 0
            while (i < chunk.size) {
                b.clear()
                val n = minOf(chunk.size - i, b.capacity() / 2)
                for (j in 0 until n) {
                    val v = (chunk[i + j].coerceIn(-1f, 1f) * 32767f).let { if (it >= 0) it + 0.5f else it - 0.5f }.toInt()
                    b.putShort(v.toShort())
                }
                if (runCatching { o.out.write(b.array(), 0, n * 2) }.isFailure) { stop(); return }
                i += n
            }
            o.samples += chunk.size
            val s = (o.samples / sampleRate).toInt()
            if (s != _seconds.value) _seconds.value = s
        }
    }

    /** Completes the header and shows the file to the phone. A recording of nothing is discarded. */
    fun stop() {
        synchronized(lock) {
            val o = open ?: return
            open = null
            _recording.value = false
            close(o, keep = o.samples > 0)
        }
    }

    private fun close(o: Open, keep: Boolean) {
        runCatching {
            o.out.channel.position(0)
            o.out.write(header(o.samples))
            o.out.flush()
        }
        runCatching { o.out.close() }
        runCatching { o.pfd.close() }
        val resolver = context.contentResolver
        if (keep) {
            resolver.update(o.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } else {
            resolver.delete(o.uri, null, null)
        }
    }

    /** The 44-byte RIFF header for [samples] 16-bit mono samples. */
    private fun header(samples: Long): ByteArray {
        val data = (samples * 2).coerceAtMost(Int.MAX_VALUE - 44L).toInt()
        val out = ByteArray(44)
        fun str(at: Int, s: String) = s.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
        fun u32(at: Int, v: Int) { for (i in 0..3) out[at + i] = (v ushr (8 * i)).toByte() }
        fun u16(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v ushr 8).toByte() }
        str(0, "RIFF"); u32(4, 36 + data); str(8, "WAVE")
        str(12, "fmt "); u32(16, 16); u16(20, 1); u16(22, 1); u32(24, sampleRate); u32(28, sampleRate * 2); u16(32, 2); u16(34, 16)
        str(36, "data"); u32(40, data)
        return out
    }
}
