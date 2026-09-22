package at.clavierhaus.unisonmaster.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import at.clavierhaus.unisonmaster.ui.OnePicture
import at.clavierhaus.unisonmaster.ui.HomeHubItem
import at.clavierhaus.unisonmaster.ui.HomeHub
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.persistence.PrivateSaveFile
import at.clavierhaus.unisonmaster.persistence.KeystoreSealer
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.settings.TunerSettings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AndroidAudioSource
import at.clavierhaus.unisonmaster.audio.createAudioSource

class MainActivity : ComponentActivity() {

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
    /** The red button's recorder: hears every buffer of the live screen, writes only between its taps. */
    private val recorder by lazy {
        SessionRecorder(this, audioSource.sampleRateHz) { (audioSource as? AndroidAudioSource)?.usedUnprocessed ?: false }
    }

    /** The microphone off, and with it any recording. */
    private fun stopListening() {
        recorder.stop()
        controller.stopLive()
    }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && micWanted) controller.startLive()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        hideSystemBars()
        lastTuning.value = sessionStore.load()
        controller.onSessionChanged = { snap -> sessionStore.save(snap) }
        controller.tap = { chunk -> recorder.push(chunk) }
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
                LaunchedEffect(settings) {
                    // FOSS: the temperament octave is always A3-A4 (selectable in Pro only)
                    controller.applySettings(settings.copy(temperamentLowMidi = TunerSettings.MAX_TEMPERAMENT_LOW))
                }
                LaunchedEffect(screen) {
                    // the microphone runs only while tuning: nothing is measured unseen
                    micWanted = screen == "tune"
                    if (micWanted && hasMic()) controller.startLive() else stopListening()
                }
                fun openSettings() { settingsFrom = screen; screen = "settings" }
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
                            )
                        }
                        "record" -> {
                            BackHandler { screen = "hub" }
                            RecordStrikesScreen(
                                firstPlainMidi = settings.lowestUnwoundMidi,
                                micGranted = hasMic(),
                                onBack = { screen = "hub" },
                            )
                        }
                        "tune" -> {
                            BackHandler { backToHub() }
                            BasicHub(
                                controller = controller,
                                recorder = if (settings.recordPcm) recorder else null,
                                onSettings = { openSettings() },
                                onBack = { backToHub() },
                            )
                        }
                        else -> {
                            val last by lastTuning
                            val entries = buildList {
                                (last as? SessionStore.Load.Ok)?.snapshot?.let { snap ->
                                    add(
                                        HomeHubItem("Continue", " Tuning", "from ${lastUsed(snap.savedAtMs)}, A4 ${at.clavierhaus.unisonmaster.ui.formatHz(snap.a4Hz)}") {
                                            controller.restore(snap)
                                            screen = "tune"
                                        },
                                    )
                                }
                                add(
                                    HomeHubItem(
                                        "New", " Tuning",
                                        if (last is SessionStore.Load.Rejected) "the saved tuning could not be verified" else "starting from A4",
                                    ) {
                                        sessionStore.clear()
                                        lastTuning.value = SessionStore.Load.None
                                        controller.resetSession()
                                        screen = "tune"
                                    },
                                )
                            }
                            // Record Strikes lives in Pro only: the wobble study continues
                            // there, and the FOSS hub carries nothing research-only.
                            HomeHub(entries = entries, onSettings = { openSettings() })
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

/** "15 Sep" — the day the saved tuning was last used. */
private fun lastUsed(ms: Long): String =
    java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date(ms))
