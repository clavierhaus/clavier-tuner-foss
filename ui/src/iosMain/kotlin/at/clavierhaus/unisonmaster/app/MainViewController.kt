@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package at.clavierhaus.unisonmaster.app

import androidx.compose.ui.window.ComposeUIViewController
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.createAudioSource
import at.clavierhaus.unisonmaster.persistence.SaveFile
import at.clavierhaus.unisonmaster.persistence.Sealer
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.settings.KeyValueStore
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.ui.nowMs
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 * The free app on the iPhone (31 Oct 2026): the phone's own parts, handed to the app ([TunerApp]), and the phone's
 * events forwarded to it -- the same app as Android's MainActivity mounts, drawn by the same screens. What is here is
 * only what an iPhone has.
 *
 * Not yet carried over in this first build: the saved tuning (the session is kept while the app runs, not across a
 * restart: Continue needs a sealed file under a key in the Keychain, still to be written), the bundled faces and the
 * hub's photograph (PlatformParts.ios.kt), the status bar's hiding while tuning, and the crash note.
 */

/** The tuner's settings in the phone's user defaults: the iPhone's preferences. */
private class UserDefaultsStore : KeyValueStore {
    private val d = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = d.stringForKey("tuner.$key")
    override fun put(key: String, value: String) = d.setObject(value, "tuner.$key")
}

/** No file yet: nothing is kept across a restart (see above). Nothing is stored, so nothing is sealed. */
private class NoSaveFile : SaveFile {
    override fun read(): ByteArray? = null
    override fun writeAtomic(bytes: ByteArray) = Unit
    override fun setAside() = Unit
    override fun delete() = Unit
}

private object NotSealed : Sealer {
    override fun seal(plain: ByteArray): ByteArray = plain
    override fun open(sealed: ByteArray): ByteArray = sealed
}

private object IosPhone : Phone {
    override val version: String
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "dev"

    override fun language(): String = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"

    override fun hasMic(): Boolean = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

    override fun systemBars(hidden: Boolean, navigation: Boolean, status: Boolean) = Unit
}

private val app: TunerApp by lazy {
    TunerApp(
        controller = TuningController(createAudioSource()) { nowMs() },
        // the free app's range is A0–C8 and cannot be changed, on the iPhone as on Android (Peter, 19 Oct 2026)
        settingsModel = SettingsModel(UserDefaultsStore(), lowestKeyFixed = TunerSettings.MIDI_A0),
        sessionStore = SessionStore(NoSaveFile(), NotSealed),
        phone = IosPhone,
    )
}

/** The view controller the iOS app shows: the tuner's screens. */
fun MainViewController(): UIViewController {
    app.start(recreated = false)
    // the screen stays on while the app is in front: a tuner's hands are on the piano (Android: FLAG_KEEP_SCREEN_ON)
    UIApplication.sharedApplication.idleTimerDisabled = true
    AVAudioSession.sharedInstance().requestRecordPermission { granted ->
        if (granted) dispatch_async(dispatch_get_main_queue()) { app.micAllowed() }
    }
    val centre = NSNotificationCenter.defaultCenter
    centre.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ -> app.resume() }
    centre.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ -> app.pause() }
    // no back gesture of the system's own: every screen has its way home
    return ComposeUIViewController { TunerAppScreen(app) { } }
}
