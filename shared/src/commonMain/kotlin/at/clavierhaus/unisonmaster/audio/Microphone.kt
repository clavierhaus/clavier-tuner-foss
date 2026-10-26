package at.clavierhaus.unisonmaster.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which microphone the platform's source actually opened, so the screen can say it (26 Oct 2026). The Core
 * Directive admits no processing between the string and the reading, and the Android source falls back to the
 * phone's ordinary, processed microphone when the unprocessed one cannot be opened; until now nothing said so,
 * and a tester's phone (Gregor's Motorola) could not be told apart from a phone that measures cleanly.
 */
object Microphone {
    enum class Opened {
        /** The microphone has not been opened yet: it runs only on the tuning screen. */
        NOT_YET,
        /** The unprocessed source: no gain control, no noise suppression. */
        UNPROCESSED,
        /** The phone's ordinary microphone, with whatever processing the phone applies. */
        PROCESSED,
    }

    private val state = MutableStateFlow(Opened.NOT_YET)
    val opened: StateFlow<Opened> = state

    /** Said by the platform source each time it opens the microphone. */
    fun opened(which: Opened) { state.value = which }
}
