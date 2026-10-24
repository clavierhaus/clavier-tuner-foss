package at.clavierhaus.unisonmaster.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.audio.WavPcm
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.persistence.SessionStore
import at.clavierhaus.unisonmaster.settings.MemoryStore
import at.clavierhaus.unisonmaster.settings.SettingsModel
import at.clavierhaus.unisonmaster.settings.TunerSettings
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The screens drawn on the phones they must work on, in every language, and read back (Peter, 24 Oct
 * 2026: "Keep all positions relative, never cut off text and be as flexible as possible in regard to
 * available mobile phones"; and, 15:10, a button of one word never breaks, and one element never
 * carries two sizes of type).
 *
 * Each screen is put on each pane of [PANES] by Robolectric, which lays it out and draws it natively on
 * the JVM, fed the strikes of the corpus through the engine exactly as the microphone feeds it. Every
 * piece of text on it is then read back from its own layout:
 * - **cut**: the text runs past its box (`hasVisualOverflow`), or its container clips it;
 * - **broken word**: a line ends inside a word, as "Übernehmen" became "Ü / h" and "gemessen"
 *   "ge / me" on the Motorola edge 50 neo (Gregor, Clavio, 24 Oct 2026);
 * - **overlap**: two pieces of text stand on each other;
 * - **too small**: stepped down below [MIN_SP], which is no longer type a tuner reads at the piano;
 * - **two sizes in one element**: a node tagged [ELEMENT] holds text of more than one size.
 * Every picture is written to `ui/build/layout-check/<pane>-<language>/`, so the eye can follow the
 * machine. Help scrolls by law (docs/UI.md) and is not drawn here.
 *
 * Says so when it cannot find its corpus, instead of passing (ledger, 5 Oct 2026). The corpus is found
 * as every corpus test finds it: `CLAVIER_DATA`, or `data/recordings` above the working directory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LayoutCheckTest {
    @get:Rule val rule = createComposeRule()

    private class Played : AudioSource {
        override val sampleRateHz = 48_000
        var signal = FloatArray(0)
        override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
            var pos = 0; val buf = FloatArray(bufferSize)
            while (pos + bufferSize <= signal.size) { signal.copyInto(buf, 0, pos, pos + bufferSize); onBuffer(buf); pos += bufferSize }
        }
        override fun stop() = Unit
    }

    private fun day(): File? {
        val base = System.getenv("CLAVIER_DATA")?.let { File(it) } ?: run {
            var d: File? = File(".").absoluteFile
            while (d != null && !File(d, "data/recordings").isDirectory) d = d.parentFile
            d?.let { File(it, "data/recordings") }
        }
        return base?.let { File(it, "2026-09-22") }?.takeIf { File(it, "Boesendorfer_manifest.csv").isFile }
    }

    /** One string's strike from the corpus, from [fromS] to [toS] seconds after its onset (a hop 6 dB over the five before). */
    private fun strike(dir: File, name: String, fromS: Double, toS: Double): FloatArray {
        val f = dir.listFiles()!!.filter { it.name.startsWith("Boesendorfer_$name") }.minByOrNull { it.name }!!
        val p = WavPcm.read(f.readBytes()); val x = p.samples; val h = 1024
        val db = DoubleArray(x.size / h) { i -> var s = 0.0; for (j in 0 until h) { val v = x[i * h + j].toDouble(); s += v * v }; 10 * kotlin.math.log10(s / h + 1e-24) }
        var on = 0
        for (i in 5 until db.size) if (db[i] - (i - 5 until i).maxOf { db[it] } >= 6) { on = i * h; break }
        return x.copyOfRange((on + fromS * p.sampleRateHz).toInt().coerceAtLeast(0), (on + toS * p.sampleRateHz).toInt().coerceAtMost(x.size))
    }

    private fun TuningController.hear(src: Played, s: FloatArray) { stopLive(); src.signal = s; startLive() }

    private val problems = ArrayList<String>()

    private fun textOf(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""

    private fun layoutOf(n: SemanticsNode): TextLayoutResult? {
        val out = ArrayList<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.firstOrNull()
    }

    /** Reads every piece of text on the screen back from its layout, and writes the picture. */
    private fun look(where: String, dir: File, name: String, dialog: Boolean = false) {
        // what the test changed (the scene, the engine's state) is sent to the composition, and frames run
        // until it is drawn: without this the picture was one scene behind
        Snapshot.sendApplyNotifications()
        repeat(4) { rule.mainClock.advanceTimeBy(200); rule.waitForIdle() }
        val img = (if (dialog) rule.onNode(isDialog()) else rule.onRoot()).captureToImage().asAndroidBitmap()
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        val boxes = ArrayList<Pair<String, Rect>>()
        for (n in texts) {
            // a control laid out unseen to give a heading its width is cleared of its meaning, and not read
            if (generateSequence(n.parent) { it.parent }.any { it.config.isClearingSemantics }) continue
            val r = layoutOf(n) ?: continue
            val text = r.layoutInput.text.text
            if (text.isBlank()) continue
            val at = "$where $name: \"$text\""
            // its lines against the box the text was given: past either edge, or below its foot, is cut
            // (read from the lines themselves: TextLayoutResult.hasVisualOverflow compares the paragraph laid
            // out at its widest, which this renderer reports wider than the box for any text that fits)
            val w = n.size.width; val h = n.size.height
            // (a centred line on one line may stand offset in the paragraph; its width is what must fit)
            if ((0 until r.lineCount).any { r.getLineRight(it) - r.getLineLeft(it) > w + 1.5f } || r.getLineBottom(r.lineCount - 1) > h + 1.5f)
                problems += "$at is cut (runs past its box)"
            val shown = n.boundsInRoot
            if (shown.width < n.size.width - 1.5f || shown.height < n.size.height - 1.5f)
                problems += "$at is cut by its container (shows ${shown.width.toInt()}×${shown.height.toInt()} of ${n.size.width}×${n.size.height} px)"
            (0 until r.lineCount - 1).map { r.getLineEnd(it) }
                .firstOrNull { e -> e in 1 until text.length && text[e - 1].isLetterOrDigit() && text[e].isLetterOrDigit() }
                ?.let { e -> problems += "$at breaks inside a word: \"…${text.substring(maxOf(0, e - 6), e)}|${text.substring(e, minOf(text.length, e + 6))}…\"" }
            val sp = r.layoutInput.style.fontSize.value
            if (sp < MIN_SP) problems += "$at is ${"%.1f".format(sp)} sp, under $MIN_SP"
            if (shown.width > 0 && shown.height > 0) boxes += text to shown
        }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val o = boxes[i].second.intersect(boxes[j].second)
            if (o.width > 2 && o.height > 2) problems += "$where $name: \"${boxes[i].first}\" and \"${boxes[j].first}\" stand on each other"
        }
        // an element is one size of type
        for (e in rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, ELEMENT), useUnmergedTree = true).fetchSemanticsNodes()) {
            val sizes = HashSet<Float>()
            fun walk(n: SemanticsNode) { layoutOf(n)?.let { if (it.layoutInput.text.text.isNotBlank()) sizes += it.layoutInput.style.fontSize.value }; n.children.forEach { walk(it) } }
            walk(e)
            if (sizes.size > 1) problems += "$where $name: one element carries ${sizes.size} sizes of type ${sizes.sorted()}"
        }
    }

    private fun check(pane: String, lang: String, fontScale: Float = 1f) {
        val corpus = day() ?: run { println("LayoutCheckTest: no recordings of 22 September here; nothing drawn, nothing checked"); return }
        Strings.use(lang)
        val where = "$pane, $lang" + if (fontScale != 1f) ", text ×$fontScale" else ""
        val dir = File("build/layout-check/$pane-$lang" + if (fontScale != 1f) "-text$fontScale" else "")
        val src = Played()
        val c = TuningController(src) { 1_761_300_000_000L }
        val model = SettingsModel(MemoryStore(), lowestKeyFixed = TunerSettings.MIDI_A0)
        c.applySettings(model.settings.value)
        val scene = mutableStateOf<Pair<String, @Composable () -> Unit>>("" to {})
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(Brand.ORANGE), onPrimary = Color(Brand.BLACK), secondary = Color(Brand.WHITE),
                onSecondary = Color(Brand.BLACK), background = Color(Brand.BLACK), onBackground = Color(Brand.WHITE), surface = Color(Brand.NEAR_BLACK), onSurface = Color(Brand.WHITE))) {
                // the phone's text size: the system setting is the density's font scale
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * fontScale)) {
                    Box(Modifier.fillMaxSize().background(Color(Brand.BLACK))) { key(scene.value.first) { scene.value.second() } }
                }
            }
        }
        fun show(name: String, content: @Composable () -> Unit) { scene.value = name to content }
        val tune: @Composable () -> Unit = {
            BasicHub(controller = c, onSettings = {}, onHome = {}, onAutoNote = {}, onStretch = {}, onTemperamentLow = {},
                onChangeSettings = { ch -> model.update(ch); c.applySettings(model.settings.value) })
        }
        // A4, its string sounding
        c.setReference(439.4)
        show("tune", tune)
        c.hear(src, strike(corpus, "A4-C-s1", -0.2, 1.6)); look(where, dir, "02-a4")
        c.acceptLive()
        // sampling: nothing yet; a key measuring; measured; kept; a short one
        look(where, dir, "03-sampling")
        c.hear(src, strike(corpus, "G3-C-s1", -0.2, 0.6)); look(where, dir, "04-sampling-measuring")
        c.hear(src, strike(corpus, "G3-C-s1", -0.2, 3.0)); look(where, dir, "05-sampling-measured")
        c.acceptLive(); look(where, dir, "06-sampling-kept")
        c.hear(src, strike(corpus, "C1-L-s1", -0.2, 0.9)); c.hear(src, strike(corpus, "G3-C-s1", -0.2, 0.3)); look(where, dir, "07-sampling-short")
        // tuning, on the strings sampled
        c.snapshot()?.let { c.restore(it.copy(sampling = false, currentMidi = 55)) }
        c.hear(src, strike(corpus, "G3-C-s1", -0.2, 1.5)); look(where, dir, "08-tuning")
        // settings, every page but Help's (Help scrolls by law)
        for ((tab, page) in listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 1 to 2, 1 to 3, 2 to 0, 4 to 0, 4 to 1)) {
            show("settings $tab $page") { TunerSettingsScreen(controller = c, model = model, version = "0.3.0", onBack = {}, onHome = {}, open = tab to page) }
            look(where, dir, "09-settings-$tab-$page")
        }
        // the hub, with a tuning to continue
        show("hub") { TunerHub(last = c.snapshot()?.let { SessionStore.Load.Ok(it) } ?: SessionStore.Load.None, onContinue = {}, onNew = {}, onSettings = {}) }
        look(where, dir, "01-hub")
        // the window behind the info line
        rule.mainClock.autoAdvance = true          // nothing moves behind a dialog, and its window draws only on a running clock
        show("info") { TuningInfoDialog(model.settings.value, { ch -> model.update(ch) }, {}) }
        look(where, dir, "10-info", dialog = true)
        problems.distinct().let { problems.clear(); problems.addAll(it) }
        if (problems.isNotEmpty()) fail("${problems.size} faults of the screen:\n" + problems.joinToString("\n"))
    }

    // The panes, landscape, in dp. The Motorola edge 50 neo (2670 × 1220 px, 460 ppi) at its densities
    // 3.0 and 2.75, and with the navigation bar standing at its side; a small 20:9 phone (1560 × 720 at
    // 2.0, or any phone set to a large display size); and the edge's system text at its largest.
    @Test @Config(qualifiers = "w890dp-h406dp-land-480dpi") fun edge50neoGerman() = check("890x406", "de")
    @Test @Config(qualifiers = "w890dp-h406dp-land-480dpi") fun edge50neoEnglish() = check("890x406", "en")
    @Test @Config(qualifiers = "w971dp-h443dp-land-440dpi") fun edge50neoAt275German() = check("971x443", "de")
    @Test @Config(qualifiers = "w842dp-h406dp-land-480dpi") fun edge50neoNavigationBarGerman() = check("842x406", "de")
    @Test @Config(qualifiers = "w780dp-h360dp-land-320dpi") fun smallPhoneGerman() = check("780x360", "de")
    @Test @Config(qualifiers = "w780dp-h360dp-land-320dpi") fun smallPhoneEnglish() = check("780x360", "en")
    @Test @Config(qualifiers = "w890dp-h406dp-land-480dpi") fun largestTextGerman() = check("890x406", "de", 1.3f)

    companion object {
        /** Under this the type is no longer read at arm's length from a piano. */
        const val MIN_SP = 10f
        /** The test tag of a screen element whose text is one size (TextBlock). */
        const val ELEMENT = "element"
    }
}
