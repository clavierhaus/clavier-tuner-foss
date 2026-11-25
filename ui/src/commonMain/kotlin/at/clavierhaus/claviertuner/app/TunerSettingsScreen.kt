package at.clavierhaus.claviertuner.app

import at.clavierhaus.claviertuner.ui.microphoneDeclaresUnprocessed
import at.clavierhaus.claviertuner.i18n.noteName
import at.clavierhaus.claviertuner.ui.num
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import at.clavierhaus.claviertuner.Brand
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import at.clavierhaus.claviertuner.audio.Microphone
import at.clavierhaus.claviertuner.TuningController
import at.clavierhaus.claviertuner.i18n.K
import at.clavierhaus.claviertuner.i18n.Strings
import at.clavierhaus.claviertuner.i18n.t
import at.clavierhaus.claviertuner.ui.HelpText
import at.clavierhaus.claviertuner.ui.HelpTopic
import at.clavierhaus.claviertuner.ui.helpPage
import at.clavierhaus.claviertuner.ui.LocalHelpText
import androidx.compose.runtime.CompositionLocalProvider
import at.clavierhaus.claviertuner.settings.OctaveType
import at.clavierhaus.claviertuner.settings.SettingsModel
import at.clavierhaus.claviertuner.settings.StretchPreset
import at.clavierhaus.claviertuner.settings.TunerSettings
import at.clavierhaus.claviertuner.tuning.Notes
import at.clavierhaus.claviertuner.ui.ChoiceRow
import at.clavierhaus.claviertuner.ui.ReadOnlyRow
import at.clavierhaus.claviertuner.ui.SettingsPage
import at.clavierhaus.claviertuner.ui.SettingsGear
import at.clavierhaus.claviertuner.ui.SettingsSection
import at.clavierhaus.claviertuner.ui.SettingsTab
import at.clavierhaus.claviertuner.ui.StepperRow
import at.clavierhaus.claviertuner.ui.TextBlock
import at.clavierhaus.claviertuner.ui.SwitchRow
import kotlin.math.round

private fun tenths(x: Double) = round(x * 10.0) / 10.0

/**
 * The settings, in the gear of the restructured interface (Plus's of 21-22 Nov 2026; the free app's and Pro's since 23 Nov
 * 2026, Peter: "foss and pro get the same look of settings as plus. Strike the law about scrolling for the settings tabs"):
 * tab buttons across the top, the open tab one list of cards that scrolls (SettingsGear), sorted by how often a tuner needs a
 * thing:
 *
 * - Tuning: the temperament's order and the tolerance, the stretch preset, the two bars above C6;
 * - Stretch: each region, or the six places;
 * - Piano: the range, and an edition's piano pages; an edition's tabs after it;
 * - App: an edition's pages, the phone's bars;
 * - Advanced: how the screen reads;
 * - Help: one page, the index of topics and the open topic, scrolling on their own;
 * - About.
 *
 * Every row is the one it was, saying and changing the same setting; only the tabs they stand in change. Until 23 Nov 2026
 * the tabs were Tuning (Temperament, Reading, Above C6), Stretch, Piano, an edition's, Help, App (its pages, the bars, About),
 * each page two columns and nothing scrolling (the map of 3 Oct 2026). [open] names a tab to start on; the page within it
 * is the list's top.
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
    /** Pages an edition adds to the Piano tab after Range. */
    extraPianoPages: List<SettingsPage> = emptyList(),
    /** Tabs an edition adds after Piano, before Help (Pro: the customers book). */
    extraTabs: List<SettingsTab> = emptyList(),
    /** Topics an edition adds to Help's index after Quick start (Pro: the book's own help). */
    extraHelpTopics: List<HelpTopic> = emptyList(),
    /** Pages an edition adds to the App tab before About (Pro: Language, Probe). */
    extraAppPages: List<SettingsPage> = emptyList(),
    /**
     * The lowest key's row, for an edition that lets the tuner set it (Pro: down to C0). Without it the
     * range is the fixed A0–C8 of the free app (Peter, 19 Oct 2026), and the row only shows it.
     */
    lowestKeyRow: (@Composable () -> Unit)? = null,
    /**
     * The lowest plain string's row, for an edition that keeps it (Pro). The free app has none since 30 Oct
     * 2026 (Peter: "isn't relevant anymore"): since 29 Oct it changes no target.
     */
    lowestPlainRow: Boolean = false,
) {
    val s by model.settings.collectAsState()
    Strings.language.collectAsState().value                    // recompose on a language change

    // Help's text size reaches every help page, whichever edition built it (HelpParts.LocalHelpText)
    CompositionLocalProvider(LocalHelpText provides HelpText(s.helpTextSp) { sp -> model.update { it.copy(helpTextSp = sp) } }) {
    val stretch = stretchPages(s, model)
    val tabs = listOf(
        SettingsTab(t(K.settings_tab_tuning), listOf(temperamentPage(s, model), stretch.first(), topOctavePage(s, model))),
        SettingsTab(t(K.settings_tab_stretch), stretch.drop(1)),
        SettingsTab(t(K.settings_tab_piano), listOf(SettingsPage(t(K.settings_page_range), left = { RangeRows(s, model, lowestKeyRow, lowestPlainRow) })) + extraPianoPages),
    ) + extraTabs + listOf(
        SettingsTab(t(K.settings_tab_app), extraAppPages + listOf(
            // the phone's bars outside the tuning screen (Peter, 23 Oct 2026)
            SettingsPage(t(K.settings_page_display),
                left = {
                    SettingsSection(t(K.settings_page_display), info = t(K.settings_note_bars))
                    SwitchRow(t(K.settings_navigationBar), s.showNavigationBar) { on -> model.update { it.copy(showNavigationBar = on) } }
                    SwitchRow(t(K.settings_statusBar), s.showStatusBar) { on -> model.update { it.copy(showStatusBar = on) } }
                }),
        )),
        SettingsTab(t(K.settings_tab_advanced), listOf(readingPage(s, model))),
        SettingsTab(t(K.settings_tab_help), helpPage(t(K.settings_tab_help), helpTopics(rangeFixed = lowestKeyRow == null, temperaments = !model.equalOnly) + extraHelpTopics)),
        SettingsTab(t(K.settings_page_about), listOf(
            SettingsPage(t(K.settings_page_about),
                left = {
                    SettingsSection("Clavier Tuner")
                    ReadOnlyRow(t(K.about_version), version)
                    ReadOnlyRow(t(K.about_core), "clavier-tuner-foss")
                    ReadOnlyRow(t(K.about_typeface), t(K.about_typefaceValue))
                    ReadOnlyRow(t(K.about_icon), "Material Design icons · Apache 2.0")
                },
                right = {
                    SettingsSection(t(K.about_licence), info = ABOUT_NOTE)
                    ReadOnlyRow("Clavier Tuner", "Apache License 2.0")
                    ReadOnlyRow("clavierhaus.at", t(K.about_vienna))
                    // which microphone the app hears through
                    MicrophoneRow()
                }),
        )),
    )
    SettingsGear(
        tabs = tabs,
        onClose = onBack,
        onHome = onHome,
        open = open,
        // Help as one page: its index and its text scroll on their own (a list that scrolls cannot hold one that scrolls)
        fullPage = setOf(tabs.size - 2),
    )
    }
}


/** The Tuning tab's first page: the temperament octave's order, and the tolerance that makes a key green. */
private fun temperamentPage(s: TunerSettings, model: SettingsModel): SettingsPage =
    SettingsPage(t(K.settings_page_temperament),
        left = {
            // the temperament itself and its octave are on the start screen's edge (its strip sets start, end and the
            // temperament); the gear keeps the order, set once (Plus, 21 Nov 2026: a row that sets nothing is clutter)
            SettingsSection(t(K.settings_order), info = TEMPERAMENT_NOTE)
            SwitchRow(t(K.settings_temperamentFirst), s.temperamentFirst) { on -> model.update { it.copy(temperamentFirst = on) } }
        },
        right = {
            // the tolerance a general setting of the tuning (Plus, 22 Nov 2026, Peter: "That precision should be a general
            // setting a tuner sets in regular settings")
            SettingsSection(t(K.settings_matchWindow))
            StepperRow(
                t(K.settings_matchWindow), "±" + num(s.matchCents, 1) + " c",
                canDecrease = s.matchCents > 0.101,
                canIncrease = s.matchCents < 4.99,
                info = READING_NOTE,
                onDecrease = { model.update { it.copy(matchCents = round((it.matchCents - 0.1) * 10) / 10) } },
                onIncrease = { model.update { it.copy(matchCents = round((it.matchCents + 0.1) * 10) / 10) } },
            )
        })

/** Advanced: how the screen reads. */
private fun readingPage(s: TunerSettings, model: SettingsModel): SettingsPage =
    SettingsPage(t(K.settings_page_reading),
        left = {
            SettingsSection(t(K.settings_screen))
            SwitchRow(t(K.settings_followKey), s.autoNote) { on -> model.update { it.copy(autoNote = on) } }
            SwitchRow(t(K.settings_cleanTogether), s.cleanPartials) { on -> model.update { it.copy(cleanPartials = on) } }
            // what the strobe shows left the settings for the tuning screen, beside the strobe (StrobeChoice, 31 Oct 2026)
            StepperRow(
                t(K.settings_highestPartial), noteName(s.highestPartialMidi),
                canDecrease = s.highestPartialMidi > TunerSettings.MIN_HIGHEST_PARTIAL,
                canIncrease = s.highestPartialMidi < TunerSettings.MAX_HIGHEST_PARTIAL,
                onDecrease = { model.update { it.copy(highestPartialMidi = it.highestPartialMidi - 1) } },
                onIncrease = { model.update { it.copy(highestPartialMidi = it.highestPartialMidi + 1) } },
            )
        },
        right = {
            SettingsSection(t(K.settings_reading))
            // whether the row comes up at once (Peter, 30 Oct 2026); how many strobes is chosen beside the strobe since
            // 31 Oct 2026 (StrobeChoice)
            if (!s.strobeOne) {
                SwitchRow(t(K.settings_strobeTogether), s.strobeTogether, info = t(K.settings_note_strobeTogether)) { on ->
                    model.update { it.copy(strobeTogether = on) }
                }
            }
            // how far back the other strobes' ratios are averaged (Peter, 31 Oct 2026: "Strobe-Glättung"); the
            // whole strike last, after the longest step
            val smooth = TunerSettings.BAND_AVERAGING_STEPS
            val si = smooth.indexOfFirst { kotlin.math.abs(it - s.bandAveragingS) < 1e-6 }.let { if (it < 0) smooth.size - 1 else it }
            StepperRow(
                t(K.settings_strobeSmoothing), if (smooth[si] == 0.0) t(K.settings_strobeSmoothingWhole) else num(smooth[si], 2) + " s",
                canDecrease = si > 0,
                canIncrease = si < smooth.size - 1,
                info = t(K.settings_note_strobeSmoothing),
                onDecrease = { model.update { it.copy(bandAveragingS = smooth[si - 1]) } },
                onIncrease = { model.update { it.copy(bandAveragingS = smooth[si + 1]) } },
            )
            val steps = TunerSettings.MEMORY_STEPS
            val i = steps.indexOfFirst { kotlin.math.abs(it - s.readingMemoryS) < 1e-6 }.let { if (it < 0) 0 else it }
            StepperRow(
                t(K.settings_readingMemory), num(steps[i], 2) + " s",
                canDecrease = i > 0,
                canIncrease = i < steps.size - 1,
                onDecrease = { model.update { it.copy(readingMemoryS = steps[i - 1]) } },
                onIncrease = { model.update { it.copy(readingMemoryS = steps[i + 1]) } },
            )
        })

/**
 * The two bars above C6, an experiment (OctaveBars, 16 Nov 2026), with a way back to the strobe: in the Tuning tab, what a
 * tuner chooses once for every tuning (Peter, 18 Nov 2026); its note behind the i (Plus, 21 Nov 2026).
 */
private fun topOctavePage(s: TunerSettings, model: SettingsModel): SettingsPage = SettingsPage(t(K.settings_page_topOctave), left = {
    SettingsSection(t(K.settings_page_topOctave), info = t(K.settings_note_octaveBars))
    SwitchRow(t(K.settings_octaveBars), s.octaveBars) { on -> model.update { it.copy(octaveBars = on) } }
})

/** The Stretch tab: one page per region, named as the engine names them. */
private fun stretchPages(s: TunerSettings, model: SettingsModel): List<SettingsPage> {
    // each region offers only the octave types its strings sound (TunerSettings.BASS_OCTAVES, 30 Oct 2026)
    // the choice comes first, and the explanation beside it (Peter, 9 Nov 2026: "offer this as a default alternative in
    // settings with an explanation"; the switch had stood at the end of the Midrange page, and he could not find it)
    val choice = SettingsPage(t(K.settings_page_stretchDefault),
        left = {
            SettingsSection(t(K.settings_stretchDefault))
            ChoiceRow(t(K.settings_stretchPoints), stretchPresetLabels(), StretchPreset.of(s).ordinal) { i ->
                model.update { StretchPreset.entries[i].applyTo(it) }
            }
            TextBlock(listOf(t(K.settings_stretchBoth)), Modifier.fillMaxWidth(), size = 14.sp, color = Color(Brand.WHITE_MUTED))
        },
        right = { TextBlock(listOf(t(K.settings_stretchThreeNote), t(K.settings_stretchSixNote)), Modifier.fillMaxSize(), size = 14.sp, color = Color(Brand.WHITE_MUTED)) })
    if (s.hasPlaces) return listOf(choice, SettingsPage(t(K.settings_page_sixPoints), left = { StretchPlaces(s) { change -> model.update(change) } }))
    return listOf(
        choice,
        SettingsPage(t(K.settings_page_bass),
            left = { RegionLeft(t(K.settings_note_bass), TunerSettings.BASS_OCTAVES, s.octaveBass, s.widthBass, { t -> model.update { it.copy(octaveBass = t) } }, { w -> model.update { it.copy(widthBass = w) } }) },
            right = { RegionRight(s.widthBass, s.widthBassFar, t(K.settings_theLowestKey)) { w -> model.update { it.copy(widthBassFar = w) } } }),
        SettingsPage(t(K.settings_page_middle),
            left = { RegionLeft(t(K.settings_note_middle), TunerSettings.MIDDLE_OCTAVES, s.octaveMiddle, s.widthMiddle, { t -> model.update { it.copy(octaveMiddle = t) } }, { w -> model.update { it.copy(widthMiddle = w) } }) }),
        SettingsPage(t(K.settings_page_treble),
            left = { RegionLeft(t(K.settings_note_treble), TunerSettings.TREBLE_OCTAVES, s.octaveTreble, s.widthTreble, { t -> model.update { it.copy(octaveTreble = t) } }, { w -> model.update { it.copy(widthTreble = w) } }) },
            right = { RegionRight(s.widthTreble, s.widthTrebleFar, "C8") { w -> model.update { it.copy(widthTrebleFar = w) } } }),
    )
}

/**
 * The Stretch tab's page for a note's [region] (0 bass, 1 middle, 2 treble; TunerSettings.regionOf): the tuning screen
 * reaches it in one tap. The choice is the first page, so a region's page is one further on; with six points the three
 * regions are the one page of places.
 */
fun stretchPageOf(region: Int, s: TunerSettings): Int = if (s.hasPlaces) 1 else region + 1

@Composable
private fun RegionLeft(info: String, octaves: List<OctaveType>, type: OctaveType, width: Double, onType: (OctaveType) -> Unit, onWidth: (Double) -> Unit) {
    SettingsSection(t(K.settings_octave), info = info)
    ChoiceRow(t(K.settings_octaveType), octaves.map { it.label }, octaves.indexOf(type)) { onType(octaves[it]) }
    StepperRow(
        t(K.settings_octaveWidth), widthLabel(width),
        canDecrease = width > TunerSettings.MIN_WIDTH_CENTS,
        canIncrease = width < TunerSettings.MAX_WIDTH_CENTS,
        onDecrease = { onWidth(tenths(maxOf(TunerSettings.MIN_WIDTH_CENTS, width - WIDTH_STEP))) },
        onIncrease = { onWidth(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, width + WIDTH_STEP))) },
    )
}

/** The width at the region's far end: null = the same as the region's; set, and the width slides by key towards it. */
@Composable
private fun RegionRight(width: Double, far: Double?, farName: String, onFar: (Double?) -> Unit) {
    SettingsSection(t(K.settings_farEnd))
    val f = far ?: width
    StepperRow(
        t(K.settings_atFar, farName),
        if (far == null) t(K.settings_same) else widthLabel(f),
        canDecrease = f > TunerSettings.MIN_WIDTH_CENTS || far != null,
        canIncrease = f < TunerSettings.MAX_WIDTH_CENTS,
        onDecrease = { val v = tenths(maxOf(TunerSettings.MIN_WIDTH_CENTS, f - WIDTH_STEP)); onFar(if (v == width) null else v) },
        onIncrease = { onFar(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, f + WIDTH_STEP))) },
    )
}

@Composable
private fun RangeRows(s: TunerSettings, model: SettingsModel, lowestKeyRow: (@Composable () -> Unit)?, lowestPlainRow: Boolean) {
    SettingsSection(t(K.settings_piano))
    // the free app's range is fixed (SettingsModel.lowestKeyFixed); the row that sets it is Pro's
    if (lowestKeyRow != null) lowestKeyRow() else ReadOnlyRow(t(K.settings_lowestKey), noteName(s.lowestKeyMidi), info = RANGE_NOTE)
    if (lowestPlainRow) StepperRow(
        t(K.settings_lowestUnwound), noteName(s.lowestUnwoundMidi),
        canDecrease = s.lowestUnwoundMidi > TunerSettings.MIN_UNWOUND,
        canIncrease = s.lowestUnwoundMidi < TunerSettings.MAX_UNWOUND,
        info = UNWOUND_NOTE,
        onDecrease = { model.update { it.copy(lowestUnwoundMidi = it.lowestUnwoundMidi - 1) } },
        onIncrease = { model.update { it.copy(lowestUnwoundMidi = it.lowestUnwoundMidi + 1) } },
    )
}

private const val WIDTH_STEP = 0.5

/** What the entries mean, each opened by the (i) behind its entry. */
private val TEMPERAMENT_NOTE: String get() = t(K.settings_note_temperament)
private val READING_NOTE: String get() = t(K.settings_note_reading)
private val RANGE_NOTE: String get() = t(K.settings_note_range)
private val UNWOUND_NOTE: String get() = t(K.settings_note_unwound)
private val ABOUT_NOTE: String get() = t(K.settings_note_about)

/**
 * Which microphone the app hears through (26 Oct 2026): what the source opened ([Microphone]), and whether the
 * phone declares its unprocessed source at all. Android makes that source optional; a phone without it must say
 * so through PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED (Android CDD 5.11), and may still open the source with
 * its own processing. So "unprocessed" is said only when both the source and the phone say it.
 */
@Composable
private fun MicrophoneRow() {
    val opened by Microphone.opened.collectAsState()
    val declared = microphoneDeclaresUnprocessed()
    val value = when (opened) {
        Microphone.Opened.NOT_YET -> t(K.about_mic_notYet)
        Microphone.Opened.PROCESSED -> t(K.about_mic_processed)
        Microphone.Opened.UNPROCESSED -> if (declared) t(K.about_mic_unprocessed) else t(K.about_mic_undeclared)
    }
    ReadOnlyRow(t(K.about_microphone), value)
}
