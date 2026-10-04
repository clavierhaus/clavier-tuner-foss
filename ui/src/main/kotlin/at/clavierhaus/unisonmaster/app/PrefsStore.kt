package at.clavierhaus.unisonmaster.app

import android.content.SharedPreferences
import at.clavierhaus.unisonmaster.settings.KeyValueStore

/** Settings persistence on Android: SharedPreferences behind the core's store interface. */
class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}
