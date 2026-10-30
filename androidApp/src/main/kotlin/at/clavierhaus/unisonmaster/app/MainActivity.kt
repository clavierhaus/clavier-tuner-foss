package at.clavierhaus.unisonmaster.app

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.createAudioSource
import at.clavierhaus.unisonmaster.persistence.KeystoreSealer
import at.clavierhaus.unisonmaster.persistence.PrivateSaveFile
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.settings.TunerSettings

/**
 * The free app on Android: the phone's own parts, handed to the app ([TunerApp]), and the phone's
 * events forwarded to it. What the app does lives in the screens' library since 30 Oct 2026; what is
 * here is what only an Android phone has.
 */
class MainActivity : ComponentActivity(), Phone {

    // the microphone, and nothing that could stand in for it: the probe is Pro's (10 Oct 2026, docs/PROBE.md)
    private val app by lazy {
        TunerApp(
            controller = TuningController(createAudioSource()) { System.currentTimeMillis() },
            // the free app's range is A0–C8 and cannot be changed (Peter, 19 Oct 2026); Pro sets it down to C0
            settingsModel = SettingsModel(PrefsStore(getSharedPreferences("tuner-settings", MODE_PRIVATE)), lowestKeyFixed = TunerSettings.MIDI_A0),
            // Continue last tuning: one sealed file in app-private storage, this device only
            sessionStore = SessionStore(PrivateSaveFile(java.io.File(filesDir, "session")), KeystoreSealer()),
            phone = this,
        )
    }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) app.micAllowed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // a crash is kept on the phone and shown on the next start, never sent (CrashNote, 27 Oct 2026)
        CrashNote.install(this, version)
        // The screen stays on while the app is on it. A tuner's hands are on the piano, and the
        // phone's own timeout turned the screen off mid-tuning, which pauses the app and stops the
        // listening (17 Oct 2026, the first tuner's report after the F-Droid release; Peter: to be
        // fixed at once). Only while this window is shown: in the background the phone's setting holds.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        app.start(recreated = savedInstanceState != null)
        setContent { TunerAppScreen(app) { onBack -> BackHandler(onBack = onBack) } }
    }

    override fun onResume() {
        super.onResume()
        app.resume()
    }

    override fun onPause() {
        super.onPause()
        app.pause()
    }

    // --- Phone: what only an Android phone has ---

    override val version: String get() = packageManager.getPackageInfo(packageName, 0).versionName ?: "dev"

    override fun language(): String = java.util.Locale.getDefault().toLanguageTag()

    override fun hasMic(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** The bars as last asked for: set again when the window regains its focus. */
    private var bars = Triple(false, true, true)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) bars.let { (hidden, navigation, status) -> systemBars(hidden, navigation, status) }
    }

    /**
     * The status and navigation bars: hidden while tuning, shown everywhere else (Peter, 19 Oct 2026).
     * Until then they were hidden on every screen, and two testers in the forum found no way to close
     * the app: the Back, Home and Recents buttons only came for a moment after a swipe from the edge.
     * Hidden, a swipe from the edge still shows them for a moment, as before.
     */
    override fun systemBars(hidden: Boolean, navigation: Boolean, status: Boolean) {
        bars = Triple(hidden, navigation, status)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // outside the tuning screen each bar as the tuner set it under Settings › App › Display (23 Oct 2026)
            val nav = !hidden && navigation
            val stat = !hidden && status
            if (nav) show(WindowInsetsCompat.Type.navigationBars()) else hide(WindowInsetsCompat.Type.navigationBars())
            if (stat) show(WindowInsetsCompat.Type.statusBars()) else hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}
