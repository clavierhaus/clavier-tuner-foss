package at.clavierhaus.unisonmaster.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import at.clavierhaus.unisonmaster.ui.OnePicture
import at.clavierhaus.unisonmaster.ui.HomeHubItem
import at.clavierhaus.unisonmaster.ui.HomeHub
import at.clavierhaus.unisonmaster.ui.SequenceChoice
import at.clavierhaus.unisonmaster.ui.SequencePicker
import at.clavierhaus.unisonmaster.tuning.AuralSequence
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AndroidAudioSource
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
    /** The red button's recorder: hears every buffer of the live screen, writes only between its taps. */
    private val recorder by lazy {
        SessionRecorder(this, audioSource.sampleRateHz) { (audioSource.live as? AndroidAudioSource)?.usedUnprocessed ?: false }
    }

    /** The microphone off, and with it any recording. */
    private fun stopListening() {
        recorder.stop()
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

    /** What the last sequence file had to say for itself, for the picker to show. */
    private val sequenceNote = mutableStateOf<String?>(null)
    /** A sequence chosen and waiting for the tuning screen to open on it. */
    private val sequenceToStart = mutableStateOf<AuralSequence?>(null)

    /** Picks a sequence file anywhere the phone can reach; a line that is not a note refuses the file by its number. */
    private val pickSequenceLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val name = (uri.lastPathSegment?.substringAfterLast('/') ?: "sequence").removeSuffix(".txt")
            runCatching {
                val text = contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8)
                AuralSequence.parse(name, text)
            }.onSuccess { seq -> sequenceNote.value = null; sequenceToStart.value = seq }
                .onFailure { e -> sequenceNote.value = "$name: " + (e.message ?: "that file could not be read") }
        }

    private fun pickSequence() = pickSequenceLauncher.launch(arrayOf("text/*", "application/octet-stream"))

    /** The tuning as it stands (or a fresh one) with [seq] walking it: the targets are the session's own. */
    private fun beginSequence(seq: AuralSequence) {
        val last = lastTuning.value
        if (last is SessionStore.Load.Ok) controller.restore(last.snapshot) else {
            sessionStore.clear(); lastTuning.value = SessionStore.Load.None; controller.resetSession()
        }
        controller.startSequence(seq)
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
                LaunchedEffect(settings.language) {
                    // the words: the chosen language, or the phone's
                    Strings.use(settings.language.ifEmpty { java.util.Locale.getDefault().toLanguageTag() })
                }
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
                    controller.stopSequence()
                    controller.snapshot()?.let { sessionStore.save(it) }
                    lastTuning.value = sessionStore.load()
                    screen = "hub"
                }
                // a sequence picked from a file: on to the tuning screen with it
                val chosen by sequenceToStart
                LaunchedEffect(chosen) {
                    chosen?.let { seq -> sequenceToStart.value = null; beginSequence(seq); screen = "tune" }
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
                            )
                        }
                        "sequence" -> {
                            BackHandler { screen = "hub" }
                            SequencePicker(
                                choices = listOf(
                                    SequenceChoice(
                                        AuralSequence.EXAMPLE_NAME,
                                        "the temperament F3–F4 by fourths and fifths, A4 first — an order, not a method",
                                    ) { beginSequence(AuralSequence.EXAMPLE); screen = "tune" },
                                ),
                                onPickFile = { pickSequence() },
                                fileNote = sequenceNote.value,
                                onBack = { screen = "hub" },
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
                                feeding = audioSource.feeding.collectAsState().value,
                                onPickRecording = if (settings.probe) ({ pickRecording() }) else null,
                                onUseMicrophone = { useMicrophone() },
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
                            val withSequence = entries + HomeHubItem(
                                "Aural", " sequence", "tune by ear in your own order; the reading checks each note",
                            ) { screen = "sequence" }
                            HomeHub(entries = withSequence, onSettings = { openSettings() })
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
