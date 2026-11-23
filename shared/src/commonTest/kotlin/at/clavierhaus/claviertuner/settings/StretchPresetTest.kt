package at.clavierhaus.claviertuner.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** The presets (23 Nov 2026): the choice reads back as the preset it set, whatever was set before. */
class StretchPresetTest {
    @Test
    fun eachPresetReadsBackAsItself() {
        for (p in StretchPreset.entries) assertEquals(p, StretchPreset.of(p.applyTo(TunerSettings())), "$p")
        for (p in StretchPreset.entries) for (q in StretchPreset.entries) assertEquals(q, StretchPreset.of(q.applyTo(p.applyTo(TunerSettings()))), "$p then $q")
    }
}
