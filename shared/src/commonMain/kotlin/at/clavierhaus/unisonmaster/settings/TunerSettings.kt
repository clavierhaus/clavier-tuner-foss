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

/** Octave types a stretch can be built on: which partials of the two notes coincide. */
enum class OctaveType(val label: String) { O2_1("2:1"), O4_2("4:2"), O6_3("6:3"), O8_4("8:4"), O4_1("4:1") }

/**
 * Everything the tuner sets in Settings. Values that determine a target
 * frequency live in the Temperament tab. Stretch and interval weights are
 * stored now and applied when stretch lands.
 */
data class TunerSettings(
    // TEMPERAMENT
    /** Lowest note of the temperament octave; the session runs from A4 down to here. */
    val temperamentLowMidi: Int = 57,          // A3
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
    /** Lowest plain (unwound) string; wound strings below it follow a different physics. */
    val lowestUnwoundMidi: Int = 43,           // G2
    // PRECISION
    /** A partial matches within this of its target, Hz. */
    val matchHz: Double = 0.1,
    /** Suggestion: a partial must peak within this of the loudest, dB ... */
    val suggestLevelDb: Double = 30.0,
    /** ... and stay there this long, s. */
    val suggestSustainS: Double = 1.0,
    /** Partials above this note are not offered: nothing up there helps a tuning. */
    val highestPartialMidi: Int = 99,          // D#7
) {
    companion object {
        const val MIN_TEMPERAMENT_LOW = 48     // C3
        const val MAX_TEMPERAMENT_LOW = 57     // A3
        const val MIN_UNWOUND = 28             // E1
        const val MAX_UNWOUND = 60             // C4
        const val MIN_HIGHEST_PARTIAL = 84     // C6
        const val MAX_HIGHEST_PARTIAL = 108    // C8
    }
}

/** Holds the settings, persists every change, and exposes them as a flow. */
class SettingsModel(private val store: KeyValueStore) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TunerSettings> = _settings.asStateFlow()

    fun update(change: (TunerSettings) -> TunerSettings) {
        val s = change(_settings.value)
        _settings.value = s
        save(s)
    }

    private fun load(): TunerSettings {
        val d = TunerSettings()
        fun i(k: String, v: Int) = store.get(k)?.toIntOrNull() ?: v
        fun f(k: String, v: Double) = store.get(k)?.toDoubleOrNull() ?: v
        fun o(k: String, v: OctaveType) = store.get(k)?.let { n -> OctaveType.entries.firstOrNull { it.name == n } } ?: v
        return TunerSettings(
            temperamentLowMidi = i("temperamentLowMidi", d.temperamentLowMidi),
            octaveBass = o("octaveBass", d.octaveBass),
            octaveMiddle = o("octaveMiddle", d.octaveMiddle),
            octaveTreble = o("octaveTreble", d.octaveTreble),
            weightOctave = i("weightOctave", d.weightOctave),
            weightTwelfth = i("weightTwelfth", d.weightTwelfth),
            weightDoubleOctave = i("weightDoubleOctave", d.weightDoubleOctave),
            weightFifth = i("weightFifth", d.weightFifth),
            lowestUnwoundMidi = i("lowestUnwoundMidi", d.lowestUnwoundMidi),
            matchHz = f("matchHz", d.matchHz),
            suggestLevelDb = f("suggestLevelDb", d.suggestLevelDb),
            suggestSustainS = f("suggestSustainS", d.suggestSustainS),
            highestPartialMidi = i("highestPartialMidi", d.highestPartialMidi),
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
        store.put("matchHz", s.matchHz.toString())
        store.put("suggestLevelDb", s.suggestLevelDb.toString())
        store.put("suggestSustainS", s.suggestSustainS.toString())
        store.put("highestPartialMidi", s.highestPartialMidi.toString())
    }
}
