package at.clavierhaus.claviertuner.settings

import at.clavierhaus.claviertuner.tuning.NamedTemperament

/**
 * Ron Koval's Schubert concert tune, chosen as a temperament (Peter, 23 Nov 2026, 19:57: "the Schubert is just another
 * temperament, difference being that you had to do more math in getting it into temperament"). It measures nothing and
 * reads no recording: it is equal temperament with Ron Koval's octaves, as he gave them on Piano World ("Re: Schubert concert
 * tune", 22 April 2014, forum.pianoworld.com topic 2265220), worked out from the Verituner file of the pianist's own piano:
 * "the temperament is set a bit narrower than normal ... about 70% 4:2 and 30% 2:1. Going up, transition to a pure 4:2 octave
 * by A5. By A6 ... about 60% 4:1 and 40% 4:2. Transition to a 4:1 double octave by the top. Going down ... a pure 6:3 by A2
 * ... 80% 4:1 by A1. For the bottom octave ... about 75% [12:6], checked with the 6:3 about 25%." He gives no widths, so
 * every width is nought; the keys between the places blend by key. Every target is still worked out by the engine from the
 * inharmonicity the sampling measured.
 *
 * Another temperament chosen after it takes his octaves out and the stretch returns to the six points (Peter, the same
 * evening: "Back to six points"); a stretch preset chosen takes them out too (StretchPreset). From 11:38 to 19:52 the same
 * octaves were a stretch preset of their own.
 */
object SchubertTune {
    /** Whether [s] stands on Ron Koval's octaves: any octave mixed of two types, which nothing else sets. */
    fun isOn(s: TunerSettings): Boolean =
        s.mixBass != null || s.mixMiddle != null || s.mixTreble != null || s.stretchPoints.any { it.mix != null }

    /** [s] in equal temperament with Ron Koval's octaves; everything else stays as it was. */
    fun applyTo(s: TunerSettings): TunerSettings = s.copy(
        temperament = NamedTemperament.EQUAL,
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

    /** [s] in [temperament]: from Ron Koval's octaves back to the six points, else only the temperament changes. */
    fun withTemperament(s: TunerSettings, temperament: NamedTemperament): TunerSettings =
        (if (isOn(s)) s.withSixPoints(true) else s).copy(temperament = temperament)

    private const val MIDI_A1 = 33
    private const val MIDI_A2 = 45
    private const val MIDI_A5 = 81
    private const val MIDI_A6 = 93
}
