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
     * Done (leaving one does not register it), and sampling asks for the set
     * of [SAMPLE_NOTES]; off, leaving any note registers it and the tuner
     * samples where he sees fit, at least [MIN_SAMPLES] strings. The tuner's
     * choice in both editions since 4 Oct 2026 (it was the free app's pin);
     * neither way restricts where he may go. Off by default: the
     * professional's way.
     */
    val temperamentFirst: Boolean = false,
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
    /**
     * The width at the far end of each region — the lowest key for the
     * three lower regions, C8 for the treble — interpolated by key from the
     * region's width at the end nearest the temperament octave (the value
     * above). Null: the same at both ends. A tuner's ear may want the low
     * bass wider the lower it goes (the 225's, 30 Sept): this is that.
     */
    val widthWoundFar: Double? = null,
    val widthBassFar: Double? = null,
    val widthMiddleFar: Double? = null,
    val widthTrebleFar: Double? = null,
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
    // LANGUAGE
    /** The language of the screens: a tag the strings table has, or "" for the system's. */
    val language: String = "",
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
    /**
     * How close a partial must stand to its target to turn green, in cents.
     * Cents and not Hertz: a tenth of a Hertz is a cent at F3 and a twelfth of a
     * cent at C7. The default is about twice the scatter the corpus shows between
     * strikes of one string, so green cannot promise more than the measurement has.
     */
    /** The tuner's tolerance, cents; [at.clavierhaus.unisonmaster.tuning.TuningSession.MATCH_CENTS] says where the figure comes from. */
    val matchCents: Double = 1.5,
    /**
     * The reading is the string's clean partials read together — a partial
     * that beats on its own is left out and named (CleanReading). Off: the
     * partial being tuned alone, as an ETD reads. Tapping a partial under
     * Partials reads that partial alone either way.
     */
    val cleanPartials: Boolean = true,
    /**
     * The reading's memory, seconds: how far back the shown reading looks.
     * 0.15 s shows every wobble as it happens (Verituner's); longer steadies
     * the bell in a noisy room or on a string that wanders, at the price of
     * a beat shown slower than it is. The tuner's control over the averaging.
     */
    val readingMemoryS: Double = 0.15,
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
    // PROBE
    /**
     * A play button beside the recording button that feeds a recording to the
     * engine in place of the microphone (docs/PROBE.md). Off by default and
     * meant to stay off: it is a workshop tool, and a tuner who forgets it is
     * on is tuning to yesterday's piano.
     */
    val probe: Boolean = false,
) {
    companion object {
        const val MIN_TEMPERAMENT_LOW = 48     // C3
        const val MAX_TEMPERAMENT_LOW = 57     // A3
        /** Top of the temperament octave: A4, the note the session is defined on. */
        const val TEMPERAMENT_HIGH = 69        // A4
        const val MIDI_C8 = 108
        const val MIN_MEMORY_S = 0.15
        const val MAX_MEMORY_S = 1.2
        val MEMORY_STEPS = listOf(0.15, 0.3, 0.6, 1.2)
        const val MIN_LOWEST_KEY = 12          // C0, the 97-key Imperial
        const val MAX_LOWEST_KEY = 40          // E2: never above the lowest plain string in practice
        const val MIN_UNWOUND = 28             // E1
        const val MAX_UNWOUND = 60             // C4
        const val MIN_HIGHEST_PARTIAL = 84     // C6
        const val MAX_HIGHEST_PARTIAL = 108    // C8
        const val MAX_WIDTH_CENTS = 20.0
        /**
         * The prescribed sample set (25 September): the temperament octave
         * A3–A4 (A4 is the hub's), three bass notes and three treble notes —
         * what a tuning with [temperamentFirst] samples before it starts.
         */
        val SAMPLE_NOTES: List<Int> = listOf(24, 43, 48) + (57..68).toList() + listOf(79, 84, 93)   // C1 G2 C3 · A3..G#4 · G5 C6 A6
        /** Sampling where the tuner sees fit may end once this many strings besides A4 are in. */
        const val MIN_SAMPLES = 6
    }

    /**
     * The strings a tuning must have sampled before it starts (docs/ENGINE.md
     * §1): with [temperamentFirst] exactly [SAMPLE_NOTES]; otherwise none —
     * the tuner samples the instrument as he sees fit, at least [MIN_SAMPLES].
     */
    val sampleNotes: List<Int>
        get() = if (temperamentFirst) SAMPLE_NOTES.filter { it >= lowestKeyMidi } else emptyList()

    /** True for a wound string: below the lowest plain one. */
    fun isWound(midi: Int): Boolean = midi < lowestUnwoundMidi

    /** Below this note the bass octave type applies: an octave above the lowest plain string. */
    val bassBoundaryMidi: Int get() = lowestUnwoundMidi + 12

    /**
     * Which octave type links [midi] to an already tuned note: the wound
     * strings, the bass within an octave of the plain-wire floor, the treble
     * above the temperament octave, the middle in between.
     */
    /** The stretch region [midi] lies in, 0..3: wound, bass, middle, treble — the order of the Stretch pages in Settings. */
    fun regionOf(midi: Int): Int = when {
        isWound(midi) -> 0
        midi <= bassBoundaryMidi -> 1
        midi > TEMPERAMENT_HIGH -> 3
        else -> 2
    }

    fun octaveTypeFor(midi: Int): OctaveType = when {
        isWound(midi) -> octaveWound
        midi <= bassBoundaryMidi -> octaveBass
        midi > TEMPERAMENT_HIGH -> octaveTreble
        else -> octaveMiddle
    }

    /**
     * The octave width for [midi], cents: its region's width at the end
     * nearest the temperament octave, sliding to the far end's where one is
     * set ([widthWoundFar] …), by key.
     */
    fun widthFor(midi: Int): Double {
        val (near, far, nearMidi, farMidi) = when {
            isWound(midi) -> Region(widthWound, widthWoundFar, lowestUnwoundMidi - 1, lowestKeyMidi)
            midi <= bassBoundaryMidi -> Region(widthBass, widthBassFar, bassBoundaryMidi, lowestUnwoundMidi)
            midi > TEMPERAMENT_HIGH -> Region(widthTreble, widthTrebleFar, TEMPERAMENT_HIGH + 1, MIDI_C8)
            else -> Region(widthMiddle, widthMiddleFar, temperamentLowMidi - 1, bassBoundaryMidi + 1)
        }
        if (far == null || farMidi == nearMidi) return near
        val t = ((midi - nearMidi).toDouble() / (farMidi - nearMidi)).coerceIn(0.0, 1.0)
        return near + (far - near) * t
    }

    private data class Region(val near: Double, val far: Double?, val nearMidi: Int, val farMidi: Int)
}

/**
 * Holds the settings, persists every change, and exposes them as a flow.
 * [defaults] is what an unset value is; nothing is pinned by edition — foss
 * and Pro are one tuner since 4 Oct 2026, and what the tuner may set in one
 * he may set in the other (the compass and the temperament's order were the
 * free app's pins from 25 Sept to then).
 */
class SettingsModel(
    private val store: KeyValueStore,
    private val defaults: TunerSettings = TunerSettings(),
) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TunerSettings> = _settings.asStateFlow()

    fun update(change: (TunerSettings) -> TunerSettings) {
        val s = change(_settings.value)
        _settings.value = s
        save(s)
    }

    private fun load(): TunerSettings = loadStored()

    private fun loadStored(): TunerSettings {
        val d = defaults
        fun i(k: String, v: Int) = store.get(k)?.toIntOrNull() ?: v
        fun f(k: String, v: Double) = store.get(k)?.toDoubleOrNull() ?: v
        fun fOrNull(k: String) = store.get(k)?.toDoubleOrNull()?.coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS)
        fun b(k: String, v: Boolean) = store.get(k)?.toBooleanStrictOrNull() ?: v
        fun o(k: String, v: OctaveType) = store.get(k)?.let { n -> OctaveType.entries.firstOrNull { it.name == n } } ?: v
        return TunerSettings(
            temperamentFirst = b("temperamentFirst", d.temperamentFirst),
            temperamentLowMidi = i("temperamentLowMidi", d.temperamentLowMidi),
            octaveWound = o("octaveWound", d.octaveWound),
            octaveBass = o("octaveBass", d.octaveBass),
            octaveMiddle = o("octaveMiddle", d.octaveMiddle),
            octaveTreble = o("octaveTreble", d.octaveTreble),
            widthWound = f("widthWound", d.widthWound).coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS),
            widthBass = f("widthBass", d.widthBass).coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS),
            widthMiddle = f("widthMiddle", d.widthMiddle).coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS),
            widthTreble = f("widthTreble", d.widthTreble).coerceIn(0.0, TunerSettings.MAX_WIDTH_CENTS),
            widthWoundFar = fOrNull("widthWoundFar"), widthBassFar = fOrNull("widthBassFar"),
            widthMiddleFar = fOrNull("widthMiddleFar"), widthTrebleFar = fOrNull("widthTrebleFar"),
            weightOctave = i("weightOctave", d.weightOctave),
            weightTwelfth = i("weightTwelfth", d.weightTwelfth),
            weightDoubleOctave = i("weightDoubleOctave", d.weightDoubleOctave),
            weightFifth = i("weightFifth", d.weightFifth),
            lowestUnwoundMidi = i("lowestUnwoundMidi", d.lowestUnwoundMidi),
            lowestKeyMidi = i("lowestKeyMidi", d.lowestKeyMidi).coerceIn(TunerSettings.MIN_LOWEST_KEY, TunerSettings.MAX_LOWEST_KEY),
            autoNote = b("autoNote", d.autoNote),
            cleanPartials = b("cleanPartials", d.cleanPartials),
            readingMemoryS = f("readingMemoryS", d.readingMemoryS).coerceIn(TunerSettings.MIN_MEMORY_S, TunerSettings.MAX_MEMORY_S),
            language = store.get("language") ?: d.language,
            curveBreaks = store.get("curveBreaks")?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..127 }?.toSet() ?: d.curveBreaks,
            matchCents = f("matchCents", d.matchCents),
            highestPartialMidi = i("highestPartialMidi", d.highestPartialMidi),
            recordPcm = b("recordPcm", d.recordPcm),
            probe = b("probe", d.probe),
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
        store.put("widthWoundFar", s.widthWoundFar?.toString() ?: "")
        store.put("widthBassFar", s.widthBassFar?.toString() ?: "")
        store.put("widthMiddleFar", s.widthMiddleFar?.toString() ?: "")
        store.put("widthTrebleFar", s.widthTrebleFar?.toString() ?: "")
        store.put("weightOctave", s.weightOctave.toString())
        store.put("weightTwelfth", s.weightTwelfth.toString())
        store.put("weightDoubleOctave", s.weightDoubleOctave.toString())
        store.put("weightFifth", s.weightFifth.toString())
        store.put("lowestUnwoundMidi", s.lowestUnwoundMidi.toString())
        store.put("lowestKeyMidi", s.lowestKeyMidi.toString())
        store.put("autoNote", s.autoNote.toString())
        store.put("temperamentFirst", s.temperamentFirst.toString())
        store.put("cleanPartials", s.cleanPartials.toString())
        store.put("readingMemoryS", s.readingMemoryS.toString())
        store.put("language", s.language)
        store.put("curveBreaks", s.curveBreaks.sorted().joinToString(","))
        store.put("matchCents", s.matchCents.toString())
        store.put("highestPartialMidi", s.highestPartialMidi.toString())
        store.put("recordPcm", s.recordPcm.toString())
        store.put("probe", s.probe.toString())
    }
}
