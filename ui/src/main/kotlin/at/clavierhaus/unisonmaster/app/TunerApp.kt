package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
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
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.ui.OnePicture

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

    /** The microphone runs only on the tuning screen. */
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
            app.phone.systemBars(hidden = screen == "tune", navigation = settings.showNavigationBar, status = settings.showStatusBar)
        }
        LaunchedEffect(screen) { app.screenShown(screen) }
        // where Settings opens: null = where it last was; the tuning screen asks for the stretch's page (tab 1)
        var settingsOpen by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        fun openSettings(at: Pair<Int, Int>? = null) { settingsOpen = at; settingsFrom = screen; screen = "settings" }
        fun backToHub() {
            app.saveForHub()
            screen = "hub"
        }
        // where the bars show, the picture stands inside them; behind them the screens' black
        OnePicture(Modifier.background(Color(Brand.BLACK)).then(if (screen == "tune") Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars))) {
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
                    BasicHub(
                        controller = controller,
                        // no record and no play button in the free edition (blueprint §18.5): the recorder
                        // and the reference recording belong to the piano's record, which only Pro keeps, and
                        // since 5 Oct 2026 the recorder's own code lives in Pro too; since 10 Oct the probe
                        // as well, so the hub's probe slots are left at their defaults here
                        onSettings = { openSettings() },
                        onHome = { backToHub() },
                        onAutoNote = { on -> settingsModel.update { it.copy(autoNote = on) } },
                        onStretch = { region -> openSettings(1 to region) },
                        onTemperamentLow = { low -> settingsModel.update { it.copy(temperamentLowMidi = low) } },
                        onChangeSettings = { change -> settingsModel.update(change) },
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
