package at.clavierhaus.unisonmaster.app

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.FeedableAudioSource
import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.audio.createAudioSource

class MainActivity : ComponentActivity() {

    // the microphone, or a recording played in its place (the probe, docs/PROBE.md)
    private val audioSource by lazy { FeedableAudioSource(createAudioSource()) }
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

    /** What the probe last said, for the settings screen to show. */
    private val feedNote = mutableStateOf<String?>(null)

    /** Picks a WAV anywhere the phone can reach — the recordings folder, Downloads, adb push. */
    private val pickRecordingLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "recording"
            val listening = controller.live.value
            runCatching {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                audioSource.feed(name, WavPcm.read(bytes))
            }.onSuccess {
                feedNote.value = null
                // take it up at once if the tuning screen is already listening
                if (listening) { controller.stopLive(); controller.startLive() }
            }.onFailure { e -> feedNote.value = e.message ?: "That file could not be played." }
        }

    /** Opens the file dialog; the probe takes over from the next buffer. */
    private fun pickRecording() =
        pickRecordingLauncher.launch(arrayOf("audio/*", "application/octet-stream"))

    /** Gives the microphone back, without leaving the tuning screen. */
    private fun useMicrophone() {
        val listening = controller.live.value
        audioSource.useMicrophone()
        feedNote.value = null
        if (listening) { controller.stopLive(); controller.startLive() }
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
                    // the temperament octave is A3-A4 in both editions; the setting is kept for the day it is offered
                    controller.applySettings(settings.copy(temperamentLowMidi = TunerSettings.MAX_TEMPERAMENT_LOW))
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
                                feeding = audioSource.feeding.collectAsState().value,
                                feedNote = feedNote.value,
                                onPickRecording = { pickRecording() },
                                onUseMicrophone = { useMicrophone() },
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
                                // no record and no play button in the free edition (blueprint §18.5): the reference
                                // recording belongs to the piano's record, which only Pro keeps; the probe itself
                                // stays — App · Probe feeds the engine a recording where the microphone's buffers arrive
                                recorder = null,
                                onSettings = { openSettings() },
                                onHome = { backToHub() },
                                feeding = audioSource.feeding.collectAsState().value,
                                onPickRecording = null,
                                onUseMicrophone = { useMicrophone() },
                                onAutoNote = { on -> settingsModel.update { it.copy(autoNote = on) } },
                                onStretch = { region -> openSettings(1 to region) },
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
