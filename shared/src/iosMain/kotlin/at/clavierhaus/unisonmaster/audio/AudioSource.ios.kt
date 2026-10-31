@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package at.clavierhaus.unisonmaster.audio

import kotlinx.cinterop.get
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.sampleRate
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredSampleRate

/**
 * The iPhone's microphone, as the engine needs it (31 Oct 2026): mono float buffers, unprocessed. The audio session
 * runs in Apple's *measurement* mode, which turns off the automatic gain, the voice processing and the equaliser --
 * the iPhone's counterpart of Android's UNPROCESSED source. The tap takes the input in the hardware's own format and
 * sample rate; nothing is resampled (the Core Directive). The session asks for 48 kHz; [sampleRateHz] is the rate the
 * hardware actually runs at, which the engine's readers take as a parameter.
 *
 * Only the first channel is read, as everywhere in the engine. The tap's buffers arrive in whatever length the system
 * chooses; they are cut into the [bufferSize] the engine asks for.
 */
class IosAudioSource(requestedRateHz: Int) : AudioSource {
    private val session = AVAudioSession.sharedInstance().also { s ->
        s.setCategory(AVAudioSessionCategoryRecord, AVAudioSessionModeMeasurement, 0u, null)
        s.setPreferredSampleRate(requestedRateHz.toDouble(), null)
        s.setActive(true, null)
    }

    override val sampleRateHz: Int = session.sampleRate.toInt().takeIf { it > 0 } ?: requestedRateHz

    private var engine: AVAudioEngine? = null

    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        if (engine != null) return
        session.setActive(true, null)
        val e = AVAudioEngine()
        val input = e.inputNode
        val format = input.outputFormatForBus(0u)
        val out = FloatArray(bufferSize)
        var filled = 0
        input.installTapOnBus(0u, bufferSize.toUInt(), format) { buffer, _ ->
            val b = buffer ?: return@installTapOnBus
            val data = b.floatChannelData ?: return@installTapOnBus
            val ch0 = data[0] ?: return@installTapOnBus
            val n = b.frameLength.toInt()
            var i = 0
            while (i < n) {
                out[filled++] = ch0[i++]
                if (filled == bufferSize) { onBuffer(out); filled = 0 }
            }
        }
        e.prepare()
        if (!e.startAndReturnError(null)) { input.removeTapOnBus(0u); return }
        engine = e
    }

    override fun stop() {
        engine?.let { e ->
            e.inputNode.removeTapOnBus(0u)
            e.stop()
        }
        engine = null
    }
}

actual fun createAudioSource(sampleRateHz: Int): AudioSource = IosAudioSource(sampleRateHz)
