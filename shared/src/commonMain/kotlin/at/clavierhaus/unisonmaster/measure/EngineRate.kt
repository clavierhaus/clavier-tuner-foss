package at.clavierhaus.unisonmaster.measure

/**
 * The engine's sample counts -- its hop, its analysis windows, the phase reader's longest block -- are written for
 * 48 kHz. At 48 kHz × 2^n (96, 192 kHz) each is taken 2^n times, so every window and step lasts as long as at
 * 48 kHz and every FFT keeps its power of two; at any other rate they are taken as written, as before
 * (6 Nov 2026, for an audio interface at 96 kHz: the same string read at 48 and 96 kHz gives the same readings,
 * EngineRateTest). Nothing is resampled: the engine reads the rate the source delivers.
 */
object EngineRate {
    /** The rate the sample counts are written for. */
    const val BASE_HZ = 48_000

    /** How many times the written sample counts are taken at [sampleRate]: 2^n at 48 kHz × 2^n, else 1. */
    fun scaleOf(sampleRate: Int): Int {
        if (sampleRate < BASE_HZ || sampleRate % BASE_HZ != 0) return 1
        val q = sampleRate / BASE_HZ
        return if (q and (q - 1) == 0) q else 1
    }

    /** The engine's hop at [sampleRate]: 1024 samples at 48 kHz, the same 21.3 ms at 48 kHz × 2^n. */
    fun hopOf(sampleRate: Int): Int = 1024 * scaleOf(sampleRate)
}
