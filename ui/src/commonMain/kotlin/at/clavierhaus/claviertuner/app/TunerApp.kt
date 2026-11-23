package at.clavierhaus.claviertuner.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import at.clavierhaus.claviertuner.Brand
import at.clavierhaus.claviertuner.TuningController
import at.clavierhaus.claviertuner.i18n.Strings
import at.clavierhaus.claviertuner.persistence.SessionStore
import at.clavierhaus.claviertuner.settings.SettingsModel
import at.clavierhaus.claviertuner.settings.SchubertTune
import at.clavierhaus.claviertuner.settings.TunerSettings
import at.clavierhaus.claviertuner.tuning.OctaveSuggestion
import at.clavierhaus.claviertuner.ui.OnePicture

/*
 * The free app between the phone and the screens: which screen is in front, the microphone on the
 * tuning screen only, the tuning saved and continued, the system bars, the words. Until 30 Oct 2026
 * this was the Android activity's own code; it moved here, beside the screens it mounts, so the logic
 * is written once in plain Kotlin and only the phone's own parts ([Phone]) are written per platform.
 * The activity now makes the parts, hands them over, and forwards the phone's events. Nothing the
 * tuner sees or does changed in the move.
 */

/** What the app needs of the phone it runs on, and nothing else. */
interface Phone {
    /** The app's version, for Settings › App › About. */
    val version: String

    /** The phone's language as a language tag, for when the tuner has chosen none. */
    fun language(): String

    /** Whether the tuner has let the app hear through the microphone. */
    fun hasMic(): Boolean

    /**
     * The status and navigation bars: hidden while tuning, where the picture needs every millimetre;
     * on the other screens each shown or hidden as Settings › App › Display says (19 and 23 Oct 2026).
     */
    fun systemBars(hidden: Boolean, navigation: Boolean, status: Boolean)
}

/** The app's state outside the screens, and what the phone's events do to it. */
class TunerApp(
    val controller: TuningController,
    val settingsModel: SettingsModel,
    private val sessionStore: SessionStore,
    internal val phone: Phone,
) {
    /** The tuning saved last, for the hub's Continue. */
    internal val lastTuning = mutableStateOf<SessionStore.Load>(SessionStore.Load.None)

    /** The microphone runs only on the tuning screen and the false-beat check. */
    private var micWanted = false

    /**
     * The app starting. [recreated]: the phone made the app again while a tuning was under way
     * (4 Oct 2026, Peter at the piano: a turn of the phone between the two landscapes, or the system
     * taking the app down behind a screen recording). The screen comes back as "tune" from its saved
     * state, but the controller is new and holds no session -- the page stood as if nothing had been
     * sampled. The session was saved when the app was paused; it is the same tuning, so it is
     * restored here before the first frame.
     */
    fun start(recreated: Boolean) {
        phone.systemBars(hidden = false, navigation = true, status = true)
        lastTuning.value = sessionStore.load()
        controller.onSessionChanged = { snap -> sessionStore.save(snap) }
        if (recreated) (lastTuning.value as? SessionStore.Load.Ok)?.let { controller.restore(it.snapshot) }
    }

    /** The tuner has just allowed the microphone. */
    fun micAllowed() {
        if (micWanted) controller.startLive()
    }

    /** The app in front again. */
    fun resume() {
        if (phone.hasMic() && micWanted) controller.startLive()
    }

    /** The app leaving the front: the tuning is saved and the microphone stops. */
    fun pause() {
        controller.snapshot()?.let { sessionStore.save(it) }
        controller.stopLive()
    }

    /** The microphone on for the tuning screen, off for every other: nothing is measured unseen. */
    internal fun screenShown(screen: String) {
        micWanted = screen == "tune"
        if (micWanted && phone.hasMic()) controller.startLive() else controller.stopLive()
        // the hub offers the tuning under way however it is reached -- from the settings' Home too (Gregor,
        // 4 Nov 2026: Continue was not offered there, the hub showed what was saved when the app started)
        if (screen == "hub") saveForHub()
    }

    internal fun saveForHub() {
        controller.snapshot()?.let { sessionStore.save(it) }
        lastTuning.value = sessionStore.load()
    }

    internal fun newTuning() {
        sessionStore.clear()
        lastTuning.value = SessionStore.Load.None
        controller.resetSession()
    }
}

/**
 * The app's screens: the hub, the tuning screen and the settings, in the house's colours.
 * [backHandler] is the phone's Back gesture, given by the platform.
 */
@Composable
fun TunerAppScreen(app: TunerApp, backHandler: @Composable (onBack: () -> Unit) -> Unit) {
    val controller = app.controller
    val settingsModel = app.settingsModel
    /** A change of the stretch from the hub: the tuning's own while one is under way, else the defaults (5 Nov 2026). */
    val tuneOrDefault: ((TunerSettings) -> TunerSettings) -> Unit = { change -> controller.changeInForce(settingsModel, change) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(Brand.ORANGE),
            onPrimary = Color(Brand.BLACK),
            secondary = Color(Brand.WHITE),
            onSecondary = Color(Brand.BLACK),
            background = Color(Brand.BLACK),
            onBackground = Color(Brand.WHITE),
            surface = Color(Brand.NEAR_BLACK),
            onSurface = Color(Brand.WHITE),
        )
    ) {
        var screen by rememberSaveable { mutableStateOf("hub") }
        var settingsFrom by rememberSaveable { mutableStateOf("hub") }
        val settings by settingsModel.settings.collectAsState()
        // the last crash's note, if the app stopped on an error last time
        CrashNoteDialog()
        LaunchedEffect(settings.language) {
            // the words: the chosen language, or the phone's
            Strings.use(settings.language.ifEmpty { app.phone.language() })
        }
        LaunchedEffect(settings) {
            // the temperament octave's low note is the tuner's, set on the sampling page (Peter, 4 Oct 2026)
            controller.applySettings(settings)
        }
        LaunchedEffect(screen, settings.showNavigationBar, settings.showStatusBar) {
            // each bar as the tuner set it under Settings › App, on every screen, the tuning screen too (Peter,
            // 4 Nov 2026: "These settings do not work in tuning mode"); until then the tuning screen hid both always
            app.phone.systemBars(hidden = false, navigation = settings.showNavigationBar, status = settings.showStatusBar)
        }
        LaunchedEffect(screen) { app.screenShown(screen) }
        // the sampling ended with Done: the bass takes the octave type the sampling suggests and the tuning starts (Plus,
        // 22 Nov 2026, Peter: "We will simply choose what the app suggests. It's displayed in the tuning window and can then
        // be changed by a tuner who wants it differently. One step less."). The suggestion is the sampled strings' own
        // (OctaveSuggestion, Rule 9); none, and nothing is changed.
        val samplingDoneCount by controller.samplingDone.collectAsState()
        val seenSamplingDone = remember { mutableStateOf(samplingDoneCount) }
        /** The octave types open in the tuning screen's work, from the edge's Octaves. */
        var octavesOpen by remember { mutableStateOf(false) }
        LaunchedEffect(samplingDoneCount) {
            if (samplingDoneCount == seenSamplingDone.value) return@LaunchedEffect
            seenSamplingDone.value = samplingDoneCount
            // Ron Koval's Schubert is his octaves as published: the suggestion does not change them (23 Nov 2026)
            if (SchubertTune.isOn(controller.settings.value)) return@LaunchedEffect
            val snap = controller.snapshot() ?: return@LaunchedEffect
            val keys = snap.basis?.keys
            val suggested = OctaveSuggestion.bass(snap.measurements.filter { keys == null || it.midi in keys },
                controller.settings.value.temperamentLowMidi) ?: return@LaunchedEffect
            controller.changeInForce(settingsModel) { it.withBassOctave(suggested.type) }
        }
        // where Settings opens: null = where it last was; the tuning screen asks for the stretch's page (tab 1)
        var settingsOpen by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        fun openSettings(at: Pair<Int, Int>? = null) { settingsOpen = at; settingsFrom = screen; screen = "settings" }
        fun backToHub() {
            app.saveForHub()
            screen = "hub"
        }
        // where the bars show, the picture stands inside them; behind them the screens' black
        // where a bar shows the picture stands inside it, on every screen; a hidden bar takes no room
        OnePicture(Modifier.background(Color(Brand.BLACK)).windowInsetsPadding(WindowInsets.systemBars)) {
            when (screen) {
                "settings" -> {
                    backHandler { screen = settingsFrom }
                    TunerSettingsScreen(
                        controller = controller,
                        model = settingsModel,
                        version = app.phone.version,
                        onBack = { screen = settingsFrom },
                        onHome = { screen = "hub" },
                        open = settingsOpen,
                    )
                }
                "tune" -> {
                    backHandler { backToHub() }
                    // the restructured interface (Plus's, 21-23 Nov 2026; here since 23 Nov 2026, Peter: "the interface
                    // restructuring is a winner. Please apply it to foss and Pro"): A4 and the sampling on the start screen,
                    // the tuning on the tuning screen, the same edge, steps and keyboard on both. BasicHub, which drew all
                    // three here until then, stays in the library for an edition that still mounts it. No record button and
                    // no probe in the free edition (blueprint §18.5; the probe since 10 Oct 2026).
                    val tv = controller.tuning.collectAsState().value
                    if (tv != null && !tv.sampling) TuningScreen(
                        controller = controller,
                        onSettings = { openSettings() },
                        onHome = { backToHub() },
                        // the tuning's own stretch, saved with it; without a tuning, the defaults (5 Nov 2026)
                        onChangeSettings = { change -> tuneOrDefault(change) },
                        onAutoNote = { on -> settingsModel.update { it.copy(autoNote = on) } },
                        octavesStep = octavesOpen,
                        onOctavesStep = { octavesOpen = it },
                    ) else StartScreen(
                        controller = controller,
                        onSettings = { openSettings() },
                        onHome = { backToHub() },
                        onTemperamentLow = { low -> tuneOrDefault { it.copy(temperamentLowMidi = low) } },
                        onTemperamentHigh = { high -> tuneOrDefault { it.copy(temperamentHighMidi = high) } },
                        onChangeSettings = { change -> tuneOrDefault(change) },
                        temperaments = !settingsModel.equalOnly,
                    )
                }
                else -> {
                    // the hub is the library's (TunerHub); this app only says what is behind each door
                    TunerHub(
                        last = app.lastTuning.value,
                        onContinue = { snap -> controller.restore(snap); screen = "tune" },
                        onNew = {
                            app.newTuning()
                            screen = "tune"
                        },
                        onSettings = { openSettings() },
                    )
                }
            }
        }
    }
}
