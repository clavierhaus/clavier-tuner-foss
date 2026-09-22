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
fun TunerSettingsScreen(controller: TuningController, model: SettingsModel, version: String, onBack: () -> Unit) {
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

    SettingsSection("Temperament")
    ChoiceRow("Temperament", "Unequal temperaments will be listed here", listOf("Equal"), 0) { }
    ReadOnlyRow(
        "Temperament octave",
        "Tuned first, downward from A4, to equal temperament on it; every other note is tuned by octaves from here",
        "A3–A4",
    )

    SettingsSection("Stretch")
    SettingsNote("Every note outside the temperament octave is tuned by an octave to a note already tuned: its partial meets the partner's measured partial. The octave type says which partials; the width, how far beyond beatless — wide means the lower note flatter, the upper sharper.")
    RegionRows("Wound strings", "Below the lowest unwound string", octaves, s.octaveWound, s.widthWound,
        { t -> model.update { it.copy(octaveWound = t) } }, { w -> model.update { it.copy(widthWound = w) } })
    RegionRows("Bass", "From the lowest unwound string, one octave up", octaves, s.octaveBass, s.widthBass,
        { t -> model.update { it.copy(octaveBass = t) } }, { w -> model.update { it.copy(widthBass = w) } })
    RegionRows("Middle", "Up to the temperament octave", octaves, s.octaveMiddle, s.widthMiddle,
        { t -> model.update { it.copy(octaveMiddle = t) } }, { w -> model.update { it.copy(widthMiddle = w) } })
    RegionRows("Treble", "Above A4", octaves, s.octaveTreble, s.widthTreble,
        { t -> model.update { it.copy(octaveTreble = t) } }, { w -> model.update { it.copy(widthTreble = w) } })

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

    SettingsSection("Workflow")
    SwitchRow(
        "Follow the key struck",
        if (s.temperamentFirst) "The screen moves to the note played, once the temperament octave is finished; the note left is kept"
        else "The screen moves to the note played; the note left is kept",
        s.autoNote,
    ) { on -> model.update { it.copy(autoNote = on) } }

    SettingsSection("Precision")
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
}

private const val WIDTH_STEP = 0.5
