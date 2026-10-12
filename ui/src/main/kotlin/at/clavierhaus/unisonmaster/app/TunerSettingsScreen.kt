package at.clavierhaus.unisonmaster.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.ui.HelpText
import at.clavierhaus.unisonmaster.ui.LocalHelpText
import androidx.compose.runtime.CompositionLocalProvider
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.ui.ChoiceRow
import at.clavierhaus.unisonmaster.ui.ReadOnlyRow
import at.clavierhaus.unisonmaster.ui.SettingsPage
import at.clavierhaus.unisonmaster.ui.SettingsScreen
import at.clavierhaus.unisonmaster.ui.SettingsSection
import at.clavierhaus.unisonmaster.ui.SettingsTab
import at.clavierhaus.unisonmaster.ui.StepperRow
import at.clavierhaus.unisonmaster.ui.SwitchRow
import java.util.Locale
import kotlin.math.round

private fun tenths(x: Double) = round(x * 10.0) / 10.0

/**
 * The foss settings, drawn to the map of 3 Oct 2026 (Pro's
 * docs/SCREEN-REVAMP.md, "Settings: tabs, sub-tabs and sections"): four tabs
 * by function, each page two columns of rows that say their name, the page's
 * one line saying the rest, nothing scrolling.
 *
 * - Tuning: Temperament · Reading
 * - Stretch: Wound · Bass · Middle · Treble · Breaks
 * - Piano: Compass
 * - Help: Quick start (12 Oct 2026, Peter: between Piano and App; an edition adds its own
 *   help after it, Pro the book's)
 * - App: About
 *
 * The same in both editions since 4 Oct 2026 (CLAUDE.md, "What Pro is"):
 * this screen lives in the ui library, both apps mount it, and Pro adds the
 * book's own pages and tabs through [extraPianoPages], [extraTabs] and
 * [extraAppPages]. Language and Probe left the App tab on 10 Oct 2026 for the
 * free edition's release (Peter: "remove the language tab, the probe tab --
 * and that should be it"): the free app follows the phone's language, and the
 * probe -- the engine fed a recording -- is a working tool of Pro's. Both are
 * Pro's pages now (ProSettings.kt) and arrive through [extraAppPages].
 *
 * A4 is not here: it is the piano's, set at the hub where the session starts.
 * [open] names a tab and page to start on — the tuning screen reaches the
 * stretch of the region it is in with one tap.
 */
@Composable
fun TunerSettingsScreen(
    controller: TuningController,
    model: SettingsModel,
    version: String,
    /** The gear closes the settings: back to the screen they were opened from. */
    onBack: () -> Unit,
    /** Home: the hub, from the corner every screen shares. */
    onHome: () -> Unit,
    open: Pair<Int, Int>? = null,
    /** Pages an edition adds to the Piano tab after Compass (Pro: the unison floors and the regimen a new piano starts from). */
    extraPianoPages: List<SettingsPage> = emptyList(),
    /** Tabs an edition adds after Piano, before Help (Pro: the customers book). */
    extraTabs: List<SettingsTab> = emptyList(),
    /** Pages an edition adds to the Help tab after Quick start (Pro: the book's own help). */
    extraHelpPages: List<SettingsPage> = emptyList(),
    /** Pages an edition adds to the App tab before About (Pro: Language, Probe). */
    extraAppPages: List<SettingsPage> = emptyList(),
) {
    val s by model.settings.collectAsState()
    Strings.language.collectAsState().value                    // recompose on a language change

    // Help's text size reaches every help page, whichever edition built it (HelpParts.LocalHelpText)
    CompositionLocalProvider(LocalHelpText provides HelpText(s.helpTextSp) { sp -> model.update { it.copy(helpTextSp = sp) } }) {
    SettingsScreen(
        onClose = onBack,
        onHome = onHome,
        open = open,
        tabs = listOf(
            SettingsTab(t(K.settings_tab_tuning), tuningPages(s, model)),
            SettingsTab(t(K.settings_tab_stretch), stretchPages(s, model)),
            SettingsTab(t(K.settings_tab_piano), listOf(SettingsPage(t(K.settings_page_compass), COMPASS_NOTE, left = { CompassRows(s, model) })) + extraPianoPages),
        ) + extraTabs + listOf(
            SettingsTab(t(K.settings_tab_help), listOf(quickStartPage()) + extraHelpPages),
            SettingsTab(t(K.settings_tab_app), extraAppPages + listOf(
                SettingsPage(t(K.settings_page_about), ABOUT_NOTE,
                    left = {
                        SettingsSection("Clavier Tuner")
                        ReadOnlyRow("Version", version)
                        ReadOnlyRow("Core", "clavier-tuner-foss")
                        ReadOnlyRow("Typeface", "DejaVu Serif · Bitstream Vera licence")
                        ReadOnlyRow("Settings icon", "Material Design icons · Apache 2.0")
                    },
                    right = {
                        SettingsSection("Licence")
                        ReadOnlyRow("Clavier Tuner", "Apache License 2.0")
                        ReadOnlyRow("clavierhaus.at", "Vienna")
                    }),
            )),
        ),
    )
    }
}

/** The Tuning tab: what today's tuning does and how it reads. */
private fun tuningPages(s: TunerSettings, model: SettingsModel): List<SettingsPage> = listOf(
    SettingsPage(t(K.settings_page_temperament), TEMPERAMENT_NOTE,
        left = {
            SettingsSection("Temperament")
            // unequal temperaments are not offered until they run: nothing on a screen that does nothing
            ReadOnlyRow("Temperament octave", "A3–A4")
        },
        right = {
            SettingsSection("The order of work")
            SwitchRow("Temperament first", s.temperamentFirst) { on -> model.update { it.copy(temperamentFirst = on) } }
        }),
    SettingsPage(t(K.settings_page_reading), READING_NOTE,
        left = {
            SettingsSection("The screen")
            SwitchRow("Follow the key struck", s.autoNote) { on -> model.update { it.copy(autoNote = on) } }
            SwitchRow("Read the clean partials together", s.cleanPartials) { on -> model.update { it.copy(cleanPartials = on) } }
            StepperRow(
                "Highest partial", Notes.name(s.highestPartialMidi),
                canDecrease = s.highestPartialMidi > TunerSettings.MIN_HIGHEST_PARTIAL,
                canIncrease = s.highestPartialMidi < TunerSettings.MAX_HIGHEST_PARTIAL,
                onDecrease = { model.update { it.copy(highestPartialMidi = it.highestPartialMidi - 1) } },
                onIncrease = { model.update { it.copy(highestPartialMidi = it.highestPartialMidi + 1) } },
            )
        },
        right = {
            SettingsSection("The reading")
            val steps = TunerSettings.MEMORY_STEPS
            val i = steps.indexOfFirst { kotlin.math.abs(it - s.readingMemoryS) < 1e-6 }.let { if (it < 0) 0 else it }
            StepperRow(
                "Reading memory", String.format(Locale.ROOT, "%.2f s", steps[i]),
                canDecrease = i > 0,
                canIncrease = i < steps.size - 1,
                onDecrease = { model.update { it.copy(readingMemoryS = steps[i - 1]) } },
                onIncrease = { model.update { it.copy(readingMemoryS = steps[i + 1]) } },
            )
            StepperRow(
                "Match window", String.format(Locale.ROOT, "±%.1f c", s.matchCents),
                canDecrease = s.matchCents > 0.101,
                canIncrease = s.matchCents < 4.99,
                onDecrease = { model.update { it.copy(matchCents = round((it.matchCents - 0.1) * 10) / 10) } },
                onIncrease = { model.update { it.copy(matchCents = round((it.matchCents + 0.1) * 10) / 10) } },
            )
        }),
)

/** The Stretch tab: one page per region, named as the engine names them. */
private fun stretchPages(s: TunerSettings, model: SettingsModel): List<SettingsPage> {
    val octaves = OctaveType.entries
    return listOf(
        SettingsPage(t(K.settings_page_bass), "From the lowest key to an octave above the lowest plain string, wound and plain alike. The octave type says which partials meet; the width, how far beyond beatless — wide means the lower note flatter, the upper sharper.",
            left = { RegionLeft(octaves, s.octaveBass, s.widthBass, { t -> model.update { it.copy(octaveBass = t) } }, { w -> model.update { it.copy(widthBass = w) } }) },
            right = { RegionRight(s.widthBass, s.widthBassFar, "the lowest key") { w -> model.update { it.copy(widthBassFar = w) } } }),
        SettingsPage(t(K.settings_page_middle), "Up to the temperament octave. The width slides by key between the regions' values; the middle holds one.",
            left = { RegionLeft(octaves, s.octaveMiddle, s.widthMiddle, { t -> model.update { it.copy(octaveMiddle = t) } }, { w -> model.update { it.copy(widthMiddle = w) } }) }),
        SettingsPage(t(K.settings_page_treble), "Above A4, up to C8. The width at C8 is where the slide ends.",
            left = { RegionLeft(octaves, s.octaveTreble, s.widthTreble, { t -> model.update { it.copy(octaveTreble = t) } }, { w -> model.update { it.copy(widthTreble = w) } }) },
            right = { RegionRight(s.widthTreble, s.widthTrebleFar, "C8") { w -> model.update { it.copy(widthTrebleFar = w) } } }),
        SettingsPage(t(K.settings_page_breaks), BREAKS_NOTE,
            left = {
                SettingsSection("The curve's breaks")
                val breaks = s.curveBreaks.sorted()
                if (breaks.isEmpty()) ReadOnlyRow("Breaks", "none")
                for (b in breaks.take(5)) ReadOnlyRow("Break at", Notes.name(b))
            },
            right = {
                val rest = s.curveBreaks.sorted().drop(5)
                if (rest.isNotEmpty()) {
                    SettingsSection("…")
                    for (b in rest.take(5)) ReadOnlyRow("Break at", Notes.name(b))
                }
            }),
    )
}

@Composable
private fun RegionLeft(octaves: List<OctaveType>, type: OctaveType, width: Double, onType: (OctaveType) -> Unit, onWidth: (Double) -> Unit) {
    SettingsSection("The octave")
    ChoiceRow("Octave type", octaves.map { it.label }, octaves.indexOf(type)) { onType(octaves[it]) }
    StepperRow(
        "Octave width", if (width == 0.0) "beatless" else String.format(Locale.ROOT, "%.1f c wide", width),
        canDecrease = width > 0.0,
        canIncrease = width < TunerSettings.MAX_WIDTH_CENTS,
        onDecrease = { onWidth(tenths(maxOf(0.0, width - WIDTH_STEP))) },
        onIncrease = { onWidth(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, width + WIDTH_STEP))) },
    )
}

/** The width at the region's far end: null = the same as the region's; set, and the width slides by key towards it. */
@Composable
private fun RegionRight(width: Double, far: Double?, farName: String, onFar: (Double?) -> Unit) {
    SettingsSection("At the far end")
    val f = far ?: width
    StepperRow(
        "… at $farName",
        if (far == null) "same" else if (f == 0.0) "beatless" else String.format(Locale.ROOT, "%.1f c wide", f),
        canDecrease = f > 0.0 || far != null,
        canIncrease = f < TunerSettings.MAX_WIDTH_CENTS,
        onDecrease = { val v = tenths(maxOf(0.0, f - WIDTH_STEP)); onFar(if (v == width) null else v) },
        onIncrease = { onFar(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, f + WIDTH_STEP))) },
    )
}

@Composable
private fun CompassRows(s: TunerSettings, model: SettingsModel) {
    SettingsSection("The piano")
    StepperRow(
        "Lowest key", Notes.name(s.lowestKeyMidi),
        canDecrease = s.lowestKeyMidi > TunerSettings.MIN_LOWEST_KEY,
        canIncrease = s.lowestKeyMidi < TunerSettings.MAX_LOWEST_KEY,
        onDecrease = { model.update { it.copy(lowestKeyMidi = it.lowestKeyMidi - 1) } },
        onIncrease = { model.update { it.copy(lowestKeyMidi = it.lowestKeyMidi + 1) } },
    )
    StepperRow(
        "Lowest unwound string", Notes.name(s.lowestUnwoundMidi),
        canDecrease = s.lowestUnwoundMidi > TunerSettings.MIN_UNWOUND,
        canIncrease = s.lowestUnwoundMidi < TunerSettings.MAX_UNWOUND,
        onDecrease = { model.update { it.copy(lowestUnwoundMidi = it.lowestUnwoundMidi - 1) } },
        onIncrease = { model.update { it.copy(lowestUnwoundMidi = it.lowestUnwoundMidi + 1) } },
    )
}

private const val WIDTH_STEP = 0.5

/** The pages' one-line notes. */
private const val TEMPERAMENT_NOTE = "Tuned first, downward from A4, to equal temperament on it; every other note is tuned by octaves from here."
private const val READING_NOTE = "The match window is your tolerance, not the measurement's: the engine reads a string to a tenth of a cent."
private const val COMPASS_NOTE = "A0–C8 for most pianos; F0 on a Bösendorfer 225, C0 on the Imperial. The first plain wire is where the wound strings' curve ends."
private const val BREAKS_NOTE = "The curve of string stiffness is drawn as straight runs over the keys. A break starts a new run, so a note not yet sampled is read from its own side of it. A sampled string keeps its own measurement, break or not. Set on the piano's page (Pro)."
private const val ABOUT_NOTE = "Free to use, study, change and share, with attribution and no warranty — the full text is LICENSE in the repository."
