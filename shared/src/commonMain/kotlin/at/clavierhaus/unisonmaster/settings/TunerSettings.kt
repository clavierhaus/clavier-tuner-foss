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
 * coincides with partial [high] of the note [semitones] above. The note
 * being tuned is read on its own partial of the pair — [low] when it is the
 * lower note, [high] when it is the upper one — against the measured
 * partial of the note already tuned (docs/ENGINE.md).
 */
enum class OctaveType(val label: String, val low: Int, val high: Int, val semitones: Int = 12) {
    O2_1("2:1", 2, 1), O4_2("4:2", 4, 2), O6_3("6:3", 6, 3), O8_4("8:4", 8, 4), O10_5("10:5", 10, 5),
    /** The double octave. */
    O4_1("4:1", 4, 1, 24),
}

/**
 * Everything the tuner sets in Settings. Values that determine a target
 * frequency live in the Temperament tab. The stretch is the tuner's: an
 * octave type and an octave width for each of four regions — the wound
 * strings, the plain-wire bass, the middle, the treble. Interval weights
 * are stored for a later stretch calculation and apply to nothing yet.
 */
data class TunerSettings(
    // TEMPERAMENT
    /** Lowest note of the temperament octave; the session runs from A4 down to here. */
    val temperamentLowMidi: Int = 57,          // A3
    /**
     * The temperament octave is walked first and each of its notes asks for
     * Done (leaving one does not register it): FOSS pins this true. Pro sets
     * it false: leaving any note registers it. Neither restricts where the
     * tuner may go.
     */
    val temperamentFirst: Boolean = true,
    // STRETCH
    /** The wound strings, below the lowest plain one. */
    val octaveWound: OctaveType = OctaveType.O6_3,
    /** The plain-wire bass: from the lowest plain string to an octave above it. */
    val octaveBass: OctaveType = OctaveType.O6_3,
    val octaveMiddle: OctaveType = OctaveType.O4_2,
    val octaveTreble: OctaveType = OctaveType.O4_1,
    /**
     * How wide the octave is tuned beyond the partials' coincidence, cents
     * at the partial read (0 = beatless). Wide means the lower note flatter,
     * the upper sharper. On the 225 as its tuner left it, the wound bass
     * stood some 8–12 cents wide of 6:3; that is his choice, not a default.
     */
    val widthWound: Double = 0.0,
    val widthBass: Double = 0.0,
    val widthMiddle: Double = 0.0,
    val widthTreble: Double = 0.0,
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
     * The instrument's lowest key: A0 on the piano as it is built; a Pro
     * setting for the few that go lower (F0 on the Bösendorfer 225, C0 on
     * the Imperial). FOSS pins A0. The session runs down to here.
     */
    val lowestKeyMidi: Int = 21,               // A0
    // SAMPLING
    /**
     * Where the inharmonicity curve breaks: notes at which a new run of
     * scaling begins (a bridge break, a strut, a change of wire). Pro marks
     * them on the sampling screen; FOSS has none beyond the wound/plain
     * floor. An instrument's property, kept with the settings.
     */
    val curveBreaks: Set<Int> = emptySet(),
    // WORKFLOW
    /**
     * The tuning screen follows the key struck: when a strike is another
     * note of the session and the note on screen is not heard, the screen
     * moves to it and the note left is kept (as leaving does). Never inside
     * an unfinished temperament octave, which is walked with Done.
     */
    val autoNote: Boolean = true,
    // PRECISION
    /** The partial read matches within this of its target, Hz: the beat rate the tuner accepts as gone. */
    val matchHz: Double = 0.1,
    /** Partials above this note are not offered: nothing up there helps a tuning. */
    val highestPartialMidi: Int = 99,          // D#7
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
        const val MAX_WIDTH_CENTS = 20.0
        const val SAMPLE_STEP = 4
        const val SAMPLE_TOP = 96            // C7: above it the second partial is gone before it is read
    }

    /**
     * The strings sampled before tuning (docs/ENGINE.md §1): from the lowest
     * key up to C7, every [SAMPLE_STEP] semitones, and the lowest plain
     * string itself, so each side of the floor has its own; A4 is the hub's.
     * FOSS samples exactly these; Pro proposes them and takes any note added.
     */
    val sampleNotes: List<Int>
        get() = ((lowestKeyMidi..SAMPLE_TOP step SAMPLE_STEP) + lowestUnwoundMidi).filter { it != TEMPERAMENT_HIGH }.distinct().sorted()

    /** True for a wound string: below the lowest plain one. */
    fun isWound(midi: Int): Boolean = midi < lowestUnwoundMidi

    /** Below this note the bass octave type applies: an octave above the lowest plain string. */
    val bassBoundaryMidi: Int get() = lowestUnwoundMidi + 12

    /**
     * Which octave type links [midi] to an already tuned note: the wound
     * strings, the bass within an octave of the plain-wire floor, the treble
     * above the temperament octave, the middle in between.
     */
    fun octaveTypeFor(midi: Int): OctaveType = when {
        isWound(midi) -> octaveWound
        midi <= bassBoundaryMidi -> octaveBass
        midi > TEMPERAMENT_HIGH -> octaveTreble
        else -> octaveMiddle
    }

    /** The octave width for [midi]'s region, cents; see [widthWound]. */
    fun widthFor(midi: Int): Double = when {
        isWound(midi) -> widthWound
        midi <= bassBoundaryMidi -> widthBass
        midi > TEMPERAMENT_HIGH -> widthTreble
        else -> widthMiddle
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
        val s = pin(change(_settings.value))
        _settings.value = s
        save(s)
    }

    /**
     * What the edition fixes: FOSS is the free tuner for the piano as it is
     * built, A0 to C8; the compass is a Pro setting, for the few instruments
     * that go lower (the Bösendorfer 225 and Imperial, the Érard).
     */
    private fun pin(s: TunerSettings): TunerSettings =
        if (edition.temperamentFirst) s.copy(temperamentFirst = true, lowestKeyMidi = 21) else s.copy(temperamentFirst = false)

    private fun load(): TunerSettings = pin(loadStored())

    private fun loadStored(): TunerSettings {
        val d = edition
        fun i(k: String, v: Int) = store.get(k)?.toIntOrNull() ?: v
        fun f(k: String, v: Double) = store.get(k)?.toDoubleOrNull() ?: v
        fun b(k: String, v: Boolean) = store.get(k)?.toBooleanStrictOrNull() ?: v
        fun o(k: String, v: OctaveType) = store.get(k)?.let { n -> OctaveType.entries.firstOrNull { it.name == n } } ?: v
        return TunerSettings(
            temperamentFirst = d.temperamentFirst,
            temperamentLowMidi = i("temperamentLowMidi", d.temperamentLowMidi),
            octaveWound = o("octaveWound", d.octaveWound),
            octaveBass = o("octaveBass", d.octaveBass),
            octaveMiddle = o("octaveMiddle", d.octaveMiddle),
            octaveTreble = o("octaveTreble", d.octaveTreble),
            widthWound = f("widthWound", d.widthWound).coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS),
            widthBass = f("widthBass", d.widthBass).coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS),
            widthMiddle = f("widthMiddle", d.widthMiddle).coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS),
            widthTreble = f("widthTreble", d.widthTreble).coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS),
            weightOctave = i("weightOctave", d.weightOctave),
            weightTwelfth = i("weightTwelfth", d.weightTwelfth),
            weightDoubleOctave = i("weightDoubleOctave", d.weightDoubleOctave),
            weightFifth = i("weightFifth", d.weightFifth),
            lowestUnwoundMidi = i("lowestUnwoundMidi", d.lowestUnwoundMidi),
            lowestKeyMidi = i("lowestKeyMidi", d.lowestKeyMidi).coerceIn(TunerSettings.MIN_LOWEST_KEY, TunerSettings.MAX_LOWEST_KEY),
            autoNote = b("autoNote", d.autoNote),
            curveBreaks = store.get("curveBreaks")?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..127 }?.toSet() ?: d.curveBreaks,
            matchHz = f("matchHz", d.matchHz),
            highestPartialMidi = i("highestPartialMidi", d.highestPartialMidi),
            recordPcm = b("recordPcm", d.recordPcm),
        )
    }

    private fun save(s: TunerSettings) {
        store.put("temperamentLowMidi", s.temperamentLowMidi.toString())
        store.put("octaveWound", s.octaveWound.name)
        store.put("octaveBass", s.octaveBass.name)
        store.put("octaveMiddle", s.octaveMiddle.name)
        store.put("octaveTreble", s.octaveTreble.name)
        store.put("widthWound", s.widthWound.toString())
        store.put("widthBass", s.widthBass.toString())
        store.put("widthMiddle", s.widthMiddle.toString())
        store.put("widthTreble", s.widthTreble.toString())
        store.put("weightOctave", s.weightOctave.toString())
        store.put("weightTwelfth", s.weightTwelfth.toString())
        store.put("weightDoubleOctave", s.weightDoubleOctave.toString())
        store.put("weightFifth", s.weightFifth.toString())
        store.put("lowestUnwoundMidi", s.lowestUnwoundMidi.toString())
        store.put("lowestKeyMidi", s.lowestKeyMidi.toString())
        store.put("autoNote", s.autoNote.toString())
        store.put("curveBreaks", s.curveBreaks.sorted().joinToString(","))
        store.put("matchHz", s.matchHz.toString())
        store.put("highestPartialMidi", s.highestPartialMidi.toString())
        store.put("recordPcm", s.recordPcm.toString())
    }
}
