package at.clavierhaus.unisonmaster.audio

/**
 * iOS capture path — stub until the iOS app is built.
 *
 * Implementation plan (the whole reason this file is ~100 lines when done):
 *  - AVAudioSession: category .playAndRecord (or .record), mode .measurement
 *    -> disables AGC and voice processing, Apple's equivalent of Android's
 *    UNPROCESSED source. Set preferredSampleRate to 48_000.
 *  - AVAudioEngine: installTap(onBus: 0) on the inputNode, convert the tap's
 *    AVAudioPCMBuffer (float32) to FloatArray and forward to onBuffer.
 *  - Everything above this file (Goertzel, YIN, PartialAnalyzer,
 *    TuningController) runs unchanged.
 */
class IosAudioSource(override val sampleRateHz: Int) : AudioSource {
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        TODO("Implement with AVAudioEngine + AVAudioSession .measurement mode")
    }

    override fun stop() {
        TODO("Implement with AVAudioEngine + AVAudioSession .measurement mode")
    }
}

actual fun createAudioSource(sampleRateHz: Int): AudioSource = IosAudioSource(sampleRateHz)
