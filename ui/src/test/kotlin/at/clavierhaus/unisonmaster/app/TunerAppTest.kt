package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.persistence.SaveFile
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.persistence.Sealer
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.settings.MemoryStore
import at.clavierhaus.unisonmaster.settings.SettingsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Android activity did until 30 Oct 2026, now done by [TunerApp] for every platform: the
 * microphone on the tuning screen only and only once allowed, the tuning saved when the app leaves
 * the front and restored when the phone makes the app again, the bars shown at the start.
 */
class TunerAppTest {
    private class SilentSource : AudioSource {
        override val sampleRateHz = 48_000
        var running = false
        override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) { running = true }
        override fun stop() { running = false }
    }

    private class MemoryFile : SaveFile {
        var bytes: ByteArray? = null
        override fun read() = bytes
        override fun writeAtomic(bytes: ByteArray) { this.bytes = bytes.copyOf() }
        override fun setAside() { bytes = null }
        override fun delete() { bytes = null }
    }

    private object PlainSealer : Sealer {
        override fun seal(plain: ByteArray) = plain
        override fun open(sealed: ByteArray) = sealed
    }

    private class TestPhone(var mic: Boolean) : Phone {
        val bars = mutableListOf<Triple<Boolean, Boolean, Boolean>>()
        override val version = "test"
        override fun language() = "en"
        override fun hasMic() = mic
        override fun systemBars(hidden: Boolean, navigation: Boolean, status: Boolean) { bars += Triple(hidden, navigation, status) }
    }

    private fun app(file: MemoryFile, phone: TestPhone, source: SilentSource = SilentSource()) =
        TunerApp(TuningController(source) { 1_000L }, SettingsModel(MemoryStore()), SessionStore(file, PlainSealer), phone)

    @Test fun theMicrophoneRunsOnTheTuningScreenOnly() {
        val app = app(MemoryFile(), TestPhone(mic = true))
        app.start(recreated = false)
        app.screenShown("hub"); assertFalse(app.controller.live.value)
        app.screenShown("tune"); assertTrue(app.controller.live.value)
        app.screenShown("settings"); assertFalse(app.controller.live.value)
        app.screenShown("tune"); app.pause(); assertFalse(app.controller.live.value)
        app.resume(); assertTrue(app.controller.live.value)
    }

    @Test fun theMicrophoneWaitsForTheTunersYes() {
        val phone = TestPhone(mic = false)
        val app = app(MemoryFile(), phone)
        app.start(recreated = false)
        app.screenShown("tune"); assertFalse(app.controller.live.value)
        phone.mic = true; app.micAllowed(); assertTrue(app.controller.live.value)
        app.screenShown("hub"); app.micAllowed(); assertFalse(app.controller.live.value)
    }

    @Test fun theTuningIsSavedOnPauseAndRestoredWhenTheAppIsMadeAgain() {
        val file = MemoryFile()
        val first = app(file, TestPhone(mic = true))
        first.start(recreated = false)
        first.newTuning()
        // a tuning under way: A4 set at 443 Hz, sampling begun
        first.controller.restore(SessionSnapshot(savedAtMs = 1L, a4Hz = 443.0, currentMidi = 69, measurements = emptyList(), sampling = true))
        val snap = first.controller.snapshot()
        assertNotNull("a tuning under way has a session to save", snap)
        first.pause()
        assertNotNull("saved when the app left the front", file.bytes)

        val again = app(file, TestPhone(mic = true))
        again.start(recreated = true)
        assertEquals(443.0, again.controller.snapshot()!!.a4Hz, 1e-9)
        assertEquals(snap!!.sampling, again.controller.snapshot()!!.sampling)

        val fresh = app(file, TestPhone(mic = true))
        fresh.start(recreated = false)
        assertEquals("a cold start offers Continue, it does not restore by itself", null, fresh.controller.snapshot())
        assertTrue(fresh.lastTuning.value is SessionStore.Load.Ok)
    }

    @Test fun theBarsShowAtTheStart() {
        val phone = TestPhone(mic = true)
        app(MemoryFile(), phone).start(recreated = false)
        assertEquals(listOf(Triple(false, true, true)), phone.bars)
    }
}
