package at.clavierhaus.unisonmaster.app

import android.view.WindowManager
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import at.clavierhaus.unisonmaster.ui.OnePicture
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.persistence.PrivateSaveFile
import at.clavierhaus.unisonmaster.persistence.KeystoreSealer
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.settings.SettingsModel
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.createAudioSource

class MainActivity : ComponentActivity() {

    // the microphone, or a recording played in its place (the probe, docs/PROBE.md)
    // the microphone, and nothing that could stand in for it: the feedable source is Pro's
    private val audioSource by lazy { createAudioSource() }
    private val controller by lazy { TuningController(audioSource) { System.currentTimeMillis() } }
    private val settingsModel by lazy {
        SettingsModel(PrefsStore(getSharedPreferences("tuner-settings", MODE_PRIVATE)))
    }
    /** The microphone runs only on the tuning screen. */
    private var micWanted = false

    // Continue last tuning: one sealed file in app-private storage, this device only.
    private val sessionStore by lazy {
        SessionStore(PrivateSaveFile(java.io.File(filesDir, "session")), KeystoreSealer())
    }
    private val lastTuning = mutableStateOf<SessionStore.Load>(SessionStore.Load.None)
    /** The microphone off. */
    private fun stopListening() {
        controller.stopLive()
    }

    // No probe in the free edition (10 Oct 2026, for the release): the engine fed a recording
    // through the file picker is Pro's working tool (core/docs/PROBE.md; Pro's MainActivity),
    // and this activity only ever listens to the microphone.

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && micWanted) controller.startLive()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen stays on while the app is on it. A tuner's hands are on the piano, and the
        // phone's own timeout turned the screen off mid-tuning, which pauses the app and stops the
        // listening (17 Oct 2026, the first tuner's report after the F-Droid release; Peter: to be
        // fixed at once). Only while this window is shown: in the background the phone's setting holds.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        hideSystemBars()
        lastTuning.value = sessionStore.load()
        controller.onSessionChanged = { snap -> sessionStore.save(snap) }
        // The activity made again while a tuning was under way (4 Oct 2026, Peter at the piano: a turn of the
        // phone between the two landscapes, or the system taking the activity down behind a screen
        // recording): the screen comes back as "tune" from its saved state, but the controller is new and
        // holds no session — the page stood as if nothing had been sampled. The session was saved at
        // onPause; it is the same tuning, so it is restored here before the first frame.
        if (savedInstanceState != null) (lastTuning.value as? SessionStore.Load.Ok)?.let { controller.restore(it.snapshot) }
        setContent {
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
                LaunchedEffect(settings.language) {
                    // the words: the chosen language, or the phone's
                    Strings.use(settings.language.ifEmpty { java.util.Locale.getDefault().toLanguageTag() })
                }
                LaunchedEffect(settings) {
                    // the temperament octave's low note is the tuner's, set on the sampling page (Peter, 4 Oct 2026)
                    controller.applySettings(settings)
                }
                LaunchedEffect(screen) {
                    // the microphone runs only while tuning: nothing is measured unseen
                    micWanted = screen == "tune"
                    if (micWanted && hasMic()) controller.startLive() else stopListening()
                }
                // where Settings opens: null = where it last was; the tuning screen asks for the stretch's page (tab 1)
                var settingsOpen by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                fun openSettings(at: Pair<Int, Int>? = null) { settingsOpen = at; settingsFrom = screen; screen = "settings" }
                fun backToHub() {
                    controller.snapshot()?.let { sessionStore.save(it) }
                    lastTuning.value = sessionStore.load()
                    screen = "hub"
                }
                OnePicture {
                    when (screen) {
                        "settings" -> {
                            BackHandler { screen = settingsFrom }
                            TunerSettingsScreen(
                                controller = controller,
                                model = settingsModel,
                                version = packageManager.getPackageInfo(packageName, 0).versionName ?: "dev",
                                onBack = { screen = settingsFrom },
                                onHome = { screen = "hub" },
                                open = settingsOpen,
                            )
                        }
                        "tune" -> {
                            BackHandler { backToHub() }
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
                            )
                        }
                        else -> {
                            // the hub is the library's (TunerHub); this app only says what is behind each door
                            TunerHub(
                                last = lastTuning.value,
                                onContinue = { snap -> controller.restore(snap); screen = "tune" },
                                onNew = {
                                    sessionStore.clear()
                                    lastTuning.value = SessionStore.Load.None
                                    controller.resetSession()
                                    screen = "tune"
                                },
                                onSettings = { openSettings() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hasMic(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (hasMic() && micWanted) controller.startLive()
    }

    override fun onPause() {
        super.onPause()
        controller.snapshot()?.let { sessionStore.save(it) }
        stopListening()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
