package at.clavierhaus.unisonmaster.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.ui.ChoiceRow
import at.clavierhaus.unisonmaster.ui.ReadOnlyRow
import at.clavierhaus.unisonmaster.ui.SettingsNote
import at.clavierhaus.unisonmaster.ui.SettingsScreen
import at.clavierhaus.unisonmaster.ui.SettingsSection
import at.clavierhaus.unisonmaster.ui.SettingsTab
import at.clavierhaus.unisonmaster.ui.StepperRow
import at.clavierhaus.unisonmaster.ui.SwitchRow
import at.clavierhaus.unisonmaster.ui.formatHz
import java.util.Locale
import kotlin.math.round

private fun tenths(x: Double) = round(x * 10.0) / 10.0

/** The foss settings: Temperament, About, License. */
@Composable
fun TunerSettingsScreen(
    controller: TuningController,
    model: SettingsModel,
    version: String,
    feeding: String? = null,
    feedNote: String? = null,
    onPickRecording: () -> Unit = {},
    onUseMicrophone: () -> Unit = {},
    onBack: () -> Unit,
) {
    val s by model.settings.collectAsState()
    Strings.language.collectAsState().value                    // recompose on a language change
    val a4 by controller.referenceA4Hz.collectAsState()

    SettingsScreen(
        subtitle = "Configuration only. Tuning stays on the main screen.",
        onBack = onBack,
        tabs = listOf(
            SettingsTab(t(K.settings_tab_temperament), accent = true) { TemperamentTab(s, a4, controller, model) },
            SettingsTab(t(K.settings_tab_about)) {
                SettingsSection(t(K.settings_language))
                val langs = listOf("") + Strings.languages
                ChoiceRow(t(K.settings_language), "", langs.map { if (it.isEmpty()) t(K.settings_language_system) else it.uppercase() }, langs.indexOf(s.language).coerceAtLeast(0)) { i ->
                    model.update { c -> c.copy(language = langs[i]) }
                }
                SettingsSection("Clavier Tuner")
                ReadOnlyRow("Version", "", version)
                ReadOnlyRow("Core", "The open piano-tuning foundation shared with Clavier Tuner Pro", "clavier-tuner-foss")
                SettingsSection("Credits")
                ReadOnlyRow("Typeface", "DejaVu Serif, DejaVu fonts project", "Bitstream Vera licence")
                ReadOnlyRow("Settings icon", "Material Design icons, Google", "Apache 2.0")
            },
            SettingsTab("Probe") {
                SettingsSection("What the engine hears")
                ReadOnlyRow("Source", "Everything above the microphone runs the same either way", feeding ?: "Microphone")
                ChoiceRow(
                    "Input", "A recording is played at its own speed and repeats, with a second of silence between passes",
                    listOf("Microphone", "A recording\u2026"), if (feeding == null) 0 else 1,
                ) { i -> if (i == 0) onUseMicrophone() else onPickRecording() }
                SwitchRow(
                    "Play button on the tuning screen",
                    "Beside the recording button: opens the file dialog, then plays into the engine",
                    s.probe,
                ) { on -> model.update { it.copy(probe = on) } }
                if (feedNote != null) SettingsNote(feedNote)
                SettingsNote(
                    "For testing away from a piano. Pick any WAV the phone can reach \u2014 the app's own " +
                        "recordings, Downloads, anything pushed over adb \u2014 at 48 kHz, 16-bit PCM or " +
                        "32-bit float. The tuning screen then reads the recording exactly as it would read " +
                        "the string: same readers, same identifier, same numbers. Leave it on Microphone at " +
                        "the instrument.",
                )
            },
            SettingsTab(t(K.settings_tab_license)) {
                SettingsSection("License")
                ReadOnlyRow("Clavier Tuner", "clavierhaus.at", "Apache License 2.0")
                SettingsNote("The full text is in the repository (LICENSE) and in the app's licences screen below. Free to use, study, change and share, including commercially, with attribution and no warranty.")
                ReadOnlyRow("Typeface", "DejaVu Serif, DejaVu fonts project", "Bitstream Vera licence")
                ReadOnlyRow("Settings icon", "Material Design icons, Google", "Apache 2.0")
            },
        ),
    )
}

@Composable
private fun TemperamentTab(s: TunerSettings, a4: Double, controller: TuningController, model: SettingsModel) {
    val octaves = OctaveType.entries

    SettingsSection("Reference")
    StepperRow(
        "A4", "Set on the hub from a single string; adjust here to 0.1 Hz",
        formatHz(a4),
        canDecrease = a4 > TuningController.MIN_REFERENCE_HZ,
        canIncrease = a4 < TuningController.MAX_REFERENCE_HZ,
        onDecrease = { controller.setReference(tenths(a4 - 0.1)) },
        onIncrease = { controller.setReference(tenths(a4 + 0.1)) },
    )

    SettingsSection("Workflow")
    SwitchRow(
        "Follow the key struck",
        if (s.temperamentFirst) "The screen moves to the note played, once the temperament octave is finished; the note left is kept"
        else "The screen moves to the note played; the note left is kept",
        s.autoNote,
    ) { on -> model.update { it.copy(autoNote = on) } }

    SettingsSection("Precision")
    SwitchRow(
        "Read the clean partials together",
        "The reading is the string's clean partials read as one, once the string has been measured; a partial that beats on its own is left out and named under the bell. Off: the partial being tuned alone, as an ETD reads. A partial tapped under Partials is read alone either way.",
        s.cleanPartials,
    ) { on -> model.update { it.copy(cleanPartials = on) } }
    run {
        val steps = TunerSettings.MEMORY_STEPS
        val i = steps.indexOfFirst { kotlin.math.abs(it - s.readingMemoryS) < 1e-6 }.let { if (it < 0) 0 else it }
        StepperRow(
            "Reading memory", "How far back the bell looks. Short shows every wobble as it happens; longer steadies the bell in a noisy room or on a string that wanders, and shows a beat slower than it is",
            String.format(Locale.ROOT, "%.2f s", steps[i]),
            canDecrease = i > 0,
            canIncrease = i < steps.size - 1,
            onDecrease = { model.update { it.copy(readingMemoryS = steps[i - 1]) } },
            onIncrease = { model.update { it.copy(readingMemoryS = steps[i + 1]) } },
        )
    }
    StepperRow(
        "Match window", "A partial turns green within this of its target",
        String.format(Locale.ROOT, "±%.2f Hz", s.matchHz),
        canDecrease = s.matchHz > 0.051,
        canIncrease = s.matchHz < 0.499,
        onDecrease = { model.update { it.copy(matchHz = round((it.matchHz - 0.05) * 100) / 100) } },
        onIncrease = { model.update { it.copy(matchHz = round((it.matchHz + 0.05) * 100) / 100) } },
    )
    StepperRow(
        "Highest partial", "Partials above this note are not offered; nothing up there helps a tuning",
        Notes.name(s.highestPartialMidi),
        canDecrease = s.highestPartialMidi > TunerSettings.MIN_HIGHEST_PARTIAL,
        canIncrease = s.highestPartialMidi < TunerSettings.MAX_HIGHEST_PARTIAL,
        onDecrease = { model.update { it.copy(highestPartialMidi = it.highestPartialMidi - 1) } },
        onIncrease = { model.update { it.copy(highestPartialMidi = it.highestPartialMidi + 1) } },
    )
    SettingsSection("Temperament")
    // unequal temperaments are not offered until they run: nothing on a screen that does nothing (SCREEN.md)
    ReadOnlyRow(
        "Temperament octave",
        "Tuned first, downward from A4, to equal temperament on it; every other note is tuned by octaves from here",
        "A3–A4",
    )

    SettingsSection("Stretch")
    SettingsNote("Every note outside the temperament octave is tuned by an octave to a note already tuned: its partial meets the partner's measured partial. The octave type says which partials; the width, how far beyond beatless — wide means the lower note flatter, the upper sharper.")
    RegionRows("Wound strings", "Below the lowest plain string", octaves, s.octaveWound, s.widthWound,
        { t -> model.update { it.copy(octaveWound = t) } }, { w -> model.update { it.copy(widthWound = w) } },
        s.widthWoundFar, "the lowest key", { w -> model.update { it.copy(widthWoundFar = w) } })
    RegionRows("Bass", "From the lowest plain string, one octave up", octaves, s.octaveBass, s.widthBass,
        { t -> model.update { it.copy(octaveBass = t) } }, { w -> model.update { it.copy(widthBass = w) } },
        s.widthBassFar, "the lowest plain string", { w -> model.update { it.copy(widthBassFar = w) } })
    RegionRows("Middle", "Up to the temperament octave", octaves, s.octaveMiddle, s.widthMiddle,
        { t -> model.update { it.copy(octaveMiddle = t) } }, { w -> model.update { it.copy(widthMiddle = w) } })
    RegionRows("Treble", "Above A4", octaves, s.octaveTreble, s.widthTreble,
        { t -> model.update { it.copy(octaveTreble = t) } }, { w -> model.update { it.copy(widthTreble = w) } },
        s.widthTrebleFar, "C8", { w -> model.update { it.copy(widthTrebleFar = w) } })

    SettingsSection("Piano")
    StepperRow(
        "Lowest unwound string", "Wound strings below it follow a different physics and are modelled separately",
        Notes.name(s.lowestUnwoundMidi),
        canDecrease = s.lowestUnwoundMidi > TunerSettings.MIN_UNWOUND,
        canIncrease = s.lowestUnwoundMidi < TunerSettings.MAX_UNWOUND,
        onDecrease = { model.update { it.copy(lowestUnwoundMidi = it.lowestUnwoundMidi - 1) } },
        onIncrease = { model.update { it.copy(lowestUnwoundMidi = it.lowestUnwoundMidi + 1) } },
    )

    SettingsSection("Recording")
    SwitchRow(
        "Recording button",
        "A red button on the tuning screen records what the microphone hears, uncompressed PCM at 48 kHz, to Recordings/ClavierTuner. About 6 MB a minute",
        s.recordPcm,
    ) { on -> model.update { it.copy(recordPcm = on) } }

}

@Composable
private fun RegionRows(
    region: String,
    where: String,
    octaves: List<OctaveType>,
    type: OctaveType,
    width: Double,
    onType: (OctaveType) -> Unit,
    onWidth: (Double) -> Unit,
    /** The width at the region's far end (the lowest key, or C8), null = the same as [width]; [farName] names that end. */
    far: Double? = null,
    farName: String = "",
    onFar: ((Double?) -> Unit)? = null,
) {
    ChoiceRow("Octave type, ${region.lowercase()}", where, octaves.map { it.label }, octaves.indexOf(type)) { onType(octaves[it]) }
    StepperRow(
        "Octave width, ${region.lowercase()}", "",
        if (width == 0.0) "beatless" else String.format(Locale.ROOT, "%.1f c wide", width),
        canDecrease = width > 0.0,
        canIncrease = width < TunerSettings.MAX_WIDTH_CENTS,
        onDecrease = { onWidth(tenths(maxOf(0.0, width - WIDTH_STEP))) },
        onIncrease = { onWidth(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, width + WIDTH_STEP))) },
    )
    if (onFar != null) {
        // the width slides by key from the value above to this one at the far end of the region
        val f = far ?: width
        StepperRow(
            "… at $farName", if (far == null) "the same as above; set it and the width slides by key towards $farName" else "the width slides by key from the value above to this",
            if (far == null) "same" else if (f == 0.0) "beatless" else String.format(Locale.ROOT, "%.1f c wide", f),
            canDecrease = f > 0.0 || far != null,
            canIncrease = f < TunerSettings.MAX_WIDTH_CENTS,
            onDecrease = { val v = tenths(maxOf(0.0, f - WIDTH_STEP)); onFar(if (v == width) null else v) },
            onIncrease = { onFar(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, f + WIDTH_STEP))) },
        )
    }
}

private const val WIDTH_STEP = 0.5
