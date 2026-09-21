package at.clavierhaus.unisonmaster.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal persistent key-value storage; the app supplies the implementation. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class MemoryStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
}

/**
 * Octave types a stretch can be built on: partial [low] of the lower note
 * coincides with partial [high] of the note [semitones] above.
 */
enum class OctaveType(val label: String, val low: Int, val high: Int, val semitones: Int = 12) {
    O2_1("2:1", 2, 1), O4_2("4:2", 4, 2), O6_3("6:3", 6, 3), O8_4("8:4", 8, 4), O10_5("10:5", 10, 5),
    /** The double octave. */
    O4_1("4:1", 4, 1, 24),
}

/**
 * Everything the tuner sets in Settings. Values that determine a target
 * frequency live in the Temperament tab. Stretch and interval weights are
 * stored now and applied when stretch lands.
 */
data class TunerSettings(
    // TEMPERAMENT
    /** Lowest note of the temperament octave; the session runs from A4 down to here. */
    val temperamentLowMidi: Int = 57,          // A3
    /**
     * The temperament octave must be finished before any note outside it can
     * be tuned. FOSS pins this true — it takes temperament and inharmonicity
     * from A3–A4 only, and that promise is only kept if A3–A4 is complete.
     * Pro sets it false and samples where the tuner likes.
     */
    val temperamentFirst: Boolean = true,
    // STRETCH
    val octaveBass: OctaveType = OctaveType.O6_3,
    val octaveMiddle: OctaveType = OctaveType.O4_2,
    val octaveTreble: OctaveType = OctaveType.O4_1,
    /** Interval weights 0..10 for the stretch calculation. */
    val weightOctave: Int = 10,
    val weightTwelfth: Int = 4,
    val weightDoubleOctave: Int = 6,
    val weightFifth: Int = 2,
    // PIANO
    /**
     * Lowest plain (unwound) string; wound strings below it follow a different
     * physics. The session runs down to here; the bass octave type takes over
     * one octave above it.
     */
    val lowestUnwoundMidi: Int = 43,           // G2
    /**
     * The instrument's lowest key: A0 on the 88-key piano, F0 on the
     * Bösendorfer 225, C0 on the 290. The session runs down to here; the
     * wound strings between it and the lowest plain string are tuned like
     * any other, each against the note an octave above, and are kept out of
     * the plain-wire inharmonicity curve.
     */
    val lowestKeyMidi: Int = 21,               // A0
    // PRECISION
    /** A partial matches within this of its target, Hz. */
    val matchHz: Double = 0.1,
    /** Suggestion: a partial must peak within this of the loudest, dB ... */
    val suggestLevelDb: Double = 30.0,
    /** ... and stay there this long, s. */
    val suggestSustainS: Double = 1.0,
    /** Partials above this note are not offered: nothing up there helps a tuning. */
    val highestPartialMidi: Int = 99,          // D#7
    /**
     * The reading shown is the mean of the readings since the strike, over at
     * most this long. Short follows the string hop by hop and shows a beating
     * unison as a swing; long stands still on it, as the ear does, and
     * follows a pin turned mid-note more slowly.
     */
    val readingS: Double = 2.0,
    // WORKFLOW
    /**
     * The tuning screen follows the key struck: the note nearest the sounding
     * fundamental is selected, and the note left is registered. Never inside
     * an unfinished temperament octave, which is walked with Done.
     */
    val autoNote: Boolean = true,
    /**
     * Pro, Stretch Definition: how many single strings across the range are
     * sampled before tuning begins. 8 is quick, 12 and 16 closer; the
     * tuner's own number, placed at the transitions he knows, is the best.
     */
    val calibrationNotes: Int = 12,
    // RECORDING
    /**
     * A red button on the tuning screen that records what the microphone
     * hears, from tap to tap: uncompressed PCM, the unprocessed source,
     * 48 kHz, 16 bit, one WAV per recording in the phone's Recordings. For
     * the workshop's corpus and for hearing again what the app read. Off by
     * default: about 6 MB a minute.
     */
    val recordPcm: Boolean = false,
) {
    companion object {
        const val MIN_TEMPERAMENT_LOW = 48     // C3
        const val MAX_TEMPERAMENT_LOW = 57     // A3
        /** Top of the temperament octave: A4, the note the session is defined on. */
        const val TEMPERAMENT_HIGH = 69        // A4
        const val MIN_LOWEST_KEY = 12          // C0, the 97-key Imperial
        const val MAX_LOWEST_KEY = 40          // E2: never above the lowest plain string in practice
        const val MIN_UNWOUND = 28             // E1
        const val MAX_UNWOUND = 60             // C4
        const val MIN_HIGHEST_PARTIAL = 84     // C6
        const val MAX_HIGHEST_PARTIAL = 108    // C8
        const val MIN_CALIBRATION_NOTES = 3
        val READING_CHOICES_S = listOf(0.25, 0.5, 1.0, 2.0, 4.0)
        const val MAX_CALIBRATION_NOTES = 40
    }

    /** True for a wound string: below the lowest plain one. */
    fun isWound(midi: Int): Boolean = midi < lowestUnwoundMidi

    /** Below this note the bass octave type applies: an octave above the lowest plain string. */
    val bassBoundaryMidi: Int get() = lowestUnwoundMidi + 12

    /**
     * Which octave type links [midi] to an already tuned note: bass within an
     * octave of the plain-wire floor, treble above the temperament octave,
     * middle in between.
     */
    fun octaveTypeFor(midi: Int): OctaveType = when {
        midi <= bassBoundaryMidi -> octaveBass
        midi > TEMPERAMENT_HIGH -> octaveTreble
        else -> octaveMiddle
    }
}

/**
 * Holds the settings, persists every change, and exposes them as a flow.
 *
 * [edition] carries what the app is rather than what the tuner chose:
 * FOSS finishes the temperament octave first, Pro samples freely. Those
 * values are never written to the store, so they cannot be changed from
 * the settings screen and cannot leak from one edition to the other.
 */
class SettingsModel(
    private val store: KeyValueStore,
    private val edition: TunerSettings = TunerSettings(),
) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TunerSettings> = _settings.asStateFlow()

    fun update(change: (TunerSettings) -> TunerSettings) {
        val s = change(_settings.value).copy(temperamentFirst = edition.temperamentFirst)
        _settings.value = s
        save(s)
    }

    private fun load(): TunerSettings {
        val d = edition
        fun i(k: String, v: Int) = store.get(k)?.toIntOrNull() ?: v
        fun f(k: String, v: Double) = store.get(k)?.toDoubleOrNull() ?: v
        fun b(k: String, v: Boolean) = store.get(k)?.toBooleanStrictOrNull() ?: v
        fun o(k: String, v: OctaveType) = store.get(k)?.let { n -> OctaveType.entries.firstOrNull { it.name == n } } ?: v
        return TunerSettings(
            temperamentFirst = d.temperamentFirst,
            temperamentLowMidi = i("temperamentLowMidi", d.temperamentLowMidi),
            octaveBass = o("octaveBass", d.octaveBass),
            octaveMiddle = o("octaveMiddle", d.octaveMiddle),
            octaveTreble = o("octaveTreble", d.octaveTreble),
            weightOctave = i("weightOctave", d.weightOctave),
            weightTwelfth = i("weightTwelfth", d.weightTwelfth),
            weightDoubleOctave = i("weightDoubleOctave", d.weightDoubleOctave),
            weightFifth = i("weightFifth", d.weightFifth),
            lowestUnwoundMidi = i("lowestUnwoundMidi", d.lowestUnwoundMidi),
            lowestKeyMidi = i("lowestKeyMidi", d.lowestKeyMidi).coerceIn(TunerSettings.MIN_LOWEST_KEY, TunerSettings.MAX_LOWEST_KEY),
            matchHz = f("matchHz", d.matchHz),
            suggestLevelDb = f("suggestLevelDb", d.suggestLevelDb),
            suggestSustainS = f("suggestSustainS", d.suggestSustainS),
            highestPartialMidi = i("highestPartialMidi", d.highestPartialMidi),
            readingS = f("readingS", d.readingS).coerceIn(TunerSettings.READING_CHOICES_S.first(), TunerSettings.READING_CHOICES_S.last()),
            autoNote = b("autoNote", d.autoNote),
            calibrationNotes = i("calibrationNotes", d.calibrationNotes).coerceIn(TunerSettings.MIN_CALIBRATION_NOTES, TunerSettings.MAX_CALIBRATION_NOTES),
            recordPcm = b("recordPcm", d.recordPcm),
        )
    }

    private fun save(s: TunerSettings) {
        store.put("temperamentLowMidi", s.temperamentLowMidi.toString())
        store.put("octaveBass", s.octaveBass.name)
        store.put("octaveMiddle", s.octaveMiddle.name)
        store.put("octaveTreble", s.octaveTreble.name)
        store.put("weightOctave", s.weightOctave.toString())
        store.put("weightTwelfth", s.weightTwelfth.toString())
        store.put("weightDoubleOctave", s.weightDoubleOctave.toString())
        store.put("weightFifth", s.weightFifth.toString())
        store.put("lowestUnwoundMidi", s.lowestUnwoundMidi.toString())
        store.put("lowestKeyMidi", s.lowestKeyMidi.toString())
        store.put("matchHz", s.matchHz.toString())
        store.put("suggestLevelDb", s.suggestLevelDb.toString())
        store.put("suggestSustainS", s.suggestSustainS.toString())
        store.put("highestPartialMidi", s.highestPartialMidi.toString())
        store.put("readingS", s.readingS.toString())
        store.put("autoNote", s.autoNote.toString())
        store.put("calibrationNotes", s.calibrationNotes.toString())
        store.put("recordPcm", s.recordPcm.toString())
    }
}
