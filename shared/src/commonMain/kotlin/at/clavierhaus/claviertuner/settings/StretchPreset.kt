package at.clavierhaus.claviertuner.settings

/*
 * The stretch presets: three points and six points (23 Nov 2026). It measures nothing and reads no recording: a preset is
 * octave types, their widths and their places, the tuner's choice; every target is still worked out by the engine from the
 * inharmonicity the sampling measured (TuningSession.curveTargetF1).
 *
 * A third preset stood here on 23 Nov 2026 and is gone. The engine keeps the octave mixes (OctaveShare, 12:6, a 4:1 place in
 * the bass), offered by no screen.
 */
enum class StretchPreset {
    THREE, SIX;

    /** [s] with this preset's stretch, every mix taken out with it; everything that is not the stretch stays as it was. */
    fun applyTo(s: TunerSettings): TunerSettings = when (this) {
        THREE -> s.withSixPoints(false)
        SIX -> s.withSixPoints(true)
    }

    companion object {
        /** The preset [s] stands on: six points while there are places, else three. */
        fun of(s: TunerSettings): StretchPreset = if (s.hasPlaces) SIX else THREE
    }
}
