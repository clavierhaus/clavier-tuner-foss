package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.i18n.noteName
import at.clavierhaus.unisonmaster.ui.num
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import at.clavierhaus.unisonmaster.audio.Microphone
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.ui.HelpText
import at.clavierhaus.unisonmaster.ui.HelpTopic
import at.clavierhaus.unisonmaster.ui.helpPage
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
 * by function, each page two columns of rows that say their name, what an
 * entry means behind its (i), nothing scrolling.
 *
 * - Tuning: Temperament · Reading
 * - Stretch: Wound · Bass · Middle · Treble · Breaks
 * - Piano: Range
 * - Help: one page, the index of topics on the left and the open topic on the right (14 Oct 2026);
 *   Quick start is the first topic, an edition adds its own after it, Pro the book's
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
    /** Pages an edition adds to the Piano tab after Range (Pro: the unison floors and the regimen a new piano starts from). */
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
    SettingsScreen(
        onClose = onBack,
        onHome = onHome,
        open = open,
        tabs = listOf(
            SettingsTab(t(K.settings_tab_tuning), tuningPages(s, model)),
            SettingsTab(t(K.settings_tab_stretch), stretchPages(s, model)),
            SettingsTab(t(K.settings_tab_piano), listOf(SettingsPage(t(K.settings_page_range), left = { RangeRows(s, model, lowestKeyRow, lowestPlainRow) })) + extraPianoPages),
        ) + extraTabs + listOf(
            SettingsTab(t(K.settings_tab_help), helpPage(t(K.settings_tab_help), listOf(quickStartTopic()) + extraHelpTopics)),
            SettingsTab(t(K.settings_tab_app), extraAppPages + listOf(
                // the phone's bars outside the tuning screen (Peter, 23 Oct 2026)
                SettingsPage(t(K.settings_page_display),
                    left = {
                        SettingsSection(t(K.settings_page_display), info = t(K.settings_note_bars))
                        SwitchRow(t(K.settings_navigationBar), s.showNavigationBar) { on -> model.update { it.copy(showNavigationBar = on) } }
                        SwitchRow(t(K.settings_statusBar), s.showStatusBar) { on -> model.update { it.copy(showStatusBar = on) } }
                    }),
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
                        // which microphone the app hears through: on the right, where the small phone has the room
                        MicrophoneRow()
                    }),
            )),
        ),
    )
    }
}

/** The Tuning tab: what today's tuning does and how it reads. */
private fun tuningPages(s: TunerSettings, model: SettingsModel): List<SettingsPage> = listOf(
    SettingsPage(t(K.settings_page_temperament),
        left = {
            SettingsSection(t(K.settings_temperament))
            // unequal temperaments are not offered until they run: nothing on a screen that does nothing
            ReadOnlyRow(t(K.settings_temperamentOctave), "A3–A4", info = TEMPERAMENT_NOTE)
        },
        right = {
            SettingsSection(t(K.settings_order))
            SwitchRow(t(K.settings_temperamentFirst), s.temperamentFirst) { on -> model.update { it.copy(temperamentFirst = on) } }
        }),
    SettingsPage(t(K.settings_page_reading),
        left = {
            SettingsSection(t(K.settings_screen))
            SwitchRow(t(K.settings_followKey), s.autoNote) { on -> model.update { it.copy(autoNote = on) } }
            SwitchRow(t(K.settings_cleanTogether), s.cleanPartials) { on -> model.update { it.copy(cleanPartials = on) } }
            // every partial, or the octave's alone (Gregor and Peter, 28 Oct 2026); all partials stay the default
            ChoiceRow(t(K.settings_strobeShows), listOf(t(K.settings_strobeAll), t(K.settings_strobeOne)), if (s.strobeOne) 1 else 0, info = t(K.settings_note_strobeOne)) { i ->
                model.update { it.copy(strobeOne = i == 1) }
            }
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
            // how many bands, or all, and whether the row comes up at once (Peter, 30 Oct 2026), beside the
            // left column, which holds no more on the smallest phone. One step
            // below all is the most there are; the stepper runs 1, 2 … MAX_STROBE_COUNT, all
            if (!s.strobeOne) {
                StepperRow(
                    t(K.settings_strobeCount),
                    if (s.strobeCount == 0) t(K.settings_strobeCountAll) else s.strobeCount.toString(),
                    canDecrease = s.strobeCount != 1,
                    canIncrease = s.strobeCount != 0,
                    info = t(K.settings_note_strobeCount),
                    onDecrease = { model.update { it.copy(strobeCount = if (it.strobeCount == 0) TunerSettings.MAX_STROBE_COUNT else it.strobeCount - 1) } },
                    onIncrease = { model.update { it.copy(strobeCount = if (it.strobeCount >= TunerSettings.MAX_STROBE_COUNT) 0 else it.strobeCount + 1) } },
                )
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
            StepperRow(
                t(K.settings_matchWindow), "±" + num(s.matchCents, 1) + " c",
                canDecrease = s.matchCents > 0.101,
                canIncrease = s.matchCents < 4.99,
                info = READING_NOTE,
                onDecrease = { model.update { it.copy(matchCents = round((it.matchCents - 0.1) * 10) / 10) } },
                onIncrease = { model.update { it.copy(matchCents = round((it.matchCents + 0.1) * 10) / 10) } },
            )
        }),
)

/** The Stretch tab: one page per region, named as the engine names them. */
private fun stretchPages(s: TunerSettings, model: SettingsModel): List<SettingsPage> {
    // each region offers only the octave types its strings sound (TunerSettings.BASS_OCTAVES, 30 Oct 2026)
    return listOf(
        SettingsPage(t(K.settings_page_bass),
            left = { RegionLeft(t(K.settings_note_bass), TunerSettings.BASS_OCTAVES, s.octaveBass, s.widthBass, { t -> model.update { it.copy(octaveBass = t) } }, { w -> model.update { it.copy(widthBass = w) } }) },
            right = { RegionRight(s.widthBass, s.widthBassFar, t(K.settings_theLowestKey)) { w -> model.update { it.copy(widthBassFar = w) } } }),
        SettingsPage(t(K.settings_page_middle),
            left = { RegionLeft(t(K.settings_note_middle), TunerSettings.MIDDLE_OCTAVES, s.octaveMiddle, s.widthMiddle, { t -> model.update { it.copy(octaveMiddle = t) } }, { w -> model.update { it.copy(widthMiddle = w) } }) }),
        SettingsPage(t(K.settings_page_treble),
            left = { RegionLeft(t(K.settings_note_treble), TunerSettings.TREBLE_OCTAVES, s.octaveTreble, s.widthTreble, { t -> model.update { it.copy(octaveTreble = t) } }, { w -> model.update { it.copy(widthTreble = w) } }) },
            right = { RegionRight(s.widthTreble, s.widthTrebleFar, "C8") { w -> model.update { it.copy(widthTrebleFar = w) } } }),
        SettingsPage(t(K.settings_page_breaks),
            left = {
                SettingsSection(t(K.settings_curveBreaks), info = BREAKS_NOTE)
                val breaks = s.curveBreaks.sorted()
                if (breaks.isEmpty()) ReadOnlyRow(t(K.settings_page_breaks), t(K.settings_none))
                for (b in breaks.take(5)) ReadOnlyRow(t(K.settings_breakAt), noteName(b))
            },
            right = {
                val rest = s.curveBreaks.sorted().drop(5)
                if (rest.isNotEmpty()) {
                    SettingsSection("…")
                    for (b in rest.take(5)) ReadOnlyRow(t(K.settings_breakAt), noteName(b))
                }
            }),
    )
}

@Composable
private fun RegionLeft(info: String, octaves: List<OctaveType>, type: OctaveType, width: Double, onType: (OctaveType) -> Unit, onWidth: (Double) -> Unit) {
    SettingsSection(t(K.settings_octave), info = info)
    ChoiceRow(t(K.settings_octaveType), octaves.map { it.label }, octaves.indexOf(type)) { onType(octaves[it]) }
    StepperRow(
        t(K.settings_octaveWidth), if (width == 0.0) t(K.settings_beatless) else t(K.settings_cWide, num(width, 1)),
        canDecrease = width > 0.0,
        canIncrease = width < TunerSettings.MAX_WIDTH_CENTS,
        onDecrease = { onWidth(tenths(maxOf(0.0, width - WIDTH_STEP))) },
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
        if (far == null) t(K.settings_same) else if (f == 0.0) t(K.settings_beatless) else t(K.settings_cWide, num(f, 1)),
        canDecrease = f > 0.0 || far != null,
        canIncrease = f < TunerSettings.MAX_WIDTH_CENTS,
        onDecrease = { val v = tenths(maxOf(0.0, f - WIDTH_STEP)); onFar(if (v == width) null else v) },
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
private val BREAKS_NOTE: String get() = t(K.settings_note_breaks)
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
    val context = LocalContext.current
    val declared = remember {
        runCatching {
            (context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager)
                .getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) != null
        }.getOrDefault(false)
    }
    val value = when (opened) {
        Microphone.Opened.NOT_YET -> t(K.about_mic_notYet)
        Microphone.Opened.PROCESSED -> t(K.about_mic_processed)
        Microphone.Opened.UNPROCESSED -> if (declared) t(K.about_mic_unprocessed) else t(K.about_mic_undeclared)
    }
    ReadOnlyRow(t(K.about_microphone), value)
}
