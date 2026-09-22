package at.clavierhaus.unisonmaster.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import at.clavierhaus.unisonmaster.TuningController
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
fun TunerSettingsScreen(controller: TuningController, model: SettingsModel, version: String, onBack: () -> Unit) {
    val s by model.settings.collectAsState()
    val a4 by controller.referenceA4Hz.collectAsState()

    SettingsScreen(
        subtitle = "Configuration only. Tuning stays on the main screen.",
        onBack = onBack,
        tabs = listOf(
            SettingsTab("Temperament", accent = true) { TemperamentTab(s, a4, controller, model) },
            SettingsTab("About") {
                SettingsSection("Clavier Tuner")
                ReadOnlyRow("Version", "", version)
                ReadOnlyRow("Core", "The open piano-tuning foundation shared with Clavier Tuner Pro", "clavier-tuner-foss")
                SettingsSection("Credits")
                ReadOnlyRow("Typeface", "DejaVu Serif, DejaVu fonts project", "Bitstream Vera licence")
                ReadOnlyRow("Settings icon", "Material Design icons, Google", "Apache 2.0")
            },
            SettingsTab("License") {
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

    SettingsSection("Temperament")
    ChoiceRow("Temperament", "Unequal temperaments will be listed here", listOf("Equal"), 0) { }
    ReadOnlyRow(
        "Temperament octave",
        "Tuned first, downward from A4: temperament and inharmonicity are taken here",
        "A3–A4",
    )

    SettingsSection("Stretch")
    SettingsNote("The octave type sets the target below and above the temperament octave. Stretch inside it follows.")
    ChoiceRow("Octave type, bass", "Which partials of the two notes coincide", octaves.map { it.label }, octaves.indexOf(s.octaveBass)) {
        model.update { c -> c.copy(octaveBass = octaves[it]) }
    }
    ChoiceRow("Octave type, middle", "", octaves.map { it.label }, octaves.indexOf(s.octaveMiddle)) {
        model.update { c -> c.copy(octaveMiddle = octaves[it]) }
    }
    ChoiceRow("Octave type, treble", "", octaves.map { it.label }, octaves.indexOf(s.octaveTreble)) {
        model.update { c -> c.copy(octaveTreble = octaves[it]) }
    }

    SettingsSection("Piano")
    StepperRow(
        "Lowest key", "A0 on the 88-key piano; F0 on the Bösendorfer 225, C0 on the 290. The tuning runs down to here",
        Notes.name(s.lowestKeyMidi),
        canDecrease = s.lowestKeyMidi > TunerSettings.MIN_LOWEST_KEY,
        canIncrease = s.lowestKeyMidi < TunerSettings.MAX_LOWEST_KEY,
        onDecrease = { model.update { it.copy(lowestKeyMidi = it.lowestKeyMidi - 1) } },
        onIncrease = { model.update { it.copy(lowestKeyMidi = it.lowestKeyMidi + 1) } },
    )
    StepperRow(
        "Lowest unwound string", "Wound strings below it follow a different physics and are modelled separately",
        Notes.name(s.lowestUnwoundMidi),
        canDecrease = s.lowestUnwoundMidi > TunerSettings.MIN_UNWOUND,
        canIncrease = s.lowestUnwoundMidi < TunerSettings.MAX_UNWOUND,
        onDecrease = { model.update { it.copy(lowestUnwoundMidi = it.lowestUnwoundMidi - 1) } },
        onIncrease = { model.update { it.copy(lowestUnwoundMidi = it.lowestUnwoundMidi + 1) } },
    )

    SettingsSection("Workflow")
    SwitchRow(
        "Follow the key struck",
        if (s.temperamentFirst) "The screen moves to the note played, once the temperament octave is finished; the note left is registered"
        else "The screen moves to the note played; the note left is registered",
        s.autoNote,
    ) { on -> model.update { it.copy(autoNote = on) } }

    SettingsSection("Recording")
    SwitchRow(
        "Recording button",
        "A red button on the tuning screen records what the microphone hears, uncompressed PCM at 48 kHz, to Recordings/ClavierTuner. About 6 MB a minute",
        s.recordPcm,
    ) { on -> model.update { it.copy(recordPcm = on) } }

    SettingsSection("Precision")
    StepperRow(
        "Match window", "A partial turns green within this of its target",
        String.format(Locale.ROOT, "±%.2f Hz", s.matchHz),
        canDecrease = s.matchHz > 0.051,
        canIncrease = s.matchHz < 0.499,
        onDecrease = { model.update { it.copy(matchHz = round((it.matchHz - 0.05) * 100) / 100) } },
        onIncrease = { model.update { it.copy(matchHz = round((it.matchHz + 0.05) * 100) / 100) } },
    )
    ChoiceRow(
        "Reading", "The mean of the readings since the strike, over at most this long: short follows the string hop by hop, long stands still on a beating unison",
        listOf("¼ s", "½ s", "1 s", "2 s", "4 s"), TunerSettings.READING_CHOICES_S.indexOf(s.readingS).coerceAtLeast(0),
    ) { i -> model.update { it.copy(readingS = TunerSettings.READING_CHOICES_S[i]) } }
    StepperRow(
        "Suggested partial: level", "Within this of the note's loudest partial",
        String.format(Locale.ROOT, "%.0f dB", s.suggestLevelDb),
        canDecrease = s.suggestLevelDb > 10.1,
        canIncrease = s.suggestLevelDb < 39.9,
        onDecrease = { model.update { it.copy(suggestLevelDb = it.suggestLevelDb - 5) } },
        onIncrease = { model.update { it.copy(suggestLevelDb = it.suggestLevelDb + 5) } },
    )
    StepperRow(
        "Suggested partial: sustain", "How long it must stay that strong after the strike",
        String.format(Locale.ROOT, "%.1f s", s.suggestSustainS),
        canDecrease = s.suggestSustainS > 0.51,
        canIncrease = s.suggestSustainS < 3.99,
        onDecrease = { model.update { it.copy(suggestSustainS = tenths(it.suggestSustainS - 0.5)) } },
        onIncrease = { model.update { it.copy(suggestSustainS = tenths(it.suggestSustainS + 0.5)) } },
    )
    StepperRow(
        "Highest partial", "Partials above this note are not offered; nothing up there helps a tuning",
        Notes.name(s.highestPartialMidi),
        canDecrease = s.highestPartialMidi > TunerSettings.MIN_HIGHEST_PARTIAL,
        canIncrease = s.highestPartialMidi < TunerSettings.MAX_HIGHEST_PARTIAL,
        onDecrease = { model.update { it.copy(highestPartialMidi = it.highestPartialMidi - 1) } },
        onIncrease = { model.update { it.copy(highestPartialMidi = it.highestPartialMidi + 1) } },
    )
}
