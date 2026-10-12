package at.clavierhaus.unisonmaster.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The microphone as a pump: opens the [AudioSource] and hands every buffer
 * to whoever has [add]ed themselves, in the order they came. Nothing is
 * analysed here; the analysers (another screen, a recorder) are taps.
 *
 * This is all those analysers ever used of the retired PartialMonitor —
 * start, stop, add a tap, a listening flag — so this is all that remains of
 * it. One pump per microphone: the source cannot be open twice, so a screen
 * that pumps here must have the tuning controller's live capture stopped.
 */
class AudioTap(
    private val source: AudioSource,
    /** Samples per buffer; the hop of the readers behind the taps. */
    private val hop: Int = 1024,
) {
    val sampleRateHz: Int get() = source.sampleRateHz

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    // Replaced, never mutated: the audio thread reads the list it holds
    // without a lock while a tap may be added from the UI thread.
    private var taps: List<(FloatArray) -> Unit> = emptyList()

    fun add(tap: (FloatArray) -> Unit) { taps = taps + tap }
    fun remove(tap: (FloatArray) -> Unit) { taps = taps - tap }

    /**
     * Opens the microphone. False when the input could not be opened
     * (permission not yet granted); safe to call again.
     */
    fun start(): Boolean {
        if (_listening.value) return true
        _listening.value = true
        return try {
            source.start(hop) { chunk ->
                if (!_listening.value) return@start
                val attached = taps
                for (tap in attached) tap(chunk)
            }
            true
        } catch (e: Exception) {
            _listening.value = false
            false
        }
    }

    fun stop() {
        if (!_listening.value) return
        _listening.value = false
        source.stop()
    }
}
