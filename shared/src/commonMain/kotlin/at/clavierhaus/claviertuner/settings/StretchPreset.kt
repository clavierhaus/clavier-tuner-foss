package at.clavierhaus.claviertuner.settings


/*
 * The stretch presets (23 Nov 2026): three points, six points, and Ron Koval's Schubert concert tune; in the free app since
 * 23 Nov 2026 too (Peter: tuning is open source, all of it).
 * It measures nothing and reads no recording: a preset is octave types, their shares and their places, the tuner's choice
 * as the six points are; every target is still worked out by the engine from the inharmonicity the sampling measured
 * (TuningSession.curveTargetF1). Peter, 23 Nov 2026, 11:38: "It should be a ready made preset and Ron Koval should be
 * credited for it."
 */
enum class StretchPreset {
    THREE, SIX, SCHUBERT;

    /** [s] with this preset's stretch; everything that is not the stretch stays as it was. */
    fun applyTo(s: TunerSettings): TunerSettings = when (this) {
        THREE -> s.withSixPoints(false)
        SIX -> s.withSixPoints(true)
        SCHUBERT -> schubert(s)
    }

    companion object {
        /** The preset [s] stands on: Schubert while any octave is mixed, else six points while there are places, else three. */
        fun of(s: TunerSettings): StretchPreset = when {
            s.mixBass != null || s.mixMiddle != null || s.mixTreble != null || s.stretchPoints.any { it.mix != null } -> SCHUBERT
            s.hasPlaces -> SIX
            else -> THREE
        }

        /**
         * Ron Koval's Schubert concert tune, as he gave it in aural terms on Piano World ("Re: Schubert concert tune",
         * 22 April 2014, forum.pianoworld.com topic 2265220), worked out from the Verituner file of the pianist's own
         * piano: "the temperament is set a bit narrower than normal ... about 70% 4:2 and 30% 2:1. Going up, transition
         * to a pure 4:2 octave by A5. By A6 ... about 60% 4:1 and 40% 4:2. Transition to a 4:1 double octave by the top.
         * Going down ... a pure 6:3 by A2 ... 80% 4:1 by A1. For the bottom octave ... about 75% [12:6], checked with
         * the 6:3 about 25%." He gives no widths, so every width is nought. The keys between the places blend by key, as
         * his "transition" says.
         */
        fun schubert(s: TunerSettings): TunerSettings = s.copy(
            octaveBass = OctaveType.O6_3, octaveMiddle = OctaveType.O4_2, octaveTreble = OctaveType.O4_1,
            widthBass = 0.0, widthBassFar = null, widthMiddle = 0.0, widthTreble = 0.0, widthTrebleFar = null,
            mixMiddle = OctaveShare(OctaveType.O2_1, 0.3),
            mixBass = OctaveShare(OctaveType.O12_6, 0.75),
            mixTreble = null,
            stretchPoints = listOf(
                StretchPoint(MIDI_A1, OctaveType.O4_1, 0.0, OctaveShare(OctaveType.O6_3, 0.2)),
                StretchPoint(MIDI_A2, OctaveType.O6_3, 0.0),
                StretchPoint(MIDI_A5, OctaveType.O4_2, 0.0),
                StretchPoint(MIDI_A6, OctaveType.O4_1, 0.0, OctaveShare(OctaveType.O4_2, 0.4)),
            ),
        )

        private const val MIDI_A1 = 33
        private const val MIDI_A2 = 45
        private const val MIDI_A5 = 81
        private const val MIDI_A6 = 93
    }
}
